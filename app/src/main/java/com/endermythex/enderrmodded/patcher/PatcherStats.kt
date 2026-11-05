package com.endermythex.enderrmodded.patcher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Statistiques mémoire du patch en cours, affichées sur l'écran de progression. */
object PatcherStats {
    data class Memory(val usedMb: Int, val maxMb: Int, val peakMb: Int, val separateProcess: Boolean)

    private val _memory = MutableStateFlow<Memory?>(null)
    val memory = _memory.asStateFlow()

    fun update(usedMb: Int, maxMb: Int, separateProcess: Boolean) {
        val peak = maxOf(usedMb, _memory.value?.peakMb ?: 0)
        _memory.value = Memory(usedMb, maxMb, peak, separateProcess)
    }

    fun reset() { _memory.value = null }

    /** Abaisse la priorité du thread courant (et des threads/processus qu'il crée) pour garder l'interface fluide. */
    fun lowerCurrentThreadPriority() {
        runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
        // Les nouveaux threads Java héritent de cette priorité (4 ≈ nice 10 sur ART).
        runCatching { Thread.currentThread().priority = 4 }
    }
}
