package com.endermythex.enderrmodded.core.sources

/** Correspondance entre les libellés d'architecture des sites et les ABI Android. */
internal object Abis {
    private val universal = setOf("universal", "noarch", "")

    /** Vrai si le libellé d'architecture d'une variante est installable sur l'appareil. */
    fun matches(label: String, deviceAbis: List<String>): Boolean {
        val l = label.lowercase().trim()
        if (l in universal) return true
        val parts = l.split('+', ',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        return deviceAbis.any { abi -> parts.any { it == abi.lowercase() || (abi == "armeabi-v7a" && it == "arm-v7a") } }
    }

    /** Score : plus il est bas, mieux c'est (universel > ABI principale > ABI secondaire). */
    fun score(label: String, deviceAbis: List<String>): Int {
        val l = label.lowercase().trim()
        if (l in universal) return 0
        if (l.contains('+') || l.contains(',')) return 1
        val index = deviceAbis.indexOfFirst { l == it.lowercase() }
        return if (index >= 0) 2 + index else 100
    }
}
