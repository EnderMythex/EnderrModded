package com.endermythex.enderrmodded.patcher

import app.revanced.patcher.patch.loadPatches
import com.endermythex.enderrmodded.data.isCompatibleWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.system.exitProcess

/**
 * Point d'entrée du processus de patch séparé, lancé avec app_process (comme ReVanced Manager).
 * Il a sa propre limite de mémoire (relevée par libprop_override) et ne fait pas ramer l'interface.
 * Communication : lignes préfixées sur la sortie standard.
 */
object PatcherProcess {
    const val PREFIX = "@@EM@@"

    private fun send(type: String, payload: String = "") {
        println(PREFIX + type + ":" + payload.replace("\\", "\\\\").replace("\n", "\\n"))
        System.out.flush()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        send("START")
        // Priorité basse : le patch utilise tous les cœurs, l'interface doit rester fluide.
        PatcherStats.lowerCurrentThreadPriority()
        Thread {
            val rt = Runtime.getRuntime()
            while (true) {
                send("MEM", "${(rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)},${rt.maxMemory() / (1024 * 1024)}")
                Thread.sleep(1000)
            }
        }.apply { isDaemon = true }.start()
        try {
            val p = JSONObject(File(args[0]).readText())
            send("LOG", "Processus de patch séparé • mémoire max ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} Mo")
            val packageName = p.getString("packageName")
            val names = p.getJSONArray("patches").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            val bundle = File(p.getString("bundle"))
            var loadError: Throwable? = null
            val all = loadPatches(bundle) { _, t -> loadError = t }
            loadError?.let { throw IllegalStateException("Chargement des patchs impossible : ${it.message}", it) }
            val selected = all.filter { it.name in names && it.isCompatibleWith(packageName) }.toSet()
            val failed = PatchCore.run(
                input = File(p.getString("input")),
                patches = selected,
                aapt = File(p.getString("aapt")),
                frameworkDir = File(p.getString("frameworkDir")),
                workDir = File(p.getString("workDir")),
                output = File(p.getString("output")),
                keystore = File(p.getString("keystore")),
                log = { send("LOG", it) },
            )
            send("DONE", JSONObject().put("failed", JSONArray(failed)).toString())
            exitProcess(0)
        } catch (t: Throwable) {
            send("FAIL", t.message ?: t.toString())
            System.err.println(t.stackTraceToString())
            exitProcess(2)
        }
    }

    fun decode(payload: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < payload.length) {
            val c = payload[i]
            if (c == '\\' && i + 1 < payload.length) {
                sb.append(if (payload[i + 1] == 'n') '\n' else payload[i + 1])
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }
}
