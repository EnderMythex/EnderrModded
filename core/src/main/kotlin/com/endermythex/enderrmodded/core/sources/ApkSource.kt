package com.endermythex.enderrmodded.core.sources

import java.io.File

/** Résultat d'un téléchargement : APK simple ou bundle (APK scindés à fusionner). */
data class DownloadedApk(val file: File, val version: String, val isBundle: Boolean, val origin: String)

/** Une source d'APK téléchargeable (APKMirror, Uptodown, …). */
interface ApkSource {
    val id: String
    val displayName: String

    /** Dernière version stable disponible, ou null si l'app est introuvable. */
    fun latestVersion(packageName: String): String?

    /** Liste des versions disponibles (les plus récentes d'abord). */
    fun versions(packageName: String): List<String>

    /**
     * Télécharge l'APK [packageName] en version [version] dans [destinationDir].
     * @param abis architectures supportées par l'appareil, par ordre de préférence.
     */
    fun download(
        packageName: String,
        version: String,
        destinationDir: File,
        abis: List<String>,
        log: (String) -> Unit,
        onProgress: (Long, Long) -> Unit,
    ): DownloadedApk
}

class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause)
