package com.endermythex.enderrmodded.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.endermythex.enderrmodded.MainActivity
import com.endermythex.enderrmodded.R

object Notifications {
    const val CHANNEL_PROGRESS = "progress"
    const val CHANNEL_UPDATES = "updates"
    const val CHANNEL_INSTALL = "install"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_PROGRESS, "Patch en cours", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_UPDATES, "Mises à jour", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_INSTALL, "Installations", NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    private fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, requestCode: Int) = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(context: Context, title: String, text: String) =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(openApp(context, 1))
            .build()

    fun update(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, id))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, n)
    }

    private fun installId(key: String) = 10_000 + (key.hashCode() and 0xFFFF)

    fun installReady(context: Context, key: String, confirm: Intent) {
        if (!canNotify(context)) return
        val pi = PendingIntent.getActivity(
            context, installId(key), confirm,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_INSTALL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Prêt à installer")
            .setContentText(if (key == "self") "Nouvelle version d'EnderrModded" else "Version patchée de $key")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(installId(key), n)
    }

    fun cancelInstall(context: Context, key: String) = NotificationManagerCompat.from(context).cancel(installId(key))
}
