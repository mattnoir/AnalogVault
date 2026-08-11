package com.analogvault.ui.film

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.analogvault.ui.theme.FilmTheme

/**
 * The frame counter: one cell per frame on the roll, exposed frames lit.
 *
 * This is the app's primary logging surface, not a progress bar. Tapping the
 * next unexposed frame advances the roll; tapping an already-exposed frame
 * opens it. Both need to work one-handed while the user is holding a camera,
 * so cells are laid out on a 3:2 grid matching the 36x24mm frame aspect and
 * the row count adapts rather than the cells shrinking below a thumb.
 *
 * @param total frames on the roll (24, 36, or whatever a bulk cut gave them)
 * @param exposed how many are shot; index [exposed] is the next frame
 * @param onFrameClick receives a ZERO-BASED index
 */
@Composable
fun FrameCounter(
    total: Int,
    exposed: Int,
    modifier: Modifier = Modifier,
    accent: Color = FilmTheme.colors.cyan,
    perRow: Int = 12,
    enabled: Boolean = true,
    onFrameClick: ((Int) -> Unit)? = null,
) {
    val colors = FilmTheme.colors
    val haptics = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()

    // The "next frame" marker pulses so the eye lands on it immediately.
    // Gated on the system animation setting because it never terminates.
    val pulse by if (reduceMotion || colors.safelight) {
        remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "nextFrame").animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "nextFrameAlpha",
        )
    }

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        (0 until total).chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { index ->
                    FrameCell(
                        index = index,
                        state = when {
                            index < exposed -> FrameState.Exposed
                            index == exposed -> FrameState.Next
                            else -> FrameState.Unexposed
                        },
                        accent = accent,
                        pulseAlpha = pulse,
                        enabled = enabled,
                        onClick = onFrameClick?.let {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                it(index)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad the final row so cells stay the same width as rows above.
                repeat(perRow - row.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

private enum class FrameState { Exposed, Next, Unexposed }

@Composable
private fun FrameCell(
    index: Int,
    state: FrameState,
    accent: Color,
    pulseAlpha: Float,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = FilmTheme.colors
    val frameNumber = index + 1

    val fill by animateColorAsState(
        targetValue = if (state == FrameState.Exposed) accent else colors.void,
        animationSpec = tween(220),
        label = "frameFill",
    )
    val stroke = when (state) {
        FrameState.Exposed -> accent
        FrameState.Next -> colors.magenta
        FrameState.Unexposed -> colors.edge
    }

    Box(
        modifier
            .aspectRatio(1.5f) // 36:24 — the real frame
            .then(
                if (state == FrameState.Exposed) {
                    Modifier.halation(accent, radius = 6.dp, intensity = 0.5f, enabled = !colors.safelight)
                } else Modifier
            )
            .alpha(if (state == FrameState.Next) pulseAlpha else 1f)
            .background(fill)
            .border(1.dp, stroke)
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .semantics {
                contentDescription = "Frame $frameNumber"
                stateDescription = when (state) {
                    FrameState.Exposed -> "Exposed"
                    FrameState.Next -> "Next frame"
                    FrameState.Unexposed -> "Not yet shot"
                }
            }
    )
}

@Preview(backgroundColor = 0xFF000000, showBackground = true)
@Composable
private fun FrameCounterPreview() {
    FilmTheme {
        Column(
            Modifier
                .background(FilmTheme.colors.film)
                .padding(14.dp)
        ) {
            FrameCounter(total = 24, exposed = 7, onFrameClick = {})
        }
    }
}
