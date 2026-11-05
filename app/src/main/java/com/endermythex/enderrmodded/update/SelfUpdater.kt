package com.endermythex.enderrmodded.update

import android.content.Context
import com.endermythex.enderrmodded.BuildConfig
import com.endermythex.enderrmodded.core.GitHub
import com.endermythex.enderrmodded.core.Http
import com.endermythex.enderrmodded.core.Versions
import com.endermythex.enderrmodded.installer.Installer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Mise à jour d'EnderrModded depuis les releases GitHub du projet. */
object SelfUpdater {
    const val INSTALL_KEY = "self"

    /** Release plus récente que la version installée, ou null. */
    suspend fun check(): GitHub.Release? = withContext(Dispatchers.IO) {
        val release = GitHub.latestRelease(BuildConfig.UPDATE_REPO)
        release.takeIf {
            Versions.isNewer(it.tag, BuildConfig.VERSION_NAME) && it.assets.any { a -> a.name.endsWith(".apk") }
        }
    }

    suspend fun downloadAndInstall(context: Context, release: GitHub.Release, onProgress: (Float) -> Unit) {
        val asset = release.assets.first { it.name.endsWith(".apk") }
        val file = File(context.filesDir, "output/update.apk")
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            Http.download(asset.url, file, mapOf("Accept" to "application/octet-stream")) { done, total ->
                if (total > 0) onProgress(done.toFloat() / total)
            }
        }
        Installer.install(context, file, INSTALL_KEY)
    }
}
