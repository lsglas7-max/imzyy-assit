package com.imzz.galaxyassistant.apps

object PackageResolver {
    private val aliases = mapOf(
        "dds" to "drag drive simulator",
        "ubg" to "untitled boxing game",
        "e football" to "efootball",
        "e-football" to "efootball"
    )

    fun resolve(query: String, apps: List<AppInfo>): List<AppInfo> {
        val raw = normalize(query)
        val normalized = normalize(aliases[raw] ?: query)
        if (normalized.isBlank()) return emptyList()

        return apps.map { app ->
            val label = normalize(app.label)
            val pkg = normalize(app.packageName)
            val score = when {
                label == normalized -> 100
                pkg == normalized -> 98
                label.startsWith(normalized) -> 90
                label.contains(normalized) -> 82
                pkg.contains(normalized) -> 74
                normalized.length >= 4 && levenshtein(label, normalized) <= 2 -> 60
                else -> 0
            }
            app to score
        }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<AppInfo, Int>> { it.second }.thenBy { it.first.label })
            .map { it.first }
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), "")
        .trim()

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }
}
