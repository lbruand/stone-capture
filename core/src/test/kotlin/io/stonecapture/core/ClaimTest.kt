package io.stonecapture.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaimTest {
    private fun stone(a: Side = Side(), b: Side = Side(), owner: Player? = null) =
        stones(Stone(0, a, b, owner))

    @Test
    fun `both full - stronger formation can claim`() {
        val s = stone(side("R3 R4 R5", 1), side("B1 G2 Y5", 2))
        assertTrue(canClaim(s, 0, Player.A))
        assertFalse(canClaim(s, 0, Player.B))
    }

    @Test
    fun `both full - equal formation goes to whoever completed first`() {
        val s = stone(side("R3 R4 R5", 5), side("B3 B4 B5", 3))
        assertTrue(canClaim(s, 0, Player.B))
        assertFalse(canClaim(s, 0, Player.A))
    }

    @Test
    fun `both full - same formation, higher sum wins even if completed later`() {
        val s = stone(side("R4 R5 R6", 9), side("B1 B2 B3", 1))
        assertTrue(canClaim(s, 0, Player.A))
        assertFalse(canClaim(s, 0, Player.B))
    }

    @Test
    fun `cannot claim without a full side`() {
        val s = stone(side("R3 R4"), Side())
        assertFalse(canClaim(s, 0, Player.A))
    }

    @Test
    fun `cannot claim an already claimed stone`() {
        val s = stone(side("R3 R4 R5"), Side(), owner = Player.B)
        assertFalse(canClaim(s, 0, Player.A))
    }

    @Test
    fun `cannot claim an unknown stone`() {
        assertFalse(canClaim(stone(), 99, Player.A))
        assertFalse(canClaim(stone(), -1, Player.A))
    }

    @Test
    fun `full side against empty side is not provable with a mixed hand`() {
        // Anything the opponent could build still includes straight flushes.
        val s = stone(side("R9 B9 G9", 1), Side())
        assertFalse(canClaim(s, 0, Player.A))
    }

    @Test
    fun `best possible formation is always provable when opponent is empty`() {
        // R7 R8 R9 is the top straight flush: nothing can beat it, and ties lose to the later side.
        val s = stone(side("R7 R8 R9", 1), Side())
        assertTrue(canClaim(s, 0, Player.A))
    }

    @Test
    fun `tie by completion order lets the earlier claimant claim`() {
        // Opponent could at best equal R7 R8 R9 but would complete later, so they lose the tie.
        val s = stone(side("R7 R8 R9", 1), side("R7"))
        assertTrue(canClaim(s, 0, Player.A))
    }

    @Test
    fun `claimant's own hand is not proof - cards in hand still count as available`() {
        // The opponent holds B8 B9 and B7 would complete the top straight flush (sum 24),
        // beating R5 R6 R7 (sum 18). B7 is not face up anywhere - it may be in the claimant's own
        // hand - so the claim is rejected.
        val s = stone(side("R5 R6 R7", 1), side("B8 B9"))
        assertFalse(canClaim(s, 0, Player.A))
    }

    @Test
    fun `claim becomes provable once the blocking card is face up on another stone`() {
        val mine = side("R5 R6 R7", 1)
        val theirs = side("B8 B9")
        val s = stones(Stone(0, mine, theirs), Stone(1, side("B7 Y1 Y2", 2), Side()))
        // B7 is face up elsewhere, so the opponent can no longer make a straight flush. Their best
        // remaining formations (flushes, straights) are all weaker than a straight flush.
        assertTrue(canClaim(s, 0, Player.A))
    }

    @Test
    fun `available cards exclude every face up card`() {
        val s = stones(Stone(0, side("R1 R2 R3", 1), side("B1")), Stone(1, Side(), side("G9")))
        val available = availableCards(s)
        assertEquals(54 - 5, available.size)
        assertFalse(c("R1") in available)
        assertFalse(c("G9") in available)
        assertTrue(c("G8") in available)
    }

    @Test
    fun `win conditions - five stones or three adjacent`() {
        fun owned(vararg idx: Int) = List(STONE_COUNT) { Stone(it, owner = if (it in idx) Player.A else null) }
        assertTrue(hasWon(owned(0, 2, 4, 6, 8), Player.A))
        assertTrue(hasWon(owned(3, 4, 5), Player.A))
        assertTrue(hasWon(owned(0, 1, 2), Player.A))
        assertTrue(hasWon(owned(6, 7, 8), Player.A))
        assertFalse(hasWon(owned(0, 1, 3, 4), Player.A))
        assertFalse(hasWon(owned(0, 2, 4, 6), Player.A))
        assertFalse(hasWon(owned(0, 1, 2), Player.B))
    }
}
