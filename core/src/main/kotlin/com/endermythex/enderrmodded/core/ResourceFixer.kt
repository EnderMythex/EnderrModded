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

    private val rawReference = Regex("""\s+[\w.:]+="@(\d+)"""")

    /**
     * Retire du manifeste décodé les attributs pointant vers une ressource non résolue (ex. "@1427046400").
     *
     * apktool écrit ainsi les références vers des modules dynamiques absents de l'APK de base
     * (ex. TikTok : paquet 0x55 = df_live_cast). aapt2 link refuse ces valeurs ; les composants concernés
     * appartiennent à des modules non installés, retirer l'attribut est donc sans effet sur l'app.
     * @return le nombre d'attributs retirés.
     */
    fun fixUnresolvedManifestReferences(manifest: File, log: (String) -> Unit = {}): Int {
        if (!manifest.isFile) return 0
        val text = manifest.readText()
        var removed = 0
        val fixed = rawReference.replace(text) { match ->
            removed++
            val id = match.groupValues[1].toLongOrNull()
            log("Manifeste : attribut non résolu retiré (${match.value.trim().substringBefore('=')} → 0x${id?.toString(16) ?: "?"})")
            ""
        }
        if (removed > 0) manifest.writeText(fixed)
        return removed
    }
}
