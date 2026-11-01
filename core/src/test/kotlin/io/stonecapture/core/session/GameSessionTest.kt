package io.stonecapture.core.session

import io.stonecapture.core.GameState
import io.stonecapture.core.Phase
import io.stonecapture.core.Player
import io.stonecapture.core.Stone
import io.stonecapture.core.StoneCaptureEngine
import io.stonecapture.core.bot.Difficulty
import io.stonecapture.core.bot.EasyBot
import io.stonecapture.core.bot.MediumBot
import io.stonecapture.core.side
import io.stonecapture.core.stones
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameSessionTest {
    private val engine = StoneCaptureEngine

    /** A consistent deal where the human (A) starts, then [customize] reshapes it. */
    private fun session(customize: (GameState) -> GameState = { it }): GameSession =
        GameSession(customize(engine.newGame(seed = 5, firstPlayer = Player.A)), Difficulty.EASY)

    @Test
    fun `select toggles a card from the hand`() {
        val s = session()
        val card = s.game.handA.first()
        assertEquals(card, s.select(card).selected)
        assertNull(s.select(card).select(card).selected)
    }

    @Test
    fun `select ignores cards outside the hand, other phases and the bot's turn`() {
        val s = session()
        assertSame(s, s.select(s.game.handB.first()))

        val claimPhase = s.copy(game = s.game.copy(phase = Phase.CLAIM))
        assertSame(claimPhase, claimPhase.select(claimPhase.game.handA.first()))

        val botTurn = s.copy(game = s.game.copy(current = Player.B))
        assertSame(botTurn, botTurn.select(botTurn.game.handA.first()))
    }

    @Test
    fun `tapping a stone without a selected card does nothing in the play phase`() {
        val s = session()
        assertSame(s, s.tapStone(4))
    }

    @Test
    fun `tapping a stone plays the selected card and clears the selection`() {
        val s = session()
        val card = s.game.handA.first()
        val next = s.select(card).tapStone(4)
        assertEquals(Phase.CLAIM, next.game.phase)
        assertEquals(listOf(card), next.game.stones[4].sideA.cards)
        assertNull(next.selected)
    }

    @Test
    fun `play targets are open stones with room on the human side`() {
        val s = session { it.copy(stones = stones(Stone(2, owner = Player.B), Stone(3, side("R1 R2 R3")))) }
            .select(session().game.handA.first())
        assertFalse(s.isPlayTarget(2))
        assertFalse(s.isPlayTarget(3))
        assertTrue(s.isPlayTarget(4))
    }

    @Test
    fun `claiming works only for provable stones in the claim phase`() {
        val claimable = session {
            it.copy(
                phase = Phase.CLAIM,
                stones = stones(
                    Stone(0, side("R7 R8 R9", 1), side("B1 G2 Y4", 2)),
                    Stone(1, side("R1 R2 B5", 1), side("B7 G8 Y2", 2)),
                ),
            )
        }
        assertTrue(claimable.canClaim(0))
        assertFalse(claimable.canClaim(1))
        assertEquals(Player.A, claimable.tapStone(0).game.stones[0].owner)
        assertSame(claimable, claimable.tapStone(1))
    }

    @Test
    fun `end turn passes to the bot and the bot steps until it is the human's turn`() {
        var s = session().let { it.select(it.game.handA.first()).tapStone(0) }.endTurn()
        assertTrue(s.isBotTurn)
        val bot = EasyBot(1)
        var steps = 0
        while (s.isBotTurn) {
            s = s.botStep(bot)
            assertTrue(steps++ < 10)
        }
        assertTrue(s.isHumanTurn)
        assertEquals(Phase.PLAY, s.game.phase)
    }

    @Test
    fun `bot step does nothing on the human's turn`() {
        val s = session()
        assertSame(s, s.botStep(EasyBot()))
    }

    @Test
    fun `human input is ignored on the bot's turn`() {
        val s = session { it.copy(current = Player.B) }
        assertSame(s, s.endTurn())
        assertSame(s, s.pass())
    }

    @Test
    fun `pass is only offered when nothing can be played`() {
        assertFalse(session().canPass())
        val empty = session { it.copy(handA = emptyList()) }
        assertTrue(empty.canPass())
        assertEquals(Phase.CLAIM, empty.pass().game.phase)
    }

    @Test
    fun `auto claim takes every provable stone`() {
        val s = session {
            it.copy(
                phase = Phase.CLAIM,
                stones = stones(
                    Stone(0, side("R7 R8 R9", 1), side("B1 G2 Y4", 2)),
                    Stone(2, side("B5 B6 B7", 3), side("G1 Y3 O9", 4)),
                    Stone(5, side("R1 R2 B5", 1), side("B7 G8 Y2", 2)),
                ),
            )
        }.autoClaim()
        assertEquals(listOf(0, 2), s.game.stonesOwnedBy(Player.A))
        assertNull(s.game.stones[5].owner)
    }

    @Test
    fun `auto claim stops once the game is won`() {
        val s = session {
            it.copy(
                phase = Phase.CLAIM,
                stones = stones(
                    Stone(0, owner = Player.A),
                    Stone(1, owner = Player.A),
                    Stone(2, side("R7 R8 R9", 1), side("B1 G2 Y4", 2)),
                    Stone(7, side("B5 B6 B7", 3), side("G1 Y3 O9", 4)),
                ),
            )
        }.autoClaim()
        assertTrue(s.isFinished)
        assertEquals(Player.A, s.game.winner)
        assertNull(s.game.stones[7].owner)
    }

    @Test
    fun `nothing can be done after the game is finished`() {
        val s = session { it.copy(phase = Phase.FINISHED, winner = Player.A) }
        assertSame(s, s.select(s.game.handA.first()))
        assertSame(s, s.endTurn())
        assertSame(s, s.autoClaim())
        assertSame(s, s.botStep(EasyBot()))
    }

    @Test
    fun `a human following a random clicking policy always reaches a result`() {
        repeat(60) { seed ->
            val random = Random(seed)
            val bot = if (seed % 2 == 0) EasyBot(seed.toLong()) else MediumBot(seed.toLong(), samples = 10)
            var s = GameSession(engine.newGame(seed.toLong()), Difficulty.EASY)
            var steps = 0
            while (!s.isFinished) {
                assertTrue(steps++ < 2000, "seed $seed did not finish")
                s = when {
                    s.isBotTurn -> s.botStep(bot)
                    s.game.phase == Phase.PLAY && s.canPass() -> s.pass()
                    s.game.phase == Phase.PLAY -> {
                        val card = s.game.handA.random(random)
                        val targets = s.game.stones.indices.filter { s.select(card).isPlayTarget(it) }
                        s.select(card).tapStone(targets.random(random))
                    }
                    else -> {
                        val claim = s.game.stones.indices.firstOrNull { s.canClaim(it) }
                        if (claim != null) s.tapStone(claim) else s.endTurn()
                    }
                }
            }
            assertTrue(s.isFinished)
        }
    }
}
