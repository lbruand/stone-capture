package io.stonecapture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.stonecapture.core.Action
import io.stonecapture.core.Card
import io.stonecapture.core.GameState
import io.stonecapture.core.Phase
import io.stonecapture.core.Player
import io.stonecapture.core.StoneCaptureEngine
import io.stonecapture.core.bot.MediumBot
import io.stonecapture.core.bot.viewFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(val game: GameState, val selected: Card? = null)

class GameViewModel : ViewModel() {
    private val engine = StoneCaptureEngine
    private val bot = MediumBot(seed = System.nanoTime())
    val human = Player.A

    private val _state = MutableStateFlow(UiState(engine.newGame()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var botJob: Job? = null

    init {
        runBotIfNeeded()
    }

    fun newGame() {
        botJob?.cancel()
        _state.value = UiState(engine.newGame())
        runBotIfNeeded()
    }

    fun select(card: Card) {
        if (!isHumanTurn(Phase.PLAY)) return
        _state.value = _state.value.copy(selected = card.takeUnless { it == _state.value.selected })
    }

    fun tapStone(index: Int) {
        val ui = _state.value
        when {
            isHumanTurn(Phase.PLAY) && ui.selected != null -> dispatch(Action.PlayCard(ui.selected, index))
            isHumanTurn(Phase.CLAIM) && engine.canClaim(ui.game, index, human) -> dispatch(Action.Claim(index))
        }
    }

    fun pass() = dispatch(Action.Pass)

    fun endTurn() = dispatch(Action.EndTurn)

    fun canClaim(index: Int): Boolean =
        isHumanTurn(Phase.CLAIM) && engine.canClaim(_state.value.game, index, human)

    fun canPass(): Boolean = isHumanTurn(Phase.PLAY) && engine.legalActions(_state.value.game) == listOf(Action.Pass)

    private fun isHumanTurn(phase: Phase): Boolean {
        val game = _state.value.game
        return game.current == human && game.phase == phase
    }

    private fun dispatch(action: Action) {
        val ui = _state.value
        if (ui.game.current != human) return
        val next = engine.apply(ui.game, action).getOrNull() ?: return
        _state.value = UiState(next)
        runBotIfNeeded()
    }

    private fun runBotIfNeeded() {
        botJob?.cancel()
        botJob = viewModelScope.launch {
            while (true) {
                val game = _state.value.game
                if (game.phase == Phase.FINISHED || game.current == human) return@launch
                delay(BOT_DELAY_MS)
                val action = bot.choose(game.viewFor(game.current), engine.legalActions(game))
                _state.value = UiState(engine.apply(game, action).getOrThrow())
            }
        }
    }

    private companion object {
        const val BOT_DELAY_MS = 500L
    }
}
