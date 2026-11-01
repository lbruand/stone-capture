package io.stonecapture.core.bot

import io.stonecapture.core.Action
import kotlin.random.Random

/** Plays a random legal card and only claims stones whose two sides are both complete. */
class EasyBot(seed: Long = 0) : Bot {
    private val random = Random(seed)

    override fun choose(view: BotView, legal: List<Action>): Action {
        val obviousClaims = legal.filterIsInstance<Action.Claim>().filter {
            val stone = view.stones[it.stoneIndex]
            stone.sideA.isFull && stone.sideB.isFull
        }
        if (obviousClaims.isNotEmpty()) return obviousClaims.first()

        val plays = legal.filterIsInstance<Action.PlayCard>()
        if (plays.isNotEmpty()) return plays.random(random)
        return legal.first { it is Action.Pass || it is Action.EndTurn }
    }
}
