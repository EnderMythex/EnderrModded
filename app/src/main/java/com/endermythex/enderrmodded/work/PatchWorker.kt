package com.endermythex.enderrmodded.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.endermythex.enderrmodded.core.sources.AppCatalog
import com.endermythex.enderrmodded.installer.Installer
import com.endermythex.enderrmodded.pipeline.AutoPatcher

/** Exécute le pipeline full auto en tâche de fond (survit à la fermeture de l'écran). */
class PatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val pkg = inputData.getString(KEY_PACKAGE).orEmpty()
        val notification = Notifications.progress(applicationContext, "Patch de ${AppCatalog.nameOf(pkg)}", "En cours…")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        val pkg = inputData.getString(KEY_PACKAGE) ?: return Result.failure()
        val version = inputData.getString(KEY_VERSION)
        runCatching { setForeground(getForegroundInfo()) }
        val state = AutoPatcher(applicationContext).run(pkg, version)
        val name = AppCatalog.nameOf(pkg)
        if (!Installer.appInForeground) {
            if (state.finishedOk) {
                Notifications.update(applicationContext, pkg.hashCode(), "$name patché", "Version ${state.version} prête.")
            } else {
                Notifications.update(applicationContext, pkg.hashCode(), "Échec du patch de $name", state.error ?: "Erreur inconnue")
            }
        }
        return if (state.finishedOk) Result.success() else Result.failure()
    }

    companion object {
        private const val KEY_PACKAGE = "package"
        private const val KEY_VERSION = "version"
        private const val NOTIFICATION_ID = 42

        fun workName(packageName: String) = "patch-$packageName"

        fun enqueue(context: Context, packageName: String, version: String? = null) {
            val request = OneTimeWorkRequestBuilder<PatchWorker>()
                .setInputData(workDataOf(KEY_PACKAGE to packageName, KEY_VERSION to version))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("patch")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(packageName), ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, packageName: String) =
            WorkManager.getInstance(context).cancelUniqueWork(workName(packageName))
    }
}
