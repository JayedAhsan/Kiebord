package io.kiebord.feature.ime.khipro

/** Layout ids driven by the Khipro m17n interpreter. */
val KHIPRO_LAYOUT_IDS = setOf("bn_khipro_classic", "bn_khipro_dark")

/**
 * The punctuation keys the Khipro specs consume as part of a composition
 * (slicer/fuser `/`, separator `;`, comma sequences, danda `।f` → `॥`).
 * On Khipro layouts these stay inside the composing buffer instead of
 * committing.
 */
val KHIPRO_BUFFER_KEYS = setOf('/', ';', ',', '.', '।')

/**
 * Candidate-bar assembly for Khipro layouts: the raw roman the user typed
 * first (tap commits it), the deterministic composition second, then
 * fixed-style dictionary completions and learned words. No autocorrect —
 * Khipro output is exactly what the spec produces.
 */
fun buildKhiproCandidates(
    roman: String,
    composed: String,
    completions: List<String>,
    learned: List<String>,
): List<String> {
    val out = ArrayList<String>(8)
    fun add(text: String) {
        if (text.isNotBlank() && text != composed && text !in out) out.add(text)
    }
    if (roman.isNotBlank() && roman != composed) out.add(roman)
    if (composed.isNotBlank()) out.add(composed)
    for (c in completions) if (out.size < 8) add(c)
    for (l in learned) if (out.size < 8) add(l)
    return out
}
