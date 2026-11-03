package io.kiebord.feature.ime

import io.kiebord.feature.ime.khipro.KhiproVariant

object NativeEngine {

    private var isLoaded = false

    init {
        try {
            System.loadLibrary("borno_engine")
            isLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            isLoaded = false
        } catch (_: SecurityException) {
            isLoaded = false
        }
    }

    fun isAvailable(): Boolean = isLoaded

    @JvmStatic
    external fun ping(): Boolean

    data class DictionaryMetadata(
        val name: String,
        val author: String,
        val version: Int,
        val date: Long,
        val license: String,
        val wordCount: Int
    )

    @JvmStatic
    external fun loadDictionary(lang: String, path: String): Boolean

    @JvmStatic
    external fun getDictionaryMetadata(langOrPath: String): String

    @JvmStatic
    external fun transliterateBorno(input: String): String

    @JvmStatic
    external fun transliterate(layoutId: String, input: String): String

    @JvmStatic
    external fun loadM17nRules(layoutId: String, rulesContent: String): Boolean

    @JvmStatic
    external fun transliterateM17n(layoutId: String, input: String): String

    @JvmStatic
    external fun isPhoneticLayout(layoutId: String): Boolean

    @JvmStatic
    external fun isM17nLayout(layoutId: String): Boolean

    @JvmStatic
    external fun getSuggestions(prefix: String, lang: String, maxResults: Int): String

    @JvmStatic
    external fun isWord(word: String, lang: String): Boolean

    @JvmStatic
    external fun getTransliterationAlternatives(layoutId: String, rawInput: String): String

    @JvmStatic
    external fun predictNextWords(prevWord: String, lang: String, maxResults: Int): String

    @JvmStatic
    external fun registerLayoutProximityRows(layoutId: String, rows: Array<String>): Boolean

    @JvmStatic
    external fun decodeGesture(
        pointsX: FloatArray,
        pointsY: FloatArray,
        keysChars: String,
        keysX: FloatArray,
        keysY: FloatArray,
        keyWidth: Float,
        lang: String,
        maxResults: Int
    ): String

    // ---- Khipro (m17n spec interpreter) ----

    @JvmStatic
    external fun khiproLoadSpec(variant: Int, mimText: String): Boolean

    @JvmStatic
    external fun khiproConvert(variant: Int, buffer: String): String

    @JvmStatic
    external fun khiproLoaded(variant: Int): Boolean

    @JvmStatic
    external fun khiproReset(variant: Int)

    /** "" when the spec passes the conformance gate, else a failure report. */
    @JvmStatic
    external fun khiproValidateSpec(variant: Int, mimText: String, tsvText: String): String

    fun loadKhiproSpec(variant: KhiproVariant, mim: String): Boolean =
        khiproLoadSpec(variant.id, mim)

    fun convertKhipro(variant: KhiproVariant, buffer: String): String =
        khiproConvert(variant.id, buffer)

    fun khiproSpecLoaded(variant: KhiproVariant): Boolean = khiproLoaded(variant.id)

    fun resetKhipro(variant: KhiproVariant) = khiproReset(variant.id)

    fun validateKhiproSpec(variant: KhiproVariant, mim: String, tsv: String): String =
        khiproValidateSpec(variant.id, mim, tsv)

