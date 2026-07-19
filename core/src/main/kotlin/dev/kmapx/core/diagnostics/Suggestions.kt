package dev.kmapx.core.diagnostics

/**
 * "did you mean": el ÚNICO lugar donde vive la heurística de sugerencias.
 *
 * SRP: separa la distancia de edición (un detalle reutilizable) de quien la consume — el motor
 * (fuentes de un mapeo) y el frontend (campos de un método `@Mapper`). Antes cada uno tenía su
 * propio Levenshtein; esta clase elimina esa duplicación.
 *
 * Contrato: distancia ≤ [maxDistance] ignorando mayúsculas; solo los candidatos de
 * MENOR distancia; hasta [limit], preservando el orden de declaración en los empates.
 */
public object Suggestions {

    public fun closest(
        name: String,
        candidates: Iterable<String>,
        maxDistance: Int = 2,
        limit: Int = 2,
    ): List<String> {
        val distances = candidates.map { it to levenshtein(name.lowercase(), it.lowercase()) }
        val best = distances.minOfOrNull { it.second } ?: return emptyList()
        if (best > maxDistance) return emptyList()
        return distances.filter { it.second == best }.take(limit).map { it.first }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
        }
        return dp[a.length][b.length]
    }
}
