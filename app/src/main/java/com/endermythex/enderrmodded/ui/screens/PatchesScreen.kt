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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.endermythex.enderrmodded.MainViewModel

@Composable
fun PatchesScreen(vm: MainViewModel) {
    val apk = vm.apk ?: return
    val anyVersionWarning = vm.patchItems.any { item ->
        item.name in vm.selected && item.recommendedVersions?.let { apk.versionName !in it } == true
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Patchs pour ${apk.label}", onBack = { vm.back() })
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = vm::selectDefaults) { Text("Par défaut") }
            TextButton(onClick = vm::selectAll) { Text("Tout") }
            TextButton(onClick = vm::selectNone) { Text("Aucun") }
        }
        if (anyVersionWarning) {
            Text(
                "⚠ Certains patchs sélectionnés ne sont pas prévus pour la version ${apk.versionName}. " +
                    "Ils peuvent échouer.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(vm.patchItems, key = { it.name }) { item ->
                val versions = item.recommendedVersions
                ListItem(
                    leadingContent = {
                        Checkbox(checked = item.name in vm.selected, onCheckedChange = { vm.toggle(item.name) })
                    },
                    headlineContent = { Text(item.name) },
                    supportingContent = {
                        Column {
                            item.description?.let { Text(it) }
                            if (versions != null) {
                                Text(
                                    "Versions : " + versions.sortedDescending().take(4).joinToString(", ") +
                                        if (versions.size > 4) "…" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (apk.versionName in versions) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { vm.toggle(item.name) },
                )
                HorizontalDivider()
            }
        }
        Button(
            onClick = vm::startPatching,
            enabled = vm.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("Patcher (${vm.selected.size})") }
    }
}
