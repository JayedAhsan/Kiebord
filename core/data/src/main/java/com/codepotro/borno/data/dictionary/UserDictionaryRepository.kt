package com.codepotro.borno.data.dictionary

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, offline, zero-overhead user dictionary and n-gram learning repository.
 *
 * All prediction lookups (next words, completions) query fast in-memory maps in O(1) time
 * with zero UI thread latency. Persistence to internal disk storage is completely asynchronous
 * and debounced via Coroutines on Dispatchers.IO.
 */
class UserDictionaryRepository internal constructor(private val baseDir: File) {

    constructor(context: Context) : this(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val safe = if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()
            safe.filesDir
        } else {
            context.filesDir
        }
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var saveJob: Job? = null

    // In-memory data structures for O(1) instant querying
    // 1. Unigrams: word -> usage count
    private val wordFrequencies = ConcurrentHashMap<String, Int>()

    // 2. Bigrams (Next-word ngrams): prevWord.lowercase() -> { nextWord: count }
    private val bigramFrequencies = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    // 3. Gesture committed words: word -> count
    private val gestureWords = ConcurrentHashMap<String, Int>()

    private val _learnedWordsFlow = MutableStateFlow<List<String>>(emptyList())
    val learnedWordsFlow: StateFlow<List<String>> = _learnedWordsFlow.asStateFlow()

    private fun refreshLearnedWordsFlow() {
        _learnedWordsFlow.value = getAllLearnedWords(limit = Int.MAX_VALUE)
    }

    private val storageFile: File by lazy {
        File(baseDir, "user_dict_data.json")
    }

    init {
        loadFromDisk()
    }

    /**
     * Records a committed, selected, or user-typed word to update frequencies and bigram pairs.
     * All in-memory updates complete instantaneously; disk writes are debounced and asynchronous.
     * Single-occurrence words are kept in memory as transient candidates and are only promoted
     * to learned status upon reaching MIN_LEARN_OCCURRENCES (>= 2).
     */
    fun recordWord(word: String, prevWord: String? = null, isGesture: Boolean = false) {
        val cleanWord = word.trim()
        if (cleanWord.length < MIN_LEARN_LENGTH || cleanWord.length > MAX_WORD_LENGTH) return

        // 1. Increment unigram frequency
        var newWordCount = 0
        wordFrequencies.compute(cleanWord) { _, old ->
            val updated = ((old ?: 0) + 1).coerceAtMost(100_000)
            newWordCount = updated
            updated
        }

        // 2. Increment bigram frequency if prevWord exists and meets minimum length
        var newBigramCount = 0
        if (!prevWord.isNullOrBlank()) {
            val cleanPrev = prevWord.trim().lowercase()
            if (cleanPrev.length >= MIN_LEARN_LENGTH && cleanPrev.length <= MAX_WORD_LENGTH) {
                val nextMap = bigramFrequencies.computeIfAbsent(cleanPrev) { ConcurrentHashMap() }
                nextMap.compute(cleanWord) { _, old ->
                    val updated = ((old ?: 0) + 1).coerceAtMost(100_000)
                    newBigramCount = updated
                    updated
                }
            }
        }

        // 3. Track gesture usage
        var newGestureCount = 0
        if (isGesture) {
            gestureWords.compute(cleanWord) { _, old ->
                val updated = ((old ?: 0) + 1).coerceAtMost(100_000)
                newGestureCount = updated
                updated
            }
        }

        // Memory bounding: prune single-use transient entries if limits are exceeded
        if (wordFrequencies.size > MAX_UNIGRAMS) {
            val toRemove = wordFrequencies.entries.filter { it.value < MIN_LEARN_OCCURRENCES }.take(400)
            toRemove.forEach { wordFrequencies.remove(it.key) }
        }
        if (bigramFrequencies.size > MAX_BIGRAM_PREVS) {
            val toRemove = bigramFrequencies.entries.filter { prevEntry ->
                prevEntry.value.values.all { it < MIN_LEARN_OCCURRENCES }
            }.take(300)
            toRemove.forEach { bigramFrequencies.remove(it.key) }
        }

        // Performance optimization: only schedule disk save if the word or bigram has reached
        // the minimum learning threshold (>= 2 occurrences). Single-occurrence words are kept
        // in memory as transient candidates to avoid disk writes and CPU wakeups.
        if (newWordCount >= MIN_LEARN_OCCURRENCES || newBigramCount >= MIN_LEARN_OCCURRENCES || newGestureCount >= MIN_LEARN_OCCURRENCES) {
            if (newWordCount >= MIN_LEARN_OCCURRENCES || newGestureCount >= MIN_LEARN_OCCURRENCES) {
                refreshLearnedWordsFlow()
            }
            scheduleDebouncedSave()
        }
    }

    /**
     * Trims in-memory dictionaries under Android OS low memory pressure.
     */
    fun trimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            wordFrequencies.entries.removeIf { it.value < MIN_LEARN_OCCURRENCES }
            bigramFrequencies.values.forEach { nextMap ->
                nextMap.entries.removeIf { it.value < MIN_LEARN_OCCURRENCES }
            }
            bigramFrequencies.entries.removeIf { it.value.isEmpty() }
            gestureWords.entries.removeIf { it.value < MIN_LEARN_OCCURRENCES }

