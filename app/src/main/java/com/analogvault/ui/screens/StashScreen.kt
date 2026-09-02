@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.analogvault.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.data.model.*
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import androidx.compose.ui.graphics.Color
import com.analogvault.ui.film.FilmChip
import com.analogvault.ui.film.FilmChipRow
import com.analogvault.ui.film.FilmMeter
import com.analogvault.ui.film.FilmStockCard
import com.analogvault.ui.film.StockAccent
import com.analogvault.ui.film.rebateLine
import com.analogvault.ui.film.rememberStockAccent
import com.analogvault.ui.film.toSafelight
import com.analogvault.ui.film.toStoredValue
import com.analogvault.ui.theme.*
import com.analogvault.ui.theme.FilmTheme
import com.analogvault.ui.uid
import com.analogvault.util.Constants
import com.analogvault.util.toDecimalOrNull
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch

// ─── Format options (replaces "shots") ───────────────────────────────────────
val FILM_FORMATS_DISPLAY = listOf("135 (35mm)", "120", "220", "4x5", "8x10", "Super 8", "110", "126", "Instant")

/**
 * The "I haven't filled this in" entry in the gear-limit dropdowns, stored as a
 * blank string. It reads as a dash rather than an empty row so the option is
 * visibly pickable — an empty row looks like a rendering fault, and these limits
 * are ones a user legitimately wants to clear again once set.
 */
private const val UNKNOWN_LIMIT = "—"

/** Bare aperture number as the lens fields store it ("8", "5.6"). */
private fun apertureNum(a: Double): String =
    if (a == a.toLong().toDouble()) a.toLong().toString() else a.toString()

@Composable
fun StashScreen(vm: MainViewModel) {
    // Collect once at top level — each tab only reads what it needs
    val films       by vm.films.collectAsState()
    val cameras     by vm.cameras.collectAsState()
    val lenses      by vm.lenses.collectAsState()
    val accessories by vm.accessories.collectAsState()
    val rolls       by vm.rolls.collectAsState()

    // Cameras with an active (unfinished, undeveloped) roll loaded
    val busyCameraIds by remember(rolls) {
        derivedStateOf { rolls.filter { !it.finished && !it.developed }.map { it.cameraId }.toSet() }
    }

    val tabs = listOf(
        "Film" to films.count { it.quantity > 0 },
        "Cameras" to cameras.size,
        "Lenses" to lenses.size,
        "Accessories" to accessories.size,
    )
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        // Chips, not tabs.
        //
        // A ScrollableTabRow was the last Material component on the screen, and
        // it showed: a sliding underline, a ripple and a horizontal scroll
        // offset, none of which appear anywhere else in the app. These four are
        // one property of one list — the same shape of choice the Rolls stage
        // filter makes — so they get the same hairline chips, and Rolls and
        // Stash stop being two different ideas of how to switch a list.
        //
        // Wrapping also fixes what forced the row to scroll in the first place:
        // "Accessories" no longer has to fit a quarter of the width, so it can
        // carry its count instead of being clipped or scrolled off the edge.
        FilmChipRow(
            Modifier
                .fillMaxWidth()
                .background(FilmTheme.colors.void)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)
        ) {
            tabs.forEachIndexed { i, (label, count) ->
                val selected = pagerState.currentPage == i
                FilmChip(
                    text = "$label $count",
                    color = if (selected) FilmTheme.colors.cyan else FilmTheme.colors.dim,
                    filled = selected,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> FilmStashTab(films, vm)
                1 -> CameraStashTab(cameras, vm, busyCameraIds)
                2 -> LensStashTab(lenses, vm)
                3 -> AccessoryStashTab(accessories, vm)
            }
        }
    }
}

/**
 * The controls above a stash list.
 *
 * The section's name and its size live in the chip row at the top of the
 * screen — "FILM 12" sitting directly above "Film Stash 12/20" was the same
 * fact three times. What the chip cannot say is that a filter is hiding part
 * of the list, so that is the only thing this line says, and only while it is
 * true. The rest of the row is the two actions, which have nowhere else to be.
 */
@Composable
private fun StashHeaderRow(
    shown: Int,
    total: Int,
    filterOpen: Boolean,
    filterActive: Boolean,
    onToggleFilter: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (shown < total) {
            Text(
                "SHOWING $shown OF $total",
                style = FilmTheme.type.eyebrow,
                color = FilmTheme.colors.dim,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleFilter, Modifier.size(36.dp)) {
            Icon(
                Icons.Default.FilterList, null, modifier = Modifier.size(18.dp),
                tint = if (filterOpen || filterActive) FilmTheme.colors.cyan
                       else FilmTheme.colors.dim,
            )
        }
        VaultButton("+ Add", small = true, onClick = onAdd)
    }
}

// ─── Film Stash ───────────────────────────────────────────────────────────────

