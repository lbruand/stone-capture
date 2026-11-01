package io.stonecapture.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DeckTest {
    @Test
    fun `deck has 54 unique cards`() {
        val deck = fullDeck()
        assertEquals(54, deck.size)
        assertEquals(54, deck.toSet().size)
    }

    @Test
    fun `every color has ranks 1 to 9`() {
        val deck = fullDeck()
        for (color in CardColor.entries) {
            assertEquals((1..9).toList(), deck.filter { it.color == color }.map { it.rank }.sorted())
        }
    }

    @Test
    fun `shuffle is deterministic per seed`() {
        assertEquals(shuffledDeck(Random(1)), shuffledDeck(Random(1)))
        assertNotEquals(shuffledDeck(Random(1)), shuffledDeck(Random(2)))
    }
}
