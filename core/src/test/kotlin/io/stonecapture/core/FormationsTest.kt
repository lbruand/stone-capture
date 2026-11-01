package io.stonecapture.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FormationsTest {
    private fun value(codes: String) = evaluate(cards(codes))

    @Test
    fun `classifies every formation`() {
        assertEquals(Formation.STRAIGHT_FLUSH, value("R3 R4 R5").formation)
        assertEquals(Formation.TRIPLE, value("R5 B5 G5").formation)
        assertEquals(Formation.FLUSH, value("R1 R4 R9").formation)
        assertEquals(Formation.STRAIGHT, value("R3 B4 G5").formation)
        assertEquals(Formation.MIXED, value("R3 B4 G6").formation)
    }

    @Test
    fun `card order does not matter`() {
        assertEquals(value("R3 R4 R5"), value("R5 R3 R4"))
    }

    @Test
    fun `sum is the sum of ranks`() {
        assertEquals(12, value("R3 R4 R5").sum)
    }

    @Test
    fun `formations rank straight flush over triple over flush over straight over mixed`() {
        val ordered = listOf(
            value("R1 R2 R3"),
            value("R1 B1 G1"),
            value("R9 R8 R1"),
            value("R9 B8 G7"),
            value("R9 B8 G6"),
        )
        for ((stronger, weaker) in ordered.zipWithNext()) assertTrue(stronger > weaker, "$stronger > $weaker")
    }

    @Test
    fun `same formation is decided by sum`() {
        assertTrue(value("R4 R5 R6") > value("B1 B2 B3"))
        assertTrue(value("R9 B8 G6") > value("R1 B2 G4"))
    }

    @Test
    fun `a weak straight flush still beats a strong triple`() {
        assertTrue(value("R1 R2 R3") > value("R9 B9 G9"))
    }

    @Test
    fun `needs exactly three cards`() {
        assertFailsWith<IllegalArgumentException> { evaluate(cards("R1 R2")) }
    }

    @Test
    fun `ranks 9 1 2 do not wrap around`() {
        assertEquals(Formation.MIXED, value("R9 B1 G2").formation)
    }
}
