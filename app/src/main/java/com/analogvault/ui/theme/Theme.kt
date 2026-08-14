package com.analogvault.ui.theme

import androidx.compose.ui.graphics.Color

/* ------------------------------------------------------------------------
 * Compatibility palette.
 *
 * These are the names the pre-redesign screens were written against. Rather
 * than touch several thousand call sites, each one is now an alias onto the
 * Dye Layer value that carries the same meaning, so a screen that still says
 * `TextSecondary` renders in the right colour without being rewritten.
 *
 * Two things to know before using them in new code:
 *
 *  1. **Prefer `FilmTheme.colors`.** These are top-level vals and therefore
 *     fixed at the light scheme. They cannot follow the safelight swap, which
 *     only `FilmTheme.colors` can, because that reads a CompositionLocal.
 *     Anything painted with a constant from this file stays purple-black under
 *     a safelight. That is the one place the redesign is still incomplete, and
 *     it shrinks every time a screen is converted.
 *  2. The semantic mapping matters more than the name. `Amber` is not amber any
 *     more; it is the cyan this design uses for "live / selected", because that
 *     is the job amber was doing.
 * ---------------------------------------------------------------------- */

// Surfaces, darkest to lightest.
val Bg        = Color(0xFF000000)   // void
val Bg2       = Color(0xFF100C18)   // film
val Bg3       = Color(0xFF1A1424)   // filmRaised
val Bg4       = Color(0xFF221A2E)   // one step above raised, for tracks
val Border    = Color(0xFF2E2440)   // edge

// Accents. Amber was doing the work of "selected, live, primary", which in the
// Dye Layer is cyan; AmberBright was the emphatic version of the same idea.
val Amber       = Color(0xFF00F0FF) // cyan
val AmberBright = Color(0xFFFFE01B) // yellow — attention, the metered number
val AmberDark   = Color(0xFF7B2CFF) // violet — structural, the pressed state

val TextPrimary   = Color(0xFFEDE9F5) // halide
val TextSecondary = Color(0xFF8479A0) // dim — 4.76:1 on Bg2, see FilmTheme
val TextTertiary  = Color(0xFF8479A0) // also dim: the old tertiary failed contrast
val TextDisabled  = Color(0xFF3A3150) // dead — disabled only, exempt from 4.5:1

// Status. Green has no place in a subtractive palette, so "ok" is cyan, which
// already means live and correct everywhere else in the app.
val GreenOk   = Color(0xFF00F0FF)
val BlueInfo  = Color(0xFF7B2CFF)   // violet
val RedErr    = Color(0xFFFF1E7A)   // magenta — destructive, commits
val OrangeWarn= Color(0xFFFF6B2C)   // base mask — expiry, out of range
