package com.endermythex.enderrmodded

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.endermythex.enderrmodded.core.GitHub
import com.endermythex.enderrmodded.core.Versions
import com.endermythex.enderrmodded.core.sources.AppCatalog
import com.endermythex.enderrmodded.data.PatchedApp
import com.endermythex.enderrmodded.patcher.PatcherEngine
import com.endermythex.enderrmodded.pipeline.ApkProvider
import com.endermythex.enderrmodded.update.SelfUpdater
import com.endermythex.enderrmodded.work.PatchWorker
import com.endermythex.enderrmodded.work.UpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tab(val label: String) { Apps("Apps"), Patches("Patchs"), Settings("Réglages") }

sealed interface Route {
    data class AppDetail(val packageName: String) : Route
    data class Run(val packageName: String) : Route
    data object ManualHome : Route
    data object ManualPicker : Route
    data object ManualPatches : Route
    data object ManualRun : Route
}

/** Une app ciblée par le bundle de patchs. */
data class AppEntry(
    val packageName: String,
    val name: String,
    val patchCount: Int,
    val recommendedVersion: String?,
    val installedVersion: String?,
    val patched: PatchedApp?,
    val patchedInstalled: Boolean,
) {
    /** Une reconstruction apporterait du nouveau (version ou patchs plus récents). */
    fun needsUpdate(patchesTag: String): Boolean {
        val p = patched ?: return false
        return (recommendedVersion != null && Versions.isNewer(recommendedVersion, p.version)) || p.patchesTag != patchesTag
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val enderr = app as EnderrApp
    val settings = enderr.settings
    val repository = enderr.patches
    val store = enderr.store
    private val provider = ApkProvider(app, settings, store)

    // ---- Navigation ----
    var tab by mutableStateOf(Tab.Apps)
    var stack by mutableStateOf<List<Route>>(emptyList()); private set
    val route: Route? get() = stack.lastOrNull()

    fun open(route: Route) { stack = stack + route }
    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack = stack.dropLast(1)
        return true
    }
    fun selectTab(t: Tab) { tab = t; stack = emptyList() }

    // ---- État ----
    var busyMessage by mutableStateOf<String?>(null); private set
    var errorMessage by mutableStateOf<String?>(null); private set
    var selfUpdate by mutableStateOf<GitHub.Release?>(null); private set
    var selfUpdateProgress by mutableStateOf<Float?>(null); private set
    private var installed by mutableStateOf<Map<String, String?>>(emptyMap())
    val latestVersions = mutableStateMapOf<String, String?>()

    fun dismissError() { errorMessage = null }

    init {
        viewModelScope.launch {
            try {
                if (!repository.hasBundle) {
                    busyMessage = "Premier lancement : téléchargement des patchs…"
                    repository.update()
                } else {
                    repository.ensureLoaded()
                    runCatching { repository.checkForUpdate() }
                    if (settings.autoUpdatePatches && repository.updateAvailable) {
                        busyMessage = "Mise à jour des patchs…"
                        repository.update()
                    }
                }
            } catch (t: Throwable) {
                errorMessage = "Patchs indisponibles : ${t.message ?: t}"
            } finally {
                busyMessage = null
            }
            refreshInstalled()
        }
        if (settings.selfUpdateCheck) checkSelfUpdate(silent = true)
    }

    /** Liste des apps supportées (triée : patchées, installées, puis le reste). */
    val apps: List<AppEntry>
        get() {
            val patches = repository.patches
            val packages = repository.supportedPackages()
            return packages.map { pkg ->
                val selected = repository.selectedPatches(pkg, store.selection(pkg))
                val patched = store.get(pkg)
                AppEntry(
                    packageName = pkg,
                    name = AppCatalog.nameOf(pkg),
                    patchCount = patches.count { p -> p.compatiblePackages?.any { it.first == pkg } == true },
                    recommendedVersion = repository.recommendedVersion(pkg, selected),
                    installedVersion = installed[pkg],
                    patched = patched,
                    patchedInstalled = patched != null && installed.containsKey(patched.patchedPackageName) &&
                        installed[patched.patchedPackageName] != null,
                )
            }.sortedWith(
                compareByDescending<AppEntry> { it.patched != null }
                    .thenByDescending { it.installedVersion != null }
                    .thenByDescending { AppCatalog.apps.containsKey(it.packageName) }
                    .thenBy { it.name.lowercase() },
            )
        }

    fun refreshInstalled() {
        viewModelScope.launch {
            installed = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                val wanted = repository.supportedPackages() + store.apps.map { it.patchedPackageName }
                wanted.associateWith { pkg ->
                    runCatching { pm.getPackageInfo(pkg, 0).versionName }.getOrNull()
                }.filterValues { it != null }
            }
        }
    }

    fun fetchLatestVersion(packageName: String) {
        if (latestVersions.containsKey(packageName)) return
        latestVersions[packageName] = null
        viewModelScope.launch {
            latestVersions[packageName] = withContext(Dispatchers.IO) { runCatching { provider.latestVersion(packageName) }.getOrNull() }
        }
    }

    // ---- Patchs ----

    fun updatePatches(force: Boolean = false) = runBusy("Vérification des patchs…") {
        val changed = repository.update(force)
        if (!changed) errorMessage = "Les patchs sont déjà à jour (${settings.patchesTag})."
        refreshInstalled()
    }

    fun importBundle(uri: Uri) = runBusy("Import du bundle…") { repository.importFrom(uri) }

    fun setSelection(packageName: String, names: Set<String>?) = store.setSelection(packageName, names)

    // ---- Patch auto ----

    fun autoPatch(packageName: String, version: String? = null) {
        PatchWorker.enqueue(getApplication(), packageName, version)
        open(Route.Run(packageName))
    }

    fun cancelPatch(packageName: String) = PatchWorker.cancel(getApplication(), packageName)

    // ---- Mise à jour de l'app ----

    fun checkSelfUpdate(silent: Boolean = false) {
        viewModelScope.launch {
            try {
                selfUpdate = SelfUpdater.check()
                if (!silent && selfUpdate == null) errorMessage = "EnderrModded est à jour (${BuildConfig.VERSION_NAME})."
            } catch (t: Throwable) {
                if (!silent) errorMessage = "Vérification impossible : ${t.message}"
            }
        }
    }

    fun installSelfUpdate() {
        val release = selfUpdate ?: return
        viewModelScope.launch {
            try {
                selfUpdateProgress = 0f
                SelfUpdater.downloadAndInstall(getApplication(), release) { selfUpdateProgress = it }
            } catch (t: Throwable) {
                errorMessage = "Mise à jour impossible : ${t.message}"
            } finally {
                selfUpdateProgress = null
            }
        }
    }

    // ---- Réglages ----

    fun rescheduleChecks() = UpdateWorker.schedule(getApplication(), settings)

    fun clearCache() = runBusy("Nettoyage…") {
        withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            File(app.filesDir, "output").listFiles()?.forEach { it.delete() }
        }
        errorMessage = "Cache vidé."
    }

    fun exportKeystore(uri: Uri) = runBusy("Export de la clé…") {
        val keystore = PatcherEngine(getApplication()).keystore
        check(keystore.exists()) { "Aucune clé : elle est créée au premier patch." }
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                keystore.inputStream().use { it.copyTo(out) }
            } ?: error("Écriture impossible")
        }
        errorMessage = "Clé exportée (keystore BKS/PKCS12 sans mot de passe, alias et mot de passe de clé : EnderrModded)."
    }

    fun forget(packageName: String) = store.forget(packageName)

    private fun runBusy(message: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busyMessage = message
            try { block() } catch (t: Throwable) { errorMessage = t.message ?: t.toString() } finally { busyMessage = null }
        }
    }
}
