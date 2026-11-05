package com.endermythex.enderrmodded.core

import com.reandroid.apk.ApkBundle
import com.reandroid.app.AndroidManifest
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.value.ValueType
import java.io.File
import java.util.zip.ZipFile

/**
 * Fusionne un APK scindé (.apks, .xapk, .apkm ou dossier d'APK) en un seul APK non signé.
 * Inspiré de la commande "merge" d'APKEditor (REAndroid).
 */
object SplitMerger {

    /** Vrai si le fichier est une archive contenant plusieurs APK (bundle). */
    fun isBundle(file: File): Boolean = runCatching {
        ZipFile(file).use { zip -> zip.entries().asSequence().any { it.name.endsWith(".apk") } }
    }.getOrDefault(false)

    /** Extrait les APK d'un bundle dans [dir]. */
    fun extract(bundle: File, dir: File): List<File> {
        dir.deleteRecursively()
        dir.mkdirs()
        val out = mutableListOf<File>()
        ZipFile(bundle).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".apk") }
                .forEach { entry ->
                    val target = File(dir, entry.name.substringAfterLast('/'))
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                    out += target
                }
        }
        require(out.isNotEmpty()) { "Aucun APK dans ${bundle.name}" }
        return out
    }

    /** Fusionne les APK présents dans [apkDir] vers [output]. */
    fun merge(apkDir: File, output: File, log: (String) -> Unit = {}) {
        val bundle = ApkBundle()
        try {
            bundle.loadApkDirectory(apkDir, false)
            log("Modules trouvés : ${bundle.apkModuleList.size}")
            val merged = bundle.mergeModules(false)
            try {
                if (merged.hasAndroidManifest()) {
                    val manifest = merged.androidManifest
                    val manifestElement = manifest.manifestElement
                    manifestElement?.apply {
                        removeAttributesWithId(AndroidManifest.ID_requiredSplitTypes)
                        removeAttributesWithId(AndroidManifest.ID_splitTypes)
                        removeAttributesWithName(AndroidManifest.NAME_requiredSplitTypes)
                        removeAttributesWithName(AndroidManifest.NAME_splitTypes)
                        removeAttributesWithId(AndroidManifest.ID_isSplitRequired)
                    }
                    manifest.applicationElement?.apply {
                        removeAttributesWithId(AndroidManifest.ID_isSplitRequired)
                        removeElementsIf { element: ResXmlElement -> isSplitMetaData(element) }
                    }
                    manifest.refresh()
                }
                // Supprime les signatures des modules d'origine.
                merged.apkSignatureBlock = null
                merged.zipEntryMap.removeIf { entry -> entry.name.startsWith("META-INF/") && isSignatureFile(entry.name) }
                merged.refreshTable()
                merged.refreshManifest()
                output.delete()
                merged.writeApk(output)
                log("APK fusionné : ${output.length() / 1024} Ko")
            } finally {
                merged.close()
            }
        } finally {
            bundle.close()
        }
    }

    /** Fusionne directement un fichier bundle. */
    fun mergeBundle(bundleFile: File, output: File, workDir: File, log: (String) -> Unit = {}) {
        val dir = File(workDir, "splits")
        extract(bundleFile, dir)
        try {
            merge(dir, output, log)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun isSignatureFile(name: String): Boolean {
        val upper = name.uppercase()
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") ||
            upper.endsWith(".EC") || upper == "META-INF/MANIFEST.MF"
    }

    private fun isSplitMetaData(element: ResXmlElement): Boolean {
        if (!element.equalsName(AndroidManifest.TAG_meta_data)) return false
        val nameAttr = element.searchAttributeByResourceId(AndroidManifest.ID_name) ?: return false
        if (nameAttr.valueType != ValueType.STRING) return false
        val value = nameAttr.valueAsString ?: return false
        return value.startsWith("com.android.vending.") || value.startsWith("com.android.stamp.")
    }
}
