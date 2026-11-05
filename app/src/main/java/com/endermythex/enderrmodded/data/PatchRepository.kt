package com.endermythex.enderrmodded.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patch.loadPatches
import com.endermythex.enderrmodded.core.GitHub
import com.endermythex.enderrmodded.core.Http
import com.endermythex.enderrmodded.core.Versions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Bundle de patchs (.rvp) : téléchargement depuis GitHub, import, mise à jour et chargement.
 */
class PatchRepository(private val context: Context, private val settings: Settings) {
    private val dir = File(context.filesDir, "bundles").apply { mkdirs() }
    val bundleFile = File(dir, "patches.rvp")
    private val mutex = Mutex()

    var patches by mutableStateOf<Set<Patch>>(emptySet()); private set
    var loadError by mutableStateOf<String?>(null); private set
    var latestRelease by mutableStateOf<GitHub.Release?>(null); private set

    val hasBundle get() = bundleFile.exists()
    val installedTag get() = settings.patchesTag.ifBlank { null }
    val updateAvailable get() = latestRelease?.let { it.tag != installedTag } == true

    /** Charge le bundle en mémoire s'il ne l'est pas déjà. */
    suspend fun ensureLoaded(): Set<Patch> = mutex.withLock {
        if (patches.isEmpty() && hasBundle) loadLocked()
        patches
    }

    private suspend fun loadLocked() = withContext(Dispatchers.IO) {
        bundleFile.setReadOnly() // Android 14+ refuse le code dynamique modifiable
        var failure: Throwable? = null
        val loaded = loadPatches(bundleFile) { _, t -> failure = t }
        failure?.let {
            loadError = it.message ?: it.toString()
            throw IllegalStateException("Échec du chargement du bundle : ${it.message}", it)
        }
        loadError = null
        patches = loaded.toSet()
    }

    /** Interroge GitHub pour connaître la dernière release. */
    suspend fun checkForUpdate(): GitHub.Release = withContext(Dispatchers.IO) {
        GitHub.latestRelease(settings.patchesRepo, settings.patchesPrerelease).also { latestRelease = it }
    }

    /**
     * Télécharge la dernière release si elle est plus récente (ou si [force]).
     * @return true si un nouveau bundle a été installé.
     */
    suspend fun update(force: Boolean = false, log: (String) -> Unit = {}): Boolean {
        val release = checkForUpdate()
        if (!force && hasBundle && release.tag == installedTag) {
            log("Patchs à jour (${release.tag})")
            ensureLoaded()
            return false
        }
        val asset = release.assets.firstOrNull { it.name.endsWith(".rvp") }
            ?: error("Aucun fichier .rvp dans la release ${release.tag} de ${settings.patchesRepo}")
        log("Téléchargement des patchs ${release.tag}…")
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val tmp = File(dir, "download.rvp")
                Http.download(asset.url, tmp, mapOf("Accept" to "application/octet-stream"))
                tmp.inputStream().use { save(it) }
                tmp.delete()
            }
            settings.patchesTag = release.tag
            patches = emptySet()
            loadLocked()
        }
        log("Patchs ${release.tag} installés (${patches.size} patchs)")
        return true
    }

    suspend fun importFrom(uri: Uri) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri) ?: error("Impossible d'ouvrir le fichier")
            input.use { save(it) }
        }
        settings.patchesTag = "local"
        patches = emptySet()
        loadLocked()
    }

    private fun save(input: InputStream) {
        val tmp = File(dir, "patches.rvp.tmp")
        tmp.delete()
        tmp.outputStream().use { input.copyTo(it) }
        if (bundleFile.exists()) {
            bundleFile.setWritable(true)
            bundleFile.delete()
        }
        check(tmp.renameTo(bundleFile)) { "Impossible d'enregistrer le bundle" }
        bundleFile.setReadOnly()
    }

    // ---- Requêtes sur les patchs ----

    fun compatiblePatches(packageName: String): List<Patch> =
        patches.filter { it.isCompatibleWith(packageName) }.sortedBy { it.name.orEmpty().lowercase() }

    /** Paquets ciblés explicitement par au moins un patch. */
    fun supportedPackages(): Set<String> =
        patches.flatMap { p -> p.compatiblePackages?.map { it.first }.orEmpty() }.toSet()

    /** Patchs sélectionnés pour une app : sélection enregistrée, sinon patchs activés par défaut. */
    fun selectedPatches(packageName: String, saved: Set<String>?): Set<Patch> {
        val compatible = compatiblePatches(packageName)
        return if (saved == null) compatible.filter { it.use }.toSet()
        else compatible.filter { it.name in saved }.toSet()
    }

    /** Version recommandée pour un ensemble de patchs (null = n'importe laquelle). */
    fun recommendedVersion(packageName: String, selected: Collection<Patch>): String? =
        Versions.recommended(selected.map { it.versionsFor(packageName) })
}

fun Patch.isCompatibleWith(packageName: String): Boolean =
    compatiblePackages?.any { (name, _) -> name == packageName } ?: true

fun Patch.versionsFor(packageName: String): Set<String>? =
    compatiblePackages?.firstOrNull { (name, _) -> name == packageName }?.second
