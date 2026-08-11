package com.analogvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.analogvault.data.model.FilmStock
import com.analogvault.data.model.Roll
import com.analogvault.data.model.Shot
import com.analogvault.ui.film.ChromaticText
import com.analogvault.ui.film.SprocketRail
import com.analogvault.ui.film.rememberStockAccent
import com.analogvault.ui.theme.FilmTheme
import java.io.File

/**
 * The roll as a contact sheet.
 *
 * A real contact sheet is the whole roll printed at frame size on one piece of
 * paper: you look at it to decide what is worth enlarging, so the frames are
 * shown at a uniform size, in order, with their numbers, and nothing is
 * cropped to make a tidier grid. Frames with no photo still get a cell — a gap
 * in the sequence is information, and closing it up would misrepresent which
 * frame is which.
 */
@Composable
fun ContactSheetScreen(
    roll: Roll,
    film: FilmStock?,
    onBack: () -> Unit,
    onOpenShot: (Shot) -> Unit = {},
) {
    val colors = FilmTheme.colors
    val stock = rememberStockAccent(film?.name ?: "", film?.type ?: "", film?.stockAccent ?: "")
    val total = roll.totalShots.takeIf { it > 0 } ?: film?.frameCount ?: roll.shots.size

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.ArrowBack, "Back to rolls",
                    tint = colors.dim, modifier = Modifier.size(20.dp))
            }
            Column {
                ChromaticText(
                    (film?.name ?: "Contact sheet").uppercase(),
                    style = FilmTheme.type.stock,
                    maxLines = 1,
                )
                Text(
                    "${roll.shots.size} OF $total FRAMES",
                    style = FilmTheme.type.rebate,
                    color = colors.dim,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        SprocketRail(filmColor = colors.film)

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .weight(1f)
                .background(colors.film),
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items((0 until total).toList()) { index ->
                val shot = roll.shots.getOrNull(index)
                ContactFrame(
                    frameNumber = index + 1,
                    shot = shot,
                    accent = stock.solid,
                    onClick = { shot?.let(onOpenShot) },
                )
            }
        }

        SprocketRail(filmColor = colors.film)
    }
}

@Composable
private fun ContactFrame(
    frameNumber: Int,
    shot: Shot?,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val colors = FilmTheme.colors
    val photo = shot?.photoThumbPath?.takeIf { it.isNotBlank() && File(it).exists() }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f) // 36:24, same as the frame counter
                .background(colors.void)
                .border(1.dp, if (shot != null) colors.edge else colors.edge.copy(alpha = 0.4f))
                .then(if (shot != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when {
                photo != null -> AsyncImage(
                    model = photo,
                    contentDescription = "Frame $frameNumber",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                shot != null -> Text(
                    // Exposed but never photographed: show what it was shot at,
                    // which is the only record of that frame that exists.
                    listOfNotNull(
                        shot.shutter.takeIf { it.isNotBlank() },
                        shot.aperture.takeIf { it.isNotBlank() }?.let { "f/$it" },
                    ).joinToString("\n").ifBlank { "—" },
                    style = FilmTheme.type.rebate,
                    color = accent,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            "%02d".format(frameNumber),
            style = FilmTheme.type.rebate,
            color = if (shot != null) colors.dim else colors.dead,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
