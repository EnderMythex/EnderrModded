package com.endermythex.enderrmodded.pipeline

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** Empreintes SHA-256 des certificats de signature. */
object Signatures {
    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Suppress("DEPRECATION")
    private fun PackageInfo.digests(): Set<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = signingInfo ?: return emptySet()
            val signers = if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
            return signers.orEmpty().map { sha256(it.toByteArray()) }.toSet()
        }
        return signatures.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    /** Certificats d'un APK sur le disque (vide si illisible). */
    fun ofArchive(pm: PackageManager, path: String): Set<String> =
        runCatching { pm.getPackageArchiveInfo(path, flags)?.digests() }.getOrNull().orEmpty()

    /** Certificats d'une app installée (null si non installée). */
    fun ofInstalled(pm: PackageManager, packageName: String): Set<String>? =
        runCatching { pm.getPackageInfo(packageName, flags).digests() }.getOrNull()
}
