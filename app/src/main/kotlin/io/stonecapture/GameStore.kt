package io.stonecapture

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.stonecapture.core.SaveCodec
import io.stonecapture.core.SavedGame
import io.stonecapture.core.bot.Difficulty
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stone_capture")

/** Persists the game in progress and the difficulty last chosen. */
class GameStore(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    suspend fun loadGame(): SavedGame? = dataStore.data.first()[GAME]?.let(SaveCodec::decode)

    suspend fun loadDifficulty(): Difficulty =
        dataStore.data.first()[DIFFICULTY]?.let { name -> Difficulty.entries.firstOrNull { it.name == name } }
            ?: Difficulty.MEDIUM

    suspend fun saveGame(game: SavedGame) {
        dataStore.edit { it[GAME] = SaveCodec.encode(game) }
    }

    suspend fun clearGame() {
        dataStore.edit { it.remove(GAME) }
    }

    suspend fun saveDifficulty(difficulty: Difficulty) {
        dataStore.edit { it[DIFFICULTY] = difficulty.name }
    }

    private companion object {
        val GAME = stringPreferencesKey("game")
        val DIFFICULTY = stringPreferencesKey("difficulty")
    }
}
