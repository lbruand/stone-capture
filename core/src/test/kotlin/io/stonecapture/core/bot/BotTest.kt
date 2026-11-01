package io.stonecapture.core.bot

import io.stonecapture.core.Action
import io.stonecapture.core.Phase
import io.stonecapture.core.Player
import io.stonecapture.core.Side
import io.stonecapture.core.Stone
import io.stonecapture.core.StoneCaptureEngine
import io.stonecapture.core.c
import io.stonecapture.core.cards
import io.stonecapture.core.side
import io.stonecapture.core.stones
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BotTest {
    private val engine = StoneCaptureEngine

    @Test
    fun `view does not depend on the opponent hand or the draw pile order`() {
        val game = engine.newGame(seed = 3, firstPlayer = Player.A)
        val tampered = game.copy(handB = game.handB.reversed(), drawPile = game.drawPile.reversed())
        // Counts are public; contents and order are not.
        assertEquals(game.viewFor(Player.A), tampered.viewFor(Player.A))
    }

    @Test
    fun `easy bot only plays legal games`() {
        val result = playMatch(EasyBot(1), EasyBot(2), games = 50)
        assertEquals(50, result.games)
    }

    @Test
    fun `medium bot only plays legal games`() {
        val result = playMatch(MediumBot(1), MediumBot(2), games = 20)
        assertEquals(20, result.games)
    }

    @Test
    fun `bots pass when nothing can be played`() {
        val state = engine.newGame(seed = 1, firstPlayer = Player.A).copy(handA = emptyList())
        val legal = engine.legalActions(state)
        assertEquals(Action.Pass, EasyBot().choose(state.viewFor(Player.A), legal))
        assertEquals(Action.Pass, MediumBot().choose(state.viewFor(Player.A), legal))
    }

    @Test
    fun `medium bot claims a provable stone before ending its turn`() {
        val state = engine.newGame(seed = 1, firstPlayer = Player.A).copy(
            stones = stones(Stone(0, side("R7 R8 R9", 1), side("B1 G2 Y4", 2))),
            phase = Phase.CLAIM,
        )
        val action = MediumBot().choose(state.viewFor(Player.A), engine.legalActions(state))
        assertEquals(Action.Claim(0), action)
    }

    @Test
    fun `easy bot ignores claims that rely on an incomplete opposing side`() {
        val state = engine.newGame(seed = 1, firstPlayer = Player.A).copy(
            stones = stones(Stone(0, side("R7 R8 R9", 1), Side())),
            phase = Phase.CLAIM,
        )
        assertTrue(Action.Claim(0) in engine.legalActions(state))
        assertEquals(Action.EndTurn, EasyBot().choose(state.viewFor(Player.A), engine.legalActions(state)))
    }

    @Test
    fun `medium bot extends a promising formation instead of starting a new stone`() {
        val state = engine.newGame(seed = 1, firstPlayer = Player.A).copy(
            stones = stones(Stone(4, side("R5 R6"), Side())),
            handA = cards("R7 Y1"),
        )
        val action = MediumBot().choose(state.viewFor(Player.A), engine.legalActions(state))
        assertIs<Action.PlayCard>(action)
        assertEquals(c("R7"), action.card)
        assertEquals(4, action.stoneIndex)
    }

    @Test
    fun `medium bot beats easy bot overwhelmingly`() {
        val games = 200
        val result = playMatch(MediumBot(11), EasyBot(12), games, seed = 1000)
        val medium = result.winsA
        assertTrue(medium >= games * 0.90, "medium won only $medium of $games ($result)")
    }
}
