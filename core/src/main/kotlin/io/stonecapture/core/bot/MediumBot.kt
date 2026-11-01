package io.stonecapture.core.bot

import io.stonecapture.core.Action
import io.stonecapture.core.Card
import io.stonecapture.core.SIDE_CAPACITY
import io.stonecapture.core.availableCards
import io.stonecapture.core.evaluate
import kotlin.random.Random

/**
 * Claims whatever it can prove, then plays the card that most raises its chance of winning a
 * stone, weighted towards stones next to ones it already owns.
 *
 * The chance is estimated by dealing random completions from the cards it cannot see (not on
 * the table and not in its own hand) to both sides of a stone.
 */
class MediumBot(seed: Long = 0, private val samples: Int = 60) : Bot {
    private val random = Random(seed)

    override fun choose(view: BotView, legal: List<Action>): Action {
        legal.filterIsInstance<Action.Claim>().firstOrNull()?.let { return it }

        val plays = legal.filterIsInstance<Action.PlayCard>()
        if (plays.isEmpty()) return legal.first { it is Action.Pass || it is Action.EndTurn }

        val unseen = availableCards(view.stones) - view.hand.toSet()
        val deals = List(samples) { unseen.shuffled(random) }
        return plays.maxBy { score(view, it, deals) }
    }

    private fun score(view: BotView, play: Action.PlayCard, deals: List<List<Card>>): Double {
        val stone = view.stones[play.stoneIndex]
        val mine = stone.side(view.me).cards
        val theirs = stone.side(view.me.other()).cards
        val before = winChance(mine, theirs, deals)
        val after = winChance(mine + play.card, theirs, deals)
        return stoneWeight(view, play.stoneIndex) * (after - before) + TIE_BREAK * after
    }

    /** Average result over [deals]: 1 for a win, 0 for a loss, with ties settled by who completes last. */
    private fun winChance(mine: List<Card>, theirs: List<Card>, deals: List<List<Card>>): Double {
        val myMissing = SIDE_CAPACITY - mine.size
        val theirMissing = SIDE_CAPACITY - theirs.size
        val tie = when {
            theirs.size == SIDE_CAPACITY -> 0.0 // they finished first
            mine.size == SIDE_CAPACITY -> 1.0 // I finished first
            else -> 0.5
        }
        var total = 0.0
        var counted = 0
        for (deal in deals) {
            if (deal.size < myMissing + theirMissing) continue
            val myValue = evaluate(mine + deal.subList(0, myMissing))
            val theirValue = evaluate(theirs + deal.subList(myMissing, myMissing + theirMissing))
            total += when {
                myValue > theirValue -> 1.0
                myValue < theirValue -> 0.0
                else -> tie
            }
            counted++
        }
        return if (counted == 0) 0.5 else total / counted
    }

    private fun stoneWeight(view: BotView, index: Int): Double {
        val ownedNeighbours = listOf(index - 1, index + 1).count { view.stones.getOrNull(it)?.owner == view.me }
        return 1.0 + ADJACENT_BONUS * ownedNeighbours
    }

    private companion object {
        const val ADJACENT_BONUS = 0.25
        const val TIE_BREAK = 0.01
    }
}
