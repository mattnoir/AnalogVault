package com.analogvault.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.R

/* ------------------------------------------------------------------------
 * DYE LAYER — colour tokens
 *
 * The palette is the emulsion: the three subtractive dye layers of a colour
 * negative, plus the orange base mask. This is not decoration — the semantics
 * are load-bearing and every screen must use them the same way:
 *
 *   cyan     loaded / cold / pull / "this is live"
 *   magenta  action / push / commit / destructive-adjacent
 *   yellow   waiting / attention / metered light
 *   mask     expiry, out-of-range, latent-image decay
 *   violet   structural accent, hard shadows, archive
 * ---------------------------------------------------------------------- */

private val DyeCyan     = Color(0xFF00F0FF)
private val DyeMagenta  = Color(0xFFFF1E7A)
private val DyeYellow   = Color(0xFFFFE01B)
private val BaseMask    = Color(0xFFFF6B2C)
private val SynthViolet = Color(0xFF7B2CFF)

private val TrueVoid     = Color(0xFF000000)
private val FilmBase     = Color(0xFF100C18)
private val FilmRaised   = Color(0xFF1A1424)
private val EdgeLine     = Color(0xFF2E2440)
private val SilverHalide = Color(0xFFEDE9F5)
// 4.76:1 on FilmBase and 5.18:1 on TrueVoid. It used to be #7E7594, which
// measured 4.47:1 on FilmBase — under the 4.5:1 floor, and this is the colour
// every eyebrow, rebate line and secondary label in the app is painted in, so
// it was the single worst place to be a hair short.
private val DimHalide    = Color(0xFF8479A0)
// Deliberately below the contrast floor: this is the disabled and unavailable
// colour — hatched ladder rungs, unexposed frames, out-of-stock cards — and
// WCAG exempts disabled controls. Nothing load-bearing may be painted in it.
private val DeadHalide   = Color(0xFF3A3150)

@Immutable
data class FilmColors(
    val cyan: Color,
    val magenta: Color,
    val yellow: Color,
    val mask: Color,
    val violet: Color,
    val void: Color,
    val film: Color,
    val filmRaised: Color,
    val edge: Color,
    val halide: Color,
    val dim: Color,
    val dead: Color,
    /** True when the safelight scheme is active. Suppress glows and animation. */
    val safelight: Boolean,
)

val DyeLayerColors = FilmColors(
    cyan = DyeCyan,
    magenta = DyeMagenta,
    yellow = DyeYellow,
    mask = BaseMask,
    violet = SynthViolet,
    void = TrueVoid,
    film = FilmBase,
    filmRaised = FilmRaised,
    edge = EdgeLine,
    halide = SilverHalide,
    dim = DimHalide,
    dead = DeadHalide,
    safelight = false,
)

/**
 * Safelight is a second [FilmColors], not a colour filter.
 *
 * A ColorMatrix crushes every hue to the same red and destroys the contrast
 * hierarchy exactly when you need it most — squinting at a dev timer in the
 * dark. Swapping the scheme lets us keep four distinguishable luminance steps
 * inside the red channel, which is what actually stays readable.
 *
 * This scheme is the app's one documented exception to the 4.5:1 contrast
 * target, and the exception is the point. Safelight exists to preserve dark
 * adaptation, and the brightness that would carry a red label to 4.5:1 on black
 * is exactly the brightness that ruins the adaptation you turned it on for.
 * `dim` here is raised as far as that trade allows (about 3.6:1 rather than the
 * 2.3:1 it started at) and no further.
 */
val SafelightColors = FilmColors(
    cyan = Color(0xFFFF6B6B),
    magenta = Color(0xFFFF3B30),
    yellow = Color(0xFFFF8A80),
    mask = Color(0xFFFF3B30),
    violet = Color(0xFF7A1414),
    void = Color(0xFF000000),
    film = Color(0xFF0C0000),
    filmRaised = Color(0xFF160202),
    edge = Color(0xFF3A0808),
    halide = Color(0xFFFF7A70),
    dim = Color(0xFFB85050),
    dead = Color(0xFF4A0F0F),
    safelight = true,
)

val LocalFilmColors = staticCompositionLocalOf { DyeLayerColors }

/* ------------------------------------------------------------------------
 * Typography
 *
 * Big Shoulders Display  — condensed heavy caps, the type on a film box.
 * Space Grotesk          — prose and UI labels.
 * Space Mono             — EVERY number. Frame counts, EV, f-stops, ISO,
 *                          edge markings. Tabular figures matter here because
 *                          readouts change live and must not reflow.
 *
 * Bundled, not Downloadable Fonts — the app has to work in a darkroom with no
 * signal.
 *
 * Big Shoulders and Space Grotesk only ship as variable fonts upstream. These
 * are static instances cut from those files with
 * `fontTools.varLib.instancer --update-name-table`, not the variable fonts
 * pinned at runtime with FontVariation.Settings. Runtime instancing rendered
 * correctly on one test device and fell back to the family's default (hairline)
 * weight on another running Android 16, and a heading that silently loses 500
 * units of weight on someone's phone is not a risk worth carrying for two files.
 * Baking the outlines removes the platform from the equation.
 * ---------------------------------------------------------------------- */

