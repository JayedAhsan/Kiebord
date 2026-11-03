package io.kiebord.feature.ime.khipro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KhiproCandidatesTest {

    @Test
    fun ordersRomanComposedCompletionsLearned() {
        val list = buildKhiproCandidates(
            roman = "ami",
            composed = "আমি",
            completions = listOf("আমার", "আমাদের"),
            learned = listOf("আমারটা"),
        )
        assertEquals(listOf("ami", "আমি", "আমার", "আমাদের", "আমারটা"), list)
    }

    @Test
    fun romanHiddenWhenBlankOrEqualToComposed() {
        assertEquals(
            listOf("আমি"),
            buildKhiproCandidates("", "আমি", emptyList(), emptyList()),
        )
        assertEquals(
            listOf("ami"),
            buildKhiproCandidates("ami", "ami", emptyList(), emptyList()),
        )
    }

    @Test
    fun dedupesAndDropsEntriesEqualToComposed() {
        val list = buildKhiproCandidates(
            roman = "ami",
            composed = "আমি",
            completions = listOf("আমি", "আমার", "আমার"),
            learned = listOf("আমার", "আমি2"),
        )
        assertEquals(listOf("ami", "আমি", "আমার", "আমি2"), list)
    }

    @Test
    fun capsAtEightCandidates() {
        val list = buildKhiproCandidates(
            roman = "abc",
            composed = "ক",
            completions = (1..10).map { "ক$it" },
            learned = (1..10).map { "শেখা$it" },
        )
        assertTrue(list.size <= 8)
    }

    @Test
    fun bufferKeysAreExactlyTheSpecKeys() {
        assertEquals(setOf('/', ';', ',', '.', '।'), KHIPRO_BUFFER_KEYS)
        assertEquals(
            setOf("bn_khipro_classic", "bn_khipro_dark"),
            KHIPRO_LAYOUT_IDS,
        )
    }
}
