package com.endermythex.enderrmodded.ui.screens

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.endermythex.enderrmodded.patcher.PatcherStats
import kotlinx.coroutines.delay

/** Statistiques RAM en direct pendant le patch. */
@Composable
fun MemoryCard() {
    val context = LocalContext.current
    val stats by PatcherStats.memory.collectAsState()
    var device by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    LaunchedEffect(Unit) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        while (true) {
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            device = info.availMem / (1024 * 1024) to info.totalMem / (1024 * 1024)
            delay(1000)
        }
    }

    InfoCard {
        Text("Mémoire", style = MaterialTheme.typography.titleSmall)
        val s = stats
        if (s == null) {
            Text("En attente du patcher…", style = MaterialTheme.typography.bodySmall)
        } else {
            Row { Text(if (s.separateProcess) "Processus de patch" else "Patch (dans l'app)", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("${s.usedMb} / ${s.maxMb} Mo", style = MaterialTheme.typography.bodySmall) }
            WavyLinearProgress(s.usedMb.toFloat() / s.maxMb.coerceAtLeast(1))
            Text("Pic : ${s.peakMb} Mo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        device?.let { (avail, total) ->
            Spacer(Modifier.height(4.dp))
            Row { Text("RAM de l'appareil utilisée", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("%.1f / %.1f Go".format((total - avail) / 1024.0, total / 1024.0), style = MaterialTheme.typography.bodySmall) }
            WavyLinearProgress((total - avail).toFloat() / total.coerceAtLeast(1))
        }
    }
}
