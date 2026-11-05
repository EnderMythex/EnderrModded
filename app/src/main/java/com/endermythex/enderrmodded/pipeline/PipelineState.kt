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

/** Raccourcis pour un run donné. */
class RunReporter(private val packageName: String) {
    fun log(line: String) = PipelineRegistry.mutate(packageName) { it.copy(logs = (it.logs + line).takeLast(500)) }
    fun step(step: Step, status: StepStatus) = PipelineRegistry.mutate(packageName) {
        it.copy(steps = it.steps + (step to status), progress = if (status == StepStatus.RUNNING) null else it.progress)
    }
    fun progress(value: Float?) = PipelineRegistry.mutate(packageName) { it.copy(progress = value) }
}
