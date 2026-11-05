package com.endermythex.enderrmodded.patcher

import android.content.Context
import app.revanced.patcher.patch.Patch
import com.endermythex.enderrmodded.EnderrApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Applique des patchs ReVanced à un APK puis le signe — dans un processus séparé avec plus de
 * mémoire (par défaut), sinon directement dans l'app.
 */
class PatcherEngine(private val context: Context) {
    private val frameworkDir get() = File(context.filesDir, "framework").apply { mkdirs() }
    val keystore get() = File(context.filesDir, "signing/enderrmodded.keystore").apply { parentFile?.mkdirs() }
    val outputDir get() = File(context.filesDir, "output").apply { mkdirs() }

    data class Result(val apk: File, val failedPatches: List<String>)

    /** Le processus séparé n'a pas pu démarrer (on repasse alors dans l'app). */
    private class ProcessStartException(message: String) : Exception(message)

    suspend fun patch(
        input: File,
        patches: Set<Patch>,
        outputName: String = "patched.apk",
        log: (String) -> Unit,
    ): Result = lock.withLock {
        PatcherStats.reset()
        val aapt = Aapt.binary(context) ?: error("aapt2 introuvable : l'architecture de cet appareil n'est pas supportée")
        val output = File(outputDir, outputName)
        val workDir = File(context.cacheDir, "patcher/${outputName.removeSuffix(".apk")}")
        val settings = EnderrApp.instance.settings
        val packageName = context.packageManager.getPackageArchiveInfo(input.absolutePath, 0)?.packageName

        if (settings.useProcessRuntime && packageName != null) {
            // ART doit loger le tas Java sous 4 Go (références compressées) : une limite trop haute
            // fait avorter le processus au démarrage. On redescend alors automatiquement.
            var limit = settings.processMemoryMb
            while (true) {
                try {
                    val failed = runInProcess(input, patches, packageName, aapt, workDir, output, limit, log)
                    if (limit != settings.processMemoryMb) {
                        settings.processMemoryMb = limit
                        log("Limite mémoire ajustée à $limit Mo pour les prochains patchs.")
                    }
                    return@withLock Result(output, failed)
                } catch (e: ProcessStartException) {
                    val next = listOf(2048, 1536, 1024, 768, 512).firstOrNull { it < limit }
                    if (next == null) {
                        log("⚠ Processus séparé indisponible (${e.message}) : patch dans l'app (mémoire limitée par Android).")
                        break
                    }
                    log("⚠ Le processus de patch n'a pas démarré avec $limit Mo (${e.message?.trim()}), nouvel essai avec $next Mo…")
                    limit = next
                }
            }
        }

        // Dans l'app : thread dédié à priorité basse + relevé mémoire.
        val executor = Executors.newSingleThreadExecutor { r ->
            Thread { PatcherStats.lowerCurrentThreadPriority(); r.run() }.apply { name = "patcher" }
        }
        try {
            coroutineScope {
                val sampler = launch {
                    val rt = Runtime.getRuntime()
                    while (true) {
                        PatcherStats.update(((rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)).toInt(), (rt.maxMemory() / (1024 * 1024)).toInt(), false)
                        delay(1000)
                    }
                }
                try {
                    withContext(executor.asCoroutineDispatcher()) {
                        val job = currentCoroutineContext()
                        val failed = PatchCore.run(input, patches, aapt, frameworkDir, workDir, output, keystore, log) { job.ensureActive() }
                        Result(output, failed)
                    }
                } finally {
                    sampler.cancel()
                }
            }
        } finally {
            executor.shutdown()
        }
    }

    private suspend fun runInProcess(
        input: File,
        patches: Set<Patch>,
        packageName: String,
        aapt: File,
        workDir: File,
        output: File,
        memoryMb: Int,
        log: (String) -> Unit,
    ): List<String> = coroutineScope {
        val propOverride = File(context.applicationInfo.nativeLibraryDir, "libprop_override.so")
        if (!propOverride.exists()) throw ProcessStartException("libprop_override absente")
        val bundle = EnderrApp.instance.patches.bundleFile
        val params = File(context.cacheDir, "patcher-params.json")
        params.writeText(
            JSONObject()
                .put("packageName", packageName)
                .put("patches", JSONArray(patches.mapNotNull { it.name }))
                .put("bundle", bundle.absolutePath)
                .put("input", input.absolutePath)
                .put("output", output.absolutePath)
                .put("aapt", aapt.absolutePath)
                .put("frameworkDir", frameworkDir.absolutePath)
                .put("workDir", workDir.absolutePath)
                .put("keystore", keystore.absolutePath)
                .toString(),
        )

        val builder = ProcessBuilder(
            "/system/bin/app_process",
            "-Djava.io.tmpdir=${context.cacheDir.absolutePath}",
            "/",
            "--nice-name=${context.packageName}:patcher",
            PatcherProcess::class.java.name,
            params.absolutePath,
        ).redirectErrorStream(true)
        builder.environment().apply {
            put("CLASSPATH", context.applicationInfo.sourceDir)
            put("LD_PRELOAD", propOverride.absolutePath)
            put("PROP_dalvik.vm.heapgrowthlimit", "${memoryMb}m")
            put("PROP_dalvik.vm.heapsize", "${memoryMb}m")
        }
        val process = try {
            builder.start()
        } catch (e: Exception) {
            throw ProcessStartException(e.message ?: e.toString())
        }

        val reader = async(Dispatchers.IO) {
            var started = false
            var failed: List<String>? = null
            var failure: String? = null
            val other = ArrayDeque<String>()
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith(PatcherProcess.PREFIX)) {
                        // Sortie brute (traces Java, messages d'ART…).
                        other.addLast(line); while (other.size > 20) other.removeFirst()
                        continue
                    }
                    val body = line.removePrefix(PatcherProcess.PREFIX)
                    val type = body.substringBefore(':')
                    val payload = PatcherProcess.decode(body.substringAfter(':', ""))
                    when (type) {
                        "START" -> started = true
                        "LOG" -> log(payload)
                        "DONE" -> failed = JSONObject(payload).getJSONArray("failed").let { a -> (0 until a.length()).map { a.getString(it) } }
                        "FAIL" -> failure = payload
                        "MEM" -> payload.split(',').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 }
                            ?.let { (used, max) -> PatcherStats.update(used, max, separateProcess = true) }
                    }
                }
            }
            val code = process.waitFor()
            if (!started) throw ProcessStartException("code $code ${other.joinToString(" | ").take(300)}")
            failure?.let { throw IllegalStateException(it) }
            failed ?: throw IllegalStateException(
                "Le processus de patch s'est arrêté (code $code). Mémoire insuffisante ? " +
                    "Essaie une limite différente dans les réglages.\n" + other.joinToString("\n"),
            )
        }
        try {
            reader.await()
        } finally {
            process.destroy()
            params.delete()
        }
    }

    private companion object {
        /** Un seul patch à la fois : très gourmand en mémoire. */
        val lock = Mutex()
    }
}
