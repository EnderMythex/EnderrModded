package com.endermythex.enderrmodded.data

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Paramètres de l'app, persistés et observables par Compose. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // --- Patchs ---
    var patchesRepo by string("patches_repo", DEFAULT_PATCHES_REPO)
    var patchesPrerelease by boolean("patches_prerelease", false)
    var autoUpdatePatches by boolean("auto_update_patches", true)
    var patchesTag by string("patches_tag", "")

    // --- Téléchargement ---
    var sourceOrder by string("source_order", "device,apkmirror,uptodown")
    var verifySignatures by boolean("verify_signatures", true)
    var useLatestVersion by boolean("use_latest_version", false)

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
        val all = listOf("device", "apkmirror", "uptodown")
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
        // Le dépôt GitHub ReVanced/revanced-patches n'est plus accessible : on passe par l'API officielle.
        if (patchesRepo == "ReVanced/revanced-patches") patchesRepo = DEFAULT_PATCHES_REPO
    }

    companion object {
        /** API officielle ReVanced (même source que ReVanced Manager). */
        const val DEFAULT_PATCHES_REPO = com.endermythex.enderrmodded.core.PatchSources.REVANCED_API
    }
}
