package com.endermythex.enderrmodded.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Barre de progression ondulée Material 3 Expressive (indéterminée si [progress] est null). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyLinearProgress(progress: Float?, modifier: Modifier = Modifier.fillMaxWidth()) {
    if (progress == null) LinearWavyProgressIndicator(modifier)
    else LinearWavyProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = modifier)
}

/** Indicateur circulaire ondulé Material 3 Expressive (indéterminé si [progress] est null). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyCircularProgress(progress: Float? = null, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    if (progress == null) CircularWavyProgressIndicator(modifier.size(size))
    else CircularWavyProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = modifier.size(size))
}
