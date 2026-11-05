package com.endermythex.enderrmodded.core

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceFixerTest {
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 0)
    private val webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)
    private val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)

    @Test
    fun renamesImagesWithWrongExtension() {
        val res = Files.createTempDirectory("res").toFile()
        val drawable = File(res, "drawable-xxhdpi").apply { mkdirs() }
        File(drawable, "bs6.png").writeBytes(webp)        // WebP déguisé (cas TikTok)
        File(drawable, "ok.png").writeBytes(png)          // vrai PNG
        File(drawable, "photo.png").writeBytes(jpg)       // JPEG déguisé
        File(drawable, "frame.9.png").writeBytes(webp)    // 9-patch : ignoré
        File(drawable, "data.png").writeBytes(byteArrayOf(1, 2, 3)) // inconnu : ignoré

        assertEquals(2, ResourceFixer.fixMismatchedImages(drawable.parentFile))
        val names = drawable.list()!!.toSet()
        assertTrue("bs6.webp" in names && "photo.jpg" in names && "ok.png" in names)
        assertTrue("frame.9.png" in names && "data.png" in names)
        res.deleteRecursively()
    }
}
