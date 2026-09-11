package com.aachmanstudios.jarvismobile.feature.models

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aachmanstudios.jarvismobile.JarvisApplication
import com.aachmanstudios.jarvismobile.core.model.*
import com.aachmanstudios.jarvismobile.data.database.Preference
import com.aachmanstudios.jarvismobile.data.repository.InstalledModel
import com.aachmanstudios.jarvismobile.data.repository.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class ModelManagerUiState(
    val statusMessage: String = "",
    val isBusy: Boolean = false,
    val showAdvancedImport: Boolean = false
)

class ModelManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as JarvisApplication
    val downloadManager = app.downloadManager
    val modelRepo = app.modelRepo
    val localModelManager = app.model
    val monitor = app.monitor

    val downloadState: StateFlow<DownloadState> = downloadManager.state
    val modelState: StateFlow<ModelState> = localModelManager.state
    val installedModels: StateFlow<List<InstalledModel>> = modelRepo.installedModels.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        modelRepo.getInstalledModels()
    )

    val settings: StateFlow<Settings> = app.db.dao().preferences().map { Settings.from(it) }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        Settings()
    )

    private val _uiState = MutableStateFlow(ModelManagerUiState())
    val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()

    init {
        // Auto-load model when download completes
        viewModelScope.launch {
            downloadState.collect { state ->
                if (state is DownloadState.Completed) {
                    modelRepo.notifyChanged()
                    val installed = modelRepo.getInstalledModels().find { it.id == state.model.id }
                    if (installed != null) {
                        modelRepo.setDefaultModel(installed)
                        loadModel(installed)
                        _uiState.update { it.copy(statusMessage = "${state.model.displayName} installed and loaded successfully!") }
                    }
                } else if (state is DownloadState.Failed) {
                    _uiState.update { it.copy(statusMessage = state.error) }
                }
            }
        }
    }

    fun getDeviceProfile(): DeviceProfile = monitor.snapshot().profile

    fun getRecommendation(): Recommendation {
        val profile = getDeviceProfile()
        return DefaultModelRecommender().recommend(profile)
    }

    fun downloadModel(model: ModelDefinition) {
        val profile = getDeviceProfile()
        val freeBytes = profile.freeStorageMb * 1024 * 1024
        if (freeBytes < model.requiredStorageBytes) {
            _uiState.update {
                it.copy(statusMessage = "Cannot download: requires ${model.requiredStorageGbText} free space, but only ${profile.freeStorageGbText} is available.")
            }
            return
        }
        _uiState.update { it.copy(statusMessage = "Starting download for ${model.displayName}…") }
        downloadManager.startDownload(model)
    }

    fun cancelDownload() {
        downloadManager.cancelDownload()
        _uiState.update { it.copy(statusMessage = "Download cancelled.") }
    }

    fun deleteModel(model: InstalledModel) {
        viewModelScope.launch {
            if (settings.value.modelPath == model.file.absolutePath) {
                localModelManager.unload()
                modelRepo.clearDefaultModel()
            }
            val deleted = modelRepo.deleteModel(model)
            if (deleted) {
                _uiState.update { it.copy(statusMessage = "Deleted ${model.displayName}.") }
            }
        }
    }

    fun loadModel(model: InstalledModel) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = "Loading ${model.displayName}…") }
            try {
                localModelManager.unload()
                val profile = getDeviceProfile()
                val requiredRam = (model.sizeBytes / (1024 * 1024)) + 512
                if (profile.availableRamMb < requiredRam) {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Insufficient available RAM (${profile.availableRamMb} MB). Needs ~${requiredRam} MB. Close background apps."
                        )
                    }
                    return@launch
                }
                val ctx = settings.value.context
                localModelManager.load(model.file.absolutePath, model.displayName, ctx)
                _uiState.update { it.copy(statusMessage = "${model.displayName} loaded (${ctx} ctx).") }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = e.message ?: "Failed to load model.") }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            localModelManager.unload()
            _uiState.update { it.copy(statusMessage = "Model unloaded.") }
        }
    }

    fun setDefault(model: InstalledModel) {
        viewModelScope.launch {
            modelRepo.setDefaultModel(model)
            _uiState.update { it.copy(statusMessage = "Set ${model.displayName} as default.") }
        }
    }

    fun importManualGguf(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = "Importing GGUF file…") }
            try {
                val installed = modelRepo.importManualGguf(uri)
                loadModel(installed)
                _uiState.update { it.copy(statusMessage = "Imported and loaded ${installed.displayName}.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = e.message ?: "Import failed.") }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun toggleAdvancedImport() {
        _uiState.update { it.copy(showAdvancedImport = !it.showAdvancedImport) }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = "") }
    }
}
