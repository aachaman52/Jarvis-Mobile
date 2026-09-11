package com.aachmanstudios.jarvismobile.core.model

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(
        val model: ModelDefinition,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progress: Float,
        val speedBytesPerSec: Long,
        val etaSeconds: Long
    ) : DownloadState {
        val downloadedMb: Long get() = downloadedBytes / (1024 * 1024)
        val totalMb: Long get() = totalBytes / (1024 * 1024)
        val downloadedGbText: String get() = String.format(java.util.Locale.US, "%.2f", downloadedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
        val totalGbText: String get() = String.format(java.util.Locale.US, "%.2f GB", totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
        val speedMbText: String get() = String.format(java.util.Locale.US, "%.1f MB/s", speedBytesPerSec.toDouble() / (1024.0 * 1024.0))
        val etaText: String get() = when {
            etaSeconds <= 0 -> "calculating…"
            etaSeconds < 60 -> "~${etaSeconds}s remaining"
            else -> "~${etaSeconds / 60}m ${etaSeconds % 60}s remaining"
        }
    }
    data class Verifying(
        val model: ModelDefinition,
        val stage: String,
        val sizeVerified: Boolean = false,
        val magicVerified: Boolean = false,
        val sha256Verified: Boolean = false
    ) : DownloadState
    data class Completed(val model: ModelDefinition, val file: File) : DownloadState
    data class Failed(val model: ModelDefinition?, val error: String) : DownloadState
    data class Cancelled(val model: ModelDefinition) : DownloadState
}

class ModelDownloadManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private var activeHttpConnection: HttpURLConnection? = null

    val modelsDir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    fun getModelFile(model: ModelDefinition): File = File(modelsDir, "${model.id}.gguf")
    fun getPartFile(model: ModelDefinition): File = File(modelsDir, "${model.id}.part")

    fun isModelInstalled(model: ModelDefinition): Boolean {
        val f = getModelFile(model)
        return f.isFile && f.length() == model.expectedBytes
    }

    fun startDownload(model: ModelDefinition) {
        if (state.value is DownloadState.Downloading || state.value is DownloadState.Verifying) {
            return
        }

        activeJob = scope.launch(Dispatchers.IO) {
            mutex.withLock {
                downloadInternal(model)
            }
        }
    }

    fun cancelDownload() {
        val current = _state.value
        if (current is DownloadState.Downloading) {
            activeHttpConnection?.disconnect()
            activeJob?.cancel()
            _state.value = DownloadState.Cancelled(current.model)
        }
    }

    fun deleteModel(model: ModelDefinition): Boolean {
        val f = getModelFile(model)
        val part = getPartFile(model)
        part.delete()
        return f.delete()
    }

    fun deleteFile(file: File): Boolean {
        return file.delete()
    }

    private suspend fun downloadInternal(model: ModelDefinition) {
        val partFile = getPartFile(model)
        val finalFile = getModelFile(model)

        // 1. Storage safety check
        val freeBytes = modelsDir.usableSpace
        val requiredBytes = model.requiredStorageBytes
        if (freeBytes < requiredBytes) {
            val freeGb = String.format(java.util.Locale.US, "%.2f GB", freeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
            _state.value = DownloadState.Failed(
                model,
                "Insufficient storage: $freeGb free. Requires at least ${model.requiredStorageGbText} (including 512 MB safety reserve)."
            )
            return
        }

        var existingBytes = if (partFile.exists()) partFile.length() else 0L

        // If part file is unexpectedly larger than expected, reset it
        if (existingBytes > model.expectedBytes) {
            partFile.delete()
            existingBytes = 0L
        }

        try {
            // Check if already completely downloaded in partFile
            if (existingBytes < model.expectedBytes) {
                downloadFileWithResume(model, partFile, existingBytes)
            }

            // 2. Verification
            verifyAndFinalize(model, partFile, finalFile)

        } catch (e: CancellationException) {
            _state.value = DownloadState.Cancelled(model)
            throw e
        } catch (e: Exception) {
            _state.value = DownloadState.Failed(model, e.message ?: "Download failed")
        } finally {
            activeHttpConnection?.disconnect()
            activeHttpConnection = null
        }
    }

    private suspend fun downloadFileWithResume(
        model: ModelDefinition,
        partFile: File,
        initialExistingBytes: Long
    ) = withContext(Dispatchers.IO) {
        var downloadedBytes = initialExistingBytes
        val totalBytes = model.expectedBytes

        val connection = openConnectionWithRedirects(model.downloadUrl, downloadedBytes)
        activeHttpConnection = connection

        val responseCode = connection.responseCode
        val appendMode = when (responseCode) {
            HttpURLConnection.HTTP_PARTIAL -> true // 206 Partial Content
            HttpURLConnection.HTTP_OK -> {
                // 200 OK: server ignored Range request; start from 0
                downloadedBytes = 0L
                false
            }
            416 -> { // HTTP 416 Requested Range Not Satisfiable
                if (partFile.length() == totalBytes) {
                    return@withContext
                } else {
                    partFile.delete()
                    downloadedBytes = 0L
                    false
                }
            }
            else -> {
                throw IllegalStateException("Server returned HTTP $responseCode: ${connection.responseMessage}")
            }
        }

        val output = FileOutputStream(partFile, appendMode)
        val input = connection.inputStream

        output.use { out ->
            input.use { inp ->
                val buffer = ByteArray(64 * 1024)
                var lastTime = System.currentTimeMillis()
                var bytesSinceLastSample = 0L
                var currentSpeed = 0L

                while (coroutineScope { isActive }) {
                    val read = inp.read(buffer)
                    if (read == -1) break

                    out.write(buffer, 0, read)
                    downloadedBytes += read
                    bytesSinceLastSample += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastTime
                    if (elapsed >= 500) {
                        currentSpeed = (bytesSinceLastSample * 1000L) / elapsed
                        lastTime = now
                        bytesSinceLastSample = 0L

                        val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        val remainingBytes = (totalBytes - downloadedBytes).coerceAtLeast(0L)
                        val etaSec = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L

                        _state.value = DownloadState.Downloading(
                            model = model,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            progress = progress,
                            speedBytesPerSec = currentSpeed,
                            etaSeconds = etaSec
                        )
                    }
                }
            }
        }
    }

    private suspend fun verifyAndFinalize(
        model: ModelDefinition,
        partFile: File,
        finalFile: File
    ) = withContext(Dispatchers.IO) {
        _state.value = DownloadState.Verifying(model, "Checking file size…")

        // 1. Expected bytes check
        val actualSize = partFile.length()
        if (actualSize != model.expectedBytes) {
            throw IllegalStateException("Size mismatch: expected ${model.expectedBytes} bytes, got $actualSize bytes.")
        }
        _state.value = DownloadState.Verifying(model, "Verifying GGUF header…", sizeVerified = true)

        // 2. GGUF Magic Header check (0x47, 0x47, 0x55, 0x46 = "GGUF")
        FileInputStream(partFile).use { fis ->
            val magic = ByteArray(4)
            val n = fis.read(magic)
            if (n < 4 || !magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
                throw IllegalStateException("Invalid file format: GGUF magic header missing.")
            }
        }
        _state.value = DownloadState.Verifying(model, "Verifying SHA-256 checksum…", sizeVerified = true, magicVerified = true)

        // 3. Mandatory SHA-256 verification
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(partFile).use { fis ->
            val buffer = ByteArray(256 * 1024)
            while (coroutineScope { isActive }) {
                val read = fis.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val computedHash = digest.digest().joinToString("") { "%02x".format(it) }
        if (!computedHash.equals(model.sha256, ignoreCase = true)) {
            throw IllegalStateException("Checksum verification failed.\nExpected: ${model.sha256}\nComputed: $computedHash")
        }

        _state.value = DownloadState.Verifying(model, "Finalizing installation…", sizeVerified = true, magicVerified = true, sha256Verified = true)

        // 4. Atomic Move to final destination
        try {
            Files.move(
                partFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (e: Exception) {
            // Fallback to non-atomic replace if filesystem doesn't support atomic moves across boundaries
            Files.move(
                partFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        _state.value = DownloadState.Completed(model, finalFile)
    }

    private fun openConnectionWithRedirects(initialUrl: String, rangeStartBytes: Long): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        val maxRedirects = 10

        while (redirects < maxRedirects) {
            val url = URL(currentUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "JarvisMobile/1.0 (Android)")

            if (rangeStartBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$rangeStartBytes-")
            }

            connection.connect()
            val code = connection.responseCode

            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308
            ) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("Redirect with empty Location header.")
                }
                currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                    location
                } else {
                    URL(url, location).toString()
                }
                redirects++
            } else {
                return connection
            }
        }
        throw IllegalStateException("Too many redirects: $maxRedirects")
    }
}
