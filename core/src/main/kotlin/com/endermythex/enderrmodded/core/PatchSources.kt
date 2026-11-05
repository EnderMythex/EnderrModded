package com.endermythex.enderrmodded.core

import org.json.JSONObject

/**
 * Sources de bundles de patchs :
 * - une API compatible ReVanced (URL http(s)://…, ex. https://api.revanced.app) ;
 * - un dépôt GitHub "owner/repo" publiant des fichiers .rvp dans ses releases.
 */
object PatchSources {
    const val REVANCED_API = "https://api.revanced.app"

    fun isApi(source: String) = source.trim().startsWith("http://") || source.trim().startsWith("https://")

    /** Dernière version publiée, sous forme de release avec un asset .rvp. */
    fun latest(source: String, prerelease: Boolean): GitHub.Release {
        if (!isApi(source)) return GitHub.latestRelease(source, prerelease)
        val base = source.trim().trimEnd('/')
        val json = JSONObject(
            Http.getString("$base/v5/patches${if (prerelease) "/prerelease" else ""}", mapOf("Accept" to "application/json"), "EnderrModded"),
        )
        val version = json.getString("version")
        return GitHub.Release(
            tag = version,
            name = version,
            body = json.optString("description"),
            prerelease = prerelease,
            publishedAt = json.optString("created_at"),
            assets = listOf(GitHub.Asset("patches-$version.rvp", json.getString("download_url"), 0)),
        )
    }
}
