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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Result data class describing the outcome of a personal dictionary import operation.
 */
data class PersonalDictImportResult(
    val totalFound: Int,
    val importedCount: Int,
    val updatedCount: Int,
    val skippedDuplicates: Int
)

/**
 * Repository for managing personal dictionary words and shortcuts.
 */
class PersonalDictionaryRepository internal constructor(private val baseDir: File) {

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

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val entriesMap = ConcurrentHashMap<String, PersonalDictionaryEntry>()
    private val shortcutIndex = ConcurrentHashMap<String, CopyOnWriteArrayList<PersonalDictionaryEntry>>()
    private val _entriesFlow = MutableStateFlow<List<PersonalDictionaryEntry>>(emptyList())
    val entriesFlow: StateFlow<List<PersonalDictionaryEntry>> = _entriesFlow.asStateFlow()

    private val storageFile: File by lazy {
        File(baseDir, "personal_dict_data.json")
    }

    init {
        loadFromDisk()
    }

    /**
     * Resolves shortcut expansions matching [shortcut] for the specified [layoutLang].
     * Universal ("all") entries match any layout language.
     */
    fun getShortcutMatches(shortcut: String, layoutLang: String): List<PersonalDictionaryEntry> {
        val clean = PersonalDictionaryEntry.normalizeShortcut(shortcut) ?: return emptyList()
        val list = shortcutIndex[clean] ?: return emptyList()
        if (list.isEmpty()) return emptyList()

        return list.filter { it.matchesLanguage(layoutLang) }
    }

    /**
     * Finds an existing personal dictionary entry matching [word] (case-insensitive) for [layoutLang].
     * Prefers an exact language match over a universal ("all") entry when both exist.
     */
    fun findByWord(word: String, layoutLang: String): PersonalDictionaryEntry? {
        val clean = word.trim()
        if (clean.isEmpty()) return null
        val targetLang = layoutLang.trim().lowercase()
        var universalMatch: PersonalDictionaryEntry? = null
        for (entry in entriesMap.values) {
            if (entry.word.equals(clean, ignoreCase = true) && entry.matchesLanguage(targetLang)) {
                if (entry.language.equals(targetLang, ignoreCase = true)) {
                    return entry
                }
                if (universalMatch == null) {
                    universalMatch = entry
                }
            }
        }
        return universalMatch
    }

    /**
     * Returns candidate entries whose shortcut or expanded word starts with [prefix].
     * Exact shortcut matches and word prefix matches are prioritized.
     * Shorter shortcuts (length 2-4) require typing the full shortcut to prevent single-character false triggers.
     * Longer shortcuts (length > 4) allow prefix matching when at least 4 characters have been typed.
     */
    fun getPrefixMatches(prefix: String, layoutLang: String, limit: Int = 5): List<PersonalDictionaryEntry> {
        val clean = prefix.trim()
        if (clean.isEmpty()) return emptyList()

        val cleanLower = clean.lowercase()
        val results = mutableListOf<PersonalDictionaryEntry>()

        shortcutIndex[cleanLower]?.let { list ->
            for (entry in list) {
                if (entry.matchesLanguage(layoutLang) && results.none { it.id == entry.id }) {
                    results.add(entry)
                    if (results.size >= limit) return results
                }
            }
        }

        for (entry in entriesMap.values) {
            if (entry.matchesLanguage(layoutLang) && results.none { it.id == entry.id }) {
                val matchesWord = entry.word.startsWith(clean, ignoreCase = true)
                if (matchesWord) {
                    results.add(entry)
                    if (results.size >= limit) return results
                }
            }
        }

        if (cleanLower.length >= 4) {
            for ((sc, list) in shortcutIndex) {
                if (sc.length > 4 && sc != cleanLower && sc.startsWith(cleanLower)) {
                    for (entry in list) {
                        if (entry.matchesLanguage(layoutLang) && results.none { it.id == entry.id }) {
                            results.add(entry)
                            if (results.size >= limit) return results
                        }
                    }
                }
            }
        }

        return results
    }

    /**
     * Searches entries matching [query] across words and shortcuts, optionally filtered by [languageFilter].
     */
    fun searchEntries(query: String, languageFilter: String? = null): List<PersonalDictionaryEntry> {
        val cleanQuery = query.trim().lowercase()
        val allEntries = entriesMap.values

        return allEntries
            .asSequence()
            .filter { entry ->
                val matchesLang = languageFilter.isNullOrBlank() ||
                        languageFilter == PersonalDictionaryEntry.LANG_ALL ||
                        entry.language.equals(languageFilter, ignoreCase = true)

                if (!matchesLang) return@filter false

                if (cleanQuery.isEmpty()) return@filter true

                entry.word.lowercase().contains(cleanQuery) ||
                        (entry.shortcut?.lowercase()?.contains(cleanQuery) == true)
            }
            .sortedWith(compareByDescending<PersonalDictionaryEntry> { it.timestamp }.thenBy { it.word })
            .toList()
    }

