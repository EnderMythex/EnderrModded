package com.endermythex.enderrmodded.core

/** Outils de comparaison de versions ("20.12.46", "v5.2.0-dev.3", …). */
object Versions {
    private fun parts(version: String): List<Int> =
        version.split('.', '-', '_', ' ', '+')
            .map { part -> part.takeWhile { it.isDigit() } }
            .filter { it.isNotEmpty() }
            .map { it.toIntOrNull() ?: Int.MAX_VALUE }

    private fun compareParts(pa: List<Int>, pb: List<Int>): Int {
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val diff = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    /** Sépare "5.2.0-dev.3" en ("5.2.0", "dev.3"). */
    private fun split(version: String): Pair<String, String?> {
        val clean = version.trim().removePrefix("v").removePrefix("V")
        val match = Regex("[-_ ]?(?=(?i)(alpha|beta|dev|rc))").find(clean) ?: return clean to null
        return clean.substring(0, match.range.first) to clean.substring(match.range.last + 1)
    }

    /** Compare deux versions : négatif si a < b, 0 si égales, positif si a > b. */
    fun compare(a: String, b: String): Int {
        val (mainA, preA) = split(a)
        val (mainB, preB) = split(b)
        val main = compareParts(parts(mainA), parts(mainB))
        if (main != 0) return main
        return when {
            preA == null && preB == null -> 0
            preA == null -> 1 // stable > pré-version
            preB == null -> -1
            else -> compareParts(parts(preA), parts(preB))
        }
    }

    fun isNewer(candidate: String, current: String) = compare(candidate, current) > 0

    /**
     * Version recommandée : celle supportée par le plus de patchs, puis la plus récente.
     * @param versionSets pour chaque patch, ses versions compatibles (null = toutes les versions).
     * @return null si aucune contrainte (n'importe quelle version, idéalement la dernière).
     */
    fun recommended(versionSets: Collection<Set<String>?>): String? {
        val counts = HashMap<String, Int>()
        versionSets.filterNotNull().forEach { set -> set.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        if (counts.isEmpty()) return null
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenComparator { a, b -> compare(b.key, a.key) })
            .first().key
    }
}
