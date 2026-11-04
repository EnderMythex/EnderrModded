package com.endermythex.enderrmodded

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import com.endermythex.enderrmodded.ui.screens.AppPickerScreen
import com.endermythex.enderrmodded.ui.screens.HomeScreen
import com.endermythex.enderrmodded.ui.screens.PatchesScreen
import com.endermythex.enderrmodded.ui.screens.PatchingScreen
import com.endermythex.enderrmodded.ui.theme.EnderrTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EnderrTheme {
                BackHandler(enabled = viewModel.screen != Screen.Home) { viewModel.back() }

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (viewModel.screen) {
                            Screen.Home -> HomeScreen(viewModel)
                            Screen.AppPicker -> AppPickerScreen(viewModel)
                            Screen.Patches -> PatchesScreen(viewModel)
                            Screen.Patching -> PatchingScreen(viewModel)
                        }
                    }
                }

                viewModel.errorMessage?.let { message ->
                    AlertDialog(
                        onDismissRequest = viewModel::dismissError,
                        confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
                        title = { Text("Erreur") },
                        text = { Text(message) },
                    )
                }
            }
        }
    }
}
