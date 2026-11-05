package com.endermythex.enderrmodded.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.endermythex.enderrmodded.MainViewModel
import com.endermythex.enderrmodded.Route
import com.endermythex.enderrmodded.pipeline.PipelineRegistry

@Composable
fun AppsScreen(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val runs by PipelineRegistry.runs.collectAsState()
    val apps = vm.apps.filter {
        query.isBlank() || it.name.contains(query, true) || it.packageName.contains(query, true)
    }
    val tag = vm.settings.patchesTag

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("EnderrModded", subtitle = "Patchs : ${tag.ifBlank { "aucun" }}") {
            IconButton(onClick = { vm.updatePatches(); vm.refreshInstalled() }) { Icon(Icons.Default.Refresh, "Actualiser") }
            IconButton(onClick = { vm.open(Route.ManualHome) }) { Icon(Icons.Default.Add, "Patcher un fichier") }
        }
        BusyRow(vm.busyMessage)

        vm.selfUpdate?.let { release ->
            InfoCard {
                Text("Nouvelle version d'EnderrModded : ${release.tag}", style = MaterialTheme.typography.titleSmall)
                val progress = vm.selfUpdateProgress
                if (progress != null) WavyLinearProgress(progress)
                else Button(onClick = vm::installSelfUpdate) { Text("Mettre à jour") }
            }
        }
        if (vm.repository.updateAvailable && !vm.settings.autoUpdatePatches) {
            InfoCard {
                Text("Nouveaux patchs : ${vm.repository.latestRelease?.tag}", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { vm.updatePatches() }) { Text("Télécharger") }
            }
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Rechercher une app") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (apps.isEmpty() && vm.busyMessage == null) {
            Text(
                if (vm.repository.hasBundle) "Aucune app trouvée." else "Aucun bundle de patchs. Va dans l'onglet Patchs.",
                modifier = Modifier.padding(24.dp),
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { app ->
                val run = runs[app.packageName]
                ListItem(
                    leadingContent = { AppIcon(app.packageName, app.name) },
                    headlineContent = { Text(app.name) },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${app.patchCount} patchs • " + (app.recommendedVersion?.let { "v$it recommandée" } ?: "toutes versions"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                when {
                                    run?.running == true -> Chip("Patch en cours…", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                    app.patched != null && app.needsUpdate(vm.settings.patchesTag) ->
                                        Chip("Mise à jour dispo", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                    app.patched != null -> Chip("Patchée v${app.patched.version}")
                                }
                                if (app.installedVersion != null) Chip("Installée v${app.installedVersion}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    modifier = Modifier.clickable { vm.open(Route.AppDetail(app.packageName)) },
                )
            }
        }
    }
}
