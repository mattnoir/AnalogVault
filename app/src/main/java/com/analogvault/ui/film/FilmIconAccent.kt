package com.analogvault.ui.film

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.analogvault.ui.theme.FilmTheme

/**
 * Every icon in the set is drawn in two colours: a body, and one detail in a
 * dye colour. [FilmIcons] ships the geometry and names the intended accent in
 * each icon's doc comment, but a comment cannot be read at runtime, so this is
 * that table in code.
 *
 * It is here rather than in [FilmIcons] because that file is generated from the
 * icon sheet and would lose anything added to it. The mapping is design intent,
 * not geometry, so it survives a regeneration on this side.
 *
 * The accents are theme colours, not literals, which is what makes the set
 * safelight-correct for free: under safelight `magenta` is already a red, so an
 * icon's accent follows the scheme without anything here knowing about it.
 */
@Composable
fun FilmIconSpec.dyeAccent(): Color {
    val c = FilmTheme.colors
    return when (this) {
        FilmIcons.Home, FilmIcons.Aperture, FilmIcons.Meter,
        FilmIcons.MeterSpot, FilmIcons.MeterCentre, FilmIcons.MeterMatrix,
        FilmIcons.ContactSheet, FilmIcons.Timer, FilmIcons.Map,
        FilmIcons.Push -> c.magenta

        FilmIcons.Stash, FilmIcons.Rolls, FilmIcons.More, FilmIcons.Stats,
        FilmIcons.Backup, FilmIcons.Lens, FilmIcons.FilmFrame, FilmIcons.Camera,
        FilmIcons.Scan, FilmIcons.Plus, FilmIcons.Filter, FilmIcons.LoadRoll,
        FilmIcons.Pull, FilmIcons.Rain, FilmIcons.Drizzle, FilmIcons.Snow,
        FilmIcons.Humidity,
        // The set names green for Done; the palette has no green, and cyan is
        // what this app already uses for a state that is finished and fine.
        FilmIcons.Check -> c.cyan

        FilmIcons.Weather, FilmIcons.Settings, FilmIcons.Bulb, FilmIcons.Lock,
        FilmIcons.Day, FilmIcons.Sunrise, FilmIcons.Thunder -> c.yellow

        FilmIcons.Darkroom, FilmIcons.Invert, FilmIcons.BlueHour,
        FilmIcons.Night -> c.violet

        FilmIcons.Box, FilmIcons.Tripod, FilmIcons.Pin, FilmIcons.BulkFilm,
        FilmIcons.Thermometer, FilmIcons.Warn, FilmIcons.Sunset,
        FilmIcons.GoldenHour,
        // Safelight red in the sheet. `mask` is this app's destructive colour
        // and is already red in both schemes.
        FilmIcons.Reject, FilmIcons.Trash, FilmIcons.Safelight -> c.mask

        FilmIcons.MoonNew, FilmIcons.MoonWaxCrescent, FilmIcons.MoonFirstQuarter,
        FilmIcons.MoonWaxGibbous, FilmIcons.MoonFull, FilmIcons.MoonWaneGibbous,
        FilmIcons.MoonLastQuarter, FilmIcons.MoonWaneCrescent -> c.halide

        else -> c.dim // Close, chevrons, Unlock, Cloudy, Fog, Wind
    }
}

/**
 * A [FilmIcon] with its designed accent already applied.
 *
 * This is the call every screen should make. [FilmIcon] itself defaults the
 * accent to the body colour, which draws the icon in one tone — right for a
 * label or a dense row, wrong for the general case, where the coloured detail
 * is the thing that tells two icons apart at 18dp.
 */
@Composable
fun DyeIcon(
    icon: FilmIconSpec,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = FilmTheme.colors.halide,
    accent: Color = icon.dyeAccent(),
) = FilmIcon(
    spec = icon,
    contentDescription = contentDescription,
    modifier = modifier,
    size = size,
    tint = tint,
    accent = accent,
)
