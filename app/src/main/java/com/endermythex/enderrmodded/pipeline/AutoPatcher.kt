package com.endermythex.enderrmodded.pipeline

import android.content.Context
import com.endermythex.enderrmodded.EnderrApp
import com.endermythex.enderrmodded.core.sources.BrowserRequiredException
import com.endermythex.enderrmodded.data.PatchedApp
import com.endermythex.enderrmodded.installer.Installer
import com.endermythex.enderrmodded.patcher.PatcherEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Mode full auto : patchs à jour → version recommandée → APK officiel → patch → signature → installation.
 */
class AutoPatcher(private val context: Context) {
    private val app = EnderrApp.instance
    private val settings = app.settings
    private val repository = app.patches
    private val store = app.store
    private val provider = ApkProvider(context, settings, store)
    private val engine = PatcherEngine(context)

    /**
     * @param forcedVersion version imposée par l'utilisateur (sinon recommandée/dernière).
     * @param install installer automatiquement à la fin.
     */
    suspend fun run(
        packageName: String,
        forcedVersion: String? = null,
        install: Boolean = settings.autoInstall,
        providedApk: File? = null,
    ): RunState {
        val report = RunReporter(packageName)
        PipelineRegistry.start(packageName)
        val workDir = File(context.cacheDir, "auto/$packageName").apply { deleteRecursively(); mkdirs() }
        var current: Step = Step.PATCHES
        try {
            // 1. Patchs
            report.step(Step.PATCHES, StepStatus.RUNNING)
            if (settings.autoUpdatePatches || !repository.hasBundle) {
                runCatching { repository.update(log = report::log) }.onFailure {
                    if (!repository.hasBundle) throw it
                    report.log("⚠ Mise à jour des patchs impossible (${it.message}), utilisation du bundle local")
                }
            }
            repository.ensureLoaded()
            val selected = repository.selectedPatches(packageName, store.selection(packageName))
            check(selected.isNotEmpty()) { "Aucun patch sélectionné pour $packageName" }
            report.log("${selected.size} patch(s) sélectionné(s) • bundle ${settings.patchesTag}")
            report.step(Step.PATCHES, StepStatus.DONE)

            // 2. Version
            current = Step.VERSION
            report.step(Step.VERSION, StepStatus.RUNNING)
            val recommended = repository.recommendedVersion(packageName, selected)
            val target = when {
                forcedVersion == LATEST -> null
                forcedVersion != null -> forcedVersion
                settings.useLatestVersion -> null
                else -> recommended
            }
            report.log(
                when {
                    forcedVersion == LATEST -> "Dernière version disponible demandée"
                    forcedVersion != null -> "Version choisie : $forcedVersion"
                    target != null -> "Version recommandée par les patchs : $target"
                    else -> "Aucune contrainte de version : dernière version disponible"
                },
            )
            report.step(Step.VERSION, StepStatus.DONE)

            // 3-5. Récupération, vérification, fusion
            current = Step.DOWNLOAD
            val (input, version) = withContext(Dispatchers.IO) {
                if (providedApk != null) provider.prepareProvided(packageName, providedApk, workDir, report)
                else provider.obtain(packageName, target, workDir, report)
            }
            PipelineRegistry.mutate(packageName) { it.copy(version = version) }

            // 6. Patch + signature
            current = Step.PATCH
            report.step(Step.PATCH, StepStatus.RUNNING)
            val result = engine.patch(input, selected, "$packageName.apk", report::log)
            input.delete()
            val patchedInfo = context.packageManager.getPackageArchiveInfo(result.apk.absolutePath, 0)
            Signatures.ofArchive(context.packageManager, result.apk.absolutePath).firstOrNull()
                ?.let { settings.ownCertificate = it }
            store.record(
                PatchedApp(
                    packageName = packageName,
                    patchedPackageName = patchedInfo?.packageName ?: packageName,
                    version = version,
                    patchesTag = settings.patchesTag,
                    patches = selected.mapNotNull { it.name },
                    patchedAt = System.currentTimeMillis(),
                    apkPath = result.apk.absolutePath,
                ),
            )
            if (result.failedPatches.isNotEmpty()) report.log("⚠ Patchs en échec : ${result.failedPatches.joinToString()}")
            report.step(Step.PATCH, StepStatus.DONE)
            PipelineRegistry.mutate(packageName) { it.copy(outputApk = result.apk, failedPatches = result.failedPatches) }

            // 7. Installation
            current = Step.INSTALL
            if (install) {
                report.step(Step.INSTALL, StepStatus.RUNNING)
                report.log("Installation…")
                Installer.install(context, result.apk, packageName)
                report.step(Step.INSTALL, StepStatus.DONE)
            } else {
                report.step(Step.INSTALL, StepStatus.SKIPPED)
            }
            report.log("Terminé ✔")
            PipelineRegistry.mutate(packageName) { it.copy(running = false) }
        } catch (e: BrowserRequiredException) {
            report.fail(current)
            report.log("APKMirror demande une vérification : termine le téléchargement dans le navigateur intégré.")
            PipelineRegistry.mutate(packageName) {
                it.copy(running = false, error = "Action requise : télécharge l'APK dans le navigateur", browserPage = e.page)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            report.fail(current)
            PipelineRegistry.mutate(packageName) { it.copy(running = false, error = "Annulé") }
            throw e
        } catch (e: Throwable) {
            report.fail(current)
            report.log("✘ ${e.message ?: e}")
            PipelineRegistry.mutate(packageName) { it.copy(running = false, error = e.message ?: e.toString()) }
        } finally {
            workDir.deleteRecursively()
        }
        return PipelineRegistry.runs.value.getValue(packageName)
    }

    companion object {
        /** Valeur spéciale : télécharger la dernière version disponible. */
        const val LATEST = "latest"
    }
}
