package com.analogvault.ui.film

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.analogvault.ui.theme.FilmTheme

/**
 * A hairline data chip: one border, no radius, mono caps.
 *
 * Chips carry facts, not actions — ISO, process, expiry, storage. They are
 * deliberately the quietest element on a card so the stock name and the film
 * strip itself stay the things you see first.
 *
 * @param filled invert the chip (solid accent, void text) for the one fact on a
 *   card that has to win, e.g. "IN CAMERA". At most one per card.
 */
@Composable
fun FilmChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = FilmTheme.colors.dim,
    filled: Boolean = false,
) {
    Box(
        modifier
            .then(if (filled) Modifier.background(color) else Modifier)
            .border(1.dp, color)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = FilmTheme.type.data,
            color = if (filled) FilmTheme.colors.void else color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Chips wrap rather than scroll or truncate — a stock with six facts is a real
 * stock, and hiding the sixth is how you end up not noticing the expiry.
 *
 * The content lambda deliberately has no receiver: FlowRowScope is experimental,
 * and exposing it would force every call site to repeat the opt-in for a scope
 * none of them use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilmChipRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        content()
    }
}
