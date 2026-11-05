package com.endermythex.enderrmodded.pipeline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.endermythex.enderrmodded.core.sources.ApkMirrorSource
import java.io.File

enum class Step(val label: String) {
    PATCHES("Mise à jour des patchs"),
    VERSION("Choix de la version"),
    DOWNLOAD("Récupération de l'APK"),
    VERIFY("Vérification de la signature"),
    PREPARE("Préparation (fusion des splits)"),
    PATCH("Patch et signature"),
    INSTALL("Installation"),
}

enum class StepStatus { WAITING, RUNNING, DONE, SKIPPED, FAILED }

data class RunState(
    val packageName: String,
    val steps: Map<Step, StepStatus> = Step.entries.associateWith { StepStatus.WAITING },
    val logs: List<String> = emptyList(),
    val progress: Float? = null,
    val running: Boolean = true,
    val error: String? = null,
    val outputApk: File? = null,
    val version: String? = null,
    val failedPatches: List<String> = emptyList(),
    /** Téléchargement à terminer dans le navigateur intégré (vérification anti-robot du site). */
    val browserPage: ApkMirrorSource.DownloadPage? = null,
) {
    val finishedOk get() = !running && error == null && outputApk != null
}

/** État en mémoire des patchs en cours / terminés, partagé entre les workers et l'UI. */
object PipelineRegistry {
    private val _runs = MutableStateFlow<Map<String, RunState>>(emptyMap())
    val runs: StateFlow<Map<String, RunState>> = _runs.asStateFlow()

    fun start(packageName: String) = _runs.update { it + (packageName to RunState(packageName)) }

    fun mutate(packageName: String, block: (RunState) -> RunState) =
        _runs.update { map -> map + (packageName to block(map[packageName] ?: RunState(packageName))) }

    fun clear(packageName: String) = _runs.update { it - packageName }
}

/** Raccourcis pour un run donné. Les logs sont regroupés pour ne pas faire ramer l'interface. */
class RunReporter(private val packageName: String) {
    private val pending = ArrayList<String>()
    private var lastFlush = 0L

    fun log(line: String) {
        val now = System.currentTimeMillis()
        val toFlush = synchronized(pending) {
            pending += line
            if (now - lastFlush < 250 && pending.size < 200) return
            lastFlush = now
            pending.toList().also { pending.clear() }
        }
        PipelineRegistry.mutate(packageName) { it.copy(logs = (it.logs + toFlush).takeLast(2000)) }
    }

    /** Publie les logs en attente (fin d'étape, fin de run). */
    fun flush() {
        val toFlush = synchronized(pending) { pending.toList().also { pending.clear(); lastFlush = System.currentTimeMillis() } }
        if (toFlush.isNotEmpty()) PipelineRegistry.mutate(packageName) { it.copy(logs = (it.logs + toFlush).takeLast(2000)) }
    }

    fun step(step: Step, status: StepStatus) {
        flush()
        PipelineRegistry.mutate(packageName) {
            it.copy(steps = it.steps + (step to status), progress = if (status == StepStatus.RUNNING) null else it.progress)
        }
    }

    /** Marque l'étape en échec ainsi que toute étape restée "en cours". */
    fun fail(step: Step) {
        flush()
        PipelineRegistry.mutate(packageName) { state ->
            state.copy(
                steps = state.steps.mapValues { (s, status) -> if (s == step || status == StepStatus.RUNNING) StepStatus.FAILED else status },
                progress = null,
            )
        }
    }

    private var lastProgress = -1f

    /** Progression (0..1), mise à jour au plus tous les 1 %. */
    fun progress(value: Float?) {
        if (value != null && value - lastProgress < 0.01f && value < 1f) return
        lastProgress = value ?: -1f
        PipelineRegistry.mutate(packageName) { it.copy(progress = value) }
    }
}
