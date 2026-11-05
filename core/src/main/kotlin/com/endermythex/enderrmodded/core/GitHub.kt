package com.endermythex.enderrmodded.core

import org.json.JSONArray
import org.json.JSONObject

/** Accès en lecture aux releases GitHub (API publique, sans jeton). */
object GitHub {
    data class Asset(val name: String, val url: String, val size: Long)
    data class Release(
        val tag: String,
        val name: String,
        val body: String,
        val prerelease: Boolean,
        val publishedAt: String,
        val assets: List<Asset>,
    )

    private val headers = mapOf("Accept" to "application/vnd.github+json")

    /** Dernière release (y compris pré-releases si [includePrerelease]). */
    fun latestRelease(repo: String, includePrerelease: Boolean = false): Release {
        val cleanRepo = repo.trim().removePrefix("https://github.com/").trim('/')
        return if (includePrerelease) {
            val array = JSONArray(Http.getString("https://api.github.com/repos/$cleanRepo/releases?per_page=10", headers, "EnderrModded"))
            (0 until array.length()).map { parse(array.getJSONObject(it)) }.firstOrNull()
                ?: error("Aucune release trouvée pour $cleanRepo")
        } else {
            parse(JSONObject(Http.getString("https://api.github.com/repos/$cleanRepo/releases/latest", headers, "EnderrModded")))
        }
    }

    private fun parse(json: JSONObject): Release {
        val assets = json.optJSONArray("assets") ?: JSONArray()
        return Release(
            tag = json.getString("tag_name"),
            name = json.optString("name").ifBlank { json.getString("tag_name") },
            body = json.optString("body"),
            prerelease = json.optBoolean("prerelease"),
            publishedAt = json.optString("published_at"),
            assets = (0 until assets.length()).map {
                val a = assets.getJSONObject(it)
                Asset(a.getString("name"), a.getString("browser_download_url"), a.optLong("size"))
            },
        )
    }
}
