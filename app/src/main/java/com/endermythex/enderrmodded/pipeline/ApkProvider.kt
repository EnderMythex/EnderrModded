package com.endermythex.enderrmodded.pipeline

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.endermythex.enderrmodded.core.SplitMerger
import com.endermythex.enderrmodded.core.sources.ApkMirrorSource
import com.endermythex.enderrmodded.core.sources.ApkSource
import com.endermythex.enderrmodded.core.sources.BrowserRequiredException
import com.endermythex.enderrmodded.data.PatchedAppsStore
import com.endermythex.enderrmodded.data.Settings
import java.io.File

/**
 * Récupère un APK prêt à patcher : depuis l'appareil ou une source en ligne,
 * vérifie la signature du développeur, puis fusionne les splits si besoin.
 */
class ApkProvider(
    private val context: Context,
    private val settings: Settings,
    private val store: PatchedAppsStore,
) {
    private val pm: PackageManager = context.packageManager
    private val remoteSources: Map<String, ApkSource> = listOf(ApkMirrorSource()).associateBy { it.id }

    data class Installed(val versionName: String?, val apks: List<File>, val isOriginal: Boolean)

    /** App d'origine installée sur l'appareil (null si absente). */
    fun installed(packageName: String): Installed? {
        val info = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull() ?: return null
        val ai = info.applicationInfo ?: return null
        val apks = listOf(File(ai.sourceDir)) + ai.splitSourceDirs.orEmpty().map(::File)
        val own = settings.ownCertificate
        val certs = Signatures.ofInstalled(pm, packageName).orEmpty()
        // Si l'app installée est signée avec notre clé, c'est une version déjà patchée.
        val isOriginal = own.isBlank() || own !in certs
        return Installed(info.versionName, apks, isOriginal)
    }

    /** Dernière version disponible d'après les sources activées. */
    fun latestVersion(packageName: String): String? {
        for (id in settings.sources) {
            val version = when (id) {
                "device" -> null
                else -> remoteSources[id]?.latestVersion(packageName)
            }
            if (version != null) return version
        }
        return installed(packageName)?.takeIf { it.isOriginal }?.versionName
    }

    /**
     * @param version version voulue, ou null pour n'importe laquelle.
     * @return un APK unique non patché et la version effective.
     */
    fun obtain(packageName: String, version: String?, workDir: File, report: RunReporter): Pair<File, String> {
        workDir.mkdirs()
        val errors = mutableListOf<String>()

        for (id in settings.sources) {
            try {
                when (id) {
                    "device" -> fromDevice(packageName, version, workDir, report)?.let { return it }
                    else -> {
                        val source = remoteSources[id] ?: continue
                        val target = version ?: source.latestVersion(packageName)
                            ?: throw IllegalStateException("aucune version trouvée")
                        report.step(Step.DOWNLOAD, StepStatus.RUNNING)
                        report.log("${source.displayName} : téléchargement de $packageName $target…")
                        val downloaded = source.download(
                            packageName, target, File(workDir, "download").apply { mkdirs() },
                            Build.SUPPORTED_ABIS.toList(), report::log,
                        ) { done, total -> if (total > 0) report.progress(done.toFloat() / total) }
                        report.progress(null)
                        report.log("Téléchargé : ${downloaded.file.name} (${downloaded.file.length() / 1024 / 1024} Mo)")
                        report.step(Step.DOWNLOAD, StepStatus.DONE)
                        return prepare(packageName, target, downloaded.file, workDir, report, verify = true) to target
                    }
                }
            } catch (e: SignatureMismatchException) {
                throw e // Jamais de repli silencieux en cas de signature suspecte.
            } catch (e: BrowserRequiredException) {
                throw e // L'utilisateur doit terminer le téléchargement dans le navigateur.
            } catch (e: Exception) {
                val msg = "$id : ${e.message ?: e}"
                errors += msg
                report.log("⚠ $msg")
            }
        }
        throw IllegalStateException(
            "Impossible de récupérer $packageName${version?.let { " $it" } ?: ""}.\n" + errors.joinToString("\n"),
        )
    }

    private fun fromDevice(packageName: String, version: String?, workDir: File, report: RunReporter): Pair<File, String>? {
        val installed = installed(packageName) ?: return null.also { report.log("Appareil : app non installée") }
        if (!installed.isOriginal) return null.also { report.log("Appareil : la version installée est déjà patchée") }
        val installedVersion = installed.versionName ?: return null
        if (version != null && installedVersion != version) {
            report.log("Appareil : version installée $installedVersion ≠ $version")
            return null
        }
        report.step(Step.DOWNLOAD, StepStatus.RUNNING)
        report.log("Appareil : copie de l'app installée ($installedVersion, ${installed.apks.size} fichier(s))")
        // L'app installée sert aussi de référence de signature.
        Signatures.ofInstalled(pm, packageName)?.firstOrNull()?.let { if (store.pin(packageName) == null) store.setPin(packageName, it) }
        val splitsDir = File(workDir, "device").apply { deleteRecursively(); mkdirs() }
        installed.apks.forEachIndexed { i, f -> f.copyTo(File(splitsDir, if (i == 0) "base.apk" else f.name), overwrite = true) }
        report.step(Step.DOWNLOAD, StepStatus.DONE)
        report.step(Step.VERIFY, StepStatus.SKIPPED)
        report.log("Signature : app installée depuis l'appareil, considérée comme sûre")
        val output = File(workDir, "input.apk")
        if (installed.apks.size == 1) {
            File(splitsDir, "base.apk").copyTo(output, overwrite = true)
            report.step(Step.PREPARE, StepStatus.SKIPPED)
        } else {
            report.step(Step.PREPARE, StepStatus.RUNNING)
            SplitMerger.merge(splitsDir, output, report::log)
            report.step(Step.PREPARE, StepStatus.DONE)
        }
        splitsDir.deleteRecursively()
        return output to installedVersion
    }

    /** Prépare un fichier déjà téléchargé (navigateur intégré) : vérification + fusion. */
    fun prepareProvided(packageName: String, file: File, workDir: File, report: RunReporter): Pair<File, String> {
        workDir.mkdirs()
        val local = File(workDir, "provided-" + file.name)
        file.copyTo(local, overwrite = true)
        file.delete()
        report.step(Step.DOWNLOAD, StepStatus.DONE)
        report.log("Fichier téléchargé via le navigateur : ${local.name} (${local.length() / 1024 / 1024} Mo)")
        val apk = prepare(packageName, null, local, workDir, report, verify = true)
        val version = pm.getPackageArchiveInfo(apk.absolutePath, 0)?.versionName ?: "?"
        return apk to version
    }

    private fun prepare(
        packageName: String,
        version: String?,
        file: File,
        workDir: File,
        report: RunReporter,
        verify: Boolean,
    ): File {
        val isBundle = SplitMerger.isBundle(file)
        val splitsDir = File(workDir, "splits")
        val baseApk = if (isBundle) {
            val apks = SplitMerger.extract(file, splitsDir)
            apks.firstOrNull { it.name.equals("base.apk", true) || it.name == "$packageName.apk" }
                ?: apks.maxBy { it.length() }
        } else file

        // --- Vérification ---
        report.step(Step.VERIFY, StepStatus.RUNNING)
        val info = pm.getPackageArchiveInfo(baseApk.absolutePath, 0)
            ?: throw IllegalStateException("APK illisible ou corrompu")
        check(info.packageName == packageName) { "Paquet inattendu : ${info.packageName} au lieu de $packageName" }
        if (version != null && info.versionName != version) report.log("⚠ Version lue : ${info.versionName} (attendue $version)")
        if (verify && settings.verifySignatures) verifySignature(packageName, baseApk, report)
        else report.log("Vérification de signature désactivée")
        report.step(Step.VERIFY, StepStatus.DONE)

        // --- Fusion ---
        val output = File(workDir, "input.apk")
        if (isBundle) {
            report.step(Step.PREPARE, StepStatus.RUNNING)
            report.log("Fusion des APK scindés…")
            SplitMerger.merge(splitsDir, output, report::log)
            splitsDir.deleteRecursively()
            report.step(Step.PREPARE, StepStatus.DONE)
        } else {
            file.copyTo(output, overwrite = true)
            report.step(Step.PREPARE, StepStatus.SKIPPED)
        }
        if (!settings.keepDownloads) file.delete()
        return output
    }

    private fun verifySignature(packageName: String, apk: File, report: RunReporter) {
        val certs = Signatures.ofArchive(pm, apk.absolutePath)
        if (certs.isEmpty()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                report.log("⚠ Vérification impossible sur Android < 9")
                return
            }
            throw SignatureMismatchException("APK non signé : téléchargement refusé")
        }
        val reference = Signatures.ofInstalled(pm, packageName)
            ?.takeIf { settings.ownCertificate.isBlank() || settings.ownCertificate !in it }
        val pinned = store.pin(packageName)
        val expected = reference ?: pinned?.let { setOf(it) }
        if (expected == null) {
            store.setPin(packageName, certs.first())
            report.log("Certificat du développeur enregistré (première utilisation) : ${certs.first().take(16)}…")
            return
        }
        if (certs.intersect(expected).isEmpty()) {
            throw SignatureMismatchException(
                "Signature inattendue ! L'APK téléchargé n'est pas signé par le développeur officiel " +
                    "(reçu ${certs.first().take(16)}…, attendu ${expected.first().take(16)}…). Patch annulé.",
            )
        }
        report.log("✔ Signature officielle vérifiée (${certs.first().take(16)}…)")
    }
}

class SignatureMismatchException(message: String) : Exception(message)
