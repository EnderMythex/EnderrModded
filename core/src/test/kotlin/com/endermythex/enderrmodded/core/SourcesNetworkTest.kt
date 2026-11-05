package com.endermythex.enderrmodded.core

import com.endermythex.enderrmodded.core.sources.ApkMirrorSource
import com.endermythex.enderrmodded.core.sources.ApkSource
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Vérifie les sources réelles (exécuté en CI avec NETWORK_TESTS=1).
 * Télécharge l'app, vérifie le fichier et teste la fusion si c'est un bundle.
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
        val result = try {
            source.download(packageName, versions.first(), dir, abis, ::println) { _, _ -> }
        } catch (e: com.endermythex.enderrmodded.core.sources.BrowserRequiredException) {
            // Comportement attendu : la page finale exige un vrai navigateur (fait dans l'app).
            println("Navigateur requis : ${e.page}")
            assertTrue(e.page.url.contains("/download/"))
            return
        }
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
    
    @Test fun apkMirrorYouTube() = check(ApkMirrorSource(), "com.google.android.youtube")

    @Test
    fun revancedApi() {
        if (!enabled) return
        val release = PatchSources.latest(PatchSources.REVANCED_API, prerelease = false)
        println("API ReVanced : ${release.tag} ${release.assets.map { it.url }}")
        assertTrue(release.assets.isNotEmpty())
        val file = Files.createTempFile("patches", ".rvp").toFile()
        Http.download(release.assets.first().url, file)
        println("Bundle : ${file.length() / 1024} Ko")
        assertTrue(file.length() > 100_000)
        file.delete()
    }

    @Test
    fun gitHubRelease() {
        if (!enabled) return
        val release = GitHub.latestRelease("ReVanced/revanced-patcher")
        println("revanced-patcher : ${release.tag}")
        assertTrue(release.tag.isNotBlank())
    }
}
