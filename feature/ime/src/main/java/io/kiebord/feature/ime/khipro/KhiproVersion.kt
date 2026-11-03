package io.kiebord.feature.ime.khipro

/**
 * The numeric X.Y.Z core of an upstream Khipro spec tag. Upstream tags mix
 * prefixes (`v36.2.3`, `drk-v2.0.2`, `dark-v1.0.0`) and suffixes
 * (`-beta`, `+build.5`); comparison uses the numeric parts only.
 */
data class KhiproVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<KhiproVersion> {

    override fun compareTo(other: KhiproVersion): Int {
        return compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    }

    companion object {
        fun parse(tag: String): KhiproVersion? {
            // Strip everything up to the first digit, then cut at the first
            // non-numeric separator (pre-release '-' or build '+').
            val digits = tag.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() || it == '.' }
            val parts = digits.split('.').map { it.toIntOrNull() }
            if (parts.size < 3 || parts.any { it == null }) return null
            return KhiproVersion(parts[0]!!, parts[1]!!, parts[2]!!)
        }
    }
}
