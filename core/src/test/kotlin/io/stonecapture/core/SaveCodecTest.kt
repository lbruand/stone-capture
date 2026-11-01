package io.stonecapture.core

import io.stonecapture.core.bot.Difficulty
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaveCodecTest {
    private val engine = StoneCaptureEngine

    private fun midGame(seed: Long): GameState {
        val random = Random(seed)
        var game = engine.newGame(seed)
        repeat(20) {
            if (game.phase != Phase.FINISHED) game = engine.apply(game, engine.legalActions(game).random(random)).getOrThrow()
        }
        return game
    }

    @Test
    fun `round trips new and mid game states`() {
        for (seed in 0L until 30L) {
            for (game in listOf(engine.newGame(seed), midGame(seed))) {
                val save = SavedGame(Difficulty.MEDIUM, game)
                assertEquals(save, SaveCodec.decode(SaveCodec.encode(save)))
            }
        }
    }

    @Test
    fun `keeps the difficulty`() {
        val save = SavedGame(Difficulty.EASY, engine.newGame(1))
        assertEquals(Difficulty.EASY, SaveCodec.decode(SaveCodec.encode(save))?.difficulty)
    }

    @Test
    fun `garbage and empty text decode to null`() {
        assertNull(SaveCodec.decode(""))
        assertNull(SaveCodec.decode("not json"))
        assertNull(SaveCodec.decode("{}"))
    }

    @Test
    fun `a save with a duplicated card is rejected`() {
        val game = engine.newGame(2)
        val broken = game.copy(handA = game.handA.drop(1) + game.handB.first())
        val text = SaveCodec.encode(SavedGame(Difficulty.EASY, broken))
        assertNull(SaveCodec.decode(text))
    }

    @Test
    fun `a save with an invalid card rank is rejected`() {
        val text = SaveCodec.encode(SavedGame(Difficulty.EASY, engine.newGame(3)))
        assertNotNull(SaveCodec.decode(text))
        val tampered = Regex("\"rank\":\\d+").replaceFirst(text, "\"rank\":12")
        assertNull(SaveCodec.decode(tampered))
    }

    @Test
    fun `a save with a missing card is rejected`() {
        val game = engine.newGame(4)
        val broken = game.copy(drawPile = game.drawPile.drop(1))
        assertTrue(!broken.isConsistent())
        assertNull(SaveCodec.decode(SaveCodec.encode(SavedGame(Difficulty.MEDIUM, broken))))
    }
}
