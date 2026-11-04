package com.endermythex.enderrmodded.patcher

import android.content.Context
import app.revanced.library.ApkUtils
import app.revanced.library.ApkUtils.applyTo
import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Applique des patchs ReVanced à un APK, puis le signe.
 */
class PatcherEngine(private val context: Context) {
    private val workDir get() = File(context.cacheDir, "patcher")
    private val frameworkDir get() = File(context.filesDir, "framework").apply { mkdirs() }
    private val keystore get() = File(context.filesDir, "signing/enderrmodded.keystore").apply { parentFile?.mkdirs() }
    val outputDir get() = File(context.filesDir, "output").apply { mkdirs() }

    data class Result(val apk: File, val failedPatches: List<String>)

    suspend fun patch(
        input: File,
        patches: Set<Patch>,
        log: (String) -> Unit,
    ): Result = withContext(Dispatchers.Default) {
        val aapt = Aapt.binary(context)
            ?: error("aapt2 introuvable : l'architecture de cet appareil n'est pas supportée")

        val handler = LogHandler(log)
        val rootLogger = Logger.getLogger("")
        rootLogger.addHandler(handler)
        val failed = mutableListOf<String>()

        try {
            workDir.deleteRecursively()
            log("Lecture de l'APK…")
            val applyPatches = patcher(
                apkFile = input,
                temporaryFilesPath = File(workDir, "tmp"),
                aaptBinaryPath = aapt,
                frameworkFileDirectory = frameworkDir.absolutePath,
            ) { packageName, versionName ->
                log("Application : $packageName $versionName")
                patches
            }

            log("Application de ${patches.size} patch(s)…")
            val job = currentCoroutineContext()
            val result = applyPatches { patchResult ->
                job.ensureActive()
                val name = patchResult.patch.name ?: patchResult.patch.toString()
                val error = patchResult.exception
                if (error == null) {
                    log("✔ $name")
                } else {
                    failed += name
                    log("✘ $name : ${error.message}")
                }
            }

            log("Écriture de l'APK patché…")
            val unsigned = File(workDir, "unsigned.apk")
            input.copyTo(unsigned, overwrite = true)
            result.applyTo(unsigned)

            log("Signature de l'APK…")
            val output = File(outputDir, "patched.apk")
            output.delete()
            ApkUtils.signApk(
                unsigned,
                output,
                SIGNER,
                ApkUtils.KeyStoreDetails(keystore, null, SIGNER, SIGNER),
            )

            log("Terminé : ${output.length() / 1024} Ko")
            Result(output, failed)
        } finally {
            rootLogger.removeHandler(handler)
            File(workDir, "tmp").deleteRecursively()
        }
    }

    private class LogHandler(private val log: (String) -> Unit) : Handler() {
        override fun publish(record: LogRecord) {
            if (record.level.intValue() < Level.INFO.intValue()) return
            val message = record.message ?: return
            val prefix = when {
                record.level.intValue() >= Level.SEVERE.intValue() -> "[ERREUR] "
                record.level.intValue() >= Level.WARNING.intValue() -> "[AVERT.] "
                else -> ""
            }
            log(prefix + message)
        }

        override fun flush() = Unit
        override fun close() = Unit
    }

    private companion object {
        const val SIGNER = "EnderrModded"
    }
}
