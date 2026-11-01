package io.stonecapture.core

const val STONE_COUNT = 9
const val SIDE_CAPACITY = 3
const val HAND_SIZE = 6
const val STONES_TO_WIN = 5
const val ADJACENT_TO_WIN = 3

enum class CardColor { RED, BLUE, GREEN, YELLOW, PURPLE, ORANGE }

data class Card(val color: CardColor, val rank: Int) {
    init {
        require(rank in MIN_RANK..MAX_RANK) { "rank must be in $MIN_RANK..$MAX_RANK, was $rank" }
    }

    override fun toString() = "${color.name.first()}$rank"

    companion object {
        const val MIN_RANK = 1
        const val MAX_RANK = 9
    }
}

enum class Player {
    A,
    B;

    fun other(): Player = if (this == A) B else A
}

/** Ordered from weakest to strongest: comparison relies on the declaration order. */
enum class Formation { MIXED, STRAIGHT, FLUSH, TRIPLE, STRAIGHT_FLUSH }

data class FormationValue(val formation: Formation, val sum: Int) : Comparable<FormationValue> {
    override fun compareTo(other: FormationValue): Int =
        compareValuesBy(this, other, { it.formation }, { it.sum })
}

/**
 * One player's cards in front of one stone.
 *
 * [completedAt] is the state's move counter at the moment the third card was placed. It orders
 * completions across stones and players, which is what resolves equal formations.
 */
data class Side(val cards: List<Card> = emptyList(), val completedAt: Int? = null) {
    init {
        require(cards.size <= SIDE_CAPACITY) { "a side holds at most $SIDE_CAPACITY cards" }
        require((completedAt != null) == isFull) { "completedAt must be set exactly when the side is full" }
    }

    val isFull: Boolean get() = cards.size == SIDE_CAPACITY
}

data class Stone(
    val index: Int,
    val sideA: Side = Side(),
    val sideB: Side = Side(),
    val owner: Player? = null,
) {
    fun side(player: Player): Side = if (player == Player.A) sideA else sideB

    fun withSide(player: Player, side: Side): Stone =
        if (player == Player.A) copy(sideA = side) else copy(sideB = side)

    val isOpen: Boolean get() = owner == null
}

enum class Phase { PLAY, CLAIM, FINISHED }

data class GameState(
    val stones: List<Stone>,
    val handA: List<Card>,
    val handB: List<Card>,
    /** Top of the pile is the first element. */
    val drawPile: List<Card>,
    val current: Player,
    /** Counts card placements; used to order side completions. */
    val moveCount: Int = 0,
    val phase: Phase = Phase.PLAY,
    /** Meaningful only when [phase] is [Phase.FINISHED]; null then means a draw. */
    val winner: Player? = null,
) {
    fun hand(player: Player): List<Card> = if (player == Player.A) handA else handB

    fun withHand(player: Player, hand: List<Card>): GameState =
        if (player == Player.A) copy(handA = hand) else copy(handB = hand)

    fun stonesOwnedBy(player: Player): List<Int> = stones.filter { it.owner == player }.map { it.index }
}
