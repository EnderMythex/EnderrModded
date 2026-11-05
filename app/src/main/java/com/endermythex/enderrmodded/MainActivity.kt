package com.endermythex.enderrmodded

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import com.endermythex.enderrmodded.installer.Installer
import com.endermythex.enderrmodded.ui.screens.AppDetailScreen
import com.endermythex.enderrmodded.ui.screens.AppsScreen
import com.endermythex.enderrmodded.ui.screens.ManualHomeScreen
import com.endermythex.enderrmodded.ui.screens.ManualPatchesScreen
import com.endermythex.enderrmodded.ui.screens.ManualPickerScreen
import com.endermythex.enderrmodded.ui.screens.ManualRunScreen
import com.endermythex.enderrmodded.ui.screens.PatchesTabScreen
import com.endermythex.enderrmodded.ui.screens.RunScreen
import com.endermythex.enderrmodded.ui.screens.SettingsScreen
import com.endermythex.enderrmodded.ui.theme.EnderrTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val manual: ManualViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            EnderrTheme(theme = vm.settings.theme, dynamicColor = vm.settings.dynamicColor) {
                BackHandler(enabled = vm.stack.isNotEmpty() && !(vm.route == Route.ManualRun && manual.isPatching)) { vm.back() }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (vm.stack.isEmpty()) {
                            NavigationBar {
                                Tab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = vm.tab == tab,
                                        onClick = { vm.selectTab(tab) },
                                        icon = {
                                            Icon(
                                                when (tab) {
                                                    Tab.Apps -> Icons.Default.Home
                                                    Tab.Patches -> Icons.Default.Build
                                                    Tab.Settings -> Icons.Default.Settings
                                                },
                                                contentDescription = null,
                                            )
                                        },
                                        label = { Text(tab.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (val route = vm.route) {
                            null -> when (vm.tab) {
                                Tab.Apps -> AppsScreen(vm)
                                Tab.Patches -> PatchesTabScreen(vm)
                                Tab.Settings -> SettingsScreen(vm)
                            }
                            is Route.AppDetail -> AppDetailScreen(vm, route.packageName)
                            is Route.Run -> RunScreen(vm, route.packageName)
                            Route.ManualHome -> ManualHomeScreen(vm, manual)
                            Route.ManualPicker -> ManualPickerScreen(vm, manual)
                            Route.ManualPatches -> ManualPatchesScreen(vm, manual)
                            Route.ManualRun -> ManualRunScreen(vm, manual)
                        }
                    }
                }

                val error = vm.errorMessage ?: manual.errorMessage
                if (error != null) {
                    val dismiss = { vm.dismissError(); manual.dismissError() }
                    AlertDialog(
                        onDismissRequest = dismiss,
                        confirmButton = { TextButton(onClick = dismiss) { Text("OK") } },
                        text = { Text(error) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Installer.appInForeground = true
        vm.refreshInstalled()
    }

    override fun onPause() {
        Installer.appInForeground = false
        super.onPause()
    }
}
