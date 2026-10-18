package com.analogvault.ui.film

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.analogvault.ui.theme.FilmTheme

/**
 * Pull-to-refresh, drawn as a light leak.
 *
 * A frame fogged by light getting past the back door flares warm from the edge
 * and falls off into the emulsion. That is exactly the shape of a pull gesture:
 * strongest where your finger is, fading into the page, growing as you pull.
 *
 * There is no spinner underneath this. A circular indicator would be a second
 * loading vocabulary in an app that already advances film for the same idea,
 * and two vocabularies for one state is how a design stops meaning anything.
 *
 * Under safelight it still draws — a leak is red light by definition — but it
 * stops breathing, because movement at the edge of vision is what makes you
 * look up from the tank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.LightLeak(
    state: PullToRefreshState,
    isRefreshing: Boolean,
) {
    val colors = FilmTheme.colors
    val still = rememberReduceMotion() || colors.safelight

    // While refreshing the leak breathes rather than sitting flat, so a slow
    // network still looks like something is happening.
    val breath = if (!isRefreshing || still) 1f else {
        val transition = rememberInfiniteTransition(label = "leak")
        val v by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
            label = "leakBreath",
        )
        v
    }

    val pull = if (isRefreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
    if (pull <= 0.01f) return

    val strength = pull * breath
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            // Grows with the pull. Capped well short of the screen: a leak that
            // swallowed the page would hide the content it is refreshing.
            .height((140 * pull).dp)
            .clearAndSetSemantics { }
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to colors.mask.copy(alpha = 0.75f * strength),
                        0.35f to colors.yellow.copy(alpha = 0.35f * strength),
                        1f to Color.Transparent,
                    )
                )
            }
    )
}
