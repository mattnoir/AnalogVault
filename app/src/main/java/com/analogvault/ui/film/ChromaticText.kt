package com.analogvault.ui.film

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.analogvault.ui.theme.FilmTheme

/**
 * A headline with the cyan/magenta fringe of a misregistered colour print.
 *
 * The three dye layers of a colour negative are exposed through the same lens
 * but developed as separate emulsion layers, and when a print is misregistered
 * you get exactly this: a cyan edge on one side, a magenta edge on the other,
 * the neutral image between them. Drawn as three stacked Texts rather than a
 * shadow because Compose allows a single shadow per style and this needs two,
 * in opposite directions.
 *
 * Reserve it for the one headline on a screen. Applied to body copy it stops
 * reading as a print artefact and starts reading as a rendering bug — and it
 * costs three text layouts, which is fine once and wasteful in a list.
 */
@Composable
fun ChromaticText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = FilmTheme.type.display,
    color: Color = FilmTheme.colors.halide,
    fringe: Dp = 1.5.dp,
    maxLines: Int = Int.MAX_VALUE,
) {
    val colors = FilmTheme.colors
    // Under safelight every hue collapses toward red, so a cyan/magenta fringe
    // becomes two smudges of the same colour — noise with no information in it.
    val showFringe = !colors.safelight

    Box(modifier) {
        if (showFringe) {
            Text(
                text, style = style, color = colors.cyan, maxLines = maxLines,
                modifier = Modifier
                    .offset(x = -fringe)
                    .layoutId("fringeCyan")
                    .clearAndSetSemantics { },
            )
            Text(
                text, style = style, color = colors.magenta, maxLines = maxLines,
                modifier = Modifier
                    .offset(x = fringe)
                    .layoutId("fringeMagenta")
                    .clearAndSetSemantics { },
            )
        }
        Text(text, style = style, color = color, maxLines = maxLines)
    }
}