@Composable
fun FilmStashTab(films: List<FilmStock>, vm: MainViewModel) {
    var showSheet     by remember { mutableStateOf(false) }
    var editing       by remember { mutableStateOf<FilmStock?>(null) }
    var confirmDelete by remember { mutableStateOf<FilmStock?>(null) }
    var viewingFilm   by remember { mutableStateOf<FilmStock?>(null) }
    var loadingFilm   by remember { mutableStateOf<FilmStock?>(null) }
    var bulkExpanded  by remember { mutableStateOf(true) }
    val rolls         by vm.rolls.collectAsState()
    val bulkRolls     by vm.bulkRolls.collectAsState()
    val currency      by vm.currency.collectAsState()
    val isMetric      by vm.isMetric.collectAsState()
    var showBulkSheet   by remember { mutableStateOf(false) }
    var editingBulk     by remember { mutableStateOf<BulkRoll?>(null) }
    var loadingBulk     by remember { mutableStateOf<BulkRoll?>(null) }
    var confirmDeleteBulk by remember { mutableStateOf<BulkRoll?>(null) }

    // Films currently being shot (not yet finished) — blocks re-loading the same film
    val activeFilmIds by remember { derivedStateOf {
        rolls.filter { !it.finished && !it.developed }.map { it.filmId }.toSet()
    }}

    // Quantity-0 films stay in the DB (rolls reference them by id for name lookups)
    // but were previously invisible — surface them so users can restock or delete.
    var depletedExpanded by remember { mutableStateOf(false) }
    val depleted = remember(films) { films.filter { it.quantity <= 0 }.sortedBy { it.name } }

    // ── Sort + Filter ─────────────────────────────────────────────────────────
    var sortBy     by remember { mutableStateOf("Name") }
    var filterType by remember { mutableStateOf("All") }
    var filterExp  by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    val sortOptions   = listOf("Name", "Brand", "ISO", "Expiry", "Type")
    val typeOptions   = listOf("All") + Constants.FILM_TYPES

    val displayFilms = remember(films, sortBy, filterType, filterExp) {
        films
            .filter { f ->
                f.quantity > 0 &&
                (filterType == "All" || f.type == filterType) &&
                (!filterExp || run {
                    val raw = f.expiryDate
                    val exp = if (raw.length == 7) "$raw-01" else raw
                    if (exp.isBlank()) return@run false
                    try {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        sdf.parse(exp)!!.before(java.util.Date())
                    } catch (e: Exception) { false }
                })
            }
            .sortedWith(when (sortBy) {
                "Brand"  -> compareBy { it.brand }
                "ISO"    -> compareBy { it.iso }
                "Expiry" -> compareBy { it.expiryDate }
                "Type"   -> compareBy { it.type }
                else     -> compareBy { it.name }
            })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header row with add + filter
        item(key = "header") {
            // Against the stash count, not films.size: the depleted films below
            // are not part of what the chip counts, so counting them here would
            // report a filter that is not hiding anything.
            StashHeaderRow(
                shown = displayFilms.size,
                total = films.count { it.quantity > 0 },
                filterOpen = showFilter,
                filterActive = filterType != "All" || filterExp,
                onToggleFilter = { showFilter = !showFilter },
                onAdd = { editing = null; showSheet = true },
            )
        }

        // ── Bulk Film section ──────────────────────────────────────────────
        item(key = "bulk_header") {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth().clickable { bulkExpanded = !bulkExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = if (bulkExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null, tint = FilmTheme.colors.dim,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Bulk Film (${bulkRolls.size})", color = FilmTheme.colors.yellow, fontSize = 13.sp)
                }
                VaultButton("+ Bulk", small = true, ghost = true,
                    onClick = { editingBulk = null; showBulkSheet = true })
            }
            Spacer(Modifier.height(6.dp))
        }
        if (bulkExpanded) {
            if (bulkRolls.isEmpty()) {
                item(key = "bulk_empty") {
                    Text("No bulk film tracked yet", color = FilmTheme.colors.dim, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            items(bulkRolls, key = { "bulk_${it.id}" }, contentType = { "bulk" }) { bulk ->
                BulkRollCard(
                    bulk = bulk,
                    isMetric = isMetric,
                    onLoad   = { loadingBulk = bulk },
                    onEdit   = { editingBulk = bulk; showBulkSheet = true },
                    onDelete = { confirmDeleteBulk = bulk }
                )
            }
        }

        // ── Divider before regular stash ──────────────────────────────────
        item(key = "stash_divider") {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(Modifier.weight(1f), color = FilmTheme.colors.edge)
                Text("Individual Rolls", color = FilmTheme.colors.dim, fontSize = 11.sp)
                HorizontalDivider(Modifier.weight(1f), color = FilmTheme.colors.edge)
            }
            Spacer(Modifier.height(4.dp))
        }
        // Filter bar
        if (showFilter) {
            item(key = "filter") {
                FilterBar(
                    sortBy = sortBy, sortOptions = sortOptions, onSort = { sortBy = it },
                    filterLabel = filterType, filterOptions = typeOptions, onFilter = { filterType = it },
                    extraToggle = "Expired only" to filterExp, onExtraToggle = { filterExp = it }
                )
            }
        }
        if (displayFilms.isEmpty()) item(key = "empty") { if (films.isEmpty()) EmptyState("No film in the stash.", verb = "Add a stock", onVerb = { editing = null; showSheet = true })
            else EmptyState("No film matches this filter.", verb = "Clear the filter to see all ${films.size}.") }
        items(displayFilms, key = { it.id }, contentType = { "film" }) { film ->
            // Compute expiry once per item, not every frame
            val expKey = film.expiryDate
            val (exLabel, _, exExpired) = remember(expKey) { expiryStatus(expKey) }
            val inCamera = film.id in activeFilmIds
            FilmCard(film, exLabel, exExpired,
                inCamera = inCamera,
                onTap = { viewingFilm = film },
                onEdit = { editing = film; showSheet = true },
                onDelete = { confirmDelete = film }
            )
        }

        // ── Out of stock (quantity 0) ──────────────────────────────────────
        if (depleted.isNotEmpty()) {
            item(key = "depleted_header") {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { depletedExpanded = !depletedExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (depletedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null, tint = FilmTheme.colors.dim,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Out of stock (${depleted.size})", color = FilmTheme.colors.dim, fontSize = 13.sp)
                }
            }
            if (depletedExpanded) {
                items(depleted, key = { "out_${it.id}" }, contentType = { "film_out" }) { film ->
                    // Same card, rendered as unexposed stock: no accent, no fill.
                    // It still has to be tappable to restock, so it is not hidden.
                    FilmStockCard(
                        stockName = film.name.ifBlank { "Unnamed" },
                        subtitle  = listOf(film.brand, film.type).filter { it.isNotBlank() }
                            .joinToString(" · "),
                        rebate    = rebateLine(film.filmFormat, "out of stock"),
                        dead      = true,
                        onEdit    = { editing = film; showSheet = true },
                        onDelete  = { confirmDelete = film },
                    ) {
                        FilmChip("ISO ${film.iso}", color = FilmTheme.colors.dead)
                        FilmChip("×0", color = FilmTheme.colors.dead)
                    }
                }
            }
        }
        item(key = "spacer") { Spacer(Modifier.height(4.dp)) }
    }

    if (showSheet) FilmSheet(
        ed = editing,
        onDismiss = { showSheet = false; editing = null },
        onSave = { vm.upsertFilm(it); showSheet = false; editing = null },
        vm = vm
    )
    confirmDelete?.let { ConfirmDialog("Delete \"${it.name}\"?", onConfirm = { vm.deleteFilm(it); confirmDelete = null }, onDismiss = { confirmDelete = null }) }
    viewingFilm?.let { film ->
        FilmInfoDialog(film,
            onDismiss = { viewingFilm = null },
            onEdit = { viewingFilm = null; editing = film; showSheet = true },
            onLoad = { viewingFilm = null; loadingFilm = film },
            currency = currency
        )
    }
    loadingFilm?.let { film ->
        val cameras by vm.cameras.collectAsState()
        val lenses  by vm.lenses.collectAsState()
        LoadRollSheet(
            films = films, cameras = cameras, lenses = lenses, rolls = rolls,
            vm = vm, fixedFilm = film,
            onDismiss = { loadingFilm = null },
            onSave = { roll ->
                vm.upsertRoll(roll)
                // Remove film from stash: decrement qty or delete if last one
                if (film.quantity > 1) vm.upsertFilm(film.copy(quantity = film.quantity - 1))
                else vm.upsertFilm(film.copy(quantity = 0))
                loadingFilm = null
            }
        )
    }

    // ── Bulk roll sheets / dialogs ─────────────────────────────────────────
    if (showBulkSheet) {
        BulkRollSheet(
            editing = editingBulk,
            isMetric = isMetric,
            onDismiss = { showBulkSheet = false; editingBulk = null },
            onSave = { vm.upsertBulkRoll(it); showBulkSheet = false; editingBulk = null }
        )
    }
    confirmDeleteBulk?.let { bulk ->
        ConfirmDialog(
            message = "Delete bulk roll \"${bulk.name}\"? This won't remove rolls already loaded from it.",
            onConfirm = { vm.deleteBulkRoll(bulk); confirmDeleteBulk = null },
            onDismiss = { confirmDeleteBulk = null }
        )
    }
    loadingBulk?.let { bulk ->
        LoadFromBulkSheet(
            bulk      = bulk,
            onDismiss = { loadingBulk = null },
            onLoad    = { frames, qty, expiry ->
                vm.cutFromBulk(bulk, frames, qty, expiry)
                loadingBulk = null
            }
        )
    }
}

/**
 * A stock in the stash, as a length of film.
 *
 * First surface on the Dye Layer language — see [FilmStockCard]. The mapping
 * from the old amber card is: brand and process move out of the tag row and
 * into the mono subtitle, storage and format move down onto the rebate as edge
 * printing, and only the facts that change how you'd act on the roll (quantity,
 * expiry, whether it's currently loaded) stay as chips.
 */
@Composable
private fun FilmCard(
    film: FilmStock, exLabel: String, exExpired: Boolean,
    inCamera: Boolean = false,
    onTap: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val colors = FilmTheme.colors
    val stock = rememberStockAccent(film.name, film.type, film.stockAccent)
    // The stock's colour always names the stock. Expiry gets a chip in the mask
    // colour and nothing more: recolouring the title too would say the same
    // thing twice and cost the accent the one job it has, which is letting you
    // tell two stocks apart without reading either of them.
    val accent = stock.solid
    val expiryColor = when {
        exExpired                     -> colors.mask
        exLabel.startsWith("Exp. in") -> colors.yellow
        else                          -> colors.dim
    }

    FilmStockCard(
        stockName = film.name.ifBlank { "Unnamed" },
        spine = stock.verticalBrush(),
        subtitle  = listOf(film.brand, film.type).filter { it.isNotBlank() }.joinToString(" · "),
        // Notes are deliberately NOT on the rebate. Edge printing has to be
        // facts of fixed length, and a note is the one field with no length
        // limit — on a real stash it was the only segment ever reaching the
        // ellipsis, and it got cut at exactly the part worth reading
        // ("PUSH TO 16…"). It gets its own line below instead.
        rebate    = rebateLine(film.filmFormat, "${film.frameCount} exp", film.storage),
        notes     = film.notes,
        accent    = accent,
        onClick   = onTap,
        onEdit    = onEdit,
        onDelete  = onDelete,
    ) {
        FilmChip("ISO ${film.iso}")
        // Quantity is dim, not yellow. Yellow means "attention" in this palette,
        // and having three rolls of something is not a thing to attend to —
        // when it was yellow it out-shouted the expiry chip beside it.
        if (film.quantity > 1) FilmChip("×${film.quantity}")
        if (exLabel.isNotBlank()) FilmChip(exLabel, color = expiryColor)
        if (inCamera) FilmChip("In camera", color = colors.cyan, filled = true)
    }
}

/**
 * Choose a stock's accent, or leave it derived.
 *
 * Swatches rather than a hex field: the point of the accent is that it looks
 * like the box, and nobody knows Velvia's green as a hex triplet. "Auto" is
 * first and is the default, because the lookup table already knows most stocks
 * and the override exists for the long tail it never will.
 */
@Composable
private fun AccentPicker(
    name: String,
    type: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val derived = rememberStockAccent(name, type, "")
    val presets = remember {
        listOf(
            StockAccent(Color(0xFF00E07A), Color(0xFF0066FF)), // slide green→blue
            StockAccent(Color(0xFFFFC26B), Color(0xFFD9342B)), // warm cream→red
            StockAccent(Color(0xFFFFFFFF), Color(0xFF555555)), // monochrome
            StockAccent(Color(0xFF2BC7D4), Color(0xFFE01B3C)), // teal + halation
            StockAccent(Color(0xFFFF6B2C), Color(0xFF8B1A00)), // redscale
            StockAccent(Color(0xFFB86BFF), Color(0xFF4B0F7A)), // purple shift
            StockAccent(Color(0xFFFFD36B), Color(0xFFC07A18)), // amber
            StockAccent(Color(0xFF6FC7D8), Color(0xFF1E3F7A)), // tungsten
        )
    }

    val colors = FilmTheme.colors

    Column {
        Text("Accent", color = colors.dim, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AccentSwatch(
                brush = derived.verticalBrush(),
                selected = selected.isBlank(),
                label = "AUTO",
                onClick = { onSelect("") },
            )
            presets.forEach { preset ->
                val value = preset.toStoredValue()
                // The swatch is painted in the active scheme like everything
                // else. Eight patches of white and green light is exactly the
                // dark adaptation safelight is protecting, and the stored value
                // is unaffected — only what you can see while picking is.
                AccentSwatch(
                    brush = (if (colors.safelight) preset.toSafelight(colors) else preset).verticalBrush(),
                    selected = selected == value,
                    onClick = { onSelect(value) },
                )
            }
        }
        if (colors.safelight) {
            Spacer(Modifier.height(6.dp))
            Text("Safelight is on — these are the real accents shown in red. " +
                "Turn it off to pick by colour.",
                color = colors.dim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AccentSwatch(
    brush: androidx.compose.ui.graphics.Brush,
    selected: Boolean,
    label: String? = null,
    onClick: () -> Unit,
) {
    val colors = FilmTheme.colors
    Box(
        Modifier
            .size(width = if (label != null) 44.dp else 26.dp, height = 26.dp)
            .background(brush)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.halide else colors.edge)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(label, style = FilmTheme.type.rebate, color = colors.void)
        }
    }
}

// ─── Bulk Roll card ───────────────────────────────────────────────────────────

@Composable
fun BulkRollCard(
    bulk: BulkRoll,
    isMetric: Boolean = false,
    onLoad: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = FilmTheme.colors
    val remaining = (bulk.totalFrames - bulk.usedFrames).coerceAtLeast(0)
    // Approx remaining length. 35mm bulk yields ~18×36exp rolls per 100 ft
    // (38 mm frame pitch + per-roll leader/trailer waste) → ≈6.5 usable frames/ft, ≈21/m.
    val perUnit   = if (isMetric) 21.0 else 6.5
    val unitLabel = if (isMetric) "m" else "ft"
    val stock = rememberStockAccent(bulk.name, bulk.type, "")

    FilmStockCard(
        stockName = bulk.name.ifBlank { "Unnamed" },
        subtitle = listOf(bulk.brand, bulk.type).filter { it.isNotBlank() }.joinToString(" · "),
        rebate = rebateLine(
            "bulk",
            "${bulk.totalFrames} frames".takeIf { bulk.totalFrames > 0 },
            bulk.expiryDate.takeIf { it.isNotBlank() }?.let { "exp $it" },
        ),
        notes = bulk.notes,
        accent = if (remaining == 0) colors.dead else stock.solid,
        spine = stock.verticalBrush(),
        dead = remaining == 0,
        onEdit = onEdit,
        onDelete = onDelete,
        footer = {
            if (bulk.totalFrames > 0) {
                Spacer(Modifier.height(10.dp))
                FilmMeter(
                    remaining = remaining,
                    total = bulk.totalFrames,
                    accent = stock.solid,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${bulk.usedFrames} of ${bulk.totalFrames} frames cut".uppercase(),
                    style = FilmTheme.type.rebate,
                    color = colors.dim,
                )
            }
        },
    ) {
        FilmChip("ISO ${bulk.iso}")
        FilmChip(
            "$remaining frames left",
            color = when {
                remaining == 0 -> colors.dead
                remaining < 40 -> colors.mask
                else -> colors.dim
            },
        )
        if (remaining > 0) FilmChip("≈ ${(remaining / perUnit).toInt()} $unitLabel")
        if (remaining > 0) FilmChip("Load roll", color = colors.cyan, filled = true, onClick = onLoad)
    }
}

// ─── Bulk Roll add/edit sheet ─────────────────────────────────────────────────

@Composable
fun BulkRollSheet(
    editing: BulkRoll?,
    isMetric: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (BulkRoll) -> Unit
) {
    var name     by remember { mutableStateOf(editing?.name ?: "") }
    var brand    by remember { mutableStateOf(editing?.brand ?: "") }
    var type     by remember { mutableStateOf(editing?.type ?: Constants.FILM_TYPES[0]) }
    var iso      by remember { mutableStateOf(editing?.iso?.toString() ?: "400") }
    var frames   by remember { mutableStateOf(editing?.totalFrames?.toString() ?: "") }
    var footage  by remember { mutableStateOf("") }   // helper field only — not stored
    var footageMetric by remember { mutableStateOf(isMetric) }  // default unit from settings; false = ft, true = m
    var notes    by remember { mutableStateOf(editing?.notes ?: "") }
    // Locale.US: default-locale formatting writes "12,50" on comma-decimal locales,
    // which then fails to parse on save and silently zeroes the cost.
    var totalCost by remember { mutableStateOf(if ((editing?.totalCost ?: 0.0) > 0.0)
        String.format(java.util.Locale.US, "%.2f", editing!!.totalCost) else "") }
    var purchaseDate by remember { mutableStateOf(editing?.purchaseDate
        ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())) }
    var expiryDate by remember { mutableStateOf(editing?.expiryDate ?: "") }
    var showExpPicker by remember { mutableStateOf(false) }
    var showPurchaseDatePicker by remember { mutableStateOf(false) }
    var pickerYear  by remember { mutableStateOf(
        expiryDate.take(4).toIntOrNull()
            ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) }
    var pickerMonth by remember { mutableIntStateOf(
        expiryDate.drop(5).take(2).toIntOrNull()
            ?: (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1)) }

    // Film name selected from autocomplete → auto-fill brand/ISO/type (same as FilmSheet)
    fun onFilmSelected(filmName: String) {
        name = filmName
        Constants.FILM_METADATA[filmName]?.let { meta ->
            if (brand.isBlank()) brand = meta.first
            iso  = meta.second.toString()
            type = meta.third
        }
    }

    // Footage → frames helper. 35mm bulk yields ~18×36exp rolls per 100 ft
    // (38 mm frame pitch + leader/trailer waste per roll) → ≈6.5 frames/ft, ≈21/m.
    val footageFrames = remember(footage, footageMetric) {
        footage.toIntOrNull()?.let { v -> if (footageMetric) (v * 21) else (v * 6.5).toInt() }
    }

    VaultSheet(if (editing != null) "Edit Bulk Roll" else "Add Bulk Film", onDismiss) {
        AutoCompleteField(name, { onFilmSelected(it) }, "Film Stock", Constants.FILM_DB,
            placeholder = "e.g. Ilford HP5+")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(brand, { brand = it }, "Brand", modifier = Modifier.weight(1f))
            VaultDropdown("ISO", iso, Constants.ISOS.map { it.toString() }, { iso = it },
                modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        VaultDropdown("Film Type", type, Constants.FILM_TYPES, { type = it })
        Spacer(Modifier.height(10.dp))

        // Frame count — enter directly or calculate from footage
        Text("Total Frames in Canister", color = FilmTheme.colors.dim, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom) {
            VaultTextField(frames, { frames = it }, "Frames",
                modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                placeholder = "e.g. 3200")
            Text("or", color = FilmTheme.colors.dim, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 14.dp))
            Column(Modifier.weight(1f)) {
                // Unit toggle: ft / m
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 2.dp)) {
                    listOf("ft" to false, "m" to true).forEach { (label, isMetric) ->
                        val selected = footageMetric == isMetric
                        Box(
                            modifier = Modifier
                                
                                .background(if (selected) FilmTheme.colors.violet else FilmTheme.colors.filmRaised)
                                .border(1.dp, if (selected) FilmTheme.colors.cyan else FilmTheme.colors.edge)
                                .clickable { footageMetric = isMetric; footage = "" }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) { Text(label, color = if (selected) FilmTheme.colors.yellow else FilmTheme.colors.dim, fontSize = 11.sp) }
                    }
                }
                VaultTextField(footage, { footage = it },
                    if (footageMetric) "Length (m)" else "Footage (ft)",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    placeholder = if (footageMetric) "e.g. 30" else "e.g. 100")
                if (footageFrames != null) {
                    Text("≈ $footageFrames frames", color = FilmTheme.colors.cyan, fontSize = 10.sp)
                }
            }
            if (footageFrames != null) {
                VaultButton("Use", small = true, ghost = true,
                    onClick = { frames = footageFrames.toString(); footage = "" })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Purchase Date", color = FilmTheme.colors.dim, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        purchaseDate.ifBlank { "Not set" },
                        color = if (purchaseDate.isBlank()) FilmTheme.colors.dim else FilmTheme.colors.halide,
                        fontSize = 13.sp, modifier = Modifier.weight(1f)
                    )
                    VaultButton("Pick", small = true, ghost = true,
                        onClick = { showPurchaseDatePicker = true })
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Expiry", color = FilmTheme.colors.dim, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (expiryDate.isBlank()) "Not set" else expiryDate,
                        color = if (expiryDate.isBlank()) FilmTheme.colors.dim else FilmTheme.colors.halide,
                        fontSize = 12.sp, modifier = Modifier.weight(1f)
                    )
                    VaultButton("Pick", small = true, ghost = true, onClick = { showExpPicker = true })
                    if (expiryDate.isNotBlank())
                        VaultButton("✕", small = true, ghost = true, onClick = { expiryDate = "" })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2,
            placeholder = "Storage, batch number…")
        Spacer(Modifier.height(10.dp))
        VaultTextField(totalCost, { totalCost = it }, "Total Canister Cost (optional)",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Bulk Roll", modifier = Modifier.fillMaxWidth(), onClick = {
            val totalFrames = frames.toIntOrNull() ?: 0
            onSave(BulkRoll(
                id           = editing?.id ?: uid(),
                name         = name,
                brand        = brand,
                type         = type,
                iso          = iso.toIntOrNull() ?: 400,
                totalFrames  = totalFrames,
                usedFrames   = editing?.usedFrames ?: 0,  // preserve existing usage on edit
                notes        = notes,
                purchaseDate = purchaseDate,
                expiryDate   = expiryDate,
                totalCost    = totalCost.toDecimalOrNull() ?: 0.0
            ))
        })
    }

    if (showExpPicker) {
        MonthYearPickerDialog(
            year = pickerYear, month = pickerMonth,
            onConfirm = { y, m ->
                pickerYear = y; pickerMonth = m
                expiryDate = "%04d-%02d".format(y, m)
                showExpPicker = false
            },
            onDismiss = { showExpPicker = false }
        )
    }
    if (showPurchaseDatePicker) {
        FullDatePickerDialog(
            initialDate = purchaseDate.ifBlank {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            },
            onConfirm = { purchaseDate = it; showPurchaseDatePicker = false },
            onDismiss = { showPurchaseDatePicker = false }
        )
    }
}@Composable
fun LoadFromBulkSheet(
    bulk: BulkRoll,
    onDismiss: () -> Unit,
    onLoad: (frames: Int, quantity: Int, expiryDate: String) -> Unit
) {
    val remaining = (bulk.totalFrames - bulk.usedFrames).coerceAtLeast(0)

    var frames    by remember { mutableStateOf("36") }
    var quantity  by remember { mutableStateOf("1") }
    var expiryDate by remember { mutableStateOf(bulk.expiryDate) }
    var showExpPicker by remember { mutableStateOf(false) }
    var pickerYear  by remember { mutableStateOf(
        expiryDate.take(4).toIntOrNull()
            ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) }
    var pickerMonth by remember { mutableIntStateOf(
        expiryDate.drop(5).take(2).toIntOrNull()
            ?: (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1)) }

    val framesInt   = frames.toIntOrNull() ?: 0
    val quantityInt = quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val totalFramesNeeded = framesInt * quantityInt
    val afterLoad   = (remaining - totalFramesNeeded).coerceAtLeast(0)
    val overBudget  = totalFramesNeeded > remaining

    VaultSheet("Cut rolls from ${bulk.name}", onDismiss) {
        // Bulk roll summary
        TagRow() {
            VaultTag(bulk.type.split(" ").first())
            VaultTag("ISO ${bulk.iso}")
            VaultTag("$remaining frames left", textColor = when {
                remaining <= 36 -> FilmTheme.colors.yellow
                else -> FilmTheme.colors.cyan
            })
        }
        Spacer(Modifier.height(14.dp))

        // Frames per roll — quick picks + custom
        Text("Frames per roll", color = FilmTheme.colors.dim, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            listOf("12", "24", "36").forEach { preset ->
                val selected = frames == preset
                Box(
                    modifier = Modifier
                        
                        .background(if (selected) FilmTheme.colors.violet else FilmTheme.colors.filmRaised)
                        .border(1.dp, if (selected) FilmTheme.colors.cyan else FilmTheme.colors.edge)
                        .clickable { frames = preset }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(preset, color = if (selected) FilmTheme.colors.yellow else FilmTheme.colors.dim, fontSize = 13.sp)
                }
            }
            VaultTextField(
                value = if (frames !in listOf("12","24","36")) frames else "",
                onValueChange = { frames = it },
                label = "Custom",
                modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                placeholder = "e.g. 18"
            )
        }
        Spacer(Modifier.height(12.dp))

        // Number of rolls to cut
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            VaultTextField(quantity, { quantity = it }, "Number of rolls",
                modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                placeholder = "1")
            if (framesInt > 0) {
                Column {
                    Text("Uses $totalFramesNeeded fr", color = FilmTheme.colors.dim, fontSize = 11.sp)
                    Text("$afterLoad fr left", color = when {
                        overBudget -> FilmTheme.colors.mask; afterLoad <= 36 -> FilmTheme.colors.yellow; else -> FilmTheme.colors.cyan
                    }, fontSize = 11.sp)
                }
            }
        }
        if (overBudget) {
            Spacer(Modifier.height(4.dp))
            Text("⚠ Exceeds available footage by ${totalFramesNeeded - remaining} frames",
                color = FilmTheme.colors.mask, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))

        // Expiry date — picker, not manual entry
        Text("Expiry date", color = FilmTheme.colors.dim, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (expiryDate.isBlank()) "Not set" else expiryDate,
                color = if (expiryDate.isBlank()) FilmTheme.colors.dim else FilmTheme.colors.halide,
                fontSize = 13.sp, modifier = Modifier.weight(1f)
            )
            VaultButton("Pick", small = true, ghost = true, onClick = { showExpPicker = true })
            if (expiryDate.isNotBlank()) {
                VaultButton("✕", small = true, ghost = true, onClick = { expiryDate = "" })
            }
        }
        Spacer(Modifier.height(16.dp))

        VaultButton(
            text     = "Add ${if (quantityInt > 1) "$quantityInt rolls" else "roll"} to stash",
            modifier = Modifier.fillMaxWidth(),
            onClick  = {
                if (framesInt > 0 && !overBudget) {
                    onLoad(framesInt, quantityInt, expiryDate)
                }
            }
        )
    }

    if (showExpPicker) {
        MonthYearPickerDialog(
            year = pickerYear, month = pickerMonth,
            onConfirm = { y, m ->
                pickerYear = y; pickerMonth = m
                expiryDate = "%04d-%02d".format(y, m)
                showExpPicker = false
            },
            onDismiss = { showExpPicker = false }
        )
    }
}

