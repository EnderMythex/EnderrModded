package com.endermythex.enderrmodded

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.revanced.patcher.patch.Patch
import com.endermythex.enderrmodded.installer.Installer
import com.endermythex.enderrmodded.patcher.PatchBundleManager
import com.endermythex.enderrmodded.patcher.PatcherEngine
import com.endermythex.enderrmodded.patcher.isCompatibleWith
import com.endermythex.enderrmodded.patcher.versionsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { Home, AppPicker, Patches, Patching }

data class ApkSource(
    val file: File,
    val label: String,
    val packageName: String,
    val versionName: String?,
)

data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String?,
    val sourceDir: String,
    val isSplit: Boolean,
)

data class PatchItem(
    val patch: Patch,
    val name: String,
    val description: String?,
    val recommendedVersions: Set<String>?,
    val enabledByDefault: Boolean,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val bundles = PatchBundleManager(app)
    private val engine = PatcherEngine(app)
    private val inputApk get() = File(getApplication<Application>().cacheDir, "input/input.apk")

    var screen by mutableStateOf(Screen.Home); private set
    var apk by mutableStateOf<ApkSource?>(null); private set
    var bundleDescription by mutableStateOf<String?>(null); private set
    var bundleRepo by mutableStateOf("ReVanced/revanced-patches")
    var busyMessage by mutableStateOf<String?>(null); private set
    var errorMessage by mutableStateOf<String?>(null); private set

    var installedApps by mutableStateOf<List<InstalledApp>>(emptyList()); private set
    var patchItems by mutableStateOf<List<PatchItem>>(emptyList()); private set
    var selected by mutableStateOf<Set<String>>(emptySet()); private set

    var logs by mutableStateOf<List<String>>(emptyList()); private set
    var isPatching by mutableStateOf(false); private set
    var result by mutableStateOf<PatcherEngine.Result?>(null); private set

    private var allPatches: Set<Patch> = emptySet()
    private var patchJob: Job? = null

    init {
        refreshBundleDescription()
    }

    fun dismissError() { errorMessage = null }

    fun navigate(target: Screen) {
        if (target == Screen.AppPicker && installedApps.isEmpty()) loadInstalledApps()
        screen = target
    }

    fun back(): Boolean {
        screen = when (screen) {
            Screen.Home -> return false
            Screen.AppPicker, Screen.Patches -> Screen.Home
            Screen.Patching -> {
                if (isPatching) return true
                Screen.Patches
            }
        }
        return true
    }

    // ---- Bundle de patchs ----

    private fun refreshBundleDescription() {
        bundleDescription = if (bundles.hasBundle) {
            bundles.info()?.let { listOfNotNull(it.name, it.version).joinToString(" ").ifBlank { null } }
                ?: "Bundle importé"
        } else null
    }

    fun importBundle(uri: Uri) = runBusy("Import du bundle…") {
        bundles.importFrom(uri)
        allPatches = emptySet()
        refreshBundleDescription()
    }

    fun downloadBundle() = runBusy("Téléchargement des patchs depuis ${bundleRepo.trim()}…") {
        val tag = bundles.downloadLatest(bundleRepo.trim())
        allPatches = emptySet()
        refreshBundleDescription()
        if (bundleDescription == "Bundle importé") bundleDescription = "${bundleRepo.trim()} $tag"
    }

    // ---- Choix de l'APK ----

    fun importApk(uri: Uri) = runBusy("Copie de l'APK…") {
        withContext(Dispatchers.IO) {
            inputApk.parentFile?.mkdirs()
            val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                ?: error("Impossible d'ouvrir le fichier")
            stream.use { input -> inputApk.outputStream().use { input.copyTo(it) } }
        }
        apk = readApk(inputApk) ?: run {
            inputApk.delete()
            error("Ce fichier n'est pas un APK valide (les .apks / .xapk ne sont pas encore supportés)")
        }
    }

    fun selectInstalledApp(app: InstalledApp) {
        if (app.isSplit) {
            errorMessage = "${app.label} est installée en APK scindé (split) : non supporté pour l'instant. Utilise un APK complet."
            return
        }
        runBusy("Copie de ${app.label}…") {
            withContext(Dispatchers.IO) {
                inputApk.parentFile?.mkdirs()
                File(app.sourceDir).copyTo(inputApk, overwrite = true)
            }
            apk = readApk(inputApk)?.copy(label = app.label)
            screen = Screen.Home
        }
    }

    private fun readApk(file: File): ApkSource? {
        val pm = getApplication<Application>().packageManager
        val info = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        val appInfo = info.applicationInfo
        val label = appInfo?.let {
            it.sourceDir = file.absolutePath
            it.publicSourceDir = file.absolutePath
            pm.getApplicationLabel(it).toString()
        } ?: info.packageName
        return ApkSource(file, label, info.packageName, info.versionName)
    }

    private fun loadInstalledApps() = runBusy("Chargement des applications…") {
        installedApps = withContext(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
                .mapNotNull { pkg ->
                    val ai = pkg.applicationInfo ?: return@mapNotNull null
                    val isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                        ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                    if (isSystem) return@mapNotNull null
                    InstalledApp(
                        label = pm.getApplicationLabel(ai).toString(),
                        packageName = pkg.packageName,
                        versionName = pkg.versionName,
                        sourceDir = ai.sourceDir,
                        isSplit = !ai.splitSourceDirs.isNullOrEmpty(),
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    // ---- Sélection des patchs ----

    fun openPatches() {
        val source = apk ?: return
        runBusy("Chargement des patchs…") {
            if (allPatches.isEmpty()) allPatches = bundles.load()
            patchItems = allPatches
                .filter { it.isCompatibleWith(source.packageName) }
                .map {
                    PatchItem(
                        patch = it,
                        name = it.name.orEmpty(),
                        description = it.description,
                        recommendedVersions = it.versionsFor(source.packageName),
                        enabledByDefault = it.use,
                    )
                }
                .sortedBy { it.name.lowercase() }
            selected = patchItems.filter { it.enabledByDefault }.map { it.name }.toSet()
            if (patchItems.isEmpty()) error("Aucun patch compatible avec ${source.packageName} dans ce bundle.")
            screen = Screen.Patches
        }
    }

    fun toggle(name: String) {
        selected = if (name in selected) selected - name else selected + name
    }

    fun selectAll() { selected = patchItems.map { it.name }.toSet() }
    fun selectNone() { selected = emptySet() }
    fun selectDefaults() { selected = patchItems.filter { it.enabledByDefault }.map { it.name }.toSet() }

    // ---- Patch ----

    fun startPatching() {
        val source = apk ?: return
        val patches = patchItems.filter { it.name in selected }.map { it.patch }.toSet()
        if (patches.isEmpty()) {
            errorMessage = "Sélectionne au moins un patch."
            return
        }
        logs = emptyList()
        result = null
        isPatching = true
        Installer.reset()
        screen = Screen.Patching
        patchJob = viewModelScope.launch {
            try {
                result = engine.patch(source.file, patches) { line ->
                    viewModelScope.launch(Dispatchers.Main) { logs = logs + line }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                appendLog("Annulé.")
            } catch (t: Throwable) {
                appendLog("ÉCHEC : ${t.message ?: t}")
                appendLog(t.stackTraceToString().lineSequence().take(15).joinToString("\n"))
            } finally {
                isPatching = false
            }
        }
    }

    fun cancelPatching() { patchJob?.cancel() }

    private fun appendLog(line: String) { logs = logs + line }

    fun install() {
        val apkFile = result?.apk ?: return
        viewModelScope.launch { Installer.install(getApplication(), apkFile) }
    }

    fun exportTo(uri: Uri) = runBusy("Export de l'APK…") {
        val apkFile = result?.apk ?: return@runBusy
        withContext(Dispatchers.IO) {
            val out = getApplication<Application>().contentResolver.openOutputStream(uri)
                ?: error("Impossible d'écrire le fichier")
            out.use { o -> apkFile.inputStream().use { it.copyTo(o) } }
        }
    }

    // ---- Utilitaires ----

    private fun runBusy(message: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busyMessage = message
            try {
                block()
            } catch (t: Throwable) {
                errorMessage = t.message ?: t.toString()
            } finally {
                busyMessage = null
            }
        }
    }
}
