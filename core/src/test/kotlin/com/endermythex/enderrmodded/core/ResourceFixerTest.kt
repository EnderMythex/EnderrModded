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

    @Test
    fun removesUnresolvedManifestReferences() {
        val manifest = Files.createTempFile("AndroidManifest", ".xml").toFile()
        manifest.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
              |<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="a">
              |  <application android:label="@string/app">
              |    <activity android:name="a.B" android:theme="@1427046400" android:exported="false"/>
              |    <activity android:name="a.C" android:theme="@style/Ok"/>
              |    <meta-data android:name="live" android:resource="@1427046400"/>
              |    <meta-data android:name="keep" android:value="1"/>
              |  </application>
              |</manifest>""".trimMargin(),
        )
        assertEquals(2, ResourceFixer.fixUnresolvedManifestReferences(manifest))
        val text = manifest.readText()
        assertTrue("@1427046400" !in text, text)
        assertTrue("a.B" in text && "android:exported=\"false\"" in text && "@style/Ok" in text, text)
        assertTrue("\"live\"" !in text && "\"keep\"" in text, text)
        assertTrue("xmlns:android" in text, text)
        manifest.delete()
    }
}
