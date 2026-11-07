package com.analogvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.data.model.Roll
import com.analogvault.ui.components.VaultButton
import com.analogvault.ui.components.VaultProgressBar
import com.analogvault.ui.components.VaultSheet
import com.analogvault.ui.theme.FilmColors
import com.analogvault.ui.theme.FilmTheme
import com.analogvault.ui.theme.Palette
import com.analogvault.util.AchCategory
import com.analogvault.util.AchProgress
import com.analogvault.util.AchRecord
import com.analogvault.util.Achievement

/**
 * One badge as the tab draws it: live progress joined to the stored record.
 * The record wins on whether it is earned — a badge stays earned after the
 * roll that earned it is deleted, even though the data no longer shows it.
 */
private data class Badge(val progress: AchProgress, val record: AchRecord?) {
    val achievement get() = progress.achievement
    val tier get() = maxOf(progress.tier, record?.tier ?: 0)
    val earned get() = tier > 0
    val hidden get() = achievement.category == AchCategory.SECRET && !earned
}

/**
 * Achievements laid out as a contact sheet: every badge is a frame, earned
 * ones exposed, the rest left as unexposed film. Secrets stay "???" until
 * they turn up.
 */
@Composable
fun StatsBadges(
    progress: List<AchProgress>?,
    records: Map<Achievement, AchRecord>,
    rolls: List<Roll>,
    onOpenRoll: (String) -> Unit,
) {
    val colors = FilmTheme.colors
    if (progress == null) return
    val badges = remember(progress, records) { progress.map { Badge(it, records[it.achievement]) } }
    var open by remember { mutableStateOf<Badge?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "summary") {
            val secrets = badges.filter { it.achievement.category == AchCategory.SECRET }
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatBox("Earned", "${badges.count { it.earned }}/${badges.size}",
                    colors.cyan, Modifier.weight(1f))
                StatBox("Secrets found", "${secrets.count { it.earned }}/${secrets.size}",
                    colors.magenta, Modifier.weight(1f))
            }
        }
        AchCategory.entries.forEach { category ->
            val inCategory = badges.filter { it.achievement.category == category }
            item(key = "head_${category.name}") {
                BadgeEyebrow(category.label, inCategory.count { it.earned }, inCategory.size)
            }
            inCategory.chunked(3).forEachIndexed { row, chunk ->
                item(key = "row_${category.name}_$row") {
                    Row(
                        Modifier.padding(horizontal = 14.dp).padding(bottom = 6.dp)
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        chunk.forEach { badge ->
                            val frameNo = badges.indexOf(badge) + 1
                            BadgeFrame(badge, frameNo, Modifier.weight(1f).fillMaxHeight()) { open = badge }
                        }
                        repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    open?.let { badge ->
        val roll = badge.record?.rollId?.let { id -> rolls.find { it.id == id } }
        BadgeSheet(badge, roll, onDismiss = { open = null }, onOpenRoll = {
            open = null
            onOpenRoll(it)
        })
    }
}

private fun FilmColors.accentFor(category: AchCategory): Color = when (category) {
    AchCategory.MILESTONE -> cyan
    AchCategory.CRAFT -> yellow
    AchCategory.SECRET -> magenta
}

private fun romanTier(n: Int): String = listOf("I", "II", "III", "IV", "V").getOrElse(n - 1) { n.toString() }

@Composable
private fun BadgeEyebrow(title: String, earned: Int, total: Int) {
    val colors = FilmTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = FilmTheme.type.eyebrow, color = colors.dim)
        Spacer(Modifier.width(8.dp))
        Text("$earned/$total", style = FilmTheme.type.rebate, color = colors.dim)
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(color = colors.edge)
    }
}

@Composable
private fun BadgeFrame(badge: Badge, frameNo: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = FilmTheme.colors
    val accent = colors.accentFor(badge.achievement.category)
    val title = if (badge.hidden) "???" else badge.achievement.title
    val status = when {
        badge.earned -> badge.record?.unlockedAt ?: "Earned"
        badge.hidden -> "Secret"
        else -> "${badge.progress.current.coerceAtMost(badge.progress.target)}/${badge.progress.target}"
    }
    Column(
        modifier
            .heightIn(min = 104.dp)
            .background(if (badge.earned) colors.film else colors.void)
            .border(1.dp, if (badge.earned) accent else colors.edge)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "$title, " + if (badge.earned) "earned" else "not earned yet"
            }
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        // The tier rides on the frame number: the date below has the whole
        // width to itself, and a roman numeral plus a date does not fit.
        val tierMark = if (badge.earned && badge.achievement.tiered) "  ${romanTier(badge.tier)}" else ""
        Text(frameNo.toString().padStart(2, '0') + if (badge.earned) tierMark else "  —",
            style = FilmTheme.type.rebate, color = if (badge.earned) accent else colors.dead)
        Spacer(Modifier.height(6.dp))
        Text(
            title.uppercase(),
            style = FilmTheme.type.stock.copy(fontSize = 15.sp, lineHeight = 16.sp),
            color = when {
                badge.earned -> colors.halide
                badge.hidden -> colors.dead
                else -> colors.dim
            },
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
      }
      Column(Modifier.padding(top = 6.dp)) {
        if (!badge.earned && !badge.hidden && badge.progress.target > 1) {
            VaultProgressBar(badge.progress.current.toFloat() / badge.progress.target, colors.dim)
            Spacer(Modifier.height(4.dp))
        }
        Text(status.uppercase(), style = FilmTheme.type.rebate,
            color = if (badge.earned) accent else colors.dim, maxLines = 1)
      }
    }
}

