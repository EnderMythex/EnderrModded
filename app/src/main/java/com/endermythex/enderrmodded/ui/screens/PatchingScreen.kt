package com.endermythex.enderrmodded.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.endermythex.enderrmodded.MainViewModel
import com.endermythex.enderrmodded.installer.Installer

@Composable
fun PatchingScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val installStatus by Installer.status.collectAsState()
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri -> uri?.let(vm::exportTo) }

    LaunchedEffect(vm.logs.size) {
        if (vm.logs.isNotEmpty()) listState.animateScrollToItem(vm.logs.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            if (vm.isPatching) "Patch en cours…" else "Patch terminé",
            onBack = if (vm.isPatching) null else ({ vm.back(); Unit }),
        )
        if (vm.isPatching) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
        ) {
            itemsIndexed(vm.logs) { _, line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val result = vm.result
            when {
                vm.isPatching -> OutlinedButton(onClick = vm::cancelPatching, modifier = Modifier.fillMaxWidth()) {
                    Text("Annuler")
                }

                result != null -> {
                    if (result.failedPatches.isNotEmpty()) {
                        Text(
                            "${result.failedPatches.size} patch(s) en échec : ${result.failedPatches.joinToString()}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    when (val status = installStatus) {
                        Installer.Status.InProgress -> Text("Installation…")
                        Installer.Status.Success -> Text("✔ Application installée !", color = MaterialTheme.colorScheme.primary)
                        is Installer.Status.Failure -> Text(status.message, color = MaterialTheme.colorScheme.error)
                        Installer.Status.Idle -> Unit
                    }
                    Button(
                        onClick = vm::install,
                        enabled = installStatus != Installer.Status.InProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Installer") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { export.launch("${vm.apk?.packageName ?: "app"}-enderrmodded.apk") },
                            modifier = Modifier.weight(1f),
                        ) { Text("Enregistrer") }
                        OutlinedButton(
                            onClick = {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", result.apk)
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("application/vnd.android.package-archive")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, "Partager l'APK"))
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Partager") }
                    }
                }
            }
        }
    }
}
