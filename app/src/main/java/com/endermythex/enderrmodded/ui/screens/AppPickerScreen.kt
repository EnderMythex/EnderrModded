package com.endermythex.enderrmodded.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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

@Composable
fun AppPickerScreen(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val apps = vm.installedApps.filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Choisir une application", onBack = { vm.back() })
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Rechercher") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        BusyRow(vm.busyMessage)
        LazyColumn(Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { app ->
                ListItem(
                    headlineContent = { Text(app.label) },
                    supportingContent = {
                        Text(
                            "${app.packageName} • v${app.versionName ?: "?"}" +
                                if (app.isSplit) " • APK scindé (non supporté)" else ""
                        )
                    },
                    modifier = Modifier.clickable(enabled = vm.busyMessage == null) { vm.selectInstalledApp(app) },
                )
                HorizontalDivider()
            }
        }
    }
}