@Composable
private fun BadgeSheet(badge: Badge, roll: Roll?, onDismiss: () -> Unit, onOpenRoll: (String) -> Unit) {
    val colors = FilmTheme.colors
    val a = badge.achievement
    val accent = colors.accentFor(a.category)
    VaultSheet(title = if (badge.hidden) "???" else a.title, onDismiss = onDismiss) {
        Text(a.category.label.uppercase(), style = FilmTheme.type.eyebrow, color = accent)
        Spacer(Modifier.height(8.dp))
        if (badge.hidden) {
            Text("A secret. It shows itself once it's earned.", color = colors.halide)
            return@VaultSheet
        }
        Text(a.description, color = colors.halide)
        Spacer(Modifier.height(16.dp))

        if (a.tiered) {
            a.tiers.forEachIndexed { i, goal ->
                val reached = badge.tier > i
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${romanTier(i + 1)} · $goal", style = FilmTheme.type.data,
                        color = if (reached) accent else colors.dim)
                    Text(if (reached) "EARNED" else "—", style = FilmTheme.type.rebate,
                        color = if (reached) accent else colors.dead)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        val record = badge.record
        if (badge.earned && record != null) {
            Text("EARNED ${record.unlockedAt}", style = FilmTheme.type.rebate, color = accent)
            Palette.rewardedBy(a)?.let { p ->
                Spacer(Modifier.height(4.dp))
                Text("UNLOCKED THE ${p.label.uppercase()} PALETTE · SETTINGS → DISPLAY",
                    style = FilmTheme.type.rebate, color = colors.dim)
            }
            if (roll != null) {
                Spacer(Modifier.height(4.dp))
                val frame = record.shotIndex?.let { " · frame ${it + 1}" } ?: ""
                Text("On the roll loaded ${roll.startDate}$frame".uppercase(),
                    style = FilmTheme.type.rebate, color = colors.dim)
                Spacer(Modifier.height(16.dp))
                VaultButton("Open roll", onClick = { onOpenRoll(roll.id) })
            }
        } else if (!badge.earned) {
            val p = badge.progress
            Text("${p.current.coerceAtMost(p.target)} OF ${p.target}",
                style = FilmTheme.type.rebate, color = colors.dim)
            Spacer(Modifier.height(6.dp))
            VaultProgressBar(p.current.toFloat() / p.target, accent)
        }
    }
}
