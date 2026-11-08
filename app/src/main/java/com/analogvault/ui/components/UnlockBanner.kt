package com.analogvault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.ui.UnlockNotice
import com.analogvault.ui.film.halation
import com.analogvault.ui.theme.FilmTheme
import com.analogvault.util.AchCategory
import kotlinx.coroutines.delay

/** How long a banner stays before it clears itself. */
private const val SHOW_MS = 5_000L

/**
 * "Achievement earned", across the top of the screen for a few seconds.
 *
 * It sits over whatever is on screen rather than pushing it down: it is news,
 * not content, and the screen underneath should not jump. Tapping it opens the
 * badges; leaving it alone lets it go. Under Safelight it arrives and leaves
 * without moving or glowing, like everything else in the darkroom.
 */
@Composable
fun UnlockBanner(
    notice: UnlockNotice?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FilmTheme.colors
    // Holds the last notice through the exit, so the text does not vanish
    // before the banner does.
    var shown by remember { mutableStateOf<UnlockNotice?>(null) }
    if (notice != null) shown = notice
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(SHOW_MS)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = notice != null,
        modifier = modifier,
        enter = if (colors.safelight) fadeIn(snap()) else slideInVertically { -it } + fadeIn(),
        exit = if (colors.safelight) fadeOut(snap()) else slideOutVertically { -it } + fadeOut(),
    ) {
        val n = shown ?: return@AnimatedVisibility
        val single = n.earned.singleOrNull()
        val accent = when {
            single?.category == AchCategory.SECRET -> colors.magenta
            single?.category == AchCategory.CRAFT -> colors.yellow
            else -> colors.cyan
        }
        val eyebrow = when {
            n.earned.isEmpty() -> "Achievements"
            single == null -> "${n.earned.size} achievements earned"
            single.category == AchCategory.SECRET -> "Secret found"
            else -> "Achievement earned"
        }
        val title = when {
            n.earned.isEmpty() -> "You'd already earned ${n.backfilled}"
            single != null -> single.title
            else -> n.earned.joinToString(" · ") { it.title }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .halation(accent, radius = 16.dp, intensity = 0.25f, enabled = !colors.safelight)
                .background(colors.filmRaised)
                .border(1.dp, accent)
                .clickable(onClick = onOpen)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(36.dp).background(accent))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(eyebrow.uppercase(), style = FilmTheme.type.rebate, color = accent)
                Spacer(Modifier.height(3.dp))
                Text(
                    title.uppercase(),
                    style = FilmTheme.type.stock.copy(fontSize = 18.sp, lineHeight = 20.sp),
                    color = colors.halide, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("BADGES ›", style = FilmTheme.type.rebate, color = colors.dim)
        }
    }
}

private fun <T> snap() = androidx.compose.animation.core.snap<T>()