    fun queryGesture(
        points: List<androidx.compose.ui.geometry.Offset>,
        keyCentroids: List<io.kiebord.feature.ime.gesture.KeyCentroid>,
        keyWidth: Float,
        lang: String,
        maxResults: Int = 5,
        learnedWords: List<String> = emptyList()
    ): List<String> {
        if (!isLoaded || points.size < 2 || keyCentroids.isEmpty()) return emptyList()
        val xs = FloatArray(points.size) { points[it].x }
        val ys = FloatArray(points.size) { points[it].y }
        val chars = StringBuilder()
        val kxs = FloatArray(keyCentroids.size)
        val kys = FloatArray(keyCentroids.size)
        for (i in keyCentroids.indices) {
            val c = keyCentroids[i].character
            chars.append(if (c.isNotEmpty()) c[0] else ' ')
            kxs[i] = keyCentroids[i].centerX
            kys[i] = keyCentroids[i].centerY
        }
        val targetLang = if (learnedWords.isNotEmpty()) {
            val encoded = learnedWords.take(50).joinToString("|")
            "$lang;$encoded"
        } else lang
        return try {
            val raw = decodeGesture(xs, ys, chars.toString(), kxs, kys, keyWidth, targetLang, maxResults)
            if (raw.isEmpty()) emptyList() else raw.split("|").filter { it.isNotEmpty() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private val initializedLangs = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    @Synchronized
    fun ensureInitialized(context: android.content.Context, lang: String) {
        if (!isLoaded) return
        val baseLang = if (lang.startsWith("bn")) "bn" else "en"
        if (initializedLangs.contains(baseLang)) return

        try {
            val safeContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()
            } else {
                context
            }
            val assetPath = "dictionaries/$baseLang.dict"
            val destFile = java.io.File(safeContext.filesDir, assetPath)
            var needsCopy = !destFile.exists()

            if (!needsCopy) {
                try {
                    context.assets.open(assetPath).use { stream ->
                        if (destFile.length() != stream.available().toLong()) {
                            needsCopy = true
                        }
                    }
                } catch (_: Throwable) {}
            }

            if (needsCopy) {
                destFile.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            var loaded = loadDictionary(baseLang, destFile.absolutePath)
            if (!loaded && !needsCopy) {
                context.assets.open(assetPath).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                loaded = loadDictionary(baseLang, destFile.absolutePath)
            }
            if (loaded) {
                initializedLangs.add(baseLang)
            }
        } catch (_: Throwable) {}
    }

    fun isM17nLayoutReady(layoutId: String): Boolean {
        if (!isLoaded) return false
        return initializedM17nLayouts.contains(layoutId) ||
                initializedM17nLayouts.contains(layoutId.removePrefix("bn_")) ||
                initializedM17nLayouts.contains("bn_$layoutId") ||
                try { isM17nLayout(layoutId) } catch (_: Throwable) { false }
    }

    private val initializedM17nLayouts = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    @Synchronized
    fun ensureM17nInitialized(context: android.content.Context, layoutId: String, rulesFileName: String): Boolean {
        if (!isLoaded) return false
        if (initializedM17nLayouts.contains(layoutId)) return true

        return try {
            val candidates = listOf(
                "rules/$rulesFileName",
                rulesFileName,
                "rules/bn-khipro.classic.mim",
                "bn-khipro.classic.mim"
            )
            var content: String? = null
            for (candidate in candidates) {
                try {
                    context.assets.open(candidate).use { stream ->
                        content = stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                    if (!content.isNullOrBlank()) break
                } catch (_: Throwable) {}
            }

            if (!content.isNullOrBlank()) {
                val ok = loadM17nRules(layoutId, content!!)
                if (ok) {
                    initializedM17nLayouts.add(layoutId)
                    initializedM17nLayouts.add(layoutId.removePrefix("bn_"))
                    initializedM17nLayouts.add("bn_$layoutId")
                }
                ok
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun queryDictionaryMetadata(langOrPath: String): DictionaryMetadata? {
        if (!isLoaded) return null
        return try {
            val raw = getDictionaryMetadata(langOrPath)
            if (raw.isBlank()) return null
            var name = "Kiebord Word Dictionary"
            var author = "Jayed Ahsan Saad"
            var version = 1
            var date = 0L
            var license = "Only for Kiebord"
            var words = 0
            for (part in raw.split(";")) {
                val idx = part.indexOf('=')
                if (idx <= 0) continue
                val key = part.substring(0, idx).trim()
                val value = part.substring(idx + 1).trim()
                when (key) {
                    "name" -> if (value.isNotEmpty()) name = value
                    "author" -> if (value.isNotEmpty()) author = value
                    "version" -> version = value.toIntOrNull() ?: version
                    "date" -> date = value.toLongOrNull() ?: date
                    "license" -> if (value.isNotEmpty()) license = value
                    "words" -> words = value.toIntOrNull() ?: words
                }
            }
            DictionaryMetadata(
                name = name,
                author = author,
                version = version,
                date = date,
                license = license,
                wordCount = words
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun isDictionaryWord(word: String, lang: String): Boolean {
        if (!isLoaded || word.isBlank()) return false
        return try {
            isWord(word, lang)
        } catch (_: Throwable) {
            false
        }
    }

    fun querySuggestions(prefix: String, lang: String, maxResults: Int = 5): List<String> {
        if (!isLoaded) return emptyList()
        return try {
            val raw = getSuggestions(prefix, lang, maxResults)
            if (raw.isEmpty()) emptyList() else raw.split("|").filter { it.isNotEmpty() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun queryNextWords(prevWord: String, lang: String, maxResults: Int = 3): List<String> {
        if (!isLoaded || prevWord.isBlank()) return emptyList()
        return try {
            val raw = predictNextWords(prevWord.trim(), lang, maxResults)
            if (raw.isEmpty()) emptyList() else raw.split("|").filter { it.isNotEmpty() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun queryTransliterationAlternatives(layoutId: String, rawInput: String): List<String> {
        if (rawInput.isBlank() || layoutId != "bn_borno") return emptyList()
        if (isLoaded) {
            try {
                val raw = getTransliterationAlternatives(layoutId, rawInput)
                if (raw.isNotEmpty()) {
                    val list = raw.split("|").filter { it.isNotEmpty() }
                    if (list.isNotEmpty()) return list
                }
            } catch (_: Throwable) {}
        }
        return generateFallbackTransliterationAlternatives(layoutId, rawInput)
    }

    private fun generateFallbackTransliterationAlternatives(layoutId: String, rawInput: String): List<String> {
        if (!isLoaded) return listOf(rawInput)
        return try {
            val canonical = transliterate(layoutId, rawInput)
            val results = mutableListOf<String>()
            if (canonical.isNotEmpty()) {
                results.add(canonical)
            }

            val chars = rawInput.toCharArray()
            for (i in chars.indices) {
                val c = chars[i]
                val flipped = when (c) {
                    't' -> charArrayOf('T')
                    'T' -> charArrayOf('t')
                    'e' -> charArrayOf('a')
                    'a' -> charArrayOf('e')
                    'j' -> charArrayOf('z')
                    'z' -> charArrayOf('j')
                    'n' -> charArrayOf('N')
                    'N' -> charArrayOf('n')
                    'o' -> charArrayOf('O')
                    'O' -> charArrayOf('o')
                    'd' -> charArrayOf('D')
                    'D' -> charArrayOf('d')
                    's' -> charArrayOf('S')
                    'S' -> charArrayOf('s')
                    'r' -> charArrayOf('R')
                    'R' -> charArrayOf('r')
                    'i' -> charArrayOf('I')
                    'I' -> charArrayOf('i')
                    'u' -> charArrayOf('U')
                    'U' -> charArrayOf('u')
                    else -> charArrayOf()
                }
                for (f in flipped) {
                    val alt = chars.clone()
                    alt[i] = f
                    val altStr = String(alt)
                    val translit = transliterate(layoutId, altStr)
                    if (translit.isNotEmpty() && !results.contains(translit)) {
                        results.add(translit)
                    }
                }
            }

            val digraphSwaps = listOf(
                "ou" to "OU",
                "Ou" to "OU",
                "oU" to "OU",
                "OU" to "ou",
                "oi" to "OI",
                "Oi" to "OI",
                "oI" to "OI",
                "OI" to "oi",
                "ri" to "rri",
                "rri" to "ri",
                "o" to "O",
                "O" to "o"
            )
            for ((from, to) in digraphSwaps) {
                if (rawInput.contains(from)) {
                    val altStr = rawInput.replace(from, to)
                    val translit = transliterate(layoutId, altStr)
                    if (translit.isNotEmpty() && !results.contains(translit)) {
                        results.add(translit)
                    }
                }
            }
            results
        } catch (_: Throwable) {
            listOf(rawInput)
        }
    }
}

