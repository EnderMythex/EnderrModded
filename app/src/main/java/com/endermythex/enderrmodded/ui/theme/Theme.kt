package com.endermythex.enderrmodded.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB388FF),
    onPrimary = Color(0xFF21005D),
    secondary = Color(0xFF00E5A0),
    tertiary = Color(0xFF7FD8FF),
    background = Color(0xFF120B24),
    surface = Color(0xFF120B24),
    surfaceVariant = Color(0xFF2A1F45),
    surfaceContainer = Color(0xFF1C1433),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A3FD1),
    secondary = Color(0xFF00897B),
    tertiary = Color(0xFF00658A),
)

@Composable
fun EnderrTheme(theme: String = "system", dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
