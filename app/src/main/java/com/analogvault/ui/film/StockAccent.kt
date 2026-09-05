package com.analogvault.ui.film

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.analogvault.ui.theme.FilmColors
import com.analogvault.ui.theme.FilmTheme
import java.util.Locale

/**
 * The colour a film stock is, derived from its box.
 *
 * Photographers know their stocks by sight, and the box art is the strongest
 * handle they have on them: Velvia's saturated green-to-blue, Portra's warm
 * cream-to-red, Tri-X's plain white-to-grey. Colouring a card by process alone
 * would sort a stash into three buckets and lose the thing that actually tells
 * two rolls apart at a glance.
 *
 * A stock the table does not know falls back to its process, which is still
 * more information than a uniform accent. The user can override any of it —
 * see [FilmStock.stockAccent] — which is what makes the long tail workable
 * without this table having to know every stock ever made.
 */
data class StockAccent(val start: Color, val end: Color) {
    /** Single representative colour, for text and chips. */
    val solid: Color get() = start

    /** Top-to-bottom, for the spine down the edge of a card. */
    fun verticalBrush(): Brush = Brush.verticalGradient(listOf(start, end))
}

/**
 * Known stocks, matched on any distinctive word in the name.
 *
 * Matching is on substrings rather than exact names because the same stock is
 * entered a dozen ways — "Portra 400", "Kodak Portra 400", "portra400" — and
 * an exact-match table would miss most real stash entries.
 *
 * Order matters: the first match wins, so the more specific key has to come
 * first. "Tri-X" before "X" ever would; "Portra 800" before "Portra".
 */
private val KNOWN_STOCKS: List<Pair<String, StockAccent>> = listOf(
    // Fujifilm — Velvia's greens, Provia's cooler neutral, Superia's cyan-green
    "velvia" to StockAccent(Color(0xFF00E07A), Color(0xFF0066FF)),
    "provia" to StockAccent(Color(0xFF6FD3E8), Color(0xFF1B4FA8)),
    "astia" to StockAccent(Color(0xFFB8E0C8), Color(0xFF2E7D6B)),
    "acros" to StockAccent(Color(0xFFF2F2F2), Color(0xFF4A4A55)),
    "superia" to StockAccent(Color(0xFF57D9A8), Color(0xFF15616D)),
    "pro 400h" to StockAccent(Color(0xFFBFE8D8), Color(0xFF3E7CA8)),
    "fujicolor" to StockAccent(Color(0xFF3FBF6F), Color(0xFF0E5A8A)),
    "c200" to StockAccent(Color(0xFF3FBF6F), Color(0xFF0E5A8A)),
    // Kodak — Portra's cream-to-red, Gold's amber, Ektar's saturated red
    "portra" to StockAccent(Color(0xFFFFC26B), Color(0xFFD9342B)),
    "ektar" to StockAccent(Color(0xFFFF8A5C), Color(0xFFB0122A)),
    "gold" to StockAccent(Color(0xFFFFD36B), Color(0xFFC07A18)),
    "ultramax" to StockAccent(Color(0xFFFFC44D), Color(0xFFCE4B1E)),
    "colorplus" to StockAccent(Color(0xFFFFD9A0), Color(0xFFC2561B)),
    "tri-x" to StockAccent(Color(0xFFFFFFFF), Color(0xFF555555)),
    "trix" to StockAccent(Color(0xFFFFFFFF), Color(0xFF555555)),
    "t-max" to StockAccent(Color(0xFFE8E8F0), Color(0xFF3A3A48)),
    "tmax" to StockAccent(Color(0xFFE8E8F0), Color(0xFF3A3A48)),
    "ektachrome" to StockAccent(Color(0xFF7ED4FF), Color(0xFF1547A8)),
    // Motion picture stock, respooled — tungsten-balanced, so it gets the cool
    // cast it actually renders rather than the warm one "Kodak" implies.
    "vision" to StockAccent(Color(0xFF6FC7D8), Color(0xFF1E3F7A)),
    // Ilford — the greys, kept distinguishable from each other
    "hp5" to StockAccent(Color(0xFFEDEDED), Color(0xFF44444E)),
    "fp4" to StockAccent(Color(0xFFDCDCE4), Color(0xFF52525E)),
    "delta" to StockAccent(Color(0xFFCFD6E0), Color(0xFF3C4654)),
    "pan f" to StockAccent(Color(0xFFF5F5F5), Color(0xFF5E5E68)),
    "xp2" to StockAccent(Color(0xFFE0E4EC), Color(0xFF4A5260)),
    // The distinctive ones
    "cinestill" to StockAccent(Color(0xFF2BC7D4), Color(0xFFE01B3C)),
    "redscale" to StockAccent(Color(0xFFFF6B2C), Color(0xFF8B1A00)),
    "lomochrome" to StockAccent(Color(0xFFB86BFF), Color(0xFF4B0F7A)),
    "aerochrome" to StockAccent(Color(0xFFFF4FA3), Color(0xFF7A0F3C)),
    "fomapan" to StockAccent(Color(0xFFDCD8CC), Color(0xFF4E4A40)),
    "kentmere" to StockAccent(Color(0xFFD8D8D8), Color(0xFF4A4A4A)),
    "rollei" to StockAccent(Color(0xFFCBD5D8), Color(0xFF35454A)),
)

