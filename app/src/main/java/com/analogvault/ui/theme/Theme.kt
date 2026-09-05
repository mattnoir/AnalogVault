package com.analogvault.ui.theme

/*
 * The amber palette that predated the Dye Layer used to live here: Bg, Bg2,
 * Amber, TextPrimary and the rest. It is gone, and deliberately not replaced
 * with aliases.
 *
 * Aliases were the obvious shortcut and the wrong one. They are top-level vals,
 * so they are fixed at the light scheme and cannot follow the safelight swap —
 * which left the app half red and half purple the moment safelight came on, with
 * the seam falling in the middle of individual cards. Every screen now reads
 * `FilmTheme.colors`, which is a CompositionLocal and therefore swaps with the
 * theme.
 *
 * If you find yourself wanting a colour constant here, you want
 * `FilmTheme.colors` instead. See FilmTheme.kt for what each token means.
 */
