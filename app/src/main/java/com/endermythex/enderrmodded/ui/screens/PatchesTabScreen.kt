package com.endermythex.enderrmodded.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.endermythex.enderrmodded.MainViewModel
import com.endermythex.enderrmodded.core.sources.AppCatalog

@Composable
fun PatchesTabScreen(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val importBundle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importBundle) }
    val repo = vm.repository
    val patches = repo.patches.sortedBy { it.name.orEmpty().lowercase() }
        .filter { query.isBlank() || it.name.orEmpty().contains(query, true) || it.description.orEmpty().contains(query, true) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Patchs", subtitle = "${repo.patches.size} patchs chargés")
        BusyRow(vm.busyMessage)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                InfoCard {
                    Text("Source : ${vm.settings.patchesRepo}", style = MaterialTheme.typography.titleSmall)
                    Text("Installé : ${vm.settings.patchesTag.ifBlank { "aucun" }}")
                    repo.latestRelease?.let { Text("Dernière version : ${it.tag}" + if (repo.updateAvailable) " (nouvelle !)" else "") }
                    repo.loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.updatePatches() }, enabled = vm.busyMessage == null) { Text("Mettre à jour") }
                        OutlinedButton(onClick = { importBundle.launch(arrayOf("*/*")) }, enabled = vm.busyMessage == null) { Text("Importer .rvp") }
                    }
                    if (vm.settings.patchesTag.isNotBlank()) {
                        val notes = repo.latestRelease?.takeIf { it.tag == vm.settings.patchesTag }?.body
                        if (!notes.isNullOrBlank()) Text(notes.take(600), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                OutlinedTextField(
                    query, { query = it }, label = { Text("Rechercher un patch") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(patches, key = { it.name.orEmpty() + it.hashCode() }) { patch ->
                val apps = patch.compatiblePackages?.joinToString { AppCatalog.nameOf(it.first) } ?: "Universel"
                ListItem(
                    headlineContent = { Text(patch.name.orEmpty()) },
                    supportingContent = {
                        Column {
                            patch.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text(apps + if (!patch.use) " • désactivé par défaut" else "", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                )
            }
        }
    }
}
