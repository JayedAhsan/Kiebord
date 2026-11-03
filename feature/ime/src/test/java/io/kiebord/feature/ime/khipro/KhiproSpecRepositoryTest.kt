package io.kiebord.feature.ime.khipro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Records calls so tests can assert what the repository did to the engine. */
private class FakeNative : NativeEngineBridge {
    val loaded = mutableMapOf<Int, String>()
    var validateReport = ""
    var loadOk = true
    val conversions = mutableListOf<Pair<Int, String>>()
    var lastValidatedTsv: String? = null

    override fun loadSpec(v: KhiproVariant, mim: String): Boolean {
        if (loadOk) loaded[v.id] = mim
        return loadOk
    }

    override fun convert(v: KhiproVariant, buffer: String): String = buffer

    override fun loaded(v: KhiproVariant): Boolean = loaded.containsKey(v.id)

    override fun reset(v: KhiproVariant) {
        loaded.remove(v.id)
    }

    override fun validate(v: KhiproVariant, mim: String, tsv: String): String {
        lastValidatedTsv = tsv
        return validateReport
    }
}

private class FakeAssetReader(var files: Map<String, String>) :
    (String) -> String? by { path -> files[path] }

private class FakePrefs {
    val map = mutableMapOf<String, String>()
    fun getString(key: String, def: String?): String? = map[key] ?: def
    fun put(key: String, value: String) {
        map[key] = value
    }
    fun remove(key: String) {
        map.remove(key)
    }
}

class KhiproSpecRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val classicMim = "(input-method bn khipro-classic)\n(map (shor (\"a\" \"আ\")))"
    private val classicTsv = "input\toutput\na\tআ\n"
    private val builtinJson = "{\"classic\": \"v37.0.3\", \"dark\": \"drk-v2.0.2\"}"

    private fun makeRepo(
        fake: FakeNative,
        assets: Map<String, String> = mapOf(
            "khipro/bn-khipro-classic.mim" to classicMim,
            "khipro/builtin.json" to builtinJson,
            "khipro/khipro-testcases-touch.tsv" to classicTsv,
        ),
        fetcher: (String) -> ByteArray = { error("no network in this test") },
    ): Pair<KhiproSpecRepository, FakePrefs> {
        val prefs = FakePrefs()
        val repo = KhiproSpecRepository(
            assetReader = { path -> assets[path] },
            filesDir = tmp.newFolder("files-$fake"),
            loadPref = { key, def -> prefs.getString(key, def) },
            storePref = { key, value -> prefs.put(key, value) },
            removePref = { key -> prefs.remove(key) },
            fetcher = fetcher,
            native = fake,
        )
        return repo to prefs
    }

    private fun githubApiBody(tag: String) =
        """{"tag_name": "$tag", "name": "release"}"""

    private fun classicReleaseUrl(repo: String) =
        "https://api.github.com/repos/$repo/releases/latest"

    private fun classicRawUrl(tag: String) =
        "https://raw.githubusercontent.com/KhiproTeam/khipro-mim-touchscreen/$tag/bn-khipro-classic.mim"

    private fun classicTsvUrl(tag: String) =
        "https://raw.githubusercontent.com/KhiproTeam/khipro-mim-touchscreen/$tag/khipro-testcases.tsv"

    @Test
    fun ensureLoadedUsesBundledSpecWhenNoDownload() {
        val fake = FakeNative()
        val (repo, _) = makeRepo(fake)
        repo.ensureLoaded(KhiproVariant.CLASSIC)
        assertEquals(classicMim, fake.loaded[KhiproVariant.CLASSIC.id])
    }

    @Test
    fun installedVersionReadsBuiltinJson() {
        val fake = FakeNative()
        val (repo, _) = makeRepo(fake)
        assertEquals("v37.0.3", repo.bundledVersion(KhiproVariant.CLASSIC))
        assertEquals("drk-v2.0.2", repo.bundledVersion(KhiproVariant.DARK))
    }

    @Test
    fun checkForUpdateIsUpToDateWhenTagNotNewer() {
        val fake = FakeNative()
        val (repo, _) = makeRepo(fake, fetcher = { url ->
            when (url) {
                classicReleaseUrl(KhiproSpecs.CLASSIC_REPO) ->
                    githubApiBody("v37.0.3").toByteArray()
                else -> error("unexpected url $url")
            }
        })
        val result = repo.checkForUpdate(KhiproVariant.CLASSIC)
        assertEquals(CheckResult.UpToDate, result)
    }

    @Test
    fun checkForUpdateFailsWithoutNetwork() {
        val fake = FakeNative()
        val (repo, _) = makeRepo(fake, fetcher = { error("offline") })
        val result = repo.checkForUpdate(KhiproVariant.CLASSIC)
        assertTrue(result is CheckResult.Failed)
    }

    @Test
    fun updateFlowDownloadsValidatesInstallsAndReloads() {
        val fake = FakeNative()
        val newMim = "$classicMim\n;; newer"
        val (repo, prefs) = makeRepo(fake, fetcher = { url ->
            when (url) {
                classicReleaseUrl(KhiproSpecs.CLASSIC_REPO) ->
                    githubApiBody("v38.0.0").toByteArray()
                classicRawUrl("v38.0.0") -> newMim.toByteArray()
                else -> error("unexpected url $url")
            }
        })
        repo.ensureLoaded(KhiproVariant.CLASSIC)
        val result = repo.checkForUpdate(KhiproVariant.CLASSIC)
        assertEquals(CheckResult.Updated("v38.0.0"), result)
        // downloaded copy recorded and hot-reloaded into the engine
        assertEquals("v38.0.0", prefs.getString("khipro_dl_version_classic", null))
        assertEquals(newMim, fake.loaded[KhiproVariant.CLASSIC.id])
        // downloaded file exists in filesDir/khipro/
        val dl = java.io.File(tmp.root, "files-${fake}/khipro/bn-khipro-classic.mim")
        assertTrue(dl.isFile)
        assertEquals(newMim, dl.readText())
    }

    @Test
    fun rejectedSpecKeepsCurrentEngineUntouched() {
        val fake = FakeNative()
        fake.validateReport = "3 failed: first input=\"ks\""
        val (repo, prefs) = makeRepo(fake, fetcher = { url ->
            when (url) {
                classicReleaseUrl(KhiproSpecs.CLASSIC_REPO) ->
                    githubApiBody("v38.0.0").toByteArray()
                classicRawUrl("v38.0.0") -> "broken".toByteArray()
                else -> error("unexpected url $url")
            }
        })
        repo.ensureLoaded(KhiproVariant.CLASSIC)
        val before = fake.loaded[KhiproVariant.CLASSIC.id]
        val result = repo.checkForUpdate(KhiproVariant.CLASSIC)
        assertTrue(result is CheckResult.Rejected)
        assertEquals(before, fake.loaded[KhiproVariant.CLASSIC.id])
        assertNull(prefs.getString("khipro_dl_version_classic", null))
        assertFalse(
            java.io.File(tmp.root, "files-${fake}/khipro/bn-khipro-classic.mim").isFile,
        )
    }

    @Test
    fun headerMismatchIsRejected() {
        val fake = FakeNative()
        val (repo, _) = makeRepo(fake, fetcher = { url ->
            when (url) {
                classicReleaseUrl(KhiproSpecs.CLASSIC_REPO) ->
                    githubApiBody("v38.0.0").toByteArray()
                classicRawUrl("v38.0.0") ->
                    "(input-method bn khipro-dark)\n(bogus)".toByteArray()
                else -> error("unexpected url $url")
            }
        })
        val result = repo.checkForUpdate(KhiproVariant.CLASSIC)
        assertTrue(result is CheckResult.Rejected)
    }

    @Test
    fun oversizedPayloadIsRejected() {
        val fake = FakeNative()
        val (repo, _) = makeRepo(fake, fetcher = { url ->
            when (url) {
                classicReleaseUrl(KhiproSpecs.CLASSIC_REPO) ->
                    githubApiBody("v38.0.0").toByteArray()
                classicRawUrl("v38.0.0") -> ByteArray(300 * 1024) { 'x'.code.toByte() }
                else -> error("unexpected url $url")
            }
        })
        val result = repo.checkForUpdate(KhiproVariant.CLASSIC)
        assertTrue(result is CheckResult.Rejected)
    }

    @Test
    fun resetToBundledClearsDownloadAndReloadsBuiltin() {
        val fake = FakeNative()
        val (repo, prefs) = makeRepo(fake, fetcher = { url ->
            when (url) {
                classicReleaseUrl(KhiproSpecs.CLASSIC_REPO) ->
                    githubApiBody("v38.0.0").toByteArray()
                classicRawUrl("v38.0.0") -> "$classicMim\n;; newer".toByteArray()
                else -> error("unexpected url $url")
            }
        })
        repo.ensureLoaded(KhiproVariant.CLASSIC)
        repo.checkForUpdate(KhiproVariant.CLASSIC)
        assertEquals("v38.0.0", prefs.getString("khipro_dl_version_classic", null))

        repo.resetToBundled(KhiproVariant.CLASSIC)
        assertNull(prefs.getString("khipro_dl_version_classic", null))
        assertEquals(classicMim, fake.loaded[KhiproVariant.CLASSIC.id])
        assertFalse(
            java.io.File(tmp.root, "files-${fake}/khipro/bn-khipro-classic.mim").isFile,
        )
    }

    @Test
    fun updateFlowDownloadsValidatesWithUpstreamTsvAndSavesToDisk() {
        val fake = FakeNative()
        val newMim = "$classicMim\n;; newer"
        val newTsv = "$classicTsv\nb\tব\n"
        val (repo, prefs) = makeRepo(fake, fetcher = { url ->
            when (url) {
                classicReleaseUrl(KhiproSpecs.CLASSIC_REPO) ->
                    githubApiBody("v38.0.0").toByteArray()
                classicRawUrl("v38.0.0") -> newMim.toByteArray()
                classicTsvUrl("v38.0.0") -> newTsv.toByteArray()
                else -> error("unexpected url $url")
            }
        })
        repo.ensureLoaded(KhiproVariant.CLASSIC)
        val result = repo.checkForUpdate(KhiproVariant.CLASSIC)
        assertEquals(CheckResult.Updated("v38.0.0"), result)
        assertEquals("v38.0.0", prefs.getString("khipro_dl_version_classic", null))
        assertEquals(newMim, fake.loaded[KhiproVariant.CLASSIC.id])
        // native.validate was invoked with the downloaded upstream TSV, not bundled
        assertEquals(newTsv, fake.lastValidatedTsv)

        // both MIM and TSV persisted to disk
        val dlMim = java.io.File(tmp.root, "files-${fake}/khipro/bn-khipro-classic.mim")
        val dlTsv = java.io.File(tmp.root, "files-${fake}/khipro/khipro-testcases-touch.tsv")
        assertTrue(dlMim.isFile)
        assertEquals(newMim, dlMim.readText())
        assertTrue(dlTsv.isFile)
        assertEquals(newTsv, dlTsv.readText())
        assertEquals(newTsv, repo.activeTsv(KhiproVariant.CLASSIC))
    }

    @Test
    fun updateFlowFallsBackToBundledTsvWhenRemoteTsvFails() {
        val fake = FakeNative()
        val newMim = "$classicMim\n;; newer"
        val (repo, prefs) = makeRepo(fake, fetcher = { url ->
            when (url) {
                classicReleaseUrl(KhiproSpecs.CLASSIC_REPO) ->
                    githubApiBody("v38.0.0").toByteArray()
                classicRawUrl("v38.0.0") -> newMim.toByteArray()
                classicTsvUrl("v38.0.0") -> error("404 Not Found")
                else -> error("unexpected url $url")
            }
        })
        repo.ensureLoaded(KhiproVariant.CLASSIC)
        val result = repo.checkForUpdate(KhiproVariant.CLASSIC)
        assertEquals(CheckResult.Updated("v38.0.0"), result)
        // native.validate fell back to bundled TSV
        assertEquals(classicTsv, fake.lastValidatedTsv)

        // MIM persisted, TSV not downloaded
        val dlMim = java.io.File(tmp.root, "files-${fake}/khipro/bn-khipro-classic.mim")
        val dlTsv = java.io.File(tmp.root, "files-${fake}/khipro/khipro-testcases-touch.tsv")
        assertTrue(dlMim.isFile)
        assertFalse(dlTsv.isFile)
        assertEquals(classicTsv, repo.activeTsv(KhiproVariant.CLASSIC))
    }

    @Test
    fun resetToBundledClearsBothDownloadedMimAndTsv() {
        val fake = FakeNative()
        val newMim = "$classicMim\n;; newer"
        val newTsv = "$classicTsv\nb\tব\n"
        val (repo, prefs) = makeRepo(fake, fetcher = { url ->
            when (url) {
                classicReleaseUrl(KhiproSpecs.CLASSIC_REPO) ->
                    githubApiBody("v38.0.0").toByteArray()
                classicRawUrl("v38.0.0") -> newMim.toByteArray()
                classicTsvUrl("v38.0.0") -> newTsv.toByteArray()
                else -> error("unexpected url $url")
            }
        })
        repo.ensureLoaded(KhiproVariant.CLASSIC)
        repo.checkForUpdate(KhiproVariant.CLASSIC)

        val dlMim = java.io.File(tmp.root, "files-${fake}/khipro/bn-khipro-classic.mim")
        val dlTsv = java.io.File(tmp.root, "files-${fake}/khipro/khipro-testcases-touch.tsv")
        assertTrue(dlMim.isFile)
        assertTrue(dlTsv.isFile)
        assertEquals(newTsv, repo.activeTsv(KhiproVariant.CLASSIC))

        repo.resetToBundled(KhiproVariant.CLASSIC)
        assertNull(prefs.getString("khipro_dl_version_classic", null))
        assertEquals(classicMim, fake.loaded[KhiproVariant.CLASSIC.id])
        assertFalse(dlMim.isFile)
        assertFalse(dlTsv.isFile)
        assertEquals(classicTsv, repo.activeTsv(KhiproVariant.CLASSIC))
    }
}
