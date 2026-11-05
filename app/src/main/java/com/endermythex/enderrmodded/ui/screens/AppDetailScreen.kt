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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.endermythex.enderrmodded.core.sources.AppCatalog
import com.endermythex.enderrmodded.data.versionsFor
import com.endermythex.enderrmodded.pipeline.AutoPatcher
import com.endermythex.enderrmodded.pipeline.PipelineRegistry
import java.text.DateFormat
import java.util.Date

private enum class VersionMode { Recommended, Latest, Custom }

@Composable
fun AppDetailScreen(vm: MainViewModel, packageName: String) {
    val name = AppCatalog.nameOf(packageName)
    val entry = vm.apps.firstOrNull { it.packageName == packageName }
    val compatible = vm.repository.compatiblePatches(packageName)
    var saved by remember(packageName) { mutableStateOf(vm.store.selection(packageName)) }
    val selectedNames = saved ?: compatible.filter { it.use }.mapNotNull { it.name }.toSet()
    val recommended = vm.repository.recommendedVersion(packageName, compatible.filter { it.name in selectedNames })
    var mode by remember { mutableStateOf(if (vm.settings.useLatestVersion) VersionMode.Latest else VersionMode.Recommended) }
    var customVersion by remember { mutableStateOf("") }
    val runs by PipelineRegistry.runs.collectAsState()
    val running = runs[packageName]?.running == true

    LaunchedEffect(packageName) { vm.fetchLatestVersion(packageName) }
    val latest = vm.latestVersions[packageName]

    fun update(names: Set<String>?) {
        saved = names
        vm.setSelection(packageName, names)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(name, subtitle = packageName, onBack = { vm.back() })
        LazyColumn(Modifier.weight(1f)) {
            item {
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(packageName, name, 52.dp)
                        Column {
                            Text("Recommandée : ${recommended ?: "toutes versions"}", style = MaterialTheme.typography.bodyMedium)
                            Text("Dernière en ligne : ${latest ?: "…"}", style = MaterialTheme.typography.bodySmall)
                            Text("Installée : ${entry?.installedVersion ?: "non"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    entry?.patched?.let { p ->
                        Text(
                            "Patchée : v${p.version} • patchs ${p.patchesTag} • " +
                                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(p.patchedAt)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (p.patchedPackageName != packageName) {
                            Text("Installée sous le nom ${p.patchedPackageName}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (entry?.needsUpdate(vm.settings.patchesTag) == true) {
                            Text("Une mise à jour est disponible.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item { SectionTitle("Version à patcher") }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(mode == VersionMode.Recommended, { mode = VersionMode.Recommended }, label = { Text("Recommandée") })
                    FilterChip(mode == VersionMode.Latest, { mode = VersionMode.Latest }, label = { Text("Dernière") })
                    FilterChip(mode == VersionMode.Custom, { mode = VersionMode.Custom }, label = { Text("Autre") })
                }
                if (mode == VersionMode.Custom) {
                    OutlinedTextField(
                        customVersion, { customVersion = it.trim() }, label = { Text("Version (ex. 20.12.46)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (mode != VersionMode.Recommended && recommended != null) {
                    Text(
                        "⚠ Hors version recommandée, certains patchs peuvent échouer.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            item {
                SectionTitle("Patchs (${selectedNames.size}/${compatible.size})")
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = { update(null) }) { Text("Par défaut") }
                    TextButton(onClick = { update(compatible.mapNotNull { it.name }.toSet()) }) { Text("Tout") }
                    TextButton(onClick = { update(emptySet()) }) { Text("Aucun") }
                }
            }
            items(compatible, key = { it.name.orEmpty() }) { patch ->
                val patchName = patch.name.orEmpty()
                val checked = patchName in selectedNames
                val versions = patch.versionsFor(packageName)
                ListItem(
                    leadingContent = {
                        Checkbox(checked, onCheckedChange = { update(if (checked) selectedNames - patchName else selectedNames + patchName) })
                    },
                    headlineContent = { Text(patchName) },
                    supportingContent = {
                        Column {
                            patch.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (patch.compatiblePackages == null) Text("Patch universel", style = MaterialTheme.typography.labelSmall)
                            else if (versions != null) Text(
                                "Versions : " + versions.sortedDescending().take(3).joinToString(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    modifier = Modifier.clickable { update(if (checked) selectedNames - patchName else selectedNames + patchName) },
                )
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running) {
                OutlinedButton(onClick = { vm.open(Route.Run(packageName)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Voir le patch en cours")
                }
            } else {
                Button(
                    onClick = {
                        val version = when (mode) {
                            VersionMode.Recommended -> recommended
                            VersionMode.Latest -> AutoPatcher.LATEST
                            VersionMode.Custom -> customVersion.ifBlank { null }
                        }
                        vm.autoPatch(packageName, version)
                    },
                    enabled = selectedNames.isNotEmpty() && (mode != VersionMode.Custom || customVersion.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(if (entry?.patched != null) "  Mettre à jour automatiquement" else "  Patch automatique")
                }
                if (entry?.patched != null) {
                    TextButton(onClick = { vm.forget(packageName) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ne plus suivre les mises à jour de cette app")
                    }
                }
            }
        }
    }
}
