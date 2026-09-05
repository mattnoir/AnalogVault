package com.analogvault.ui.film

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.analogvault.ui.theme.onAccent
import com.analogvault.ui.theme.FilmTheme

/** One destination in [FilmNavBar]. */
data class FilmNavItem(
    val label: String,
    val icon: FilmIconSpec,
    /** Rendered as a small count above the icon. Null or 0 hides it. */
    val badge: Int? = null,
)

/**
 * The bottom bar: four destinations either side of a shutter release.
 *
 * Two things carry the design here and both are easy to lose in a refactor.
 *
 * The rail across the top is a real [SprocketRail] in the film colour, so the
 * bar terminates in a length of film rather than a plain edge. It is drawn in
 * `film`, not `void`: the bar is a Scaffold bottom bar, so content is laid out
 * above it and never passes behind the perforations — punching void holes out
 * of a void bar over a void background is three coats of black and an
 * invisible rail.
 *
 * The shutter is the only circle in the entire design. Everything else —
 * cards, chips, sheets, fields — is a rectangle. That contrast is the point:
 * it is the one control you press without looking, so it must be the one shape
 * your thumb can find by feel. Do not add a second circle anywhere.
 */
@Composable
fun FilmNavBar(
    items: List<FilmNavItem>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    onShutter: () -> Unit,
    modifier: Modifier = Modifier,
    /** Long-press the shutter. Wired to safelight — see [ShutterButton]. */
    onShutterLongPress: (() -> Unit)? = null,
) {
    require(items.size == 4) { "FilmNavBar splits two items either side of the shutter" }
    val colors = FilmTheme.colors

    Column(modifier.fillMaxWidth()) {
        SprocketRail(filmColor = colors.film)
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.void)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            NavButton(items[0], selectedIndex == 0) { onSelect(0) }
            NavButton(items[1], selectedIndex == 1) { onSelect(1) }
            ShutterButton(
                onClick = onShutter,
                onLongClick = onShutterLongPress,
            )
            NavButton(items[2], selectedIndex == 2) { onSelect(2) }
            NavButton(items[3], selectedIndex == 3) { onSelect(3) }
        }
    }
}

@Composable
private fun RowScope.NavButton(
    item: FilmNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = FilmTheme.colors
    val tint = if (selected) colors.cyan else colors.dim

    Column(
        Modifier
            .weight(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            DyeIcon(
                item.icon,
                contentDescription = null,
                size = 21.dp,
                tint = tint,
                // The dye detail lights up only on the selected destination.
                // Four accented icons in a row is a row of decorations; one is
                // a state.
                accent = if (selected) item.icon.dyeAccent() else tint,
                modifier = Modifier
                    // Selection is a glow AND a colour change AND the label
                    // brightening. Glow alone would be the only cue for anyone
                    // who cannot separate cyan from grey.
                    .then(
                        if (selected) Modifier.halation(
                            colors.cyan, radius = 10.dp,
                            intensity = 0.55f, enabled = !colors.safelight,
                        ) else Modifier
                    ),
            )
            if ((item.badge ?: 0) > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(start = 16.dp, bottom = 12.dp)
                        .background(colors.magenta)
                        .padding(horizontal = 3.dp),
                ) {
                    Text(
                        item.badge.toString(),
                        style = FilmTheme.type.rebate,
                        color = colors.onAccent(colors.magenta, preferred = colors.void),
                    )
                }
            }
        }
        Text(
            item.label.uppercase(),
            style = FilmTheme.type.rebate,
            color = tint,
            textAlign = TextAlign.Center,
        )
        // Selection under safelight is carried by a mark, not by colour.
        //
        // The glow above is disabled in that scheme, and once every hue in the
        // app is a red, "cyan vs dim" collapses to two brightnesses of the same
        // red — legible in daylight, not at the point of the screen you are
        // glancing at with one hand in a tank. The bar restores the second cue
        // the glow was providing.
        if (selected && colors.safelight) {
            Box(
                Modifier
                    .size(width = 18.dp, height = 2.dp)
                    .background(colors.cyan)
            )
        }
    }
}

/**
 * The shutter release. Meter and log in one action.
 *
 * Sized past the 48dp minimum on purpose — it is the largest target in the app
 * because it is the one pressed most often, one-handed, while the other hand is
 * holding a camera.
 *
 * Long-press toggles safelight. That gesture lives here rather than only in
 * Settings because the moment you need it is the moment you have just turned the
 * lights off, and the shutter is the one control this app expects you to be able
 * to find without looking. It fires a haptic so the change is confirmed by feel
 * as well as by the screen going red.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ShutterButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = FilmTheme.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(100),
        label = "shutterScale",
    )

    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .size(60.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .halation(colors.magenta, radius = 18.dp, intensity = 0.55f, enabled = !colors.safelight)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.magenta.copy(alpha = 0.95f).lighten(),
                        colors.magenta,
                    ),
                    // Off-centre highlight: a centred gradient reads as a flat
                    // disc, an offset one reads as a lit physical button.
                    center = Offset(0.34f * 60f, 0.30f * 60f),
                    radius = 60f,
                )
            )
            .border(2.dp, colors.magenta, CircleShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                },
            )
            .semantics {
                contentDescription = if (onLongClick != null)
                    "Meter and log a frame. Long press to toggle safelight."
                else "Meter and log a frame"
            },
        contentAlignment = Alignment.Center,
    ) {
        // An iris, not the word LOG.
        //
        // The shutter is the one control this app expects you to find without
        // reading it, and a three-letter label is a thing you read. The iris
        // says what the button is at a glance and in any language, and it is
        // the only icon in the set drawn as a circle, which is what the shutter
        // already was.
        //
        // Read off the disc rather than fixed white: the disc is magenta, and
        // magenta at low accent saturation is a pale grey that a white iris
        // disappears into. Under safelight this lands on void anyway, which is
        // what that scheme wants — a white glyph is white light however small.
        // Body and accent take the same colour: the dye detail would be a third
        // tone on a two-tone button.
        val glyph = colors.onAccent(colors.magenta, preferred = Color.White)
        FilmIcon(
            spec = FilmIcons.Aperture,
            contentDescription = null,
            size = 30.dp,
            tint = glyph,
            accent = glyph,
        )
    }
}

/** Nudge a colour toward white for the gradient's lit edge. */
private fun Color.lighten(amount: Float = 0.35f) = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)
