package com.endermythex.enderrmodded.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.endermythex.enderrmodded.BuildConfig
import com.endermythex.enderrmodded.MainViewModel
import com.endermythex.enderrmodded.data.Settings

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s = vm.settings
    var editRepo by remember { mutableStateOf(false) }
    val exportKey = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let(vm::exportKeystore)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Réglages")
        BusyRow(vm.busyMessage)

        SectionTitle("Patchs")
        ClickRow("Source des patchs", "${s.patchesRepo} (dépôt GitHub)") { editRepo = true }
        SwitchRow("Mise à jour automatique des patchs", "Télécharge les nouveaux patchs dès qu'ils sortent", s.autoUpdatePatches) { s.autoUpdatePatches = it }
        SwitchRow("Versions de test (pré-releases)", "Patchs en développement, moins stables", s.patchesPrerelease) { s.patchesPrerelease = it }

        SectionTitle("Téléchargement des APK")
        SwitchRow("App installée sur l'appareil", "Utilise l'app du Play Store si la version correspond (le plus sûr)", "device" in s.sources) { s.setSourceEnabled("device", it) }
        SwitchRow("APKMirror", "APK vérifiés et signés par les développeurs officiels", "apkmirror" in s.sources) { s.setSourceEnabled("apkmirror", it) }
        SwitchRow("Uptodown", "Source de secours", "uptodown" in s.sources) { s.setSourceEnabled("uptodown", it) }
        SwitchRow(
            "Vérifier la signature",
            "Refuse un APK dont le certificat ne correspond pas à celui du développeur (fortement conseillé)",
            s.verifySignatures,
        ) { s.verifySignatures = it }
        SwitchRow("Toujours la dernière version", "Ignore la version recommandée par les patchs (déconseillé)", s.useLatestVersion) { s.useLatestVersion = it }
        SwitchRow("Garder les APK téléchargés", null, s.keepDownloads) { s.keepDownloads = it }

        SectionTitle("Installation")
        SwitchRow("Installer automatiquement", "Lance l'installation à la fin du patch", s.autoInstall) { s.autoInstall = it }

        SectionTitle("Mises à jour automatiques")
        SwitchRow("Vérifier en arrière-plan", "Nouveaux patchs, nouvelles versions des apps patchées", s.backgroundChecks) {
            s.backgroundChecks = it
            vm.rescheduleChecks()
        }
        ListItem(
            headlineContent = { Text("Fréquence") },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6, 12, 24).forEach { h ->
                        FilterChip(s.checkIntervalHours == h, { s.checkIntervalHours = h; vm.rescheduleChecks() }, label = { Text("${h} h") }, enabled = s.backgroundChecks)
                    }
                }
            },
        )
        SwitchRow(
            "Reconstruire automatiquement",
            "Repatche tout seul les apps quand une mise à jour est dispo (mode full auto)",
            s.autoRebuild, enabled = s.backgroundChecks,
        ) { s.autoRebuild = it }
        SwitchRow("Mises à jour d'EnderrModded", "Vérifie les nouvelles versions de l'app", s.selfUpdateCheck) { s.selfUpdateCheck = it }

        SectionTitle("Apparence")
        ListItem(
            headlineContent = { Text("Thème") },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "Système", "light" to "Clair", "dark" to "Sombre").forEach { (id, label) ->
                        FilterChip(s.theme == id, { s.theme = id }, label = { Text(label) })
                    }
                }
            },
        )
        SwitchRow("Couleurs dynamiques", "Couleurs du fond d'écran (Android 12+)", s.dynamicColor) { s.dynamicColor = it }

        SectionTitle("Signature & sécurité")
        ClickRow("Exporter la clé de signature", "Sauvegarde-la pour pouvoir mettre à jour tes apps patchées après une réinstallation") {
            exportKey.launch("enderrmodded.keystore")
        }
        ClickRow("Oublier les certificats enregistrés", "À utiliser seulement si un développeur a changé de clé") {
            vm.store.clearPins()
        }

        SectionTitle("Avancé")
        ClickRow("Vider le cache", "Supprime les APK téléchargés et générés") { vm.clearCache() }

        SectionTitle("À propos")
        ClickRow("EnderrModded ${BuildConfig.VERSION_NAME}", "Rechercher une mise à jour") { vm.checkSelfUpdate() }
        ListItem(
            headlineContent = { Text("Licence GPLv3") },
            supportingContent = { Text("Basé sur revanced-patcher et ARSCLib. Utilise cette app uniquement sur des apps que tu as le droit de modifier.") },
        )
        Column(Modifier.padding(bottom = 24.dp)) {}
    }

    if (editRepo) {
        var value by remember { mutableStateOf(s.patchesRepo) }
        AlertDialog(
            onDismissRequest = { editRepo = false },
            title = { Text("Source des patchs") },
            text = {
                OutlinedTextField(value, { value = it.trim() }, label = { Text("owner/repo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (value.isNotBlank() && value != s.patchesRepo) {
                        s.patchesRepo = value
                        vm.updatePatches(force = true)
                    }
                    editRepo = false
                }) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { value = Settings.DEFAULT_PATCHES_REPO }) { Text("Défaut") }
            },
        )
    }
}
