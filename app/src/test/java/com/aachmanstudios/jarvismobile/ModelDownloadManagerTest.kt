package com.aachmanstudios.jarvismobile

import com.aachmanstudios.jarvismobile.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ModelDownloadManagerTest {

    @Test
    fun testCatalogIntegrity() {
        val models = ModelCatalog.models
        assertTrue("Catalog should contain models", models.isNotEmpty())

        val lite = models.find { it.tier == ModelTier.LITE }
        assertNotNull("LITE model should exist", lite)
        assertEquals("Qwen2.5 0.5B Instruct", lite?.displayName)
        assertEquals(491400032L, lite?.expectedBytes)
        assertEquals("74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db", lite?.sha256)

        val balanced = models.find { it.tier == ModelTier.BALANCED }
        assertNotNull("BALANCED model should exist", balanced)
        assertEquals("Qwen2.5 3B Instruct", balanced?.displayName)
        assertEquals(2104932768L, balanced?.expectedBytes)
        assertEquals("626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d", balanced?.sha256)
    }

    @Test
    fun testRecommenderForNordCE2() {
        val recommender = DefaultModelRecommender()
        // 8 GB RAM, 58 GB storage free, 8 CPU cores (OnePlus Nord CE 2 profile)
        val profile = DeviceProfile(
            totalRamMb = 8192,
            availableRamMb = 4200,
            cpuCores = 8,
            androidVersion = 33,
            freeStorageMb = 58000
        )
        val rec = recommender.recommend(profile)
        assertEquals(ModelTier.BALANCED, rec.tier)
        assertEquals("qwen2.5-3b-instruct-q4_k_m", rec.model?.id)
        assertTrue(rec.reason.contains("8 GB RAM"))
    }

    @Test
    fun testRecommenderForLiteDevice() {
        val recommender = DefaultModelRecommender()
        // 4 GB RAM, 20 GB storage free, 8 cores
        val profile = DeviceProfile(
            totalRamMb = 4096,
            availableRamMb = 1800,
            cpuCores = 8,
            androidVersion = 31,
            freeStorageMb = 20000
        )
        val rec = recommender.recommend(profile)
        assertEquals(ModelTier.LITE, rec.tier)
        assertEquals("qwen2.5-0.5b-instruct-q4_k_m", rec.model?.id)
    }

    @Test
    fun testGgufMagicHeaderValidation() {
        val tempFile = File.createTempFile("test_gguf", ".part")
        try {
            // Write valid GGUF magic bytes: 'G', 'G', 'U', 'F'
            FileOutputStream(tempFile).use { fos ->
                fos.write(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x01, 0x00, 0x00, 0x00))
            }

            val magic = ByteArray(4)
            tempFile.inputStream().use { it.read(magic) }
            assertTrue(magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46)))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testSha256Verification() {
        val testContent = "Jarvis Mobile Offline AI Engine"
        val tempFile = File.createTempFile("test_sha", ".tmp")
        try {
            tempFile.writeText(testContent)

            val digest = MessageDigest.getInstance("SHA-256")
            tempFile.inputStream().use { inp ->
                val buffer = ByteArray(1024)
                while (true) {
                    val read = inp.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            assertNotNull(hash)
            assertEquals(64, hash.length)
        } finally {
            tempFile.delete()
        }
    }
}
