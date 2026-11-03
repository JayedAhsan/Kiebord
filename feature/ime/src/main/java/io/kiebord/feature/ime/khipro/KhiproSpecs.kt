package io.kiebord.feature.ime.khipro


/**
 * Bundled Khipro spec metadata: asset paths, upstream OTA sources and
 * limits. Versions of the bundled specs live in `assets/khipro/builtin.json`
 * (khipro.meta refreshed specs: classic v37.0.3, dark drk-v2.0.2).
 */
object KhiproSpecs {
    const val DIR = "khipro"
    const val CLASSIC_FILE = "bn-khipro-classic.mim"
    const val DARK_FILE = "bn-khipro-dark.mim"
    const val CLASSIC_TSV = "khipro-testcases-touch.tsv"
    const val DARK_TSV = "khipro-testcases-dark-touch.tsv"
    const val UPSTREAM_TSV_FILE = "khipro-testcases.tsv"
    const val BUILTIN_JSON = "builtin.json"

    const val CLASSIC_REPO = "KhiproTeam/khipro-mim-touchscreen"
    const val DARK_REPO = "KhiproTeam/khipro-dark-mim-touchscreen"

    const val MAX_SPEC_BYTES = 256 * 1024

    /** Fallback when builtin.json is unreadable; kept in sync with it. */
    val bundledVersion: Map<KhiproVariant, String> = mapOf(
        KhiproVariant.CLASSIC to "v37.0.3",
        KhiproVariant.DARK to "drk-v2.0.2",
    )

    fun specFile(variant: KhiproVariant): String = when (variant) {
        KhiproVariant.CLASSIC -> CLASSIC_FILE
        KhiproVariant.DARK -> DARK_FILE
    }

    fun tsvFile(variant: KhiproVariant): String = when (variant) {
        KhiproVariant.CLASSIC -> CLASSIC_TSV
        KhiproVariant.DARK -> DARK_TSV
    }

    fun upstreamTsvFile(variant: KhiproVariant): String = UPSTREAM_TSV_FILE

    fun repo(variant: KhiproVariant): String = when (variant) {
        KhiproVariant.CLASSIC -> CLASSIC_REPO
        KhiproVariant.DARK -> DARK_REPO
    }

    /** The input-method header the downloaded spec must declare. */
    fun expectedHeader(variant: KhiproVariant): String = when (variant) {
        KhiproVariant.CLASSIC -> "(input-method bn khipro-classic)"
        KhiproVariant.DARK -> "(input-method bn khipro-dark)"
    }
}
