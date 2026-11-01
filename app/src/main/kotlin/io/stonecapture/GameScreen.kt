package io.stonecapture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.stonecapture.core.Card
import io.stonecapture.core.CardColor
import io.stonecapture.core.Phase
import io.stonecapture.core.Player
import io.stonecapture.core.SIDE_CAPACITY
import io.stonecapture.core.Side
import io.stonecapture.core.Stone

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val ui by viewModel.state.collectAsState()
    val game = ui.game
    val me = viewModel.human

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(statusText(game.phase, game.current == me, game.winner, me), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.draw_pile, game.drawPile.size))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (stone in game.stones) {
                StoneColumn(
                    stone = stone,
                    me = me,
                    highlighted = viewModel.canClaim(stone.index) ||
                        (ui.selected != null && stone.isOpen && !stone.side(me).isFull && game.current == me),
                    onClick = { viewModel.tapStone(stone.index) },
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            for (card in game.hand(me)) {
                CardView(card, selected = card == ui.selected, onClick = { viewModel.select(card) })
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            if (viewModel.canPass()) Button(onClick = viewModel::pass) { Text(stringResource(R.string.pass)) }
            if (game.phase == Phase.CLAIM && game.current == me) {
                Button(onClick = viewModel::endTurn) { Text(stringResource(R.string.end_turn)) }
            }
            if (game.phase == Phase.FINISHED) {
                Button(onClick = viewModel::newGame) { Text(stringResource(R.string.new_game)) }
            }
        }
    }
}

@Composable
private fun statusText(phase: Phase, myTurn: Boolean, winner: Player?, me: Player): String = when {
    phase == Phase.FINISHED -> stringResource(
        when (winner) {
            me -> R.string.you_won
            null -> R.string.draw
            else -> R.string.you_lost
        },
    )
    !myTurn -> stringResource(R.string.opponent_turn)
    phase == Phase.PLAY -> stringResource(R.string.your_turn_play)
    else -> stringResource(R.string.your_turn_claim)
}

@Composable
private fun RowScope.StoneColumn(stone: Stone, me: Player, highlighted: Boolean, onClick: () -> Unit) {
    val ownerColor = when (stone.owner) {
        null -> Color.Gray
        me -> Color(0xFF2E7D32)
        else -> Color(0xFFC62828)
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .border(
                BorderStroke(if (highlighted) 3.dp else 1.dp, if (highlighted) MaterialTheme.colorScheme.primary else Color.LightGray),
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SideView(stone.side(me.other()), reversed = true)
        Box(Modifier.fillMaxWidth().background(ownerColor, RoundedCornerShape(4.dp)).padding(vertical = 6.dp), Alignment.Center) {
            Text("${stone.index + 1}", color = Color.White, fontWeight = FontWeight.Bold)
        }
        SideView(stone.side(me), reversed = false)
    }
}

@Composable
private fun SideView(side: Side, reversed: Boolean) {
    val slots = List(SIDE_CAPACITY) { side.cards.getOrNull(it) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (card in if (reversed) slots.reversed() else slots) {
            if (card != null) CardView(card, small = true) else Box(Modifier.size(width = 36.dp, height = 28.dp))
        }
    }
}

@Composable
private fun CardView(card: Card, selected: Boolean = false, small: Boolean = false, onClick: (() -> Unit)? = null) {
    val width = if (small) 36.dp else 48.dp
    val height = if (small) 28.dp else 68.dp
    Box(
        modifier = Modifier
            .size(width, height)
            .background(card.color.tint(), RoundedCornerShape(4.dp))
            .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Color.Black else Color.DarkGray), RoundedCornerShape(4.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text("${card.color.symbol()}${card.rank}", color = Color.White, fontSize = if (small) 12.sp else 18.sp, fontWeight = FontWeight.Bold)
    }
}

private fun CardColor.tint() = when (this) {
    CardColor.RED -> Color(0xFFC62828)
    CardColor.BLUE -> Color(0xFF1565C0)
    CardColor.GREEN -> Color(0xFF2E7D32)
    CardColor.YELLOW -> Color(0xFF9E7A00)
    CardColor.PURPLE -> Color(0xFF6A1B9A)
    CardColor.ORANGE -> Color(0xFFD84315)
}

/** A letter on each card so colors stay distinguishable without relying on hue alone. */
private fun CardColor.symbol() = name.first()
