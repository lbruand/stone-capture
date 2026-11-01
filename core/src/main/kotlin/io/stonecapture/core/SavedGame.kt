package io.stonecapture.core

import io.stonecapture.core.bot.Difficulty
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SavedGame(val difficulty: Difficulty, val state: GameState)

/** True when the state is a plausible game: 9 stones in order and each of the 54 cards exactly once. */
fun GameState.isConsistent(): Boolean {
    if (stones.size != STONE_COUNT || stones.indices.any { stones[it].index != it }) return false
    val all = stones.flatMap { it.sideA.cards + it.sideB.cards } + handA + handB + drawPile
    return all.size == fullDeck().size && all.toSet() == fullDeck().toSet() && moveCount >= 0
}

object SaveCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(save: SavedGame): String = json.encodeToString(SavedGame.serializer(), save)

    /** Returns null for text that is not valid JSON, fails validation, or describes an impossible game. */
    fun decode(text: String): SavedGame? =
        runCatching { json.decodeFromString(SavedGame.serializer(), text) }
            .getOrNull()
            ?.takeIf { it.state.isConsistent() }
}
