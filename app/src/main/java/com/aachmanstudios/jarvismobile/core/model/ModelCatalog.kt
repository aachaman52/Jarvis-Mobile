package com.aachmanstudios.jarvismobile.core.model

data class ModelDefinition(
    val id: String,
    val displayName: String,
    val repository: String,
    val fileName: String,
    val downloadUrl: String,
    val parameterCount: String,
    val quantization: String,
    val expectedBytes: Long,
    val sha256: String,
    val tier: ModelTier,
    val minRamMb: Long,
    val recommendedRamMb: Long,
    val licenseName: String
) {
    val sizeMb: Long get() = expectedBytes / 1048576L
    val sizeGbText: String get() = String.format(java.util.Locale.US, "%.2f GB", expectedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
    val requiredStorageBytes: Long get() = expectedBytes + (512L * 1024L * 1024L)
    val requiredStorageGbText: String get() = String.format(java.util.Locale.US, "%.2f GB", requiredStorageBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
}

object ModelCatalog {
    val models: List<ModelDefinition> = listOf(
        ModelDefinition(
            id = "qwen2.5-0.5b-instruct-q4_k_m",
            displayName = "Qwen2.5 0.5B Instruct",
            repository = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            parameterCount = "0.5B",
            quantization = "Q4_K_M",
            expectedBytes = 491400032L,
            sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            tier = ModelTier.LITE,
            minRamMb = 3000L,
            recommendedRamMb = 4000L,
            licenseName = "Apache-2.0"
        ),
        ModelDefinition(
            id = "qwen2.5-3b-instruct-q4_k_m",
            displayName = "Qwen2.5 3B Instruct",
            repository = "Qwen/Qwen2.5-3B-Instruct-GGUF",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            parameterCount = "3B",
            quantization = "Q4_K_M",
            expectedBytes = 2104932768L,
            sha256 = "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d",
            tier = ModelTier.BALANCED,
            minRamMb = 5500L,
            recommendedRamMb = 8000L,
            licenseName = "Qwen Research / Apache-2.0 Compatible"
        )
    )

    fun findById(id: String): ModelDefinition? = models.find { it.id == id }
}
