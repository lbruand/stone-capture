package io.stonecapture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.stonecapture.core.Card
import io.stonecapture.core.Phase
import io.stonecapture.core.SavedGame
import io.stonecapture.core.StoneCaptureEngine
import io.stonecapture.core.bot.Bot
import io.stonecapture.core.bot.Difficulty
import io.stonecapture.core.session.GameSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val store = GameStore(application)
    private var bot: Bot = Difficulty.MEDIUM.createBot(System.nanoTime())

    private val _session = MutableStateFlow<GameSession?>(null)

    /** Null until the saved game (if any) has been read. */
    val session: StateFlow<GameSession?> = _session.asStateFlow()

    private val _autoClaim = MutableStateFlow(false)
    val autoClaim: StateFlow<Boolean> = _autoClaim.asStateFlow()

    private var botJob: Job? = null

    init {
        viewModelScope.launch {
            _autoClaim.value = store.loadAutoClaim()
            val saved = store.loadGame()
            if (saved != null) {
                start(GameSession(saved.state, saved.difficulty))
            } else {
                start(GameSession(StoneCaptureEngine.newGame(), store.loadDifficulty()))
            }
        }
        // Autosave after every move; a finished game is not worth resuming.
        viewModelScope.launch {
            _session.filterNotNull().collect { s ->
                if (s.isFinished) store.clearGame() else store.saveGame(SavedGame(s.difficulty, s.game))
            }
        }
    }

    fun newGame(difficulty: Difficulty) {
        viewModelScope.launch { store.saveDifficulty(difficulty) }
        start(GameSession(StoneCaptureEngine.newGame(), difficulty))
    }

    fun setAutoClaim(enabled: Boolean) {
        _autoClaim.value = enabled
        viewModelScope.launch { store.saveAutoClaim(enabled) }
        _session.value?.let { _session.value = withAutoClaim(it) }
    }

    fun select(card: Card) = update { it.select(card) }

    fun tapStone(index: Int) = update { it.tapStone(index) }

    fun pass() = update { it.pass() }

    fun endTurn() = update { it.endTurn() }

    private fun start(session: GameSession) {
        bot = session.difficulty.createBot(System.nanoTime())
        _session.value = withAutoClaim(session)
        runBotIfNeeded()
    }

    private fun update(change: (GameSession) -> GameSession) {
        val current = _session.value ?: return
        val next = withAutoClaim(change(current))
        if (next == current) return
        _session.value = next
        runBotIfNeeded()
    }

    private fun withAutoClaim(session: GameSession): GameSession =
        if (_autoClaim.value && session.game.phase == Phase.CLAIM) session.autoClaim() else session

    private fun runBotIfNeeded() {
        botJob?.cancel()
        botJob = viewModelScope.launch {
            while (true) {
                val current = _session.value ?: return@launch
                if (!current.isBotTurn) return@launch
                delay(BOT_DELAY_MS)
                _session.value = current.botStep(bot)
            }
        }
    }

    private companion object {
        const val BOT_DELAY_MS = 500L
    }
}
