package io.kiebord.feature.ime.khipro
import io.kiebord.feature.ime.NativeEngine
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The engine bridge, so JVM tests can fake the native side. The production
 * impl delegates to [NativeEngine]'s khipro* entry points.
 */
interface NativeEngineBridge {
    fun loadSpec(v: KhiproVariant, mim: String): Boolean
    fun convert(v: KhiproVariant, buffer: String): String
    fun loaded(v: KhiproVariant): Boolean
    fun reset(v: KhiproVariant)
    /** "" when the spec passes the conformance gate, else a failure report. */
    fun validate(v: KhiproVariant, mim: String, tsv: String): String
}

object NativeBridge : NativeEngineBridge {
    override fun loadSpec(v: KhiproVariant, mim: String): Boolean =
        NativeEngine.loadKhiproSpec(v, mim)

    override fun convert(v: KhiproVariant, buffer: String): String =
        NativeEngine.convertKhipro(v, buffer)

    override fun loaded(v: KhiproVariant): Boolean = NativeEngine.khiproSpecLoaded(v)

    override fun reset(v: KhiproVariant) = NativeEngine.resetKhipro(v)

    override fun validate(v: KhiproVariant, mim: String, tsv: String): String =
        NativeEngine.validateKhiproSpec(v, mim, tsv)
}

/** Outcome of a manual "check for updates". */
sealed class CheckResult {
    object UpToDate : CheckResult()
    data class Updated(val version: String) : CheckResult()
    /** Downloaded but rejected (header/conformance/size). */
    data class Rejected(val reason: String) : CheckResult()
    /** Network or parse failure; the current spec is untouched. */
    data class Failed(val reason: String) : CheckResult()
}

/**
 * Loads, updates and resets the Khipro spec data behind the engine.
 *
 * Bundled specs ship as assets; a checked update installs a downloaded copy
 * into `filesDir/khipro/` plus a version pref, and ensureLoaded prefers it.
 * Every download is validated (header + size + conformance gate through a
 * scratch engine inside the native validate call) BEFORE it touches the live
 * registry, so a rejected spec leaves active typing untouched.
 *
 * All methods are synchronous and may do network I/O — callers run them off
 * the main thread.
 */
