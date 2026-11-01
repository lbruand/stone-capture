package io.stonecapture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.stonecapture.core.Card
import io.stonecapture.core.CardColor
import io.stonecapture.core.Phase
import io.stonecapture.core.Player
import io.stonecapture.core.SIDE_CAPACITY
import io.stonecapture.core.Side
import io.stonecapture.core.Stone

private val STONE_LABEL_HEIGHT = 36.dp
private val COLUMN_GAP = 4.dp

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val ui by viewModel.state.collectAsState()
    val game = ui.game
    val me = viewModel.human

    Row(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            // Six card slots plus the stone label stack vertically; size the cards to fit.
            val cardHeight = ((maxHeight - STONE_LABEL_HEIGHT - 24.dp) / SIDE_CAPACITY / 2).coerceIn(24.dp, 72.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
                for (stone in game.stones) {
                    StoneColumn(
                        stone = stone,
                        me = me,
                        cardHeight = cardHeight,
                        highlighted = viewModel.canClaim(stone.index) ||
                            (ui.selected != null && stone.isOpen && !stone.side(me).isFull && game.current == me),
                        onClick = { viewModel.tapStone(stone.index) },
                    )
                }
            }
        }

        Column(
            modifier = Modifier.width(232.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                statusText(game.phase, game.current == me, game.winner, me),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(stringResource(R.string.draw_pile, game.drawPile.size), style = MaterialTheme.typography.bodyMedium)

            for (row in game.hand(me).chunked(HAND_COLUMNS)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (card in row) {
                        CardView(card, width = 68.dp, height = 88.dp, selected = card == ui.selected, onClick = { viewModel.select(card) })
                    }
                }
            }

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

private const val HAND_COLUMNS = 3

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
private fun RowScope.StoneColumn(stone: Stone, me: Player, cardHeight: Dp, highlighted: Boolean, onClick: () -> Unit) {
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
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SideView(stone.side(me.other()), cardHeight, reversed = true)
        Box(
            Modifier.fillMaxWidth().height(STONE_LABEL_HEIGHT).background(ownerColor, RoundedCornerShape(4.dp)),
            Alignment.Center,
        ) {
            Text("${stone.index + 1}", color = Color.White, fontWeight = FontWeight.Bold)
        }
        SideView(stone.side(me), cardHeight, reversed = false)
    }
}

@Composable
private fun SideView(side: Side, cardHeight: Dp, reversed: Boolean) {
    val slots = List(SIDE_CAPACITY) { side.cards.getOrNull(it) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (card in if (reversed) slots.reversed() else slots) {
            if (card != null) {
                CardView(card, width = null, height = cardHeight)
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
                        .border(BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.6f)), RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

/** A card of fixed [width], or one that fills the available width when [width] is null. */
@Composable
private fun CardView(card: Card, width: Dp?, height: Dp, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .background(card.color.tint(), shape)
            .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Color.Black else Color.DarkGray), shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${card.color.symbol()}${card.rank}",
            color = Color.White,
            fontSize = (height.value * 0.4f).sp,
            fontWeight = FontWeight.Bold,
        )
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
