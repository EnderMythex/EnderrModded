package com.endermythex.enderrmodded.data

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Paramètres de l'app, persistés et observables par Compose. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** RAM totale de l'appareil (Mo). */
    val deviceRamMb: Int = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem / (1024 * 1024)
    }.getOrDefault(4096L).toInt()

    /** Limites proposées pour le processus de patch, selon la RAM de l'appareil. */
    val memoryChoices: List<Int> get() = listOf(512, 768, 1024, 1536, 2048).filter { it <= deviceRamMb / 2 }.ifEmpty { listOf(512) }

    // --- Patchs ---
    var patchesRepo by string("patches_repo", DEFAULT_PATCHES_REPO)
    var patchesPrerelease by boolean("patches_prerelease", false)
    var autoUpdatePatches by boolean("auto_update_patches", true)
    var patchesTag by string("patches_tag", "")

    // --- Téléchargement ---
    var sourceOrder by string("source_order", "device,apkmirror")
    var verifySignatures by boolean("verify_signatures", true)
    var useLatestVersion by boolean("use_latest_version", false)

    // --- Performances ---
    /** Patch dans un processus séparé avec sa propre limite de mémoire (comme ReVanced Manager). */
    var useProcessRuntime by boolean("use_process_runtime", true)
    // Au-delà de ~2 Go, ART ne peut pas démarrer (tas limité aux 4 premiers Go d'adresses).
    var processMemoryMb by int("process_memory_mb", (deviceRamMb / 4).coerceIn(512, 1536).let { m -> listOf(512, 768, 1024, 1536).last { it <= m } })

    // --- Patch & installation ---
    var autoInstall by boolean("auto_install", true)
    var keepDownloads by boolean("keep_downloads", false)

    // --- Mises à jour ---
    var backgroundChecks by boolean("background_checks", true)
    var checkIntervalHours by int("check_interval_hours", 12)
    var autoRebuild by boolean("auto_rebuild", false)
    var selfUpdateCheck by boolean("self_update_check", true)

    // --- Apparence ---
    var theme by string("theme", "system")
    var dynamicColor by boolean("dynamic_color", true)

    // --- Interne ---
    var ownCertificate by string("own_certificate", "")

    val sources: List<String> get() = sourceOrder.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        val all = listOf("device", "apkmirror")
        val current = sources.toMutableList()
        if (enabled && id !in current) current += id else if (!enabled) current -= id
        sourceOrder = all.filter { it in current }.joinToString(",")
    }

    private fun <T> pref(key: String, read: () -> T, write: (T) -> Unit) = object : ReadWriteProperty<Any, T> {
        private val state: MutableState<T> = mutableStateOf(read())
        override fun getValue(thisRef: Any, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            state.value = value
            write(value)
        }
    }

    private fun string(key: String, default: String) =
        pref(key, { prefs.getString(key, default) ?: default }) { prefs.edit().putString(key, it).apply() }

    private fun boolean(key: String, default: Boolean) =
        pref(key, { prefs.getBoolean(key, default) }) { prefs.edit().putBoolean(key, it).apply() }

    private fun int(key: String, default: Int) =
        pref(key, { prefs.getInt(key, default) }) { prefs.edit().putInt(key, it).apply() }

    init {
        if (processMemoryMb > 2048) processMemoryMb = 2048
        // Le dépôt GitHub ReVanced/revanced-patches n'est plus accessible : on passe par l'API officielle.
        if (patchesRepo == "ReVanced/revanced-patches") patchesRepo = DEFAULT_PATCHES_REPO
        // Uptodown protège désormais ses téléchargements par un captcha : source retirée.
        if ("uptodown" in sourceOrder) sourceOrder = sources.filter { it != "uptodown" }.joinToString(",")
    }

    companion object {
        /** API officielle ReVanced (même source que ReVanced Manager). */
        const val DEFAULT_PATCHES_REPO = com.endermythex.enderrmodded.core.PatchSources.REVANCED_API
    }
}