class KhiproSpecRepository(
    private val assetReader: (String) -> String?,
    private val filesDir: File,
    private val loadPref: (String, String?) -> String?,
    private val storePref: (String, String) -> Unit,
    private val removePref: (String) -> Unit,
    private val fetcher: (String) -> ByteArray,
    private val native: NativeEngineBridge = NativeBridge,
) {
    private fun dir(): File = File(filesDir, KhiproSpecs.DIR).apply { mkdirs() }

    private fun versionKey(v: KhiproVariant) =
        "khipro_dl_version_" + if (v == KhiproVariant.CLASSIC) "classic" else "dark"

    private fun downloadedFile(v: KhiproVariant): File = File(dir(), KhiproSpecs.specFile(v))
    private fun downloadedTsvFile(v: KhiproVariant): File = File(dir(), KhiproSpecs.tsvFile(v))

    /** Conformance TSV currently preferred (downloaded if present, else bundled). */
    fun activeTsv(v: KhiproVariant): String? {
        val dl = downloadedTsvFile(v)
        return if (dl.isFile) dl.readText()
        else assetReader(KhiproSpecs.DIR + "/" + KhiproSpecs.tsvFile(v))
    }

    fun bundledVersion(v: KhiproVariant): String {
        val json = assetReader(KhiproSpecs.DIR + "/" + KhiproSpecs.BUILTIN_JSON)
            ?: return KhiproSpecs.bundledVersion[v]!!
        val key = if (v == KhiproVariant.CLASSIC) "classic" else "dark"
        return extractStringField(json, key) ?: KhiproSpecs.bundledVersion[v]!!
    }

    /** Version string of the spec currently preferred (downloaded or bundled). */
    fun installedVersion(v: KhiproVariant): String =
        loadPref(versionKey(v), null) ?: bundledVersion(v)

    /** True when the currently-preferred spec came from an OTA download. */
    fun isDownloaded(v: KhiproVariant): Boolean = loadPref(versionKey(v), null) != null

    /** Loads the preferred spec into the engine if it is not loaded already. */
    fun ensureLoaded(v: KhiproVariant) {
        if (native.loaded(v)) return
        val downloaded = loadPref(versionKey(v), null)
        val source = if (downloaded != null && downloadedFile(v).isFile) {
            downloadedFile(v).readText()
        } else {
            assetReader(KhiproSpecs.DIR + "/" + KhiproSpecs.specFile(v)) ?: return
        }
        native.loadSpec(v, source)
    }

    /**
     * Checks upstream for a newer release and installs it after validation.
     * Network-only-when-called: there is no background refresh.
     */
    fun checkForUpdate(v: KhiproVariant): CheckResult {
        val tag = try {
            latestTag(KhiproSpecs.repo(v))
        } catch (e: Exception) {
            return CheckResult.Failed(e.message ?: "network error")
        } ?: return CheckResult.Failed("no release tag found")

        val current = KhiproVersion.parse(installedVersion(v))
        val bundled = KhiproVersion.parse(bundledVersion(v))
        val candidate = KhiproVersion.parse(tag)
        if (candidate == null) return CheckResult.Failed("unparseable tag: $tag")
        if (current != null && candidate <= current) return CheckResult.UpToDate
        if (bundled != null && candidate <= bundled) return CheckResult.UpToDate

        val url = "https://raw.githubusercontent.com/${KhiproSpecs.repo(v)}/$tag/" +
            KhiproSpecs.specFile(v)
        val mimText = try {
            val bytes = fetcher(url)
            if (bytes.size > KhiproSpecs.MAX_SPEC_BYTES) {
                return CheckResult.Rejected("spec larger than ${KhiproSpecs.MAX_SPEC_BYTES} bytes")
            }
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return CheckResult.Failed(e.message ?: "download error")
        }

        if (!mimText.contains(KhiproSpecs.expectedHeader(v))) {
            return CheckResult.Rejected("wrong input-method header")
        }

        val tsvUrl = "https://raw.githubusercontent.com/${KhiproSpecs.repo(v)}/$tag/" +
            KhiproSpecs.upstreamTsvFile(v)
        var downloadedTsv: String? = null
        val tsvText = try {
            val bytes = fetcher(tsvUrl)
            if (bytes.size > KhiproSpecs.MAX_SPEC_BYTES) {
                return CheckResult.Rejected("tsv larger than ${KhiproSpecs.MAX_SPEC_BYTES} bytes")
            }
            String(bytes, Charsets.UTF_8).also { downloadedTsv = it }
        } catch (_: Exception) {
            assetReader(KhiproSpecs.DIR + "/" + KhiproSpecs.tsvFile(v))
        } ?: return CheckResult.Failed("conformance table missing")

        val report = native.validate(v, mimText, tsvText)
        if (report.isNotEmpty()) {
            return CheckResult.Rejected(report)
        }

        // All gates passed: atomic install, version record, hot reload.
        val target = downloadedFile(v)
        val part = File(dir(), KhiproSpecs.specFile(v) + ".part")
        try {
            part.writeText(mimText)
            if (!part.renameTo(target)) {
                target.writeText(mimText)
                part.delete()
            }
        } catch (e: IOException) {
            return CheckResult.Failed("install failed: ${e.message}")
        }

        if (downloadedTsv != null) {
            val tsvTarget = downloadedTsvFile(v)
            val tsvPart = File(dir(), KhiproSpecs.tsvFile(v) + ".part")
            try {
                tsvPart.writeText(downloadedTsv)
                if (!tsvPart.renameTo(tsvTarget)) {
                    tsvTarget.writeText(downloadedTsv)
                    tsvPart.delete()
                }
            } catch (_: IOException) {
                // Non-fatal if TSV caching fails
            }
        } else {
            downloadedTsvFile(v).delete()
        }

        storePref(versionKey(v), tag)
        native.loadSpec(v, mimText)
        return CheckResult.Updated(tag)
    }

    /** Removes the downloaded spec and reloads the bundled one. */
    fun resetToBundled(v: KhiproVariant) {
        removePref(versionKey(v))
        downloadedFile(v).delete()
        downloadedTsvFile(v).delete()
        native.reset(v)
        assetReader(KhiproSpecs.DIR + "/" + KhiproSpecs.specFile(v))?.let {
            native.loadSpec(v, it)
        }
    }

    companion object {
        fun releaseApiUrl(repo: String) = "https://api.github.com/repos/$repo/releases/latest"

        /** Production fetcher: GET with the headers GitHub's API requires. */
        fun defaultFetcher(url: String): ByteArray {
            val connection = URL(url).openConnection() as HttpURLConnection
            return try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "Kiebord")
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                val code = connection.responseCode
                if (code != 200) throw IOException("HTTP $code for $url")
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }

        /** Plain-text field extraction from the small JSON shapes we read
         *  (release tag_name, builtin.json) — keeps unit tests JVM-pure. */
        fun extractStringField(json: String, key: String): String? =
            Regex("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
    }

    private fun latestTag(repo: String): String? {
        val body = fetcher(releaseApiUrl(repo)).toString(Charsets.UTF_8)
        return extractStringField(body, "tag_name")
    }
}