// ─── Filter bar ───────────────────────────────────────────────────────────────

@Composable
fun FilterBar(
    sortBy: String, sortOptions: List<String>, onSort: (String) -> Unit,
    filterLabel: String, filterOptions: List<String>, onFilter: (String) -> Unit,
    extraToggle: Pair<String, Boolean>? = null, onExtraToggle: ((Boolean) -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().background(FilmTheme.colors.filmRaised)
            .border(1.dp, FilmTheme.colors.edge).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VaultDropdown("Sort", sortBy, sortOptions, onSort, modifier = Modifier.weight(1f))
            VaultDropdown("Filter", filterLabel, filterOptions, onFilter, modifier = Modifier.weight(1f))
        }
        if (extraToggle != null && onExtraToggle != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = extraToggle.second, onCheckedChange = onExtraToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = FilmTheme.colors.cyan, checkedTrackColor = FilmTheme.colors.violet))
                Spacer(Modifier.width(8.dp))
                Text(extraToggle.first, color = FilmTheme.colors.dim, fontSize = 12.sp)
            }
        }
    }
}

// ─── Camera Stash ─────────────────────────────────────────────────────────────

@Composable
fun CameraStashTab(cameras: List<Camera>, vm: MainViewModel, busyCameraIds: Set<String> = emptySet()) {
    var showSheet     by remember { mutableStateOf(false) }
    var editing       by remember { mutableStateOf<Camera?>(null) }
    var confirmDelete by remember { mutableStateOf<Camera?>(null) }
    var sortBy        by remember { mutableStateOf("Name") }
    var filterFormat  by remember { mutableStateOf("All") }
    var showFilter    by remember { mutableStateOf(false) }

    val formatOptions = listOf("All") + Constants.CAMERA_FORMATS
    val displayCameras = remember(cameras, sortBy, filterFormat) {
        cameras
            .filter { filterFormat == "All" || it.format == filterFormat }
            .sortedWith(when (sortBy) {
                "Brand"     -> compareBy { it.brand }
                "Format"    -> compareBy { it.format }
                "Condition" -> compareBy { it.condition }
                else        -> compareBy { it.name }
            })
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "header") {
            StashHeaderRow(
                shown = displayCameras.size,
                total = cameras.size,
                filterOpen = showFilter,
                filterActive = filterFormat != "All",
                onToggleFilter = { showFilter = !showFilter },
                onAdd = { editing = null; showSheet = true },
            )
        }
        if (showFilter) item(key = "filter") {
            FilterBar(sortBy, listOf("Name", "Brand", "Format", "Condition"), { sortBy = it },
                filterFormat, formatOptions, { filterFormat = it })
        }
        if (displayCameras.isEmpty()) item(key = "empty") { if (cameras.isEmpty()) EmptyState("No cameras yet.", verb = "Add a body", onVerb = { editing = null; showSheet = true })
            else EmptyState("No camera matches this filter.", verb = "Clear the filter to see all ${cameras.size}.") }
        items(displayCameras, key = { it.id }, contentType = { "camera" }) { cam ->
            VaultCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cam.name.ifBlank { "Unnamed" }.uppercase(),
                            style = FilmTheme.type.stock.copy(fontSize = 19.sp),
                            color = FilmTheme.colors.halide,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (cam.brand.isNotBlank()) Text(cam.brand, color = FilmTheme.colors.dim, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        TagRow() {
                            VaultTag(cam.format, textColor = FilmTheme.colors.yellow)
                            VaultTag(cam.condition)
                            if (cam.mount.isNotBlank()) VaultTag(cam.mount)
                            VaultTag(cam.lensSystem)
                            if (cam.id in busyCameraIds) VaultTag("📷 Film loaded", textColor = FilmTheme.colors.violet)
                        }
                        cam.adapterMounts.take(3).let { if (it.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            TagRow() {
                                it.forEach { m -> VaultTag(m, textColor = FilmTheme.colors.violet) }
                            }
                        }}
                    }
                    Row {
                        IconButton(onClick = { editing = cam; showSheet = true }, Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = FilmTheme.colors.dim) }
                        IconButton(onClick = { confirmDelete = cam }, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = FilmTheme.colors.mask.copy(alpha = 0.7f)) }
                    }
                }
            }
        }
        item(key = "spacer") { Spacer(Modifier.height(4.dp)) }
    }
    if (showSheet) CameraSheet(editing, onDismiss = { showSheet = false; editing = null }) { vm.upsertCamera(it); showSheet = false; editing = null }
    confirmDelete?.let { ConfirmDialog("Delete \"${it.name}\"?", onConfirm = { vm.deleteCamera(it); confirmDelete = null }, onDismiss = { confirmDelete = null }) }
}

