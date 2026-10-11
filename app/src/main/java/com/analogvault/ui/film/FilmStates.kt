package com.analogvault.ui.film

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.analogvault.ui.theme.FilmTheme

/**
 * Loading, as film advancing.
 *
 * A spinner says "the machine is busy". A sprocket rail travelling says "film is
 * moving", which is the same message in the language the rest of the app speaks,
 * and it costs one animated float.
 *
 * Falls back to a still rail when animations are off system-wide or safelight is
 * on — under a safelight, movement in the corner of your eye is the thing that
 * makes you look away from the tank.
 */
@Composable
fun FilmAdvance(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = FilmTheme.colors
    val still = rememberReduceMotion() || colors.safelight
    val advance = if (still) 0f else {
        val transition = rememberInfiniteTransition(label = "advance")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "advancePhase",
        )
        v
    }
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The rails are decorative; the label below carries the meaning, so
        // TalkBack should read one thing here rather than three.
        Column(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            SprocketRail(filmColor = colors.film, advance = advance)
            Box(Modifier.fillMaxWidth().height(18.dp).background(colors.film))
            SprocketRail(filmColor = colors.film, advance = advance)
        }
        if (label != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                label.uppercase(),
                style = FilmTheme.type.rebate,
                color = colors.dim,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

/**
 * Nothing here yet, drawn as unexposed frames.
 *
 * Empty states get a verb. "No rolls in camera" is a fact and leaves the user
 * looking for the button; "Load one" is the next thing to do, and the row of
 * blank frames says what would appear once they do it — which a line of grey
 * text never manages.
 *
 * @param verb the action, e.g. "Load one". Rendered as a button when [onVerb] is
 *   given and as plain instruction when it is not, because an empty state that
 *   looks tappable and is not is worse than one that never claimed to be.
 */
@Composable
fun UnexposedFrames(
    text: String,
    modifier: Modifier = Modifier,
    verb: String? = null,
    onVerb: (() -> Unit)? = null,
    frames: Int = 4,
) {
    val colors = FilmTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(frames) {
                Box(
                    Modifier
                        .width(40.dp)
                        .height(28.dp)
                        .border(1.dp, colors.dead)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text,
            style = FilmTheme.type.data,
            color = colors.dim,
            textAlign = TextAlign.Center,
        )
        if (verb != null) {
            Spacer(Modifier.height(12.dp))
            if (onVerb != null) {
                Box(
                    Modifier
                        .border(1.dp, colors.cyan)
                        .clickable(onClick = onVerb)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(verb.uppercase(), style = FilmTheme.type.data, color = colors.cyan)
                }
            } else {
                Text(verb, style = FilmTheme.type.data, color = colors.halide)
            }
        }
    }
}

@Preview(backgroundColor = 0xFF000000, showBackground = true, widthDp = 380)
@Composable
private fun FilmStatesPreview() {
    FilmTheme {
        Column {
            UnexposedFrames("No rolls in camera.", verb = "Load one", onVerb = {})
            FilmAdvance(label = "Fetching the light")
        }
    }
}
