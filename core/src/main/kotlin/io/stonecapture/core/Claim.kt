package io.stonecapture.core

/** Cards that are not face up on any stone: the pool an incomplete side could still draw from. */
fun availableCards(stones: List<Stone>): List<Card> {
    val onTable = stones.flatMapTo(HashSet()) { it.sideA.cards + it.sideB.cards }
    return fullDeck().filter { it !in onTable }
}

/**
 * True when [player] can prove they win [stone] whatever the opponent does next.
 *
 * The claimant needs a full side. If the opponent's side is full too, the formations are
 * compared. Otherwise every card not face up on the table counts as available to the opponent,
 * including the claimant's own hand: a hand is not proof.
 */
fun canClaim(stones: List<Stone>, stoneIndex: Int, player: Player): Boolean {
    val stone = stones.getOrNull(stoneIndex) ?: return false
    if (!stone.isOpen) return false
    val mine = stone.side(player)
    val theirs = stone.side(player.other())
    if (!mine.isFull) return false

    val myValue = evaluate(mine.cards)
    if (theirs.isFull) return beats(myValue, mine.completedAt!!, evaluate(theirs.cards), theirs.completedAt!!)

    // The opponent would complete after the claimant, so an equal formation loses the tie.
    val missing = SIDE_CAPACITY - theirs.cards.size
    return !anyCompletionBeats(theirs.cards, availableCards(stones), missing, myValue)
}

/** Higher value wins; equal values go to whoever completed first. */
internal fun beats(a: FormationValue, aCompletedAt: Int, b: FormationValue, bCompletedAt: Int): Boolean =
    a > b || (a == b && aCompletedAt < bCompletedAt)

private fun anyCompletionBeats(
    base: List<Card>,
    pool: List<Card>,
    missing: Int,
    target: FormationValue,
): Boolean {
    fun search(start: Int, chosen: List<Card>): Boolean {
        if (chosen.size == missing) return evaluate(base + chosen) > target
        for (i in start until pool.size) {
            if (search(i + 1, chosen + pool[i])) return true
        }
        return false
    }
    return search(0, emptyList())
}

/** The set of stone indices that [player] currently owns and that form a winning shape. */
internal fun hasWon(stones: List<Stone>, player: Player): Boolean {
    val owned = stones.map { it.owner == player }
    if (owned.count { it } >= STONES_TO_WIN) return true
    return owned.windowed(ADJACENT_TO_WIN).any { window -> window.all { it } }
}
