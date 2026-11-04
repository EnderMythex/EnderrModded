package com.endermythex.enderrmodded.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.endermythex.enderrmodded.MainViewModel
import com.endermythex.enderrmodded.Screen

@Composable
fun HomeScreen(vm: MainViewModel) {
    val pickBundle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBundle)
    }
    val pickApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importApk)
    }
    val idle = vm.busyMessage == null

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("EnderrModded", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Patche tes applications Android avec des bundles de patchs compatibles ReVanced.",
            style = MaterialTheme.typography.bodyMedium,
        )

        StepCard("1. Bundle de patchs") {
            Text(vm.bundleDescription ?: "Aucun bundle installé")
            OutlinedTextField(
                value = vm.bundleRepo,
                onValueChange = { vm.bundleRepo = it },
                label = { Text("Dépôt GitHub (owner/repo)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::downloadBundle, enabled = idle) { Text("Télécharger") }
                OutlinedButton(onClick = { pickBundle.launch(arrayOf("*/*")) }, enabled = idle) {
                    Text("Importer un .rvp")
                }
            }
        }

        StepCard("2. Application à patcher") {
            val apk = vm.apk
            if (apk == null) {
                Text("Aucun APK sélectionné")
            } else {
                Text(apk.label, style = MaterialTheme.typography.titleMedium)
                Text("${apk.packageName} • v${apk.versionName ?: "?"}", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.navigate(Screen.AppPicker) }, enabled = idle) { Text("App installée") }
                OutlinedButton(
                    onClick = { pickApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) },
                    enabled = idle,
                ) { Text("Fichier APK") }
            }
        }

        Button(
            onClick = vm::openPatches,
            enabled = idle && vm.apk != null && vm.bundleDescription != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("3. Choisir les patchs →") }

        BusyRow(vm.busyMessage)

        Text(
            "Astuce : l'APK patché est signé avec une autre clé que l'original. " +
                "Désinstalle l'app d'origine avant d'installer la version patchée " +
                "(sauf si un patch renomme le paquet).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StepCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
