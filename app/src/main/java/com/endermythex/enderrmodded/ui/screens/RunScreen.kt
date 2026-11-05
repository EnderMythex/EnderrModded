package com.endermythex.enderrmodded.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.endermythex.enderrmodded.MainViewModel
import com.endermythex.enderrmodded.core.sources.AppCatalog
import com.endermythex.enderrmodded.installer.Installer
import com.endermythex.enderrmodded.pipeline.PipelineRegistry
import com.endermythex.enderrmodded.pipeline.Step
import com.endermythex.enderrmodded.pipeline.StepStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun RunScreen(vm: MainViewModel, packageName: String) {
    val runs by PipelineRegistry.runs.collectAsState()
    val state = runs[packageName]
    val installs by Installer.status.collectAsState()
    val install = installs[packageName]
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLogs by remember { mutableStateOf(false) }
    val name = AppCatalog.nameOf(packageName)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            name,
            subtitle = when {
                state == null -> "En attente…"
                state.running -> "Patch automatique en cours"
                state.browserPage != null -> "Vérification APKMirror • v${state.browserPage.version}"
                state.error != null -> "Échec"
                else -> "Terminé • v${state.version}"
            },
            onBack = { vm.back() },
        )

        if (state == null) {
            BusyRow("Démarrage…")
            return@Column
        }

        // Avec la vérification APKMirror affichée, on masque les étapes pour lui laisser la place.
        if (state.browserPage == null || state.running) Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Step.entries.forEach { step ->
                val status = state.steps[step] ?: StepStatus.WAITING
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (status) {
                        StepStatus.RUNNING -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        StepStatus.DONE -> Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        StepStatus.FAILED -> Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                        StepStatus.SKIPPED -> Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                        StepStatus.WAITING -> Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Text(
                        step.label + if (status == StepStatus.SKIPPED) " (ignoré)" else "",
                        color = if (status == StepStatus.WAITING) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (status == StepStatus.RUNNING && step == Step.DOWNLOAD) {
                    val p = state.progress
                    if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }

        val browserPage = state.browserPage.takeIf { !state.running }
        if (browserPage == null) state.error?.let {
            InfoCard { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (state.failedPatches.isNotEmpty()) {
            InfoCard { Text("Patchs en échec : ${state.failedPatches.joinToString()}", color = MaterialTheme.colorScheme.error) }
        }
        when (install) {
            Installer.Status.InProgress -> InfoCard { Text("Installation en cours… (confirme si Android le demande)") }
            Installer.Status.Success -> InfoCard { Text("✔ Application installée !", color = MaterialTheme.colorScheme.primary) }
            is Installer.Status.Failure -> InfoCard { Text(install.message, color = MaterialTheme.colorScheme.error) }
            null -> Unit
        }

        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showLogs = !showLogs }) {
                Text(if (showLogs) "Masquer le journal" else "Afficher le journal (${state.logs.size})")
            }
            TextButton(onClick = { copyLogs(context, "$name ($packageName) • v${state.version ?: "?"}", state.logs) }) {
                Text("Copier les logs")
            }
        }
        if (browserPage != null && !showLogs) {
            // Vérification APKMirror affichée directement dans l'app.
            ApkMirrorWebView(browserPage, Modifier.weight(1f)) { file -> vm.autoPatchWithFile(packageName, file) }
        } else if (showLogs) {
            val listState = rememberLazyListState()
            LaunchedEffect(state.logs.size) { if (state.logs.isNotEmpty()) listState.scrollToItem(state.logs.lastIndex) }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
            ) {
                items(state.logs) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            }
        } else {
            Column(Modifier.weight(1f)) {}
        }

        val output = state.outputApk
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
            if (uri != null && output != null) scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out -> output.inputStream().use { it.copyTo(out) } }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.running -> OutlinedButton(onClick = { vm.cancelPatch(packageName) }, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
                output != null -> {
                    Button(
                        onClick = { scope.launch { Installer.install(context, output, packageName) } },
                        enabled = install != Installer.Status.InProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (install == Installer.Status.Success) "Réinstaller" else "Installer") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { export.launch("$packageName-${state.version}-enderrmodded.apk") }, modifier = Modifier.weight(1f)) { Text("Enregistrer") }
                        OutlinedButton(onClick = { shareApk(context, output) }, modifier = Modifier.weight(1f)) { Text("Partager") }
                    }
                }
                browserPage != null -> OutlinedButton(onClick = { vm.autoPatch(packageName); vm.back() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Recommencer")
                }
                else -> Button(onClick = { vm.autoPatch(packageName); vm.back() }, modifier = Modifier.fillMaxWidth()) { Text("Réessayer") }
            }
        }
    }
}

fun shareApk(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/vnd.android.package-archive")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Partager l'APK"))
}
