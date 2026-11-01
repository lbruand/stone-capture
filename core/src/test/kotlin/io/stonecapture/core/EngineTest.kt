package io.stonecapture.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineTest {
    private val engine = StoneCaptureEngine

    private fun state(
        stones: List<Stone> = stones(),
        handA: String = "",
        handB: String = "",
        pile: String = "",
        current: Player = Player.A,
        phase: Phase = Phase.PLAY,
        moveCount: Int = 10,
    ) = GameState(stones, cards(handA), cards(handB), cards(pile), current, moveCount, phase)

    @Test
    fun `new game deals six cards each and keeps all 54 cards`() {
        val game = engine.newGame(seed = 42, firstPlayer = Player.A)
        assertEquals(6, game.handA.size)
        assertEquals(6, game.handB.size)
        assertEquals(42, game.drawPile.size)
        assertEquals(54, (game.handA + game.handB + game.drawPile).toSet().size)
        assertEquals(Player.A, game.current)
        assertEquals(Phase.PLAY, game.phase)
        assertEquals(STONE_COUNT, game.stones.size)
    }

    @Test
    fun `same seed gives the same game`() {
        assertEquals(engine.newGame(seed = 7), engine.newGame(seed = 7))
    }

    @Test
    fun `playing a card moves it from hand to the stone and opens the claim phase`() {
        val next = engine.apply(state(handA = "R1 R2"), Action.PlayCard(c("R1"), 4)).getOrThrow()
        assertEquals(listOf(c("R2")), next.handA)
        assertEquals(listOf(c("R1")), next.stones[4].sideA.cards)
        assertEquals(Phase.CLAIM, next.phase)
        assertEquals(11, next.moveCount)
    }

    @Test
    fun `completing a side records the move number`() {
        val s = state(stones = stones(Stone(2, side("R1 R2"))), handA = "R3")
        val next = engine.apply(s, Action.PlayCard(c("R3"), 2)).getOrThrow()
        assertEquals(11, next.stones[2].sideA.completedAt)
    }

    @Test
    fun `rejects a card that is not in hand`() {
        assertTrue(engine.apply(state(handA = "R1"), Action.PlayCard(c("B9"), 0)).isFailure)
    }

    @Test
    fun `rejects an unknown stone`() {
        assertTrue(engine.apply(state(handA = "R1"), Action.PlayCard(c("R1"), 9)).isFailure)
        assertTrue(engine.apply(state(handA = "R1"), Action.PlayCard(c("R1"), -1)).isFailure)
    }

    @Test
    fun `rejects a full side`() {
        val s = state(stones = stones(Stone(0, side("R1 R2 R3"))), handA = "B1")
        assertTrue(engine.apply(s, Action.PlayCard(c("B1"), 0)).isFailure)
    }

    @Test
    fun `rejects a claimed stone`() {
        val s = state(stones = stones(Stone(0, owner = Player.B)), handA = "B1")
        assertTrue(engine.apply(s, Action.PlayCard(c("B1"), 0)).isFailure)
    }

    @Test
    fun `rejects actions in the wrong phase`() {
        val play = state(handA = "R1")
        assertTrue(engine.apply(play, Action.EndTurn).isFailure)
        assertTrue(engine.apply(play, Action.Claim(0)).isFailure)
        val claim = play.copy(phase = Phase.CLAIM)
        assertTrue(engine.apply(claim, Action.PlayCard(c("R1"), 0)).isFailure)
    }

    @Test
    fun `end turn draws the top card and passes the turn`() {
        val s = state(handA = "R1", pile = "B1 B2", phase = Phase.CLAIM)
        val next = engine.apply(s, Action.EndTurn).getOrThrow()
        assertEquals(cards("R1 B1"), next.handA)
        assertEquals(cards("B2"), next.drawPile)
        assertEquals(Player.B, next.current)
        assertEquals(Phase.PLAY, next.phase)
    }

    @Test
    fun `end turn with an empty pile just passes the turn`() {
        val s = state(handA = "R1", handB = "B1", phase = Phase.CLAIM)
        val next = engine.apply(s, Action.EndTurn).getOrThrow()
        assertEquals(cards("R1"), next.handA)
        assertEquals(Player.B, next.current)
    }

    @Test
    fun `claim marks the stone and keeps the turn in the claim phase`() {
        val s = state(
            stones = stones(Stone(0, side("R7 R8 R9", 1), side("B1 G2 Y4", 2))),
            phase = Phase.CLAIM,
        )
        val next = engine.apply(s, Action.Claim(0)).getOrThrow()
        assertEquals(Player.A, next.stones[0].owner)
        assertEquals(Phase.CLAIM, next.phase)
        assertNull(next.winner)
    }

    @Test
    fun `an unprovable claim is rejected without penalty`() {
        val s = state(stones = stones(Stone(0, side("R7 R8", null))), phase = Phase.CLAIM)
        assertTrue(engine.apply(s, Action.Claim(0)).isFailure)
    }

    @Test
    fun `claiming a fifth stone wins immediately`() {
        val almost = stones(
            Stone(0, owner = Player.A), Stone(2, owner = Player.A), Stone(4, owner = Player.A),
            Stone(6, owner = Player.A),
            Stone(8, side("R7 R8 R9", 1), side("B1 G2 Y4", 2)),
        )
        assertEquals(4, almost.count { it.owner == Player.A })
        val next = engine.apply(state(stones = almost, phase = Phase.CLAIM), Action.Claim(8)).getOrThrow()
        assertEquals(Phase.FINISHED, next.phase)
        assertEquals(Player.A, next.winner)
        assertTrue(engine.legalActions(next).isEmpty())
    }

    @Test
    fun `claiming a third adjacent stone wins immediately`() {
        val s = state(
            stones = stones(
                Stone(3, owner = Player.A), Stone(4, owner = Player.A),
                Stone(5, side("R7 R8 R9", 1), side("B1 G2 Y4", 2)),
            ),
            phase = Phase.CLAIM,
        )
        val next = engine.apply(s, Action.Claim(5)).getOrThrow()
        assertEquals(Player.A, next.winner)
    }

    @Test
    fun `legal plays are every held card on every open stone with room`() {
        val s = state(
            stones = stones(Stone(0, owner = Player.B), Stone(1, side("R1 R2 R3"))),
            handA = "B1 B2",
        )
        val plays = engine.legalActions(s)
        assertEquals(2 * 7, plays.size) // 7 usable stones x 2 cards
        assertTrue(plays.all { it is Action.PlayCard })
    }

    @Test
    fun `pass is only legal when nothing can be played`() {
        val noCards = state(handA = "")
        assertEquals(listOf(Action.Pass), engine.legalActions(noCards))
        assertEquals(Phase.CLAIM, engine.apply(noCards, Action.Pass).getOrThrow().phase)

        val canPlay = state(handA = "R1")
        assertTrue(engine.apply(canPlay, Action.Pass).isFailure)
    }

    @Test
    fun `claim phase offers provable claims and end turn`() {
        val s = state(
            stones = stones(
                Stone(0, side("R7 R8 R9", 1), side("B1 G2 Y4", 2)),
                Stone(1, side("R1 R2 B5", 1), side("B7 G8 Y2", 2)),
            ),
            phase = Phase.CLAIM,
        )
        assertEquals(listOf(Action.Claim(0), Action.EndTurn), engine.legalActions(s))
    }

    @Test
    fun `stalemate settles finished stones and the majority wins`() {
        // Last cards in play: after A ends the turn nobody holds a card.
        val s = state(
            stones = stones(
                Stone(0, side("R7 R8 R9", 1), side("B1 G2 Y4", 2)),
                Stone(1, side("R1 R2 B5", 1), side("B7 G8 Y2", 2)),
                Stone(2, side("R3 R4 R5", 3), side("B3 B4 B5", 2)),
            ),
            phase = Phase.CLAIM,
        )
        val next = engine.apply(s, Action.EndTurn).getOrThrow()
        assertEquals(Phase.FINISHED, next.phase)
        assertEquals(Player.A, next.stones[0].owner)
        assertEquals(Player.B, next.stones[1].owner)
        assertEquals(Player.B, next.stones[2].owner) // equal formation, B completed first
        assertEquals(Player.B, next.winner)
    }

    @Test
    fun `stalemate with equal stones is a draw`() {
        val s = state(
            stones = stones(
                Stone(0, side("R7 R8 R9", 1), side("B1 G2 Y4", 2)),
                Stone(1, side("R1 R2 B5", 1), side("B7 G8 Y2", 2)),
            ),
            phase = Phase.CLAIM,
        )
        val next = engine.apply(s, Action.EndTurn).getOrThrow()
        assertEquals(Phase.FINISHED, next.phase)
        assertNull(next.winner)
    }

    @Test
    fun `seeded random playouts always finish and keep the invariants`() {
        repeat(300) { seed ->
            val random = Random(seed)
            var game = engine.newGame(seed = seed.toLong())
            var steps = 0
            while (game.phase != Phase.FINISHED) {
                assertTrue(steps++ < 1000, "seed $seed did not terminate")
                val actions = engine.legalActions(game)
                assertTrue(actions.isNotEmpty(), "seed $seed has no legal action in ${game.phase}")
                game = engine.apply(game, actions.random(random)).getOrThrow()
                assertInvariants(game)
            }
            if (game.winner != null) assertNotNull(game.winner)
        }
    }

    private fun assertInvariants(game: GameState) {
        val onTable = game.stones.flatMap { it.sideA.cards + it.sideB.cards }
        val all = onTable + game.handA + game.handB + game.drawPile
        assertEquals(54, all.size, "card conservation")
        assertEquals(54, all.toSet().size, "no duplicate cards")
        assertEquals(STONE_COUNT, game.stones.size)
        assertFalse(game.handA.size > HAND_SIZE || game.handB.size > HAND_SIZE)
    }
}
