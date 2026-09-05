package com.analogvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.theme.FilmTheme

/**
 * The rows Home is built from.
 *
 * An enum rather than a list of strings so the stored order can never name a
 * section that does not exist, and so adding one later is a compile-time
 * prompt to give it a label rather than a silent gap in someone's layout.
 */
enum class HomeSection(val label: String, val blurb: String) {
    HERO("Loaded roll", "The roll in your camera, with the frame counter"),
    PIPELINE("Pipeline", "Shooting, to dev, to scan, archived"),
    WEATHER("Weather", "Conditions, the shooting note and a stock pick"),
    LIGHT("Light right now", "Sun arc, golden hour and a suggested exposure"),
    NUDGES("Nudges", "Latent images, expiring stock, bulk running low"),
}

/** The order Home ships with, before anyone rearranges anything. */
val DEFAULT_HOME_ORDER: List<HomeSection> = listOf(
    HomeSection.HERO,
    HomeSection.PIPELINE,
    HomeSection.WEATHER,
    HomeSection.LIGHT,
    HomeSection.NUDGES,
)

/**
 * Parse a stored order, tolerating rot in both directions.
 *
 * Names that no longer exist are dropped, and sections the stored value has
 * never heard of are appended — so a layout saved by an older version keeps
 * working and a section added by a newer one shows up rather than vanishing.
 */
fun parseHomeOrder(stored: String?): List<HomeSection> {
    val known = stored.orEmpty().split(',')
        .mapNotNull { name -> HomeSection.entries.firstOrNull { it.name == name.trim() } }
        .distinct()
    return known + DEFAULT_HOME_ORDER.filterNot { it in known }
}

fun parseHomeHidden(stored: String?): Set<HomeSection> =
    stored.orEmpty().split(',')
        .mapNotNull { name -> HomeSection.entries.firstOrNull { it.name == name.trim() } }
        .toSet()

/**
 * Rearrange Home.
 *
 * Move buttons rather than drag-and-drop. A five-item list is not worth a drag
 * handler that fights the scroll container, and buttons are the version that
 * works with TalkBack, one-handed, and with gloves on in the cold — which is
 * more of this app's life than a slick gesture is.
 */
@Composable
fun HomeLayoutScreen(vm: MainViewModel) {
    val colors = FilmTheme.colors
    val order by vm.homeOrder.collectAsState()
    val hidden by vm.homeHidden.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().background(colors.void),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
    ) {
        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                Text("HOME LAYOUT", style = FilmTheme.type.eyebrow, color = colors.dim)
                Spacer(Modifier.height(8.dp))
                Text(
                    "The order these appear in on Home, and whether they appear at all. " +
                        "The header stays put.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.dim,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        items(order, key = { it.name }) { section ->
            val index = order.indexOf(section)
            val visible = section !in hidden
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(colors.film)
                    .border(1.dp, colors.edge)
                    .padding(12.dp)
            ) {
                // Only the title shares a line with the switch. The blurb sits
                // below at full width: it is the longest text on the card, and
                // squeezing it into the gap beside a 52dp control left it
                // wrapping tight against the switch on every row.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "%02d".format(index + 1),
                        style = FilmTheme.type.data,
                        color = if (visible) colors.cyan else colors.dead,
                        modifier = Modifier.width(30.dp),
                    )
                    Text(
                        section.label.uppercase(),
                        style = FilmTheme.type.stock.copy(fontSize = 18.sp),
                        color = if (visible) colors.halide else colors.dead,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(14.dp))
                    Switch(
                        checked = visible,
                        onCheckedChange = { vm.setHomeSectionVisible(section, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.void, checkedTrackColor = colors.cyan,
                            uncheckedThumbColor = colors.dim, uncheckedTrackColor = colors.film,
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = "Show ${section.label} on Home"
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    section.blurb.uppercase(),
                    style = FilmTheme.type.rebate,
                    color = if (visible) colors.dim else colors.dead,
                    modifier = Modifier.padding(start = 30.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoveButton(
                        label = "▲ UP",
                        enabled = index > 0,
                        description = "Move ${section.label} up",
                        modifier = Modifier.weight(1f),
                    ) { vm.moveHomeSection(section, -1) }
                    MoveButton(
                        label = "▼ DOWN",
                        enabled = index < order.lastIndex,
                        description = "Move ${section.label} down",
                        modifier = Modifier.weight(1f),
                    ) { vm.moveHomeSection(section, 1) }
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = colors.edge, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
            Box(Modifier.padding(horizontal = 16.dp)) {
                MoveButton(
                    label = "RESET TO DEFAULT",
                    enabled = true,
                    description = "Reset the Home layout to its default order",
                    modifier = Modifier.fillMaxWidth(),
                ) { vm.resetHomeLayout() }
            }
        }
    }
}

@Composable
private fun MoveButton(
    label: String,
    enabled: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = FilmTheme.colors
    Box(
        modifier
            .background(colors.void)
            .border(1.dp, if (enabled) colors.edge else colors.edge.copy(alpha = 0.4f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 9.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = FilmTheme.type.data,
            color = if (enabled) colors.halide else colors.dead,
        )
    }
}
