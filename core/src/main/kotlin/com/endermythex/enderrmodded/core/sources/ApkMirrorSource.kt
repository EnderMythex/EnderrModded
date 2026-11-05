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

    /** Page parsée ; [Document.location] est l'URL finale après redirections. */
    private fun get(url: String): Document =
        Http.getPage(url, mapOf("Referer" to "$base/")).let { (finalUrl, html) -> Jsoup.parse(html, finalUrl) }

    /** Chemin propre à l'app (ex. /apk/tiktok-pte-ltd/tik-tok-including-musical-ly/). */
    private fun appPath(appUrl: String) = "/apk/" + appUrl.substringAfter("/apk/").trimEnd('/') + "/"

    private fun belongsTo(url: String, appUrl: String) = url.substringAfter("apkmirror.com").startsWith(appPath(appUrl))

    /**
     * Page de la release [version] de l'app. On la prend dans la liste des publications de l'app :
     * deviner l'URL ne suffit pas, deux apps peuvent partager le même nom de release
     * (TikTok et TikTok Asie : "tiktok-36-5-4-release") et APKMirror redirige alors vers l'autre.
     */
    private fun findRelease(appUrl: String, version: String, log: (String) -> Unit): Document {
        val category = appUrl.trimEnd('/').substringAfterLast('/')
        val versionRegex = Regex("(^|\\s)" + Regex.escape(version) + "(\\s|$)")
        for (page in 1..40) {
            val url = if (page == 1) "$base/uploads/?appcategory=$category" else "$base/uploads/page/$page/?appcategory=$category"
            val doc = runCatching { get(url) }.getOrNull() ?: break
            val links = doc.select("div.appRow h5.appRowTitle a[href]")
            if (links.isEmpty()) break
            val match = links.firstOrNull { a -> belongsTo(a.absUrl("href"), appUrl) && versionRegex.containsMatchIn(a.text()) }
            if (match != null) return get(match.absUrl("href"))
            // Liste triée de la plus récente à la plus ancienne : on s'arrête une fois la version dépassée.
            val versionsOnPage = links.mapNotNull { Regex("\\d+(\\.\\d+)+").find(it.text())?.value }
            if (versionsOnPage.isNotEmpty() && versionsOnPage.all { Versions.compare(it, version) < 0 }) break
        }
        // Repli : URL construite, mais seulement si APKMirror ne redirige pas vers une autre app.
        val appName = get(appUrl).selectFirst("h1.marginZero")?.text()
            ?: throw SourceException("Nom de l'app introuvable sur APKMirror")
        val slug = appName.lowercase().replace(' ', '-').replace(Regex("[^a-z0-9-]"), "")
        val guess = "${appUrl.trimEnd('/')}/$slug-${version.lowercase().replace(' ', '-').replace('.', '-')}-release/"
        log("APKMirror : release non trouvée dans la liste, essai de $guess")
        val doc = runCatching { get(guess) }.getOrElse { throw SourceException("Version $version introuvable sur APKMirror", it) }
        if (!belongsTo(doc.location(), appUrl)) {
            throw SourceException("Version $version introuvable sur APKMirror (redirection vers une autre app : ${doc.location()})")
        }
        return doc
    }

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
            val owner = pagePackage(page)
            if (owner == packageName) {
                appUrlCache[packageName] = url
                return url
            }
            errors += "${url.removePrefix(base)} → ${owner ?: "paquet inconnu"}"
        }
        throw SourceException("Application $packageName introuvable sur APKMirror ${errors.joinToString(prefix = "(", postfix = ")")}")
    }

    /**
     * Paquet de l'app décrite par la page : son lien Play Store (classe accent_color), et non ceux
     * des apps similaires listées sur la même page (ex. TikTok vs TikTok Asie).
     */
    internal fun pagePackage(html: String): String? {
        val patterns = listOf(
            Regex("""details\?id=([\w.]+)[^"]*"[^>]*class="[^"]*accent_color"""),
            Regex("""class="[^"]*accent_color[^"]*"[^>]*href="[^"]*details\?id=([\w.]+)"""),
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }
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
            .sortedWith(compareBy<String> { if (Regex("^[0-9.]+$").matches(it)) 0 else 1 }.thenComparator { a, b -> Versions.compare(b, a) })
    }

    /** Extrait HTML autour des indices de lien final (diagnostic). */
    private fun snippet(page: Document): String {
        val html = page.outerHtml()
        val marks = listOf("download.php", "nofollow", "download-link", "Your download", "will start", "challenge", "cf-")
        val parts = marks.mapNotNull { m ->
            val i = html.indexOf(m)
            if (i < 0) null else "[$m] " + html.substring((i - 200).coerceAtLeast(0), (i + 200).coerceAtMost(html.length)).replace(Regex("\\s+"), " ")
        }
        return "title=${page.title()} len=${html.length} " + parts.joinToString(" ||| ")
    }

    /** Liens de téléchargement présents sur une page (diagnostic). */
    private fun links(page: Document): String =
        page.select("a[href*=download], a.btn, a.downloadButton, a[rel=nofollow]").take(10)
            .joinToString(" | ") { "<a class=${it.className()} id=${it.id()} href=${it.attr("href").take(90)}>" }
            .ifBlank { page.title() }

    override fun latestVersion(packageName: String): String? = runCatching { versions(packageName).firstOrNull() }.getOrNull()

    private data class Variant(val url: String, val isBundle: Boolean, val arch: String, val dpi: String)

    /** Page de téléchargement finale d'une variante (protégée par Cloudflare). */
    data class DownloadPage(val url: String, val packageName: String, val version: String, val isBundle: Boolean)

    /** Trouve la meilleure variante pour l'appareil et sa page de téléchargement. */
    fun resolve(packageName: String, version: String, abis: List<String>, log: (String) -> Unit): DownloadPage {
        val appUrl = appUrl(packageName)
        val release = findRelease(appUrl, version, log)
        log("APKMirror : ${release.location()}")

        val variants = release.select("div.table-row.headerFont").mapNotNull { row ->
            val cells = row.select("div.table-cell")
            val link = cells.firstOrNull()?.selectFirst("a[href]") ?: return@mapNotNull null
            if (!belongsTo(link.absUrl("href"), appUrl)) return@mapNotNull null
            Variant(
                url = link.absUrl("href"),
                isBundle = cells[0].text().contains("BUNDLE", ignoreCase = true),
                arch = cells.getOrNull(1)?.text().orEmpty(),
                dpi = cells.getOrNull(3)?.text().orEmpty(),
            )
        }

        val variantPage: Document
        val isBundle: Boolean
        if (variants.isEmpty()) {
            // Une seule variante : la page de release est directement la page de la variante.
            variantPage = release
            isBundle = release.text().contains("APK bundle", ignoreCase = true)
        } else {
            val chosen = variants.filter { Abis.matches(it.arch, abis) }
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

        val downloadPageUrl = variantPage.select("a.downloadButton[href], a.btn[href*=download]")
            .map { it.absUrl("href") }
            .firstOrNull { (it.contains("/download/") || it.contains("key=")) && belongsTo(it, appUrl) }
            ?: throw SourceException("Bouton de téléchargement introuvable sur APKMirror : ${links(variantPage)}")
        log("APKMirror : page de téléchargement $downloadPageUrl")
        return DownloadPage(downloadPageUrl, packageName, version, isBundle)
    }

    override fun download(
        packageName: String,
        version: String,
        destinationDir: File,
        abis: List<String>,
        log: (String) -> Unit,
        onProgress: (Long, Long) -> Unit,
    ): DownloadedApk {
        val page = resolve(packageName, version, abis, log)
        val downloadPage = get(page.url)
        val fileUrl = downloadPage.select("a#download-link[href], span > a[rel=nofollow][href], a[href*=download.php]")
            .map { it.absUrl("href") }
            .firstOrNull { it.contains("download.php") || it.contains("/wp-content/") }
        if (fileUrl == null) {
            // Le lien final n'est délivré qu'après la vérification Cloudflare d'un vrai navigateur.
            if (downloadPage.outerHtml().contains("challenge-platform")) throw BrowserRequiredException(page)
            throw SourceException("Lien de fichier introuvable sur APKMirror : ${snippet(downloadPage)}")
        }
        val file = File(destinationDir, "$packageName-$version.${if (page.isBundle) "apkm" else "apk"}")
        Http.download(fileUrl, file, mapOf("Referer" to page.url), onProgress)
        return DownloadedApk(file, version, page.isBundle, displayName)
    }
}

/** Le téléchargement doit être terminé dans un navigateur (vérification anti-robot du site). */
class BrowserRequiredException(val page: ApkMirrorSource.DownloadPage) :
    Exception("APKMirror demande une vérification dans le navigateur")