// ─── Lens Stash ───────────────────────────────────────────────────────────────

@Composable
fun LensStashTab(lenses: List<Lens>, vm: MainViewModel) {
    var showSheet     by remember { mutableStateOf(false) }
    var editing       by remember { mutableStateOf<Lens?>(null) }
    var confirmDelete by remember { mutableStateOf<Lens?>(null) }
    var dofLens       by remember { mutableStateOf<Lens?>(null) }
    var sortBy        by remember { mutableStateOf("Name") }
    var filterMount   by remember { mutableStateOf("All") }
    var showFilter    by remember { mutableStateOf(false) }

    val mountOptions = remember(lenses) { listOf("All") + lenses.map { it.mount }.filter { it.isNotBlank() }.distinct().sorted() }
    val displayLenses = remember(lenses, sortBy, filterMount) {
        lenses
            .filter { filterMount == "All" || it.mount == filterMount }
            .sortedWith(when (sortBy) {
                "Brand"       -> compareBy { it.brand }
                "Focal"       -> compareBy { it.focalLength.toIntOrNull() ?: 0 }
                "Aperture"    -> compareBy { it.maxAperture.toDoubleOrNull() ?: 0.0 }
                "Condition"   -> compareBy { it.condition }
                else          -> compareBy { it.name }
            })
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "header") {
            StashHeaderRow(
                shown = displayLenses.size,
                total = lenses.size,
                filterOpen = showFilter,
                filterActive = filterMount != "All",
                onToggleFilter = { showFilter = !showFilter },
                onAdd = { editing = null; showSheet = true },
            )
        }
        if (showFilter) item(key = "filter") {
            FilterBar(sortBy, listOf("Name", "Brand", "Focal", "Aperture", "Condition"), { sortBy = it },
                filterMount, mountOptions, { filterMount = it })
        }
        if (displayLenses.isEmpty()) item(key = "empty") { if (lenses.isEmpty()) EmptyState("No lenses yet.", verb = "Add a lens", onVerb = { editing = null; showSheet = true })
            else EmptyState("No lens matches this filter.", verb = "Clear the filter to see all ${lenses.size}.") }
        items(displayLenses, key = { it.id }, contentType = { "lens" }) { lens ->
            VaultCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lens.name.ifBlank { "Unnamed" }.uppercase(),
                            style = FilmTheme.type.stock.copy(fontSize = 19.sp),
                            color = FilmTheme.colors.halide,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (lens.brand.isNotBlank()) Text(lens.brand, color = FilmTheme.colors.dim, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        TagRow() {
                            VaultTag("${lens.focalLength}mm", textColor = FilmTheme.colors.yellow)
                            VaultTag("f/${lens.maxAperture}")
                            if (lens.mount.isNotBlank()) VaultTag(lens.mount)
                            VaultTag(lens.condition)
                        }
                    }
                    Row {
                        IconButton(onClick = { dofLens = lens }, Modifier.size(32.dp)) { Icon(Icons.Default.CenterFocusStrong, "DOF calculator", modifier = Modifier.size(16.dp), tint = FilmTheme.colors.cyan) }
                        IconButton(onClick = { editing = lens; showSheet = true }, Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = FilmTheme.colors.dim) }
                        IconButton(onClick = { confirmDelete = lens }, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = FilmTheme.colors.mask.copy(alpha = 0.7f)) }
                    }
                }
            }
        }
        item(key = "spacer") { Spacer(Modifier.height(4.dp)) }
    }
    if (showSheet) LensSheet(editing, onDismiss = { showSheet = false; editing = null }) { vm.upsertLens(it); showSheet = false; editing = null }
    confirmDelete?.let { ConfirmDialog("Delete \"${it.name}\"?", onConfirm = { vm.deleteLens(it); confirmDelete = null }, onDismiss = { confirmDelete = null }) }
    dofLens?.let { DofDialog(it, onDismiss = { dofLens = null }) }
}