val BigShoulders = FontFamily(
    Font(R.font.big_shoulders_display_bold, FontWeight.Bold),
    Font(R.font.big_shoulders_display_black, FontWeight.Black),
)

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

/** Styles that have no sensible Material slot. Reach for these by name. */
@Immutable
data class FilmTextStyles(
    /** Screen titles. Uppercase at the call site, not here. */
    val display: TextStyle,
    /** Film stock names on cards. */
    val stock: TextStyle,
    /** Large live numerics: EV, frame counter, aperture. */
    val readout: TextStyle,
    /** Section eyebrows above a hairline rule. */
    val eyebrow: TextStyle,
    /** Chips, badges, ladder rungs. */
    val data: TextStyle,
    /** Edge printing along the film rebate. Decorative but real. */
    val rebate: TextStyle,
)

val DyeLayerTextStyles = FilmTextStyles(
    display = TextStyle(
        fontFamily = BigShoulders, fontWeight = FontWeight.Black,
        fontSize = 38.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp,
    ),
    stock = TextStyle(
        fontFamily = BigShoulders, fontWeight = FontWeight.Black,
        fontSize = 26.sp, lineHeight = 24.sp,
    ),
    readout = TextStyle(
        fontFamily = SpaceMono, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = (-1).sp,
    ),
    eyebrow = TextStyle(
        fontFamily = SpaceMono, fontWeight = FontWeight.Normal,
        fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.8.sp,
    ),
    data = TextStyle(
        fontFamily = SpaceMono, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp,
    ),
    rebate = TextStyle(
        fontFamily = SpaceMono, fontWeight = FontWeight.Normal,
        fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 2.2.sp,
    ),
)

val LocalFilmTextStyles = staticCompositionLocalOf { DyeLayerTextStyles }

private val FilmTypography = Typography(
    displayLarge = DyeLayerTextStyles.display,
    headlineMedium = DyeLayerTextStyles.stock,
    titleMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = SpaceGrotesk, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = SpaceGrotesk, fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = DyeLayerTextStyles.data,
    labelSmall = DyeLayerTextStyles.eyebrow,
)

/* ------------------------------------------------------------------------
 * Shapes — everything is a rectangle. No exceptions except the shutter
 * button (a circle, because it's a shutter release) and the leader tongue.
 * ---------------------------------------------------------------------- */

// Material's Shapes slots only accept CornerBasedShape, so a zero-radius
// RoundedCornerShape stands in for RectangleShape. Same pixels, and it keeps the
// corner-animation machinery in components like Button working.
private val NoCorners = RoundedCornerShape(0.dp)

private val FilmShapes = Shapes(
    extraSmall = NoCorners,
    small = NoCorners,
    medium = NoCorners,
    large = NoCorners,
    extraLarge = NoCorners,
)

private fun FilmColors.toMaterialScheme(): ColorScheme = darkColorScheme(
    primary = cyan, onPrimary = void,
    secondary = magenta, onSecondary = void,
    tertiary = yellow, onTertiary = void,
    background = void, onBackground = halide,
    surface = film, onSurface = halide,
    surfaceVariant = filmRaised, onSurfaceVariant = dim,
    outline = edge, outlineVariant = edge,
    error = mask, onError = void,
    scrim = Color.Black.copy(alpha = 0.72f),
    // The single most important line in this file. Material 3 tints every
    // Surface toward `surfaceTint` as elevation rises — that tonal wash is
    // why the old build reads as muddy brown instead of black. Kill it
    // and elevation becomes purely a shadow/border decision, which is what
    // this design wants.
    surfaceTint = Color.Transparent,
)

@Composable
fun FilmTheme(
    safelight: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (safelight) SafelightColors else DyeLayerColors
    CompositionLocalProvider(
        LocalFilmColors provides colors,
        LocalFilmTextStyles provides DyeLayerTextStyles,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = FilmTypography,
            shapes = FilmShapes,
            content = content,
        )
    }
}

/** Accessor: `FilmTheme.colors.cyan`, `FilmTheme.type.readout`. */
object FilmTheme {
    val colors: FilmColors
        @Composable @ReadOnlyComposable get() = LocalFilmColors.current
    val type: FilmTextStyles
        @Composable @ReadOnlyComposable get() = LocalFilmTextStyles.current
}
