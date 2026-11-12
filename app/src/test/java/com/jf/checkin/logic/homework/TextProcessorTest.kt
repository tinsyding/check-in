package com.jf.checkin.logic.homework

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextProcessorTest {

    @Test
    fun smartMode_splitsContinuousDigitsIntoIncreasingNumbers() {
        assertEquals(listOf("1", "3", "5"), TextProcessor.parseNumbers("135"))
        assertEquals(
            listOf("1", "5", "7", "11", "13", "25"),
            TextProcessor.parseNumbers("157111325"),
        )
    }

    @Test
    fun smartMode_keepsExplicitSeparators() {
        assertEquals(listOf("1", "3", "12"), TextProcessor.parseNumbers("1 3 12"))
        assertEquals(listOf("1", "3", "5"), TextProcessor.parseNumbers("1,3,5"))
        assertEquals(listOf("1", "3", "5"), TextProcessor.parseNumbers("1，3，5"))
    }

    @Test
    fun continuousMode_splitsEveryDigit() {
        assertEquals(listOf("1", "5", "7"), TextProcessor.parseNumbers("157", TextProcessor.MODE_CONTINUOUS))
    }

    @Test
    fun separatedMode_onlyUsesExistingSeparators() {
        assertEquals(listOf("135"), TextProcessor.parseNumbers("135", TextProcessor.MODE_SEPARATED))
        assertEquals(listOf("1", "3"), TextProcessor.parseNumbers("1，3", TextProcessor.MODE_SEPARATED))
    }

    @Test
    fun emptyInputReturnsEmptyList() {
        assertTrue(TextProcessor.parseNumbers("   ").isEmpty())
    }

    @Test
    fun formatOutput_replacesPlaceholder() {
        assertEquals(
            "本次作业的错题题号为：1，3，5",
            TextProcessor.formatOutput(listOf("1", "3", "5")),
        )
    }

    @Test
    fun formatOutput_appendsNumbersWhenTemplateHasNoPlaceholder() {
        assertEquals(
            "错题订正：1、3",
            TextProcessor.formatOutput(listOf("1", "3"), "错题订正：", "、"),
        )
    }

    @Test
    fun formatOutput_returnsEmptyForNoNumbers() {
        assertEquals("", TextProcessor.formatOutput(emptyList()))
    }

    @Test
    fun matchTrigger_matchesDigitsBeforeTrigger() {
        val match = TextProcessor.matchTrigger("135qqq", "qqq")
        assertEquals("135", match?.rawNumbers)
        assertEquals(0, match?.startIndex)
        assertEquals(6, match?.endIndex)
    }

    @Test
    fun matchTrigger_matchesSeparatedNumbersAndPrefix() {
        val match = TextProcessor.matchTrigger("题目 1 3 12qqq", "qqq")
        assertEquals("1 3 12", match?.rawNumbers)
        assertEquals(3, match?.startIndex)
    }

    @Test
    fun matchTrigger_isCaseInsensitiveAndEscapesRegex() {
        assertEquals("135", TextProcessor.matchTrigger("135QQQ", "qqq")?.rawNumbers)
        assertEquals("135", TextProcessor.matchTrigger("135..", "..")?.rawNumbers)
    }

    @Test
    fun matchTrigger_returnsNullWithoutTriggerOrDigits() {
        assertNull(TextProcessor.matchTrigger("135", "qqq"))
        assertNull(TextProcessor.matchTrigger("qqq", "qqq"))
        assertNull(TextProcessor.matchTrigger("abcqqq", "qqq"))
    }
}