    /**
     * Returns a snapshot list of all personal dictionary entries.
     */
    fun getAllEntries(): List<PersonalDictionaryEntry> {
        return entriesMap.values
            .sortedWith(compareByDescending<PersonalDictionaryEntry> { it.timestamp }.thenBy { it.word })
    }

    /**
     * Adds or updates a personal dictionary entry.
     */
    fun addOrUpdate(
        word: String,
        shortcut: String?,
        language: String = PersonalDictionaryEntry.LANG_ALL,
        existingId: String? = null
    ): PersonalDictionaryEntry {
        val cleanWord = PersonalDictionaryEntry.normalizeWord(word)
        val cleanShortcut = PersonalDictionaryEntry.normalizeShortcut(shortcut)
        val cleanLang = language.lowercase().trim().ifEmpty { PersonalDictionaryEntry.LANG_ALL }

        val id = existingId ?: java.util.UUID.randomUUID().toString()
        val entry = PersonalDictionaryEntry(
            id = id,
            word = cleanWord,
            shortcut = cleanShortcut,
            language = cleanLang,
            timestamp = System.currentTimeMillis()
        )

        val previous = entriesMap.put(id, entry)
        if (previous != null) {
            removeEntryFromIndices(previous)
        }
        addEntryToIndices(entry)

        notifyStateChanged()
        scheduleDebouncedSave()
        return entry
    }

    /**
     * Updates an existing entry directly.
     */
    fun update(entry: PersonalDictionaryEntry) {
        val cleanWord = PersonalDictionaryEntry.normalizeWord(entry.word)
        val cleanShortcut = PersonalDictionaryEntry.normalizeShortcut(entry.shortcut)
        val cleanLang = entry.language.lowercase().trim().ifEmpty { PersonalDictionaryEntry.LANG_ALL }

        val normalized = entry.copy(
            word = cleanWord,
            shortcut = cleanShortcut,
            language = cleanLang,
            timestamp = System.currentTimeMillis()
        )

        val previous = entriesMap.put(normalized.id, normalized)
        if (previous != null) {
            removeEntryFromIndices(previous)
        }
        addEntryToIndices(normalized)

        notifyStateChanged()
        scheduleDebouncedSave()
    }

    /**
     * Deletes a single entry by ID.
     */
    fun delete(id: String): Boolean {
        val removed = entriesMap.remove(id) ?: return false
        removeEntryFromIndices(removed)
        notifyStateChanged()
        scheduleDebouncedSave()
        return true
    }

    /**
     * Deletes multiple entries by IDs.
     */
    fun deleteMultiple(ids: Collection<String>): Int {
        var count = 0
        for (id in ids) {
            val removed = entriesMap.remove(id)
            if (removed != null) {
                removeEntryFromIndices(removed)
                count++
            }
        }
        if (count > 0) {
            notifyStateChanged()
            scheduleDebouncedSave()
        }
        return count
    }

