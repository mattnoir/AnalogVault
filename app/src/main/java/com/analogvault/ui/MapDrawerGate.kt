package com.analogvault.ui

import androidx.compose.runtime.compositionLocalOf

/** Notifies the root drawer when a map is on screen so edge-swipe won't steal pan gestures. */
val LocalSetMapScreenActive = compositionLocalOf<(Boolean) -> Unit> { {} }
