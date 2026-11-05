package com.endermythex.enderrmodded

import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.revanced.patcher.patch.Patch
import com.endermythex.enderrmodded.core.SplitMerger
import com.endermythex.enderrmodded.data.versionsFor
import com.endermythex.enderrmodded.installer.Installer
import com.endermythex.enderrmodded.patcher.PatcherEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ApkSource(val file: File, val label: String, val packageName: String, val versionName: String?)

data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String?,
    val apks: List<String>,
)

data class PatchItem(
    val patch: Patch,
    val name: String,
    val description: String?,
    val recommendedVersions: Set<String>?,
    val enabledByDefault: Boolean,
)

/** Patch manuel d'un fichier APK / bundle ou d'une app installée. */
class ManualViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as EnderrApp).patches
    private val engine = PatcherEngine(app)
    private val workDir get() = File(getApplication<Application>().cacheDir, "manual")
    private val inputApk get() = File(workDir, "input.apk")

    var apk by mutableStateOf<ApkSource?>(null); private set
    var busyMessage by mutableStateOf<String?>(null); private set
    var errorMessage by mutableStateOf<String?>(null); private set
    var installedApps by mutableStateOf<List<InstalledApp>>(emptyList()); private set
    var patchItems by mutableStateOf<List<PatchItem>>(emptyList()); private set
    var selected by mutableStateOf<Set<String>>(emptySet()); private set
    var logs by mutableStateOf<List<String>>(emptyList()); private set
    var isPatching by mutableStateOf(false); private set
    var result by mutableStateOf<PatcherEngine.Result?>(null); private set
    private var patchJob: Job? = null

    fun dismissError() { errorMessage = null }

    /** Importe un APK, ou un bundle (.apks/.xapk/.apkm) qui sera fusionné. */
    fun importApk(uri: Uri, onDone: () -> Unit = {}) = runBusy("Copie du fichier…") {
        withContext(Dispatchers.IO) {
            workDir.mkdirs()
            val raw = File(workDir, "picked")
            val stream = getApplication<Application>().contentResolver.openInputStream(uri) ?: error("Impossible d'ouvrir le fichier")
            stream.use { input -> raw.outputStream().use { input.copyTo(it) } }
            if (getApplication<Application>().packageManager.getPackageArchiveInfo(raw.absolutePath, 0) == null && SplitMerger.isBundle(raw)) {
                busyMessage = "Fusion des APK scindés…"
                SplitMerger.mergeBundle(raw, inputApk, workDir)
                raw.delete()
            } else {
                raw.renameTo(inputApk)
            }
        }
        apk = readApk(inputApk) ?: error("Ce fichier n'est pas un APK valide")
        onDone()
    }

    fun selectInstalledApp(app: InstalledApp, onDone: () -> Unit) = runBusy("Copie de ${app.label}…") {
        withContext(Dispatchers.IO) {
            workDir.mkdirs()
            if (app.apks.size == 1) {
                File(app.apks.first()).copyTo(inputApk, overwrite = true)
            } else {
                busyMessage = "Fusion de ${app.apks.size} APK scindés…"
                val dir = File(workDir, "splits").apply { deleteRecursively(); mkdirs() }
                app.apks.forEachIndexed { i, path -> File(path).copyTo(File(dir, if (i == 0) "base.apk" else File(path).name)) }
                SplitMerger.merge(dir, inputApk)
                dir.deleteRecursively()
            }
        }
        apk = readApk(inputApk)?.copy(label = app.label)
        onDone()
    }

    private fun readApk(file: File): ApkSource? {
        val pm = getApplication<Application>().packageManager
        val info = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        val label = info.applicationInfo?.let {
            it.sourceDir = file.absolutePath
            it.publicSourceDir = file.absolutePath
            pm.getApplicationLabel(it).toString()
        } ?: info.packageName
        return ApkSource(file, label, info.packageName, info.versionName)
    }

    fun loadInstalledApps() {
        if (installedApps.isNotEmpty()) return
        runBusy("Chargement des applications…") {
            installedApps = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                pm.getInstalledPackages(0).mapNotNull { pkg ->
                    val ai = pkg.applicationInfo ?: return@mapNotNull null
                    val isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                        ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                    if (isSystem) return@mapNotNull null
                    InstalledApp(
                        label = pm.getApplicationLabel(ai).toString(),
                        packageName = pkg.packageName,
                        versionName = pkg.versionName,
                        apks = listOf(ai.sourceDir) + ai.splitSourceDirs.orEmpty(),
                    )
                }.sortedBy { it.label.lowercase() }
            }
        }
    }

    fun preparePatches(onDone: () -> Unit) {
        val source = apk ?: return
        runBusy("Chargement des patchs…") {
            if (!repository.hasBundle) repository.update()
            repository.ensureLoaded()
            patchItems = repository.compatiblePatches(source.packageName).map {
                PatchItem(it, it.name.orEmpty(), it.description, it.versionsFor(source.packageName), it.use)
            }
            selected = patchItems.filter { it.enabledByDefault }.map { it.name }.toSet()
            if (patchItems.isEmpty()) error("Aucun patch compatible avec ${source.packageName}.")
            onDone()
        }
    }

    fun toggle(name: String) { selected = if (name in selected) selected - name else selected + name }
    fun selectAll() { selected = patchItems.map { it.name }.toSet() }
    fun selectNone() { selected = emptySet() }
    fun selectDefaults() { selected = patchItems.filter { it.enabledByDefault }.map { it.name }.toSet() }

    fun startPatching() {
        val source = apk ?: return
        val patches = patchItems.filter { it.name in selected }.map { it.patch }.toSet()
        if (patches.isEmpty()) { errorMessage = "Sélectionne au moins un patch."; return }
        logs = emptyList()
        result = null
        isPatching = true
        Installer.reset(KEY)
        patchJob = viewModelScope.launch {
            try {
                result = engine.patch(source.file, patches, "manual.apk") { line ->
                    viewModelScope.launch(Dispatchers.Main) { logs = logs + line }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                logs = logs + "Annulé."
            } catch (t: Throwable) {
                logs = logs + "ÉCHEC : ${t.message ?: t}"
            } finally {
                isPatching = false
            }
        }
    }

    fun cancelPatching() { patchJob?.cancel() }

    fun install() {
        val file = result?.apk ?: return
        viewModelScope.launch { Installer.install(getApplication(), file, KEY) }
    }

    fun exportTo(uri: Uri) = runBusy("Export de l'APK…") {
        val file = result?.apk ?: return@runBusy
        withContext(Dispatchers.IO) {
            val out = getApplication<Application>().contentResolver.openOutputStream(uri) ?: error("Écriture impossible")
            out.use { o -> file.inputStream().use { it.copyTo(o) } }
        }
    }

    private fun runBusy(message: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busyMessage = message
            try { block() } catch (t: Throwable) { errorMessage = t.message ?: t.toString() } finally { busyMessage = null }
        }
    }

    companion object { const val KEY = "manual" }
}
