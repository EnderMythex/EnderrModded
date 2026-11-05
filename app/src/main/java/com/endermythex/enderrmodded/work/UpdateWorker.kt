package com.endermythex.enderrmodded.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.endermythex.enderrmodded.EnderrApp
import com.endermythex.enderrmodded.core.Versions
import com.endermythex.enderrmodded.core.sources.AppCatalog
import com.endermythex.enderrmodded.data.Settings
import com.endermythex.enderrmodded.update.SelfUpdater
import java.util.concurrent.TimeUnit

/**
 * Vérification périodique : nouveaux patchs, nouvelles versions des apps patchées, mise à jour d'EnderrModded.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = EnderrApp.instance
        val settings = app.settings
        val repo = app.patches
        val ctx = applicationContext

        // 1. Mise à jour d'EnderrModded
        if (settings.selfUpdateCheck) {
            runCatching { SelfUpdater.check() }.getOrNull()?.let {
                Notifications.update(ctx, 1001, "EnderrModded ${it.tag} disponible", "Ouvre l'app pour mettre à jour.")
            }
        }

        // 2. Nouveaux patchs
        val previousTag = settings.patchesTag
        val newPatches = runCatching {
            if (settings.autoUpdatePatches) repo.update() else { repo.checkForUpdate(); false }
        }.getOrElse { return Result.retry() }
        if (!settings.autoUpdatePatches && repo.updateAvailable) {
            Notifications.update(ctx, 1002, "Nouveaux patchs disponibles", "${repo.latestRelease?.tag} (installé : $previousTag)")
        }
        runCatching { repo.ensureLoaded() }

        // 3. Apps patchées : faut-il les reconstruire ?
        for (patched in app.store.apps) {
            val selected = repo.selectedPatches(patched.packageName, app.store.selection(patched.packageName))
            if (selected.isEmpty()) continue
            val recommended = repo.recommendedVersion(patched.packageName, selected)
            val newVersion = recommended?.takeIf { Versions.isNewer(it, patched.version) }
            val patchesChanged = newPatches && patched.patchesTag != settings.patchesTag
            if (newVersion == null && !patchesChanged) continue

            val name = AppCatalog.nameOf(patched.packageName)
            val reason = newVersion?.let { "version $it" } ?: "patchs ${settings.patchesTag}"
            if (settings.autoRebuild) {
                PatchWorker.enqueue(ctx, patched.packageName)
                Notifications.update(ctx, patched.packageName.hashCode(), "Mise à jour de $name", "Reconstruction automatique ($reason)…")
            } else {
                Notifications.update(ctx, patched.packageName.hashCode(), "Mise à jour disponible pour $name", "Nouveaux éléments : $reason. Ouvre l'app pour patcher.")
            }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "update-check"

        fun schedule(context: Context, settings: Settings) {
            val wm = WorkManager.getInstance(context)
            if (!settings.backgroundChecks) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(settings.checkIntervalHours.toLong().coerceAtLeast(1), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