/**
 * Resolve a stock's accent: explicit override, then the name table, then the
 * process.
 */
@Composable
fun rememberStockAccent(
    name: String,
    type: String,
    override: String = "",
): StockAccent {
    // A stock accent is the one colour on screen that does not come from the
    // scheme, so the safelight swap misses it: left alone, Tri-X's white and
    // Vision 3's cyan go on throwing a second hue in the dark, which is the
    // exact thing the mode exists to stop. [stockAccentFor] folds the accent
    // into the red ramp instead of dropping it — brightness still tells the
    // stocks apart.
    return stockAccentFor(FilmTheme.colors, name, type, override)
}

/**
 * The same resolution as [rememberStockAccent], for callers outside composition
 * — building a list of map markers, say, where the accent is one field of a data
 * object rather than something being drawn.
 *
 * Takes the palette as a parameter because [FilmTheme] is a CompositionLocal.
 * Read it once at the call site and pass it in.
 */
fun stockAccentFor(
    colors: FilmColors,
    name: String,
    type: String,
    override: String = "",
): StockAccent {
    val accent = resolveAccent(colors, name, type, override)
    return if (colors.safelight) accent.toSafelight(colors) else accent
}

private fun resolveAccent(
    colors: FilmColors,
    name: String,
    type: String,
    override: String,
): StockAccent {
    parseAccentOverride(override)?.let { return it }

    val haystack = name.lowercase(Locale.ROOT)
    KNOWN_STOCKS.firstOrNull { (key, _) -> haystack.contains(key) }?.let { return it.second }

    // Unknown stock: fall back to the process, which at least separates a slide
    // film from a colour negative on sight.
    val process = type.lowercase(Locale.ROOT)
    return when {
        process.contains("slide") || process.contains("e-6") ->
            StockAccent(colors.cyan, colors.violet)
        process.contains("black") || process.contains("b&w") ->
            StockAccent(colors.halide, colors.dim)
        process.contains("infrared") ->
            StockAccent(colors.magenta, colors.mask)
        process.contains("instant") ->
            StockAccent(colors.yellow, colors.mask)
        else ->
            StockAccent(colors.mask, colors.magenta) // C-41 and anything unlabelled
    }
}

/**
 * The same accent, rewritten in the safelight scheme's reds.
 *
 * Both ends are mapped, so a gradient spine keeps its direction and a
 * white-to-grey stock still reads brighter than a dark one.
 */
fun StockAccent.toSafelight(colors: FilmColors): StockAccent =
    StockAccent(colors.redAt(start), colors.redAt(end))

/**
 * A red of the same apparent brightness as [source].
 *
 * The scheme's own ramp is the anchor — `violet` is its darkest red and
 * `halide` its brightest — so interpolating between them by luminance lands
 * inside the four steps safelight is built around rather than inventing a red
 * of its own.
 */
fun FilmColors.redAt(source: Color): Color =
    lerp(violet, halide, luminanceOf(source))

private fun luminanceOf(c: Color): Float =
    (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue).coerceIn(0f, 1f)

/** "#RRGGBB" or "#RRGGBB,#RRGGBB". Returns null on anything unparseable. */
fun parseAccentOverride(raw: String): StockAccent? {
    if (raw.isBlank()) return null
    val parts = raw.split(",").map { it.trim() }
    val colors = parts.mapNotNull { parseHexColor(it) }
    return when (colors.size) {
        1 -> StockAccent(colors[0], colors[0])
        2 -> StockAccent(colors[0], colors[1])
        else -> null
    }
}

private fun parseHexColor(hex: String): Color? {
    val body = hex.removePrefix("#")
    if (body.length != 6) return null
    val value = body.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or value)
}

/** Serialise back to the stored form. */
fun StockAccent.toStoredValue(): String =
    if (start == end) hexOf(start) else "${hexOf(start)},${hexOf(end)}"

private fun hexOf(c: Color): String =
    "#%02X%02X%02X".format(
        (c.red * 255).toInt(),
        (c.green * 255).toInt(),
        (c.blue * 255).toInt(),
    )
