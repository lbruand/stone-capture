package io.stonecapture.core.session

import io.stonecapture.core.Action
import io.stonecapture.core.Card
import io.stonecapture.core.GameEngine
import io.stonecapture.core.GameState
import io.stonecapture.core.Phase
import io.stonecapture.core.Player
import io.stonecapture.core.StoneCaptureEngine
import io.stonecapture.core.bot.Bot
import io.stonecapture.core.bot.Difficulty
import io.stonecapture.core.bot.viewFor

/**
 * One human playing against a bot: what the screen shows plus the taps that change it.
 *
 * Immutable. Every method returns the next session, or this one unchanged when the input does not
 * apply (wrong phase, not the human's turn, illegal move), so the UI can forward taps blindly.
 */
data class GameSession(
    val game: GameState,
    val difficulty: Difficulty,
    val selected: Card? = null,
    val human: Player = Player.A,
    private val engine: GameEngine = StoneCaptureEngine,
) {
    val isFinished: Boolean get() = game.phase == Phase.FINISHED

    val isHumanTurn: Boolean get() = game.current == human && !isFinished

    val isBotTurn: Boolean get() = game.current != human && !isFinished

    fun canClaim(stoneIndex: Int): Boolean =
        isHumanTurn && game.phase == Phase.CLAIM && engine.canClaim(game, stoneIndex, human)

    fun canPass(): Boolean = isHumanTurn && engine.legalActions(game) == listOf(Action.Pass)

    /** Stones where [selected] could be played right now. */
    fun isPlayTarget(stoneIndex: Int): Boolean =
        selected != null && isHumanTurn && game.phase == Phase.PLAY &&
            engine.legalActions(game).contains(Action.PlayCard(selected, stoneIndex))

    /** Picks a card from the hand; picking the same card again deselects it. */
    fun select(card: Card): GameSession {
        if (!isHumanTurn || game.phase != Phase.PLAY || card !in game.hand(human)) return this
        return copy(selected = card.takeUnless { it == selected })
    }

    /** Plays the selected card on the stone, or claims the stone during the claim phase. */
    fun tapStone(stoneIndex: Int): GameSession = when {
        isPlayTarget(stoneIndex) -> perform(Action.PlayCard(selected!!, stoneIndex))
        canClaim(stoneIndex) -> perform(Action.Claim(stoneIndex))
        else -> this
    }

    fun pass(): GameSession = if (isHumanTurn) perform(Action.Pass) else this

    fun endTurn(): GameSession = if (isHumanTurn) perform(Action.EndTurn) else this

    /** Claims every stone the human can prove, in order, until none is left or the game ends. */
    fun autoClaim(): GameSession {
        var next = this
        while (true) {
            val stone = next.game.stones.indices.firstOrNull { next.canClaim(it) } ?: return next
            next = next.perform(Action.Claim(stone))
        }
    }

    /** Lets the bot take one action, or returns this when it is not the bot's turn. */
    fun botStep(bot: Bot): GameSession {
        if (!isBotTurn) return this
        val action = bot.choose(game.viewFor(game.current), engine.legalActions(game))
        return copy(game = engine.apply(game, action).getOrThrow())
    }

    private fun perform(action: Action): GameSession {
        val next = engine.apply(game, action).getOrNull() ?: return this
        return copy(game = next, selected = null)
    }
}
