package io.kiebord.feature.ime.khipro

/**
 * The two bundled Khipro layouts. Ordinals match the Rust registry
 * ([Variant::Classic] = 0, [Variant::Dark] = 1) over JNI.
 */
enum class KhiproVariant(val id: Int) {
    CLASSIC(0),
    DARK(1),
}