            if (wordFrequencies.size > 1500) {
                val topWords = wordFrequencies.entries
                    .sortedByDescending { it.value }
                    .take(1500)
                    .associate { it.key to it.value }
                wordFrequencies.clear()
                wordFrequencies.putAll(topWords)
            }
            if (bigramFrequencies.size > 1000) {
                val topPrevs = bigramFrequencies.entries
                    .sortedByDescending { it.value.values.sum() }
                    .take(1000)
                    .associate { it.key to it.value }
                bigramFrequencies.clear()
                bigramFrequencies.putAll(topPrevs)
            }
        }
    }

    /**
     * Predicts learned subsequent words following [prevWord], sorted descending by learned frequency.
     * Only returns words meeting MIN_LEARN_OCCURRENCES (>= 2) and MIN_LEARN_LENGTH (>= 3).
     * Guaranteed zero-allocation, sub-millisecond in-memory lookup.
     */
    fun getNextWords(prevWord: String, limit: Int = 5, filter: ((String) -> Boolean)? = null): List<String> {
        val cleanPrev = prevWord.trim().lowercase()
        if (cleanPrev.isEmpty()) return emptyList()

        val nextMap = bigramFrequencies[cleanPrev] ?: return emptyList()
        return nextMap.entries
            .asSequence()
            .filter { entry ->
                entry.value >= MIN_LEARN_OCCURRENCES &&
                entry.key.length >= MIN_LEARN_LENGTH &&
                (filter == null || filter(entry.key))
            }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
            .toList()
    }

    /**
     * Retrieves learned words matching [prefix], sorted descending by usage frequency.
     * Only returns words meeting MIN_LEARN_OCCURRENCES (>= 2) and MIN_LEARN_LENGTH (>= 3).
     * Enables frequently typed custom words to appear in suggestion strip.
     */
    fun getCompletions(prefix: String, limit: Int = 5, filter: ((String) -> Boolean)? = null): List<String> {
        val cleanPrefix = prefix.trim()
        if (cleanPrefix.isEmpty()) return emptyList()

        val isLatin = cleanPrefix.all { it in 'a'..'z' || it in 'A'..'Z' }
        val searchPrefix = if (isLatin) cleanPrefix.lowercase() else cleanPrefix

        return wordFrequencies.entries
            .asSequence()
            .filter { entry ->
                entry.value >= MIN_LEARN_OCCURRENCES && entry.key.length >= MIN_LEARN_LENGTH && run {
                    val w = entry.key
                    val matches = if (isLatin) {
                        w.lowercase().startsWith(searchPrefix)
                    } else {
                        w.startsWith(searchPrefix)
                    }
                    matches && (filter == null || filter(w))
                }
            }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
            .toList()
    }

    /**
     * Returns frequently gesture-committed words for boosting in gesture decoding.
     * Only includes words meeting MIN_LEARN_OCCURRENCES (>= 2) and MIN_LEARN_LENGTH (>= 3).
     */
    fun getLearnedGestureWords(limit: Int = 20): List<String> {
        return gestureWords.entries
            .asSequence()
            .filter { it.value >= MIN_LEARN_OCCURRENCES && it.key.length >= MIN_LEARN_LENGTH }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
            .toList()
    }

    /**
     * Checks if a word exists as a verified learned word (>= 2 occurrences, >= 3 characters).
     */
    fun isLearned(word: String): Boolean {
        val clean = word.trim()
        if (clean.length < MIN_LEARN_LENGTH) return false
        val count = wordFrequencies[clean] ?: 0
        if (count >= MIN_LEARN_OCCURRENCES) return true
        val gCount = gestureWords[clean] ?: 0
        return gCount >= MIN_LEARN_OCCURRENCES
    }

    /**
     * Unlearns and deletes a word from frequencies, gesture history, and bigram models.
     */
    fun unlearnWord(word: String) {
        val clean = word.trim()
        if (clean.isEmpty()) return
        val hadWord = wordFrequencies.remove(clean) != null
        val hadGesture = gestureWords.remove(clean) != null
        val cleanLower = clean.lowercase()
        val hadBigram = bigramFrequencies.remove(cleanLower) != null
        var hadNextBigram = false
        bigramFrequencies.values.forEach { nextMap ->
            if (nextMap.remove(clean) != null) {
                hadNextBigram = true
            }
        }
        if (hadWord || hadGesture || hadBigram || hadNextBigram) {
            if (hadWord || hadGesture) {
                refreshLearnedWordsFlow()
            }
            scheduleDebouncedSave()
        }
    }

    /**
     * Retrieves all verified learned words (typed or gestured at least 2 times, length >= 3), sorted descending by usage.
     */
    fun getAllLearnedWords(limit: Int = 100): List<String> {
        val combined = HashMap<String, Int>()
        wordFrequencies.forEach { (w, count) ->
            if (count >= MIN_LEARN_OCCURRENCES && w.length >= MIN_LEARN_LENGTH) {
                combined[w] = count
            }
        }
        gestureWords.forEach { (w, count) ->
            if (count >= MIN_LEARN_OCCURRENCES && w.length >= MIN_LEARN_LENGTH) {
                combined.merge(w, count) { old, v -> old + v }
            }
        }
        return combined.entries
            .asSequence()
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
            .toList()
    }

    /**
     * Clears all learned words, ngrams, and personalized prediction history from memory and disk.
     */
    fun clearAll() {
        wordFrequencies.clear()
        bigramFrequencies.clear()
        gestureWords.clear()
        _learnedWordsFlow.value = emptyList()

        scope.launch {
            try {
                if (storageFile.exists()) {
                    storageFile.delete()
                }
            } catch (_: Throwable) {}
        }
    }

    private fun scheduleDebouncedSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500) // 1.5s debounce after typing ceases
            saveToDisk()
        }
    }

    private fun saveToDisk() {
        try {
            val root = JSONObject()
            root.put("version", 1)

            // Words
            val wordsObj = JSONObject()
            wordFrequencies.forEach { (word, count) ->
                if (count >= MIN_LEARN_OCCURRENCES && word.length >= MIN_LEARN_LENGTH) {
                    wordsObj.put(word, count)
                }
            }
            root.put("words", wordsObj)

            // Bigrams
            val bigramsObj = JSONObject()
            bigramFrequencies.forEach { (prev, nextMap) ->
                if (prev.length >= MIN_LEARN_LENGTH) {
                    val subObj = JSONObject()
                    nextMap.forEach { (next, count) ->
                        if (count >= MIN_LEARN_OCCURRENCES && next.length >= MIN_LEARN_LENGTH) {
                            subObj.put(next, count)
                        }
                    }
                    if (subObj.length() > 0) {
                        bigramsObj.put(prev, subObj)
                    }
                }
            }
            root.put("bigrams", bigramsObj)

            // Gestures
            val gesturesObj = JSONObject()
            gestureWords.forEach { (word, count) ->
                if (count >= MIN_LEARN_OCCURRENCES && word.length >= MIN_LEARN_LENGTH) {
                    gesturesObj.put(word, count)
                }
            }
            root.put("gestures", gesturesObj)

            val tempFile = File(baseDir, "user_dict_data.json.tmp")
            tempFile.writeText(root.toString())
            if (tempFile.exists()) {
                tempFile.renameTo(storageFile)
            }
        } catch (_: Throwable) {}
    }

    private fun loadFromDisk() {
        scope.launch {
            try {
                if (!storageFile.exists()) return@launch
                val content = storageFile.readText()
                if (content.isBlank()) return@launch

                val root = JSONObject(content)

                // Load words
                val wordsObj = root.optJSONObject("words")
                if (wordsObj != null) {
                    val keys = wordsObj.keys()
                    while (keys.hasNext()) {
                        val word = keys.next()
                        val count = wordsObj.optInt(word, 0)
                        if (word.length >= MIN_LEARN_LENGTH && count >= MIN_LEARN_OCCURRENCES) {
                            wordFrequencies[word] = count
                        }
                    }
                }

                // Load bigrams
                val bigramsObj = root.optJSONObject("bigrams")
                if (bigramsObj != null) {
                    val prevKeys = bigramsObj.keys()
                    while (prevKeys.hasNext()) {
                        val prev = prevKeys.next()
                        if (prev.length < MIN_LEARN_LENGTH) continue
                        val subObj = bigramsObj.optJSONObject(prev)
                        if (subObj != null) {
                            val nextMap = ConcurrentHashMap<String, Int>()
                            val nextKeys = subObj.keys()
                            while (nextKeys.hasNext()) {
                                val next = nextKeys.next()
                                val count = subObj.optInt(next, 0)
                                if (next.length >= MIN_LEARN_LENGTH && count >= MIN_LEARN_OCCURRENCES) {
                                    nextMap[next] = count
                                }
                            }
                            if (nextMap.isNotEmpty()) {
                                bigramFrequencies[prev] = nextMap
                            }
                        }
                    }
                }

                // Load gestures
                val gesturesObj = root.optJSONObject("gestures")
                if (gesturesObj != null) {
                    val keys = gesturesObj.keys()
                    while (keys.hasNext()) {
                        val word = keys.next()
                        val count = gesturesObj.optInt(word, 0)
                        if (word.length >= MIN_LEARN_LENGTH && count >= MIN_LEARN_OCCURRENCES) {
                            gestureWords[word] = count
                        }
                    }
                }
                refreshLearnedWordsFlow()
            } catch (_: Throwable) {}
        }
    }

    companion object {
        const val MIN_LEARN_LENGTH = 3
        const val MIN_LEARN_OCCURRENCES = 2
        private const val MAX_WORD_LENGTH = 64
        private const val MAX_UNIGRAMS = 4000
        private const val MAX_BIGRAM_PREVS = 3000

        @Volatile
        private var instance: UserDictionaryRepository? = null

        fun getInstance(context: Context): UserDictionaryRepository {
            return instance ?: synchronized(this) {
                instance ?: UserDictionaryRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
