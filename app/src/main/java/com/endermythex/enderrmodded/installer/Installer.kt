package com.endermythex.enderrmodded.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.endermythex.enderrmodded.work.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/** Installe des APK via PackageInstaller (silencieux pour les mises à jour sur Android 12+). */
object Installer {
    sealed interface Status {
        data object InProgress : Status
        data object Success : Status
        data class Failure(val message: String) : Status
    }

    /** Statut par clé (nom de paquet d'origine, ou "self" pour la mise à jour de l'app). */
    private val _status = MutableStateFlow<Map<String, Status>>(emptyMap())
    val status = _status.asStateFlow()

    /** Vrai quand une activité de l'app est au premier plan. */
    @Volatile var appInForeground = false

    internal fun update(key: String, status: Status) = _status.update { it + (key to status) }
    fun reset(key: String) = _status.update { it - key }

    suspend fun install(context: Context, apk: File, key: String) = withContext(Dispatchers.IO) {
        update(key, Status.InProgress)
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setSize(apk.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val intent = Intent(context, InstallReceiver::class.java).putExtra(EXTRA_KEY, key)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            update(key, Status.Failure(e.message ?: e.toString()))
        }
    }

    const val EXTRA_KEY = "installer_key"
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(Installer.EXTRA_KEY) ?: "?"
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                } ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Installer.appInForeground) {
                    context.startActivity(confirm)
                } else {
                    // Android bloque l'ouverture d'écrans depuis l'arrière-plan : on passe par une notification.
                    Notifications.installReady(context, key, confirm)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Installer.update(key, Installer.Status.Success)
                Notifications.cancelInstall(context, key)
            }

            else -> {
                val raw = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                val message = when (status) {
                    PackageInstaller.STATUS_FAILURE_CONFLICT ->
                        "Conflit de signature : désinstalle d'abord la version existante. $raw"
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation annulée."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "APK incompatible avec cet appareil. $raw"
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Espace de stockage insuffisant."
                    else -> "Échec de l'installation ($status). $raw"
                }
                Installer.update(key, Installer.Status.Failure(message.trim()))
            }
        }
    }
}
