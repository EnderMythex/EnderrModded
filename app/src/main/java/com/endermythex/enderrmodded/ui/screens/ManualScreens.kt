package com.endermythex.enderrmodded.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.endermythex.enderrmodded.MainViewModel
import com.endermythex.enderrmodded.ManualViewModel
import com.endermythex.enderrmodded.Route
import com.endermythex.enderrmodded.installer.Installer

@Composable
fun ManualHomeScreen(vm: MainViewModel, manual: ManualViewModel) {
    val pickApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { manual.importApk(it) } }
    val idle = manual.busyMessage == null
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Patch manuel", subtitle = "APK, .apks, .xapk ou .apkm", onBack = { vm.back() })
        InfoCard {
            val apk = manual.apk
            if (apk == null) Text("Aucune app sélectionnée")
            else {
                Text(apk.label, style = MaterialTheme.typography.titleMedium)
                Text("${apk.packageName} • v${apk.versionName ?: "?"}", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.open(Route.ManualPicker) }, enabled = idle) { Text("App installée") }
                OutlinedButton(onClick = { pickApk.launch(arrayOf("*/*")) }, enabled = idle) { Text("Fichier") }
            }
        }
        BusyRow(manual.busyMessage)
        Button(
            onClick = { manual.preparePatches { vm.open(Route.ManualPatches) } },
            enabled = idle && manual.apk != null,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("Choisir les patchs →") }
    }
}

@Composable
fun ManualPickerScreen(vm: MainViewModel, manual: ManualViewModel) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { manual.loadInstalledApps() }
    val apps = manual.installedApps.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Choisir une application", onBack = { vm.back() })
        OutlinedTextField(query, { query = it }, label = { Text("Rechercher") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        BusyRow(manual.busyMessage)
        LazyColumn(Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { app ->
                ListItem(
                    leadingContent = { AppIcon(app.packageName, app.label, 40.dp) },
                    headlineContent = { Text(app.label) },
                    supportingContent = { Text("${app.packageName} • v${app.versionName ?: "?"}" + if (app.apks.size > 1) " • ${app.apks.size} splits" else "") },
                    modifier = Modifier.clickable(enabled = manual.busyMessage == null) { manual.selectInstalledApp(app) { vm.back() } },
                )
            }
        }
    }
}

@Composable
fun ManualPatchesScreen(vm: MainViewModel, manual: ManualViewModel) {
    val apk = manual.apk ?: return
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Patchs pour ${apk.label}", subtitle = "v${apk.versionName}", onBack = { vm.back() })
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = manual::selectDefaults) { Text("Par défaut") }
            TextButton(onClick = manual::selectAll) { Text("Tout") }
            TextButton(onClick = manual::selectNone) { Text("Aucun") }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(manual.patchItems, key = { it.name }) { item ->
                val versions = item.recommendedVersions
                ListItem(
                    leadingContent = { Checkbox(item.name in manual.selected, { manual.toggle(item.name) }) },
                    headlineContent = { Text(item.name) },
                    supportingContent = {
                        Column {
                            item.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (versions != null) Text(
                                "Versions : " + versions.sortedDescending().take(3).joinToString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (apk.versionName in versions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    modifier = Modifier.clickable { manual.toggle(item.name) },
                )
            }
        }
        Button(
            onClick = { manual.startPatching(); vm.open(Route.ManualRun) },
            enabled = manual.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("Patcher (${manual.selected.size})") }
    }
}

@Composable
fun ManualRunScreen(vm: MainViewModel, manual: ManualViewModel) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val installs by Installer.status.collectAsState()
    val install = installs[ManualViewModel.KEY]
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
        uri?.let(manual::exportTo)
    }
    LaunchedEffect(manual.logs.size) { if (manual.logs.isNotEmpty()) listState.animateScrollToItem(manual.logs.lastIndex) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (manual.isPatching) "Patch en cours…" else "Patch terminé", onBack = if (manual.isPatching) null else ({ vm.back(); Unit }))
        if (manual.isPatching) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
        ) {
            items(manual.logs) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val result = manual.result
            when {
                manual.isPatching -> OutlinedButton(onClick = manual::cancelPatching, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
                result != null -> {
                    when (install) {
                        Installer.Status.InProgress -> Text("Installation…")
                        Installer.Status.Success -> Text("✔ Application installée !", color = MaterialTheme.colorScheme.primary)
                        is Installer.Status.Failure -> Text(install.message, color = MaterialTheme.colorScheme.error)
                        null -> Unit
                    }
                    Button(onClick = manual::install, enabled = install != Installer.Status.InProgress, modifier = Modifier.fillMaxWidth()) { Text("Installer") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { export.launch("${manual.apk?.packageName ?: "app"}-enderrmodded.apk") }, modifier = Modifier.weight(1f)) { Text("Enregistrer") }
                        OutlinedButton(onClick = { shareApk(context, result.apk) }, modifier = Modifier.weight(1f)) { Text("Partager") }
                    }
                }
            }
        }
    }
}
