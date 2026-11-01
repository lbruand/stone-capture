package io.stonecapture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.stonecapture.core.Action
import io.stonecapture.core.Card
import io.stonecapture.core.GameState
import io.stonecapture.core.Phase
import io.stonecapture.core.Player
import io.stonecapture.core.SavedGame
import io.stonecapture.core.StoneCaptureEngine
import io.stonecapture.core.bot.Bot
import io.stonecapture.core.bot.Difficulty
import io.stonecapture.core.bot.viewFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

data class UiState(val game: GameState, val difficulty: Difficulty, val selected: Card? = null)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = StoneCaptureEngine
    private val store = GameStore(application)
    private var bot: Bot = Difficulty.MEDIUM.createBot(System.nanoTime())
    val human = Player.A

    private val _state = MutableStateFlow<UiState?>(null)

    /** Null until the saved game (if any) has been read. */
    val state: StateFlow<UiState?> = _state.asStateFlow()

    private var botJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = store.loadGame()
            if (saved != null) {
                start(saved.state, saved.difficulty)
            } else {
                start(engine.newGame(), store.loadDifficulty())
            }
        }
        // Autosave after every move; a finished game is not worth resuming.
        viewModelScope.launch {
            _state.filterNotNull().collect { ui ->
                if (ui.game.phase == Phase.FINISHED) store.clearGame() else store.saveGame(SavedGame(ui.difficulty, ui.game))
            }
        }
    }

    fun newGame(difficulty: Difficulty) {
        viewModelScope.launch { store.saveDifficulty(difficulty) }
        start(engine.newGame(), difficulty)
    }

    private fun start(game: GameState, difficulty: Difficulty) {
        botJob?.cancel()
        bot = difficulty.createBot(System.nanoTime())
        _state.value = UiState(game, difficulty)
        runBotIfNeeded()
    }

    fun select(card: Card) {
        val ui = _state.value ?: return
        if (!isHumanTurn(Phase.PLAY)) return
        _state.value = ui.copy(selected = card.takeUnless { it == ui.selected })
    }

    fun tapStone(index: Int) {
        val ui = _state.value ?: return
        when {
            isHumanTurn(Phase.PLAY) && ui.selected != null -> dispatch(Action.PlayCard(ui.selected, index))
            isHumanTurn(Phase.CLAIM) && engine.canClaim(ui.game, index, human) -> dispatch(Action.Claim(index))
        }
    }

    fun pass() = dispatch(Action.Pass)

    fun endTurn() = dispatch(Action.EndTurn)

    fun canClaim(index: Int): Boolean {
        val ui = _state.value ?: return false
        return isHumanTurn(Phase.CLAIM) && engine.canClaim(ui.game, index, human)
    }

    fun canPass(): Boolean {
        val ui = _state.value ?: return false
        return isHumanTurn(Phase.PLAY) && engine.legalActions(ui.game) == listOf(Action.Pass)
    }

    private fun isHumanTurn(phase: Phase): Boolean {
        val game = _state.value?.game ?: return false
        return game.current == human && game.phase == phase
    }

    private fun dispatch(action: Action) {
        val ui = _state.value ?: return
        if (ui.game.current != human) return
        val next = engine.apply(ui.game, action).getOrNull() ?: return
        _state.value = UiState(next, ui.difficulty)
        runBotIfNeeded()
    }

    private fun runBotIfNeeded() {
        botJob?.cancel()
        botJob = viewModelScope.launch {
            while (true) {
                val ui = _state.value ?: return@launch
                val game = ui.game
                if (game.phase == Phase.FINISHED || game.current == human) return@launch
                delay(BOT_DELAY_MS)
                val action = bot.choose(game.viewFor(game.current), engine.legalActions(game))
                _state.value = UiState(engine.apply(game, action).getOrThrow(), ui.difficulty)
            }
        }
    }

    private companion object {
        const val BOT_DELAY_MS = 500L
    }
}
