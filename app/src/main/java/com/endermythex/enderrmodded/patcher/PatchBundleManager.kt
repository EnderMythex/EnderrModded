package com.endermythex.enderrmodded.patcher

import android.content.Context
import android.net.Uri
import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patch.loadPatches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.jar.JarFile

/**
 * Gère le bundle de patchs (.rvp) : import, téléchargement et chargement.
 */
class PatchBundleManager(private val context: Context) {
    private val bundleDir = File(context.filesDir, "bundles").apply { mkdirs() }
    val bundleFile = File(bundleDir, "patches.rvp")

    val hasBundle get() = bundleFile.exists()

    data class BundleInfo(val name: String?, val version: String?)

    fun info(): BundleInfo? = runCatching {
        JarFile(bundleFile).use { jar ->
            val attrs = jar.manifest?.mainAttributes
            BundleInfo(attrs?.getValue("Name") ?: attrs?.getValue("name"), attrs?.getValue("Version") ?: attrs?.getValue("version"))
        }
    }.getOrNull()

    suspend fun importFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Impossible d'ouvrir le fichier sélectionné")
        input.use { save(it) }
    }

    /**
     * Télécharge le premier fichier .rvp de la dernière release GitHub d'un dépôt "owner/repo".
     * @return le nom de la release téléchargée.
     */
    suspend fun downloadLatest(repo: String): String = withContext(Dispatchers.IO) {
        val release = JSONObject(httpGet("https://api.github.com/repos/$repo/releases/latest").use { it.readBytes().decodeToString() })
        val assets = release.getJSONArray("assets")
        val asset = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".rvp") }
            ?: error("Aucun fichier .rvp dans la dernière release de $repo")

        httpGet(asset.getString("browser_download_url")).use { save(it) }
        release.optString("tag_name", asset.getString("name"))
    }

    private fun httpGet(url: String): InputStream {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
            setRequestProperty("User-Agent", "EnderrModded")
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        val code = connection.responseCode
        if (code !in 200..299) error("Erreur HTTP $code pour $url")
        return connection.inputStream
    }

    private fun save(input: InputStream) {
        val tmp = File(bundleDir, "patches.rvp.tmp")
        tmp.delete()
        tmp.outputStream().use { input.copyTo(it) }
        if (bundleFile.exists()) {
            bundleFile.setWritable(true)
            bundleFile.delete()
        }
        if (!tmp.renameTo(bundleFile)) error("Impossible d'enregistrer le bundle")
        // Android 14+ refuse de charger du code dynamique depuis un fichier modifiable.
        bundleFile.setReadOnly()
    }

    /** Charge tous les patchs nommés contenus dans le bundle. */
    suspend fun load(): Set<Patch> = withContext(Dispatchers.IO) {
        check(hasBundle) { "Aucun bundle de patchs installé" }
        bundleFile.setReadOnly()
        var failure: Throwable? = null
        val patches = loadPatches(bundleFile) { _, throwable -> failure = throwable }
        failure?.let { throw IllegalStateException("Échec du chargement du bundle : ${it.message}", it) }
        patches.toSet()
    }
}

/** Vrai si le patch peut s'appliquer au paquet (patch universel ou paquet listé). */
fun Patch.isCompatibleWith(packageName: String): Boolean =
    compatiblePackages?.any { (name, _) -> name == packageName } ?: true

/** Versions recommandées par le patch pour ce paquet, ou null si toutes les versions conviennent. */
fun Patch.versionsFor(packageName: String): Set<String>? =
    compatiblePackages?.firstOrNull { (name, _) -> name == packageName }?.second
