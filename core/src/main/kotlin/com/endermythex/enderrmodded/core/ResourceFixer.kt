package com.endermythex.enderrmodded.core

import java.io.File

/**
 * Corrige les ressources décodées avant leur recompilation par aapt2.
 *
 * Certaines apps (ex. TikTok) contiennent des images nommées ".png" qui sont en réalité du WebP,
 * du JPEG ou du GIF : aapt2 échoue alors avec "failed to read PNG signature". Les ressources étant
 * référencées par leur nom (sans extension), on peut simplement leur redonner la bonne extension.
 */
object ResourceFixer {
    private val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

    /** Format réel d'après les premiers octets, ou null si inconnu. */
    fun detect(header: ByteArray): String? = when {
        header.size >= 4 && header.copyOfRange(0, 4).contentEquals(PNG) -> "png"
        header.size >= 12 && String(header, 0, 4, Charsets.ISO_8859_1) == "RIFF" &&
            String(header, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> "webp"
        header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> "jpg"
        header.size >= 4 && String(header, 0, 4, Charsets.ISO_8859_1) == "GIF8" -> "gif"
        else -> null
    }

    private fun header(file: File): ByteArray = file.inputStream().use { input ->
        val buffer = ByteArray(12)
        val read = input.read(buffer)
        if (read <= 0) ByteArray(0) else buffer.copyOf(read)
    }

    /**
     * Renomme les images dont l'extension ne correspond pas au contenu.
     * @return le nombre de fichiers corrigés.
     */
    fun fixMismatchedImages(resDir: File, log: (String) -> Unit = {}): Int {
        if (!resDir.isDirectory) return 0
        var fixed = 0
        resDir.listFiles { f -> f.isDirectory }.orEmpty().forEach { dir ->
            dir.listFiles { f -> f.isFile }.orEmpty().forEach { file ->
                val name = file.name
                val lower = name.lowercase()
                val ext = when {
                    lower.endsWith(".9.png") -> return@forEach // 9-patch : format PNG obligatoire, on n'y touche pas
                    lower.endsWith(".png") -> "png"
                    lower.endsWith(".webp") -> "webp"
                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "jpg"
                    lower.endsWith(".gif") -> "gif"
                    else -> return@forEach
                }
                val real = detect(header(file)) ?: return@forEach
                if (real == ext) return@forEach
                val target = File(dir, name.substringBeforeLast('.') + "." + real)
                if (target.exists()) {
                    log("⚠ Ressource ${dir.name}/$name : format $real, mais ${target.name} existe déjà")
                    return@forEach
                }
                if (file.renameTo(target)) fixed++
            }
        }
        if (fixed > 0) log("$fixed image(s) au format incorrect corrigée(s) (ex. WebP nommé .png)")
        return fixed
    }

    private val rawReference = Regex("^@\\d+$")

    /**
     * Corrige le manifeste décodé : apktool écrit les références vers des modules dynamiques absents de
     * l'APK de base sous forme brute (ex. TikTok : "@1427046400" = 0x550f0000, module df_live_cast), ce
     * que aapt2 link refuse.
     * - sur un `<meta-data>`, on retire l'élément entier (un meta-data sans valeur rend l'APK illisible
     *   par Android : "Un problème est survenu lors de l'analyse du package") ;
     * - ailleurs, on retire seulement l'attribut.
     * Ces éléments concernent des modules non installés : sans effet sur l'app.
     * @return le nombre de corrections.
     */
    fun fixUnresolvedManifestReferences(manifest: File, log: (String) -> Unit = {}): Int {
        if (!manifest.isFile) return 0
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val doc = factory.newDocumentBuilder().parse(manifest)
        var fixes = 0
        val elements = doc.getElementsByTagName("*")
        val toRemove = mutableListOf<org.w3c.dom.Element>()
        for (i in 0 until elements.length) {
            val element = elements.item(i) as org.w3c.dom.Element
            val attrs = element.attributes
            val bad = (0 until attrs.length).map { attrs.item(it) }.filter { rawReference.matches(it.nodeValue) }
            if (bad.isEmpty()) continue
            val ids = bad.joinToString { "${it.nodeName} → 0x" + (it.nodeValue.drop(1).toLongOrNull()?.toString(16) ?: "?") }
            if (element.tagName == "meta-data") {
                toRemove += element
                log("Manifeste : <meta-data ${element.getAttribute("android:name")}> retiré (référence non résolue : $ids)")
            } else {
                bad.forEach { element.removeAttribute(it.nodeName) }
                log("Manifeste : attribut non résolu retiré sur <${element.tagName}> ($ids)")
            }
            fixes++
        }
        toRemove.forEach { it.parentNode?.removeChild(it) }
        if (fixes > 0) {
            val transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "utf-8")
                setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
            }
            transformer.transform(javax.xml.transform.dom.DOMSource(doc), javax.xml.transform.stream.StreamResult(manifest))
        }
        return fixes
    }
}
