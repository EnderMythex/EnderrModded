package com.endermythex.enderrmodded.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Installe un APK via l'API PackageInstaller. */
object Installer {
    sealed interface Status {
        data object Idle : Status
        data object InProgress : Status
        data object Success : Status
        data class Failure(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status = _status.asStateFlow()

    fun reset() { _status.value = Status.Idle }

    internal fun update(status: Status) { _status.value = status }

    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        _status.value = Status.InProgress
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
                val intent = Intent(context, InstallReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            _status.value = Status.Failure(e.message ?: e.toString())
        }
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> Installer.update(Installer.Status.Success)

            else -> {
                val raw = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                val message = when (status) {
                    PackageInstaller.STATUS_FAILURE_CONFLICT ->
                        "Conflit de signature : désinstalle d'abord l'app d'origine. $raw"
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation annulée."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "APK incompatible avec cet appareil. $raw"
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Espace de stockage insuffisant."
                    else -> "Échec de l'installation ($status). $raw"
                }
                Installer.update(Installer.Status.Failure(message.trim()))
            }
        }
    }
}
