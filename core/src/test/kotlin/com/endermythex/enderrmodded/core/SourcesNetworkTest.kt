package com.endermythex.enderrmodded.core

import com.endermythex.enderrmodded.core.sources.ApkMirrorSource
import com.endermythex.enderrmodded.core.sources.ApkSource
import com.endermythex.enderrmodded.core.sources.UptodownSource
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Vérifie les sources réelles (exécuté en CI avec NETWORK_TESTS=1).
 * Télécharge une petite app, vérifie le fichier et teste la fusion si c'est un bundle.
 */
class SourcesNetworkTest {
    private val enabled = System.getenv("NETWORK_TESTS") == "1"
    private val abis = listOf("arm64-v8a", "armeabi-v7a")

    private fun check(source: ApkSource, packageName: String) {
        if (!enabled) return
        println("=== ${source.displayName} / $packageName")
        val versions = source.versions(packageName)
        println("Versions : ${versions.take(5)}")
        assertTrue(versions.isNotEmpty(), "Aucune version trouvée")
        val dir = Files.createTempDirectory("dl").toFile()
        val result = source.download(packageName, versions.first(), dir, abis, ::println) { _, _ -> }
        println("Fichier : ${result.file.name} ${result.file.length() / 1024} Ko (bundle=${result.isBundle})")
        assertTrue(result.file.length() > 100_000, "Fichier trop petit")
        if (result.isBundle || SplitMerger.isBundle(result.file)) {
            val merged = File(dir, "merged.apk")
            SplitMerger.mergeBundle(result.file, merged, dir, ::println)
            assertTrue(merged.length() > 100_000, "Fusion vide")
        }
        dir.deleteRecursively()
    }

    @Test fun apkMirrorYouTubeMusic() = check(ApkMirrorSource(), "com.google.android.apps.youtube.music")
    @Test fun apkMirrorSearch() = check(ApkMirrorSource(), "com.google.android.apps.recorder")
    @Test fun uptodownYouTubeMusic() = check(UptodownSource(), "com.google.android.apps.youtube.music")

    @Test
    fun gitHubRelease() {
        if (!enabled) return
        val release = GitHub.latestRelease("ReVanced/revanced-patches")
        println("Patchs ReVanced : ${release.tag} ${release.assets.map { it.name }}")
        assertTrue(release.assets.any { it.name.endsWith(".rvp") })
    }
}