// ─── DOF / hyperfocal calculator ─────────────────────────────────────────────

@Composable
fun DofDialog(lens: Lens, onDismiss: () -> Unit) {
    val focal = lens.focalLength.toDoubleOrNull() ?: 50.0
    val maxAp = lens.maxAperture.toDoubleOrNull()
    val apertureOptions = remember(maxAp) {
        Constants.APERTURES.filter { maxAp == null || it >= maxAp - 0.01 }
    }
    fun apLabel(a: Double) = if (a == a.toLong().toDouble()) "f/${a.toLong()}" else "f/$a"
    fun m(v: Double) = "${((v * 100).toInt() / 100.0)} m"   // Double.toString → '.' on all locales

    var aperture  by remember { mutableStateOf(apertureOptions.firstOrNull { it >= 8.0 } ?: apertureOptions.last()) }
    var format    by remember { mutableStateOf("35mm") }
    var distanceM by remember { mutableStateOf(3.0f) }

    val coc = com.analogvault.util.Exposure.cocForFormat(format)
    val hyperM = com.analogvault.util.Exposure.hyperfocalMm(focal, aperture, coc) / 1000.0
    val (near, far) = com.analogvault.util.Exposure.dofNearFar(focal, aperture, distanceM.toDouble(), coc)

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = FilmTheme.colors.filmRaised,
        title = { Text("DOF · ${lens.name.ifBlank { "${focal.toInt()}mm" }}", color = FilmTheme.colors.yellow, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VaultDropdown("Aperture", apLabel(aperture), apertureOptions.map { apLabel(it) },
                        { sel -> sel.removePrefix("f/").toDoubleOrNull()?.let { aperture = it } },
                        modifier = Modifier.weight(1f))
                    VaultDropdown("Format", format, com.analogvault.util.Exposure.DOF_FORMATS,
                        { format = it }, modifier = Modifier.weight(1f))
                }
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Focus distance", color = FilmTheme.colors.dim, fontSize = 12.sp)
                        Text(m(distanceM.toDouble()), color = FilmTheme.colors.cyan, fontSize = 12.sp)
                    }
                    Slider(
                        value = distanceM,
                        onValueChange = { distanceM = ((it * 10).toInt() / 10f) },
                        valueRange = 0.5f..30f,
                        colors = SliderDefaults.colors(thumbColor = FilmTheme.colors.cyan, activeTrackColor = FilmTheme.colors.cyan, inactiveTrackColor = FilmTheme.colors.edge)
                    )
                }
                HorizontalDivider(color = FilmTheme.colors.edge)
                DofRow("In focus", if (far == null) "${m(near)} → ∞" else "${m(near)} → ${m(far)}")
                DofRow("Total DOF", if (far == null) "∞" else m(far - near))
                DofRow("Hyperfocal", "${m(hyperM)} (focus here → ${m(hyperM / 2)} to ∞)")
                Text(
                    "${focal.toInt()}mm on $format · CoC ${coc}mm",
                    color = FilmTheme.colors.dim, fontSize = 10.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = FilmTheme.colors.cyan) } }
    )
}

