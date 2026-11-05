package com.endermythex.enderrmodded.core.sources

import com.endermythex.enderrmodded.core.Http
import com.endermythex.enderrmodded.core.Versions
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File

/**
 * APKMirror : chaque APK publié est vérifié par APKMirror (signature identique à celle du développeur).
 */
class ApkMirrorSource : ApkSource {
    override val id = "apkmirror"
    override val displayName = "APKMirror"

    private val base = "https://www.apkmirror.com"
    private val appUrlCache = HashMap<String, String>()

    private fun get(url: String): Document = Jsoup.parse(Http.getString(url, mapOf("Referer" to "$base/")), url)

    /** URL de la page de l'app (ex. https://www.apkmirror.com/apk/google-inc/youtube/). */
    fun appUrl(packageName: String): String {
        appUrlCache[packageName]?.let { return it }
        val known = AppCatalog.apps[packageName]?.apkMirrorPath
        val candidates = buildList {
            if (known != null) add("$base/apk/$known/")
            val search = runCatching { get("$base/?post_type=app_release&searchtype=app&s=$packageName") }.getOrNull()
            search?.select("div.appRow h5.appRowTitle a[href], div.listWidget a.fontBlack[href]").orEmpty()
                .map { it.absUrl("href") }
                .filter { it.contains("/apk/") }
                .map { url -> url.substringBefore("/apk/") + "/apk/" + url.substringAfter("/apk/").split('/').take(2).joinToString("/") + "/" }
                .distinct()
                .take(5)
                .forEach { add(it) }
        }
        val errors = mutableListOf<String>()
        for (url in candidates.distinct()) {
            val page = runCatching { Http.getString(url, mapOf("Referer" to "$base/")) }
                .onFailure { errors += "${url.removePrefix(base)} → ${it.message}" }
                .getOrNull() ?: continue
            if (page.contains("id=$packageName\"") || page.contains("id=$packageName&")) {
                appUrlCache[packageName] = url
                return url
            }
        }
        throw SourceException("Application $packageName introuvable sur APKMirror ${errors.joinToString(prefix = "(", postfix = ")")}")
    }

    override fun versions(packageName: String): List<String> {
        val appUrl = appUrl(packageName)
        val category = appUrl.trimEnd('/').substringAfterLast('/')
        val html = Http.getString("$base/uploads/?appcategory=$category")
        val doc = Jsoup.parse(html, base)
        // Titres du type "YouTube 20.12.46" ; on exclut les bêtas / alphas.
        val titles = doc.select("div.appRow h5.appRowTitle").map { it.text().trim() }
        val fromInfo = Regex("""Version:</span><span class="infoSlide-value">([^<]+?)\s*</span>""")
            .findAll(html).map { it.groupValues[1].trim() }.toList()
        return fromInfo
            .filter { v -> titles.none { t -> t.contains(v) && Regex("(?i)\\b(beta|alpha)\\b").containsMatchIn(t) } }
            .filterNot { Regex("(?i)(beta|alpha)").containsMatchIn(it) }
            .distinct()
            .sortedWith { a, b -> Versions.compare(b, a) }
    }

    override fun latestVersion(packageName: String): String? = runCatching { versions(packageName).firstOrNull() }.getOrNull()

    private data class Variant(val url: String, val isBundle: Boolean, val arch: String, val dpi: String)

    override fun download(
        packageName: String,
        version: String,
        destinationDir: File,
        abis: List<String>,
        log: (String) -> Unit,
        onProgress: (Long, Long) -> Unit,
    ): DownloadedApk {
        val appUrl = appUrl(packageName)
        val appPage = get(appUrl)
        val appName = appPage.selectFirst("h1.marginZero")?.text()
            ?: throw SourceException("Nom de l'app introuvable sur APKMirror")
        val slug = appName.lowercase().replace(' ', '-').replace(Regex("[^a-z0-9-]"), "")
        val releaseUrl = "${appUrl.trimEnd('/')}/$slug-${version.replace(' ', '-').replace('.', '-')}-release/"
        log("APKMirror : $releaseUrl")
        val release = runCatching { get(releaseUrl) }.getOrElse {
            throw SourceException("Version $version introuvable sur APKMirror", it)
        }

        val variants = release.select("div.table-row.headerFont").mapNotNull { row ->
            val cells = row.select("div.table-cell")
            val link = cells.firstOrNull()?.selectFirst("a[href]") ?: return@mapNotNull null
            val isBundle = cells[0].text().contains("BUNDLE", ignoreCase = true)
            Variant(
                url = link.absUrl("href"),
                isBundle = isBundle,
                arch = cells.getOrNull(1)?.text().orEmpty(),
                dpi = cells.getOrNull(3)?.text().orEmpty(),
            )
        }

        val variantPage: Document
        val isBundle: Boolean
        if (variants.isEmpty()) {
            // Une seule variante : la page de release est directement la page de téléchargement.
            variantPage = release
            isBundle = release.text().contains("APK bundle", ignoreCase = true)
        } else {
            val compatible = variants.filter { Abis.matches(it.arch, abis) }
            val chosen = compatible
                .sortedWith(
                    compareBy<Variant> { it.isBundle } // APK simple de préférence
                        .thenBy { if (it.dpi.contains("nodpi", true) || it.dpi.contains("anydpi", true)) 0 else 1 }
                        .thenBy { Abis.score(it.arch, abis) },
                )
                .firstOrNull()
                ?: throw SourceException("Aucune variante compatible (${abis.joinToString()}) sur APKMirror")
            log("Variante : ${if (chosen.isBundle) "BUNDLE" else "APK"} • ${chosen.arch} • ${chosen.dpi}")
            variantPage = get(chosen.url)
            isBundle = chosen.isBundle
        }

        val downloadPageUrl = variantPage.selectFirst("a.downloadButton[href], a.btn[href*=download]")?.absUrl("href")
            ?: throw SourceException("Bouton de téléchargement introuvable sur APKMirror")
        val downloadPage = get(downloadPageUrl)
        val fileUrl = downloadPage.selectFirst("a#download-link[href], span > a[rel=nofollow][href]")?.absUrl("href")
            ?: throw SourceException("Lien de fichier introuvable sur APKMirror")

        val file = File(destinationDir, "$packageName-$version.${if (isBundle) "apkm" else "apk"}")
        Http.download(fileUrl, file, mapOf("Referer" to downloadPageUrl), onProgress)
        return DownloadedApk(file, version, isBundle, displayName)
    }
}
