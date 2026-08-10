package com.analogvault.ui.film

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.analogvault.ui.theme.FilmTheme

/**
 * A stock in the stash, rendered as a length of film.
 *
 * This is the first surface converted to the Dye Layer language, and it is the
 * one that decides whether the language works: a stash is a long list of similar
 * items, so if the sprocket rails read as noise here they will read as noise
 * everywhere. Everything on the card is deliberately flat — the hierarchy comes
 * from type weight and one accent colour, not from elevation, radius or fill.
 *
 * The three text tiers map to the three type families:
 *   stock name   Big Shoulders, the type printed on the box
 *   subtitle     Space Mono eyebrow, the manufacturer's own shorthand
 *   rebate       Space Mono at 9sp, the edge printing along real film
 *
 * @param accent drives the stock name and is the card's only saturated colour.
 * @param dead render the card as unexposed/out-of-stock: no accent, dim rails.
 * @param rebate edge printing. Fixed-length facts only — format, frame count,
 *   storage. Anything free-form belongs in [notes]; the rebate is one line and
 *   a variable-length segment just truncates whatever follows it.
 * @param notes free-form user text, one line, above the rebate. Blank hides it.
 */
@Composable
fun FilmStockCard(
    stockName: String,
    subtitle: String,
    rebate: String,
    modifier: Modifier = Modifier,
    notes: String = "",
    accent: Color = FilmTheme.colors.halide,
    dead: Boolean = false,
    onClick: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    chips: @Composable () -> Unit = {},
) {
    val colors = FilmTheme.colors
    val nameColor = if (dead) colors.dead else accent

    FilmStripCard(
        modifier = modifier,
        filmColor = if (dead) colors.void else colors.film,
        borderColor = if (dead) colors.edge.copy(alpha = 0.6f) else colors.edge,
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stockName.uppercase(),
                    style = FilmTheme.type.stock,
                    color = nameColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = subtitle.uppercase(),
                        style = FilmTheme.type.eyebrow,
                        color = if (dead) colors.dead else colors.dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(9.dp))
                FilmChipRow(content = chips)
            }
            // Actions stay square and unfilled — a round button would be the only
            // circle on screen other than the shutter, which is exactly the
            // distinction the shutter needs to keep.
            if (onEdit != null || onDelete != null) {
                Spacer(Modifier.width(4.dp))
                Row {
                    if (onEdit != null) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.Edit, "Edit $stockName",
                                modifier = Modifier.size(16.dp), tint = colors.dim,
                            )
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                            Icon(
                                // Muted, not full mask orange: at full strength the
                                // bin is the brightest thing on the card, which is
                                // the wrong thing for the eye to land on in a list.
                                Icons.Default.Delete, "Delete $stockName",
                                modifier = Modifier.size(16.dp),
                                tint = colors.mask.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }
        }
        if (notes.isNotBlank()) {
            Text(
                text = notes,
                style = FilmTheme.type.data,
                color = if (dead) colors.dead else colors.dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
            )
        }
        if (rebate.isNotBlank()) {
            Text(
                text = rebate.uppercase(),
                style = FilmTheme.type.rebate,
                color = if (dead) colors.dead else colors.dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
            )
        }
    }
}

/** Join rebate segments the way edge printing runs along real film. */
fun rebateLine(vararg parts: String?): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString("  ▸  ", prefix = "▸  ")

@Preview(backgroundColor = 0xFF000000, showBackground = true, widthDp = 380)
@Composable
private fun FilmStockCardPreview() {
    FilmTheme {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilmStockCard(
                stockName = "Portra 400",
                subtitle = "Kodak · Colour Negative (C-41)",
                rebate = rebateLine("135 (35MM)", "36 EXP", "FRIDGE"),
                notes = "Push to 800 for the overcast walk",
                accent = FilmTheme.colors.cyan,
                onClick = {}, onEdit = {}, onDelete = {},
            ) {
                FilmChip("ISO 400")
                FilmChip("×3")
                FilmChip("In camera", color = FilmTheme.colors.cyan, filled = true)
            }
            FilmStockCard(
                stockName = "Velvia 50",
                subtitle = "Fujifilm · Slide (E-6)",
                rebate = rebateLine("135 (35MM)", "36 EXP", "FREEZER"),
                accent = FilmTheme.colors.mask,
                onClick = {}, onEdit = {}, onDelete = {},
            ) {
                FilmChip("ISO 50")
                FilmChip("Expired", color = FilmTheme.colors.mask)
            }
            FilmStockCard(
                stockName = "Tri-X 400",
                subtitle = "Kodak · Black & White",
                rebate = rebateLine("135 (35MM)", "OUT OF STOCK"),
                dead = true,
                onEdit = {}, onDelete = {},
            ) {
                FilmChip("ISO 400", color = FilmTheme.colors.dead)
                FilmChip("×0", color = FilmTheme.colors.dead)
            }
        }
    }
}