@Composable
private fun DofRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = FilmTheme.colors.dim, fontSize = 12.sp)
        Text(value, color = FilmTheme.colors.halide, fontSize = 12.sp)
    }
}

// ─── Accessory Stash ──────────────────────────────────────────────────────────

@Composable
fun AccessoryStashTab(accessories: List<Accessory>, vm: MainViewModel) {
    var showSheet     by remember { mutableStateOf(false) }
    var editing       by remember { mutableStateOf<Accessory?>(null) }
    var confirmDelete by remember { mutableStateOf<Accessory?>(null) }
    var sortBy        by remember { mutableStateOf("Name") }
    var filterType    by remember { mutableStateOf("All") }
    var showFilter    by remember { mutableStateOf(false) }

    val typeOptions = listOf("All") + Constants.ACCESSORY_TYPES
    val displayAcc  = remember(accessories, sortBy, filterType) {
        accessories
            .filter { filterType == "All" || it.type == filterType }
            .sortedWith(when (sortBy) {
                "Brand"     -> compareBy { it.brand }
                "Type"      -> compareBy { it.type }
                "Condition" -> compareBy { it.condition }
                else        -> compareBy { it.name }
            })
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "header") {
            StashHeaderRow(
                shown = displayAcc.size,
                total = accessories.size,
                filterOpen = showFilter,
                filterActive = filterType != "All",
                onToggleFilter = { showFilter = !showFilter },
                onAdd = { editing = null; showSheet = true },
            )
        }
        if (showFilter) item(key = "filter") {
            FilterBar(sortBy, listOf("Name", "Brand", "Type", "Condition"), { sortBy = it },
                filterType, typeOptions, { filterType = it })
        }
        if (displayAcc.isEmpty()) item(key = "empty") { if (accessories.isEmpty()) EmptyState("No accessories yet.", verb = "Add one", onVerb = { editing = null; showSheet = true })
            else EmptyState("No accessory matches this filter.", verb = "Clear the filter to see all ${accessories.size}.") }
        items(displayAcc, key = { it.id }, contentType = { "accessory" }) { acc ->
            VaultCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(acc.name.ifBlank { "Unnamed" }.uppercase(),
                            style = FilmTheme.type.stock.copy(fontSize = 19.sp),
                            color = FilmTheme.colors.halide,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        TagRow() {
                            VaultTag(acc.type, textColor = FilmTheme.colors.yellow)
                            if (acc.brand.isNotBlank()) VaultTag(acc.brand)
                            VaultTag(acc.condition)
                        }
                    }
                    Row {
                        IconButton(onClick = { editing = acc; showSheet = true }, Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = FilmTheme.colors.dim) }
                        IconButton(onClick = { confirmDelete = acc }, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = FilmTheme.colors.mask.copy(alpha = 0.7f)) }
                    }
                }
            }
        }
        item(key = "spacer") { Spacer(Modifier.height(4.dp)) }
    }
    if (showSheet) AccessorySheet(editing, onDismiss = { showSheet = false; editing = null }) { vm.upsertAccessory(it); showSheet = false; editing = null }
    confirmDelete?.let { ConfirmDialog("Delete \"${it.name}\"?", onConfirm = { vm.deleteAccessory(it); confirmDelete = null }, onDismiss = { confirmDelete = null }) }
}

