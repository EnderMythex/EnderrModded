package com.endermythex.enderrmodded.patcher

import android.content.Context
import java.io.File

/**
 * Localise le binaire aapt2 embarqué dans jniLibs (extrait dans nativeLibraryDir à l'installation).
 * apktool en a besoin pour recompiler les ressources lorsqu'un patch les modifie.
 */
object Aapt {
    fun binary(context: Context): File? =
        File(context.applicationInfo.nativeLibraryDir)
            .listFiles()
            ?.firstOrNull { it.isFile && it.name.contains("aapt") }
}
