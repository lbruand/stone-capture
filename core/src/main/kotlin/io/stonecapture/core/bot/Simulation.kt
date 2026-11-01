package io.stonecapture.core.bot

import io.stonecapture.core.GameEngine
import io.stonecapture.core.Phase
import io.stonecapture.core.Player
import io.stonecapture.core.StoneCaptureEngine

data class MatchResult(val winsA: Int, val winsB: Int, val draws: Int) {
    val games: Int get() = winsA + winsB + draws
}

private const val MAX_STEPS = 5_000

/** Plays one full game and returns the winner (null for a draw). Throws if a bot picks an illegal action. */
fun playGame(
    botA: Bot,
    botB: Bot,
    seed: Long,
    firstPlayer: Player? = null,
    engine: GameEngine = StoneCaptureEngine,
): Player? {
    var state = engine.newGame(seed, firstPlayer)
    var steps = 0
    while (state.phase != Phase.FINISHED) {
        check(steps++ < MAX_STEPS) { "game with seed $seed did not finish" }
        val legal = engine.legalActions(state)
        val bot = if (state.current == Player.A) botA else botB
        val action = bot.choose(state.viewFor(state.current), legal)
        check(action in legal) { "${bot::class.simpleName} chose illegal action $action" }
        state = engine.apply(state, action).getOrThrow()
    }
    return state.winner
}

/** Plays [games] games, alternating who moves first so neither side gets a first-move edge. */
fun playMatch(botA: Bot, botB: Bot, games: Int, seed: Long = 0): MatchResult {
    var a = 0
    var b = 0
    var draws = 0
    repeat(games) { i ->
        val first = if (i % 2 == 0) Player.A else Player.B
        when (playGame(botA, botB, seed + i, first)) {
            Player.A -> a++
            Player.B -> b++
            null -> draws++
        }
    }
    return MatchResult(a, b, draws)
}
