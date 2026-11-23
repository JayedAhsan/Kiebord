package com.codepotro.borno.data.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserDictionaryRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testWordTypedOnceIsNotLearned() {
        val repo = UserDictionaryRepository(tempFolder.root)

        // Single occurrence should not be learned (transient candidate)
        repo.recordWord("developer")
        assertFalse(repo.isLearned("developer"))
        assertTrue(repo.getCompletions("dev").isEmpty())
        assertTrue(repo.getAllLearnedWords().isEmpty())

        // Minimum 2 occurrences promotes to learned
        repo.recordWord("developer")
        assertTrue(repo.isLearned("developer"))
        assertEquals(listOf("developer"), repo.getCompletions("dev"))
        assertEquals(listOf("developer"), repo.getAllLearnedWords())
    }

    @Test
    fun testShortWordsAreNeverLearned() {
        val repo = UserDictionaryRepository(tempFolder.root)

        // 1-letter and 2-letter English words
        repo.recordWord("a")
        repo.recordWord("a")
        repo.recordWord("hi")
        repo.recordWord("hi")
        repo.recordWord("to")
        repo.recordWord("to")

        assertFalse(repo.isLearned("a"))
        assertFalse(repo.isLearned("hi"))
        assertFalse(repo.isLearned("to"))
        assertTrue(repo.getCompletions("h").isEmpty())
        assertTrue(repo.getAllLearnedWords().isEmpty())

        // 2-character Bengali words (e.g. "না", "সে")
        repo.recordWord("না")
        repo.recordWord("না")
        repo.recordWord("সে")
        repo.recordWord("সে")

        assertFalse(repo.isLearned("না"))
        assertFalse(repo.isLearned("সে"))
        assertTrue(repo.getCompletions("ন").isEmpty())
        assertTrue(repo.getAllLearnedWords().isEmpty())
    }

    @Test
    fun testLearnedBengaliBigrams() {
        val repo = UserDictionaryRepository(tempFolder.root)

        // Record "নাম" following "আমার" twice to meet MIN_LEARN_OCCURRENCES
        repo.recordWord("নাম", prevWord = "আমার")
        assertTrue(repo.getNextWords("আমার").isEmpty()) // Not learned on 1 occurrence

        repo.recordWord("নাম", prevWord = "আমার")
        val nextWords = repo.getNextWords("আমার")
        assertEquals(listOf("নাম"), nextWords)
    }

    @Test
    fun testLearnedBigramRankingByFrequency() {
        val repo = UserDictionaryRepository(tempFolder.root)

        // "আমার দেশ" typed twice, "আমার নাম" typed three times
        repo.recordWord("দেশ", prevWord = "আমার")
        repo.recordWord("দেশ", prevWord = "আমার")
        repo.recordWord("নাম", prevWord = "আমার")
        repo.recordWord("নাম", prevWord = "আমার")
        repo.recordWord("নাম", prevWord = "আমার")

        val nextWords = repo.getNextWords("আমার")
        assertEquals("নাম", nextWords.firstOrNull())
        assertTrue(nextWords.contains("দেশ"))
    }

    @Test
    fun testReduplicatedWordsBigram() {
        val repo = UserDictionaryRepository(tempFolder.root)

        // "খুব খুব" (khub khub) typed twice
        repo.recordWord("খুব", prevWord = "খুব")
        repo.recordWord("খুব", prevWord = "খুব")

        val nextWords = repo.getNextWords("খুব")
        assertEquals(listOf("খুব"), nextWords)
    }

    @Test
    fun testCompletionsForLearnedWords() {
        val repo = UserDictionaryRepository(tempFolder.root)

        repo.recordWord("বাংলাদেশ")
        repo.recordWord("বাংলাদেশ")
        repo.recordWord("বাঙালি")
        repo.recordWord("বাঙালি")

        val completions = repo.getCompletions("বা")
        assertTrue(completions.contains("বাংলাদেশ"))
        assertTrue(completions.contains("বাঙালি"))
    }

    @Test
    fun testIsLearnedAndUnlearnWord() {
        val repo = UserDictionaryRepository(tempFolder.root)

        repo.recordWord("kiebord", prevWord = "antigravity")
        repo.recordWord("kiebord", prevWord = "antigravity")
        repo.recordWord("swift", isGesture = true)
        repo.recordWord("swift", isGesture = true)

        assertTrue(repo.isLearned("kiebord"))
        assertTrue(repo.isLearned("swift"))
        assertFalse(repo.isLearned("nonexistent"))

        // Bigram check
        assertEquals(listOf("kiebord"), repo.getNextWords("antigravity"))

        // Unlearn "kiebord"
        repo.unlearnWord("kiebord")
        assertFalse(repo.isLearned("kiebord"))
        assertTrue(repo.getNextWords("antigravity").isEmpty())

        // Unlearn "swift"
        repo.unlearnWord("swift")
        assertFalse(repo.isLearned("swift"))
    }

    @Test
    fun testGetAllLearnedWords() {
        val repo = UserDictionaryRepository(tempFolder.root)

        repo.recordWord("apple")
        repo.recordWord("apple")
        repeat(5) {
            repo.recordWord("banana")
        }
        repo.recordWord("cherry", isGesture = true)
        repo.recordWord("cherry", isGesture = true)

        val learned = repo.getAllLearnedWords()
        assertTrue(learned.contains("apple"))
        assertTrue(learned.contains("banana"))
        assertTrue(learned.contains("cherry"))
        assertEquals("banana", learned.first())
    }


    @Test
    fun testBengaliNextWordPredictionFromCombinedCorpus() {
        val repo = UserDictionaryRepository(tempFolder.root)

        // Contextual Bengali bigrams matching bn.combined corpus pairs (length >= 3, recorded >= 2 times)
        // "আমাদের" -> "আগ্রহের", "দেশে"
        repeat(3) {
            repo.recordWord("আগ্রহের", prevWord = "আমাদের")
        }
        repeat(2) {
            repo.recordWord("দেশে", prevWord = "আমাদের")
        }

        // "অনেক" -> "সময়", "বেশি"
        repeat(3) {
            repo.recordWord("সময়", prevWord = "অনেক")
        }
        repeat(2) {
            repo.recordWord("বেশি", prevWord = "অনেক")
        }

        val amaderNext = repo.getNextWords("আমাদের")
        assertEquals(listOf("আগ্রহের", "দেশে"), amaderNext)

        val onekNext = repo.getNextWords("অনেক")
        assertEquals(listOf("সময়", "বেশি"), onekNext)
    }

    @Test
    fun testLearnedGestureWordPrioritization() {
        val repo = UserDictionaryRepository(tempFolder.root)

        // Simulate choosing a gesture alternative
        repeat(3) {
            repo.recordWord("আমির", prevWord = "আমরা", isGesture = true)
        }

        val gestureWords = repo.getLearnedGestureWords(10)
        assertEquals(listOf("আমির"), gestureWords)

        val allLearned = repo.getAllLearnedWords(10)
        assertTrue(allLearned.contains("আমির"))
    }

    @Test
    fun testLearnedWordsFlowUpdatesOnRecordUnlearnAndClear() {
        val repo = UserDictionaryRepository(tempFolder.root)
        assertTrue(repo.learnedWordsFlow.value.isEmpty())

        repo.recordWord("বাংলা")
        assertTrue(repo.learnedWordsFlow.value.isEmpty())

        repo.recordWord("বাংলা")
        assertEquals(listOf("বাংলা"), repo.learnedWordsFlow.value)

        repo.recordWord("hello")
        repo.recordWord("hello")
        assertTrue(repo.learnedWordsFlow.value.containsAll(listOf("বাংলা", "hello")))

        repo.unlearnWord("বাংলা")
        assertEquals(listOf("hello"), repo.learnedWordsFlow.value)

        repo.clearAll()
        assertTrue(repo.learnedWordsFlow.value.isEmpty())
    }
}

