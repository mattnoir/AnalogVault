package com.analogvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.analogvault.data.model.Shot
import com.analogvault.ui.theme.FilmTheme
import com.analogvault.util.monoMatrix
import java.io.File

/** The meter's black-and-white look as a Compose filter; null leaves colour alone. */
fun monoColorFilter(contrast: Float?): ColorFilter? =
    contrast?.let { ColorFilter.colorMatrix(ColorMatrix(monoMatrix(it))) }

/**
 * A logged frame's photo, shown the way it was seen when it was logged: grey
 * at the meter's contrast for a frame logged in black and white, in colour
 * otherwise. [mono] overrides that; pass a contrast to grey it, or leave it as
 * the shot's own.
 */
@Composable
fun FramePhoto(
    shot: Shot,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
    mono: Float? = shot.monoContrast,
) {
    val filter = remember(mono) { monoColorFilter(mono) }
    AsyncImage(
        model = File(shot.photoThumbPath),
        contentDescription = contentDescription,
        contentScale = contentScale,
        colorFilter = filter,
        modifier = modifier,
    )
}

/**
 * A frame's photo full screen, with a switch between black and white and
 * colour. It opens the way the frame was logged; the switch only changes what
 * is shown here and is forgotten when the lightbox closes.
 *
 * A colour frame greyed here uses [fallbackContrast], the meter's current
 * contrast, so it looks the way the meter would have shown it.
 */
@Composable
fun FrameLightbox(shot: Shot, fallbackContrast: Float, onDismiss: () -> Unit) {
    val colors = FilmTheme.colors
    val loggedMono = shot.monoContrast != null
    var showMono by rememberSaveable(shot.id) { mutableStateOf(loggedMono) }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(colors.void.copy(alpha = 0.95f))
            .clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                FramePhoto(
                    shot,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                    mono = if (showMono) shot.monoContrast ?: fallbackContrast else null,
                )
                Spacer(Modifier.height(12.dp))
                VaultButton(
                    text = if (showMono) "Show in colour" else "Show in B&W",
                    small = true, ghost = true,
                    onClick = { showMono = !showMono },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (loggedMono) "Logged in B&W" else "Logged in colour",
                    style = FilmTheme.type.eyebrow, color = colors.dim,
                )
            }
        }
    }
}