    /**
     * Clears all personal dictionary entries.
     */
    fun clearAll() {
        entriesMap.clear()
        shortcutIndex.clear()
        notifyStateChanged()

        scope.launch {
            try {
                if (storageFile.exists()) {
                    storageFile.delete()
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Exports all personal dictionary entries to a .bornopdb formatted stream.
     */
    suspend fun exportToBornopdb(outputStream: OutputStream): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val snapshot = getAllEntries()
            val now = System.currentTimeMillis()
            val backup = PersonalDictionaryBackup(
                format = PersonalDictionaryBackup.FORMAT_BORNOPDB,
                schemaVersion = 1,
                type = PersonalDictionaryBackup.TYPE_PERSONAL_DICTIONARY,
                generator = PersonalDictionaryBackup.GENERATOR_KIEBORD,
                exportedAt = now,
                count = snapshot.size,
                db = PersonalDictionaryDbPayload(version = 1, data = snapshot),
                entries = snapshot,
                version = 1,
                exportTime = now
            )
            val jsonString = json.encodeToString(PersonalDictionaryBackup.serializer(), backup)

            OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonString)
                writer.flush()
            }
            Result.success(snapshot.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportToJson(outputStream: OutputStream): Result<Int> = exportToBornopdb(outputStream)

    /**
     * Imports entries from an [inputStream].
     *
     * Supports:
     * - Kiebord JSON backup format: `{ "version": 1, "entries": [ ... ] }`
     * - Direct JSON Array format: `[ { "word": ..., "shortcut": ..., "language": ... } ]`
     * - Borno/alternative nested format: `{ "db": { "data": [ ... ] } }` or `{ "data": [ ... ] }`
     * - Line-delimited TSV/CSV format: `word \t shortcut \t language`
     */
    suspend fun importFromJson(
        inputStream: InputStream,
        replaceExisting: Boolean = false
    ): Result<PersonalDictImportResult> = withContext(Dispatchers.IO) {
        try {
            val content = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use {
                it.readText()
            }
            if (content.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Empty input file"))
            }

            val parsedEntries = parseEntriesFromContent(content)
            if (parsedEntries.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No valid dictionary entries found in file"))
            }

            if (replaceExisting) {
                entriesMap.clear()
                shortcutIndex.clear()
            }

            var importedCount = 0
            var updatedCount = 0
            var skippedDuplicates = 0

            for (candidate in parsedEntries) {
                val cleanWord = PersonalDictionaryEntry.normalizeWord(candidate.word)
                if (cleanWord.isEmpty()) continue

                val cleanShortcut = PersonalDictionaryEntry.normalizeShortcut(candidate.shortcut)
                val cleanLang = candidate.language.lowercase().trim().ifEmpty { PersonalDictionaryEntry.LANG_ALL }

                // Check for duplicate or existing match (matching word, shortcut, and language)
                val existing = entriesMap.values.find {
                    it.word.equals(cleanWord, ignoreCase = false) &&
                            it.shortcut == cleanShortcut &&
                            it.language.equals(cleanLang, ignoreCase = true)
                }

                if (existing != null) {
                    if (replaceExisting) {
                        entriesMap[existing.id] = existing.copy(timestamp = candidate.timestamp)
                        updatedCount++
                    } else {
                        skippedDuplicates++
                    }
                } else {
                    val newEntry = candidate.copy(
                        id = if (candidate.id.isNotBlank()) candidate.id else java.util.UUID.randomUUID().toString(),
                        word = cleanWord,
                        shortcut = cleanShortcut,
                        language = cleanLang
                    )
                    entriesMap[newEntry.id] = newEntry
                    addEntryToIndices(newEntry)
                    importedCount++
                }
            }

            notifyStateChanged()
            saveToDiskImmediate()

            Result.success(
                PersonalDictImportResult(
                    totalFound = parsedEntries.size,
                    importedCount = importedCount,
                    updatedCount = updatedCount,
                    skippedDuplicates = skippedDuplicates
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseEntriesFromContent(content: String): List<PersonalDictionaryEntry> {
        val trimmed = content.trim()
        val list = mutableListOf<PersonalDictionaryEntry>()

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                // Try decoding as PersonalDictionaryBackup
                if (trimmed.startsWith("{")) {
                    try {
                        val backup = json.decodeFromString(PersonalDictionaryBackup.serializer(), trimmed)
                        if (backup.entries.isNotEmpty()) {
                            return backup.entries
                        }
                        if (backup.db.data.isNotEmpty()) {
                            return backup.db.data
                        }
                    } catch (_: Exception) {}
                }

                // Try decoding as List<PersonalDictionaryEntry>
                if (trimmed.startsWith("[")) {
                    try {
                        val directList = json.decodeFromString<List<PersonalDictionaryEntry>>(trimmed)
                        if (directList.isNotEmpty()) {
                            return directList
                        }
                    } catch (_: Exception) {}
                }

                // Fallback to generic JSON element parsing for Borno or loose JSON objects
                val element = json.parseToJsonElement(trimmed)
                extractEntriesFromJsonElement(element, list)
            } catch (_: Exception) {}
        }

        // If JSON parsing yielded nothing, attempt line-by-line TSV/CSV parsing
        if (list.isEmpty()) {
            val lines = trimmed.lines()
            for (line in lines) {
                val lineTrimmed = line.trim()
                if (lineTrimmed.isEmpty() || lineTrimmed.startsWith("#")) continue
                val parts = if (lineTrimmed.contains("\t")) {
                    lineTrimmed.split("\t")
                } else if (lineTrimmed.contains(",")) {
                    lineTrimmed.split(",")
                } else {
                    listOf(lineTrimmed)
                }

                val word = parts.getOrNull(0)?.trim() ?: continue
                if (word.isEmpty()) continue
                val shortcut = parts.getOrNull(1)?.trim()?.ifEmpty { null }
                val lang = parts.getOrNull(2)?.trim()?.ifEmpty { PersonalDictionaryEntry.LANG_ALL }
                    ?: PersonalDictionaryEntry.LANG_ALL

                list.add(PersonalDictionaryEntry(word = word, shortcut = shortcut, language = lang))
            }
        }

        return list
    }

    private fun extractEntriesFromJsonElement(element: JsonElement, list: MutableList<PersonalDictionaryEntry>) {
        when (element) {
            is JsonArray -> {
                for (item in element) {
                    if (item is JsonObject) {
                        parseJsonObjectEntry(item)?.let { list.add(it) }
                    }
                }
            }
            is JsonObject -> {
                val moduleObj = (element["modules"] as? JsonObject)?.get("personal_dictionary")
                    ?: element["personal_dictionary"]
                if (moduleObj != null) {
                    extractEntriesFromJsonElement(moduleObj, list)
                    if (list.isNotEmpty()) return
                }

                val entriesArr = element["entries"] as? JsonArray
                    ?: element["data"] as? JsonArray
                    ?: (element["db"] as? JsonObject)?.get("data") as? JsonArray

                if (entriesArr != null) {
                    extractEntriesFromJsonElement(entriesArr, list)
                }
            }
            else -> {}
        }
    }

    private fun parseJsonObjectEntry(obj: JsonObject): PersonalDictionaryEntry? {
        val word = obj["word"]?.jsonPrimitive?.content
            ?: obj["text"]?.jsonPrimitive?.content
            ?: return null
        if (word.isBlank()) return null

        val shortcut = obj["shortcut"]?.jsonPrimitive?.content?.ifBlank { null }
            ?: obj["mapping"]?.jsonPrimitive?.content?.ifBlank { null }
        val language = obj["language"]?.jsonPrimitive?.content?.ifBlank { PersonalDictionaryEntry.LANG_ALL }
            ?: obj["lang"]?.jsonPrimitive?.content?.ifBlank { PersonalDictionaryEntry.LANG_ALL }
            ?: PersonalDictionaryEntry.LANG_ALL
        val id = obj["id"]?.jsonPrimitive?.content?.ifBlank { java.util.UUID.randomUUID().toString() }
            ?: java.util.UUID.randomUUID().toString()
        val timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

        return PersonalDictionaryEntry(
            id = id,
            word = word,
            shortcut = shortcut,
            language = language,
            timestamp = timestamp
        )
    }

    private fun addEntryToIndices(entry: PersonalDictionaryEntry) {
        val sc = entry.shortcut ?: return
        val list = shortcutIndex.computeIfAbsent(sc) { CopyOnWriteArrayList() }
        list.addIfAbsent(entry)
    }

    private fun removeEntryFromIndices(entry: PersonalDictionaryEntry) {
        val sc = entry.shortcut ?: return
        val list = shortcutIndex[sc] ?: return
        list.removeIf { it.id == entry.id }
        if (list.isEmpty()) {
            shortcutIndex.remove(sc)
        }
    }

    private fun notifyStateChanged() {
        _entriesFlow.value = getAllEntries()
    }

    private fun scheduleDebouncedSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            saveToDiskImmediate()
        }
    }

    private fun saveToDiskImmediate() {
        try {
            val snapshot = getAllEntries()
            val now = System.currentTimeMillis()
            val backup = PersonalDictionaryBackup(
                format = PersonalDictionaryBackup.FORMAT_BORNOPDB,
                schemaVersion = 1,
                type = PersonalDictionaryBackup.TYPE_PERSONAL_DICTIONARY,
                generator = PersonalDictionaryBackup.GENERATOR_KIEBORD,
                exportedAt = now,
                count = snapshot.size,
                db = PersonalDictionaryDbPayload(version = 1, data = snapshot),
                entries = snapshot,
                version = 1,
                exportTime = now
            )
            val jsonString = json.encodeToString(PersonalDictionaryBackup.serializer(), backup)

            val tempFile = File(baseDir, "personal_dict_data.json.tmp")
            tempFile.writeText(jsonString)
            if (tempFile.exists()) {
                if (storageFile.exists()) {
                    storageFile.delete()
                }
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

                val parsed = parseEntriesFromContent(content)
                for (entry in parsed) {
                    val cleanWord = PersonalDictionaryEntry.normalizeWord(entry.word)
                    val cleanShortcut = PersonalDictionaryEntry.normalizeShortcut(entry.shortcut)
                    val cleanLang = entry.language.lowercase().trim().ifEmpty { PersonalDictionaryEntry.LANG_ALL }

                    val validEntry = entry.copy(
                        word = cleanWord,
                        shortcut = cleanShortcut,
                        language = cleanLang
                    )
                    entriesMap[validEntry.id] = validEntry
                    addEntryToIndices(validEntry)
                }
                notifyStateChanged()
            } catch (_: Throwable) {}
        }
    }

    companion object {
        @Volatile
        private var instance: PersonalDictionaryRepository? = null

        fun getInstance(context: Context): PersonalDictionaryRepository {
            return instance ?: synchronized(this) {
                instance ?: PersonalDictionaryRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
