package com.endermythex.enderrmodded.core.sources

import com.endermythex.enderrmodded.core.Http
import com.endermythex.enderrmodded.core.Versions
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File

/**
 * Uptodown : source de secours. Nécessite que l'app soit dans [AppCatalog] (slug Uptodown).
 */
class UptodownSource : ApkSource {
    override val id = "uptodown"
    override val displayName = "Uptodown"

    private fun baseUrl(packageName: String): String {
        val slug = AppCatalog.apps[packageName]?.uptodownSlug
            ?: throw SourceException("$packageName n'est pas référencé pour Uptodown")
        return "https://$slug.en.uptodown.com/android"
    }

    private fun get(url: String): Document = Jsoup.parse(Http.getString(url), url)

    private fun appCode(base: String): String =
        get("$base/versions").selectFirst("#detail-app-name[data-code]")?.attr("data-code")
            ?: throw SourceException("Code de l'app introuvable sur Uptodown")

    private data class Entry(val version: String, val url: String, val isBundle: Boolean)

    private fun entries(base: String, code: String, maxPages: Int): Sequence<Entry> = sequence {
        for (page in 1..maxPages) {
            val json = runCatching { JSONObject(Http.getString("$base/apps/$code/versions/$page")) }.getOrNull() ?: break
            val data = json.optJSONArray("data") ?: break
            if (data.length() == 0) break
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val version = item.optString("version").takeIf { it.isNotBlank() } ?: continue
                val url = when (val v = item.opt("versionURL")) {
                    is JSONObject -> listOf(v.optString("url"), v.optString("extraURL"), v.opt("versionID")?.toString().orEmpty())
                        .filter { it.isNotBlank() }.joinToString("/")
                    is String -> v
                    else -> continue
                }
                yield(Entry(version, url, item.optString("kindFile").equals("xapk", true)))
            }
        }
    }

    override fun versions(packageName: String): List<String> {
        val base = baseUrl(packageName)
        val code = appCode(base)
        return entries(base, code, 2).map { it.version }
            .filterNot { Regex("(?i)(beta|alpha)").containsMatchIn(it) }
            .distinct().toList()
            .sortedWith { a, b -> Versions.compare(b, a) }
    }

    override fun latestVersion(packageName: String): String? = runCatching { versions(packageName).firstOrNull() }.getOrNull()

    override fun download(
        packageName: String,
        version: String,
        destinationDir: File,
        abis: List<String>,
        log: (String) -> Unit,
        onProgress: (Long, Long) -> Unit,
    ): DownloadedApk {
        val base = baseUrl(packageName)
        val code = appCode(base)
        val entry = entries(base, code, 20).firstOrNull { it.version == version }
            ?: throw SourceException("Version $version introuvable sur Uptodown")
        log("Uptodown : ${entry.url}")
        var isBundle = entry.isBundle
        var page = get(entry.url)

        // Plusieurs variantes (architectures) : on choisit la plus adaptée.
        val dataVersion = page.selectFirst(".button.variants[data-version]")?.attr("data-version")
        if (!dataVersion.isNullOrBlank()) {
            val filesJson = JSONObject(Http.getString("${base.removeSuffix("/android")}/app/$code/version/$dataVersion/files"))
            val content = Jsoup.parse(filesJson.getString("content"))
            val container = content.selectFirst(".content") ?: content.body()
            var currentArch = ""
            data class Variant(val arch: String, val fileId: String, val isBundle: Boolean)
            val variants = mutableListOf<Variant>()
            for (node in container.children()) {
                if (!node.hasClass("variant")) {
                    currentArch = node.text().trim()
                    continue
                }
                val fileId = node.selectFirst(".v-report[data-file-id]")?.attr("data-file-id") ?: continue
                val type = node.selectFirst(".v-file > span")?.text().orEmpty()
                variants += Variant(currentArch, fileId, type.equals("xapk", true))
            }
            val chosen = variants.filter { Abis.matches(it.arch, abis) }
                .sortedWith(compareBy<Variant> { it.isBundle }.thenBy { Abis.score(it.arch, abis) })
                .firstOrNull()
                ?: throw SourceException("Aucune variante compatible (${abis.joinToString()}) sur Uptodown")
            log("Variante : ${chosen.arch}")
            isBundle = chosen.isBundle
            page = get("$base/download/${chosen.fileId}-x")
        }

        val dataUrl = page.selectFirst("#detail-download-button[data-url]")?.attr("data-url")
            ?: throw SourceException("Lien de téléchargement introuvable sur Uptodown")
        val file = File(destinationDir, "$packageName-$version.${if (isBundle) "xapk" else "apk"}")
        Http.download("https://dw.uptodown.com/dwn/$dataUrl", file, mapOf("Referer" to entry.url), onProgress)
        return DownloadedApk(file, version, isBundle, displayName)
    }
}
