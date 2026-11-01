package io.stonecapture.core.bot

import io.stonecapture.core.Action
import io.stonecapture.core.Card
import io.stonecapture.core.GameState
import io.stonecapture.core.Phase
import io.stonecapture.core.Player
import io.stonecapture.core.Stone

/**
 * Everything a player may legitimately know: the board, their own hand and public counts.
 * Bots only ever receive this, so they cannot read the opponent's hand or the draw pile order.
 */
data class BotView(
    val me: Player,
    val stones: List<Stone>,
    val hand: List<Card>,
    val phase: Phase,
    val opponentHandSize: Int,
    val drawPileSize: Int,
)

fun GameState.viewFor(player: Player) = BotView(
    me = player,
    stones = stones,
    hand = hand(player),
    phase = phase,
    opponentHandSize = hand(player.other()).size,
    drawPileSize = drawPile.size,
)

interface Bot {
    /** Picks one of [legal] (never empty). The caller applies it and asks again until the turn ends. */
    fun choose(view: BotView, legal: List<Action>): Action
}
