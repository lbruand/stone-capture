package io.stonecapture.core

import kotlin.random.Random

sealed interface Action {
    data class PlayCard(val card: Card, val stoneIndex: Int) : Action

    /** Only legal when the player has no card they can place. */
    data object Pass : Action

    data class Claim(val stoneIndex: Int) : Action

    /** Draws a card (if any are left) and hands the turn to the other player. */
    data object EndTurn : Action
}

class IllegalActionException(message: String) : Exception(message)

interface GameEngine {
    fun newGame(seed: Long? = null, firstPlayer: Player? = null): GameState
    fun legalActions(state: GameState): List<Action>
    fun apply(state: GameState, action: Action): Result<GameState>
    fun canClaim(state: GameState, stoneIndex: Int, player: Player): Boolean
}

object StoneCaptureEngine : GameEngine {

    override fun newGame(seed: Long?, firstPlayer: Player?): GameState {
        val random = if (seed != null) Random(seed) else Random.Default
        val deck = shuffledDeck(random)
        val handA = deck.subList(0, HAND_SIZE)
        val handB = deck.subList(HAND_SIZE, 2 * HAND_SIZE)
        return GameState(
            stones = List(STONE_COUNT) { Stone(it) },
            handA = handA,
            handB = handB,
            drawPile = deck.drop(2 * HAND_SIZE),
            current = firstPlayer ?: Player.entries.random(random),
        )
    }

    override fun canClaim(state: GameState, stoneIndex: Int, player: Player): Boolean =
        io.stonecapture.core.canClaim(state.stones, stoneIndex, player)

    override fun legalActions(state: GameState): List<Action> = when (state.phase) {
        Phase.FINISHED -> emptyList()
        Phase.PLAY -> {
            val plays = playableStones(state, state.current).flatMap { stone ->
                state.hand(state.current).map { Action.PlayCard(it, stone) }
            }
            plays.ifEmpty { listOf(Action.Pass) }
        }
        Phase.CLAIM -> {
            val claims = state.stones.indices
                .filter { canClaim(state, it, state.current) }
                .map { Action.Claim(it) }
            claims + Action.EndTurn
        }
    }

    override fun apply(state: GameState, action: Action): Result<GameState> = try {
        Result.success(
            when (action) {
                is Action.PlayCard -> playCard(state, action)
                Action.Pass -> pass(state)
                is Action.Claim -> claim(state, action)
                Action.EndTurn -> endTurn(state)
            },
        )
    } catch (e: IllegalActionException) {
        Result.failure(e)
    }

    private fun playableStones(state: GameState, player: Player): List<Int> =
        if (state.hand(player).isEmpty()) {
            emptyList()
        } else {
            state.stones.filter { it.isOpen && !it.side(player).isFull }.map { it.index }
        }

    private fun playCard(state: GameState, action: Action.PlayCard): GameState {
        requirePhase(state, Phase.PLAY)
        val player = state.current
        if (action.card !in state.hand(player)) illegal("${action.card} is not in the hand of $player")
        val stone = state.stones.getOrNull(action.stoneIndex) ?: illegal("no stone ${action.stoneIndex}")
        if (!stone.isOpen) illegal("stone ${stone.index} is already claimed")
        val side = stone.side(player)
        if (side.isFull) illegal("side of $player on stone ${stone.index} is full")

        val moveCount = state.moveCount + 1
        val cards = side.cards + action.card
        val newSide = Side(cards, completedAt = if (cards.size == SIDE_CAPACITY) moveCount else null)
        val stones = state.stones.toMutableList().also { it[stone.index] = stone.withSide(player, newSide) }
        return state
            .withHand(player, state.hand(player) - action.card)
            .copy(stones = stones, moveCount = moveCount, phase = Phase.CLAIM)
    }

    private fun pass(state: GameState): GameState {
        requirePhase(state, Phase.PLAY)
        if (playableStones(state, state.current).isNotEmpty()) illegal("cannot pass while a card can be played")
        return state.copy(phase = Phase.CLAIM)
    }

    private fun claim(state: GameState, action: Action.Claim): GameState {
        requirePhase(state, Phase.CLAIM)
        if (!canClaim(state, action.stoneIndex, state.current)) {
            illegal("stone ${action.stoneIndex} cannot be proven for ${state.current}")
        }
        val stones = state.stones.toMutableList()
        stones[action.stoneIndex] = stones[action.stoneIndex].copy(owner = state.current)
        val next = state.copy(stones = stones)
        return if (hasWon(stones, state.current)) next.copy(phase = Phase.FINISHED, winner = state.current) else next
    }

    private fun endTurn(state: GameState): GameState {
        requirePhase(state, Phase.CLAIM)
        val player = state.current
        val drawn = state.drawPile.firstOrNull()
        val drawing = if (drawn != null) {
            state.withHand(player, state.hand(player) + drawn).copy(drawPile = state.drawPile.drop(1))
        } else {
            state
        }
        val next = drawing.copy(current = player.other(), phase = Phase.PLAY)
        val stuck = Player.entries.none { playableStones(next, it).isNotEmpty() }
        return if (stuck) finishStalemate(next) else next
    }

    /** Nobody can place a card any more: settle finished stones, then the majority decides. */
    private fun finishStalemate(state: GameState): GameState {
        val settled = state.stones.map { stone ->
            val a = stone.sideA
            val b = stone.sideB
            if (stone.isOpen && a.isFull && b.isFull) {
                val aWins = beats(evaluate(a.cards), a.completedAt!!, evaluate(b.cards), b.completedAt!!)
                stone.copy(owner = if (aWins) Player.A else Player.B)
            } else {
                stone
            }
        }
        val winner = Player.entries.firstOrNull { hasWon(settled, it) }
            ?: run {
                val a = settled.count { it.owner == Player.A }
                val b = settled.count { it.owner == Player.B }
                when {
                    a > b -> Player.A
                    b > a -> Player.B
                    else -> null
                }
            }
        return state.copy(stones = settled, phase = Phase.FINISHED, winner = winner)
    }

    private fun requirePhase(state: GameState, expected: Phase) {
        if (state.phase != expected) illegal("expected phase $expected but was ${state.phase}")
    }

    private fun illegal(message: String): Nothing = throw IllegalActionException(message)
}
