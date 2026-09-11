package com.aachmanstudios.jarvismobile.data.repository

import android.content.Context
import android.net.Uri
import com.aachmanstudios.jarvismobile.core.model.ModelCatalog
import com.aachmanstudios.jarvismobile.core.model.ModelDefinition
import com.aachmanstudios.jarvismobile.core.model.ModelDownloadManager
import com.aachmanstudios.jarvismobile.data.database.JarvisDao
import com.aachmanstudios.jarvismobile.data.database.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class InstalledModel(
    val id: String,
    val displayName: String,
    val file: File,
    val sizeBytes: Long,
    val isCatalogModel: Boolean,
    val definition: ModelDefinition?
) {
    val sizeGbText: String get() = String.format(java.util.Locale.US, "%.2f GB", sizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
}

class ModelRepository(
    private val context: Context,
    private val dao: JarvisDao,
    private val downloadManager: ModelDownloadManager
) {
    private val _refreshTrigger = MutableStateFlow(0)

    fun getInstalledModels(): List<InstalledModel> {
        val dir = downloadManager.modelsDir
        val files = dir.listFiles { f -> f.isFile && f.extension == "gguf" } ?: emptyArray()

        return files.map { file ->
            val catalogDef = ModelCatalog.models.find { it.id == file.nameWithoutExtension || it.fileName == file.name }
            if (catalogDef != null) {
                InstalledModel(
                    id = catalogDef.id,
                    displayName = catalogDef.displayName,
                    file = file,
                    sizeBytes = file.length(),
                    isCatalogModel = true,
                    definition = catalogDef
                )
            } else {
                InstalledModel(
                    id = file.nameWithoutExtension,
                    displayName = file.nameWithoutExtension.replace('-', ' ').replace('_', ' ').capitalizeWords(),
                    file = file,
                    sizeBytes = file.length(),
                    isCatalogModel = false,
                    definition = null
                )
            }
        }
    }

    val installedModels: Flow<List<InstalledModel>> = _refreshTrigger.map {
        getInstalledModels()
    }

    fun notifyChanged() {
        _refreshTrigger.update { it + 1 }
    }

    suspend fun setDefaultModel(model: InstalledModel) {
        dao.preference(Preference("modelPath", model.file.absolutePath))
        dao.preference(Preference("modelName", model.displayName))
        notifyChanged()
    }

    suspend fun clearDefaultModel() {
        dao.preference(Preference("modelPath", ""))
        dao.preference(Preference("modelName", ""))
        notifyChanged()
    }

    suspend fun deleteModel(model: InstalledModel): Boolean = withContext(Dispatchers.IO) {
        val deleted = model.file.delete()
        val part = File(model.file.parentFile, "${model.id}.part")
        if (part.exists()) part.delete()
        notifyChanged()
        deleted
    }

    suspend fun importManualGguf(uri: Uri): InstalledModel = withContext(Dispatchers.IO) {
        val dir = downloadManager.modelsDir
        val temp = File(dir, "import_${System.currentTimeMillis()}.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    val magic = ByteArray(4)
                    var got = 0
                    while (got < 4) {
                        val n = input.read(magic, got, 4 - got)
                        check(n > 0) { "Empty or truncated file" }
                        got += n
                    }
                    check(magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) { "Selected file is not a valid GGUF file." }
                    output.write(magic)

                    val buffer = ByteArray(128 * 1024)
                    var total = 4L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        check(dir.usableSpace > 256L * 1024 * 1024) { "Insufficient storage during import." }
                        total += n
                        output.write(buffer, 0, n)
                    }
                }
            } ?: error("Unable to open selected file.")

            val destination = File(dir, "imported_${System.currentTimeMillis()}.gguf")
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)

            val installed = InstalledModel(
                id = destination.nameWithoutExtension,
                displayName = "Imported GGUF",
                file = destination,
                sizeBytes = destination.length(),
                isCatalogModel = false,
                definition = null
            )
            setDefaultModel(installed)
            notifyChanged()
            installed
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }
    }
}
