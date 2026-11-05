package com.endermythex.enderrmodded.patcher

import app.revanced.library.ApkUtils
import app.revanced.library.ApkUtils.applyTo
import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patcher
import java.io.File
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Cœur du patch, sans dépendance à un Context Android : utilisé dans l'app
 * et dans le processus de patch séparé ([PatcherProcess]).
 */
object PatchCore {
    const val SIGNER = "EnderrModded"

    class PatchFailure(message: String, cause: Throwable?) : Exception(message, cause)

    /** @return les noms des patchs en échec. */
    fun run(
        input: File,
        patches: Set<Patch>,
        aapt: File,
        frameworkDir: File,
        workDir: File,
        output: File,
        keystore: File,
        log: (String) -> Unit,
        checkCancelled: () -> Unit = {},
    ): List<String> {
        val handler = FilteringLogHandler(log)
        val rootLogger = Logger.getLogger("")
        rootLogger.addHandler(handler)
        val failed = mutableListOf<String>()
        try {
            workDir.deleteRecursively()
            frameworkDir.mkdirs()
            keystore.parentFile?.mkdirs()
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
            val result = applyPatches { patchResult ->
                checkCancelled()
                val name = patchResult.patch.name ?: patchResult.patch.toString()
                val error = patchResult.exception
                if (error == null) log("✔ $name") else {
                    failed += name
                    log("✘ $name : ${error.message?.lineSequence()?.firstOrNull()}")
                }
            }
            handler.summary()

            log("Écriture de l'APK patché…")
            val unsigned = File(workDir, "unsigned.apk")
            input.copyTo(unsigned, overwrite = true)
            result.applyTo(unsigned)

            log("Signature de l'APK…")
            output.parentFile?.mkdirs()
            output.delete()
            ApkUtils.signApk(unsigned, output, SIGNER, ApkUtils.KeyStoreDetails(keystore, null, SIGNER, SIGNER))
            log("Terminé : ${output.length() / 1024} Ko")
            return failed
        } catch (t: Throwable) {
            handler.summary()
            // Les erreurs d'aapt2 sont noyées dans le journal : on les remonte dans le message d'erreur.
            val details = handler.errorLines().takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "\nDétails :\n")
            if (details != null && t !is InterruptedException) throw PatchFailure((t.message ?: t.toString()) + details, t)
            throw t
        } finally {
            rootLogger.removeHandler(handler)
            workDir.deleteRecursively()
        }
    }

    /**
     * Transmet les logs du patcher en masquant le bruit (avertissements de décodage répétés)
     * et garde les lignes d'erreur (aapt2…) pour le diagnostic.
     */
    private class FilteringLogHandler(private val log: (String) -> Unit) : Handler() {
        private var hidden = 0
        private val errors = ArrayDeque<String>()

        override fun publish(record: LogRecord) {
            if (record.level.intValue() < Level.INFO.intValue()) return
            val message = record.message ?: return
            if (message.startsWith("Could not decode attr value") || message.startsWith("Could not decode")) {
                hidden++
                return
            }
            if (record.level.intValue() >= Level.WARNING.intValue() &&
                Regex("(?i)\\berror\\b|: error|exception|failed").containsMatchIn(message)
            ) {
                synchronized(errors) {
                    errors.addLast(message)
                    while (errors.size > 15) errors.removeFirst()
                }
            }
            val prefix = when {
                record.level.intValue() >= Level.SEVERE.intValue() -> "[ERREUR] "
                record.level.intValue() >= Level.WARNING.intValue() -> "[AVERT.] "
                else -> ""
            }
            log(prefix + message)
        }

        fun summary() {
            if (hidden > 0) log("($hidden avertissements de décodage de ressources masqués)")
            hidden = 0
        }

        fun errorLines(): List<String> = synchronized(errors) { errors.toList() }

        override fun flush() = Unit
        override fun close() = Unit
    }
}
