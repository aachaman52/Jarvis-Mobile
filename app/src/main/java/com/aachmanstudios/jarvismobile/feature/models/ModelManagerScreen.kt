package com.aachmanstudios.jarvismobile.feature.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aachmanstudios.jarvismobile.core.model.*
import com.aachmanstudios.jarvismobile.data.repository.InstalledModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    vm: ModelManagerViewModel = viewModel(),
    onNavigateToChat: () -> Unit = {}
) {
    val downloadState by vm.downloadState.collectAsState()
    val modelState by vm.modelState.collectAsState()
    val installedModels by vm.installedModels.collectAsState()
    val settings by vm.settings.collectAsState()
    val uiState by vm.uiState.collectAsState()

    val device = remember { vm.getDeviceProfile() }
    val recommendation = remember { vm.getRecommendation() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importManualGguf(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Text(
            text = "Model Download Manager",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Status banner if present
        if (uiState.statusMessage.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.clearStatus() }) {
                        Text("Dismiss")
                    }
                }
            }
        }

        // Active Download / Verifying Progress Card
        when (val state = downloadState) {
            is DownloadState.Downloading -> {
                DownloadingCard(state = state, onCancel = { vm.cancelDownload() })
            }
            is DownloadState.Verifying -> {
                VerifyingCard(state = state)
            }
            is DownloadState.Completed -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "✓ ${state.model.displayName} Ready!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Model is verified and loaded for offline intelligence.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = onNavigateToChat) {
                            Text("Open Chat")
                        }
                    }
                }
            }
            else -> {}
        }

        // Device Profile & Recommendation Card
        DeviceRecommendationCard(
            device = device,
            recommendation = recommendation,
            installedModels = installedModels,
            isDownloading = downloadState is DownloadState.Downloading || downloadState is DownloadState.Verifying,
            onDownload = { model -> vm.downloadModel(model) },
            onOpenChat = onNavigateToChat
        )

        // Installed Models Section
        Text(
            text = "Installed Models (${installedModels.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (installedModels.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "No models installed yet. Download a recommended model below to enable offline chat.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            installedModels.forEach { model ->
                val isLoaded = modelState is ModelState.Ready && (modelState as ModelState.Ready).name == model.displayName
                val isDefault = settings.modelPath == model.file.absolutePath

                InstalledModelCard(
                    model = model,
                    isLoaded = isLoaded,
                    isDefault = isDefault,
                    isBusy = uiState.isBusy,
                    onLoad = { vm.loadModel(model) },
                    onUnload = { vm.unloadModel() },
                    onSetDefault = { vm.setDefault(model) },
                    onDelete = { vm.deleteModel(model) }
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // Available Models Catalog Section
        Text(
            text = "Available Models Catalog",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        ModelCatalog.models.forEach { catalogModel ->
            val isInstalled = installedModels.any { it.id == catalogModel.id }
            val isRecommended = recommendation.model?.id == catalogModel.id
            val isDownloadingThis = (downloadState as? DownloadState.Downloading)?.model?.id == catalogModel.id

            CatalogModelCard(
                model = catalogModel,
                isInstalled = isInstalled,
                isRecommended = isRecommended,
                isDownloading = downloadState is DownloadState.Downloading || downloadState is DownloadState.Verifying,
                isDownloadingThis = isDownloadingThis,
                onDownload = { vm.downloadModel(catalogModel) }
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // Advanced Options Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Advanced Options",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { vm.toggleAdvancedImport() }) {
                        Text(if (uiState.showAdvancedImport) "Hide" else "Show")
                    }
                }

                if (uiState.showAdvancedImport) {
                    Text(
                        text = "Have a custom instruction-tuned GGUF file? You can manually import it from device storage.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        enabled = !uiState.isBusy
                    ) {
                        Text("Import Custom GGUF File")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRecommendationCard(
    device: DeviceProfile,
    recommendation: Recommendation,
    installedModels: List<InstalledModel>,
    isDownloading: Boolean,
    onDownload: (ModelDefinition) -> Unit,
    onOpenChat: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Device Specs & Recommendation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Text(
                text = "Hardware: ${device.totalRamGbText} RAM (${device.availableRamMb} MB free) • ${device.cpuCores} CPU cores • ${device.freeStorageGbText} storage free",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            recommendation.model?.let { recModel ->
                val isAlreadyInstalled = installedModels.any { it.id == recModel.id }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RECOMMENDED: ${recModel.displayName}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Badge { Text(recModel.tier.name) }
                        }

                        Text(
                            text = "${recModel.parameterCount} params • ${recModel.quantization} • ${recModel.sizeGbText} download",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = recommendation.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(4.dp))

                        if (isAlreadyInstalled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onOpenChat) {
                                    Text("Model Ready • Start Chat")
                                }
                            }
                        } else {
                            Button(
                                onClick = { onDownload(recModel) },
                                enabled = !isDownloading
                            ) {
                                Text("Download Recommended Model (${recModel.sizeGbText})")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadingCard(
    state: DownloadState.Downloading,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Downloading ${state.model.displayName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${state.downloadedGbText} / ${state.totalGbText}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${state.speedMbText} • ${state.etaText}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Cancel Download")
            }
        }
    }
}

@Composable
private fun VerifyingCard(state: DownloadState.Verifying) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Verifying ${state.model.displayName}…",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Text(
                text = "${if (state.sizeVerified) "✓" else "⏳"} Expected Size Check",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${if (state.magicVerified) "✓" else "⏳"} GGUF Magic Header Check",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${if (state.sha256Verified) "✓" else "⏳"} Mandatory SHA-256 Checksum",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun InstalledModelCard(
    model: InstalledModel,
    isLoaded: Boolean,
    isDefault: Boolean,
    isBusy: Boolean,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isLoaded) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("Active") }
                    }
                    if (isDefault) {
                        Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("Default") }
                    }
                }
            }

            Text(
                text = "Size on disk: ${model.sizeGbText} • ${if (model.isCatalogModel) "Official Verified Model" else "Custom Import"}",
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLoaded) {
                    OutlinedButton(onClick = onUnload, enabled = !isBusy) {
                        Text("Unload")
                    }
                } else {
                    Button(onClick = onLoad, enabled = !isBusy) {
                        Text("Load")
                    }
                }

                if (!isDefault) {
                    TextButton(onClick = onSetDefault, enabled = !isBusy) {
                        Text("Set Default")
                    }
                }

                TextButton(
                    onClick = onDelete,
                    enabled = !isBusy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun CatalogModelCard(
    model: ModelDefinition,
    isInstalled: Boolean,
    isRecommended: Boolean,
    isDownloading: Boolean,
    isDownloadingThis: Boolean,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecommended) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isRecommended) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("Recommended") }
                    }
                    Badge { Text(model.tier.name) }
                }
            }

            Text(
                text = "${model.parameterCount} params • ${model.quantization} • ${model.sizeGbText} download",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "Repo: ${model.repository}\nLicense: ${model.licenseName}\nRequires: ${model.requiredStorageGbText} storage (with 512 MB reserve)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            if (isInstalled) {
                Text(
                    text = "✓ Installed in local storage",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (isDownloadingThis) {
                Text(
                    text = "Downloading… see progress above",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else {
                Button(
                    onClick = onDownload,
                    enabled = !isDownloading
                ) {
                    Text("Download (${model.sizeGbText})")
                }
            }
        }
    }
}