// ─── Sheets ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilmSheet(ed: FilmStock?, onDismiss: () -> Unit, onSave: (FilmStock) -> Unit,
              vm: MainViewModel) {
    var name       by remember { mutableStateOf(ed?.name ?: "") }
    var brand      by remember { mutableStateOf(ed?.brand ?: "") }
    var type       by remember { mutableStateOf(ed?.type ?: Constants.FILM_TYPES[0]) }
    var iso        by remember { mutableStateOf(ed?.iso?.toString() ?: "400") }
    var filmFormat by remember { mutableStateOf(ed?.filmFormat?.ifBlank { "135 (35mm)" } ?: "135 (35mm)") }
    var frameCount by remember { mutableStateOf(ed?.frameCount?.toString() ?: "36") }
    var expiry     by remember { mutableStateOf(ed?.expiryDate ?: "") }
    var purchaseDate by remember { mutableStateOf(ed?.purchaseDate ?: "") }
    var storage    by remember { mutableStateOf(ed?.storage ?: Constants.STORAGE_TYPES[0]) }
    var quantity   by remember { mutableStateOf(ed?.quantity?.toString() ?: "1") }
    // Locale.US — see BulkRollSheet.totalCost
    var costPerRoll by remember { mutableStateOf(if ((ed?.costPerRoll ?: 0.0) > 0.0)
        String.format(java.util.Locale.US, "%.2f", ed!!.costPerRoll) else "") }
    var stockAccent by remember { mutableStateOf(ed?.stockAccent ?: "") }
    val customIsos by vm.customIsos.collectAsState()
    val allIsos = remember(customIsos) { (Constants.ISOS + customIsos).distinct().sorted() }
    var showAddIsoDialog by remember { mutableStateOf(false) }
    var customIsoInput by remember { mutableStateOf("") }

    if (showAddIsoDialog) {
        AlertDialog(
            onDismissRequest = { showAddIsoDialog = false; customIsoInput = "" },
            containerColor = FilmTheme.colors.filmRaised,
            title = { Text("Add custom ISO", color = FilmTheme.colors.yellow) },
            text = {
                VaultTextField(customIsoInput, { customIsoInput = it.filter(Char::isDigit) },
                    "ISO value", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            },
            confirmButton = {
                TextButton(onClick = {
                    customIsoInput.toIntOrNull()?.let { v ->
                        vm.addCustomIso(v)
                        iso = v.toString()
                    }
                    showAddIsoDialog = false; customIsoInput = ""
                }) { Text("Add", color = FilmTheme.colors.cyan) }
            },
            dismissButton = {
                TextButton(onClick = { showAddIsoDialog = false; customIsoInput = "" }) {
                    Text("Cancel", color = FilmTheme.colors.dim)
                }
            }
        )
    }
    var notes      by remember { mutableStateOf(ed?.notes ?: "") }

    // Expiry month/year picker + purchase date picker state
    var showExpPicker by remember { mutableStateOf(false) }
    var showPurchaseDatePicker by remember { mutableStateOf(false) }
    var pickerYear    by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) }
    var pickerMonth   by remember { mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1) }

    val is135 = filmFormat == "135 (35mm)"
    val is120 = filmFormat == "120"

    fun onFilmSelected(filmName: String) {
        name = filmName
        Constants.FILM_METADATA[filmName]?.let { meta ->
            if (brand.isBlank()) brand = meta.first
            iso = meta.second.toString()
            type = meta.third
        }
    }

    VaultSheet(if (ed != null) "Edit Film" else "Add Film", onDismiss) {
        AutoCompleteField(name, { onFilmSelected(it) }, "Film Stock", Constants.FILM_DB, placeholder = "e.g. Kodak Portra 400")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(brand, { brand = it }, "Brand", modifier = Modifier.weight(1f))
            VaultTextField(quantity, { quantity = it }, "Qty", modifier = Modifier.weight(0.4f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        }
        Spacer(Modifier.height(10.dp))
        VaultDropdown("Type", type, Constants.FILM_TYPES, { type = it })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            VaultDropdown("ISO", iso, allIsos.map { it.toString() }, { iso = it }, modifier = Modifier.weight(1f))
            VaultButton("+", small = true, ghost = true, onClick = { showAddIsoDialog = true })
            VaultDropdown("Format", filmFormat, FILM_FORMATS_DISPLAY, {
                filmFormat = it
                // Reset frame count to sensible default when format changes
                frameCount = when (it) {
                    "135 (35mm)" -> "36"
                    "120"        -> "12"
                    "220"        -> "24"
                    else         -> frameCount
                }
            }, modifier = Modifier.weight(1f))
        }

        // ── Frame count picker (format-aware) ────────────────────────────────
        if (is135 || is120) {
            Spacer(Modifier.height(10.dp))
            Text("Frames per roll", color = FilmTheme.colors.dim, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            if (is135) {
                // Quick-pick 12 / 24 / 36 + custom
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Constants.FRAMES_135.forEach { preset ->
                        val sel = frameCount == preset.toString()
                        Box(
                            modifier = Modifier
                                
                                .background(if (sel) FilmTheme.colors.violet else FilmTheme.colors.filmRaised)
                                .border(1.dp, if (sel) FilmTheme.colors.cyan else FilmTheme.colors.edge)
                                .clickable { frameCount = preset.toString() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(preset.toString(), color = if (sel) FilmTheme.colors.yellow else FilmTheme.colors.dim, fontSize = 13.sp) }
                    }
                    VaultTextField(
                        value = if (frameCount !in listOf("12","24","36")) frameCount else "",
                        onValueChange = { frameCount = it },
                        label = "Custom",
                        modifier = Modifier.weight(1f),
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        placeholder = ""
                    )
                }
            } else {
                // 120 — show MF format presets with frame count labels
                val mfOptions = Constants.MF_FRAME_COUNTS.entries.toList()
                TagRow() {
                    mfOptions.forEach { (fmt, n) ->
                        val sel = frameCount == n.toString()
                        Box(
                            modifier = Modifier
                                
                                .background(if (sel) FilmTheme.colors.violet else FilmTheme.colors.filmRaised)
                                .border(1.dp, if (sel) FilmTheme.colors.cyan else FilmTheme.colors.edge)
                                .clickable { frameCount = n.toString() }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(fmt, color = if (sel) FilmTheme.colors.yellow else FilmTheme.colors.dim, fontSize = 12.sp)
                                Text("$n fr", color = if (sel) FilmTheme.colors.cyan else FilmTheme.colors.dim, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Expiry: show picker button + text field
            Column(Modifier.weight(1f)) {
                Text("Expiry", color = FilmTheme.colors.dim, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (expiry.isBlank()) "Not set" else expiry, color = if (expiry.isBlank()) FilmTheme.colors.dim else FilmTheme.colors.halide, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    VaultButton("Pick", small = true, ghost = true, onClick = { showExpPicker = true })
                    if (expiry.isNotBlank()) VaultButton("✕", small = true, ghost = true, onClick = { expiry = "" })
                }
            }
            VaultDropdown("Storage", storage, Constants.STORAGE_TYPES, { storage = it }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        // Purchase date — optional, uses the same date picker as the rest of the app
        Column {
            Text("Purchase Date", color = FilmTheme.colors.dim, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (purchaseDate.isBlank()) "Not set" else formatDate(purchaseDate),
                    color = if (purchaseDate.isBlank()) FilmTheme.colors.dim else FilmTheme.colors.halide,
                    fontSize = 13.sp, modifier = Modifier.weight(1f))
                VaultButton("Pick", small = true, ghost = true, onClick = { showPurchaseDatePicker = true })
                if (purchaseDate.isNotBlank()) VaultButton("✕", small = true, ghost = true, onClick = { purchaseDate = "" })
            }
        }
        Spacer(Modifier.height(10.dp))
        VaultTextField(costPerRoll, { costPerRoll = it }, "Cost per Roll (optional)",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Spacer(Modifier.height(14.dp))
        AccentPicker(
            name = name,
            type = type,
            selected = stockAccent,
            onSelect = { stockAccent = it },
        )
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Film", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(FilmStock(
                id          = ed?.id ?: uid(),
                name        = name,
                brand       = brand,
                type        = type,
                iso         = iso.toIntOrNull() ?: 400,
                shots       = frameCount.toIntOrNull() ?: 36,  // keep legacy field in sync
                filmFormat  = filmFormat,
                frameCount  = frameCount.toIntOrNull() ?: 36,
                expiryDate  = expiry,
                purchaseDate = purchaseDate,
                storage     = storage,
                quantity    = quantity.toIntOrNull() ?: 1,
                notes       = notes,
                costPerRoll = costPerRoll.toDecimalOrNull() ?: 0.0,
                stockAccent = stockAccent
            ))
        })
    }

    if (showExpPicker) {
        MonthYearPickerDialog(
            year = pickerYear, month = pickerMonth,
            onConfirm = { y, m ->
                pickerYear = y; pickerMonth = m
                expiry = "%04d-%02d".format(y, m)
                showExpPicker = false
            },
            onDismiss = { showExpPicker = false }
        )
    }
    if (showPurchaseDatePicker) {
        FullDatePickerDialog(
            initialDate = purchaseDate.ifBlank {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            },
            includeTime = false,
            onConfirm = { purchaseDate = it; showPurchaseDatePicker = false },
            onDismiss = { showPurchaseDatePicker = false }
        )
    }
}

// ─── Month/Year Picker ────────────────────────────────────────────────────────

@Composable
fun MonthYearPickerDialog(year: Int, month: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var selYear  by remember { mutableIntStateOf(year) }
    var selMonth by remember { mutableIntStateOf(month) }
    val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    val monthOptions = months.mapIndexed { i, m -> m to (i + 1) }
    val nowYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
    // Same wide range as FullDatePickerDialog (vintage film .. far future expiry).
    val years = remember(nowYear) { 1950..(nowYear + 30) }
    val yearOptions = remember(years) { years.map { it.toString() to it } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FilmTheme.colors.filmRaised,
        title = { Text("Expiry Date", color = FilmTheme.colors.yellow) },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpinnerField(
                    label = "Month", value = months[selMonth - 1],
                    onInc = { selMonth = if (selMonth >= 12) 1 else selMonth + 1 },
                    onDec = { selMonth = if (selMonth <= 1) 12 else selMonth - 1 },
                    pickerOptions = monthOptions, onPick = { selMonth = it },
                    modifier = Modifier.weight(1f)
                )
                SpinnerField(
                    label = "Year", value = selYear.toString(),
                    onInc = { if (selYear < years.last) selYear++ },
                    onDec = { if (selYear > years.first) selYear-- },
                    pickerOptions = yearOptions, onPick = { selYear = it },
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selYear, selMonth) }) { Text("Set", color = FilmTheme.colors.cyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = FilmTheme.colors.dim) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CameraSheet(ed: Camera?, onDismiss: () -> Unit, onSave: (Camera) -> Unit) {
    var name       by remember { mutableStateOf(ed?.name ?: "") }
    var brand      by remember { mutableStateOf(ed?.brand ?: "") }
    var format     by remember { mutableStateOf(ed?.format ?: "35mm") }
    var mfFormat   by remember { mutableStateOf(ed?.mfFormat ?: "") }
    var lensSystem by remember { mutableStateOf(ed?.lensSystem ?: "fixed") }
    var condition  by remember { mutableStateOf(ed?.condition ?: "Good") }
    var mount      by remember { mutableStateOf(ed?.mount ?: "") }
    var adapters   by remember { mutableStateOf(ed?.adapterMounts ?: emptyList<String>()) }
    var notes      by remember { mutableStateOf(ed?.notes ?: "") }
    var showAdapters by remember { mutableStateOf(adapters.isNotEmpty()) }
    var fastest    by remember { mutableStateOf(ed?.fastestShutter ?: "") }
    var slowest    by remember { mutableStateOf(ed?.slowestShutter ?: "") }
    var hasBulb    by remember { mutableStateOf(ed?.hasBulb ?: false) }

    fun onCameraSelected(modelName: String) {
        name = modelName
        Constants.CAMERA_METADATA[modelName]?.let { (b, f) ->
            if (brand.isBlank()) brand = b
            format = f
            if (f != "120 (MF)") mfFormat = ""
        }
    }

    VaultSheet(if (ed != null) "Edit Camera" else "Add Camera", onDismiss) {
        AutoCompleteField(name, { onCameraSelected(it) }, "Model", Constants.CAMERA_DB, placeholder = "e.g. Nikon F3")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(brand, { brand = it }, "Brand", modifier = Modifier.weight(1f))
            VaultDropdown("Format", format, Constants.CAMERA_FORMATS, {
                format = it; if (it != "120 (MF)") mfFormat = ""
            }, modifier = Modifier.weight(1f))
        }

        // MF shooting format — only for 120 cameras
        if (format == "120 (MF)") {
            Spacer(Modifier.height(10.dp))
            Text("Shooting format", color = FilmTheme.colors.dim, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            TagRow() {
                Constants.MF_FORMATS.forEach { fmt ->
                    val frames = Constants.MF_FRAME_COUNTS[fmt] ?: 0
                    val sel = mfFormat == fmt
                    Box(
                        modifier = Modifier
                            
                            .background(if (sel) FilmTheme.colors.violet else FilmTheme.colors.filmRaised)
                            .border(1.dp, if (sel) FilmTheme.colors.cyan else FilmTheme.colors.edge)
                            .clickable { mfFormat = fmt }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(fmt, color = if (sel) FilmTheme.colors.yellow else FilmTheme.colors.dim, fontSize = 12.sp)
                            Text("$frames fr", color = if (sel) FilmTheme.colors.cyan else FilmTheme.colors.dim, fontSize = 10.sp)
                        }
                    }
                }
            }
            if (mfFormat.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Loading a 120 roll will default to ${Constants.MF_FRAME_COUNTS[mfFormat]} frames",
                    color = FilmTheme.colors.dim, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultDropdown("Lens System", lensSystem, listOf("fixed","interchangeable"), { lensSystem = it }, modifier = Modifier.weight(1f))
            VaultDropdown("Condition", condition, Constants.CONDITIONS, { condition = it }, modifier = Modifier.weight(1f))
        }
        if (lensSystem == "interchangeable") {
            Spacer(Modifier.height(10.dp))
            VaultDropdown("Native Mount", mount, listOf("") + Constants.COMMON_MOUNTS, { mount = it })
            if (mount.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showAdapters = !showAdapters }) {
                    Text("🔧 ${if (showAdapters) "Hide" else "Add"} Adapter Mounts${if (adapters.isNotEmpty()) " (${adapters.size})" else ""}", color = FilmTheme.colors.cyan, fontSize = 12.sp)
                }
                if (showAdapters) {
                    val options = Constants.MOUNT_GROUPS[mount]?.adapters ?: Constants.COMMON_MOUNTS.filter { it != mount }
                    TagRow() {
                        options.forEach { m ->
                            val on = adapters.contains(m)
                            FilterChip(selected = on, onClick = { adapters = if (on) adapters - m else adapters + m },
                                label = { Text(m, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FilmTheme.colors.violet, selectedLabelColor = FilmTheme.colors.halide,
                                    containerColor = FilmTheme.colors.filmRaised, labelColor = FilmTheme.colors.dim))
                        }
                    }
                }
            }
        }
        // Shutter range. Left blank the meter suggests whatever the light asks
        // for, which is the old behaviour; filled in it stops offering speeds the
        // dial does not have.
        Spacer(Modifier.height(14.dp))
        Text("Shutter range", color = FilmTheme.colors.dim, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultDropdown("Fastest", fastest.ifBlank { UNKNOWN_LIMIT },
                listOf(UNKNOWN_LIMIT) + Constants.SHUTTER_SPEEDS.filter { it != "B" },
                { fastest = if (it == UNKNOWN_LIMIT) "" else it }, modifier = Modifier.weight(1f))
            VaultDropdown("Slowest", slowest.ifBlank { UNKNOWN_LIMIT },
                listOf(UNKNOWN_LIMIT) + Constants.SHUTTER_SPEEDS.filter { it != "B" },
                { slowest = if (it == UNKNOWN_LIMIT) "" else it }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bulb", color = FilmTheme.colors.dim, fontSize = 13.sp)
                Text("Lets the meter suggest holding past the slowest speed",
                    color = FilmTheme.colors.dim, fontSize = 10.sp)
            }
            Switch(checked = hasBulb, onCheckedChange = { hasBulb = it },
                colors = SwitchDefaults.colors(checkedThumbColor = FilmTheme.colors.cyan, checkedTrackColor = FilmTheme.colors.violet))
        }

        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Camera", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(Camera(id = ed?.id ?: uid(), name = name, brand = brand, format = format,
                mfFormat = mfFormat, lensSystem = lensSystem, condition = condition,
                mount = mount, adapterMounts = adapters, notes = notes,
                fastestShutter = fastest, slowestShutter = slowest, hasBulb = hasBulb))
        })
    }
}

