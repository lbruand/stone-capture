package io.stonecapture.core

/** Scores a complete three-card side. */
fun evaluate(cards: List<Card>): FormationValue {
    require(cards.size == SIDE_CAPACITY) { "a formation needs exactly $SIDE_CAPACITY cards" }
    val sameColor = cards.all { it.color == cards[0].color }
    val ranks = cards.map { it.rank }.sorted()
    val sameRank = ranks.first() == ranks.last()
    val consecutive = ranks.zipWithNext().all { (a, b) -> b == a + 1 }
    val formation = when {
        sameColor && consecutive -> Formation.STRAIGHT_FLUSH
        sameRank -> Formation.TRIPLE
        sameColor -> Formation.FLUSH
        consecutive -> Formation.STRAIGHT
        else -> Formation.MIXED
    }
    return FormationValue(formation, ranks.sum())
}
