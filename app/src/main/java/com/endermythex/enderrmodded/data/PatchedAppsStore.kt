package com.endermythex.enderrmodded.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Une app patchée par EnderrModded. */
data class PatchedApp(
    val packageName: String,
    val patchedPackageName: String,
    val version: String,
    val patchesTag: String,
    val patches: List<String>,
    val patchedAt: Long,
    val apkPath: String,
)

/**
 * Mémorise les apps patchées, la sélection de patchs par app et les certificats épinglés.
 */
class PatchedAppsStore(context: Context) {
    private val dir = File(context.filesDir, "data").apply { mkdirs() }
    private val appsFile = File(dir, "patched_apps.json")
    private val selectionsFile = File(dir, "selections.json")
    private val pinsFile = File(dir, "certificate_pins.json")

    var apps by mutableStateOf(loadApps()); private set
    private var selections: Map<String, Set<String>> = loadMap(selectionsFile) { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() }
    private var pins: Map<String, String> = loadStringMap(pinsFile)

    @Synchronized
    fun record(app: PatchedApp) {
        apps = apps.filterNot { it.packageName == app.packageName } + app
        appsFile.writeText(JSONArray(apps.map { it.toJson() }).toString())
    }

    @Synchronized
    fun forget(packageName: String) {
        apps = apps.filterNot { it.packageName == packageName }
        appsFile.writeText(JSONArray(apps.map { it.toJson() }).toString())
    }

    fun get(packageName: String) = apps.firstOrNull { it.packageName == packageName }

    fun selection(packageName: String): Set<String>? = selections[packageName]

    @Synchronized
    fun setSelection(packageName: String, names: Set<String>?) {
        selections = if (names == null) selections - packageName else selections + (packageName to names)
        selectionsFile.writeText(JSONObject(selections.mapValues { JSONArray(it.value.toList()) }).toString())
    }

    fun pin(packageName: String): String? = pins[packageName]

    @Synchronized
    fun setPin(packageName: String, digest: String?) {
        pins = if (digest == null) pins - packageName else pins + (packageName to digest)
        pinsFile.writeText(JSONObject(pins).toString())
    }

    @Synchronized
    fun clearPins() {
        pins = emptyMap()
        pinsFile.delete()
    }

    private fun loadApps(): List<PatchedApp> = runCatching {
        val arr = JSONArray(appsFile.readText())
        (0 until arr.length()).map { arr.getJSONObject(it).toPatchedApp() }
    }.getOrDefault(emptyList())

    private fun <T> loadMap(file: File, parse: (JSONArray) -> T): Map<String, T> = runCatching {
        val json = JSONObject(file.readText())
        json.keys().asSequence().associateWith { parse(json.getJSONArray(it)) }
    }.getOrDefault(emptyMap())

    private fun loadStringMap(file: File): Map<String, String> = runCatching {
        val json = JSONObject(file.readText())
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())

    private fun PatchedApp.toJson() = JSONObject()
        .put("packageName", packageName)
        .put("patchedPackageName", patchedPackageName)
        .put("version", version)
        .put("patchesTag", patchesTag)
        .put("patches", JSONArray(patches))
        .put("patchedAt", patchedAt)
        .put("apkPath", apkPath)

    private fun JSONObject.toPatchedApp(): PatchedApp {
        val list = optJSONArray("patches") ?: JSONArray()
        return PatchedApp(
            packageName = getString("packageName"),
            patchedPackageName = optString("patchedPackageName", getString("packageName")),
            version = optString("version"),
            patchesTag = optString("patchesTag"),
            patches = (0 until list.length()).map { list.getString(it) },
            patchedAt = optLong("patchedAt"),
            apkPath = optString("apkPath"),
        )
    }
}
