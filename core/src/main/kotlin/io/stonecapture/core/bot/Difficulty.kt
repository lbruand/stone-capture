package io.stonecapture.core.bot

import kotlinx.serialization.Serializable

@Serializable
enum class Difficulty {
    EASY,
    MEDIUM;

    fun createBot(seed: Long): Bot = when (this) {
        EASY -> EasyBot(seed)
        MEDIUM -> MediumBot(seed)
    }
}