@Composable
fun LensSheet(ed: Lens?, onDismiss: () -> Unit, onSave: (Lens) -> Unit) {
    var name        by remember { mutableStateOf(ed?.name ?: "") }
    var brand       by remember { mutableStateOf(ed?.brand ?: "") }
    var focalLength by remember { mutableStateOf(ed?.focalLength ?: "50") }
    var maxAperture by remember { mutableStateOf(ed?.maxAperture ?: "1.8") }
    var minAperture by remember { mutableStateOf(ed?.minAperture ?: "") }
    var mount       by remember { mutableStateOf(ed?.mount ?: "") }
    var condition   by remember { mutableStateOf(ed?.condition ?: "Good") }

    VaultSheet(if (ed != null) "Edit Lens" else "Add Lens", onDismiss) {
        AutoCompleteField(name, { name = it }, "Lens Name", Constants.LENS_DB, placeholder = "e.g. Nikkor 50mm f/1.8")
        Spacer(Modifier.height(10.dp))
        VaultTextField(brand, { brand = it }, "Brand")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(focalLength, { focalLength = it }, "Focal (mm)", modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            VaultTextField(maxAperture, { maxAperture = it }, "Max Aperture", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultDropdown("Mount", mount, listOf("") + Constants.COMMON_MOUNTS, { mount = it }, modifier = Modifier.weight(1f))
            VaultDropdown("Condition", condition, Constants.LENS_CONDITIONS, { condition = it }, modifier = Modifier.weight(1f))
        }
        // The far end of the ring. Left unknown the meter never stripes a
        // stopped-down rung, because guessing f/16 on a lens that goes to f/22
        // would rule out a frame the lens can take.
        Spacer(Modifier.height(10.dp))
        VaultDropdown("Min Aperture (stops down to)", minAperture.ifBlank { UNKNOWN_LIMIT },
            listOf(UNKNOWN_LIMIT) + Constants.APERTURES.filter { it >= 4.0 }.map { apertureNum(it) },
            { minAperture = if (it == UNKNOWN_LIMIT) "" else it })
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Lens", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(Lens(id = ed?.id ?: uid(), name = name, brand = brand, focalLength = focalLength,
                maxAperture = maxAperture, minAperture = minAperture, mount = mount,
                condition = condition))
        })
    }
}

@Composable
fun AccessorySheet(ed: Accessory?, onDismiss: () -> Unit, onSave: (Accessory) -> Unit) {
    var name      by remember { mutableStateOf(ed?.name ?: "") }
    var type      by remember { mutableStateOf(ed?.type ?: Constants.ACCESSORY_TYPES[0]) }
    var brand     by remember { mutableStateOf(ed?.brand ?: "") }
    var condition by remember { mutableStateOf(ed?.condition ?: "Good") }
    var notes     by remember { mutableStateOf(ed?.notes ?: "") }

    VaultSheet(if (ed != null) "Edit Accessory" else "Add Accessory", onDismiss) {
        VaultTextField(name, { name = it }, "Name", placeholder = "Sekonic L-308X")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultDropdown("Type", type, Constants.ACCESSORY_TYPES, { type = it }, modifier = Modifier.weight(1f))
            VaultTextField(brand, { brand = it }, "Brand", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        VaultDropdown("Condition", condition, Constants.CONDITIONS, { condition = it })
        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Spacer(Modifier.height(16.dp))
        VaultButton("Save", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(Accessory(id = ed?.id ?: uid(), name = name, type = type, brand = brand, condition = condition, notes = notes))
        })
    }
}

// ─── Film Info Dialog ─────────────────────────────────────────────────────────

@Composable
fun FilmInfoDialog(film: FilmStock, onDismiss: () -> Unit, onEdit: () -> Unit,
                   onLoad: (() -> Unit)? = null, currency: String = "€") {
    val expKey = film.expiryDate
    val (exLabel, exLevel, _) = expiryStatus(expKey)
    val exColor = expiryColor(exLevel)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = FilmTheme.colors.filmRaised,
        title = { Text(film.name.ifBlank { "Film" }, color = FilmTheme.colors.yellow) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (film.brand.isNotBlank()) InfoRow("Brand", film.brand)
                InfoRow("Type", film.type)
                InfoRow("ISO", film.iso.toString())
                InfoRow("Storage", film.storage)
                if (film.quantity > 1) InfoRow("Quantity", "${film.quantity} rolls")
                if (film.costPerRoll > 0.0) InfoRow("Cost/Roll", "${currency}%.2f".format(film.costPerRoll))
                if (exLabel.isNotBlank()) TagRow() {
                    Text("Expiry", color = FilmTheme.colors.dim, fontSize = 12.sp)
                    VaultTag(exLabel, textColor = exColor)
                }
                if (film.notes.isNotBlank()) Text(film.notes, color = FilmTheme.colors.dim, fontSize = 12.sp)
            }
        },
        confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onLoad != null) VaultButton("Load Film", small = true, onClick = onLoad)
            else VaultTag("📷 In Camera", textColor = FilmTheme.colors.violet)
            VaultButton("Edit", small = true, ghost = true, onClick = onEdit)
        }},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = FilmTheme.colors.dim) } }
    )
}

@Composable private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = FilmTheme.colors.dim, fontSize = 12.sp, modifier = Modifier.width(80.dp))
        Text(value, color = FilmTheme.colors.halide, fontSize = 12.sp)
    }
}

// Loading film into a camera (from stash or the Loaded tab) uses the shared
// LoadRollSheet(fixedFilm = …) in ActiveScreen.kt — one consistent flow with a date
// picker, exposure count and shoot-at-ISO (incl. custom ISOs).
