package com.analogvault.ui.film

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.analogvault.ui.theme.FilmTheme

/**
 * A strip of 35mm perforations.
 *
 * The holes are punched, not painted: the rail draws its film base colour and
 * then clears rounded rectangles out of it with BlendMode.Clear on an offscreen
 * layer. That means whatever sits behind the rail — a gradient, a photo, the
 * navigation bar — shows through the holes correctly. Painting black
 * rectangles instead would only work over a black background, which is the
 * mistake worth avoiding here.
 *
 * Geometry keeps real 35mm's hole-to-pitch ratio (2.8mm of every 4.75mm, so a
 * hole is a little under 60% of the pitch) but not its absolute scale — at true
 * scale a card this wide would show four perforations. Tuned on device: at a
 * 9.5dp pitch you get forty-odd small squares that read as a dotted line, and
 * the eye only calls it film once the perforations are large enough to have a
 * recognisable shape.
 *
 * @param advance 0f..1f horizontal phase. Animate it to make film travel.
 */
@Composable
fun SprocketRail(
    modifier: Modifier = Modifier,
    filmColor: Color = FilmTheme.colors.film,
    railHeight: Dp = 14.dp,
    pitch: Dp = 12.dp,
    holeWidth: Dp = 7.dp,
    holeHeight: Dp = 8.dp,
    holeCorner: Dp = 1.5.dp,
    advance: Float = 0f,
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(railHeight)
            // Required: BlendMode.Clear only punches through within a layer.
            // Without this the clear applies to the whole window and you get
            // black holes (or nothing) depending on the device's GPU.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .clearAndSetSemantics { } // decorative — keep it out of TalkBack
    ) {
        drawRect(filmColor)

        val pitchPx = pitch.toPx()
        val hw = holeWidth.toPx()
        val hh = holeHeight.toPx()
        val radius = CornerRadius(holeCorner.toPx())
        val top = (size.height - hh) / 2f
        val phase = (advance % 1f) * pitchPx

        var x = -pitchPx + phase
        while (x < size.width + pitchPx) {
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(x + (pitchPx - hw) / 2f, top),
                size = Size(hw, hh),
                cornerRadius = radius,
                blendMode = BlendMode.Clear,
            )
            x += pitchPx
        }
    }
}

/**
 * The workhorse container: content between two sprocket rails.
 *
 * Use this for anything that should read as a length of film — the loaded-roll
 * hero, roll cards, the bottom sheet body. Consistency is what sells the
 * metaphor; if only some surfaces have rails it looks like a sticker.
 */
@Composable
fun FilmStripCard(
    modifier: Modifier = Modifier,
    filmColor: Color = FilmTheme.colors.film,
    borderColor: Color = FilmTheme.colors.edge,
    backgroundBrush: Brush? = null,
    advance: Float = 0f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, borderColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        SprocketRail(filmColor = filmColor, advance = advance)
        // The fill goes on the body only, never on the whole card. A background
        // spanning the rails sits directly behind their punched holes, so the
        // holes reveal the same colour as the rail and the perforations vanish —
        // the card renders as a plain rectangle and the metaphor is gone.
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (backgroundBrush != null) Modifier.background(backgroundBrush)
                    else Modifier.background(filmColor)
                ),
            content = content,
        )
        SprocketRail(filmColor = filmColor, advance = advance)
    }
}

@Preview(backgroundColor = 0xFF000000, showBackground = true)
@Composable
private fun SprocketRailPreview() {
    FilmTheme {
        Column(Modifier.background(FilmTheme.colors.void)) {
            SprocketRail()
            SprocketRail(advance = 0.5f, filmColor = FilmTheme.colors.filmRaised)
        }
    }
}
