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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.analogvault.data.model.Roll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.data.model.*
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*
import com.analogvault.ui.uid
import com.analogvault.util.Constants
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch

// ─── Format options (replaces "shots") ───────────────────────────────────────
val FILM_FORMATS_DISPLAY = listOf("135 (35mm)", "120", "220", "4x5", "8x10", "Super 8", "110", "126", "Instant")

@Composable
fun StashScreen(vm: MainViewModel) {
    // Collect once at top level — each tab only reads what it needs
    val films       by vm.films.collectAsState()
    val cameras     by vm.cameras.collectAsState()
    val lenses      by vm.lenses.collectAsState()
    val accessories by vm.accessories.collectAsState()

    val tabs = listOf("Film", "Cameras", "Lenses", "Accessories")
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Bg2,
            contentColor = Amber,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]), color = Amber
                )
            }
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text = { Text(t, fontSize = 12.sp) },
                    selectedContentColor = Amber,
                    unselectedContentColor = TextTertiary)
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> FilmStashTab(films, vm)
                1 -> CameraStashTab(cameras, vm)
                2 -> LensStashTab(lenses, vm)
                3 -> AccessoryStashTab(accessories, vm)
            }
        }
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
    val rolls         by vm.rolls.collectAsState()
    val bulkRolls     by vm.bulkRolls.collectAsState()
    var showBulkSheet   by remember { mutableStateOf(false) }
    var editingBulk     by remember { mutableStateOf<BulkRoll?>(null) }
    var loadingBulk     by remember { mutableStateOf<BulkRoll?>(null) }
    var confirmDeleteBulk by remember { mutableStateOf<BulkRoll?>(null) }

    // Films currently in camera (not yet developed)
    val activeFilmIds by remember { derivedStateOf {
        rolls.filter { !it.developed }.map { it.filmId }.toSet()
    }}

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
                (filterType == "All" || f.type == filterType) &&
                (!filterExp || run {
                    val exp = f.expiryDate.let { if (it.length == 7) "$it-01" else it }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Film Stash", color = Amber, fontSize = 18.sp)
                Text("${displayFilms.size}/${films.size}", color = TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showFilter = !showFilter }, Modifier.size(36.dp)) {
                    Icon(Icons.Default.FilterList, null, modifier = Modifier.size(18.dp), tint = if (showFilter || filterType != "All" || filterExp) Amber else TextTertiary)
                }
                VaultButton("+ Add", small = true, onClick = { editing = null; showSheet = true })
            }
        }

        // ── Bulk Film section ──────────────────────────────────────────────
        item(key = "bulk_header") {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Bulk Film", color = AmberBright, fontSize = 13.sp)
                VaultButton("+ Bulk", small = true, ghost = true,
                    onClick = { editingBulk = null; showBulkSheet = true })
            }
            Spacer(Modifier.height(6.dp))
        }
        if (bulkRolls.isEmpty()) {
            item(key = "bulk_empty") {
                Text("No bulk film tracked yet", color = TextTertiary, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        items(bulkRolls, key = { "bulk_${it.id}" }, contentType = { "bulk" }) { bulk ->
            BulkRollCard(
                bulk = bulk,
                onLoad   = { loadingBulk = bulk },
                onEdit   = { editingBulk = bulk; showBulkSheet = true },
                onDelete = { confirmDeleteBulk = bulk }
            )
        }

        // ── Divider before regular stash ──────────────────────────────────
        item(key = "stash_divider") {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(Modifier.weight(1f), color = Border)
                Text("Individual Rolls", color = TextTertiary, fontSize = 11.sp)
                HorizontalDivider(Modifier.weight(1f), color = Border)
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
        if (displayFilms.isEmpty()) item(key = "empty") { EmptyState(if (films.isEmpty()) "No film stocks yet" else "No films match filter") }
        items(displayFilms, key = { it.id }, contentType = { "film" }) { film ->
            // Compute expiry once per item, not every frame
            val expKey = remember(film.expiryDate) { film.expiryDate.let { if (it.length == 7) "$it-01" else it } }
            val (exLabel, exColor, _) = remember(expKey) { expiryStatus(expKey) }
            val inCamera = film.id in activeFilmIds
            FilmCard(film, exLabel, exColor,
                inCamera = inCamera,
                onTap = { viewingFilm = film },
                onEdit = { editing = film; showSheet = true },
                onDelete = { confirmDelete = film }
            )
        }
        item(key = "spacer") { Spacer(Modifier.height(4.dp)) }
    }

    if (showSheet) FilmSheet(editing, onDismiss = { showSheet = false; editing = null }) { vm.upsertFilm(it); showSheet = false; editing = null }
    confirmDelete?.let { ConfirmDialog("Delete \"${it.name}\"?", onConfirm = { vm.deleteFilm(it); confirmDelete = null }, onDismiss = { confirmDelete = null }) }
    viewingFilm?.let { film ->
        val inCamera = film.id in activeFilmIds
        FilmInfoDialog(film,
            onDismiss = { viewingFilm = null },
            onEdit = { viewingFilm = null; editing = film; showSheet = true },
            onLoad = if (!inCamera) {{ viewingFilm = null; loadingFilm = film }} else null
        )
    }
    loadingFilm?.let { film ->
        val cameras by vm.cameras.collectAsState()
        val lenses  by vm.lenses.collectAsState()
        LoadRollSheetFromStash(
            film = film,
            cameras = cameras,
            lenses = lenses,
            rolls = rolls,
            onDismiss = { loadingFilm = null },
            onSave = { roll -> vm.upsertRoll(roll); loadingFilm = null }
        )
    }

    // ── Bulk roll sheets / dialogs ─────────────────────────────────────────
    if (showBulkSheet) {
        BulkRollSheet(
            editing = editingBulk,
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

@Composable
private fun FilmCard(
    film: FilmStock, exLabel: String, exColor: androidx.compose.ui.graphics.Color,
    inCamera: Boolean = false,
    onTap: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    VaultCard(onClick = onTap) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(film.name.ifBlank { "Unnamed" }, color = TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (film.brand.isNotBlank()) Text(film.brand, color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    VaultTag(film.type.split(" ").first())
                    VaultTag("ISO ${film.iso}")
                    if (inCamera) VaultTag("📷 In Camera", textColor = BlueInfo)
                    if (film.quantity > 1) VaultTag("×${film.quantity}", textColor = AmberBright)
                    if (exLabel.isNotBlank()) VaultTag(exLabel, textColor = exColor)
                }
                if (film.notes.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(film.notes, color = TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row {
                IconButton(onClick = onEdit, Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                }
                IconButton(onClick = onDelete, Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = RedErr.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// ─── Bulk Roll card ───────────────────────────────────────────────────────────

@Composable
fun BulkRollCard(
    bulk: BulkRoll,
    onLoad: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val remaining = (bulk.totalFrames - bulk.usedFrames).coerceAtLeast(0)
    val pct       = if (bulk.totalFrames > 0) bulk.usedFrames.toFloat() / bulk.totalFrames else 0f
    val barColor  = when {
        pct >= 0.9f -> RedErr
        pct >= 0.65f -> OrangeWarn
        else        -> GreenOk
    }

    VaultCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(bulk.name.ifBlank { "Unnamed" }, color = TextPrimary, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (bulk.brand.isNotBlank()) Text(bulk.brand, color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    VaultTag(bulk.type.split(" ").first())
                    VaultTag("ISO ${bulk.iso}")
                    VaultTag(
                        text = "$remaining frames left",
                        textColor = barColor
                    )
                }
                if (bulk.expiryDate.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text("Exp: ${bulk.expiryDate}", color = TextTertiary, fontSize = 10.sp)
                }
                if (bulk.totalFrames > 0) {
                    Spacer(Modifier.height(6.dp))
                    VaultProgressBar(pct, color = barColor)
                    Spacer(Modifier.height(2.dp))
                    Text("${bulk.usedFrames} / ${bulk.totalFrames} frames used",
                        color = TextTertiary, fontSize = 10.sp)
                }
                if (bulk.notes.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(bulk.notes, color = TextTertiary, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    IconButton(onClick = onEdit, Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                    }
                    IconButton(onClick = onDelete, Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = RedErr.copy(alpha = 0.7f))
                    }
                }
                if (remaining > 0) {
                    VaultButton("⬇ Load Roll", small = true, onClick = onLoad)
                } else {
                    VaultTag("Exhausted", textColor = RedErr)
                }
            }
        }
    }
}

// ─── Bulk Roll add/edit sheet ─────────────────────────────────────────────────

@Composable
fun BulkRollSheet(
    editing: BulkRoll?,
    onDismiss: () -> Unit,
    onSave: (BulkRoll) -> Unit
) {
    var name     by remember { mutableStateOf(editing?.name ?: "") }
    var brand    by remember { mutableStateOf(editing?.brand ?: "") }
    var type     by remember { mutableStateOf(editing?.type ?: Constants.FILM_TYPES[0]) }
    var iso      by remember { mutableStateOf(editing?.iso?.toString() ?: "400") }
    var frames   by remember { mutableStateOf(editing?.totalFrames?.toString() ?: "") }
    var footage  by remember { mutableStateOf("") }   // helper field only — not stored
    var footageMetric by remember { mutableStateOf(false) }  // false = ft, true = m
    var notes    by remember { mutableStateOf(editing?.notes ?: "") }
    var purchaseDate by remember { mutableStateOf(editing?.purchaseDate
        ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())) }
    var expiryDate by remember { mutableStateOf(editing?.expiryDate ?: "") }
    var showExpPicker by remember { mutableStateOf(false) }
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

    // Footage → frames helper
    // 35mm: 1ft ≈ 32 usable frames at 36mm spacing; 1m ≈ 105 frames
    val footageFrames = remember(footage, footageMetric) {
        footage.toIntOrNull()?.let { v -> if (footageMetric) (v * 105) else (v * 32) }
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
        Text("Total Frames in Canister", color = TextTertiary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom) {
            VaultTextField(frames, { frames = it }, "Frames",
                modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                placeholder = "e.g. 3200")
            Text("or", color = TextTertiary, fontSize = 12.sp,
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
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selected) AmberDark else Bg3)
                                .border(1.dp, if (selected) Amber else Border, RoundedCornerShape(4.dp))
                                .clickable { footageMetric = isMetric; footage = "" }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) { Text(label, color = if (selected) AmberBright else TextTertiary, fontSize = 11.sp) }
                    }
                }
                VaultTextField(footage, { footage = it },
                    if (footageMetric) "Length (m)" else "Footage (ft)",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    placeholder = if (footageMetric) "e.g. 30" else "e.g. 100")
                if (footageFrames != null) {
                    Text("≈ $footageFrames frames", color = Amber, fontSize = 10.sp)
                }
            }
            if (footageFrames != null) {
                VaultButton("Use", small = true, ghost = true,
                    onClick = { frames = footageFrames.toString(); footage = "" })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(purchaseDate, { purchaseDate = it }, "Purchase Date",
                placeholder = "YYYY-MM-DD", modifier = Modifier.weight(1f))
            Column(Modifier.weight(1f)) {
                Text("Expiry", color = TextTertiary, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (expiryDate.isBlank()) "Not set" else expiryDate,
                        color = if (expiryDate.isBlank()) TextTertiary else TextPrimary,
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
                expiryDate   = expiryDate
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
}

// ─── Load Roll from Bulk sheet ────────────────────────────────────────────────

@Composable
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VaultTag(bulk.type.split(" ").first())
            VaultTag("ISO ${bulk.iso}")
            VaultTag("$remaining frames left", textColor = when {
                remaining <= 36 -> OrangeWarn
                else -> GreenOk
            })
        }
        Spacer(Modifier.height(14.dp))

        // Frames per roll — quick picks + custom
        Text("Frames per roll", color = TextTertiary, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            listOf("12", "24", "36").forEach { preset ->
                val selected = frames == preset
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) AmberDark else Bg3)
                        .border(1.dp, if (selected) Amber else Border, RoundedCornerShape(6.dp))
                        .clickable { frames = preset }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(preset, color = if (selected) AmberBright else TextSecondary, fontSize = 13.sp)
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
                    Text("Uses $totalFramesNeeded fr", color = TextSecondary, fontSize = 11.sp)
                    Text("$afterLoad fr left", color = when {
                        overBudget -> RedErr; afterLoad <= 36 -> OrangeWarn; else -> GreenOk
                    }, fontSize = 11.sp)
                }
            }
        }
        if (overBudget) {
            Spacer(Modifier.height(4.dp))
            Text("⚠ Exceeds available footage by ${totalFramesNeeded - remaining} frames",
                color = RedErr, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))

        // Expiry date — picker, not manual entry
        Text("Expiry date", color = TextTertiary, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (expiryDate.isBlank()) "Not set" else expiryDate,
                color = if (expiryDate.isBlank()) TextTertiary else TextPrimary,
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Bg3)
            .border(1.dp, Border, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VaultDropdown("Sort", sortBy, sortOptions, onSort, modifier = Modifier.weight(1f))
            VaultDropdown("Filter", filterLabel, filterOptions, onFilter, modifier = Modifier.weight(1f))
        }
        if (extraToggle != null && onExtraToggle != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = extraToggle.second, onCheckedChange = onExtraToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = AmberDark))
                Spacer(Modifier.width(8.dp))
                Text(extraToggle.first, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

// ─── Camera Stash ─────────────────────────────────────────────────────────────

@Composable
fun CameraStashTab(cameras: List<Camera>, vm: MainViewModel) {
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Cameras", color = Amber, fontSize = 18.sp)
                Text("${displayCameras.size}/${cameras.size}", color = TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showFilter = !showFilter }, Modifier.size(36.dp)) {
                    Icon(Icons.Default.FilterList, null, modifier = Modifier.size(18.dp), tint = if (showFilter || filterFormat != "All") Amber else TextTertiary)
                }
                VaultButton("+ Add", small = true, onClick = { editing = null; showSheet = true })
            }
        }
        if (showFilter) item(key = "filter") {
            FilterBar(sortBy, listOf("Name", "Brand", "Format", "Condition"), { sortBy = it },
                filterFormat, formatOptions, { filterFormat = it })
        }
        if (displayCameras.isEmpty()) item(key = "empty") { EmptyState(if (cameras.isEmpty()) "No cameras yet" else "No cameras match filter") }
        items(displayCameras, key = { it.id }, contentType = { "camera" }) { cam ->
            VaultCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cam.name.ifBlank { "Unnamed" }, color = TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (cam.brand.isNotBlank()) Text(cam.brand, color = TextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            VaultTag(cam.format, textColor = AmberBright)
                            VaultTag(cam.condition)
                            if (cam.mount.isNotBlank()) VaultTag(cam.mount)
                            VaultTag(cam.lensSystem)
                        }
                        cam.adapterMounts.take(3).let { if (it.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                it.forEach { m -> VaultTag(m, textColor = BlueInfo) }
                            }
                        }}
                    }
                    Row {
                        IconButton(onClick = { editing = cam; showSheet = true }, Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = TextSecondary) }
                        IconButton(onClick = { confirmDelete = cam }, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = RedErr.copy(alpha = 0.7f)) }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Lenses", color = Amber, fontSize = 18.sp)
                Text("${displayLenses.size}/${lenses.size}", color = TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showFilter = !showFilter }, Modifier.size(36.dp)) {
                    Icon(Icons.Default.FilterList, null, modifier = Modifier.size(18.dp), tint = if (showFilter || filterMount != "All") Amber else TextTertiary)
                }
                VaultButton("+ Add", small = true, onClick = { editing = null; showSheet = true })
            }
        }
        if (showFilter) item(key = "filter") {
            FilterBar(sortBy, listOf("Name", "Brand", "Focal", "Aperture", "Condition"), { sortBy = it },
                filterMount, mountOptions, { filterMount = it })
        }
        if (displayLenses.isEmpty()) item(key = "empty") { EmptyState(if (lenses.isEmpty()) "No lenses yet" else "No lenses match filter") }
        items(displayLenses, key = { it.id }, contentType = { "lens" }) { lens ->
            VaultCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lens.name.ifBlank { "Unnamed" }, color = TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (lens.brand.isNotBlank()) Text(lens.brand, color = TextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            VaultTag("${lens.focalLength}mm", textColor = AmberBright)
                            VaultTag("f/${lens.maxAperture}")
                            if (lens.mount.isNotBlank()) VaultTag(lens.mount)
                            VaultTag(lens.condition)
                        }
                    }
                    Row {
                        IconButton(onClick = { editing = lens; showSheet = true }, Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = TextSecondary) }
                        IconButton(onClick = { confirmDelete = lens }, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = RedErr.copy(alpha = 0.7f)) }
                    }
                }
            }
        }
        item(key = "spacer") { Spacer(Modifier.height(4.dp)) }
    }
    if (showSheet) LensSheet(editing, onDismiss = { showSheet = false; editing = null }) { vm.upsertLens(it); showSheet = false; editing = null }
    confirmDelete?.let { ConfirmDialog("Delete \"${it.name}\"?", onConfirm = { vm.deleteLens(it); confirmDelete = null }, onDismiss = { confirmDelete = null }) }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Accessories", color = Amber, fontSize = 18.sp)
                Text("${displayAcc.size}/${accessories.size}", color = TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showFilter = !showFilter }, Modifier.size(36.dp)) {
                    Icon(Icons.Default.FilterList, null, modifier = Modifier.size(18.dp), tint = if (showFilter || filterType != "All") Amber else TextTertiary)
                }
                VaultButton("+ Add", small = true, onClick = { editing = null; showSheet = true })
            }
        }
        if (showFilter) item(key = "filter") {
            FilterBar(sortBy, listOf("Name", "Brand", "Type", "Condition"), { sortBy = it },
                filterType, typeOptions, { filterType = it })
        }
        if (displayAcc.isEmpty()) item(key = "empty") { EmptyState(if (accessories.isEmpty()) "No accessories yet" else "No accessories match filter") }
        items(displayAcc, key = { it.id }, contentType = { "accessory" }) { acc ->
            VaultCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(acc.name.ifBlank { "Unnamed" }, color = TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            VaultTag(acc.type, textColor = AmberBright)
                            if (acc.brand.isNotBlank()) VaultTag(acc.brand)
                            VaultTag(acc.condition)
                        }
                    }
                    Row {
                        IconButton(onClick = { editing = acc; showSheet = true }, Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = TextSecondary) }
                        IconButton(onClick = { confirmDelete = acc }, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = RedErr.copy(alpha = 0.7f)) }
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
fun FilmSheet(ed: FilmStock?, onDismiss: () -> Unit, onSave: (FilmStock) -> Unit) {
    var name       by remember { mutableStateOf(ed?.name ?: "") }
    var brand      by remember { mutableStateOf(ed?.brand ?: "") }
    var type       by remember { mutableStateOf(ed?.type ?: Constants.FILM_TYPES[0]) }
    var iso        by remember { mutableStateOf(ed?.iso?.toString() ?: "400") }
    var filmFormat by remember { mutableStateOf(ed?.filmFormat?.ifBlank { "135 (35mm)" } ?: "135 (35mm)") }
    var frameCount by remember { mutableStateOf(ed?.frameCount?.toString() ?: "36") }
    var expiry     by remember { mutableStateOf(ed?.expiryDate ?: "") }
    var storage    by remember { mutableStateOf(ed?.storage ?: Constants.STORAGE_TYPES[0]) }
    var quantity   by remember { mutableStateOf(ed?.quantity?.toString() ?: "1") }
    var notes      by remember { mutableStateOf(ed?.notes ?: "") }

    // Expiry month/year picker state
    var showExpPicker by remember { mutableStateOf(false) }
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultDropdown("ISO", iso, Constants.ISOS.map { it.toString() }, { iso = it }, modifier = Modifier.weight(1f))
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
            Text("Frames per roll", color = TextTertiary, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            if (is135) {
                // Quick-pick 12 / 24 / 36 + custom
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Constants.FRAMES_135.forEach { preset ->
                        val sel = frameCount == preset.toString()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sel) AmberDark else Bg3)
                                .border(1.dp, if (sel) Amber else Border, RoundedCornerShape(6.dp))
                                .clickable { frameCount = preset.toString() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(preset.toString(), color = if (sel) AmberBright else TextSecondary, fontSize = 13.sp) }
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    mfOptions.forEach { (fmt, n) ->
                        val sel = frameCount == n.toString()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sel) AmberDark else Bg3)
                                .border(1.dp, if (sel) Amber else Border, RoundedCornerShape(6.dp))
                                .clickable { frameCount = n.toString() }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(fmt, color = if (sel) AmberBright else TextSecondary, fontSize = 12.sp)
                                Text("$n fr", color = if (sel) Amber else TextTertiary, fontSize = 10.sp)
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
                Text("Expiry", color = TextTertiary, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (expiry.isBlank()) "Not set" else expiry, color = if (expiry.isBlank()) TextTertiary else TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    VaultButton("Pick", small = true, ghost = true, onClick = { showExpPicker = true })
                    if (expiry.isNotBlank()) VaultButton("✕", small = true, ghost = true, onClick = { expiry = "" })
                }
            }
            VaultDropdown("Storage", storage, Constants.STORAGE_TYPES, { storage = it }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
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
                storage     = storage,
                quantity    = quantity.toIntOrNull() ?: 1,
                notes       = notes
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
}

// ─── Month/Year Picker ────────────────────────────────────────────────────────

@Composable
fun MonthYearPickerDialog(year: Int, month: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var selYear  by remember { mutableIntStateOf(year) }
    var selMonth by remember { mutableIntStateOf(month) }
    val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg3,
        title = { Text("Expiry Date", color = AmberBright) },
        text = {
            Column {
                // Year picker
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selYear-- }) {
                        Icon(Icons.Default.ChevronLeft, null, tint = Amber)
                    }
                    Text(selYear.toString(), color = TextPrimary, fontSize = 18.sp)
                    IconButton(onClick = { selYear++ }) {
                        Icon(Icons.Default.ChevronRight, null, tint = Amber)
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Month grid
                val rows = months.chunked(3)
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { m ->
                            val mIdx = months.indexOf(m) + 1
                            Box(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selMonth == mIdx) AmberDark else Bg4)
                                    .border(1.dp, if (selMonth == mIdx) Amber else Border, RoundedCornerShape(6.dp))
                                    .clickable { selMonth = mIdx }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(m, color = if (selMonth == mIdx) AmberBright else TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selYear, selMonth) }) { Text("Set", color = Amber) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
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
            Text("Shooting format", color = TextTertiary, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Constants.MF_FORMATS.forEach { fmt ->
                    val frames = Constants.MF_FRAME_COUNTS[fmt] ?: 0
                    val sel = mfFormat == fmt
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sel) AmberDark else Bg3)
                            .border(1.dp, if (sel) Amber else Border, RoundedCornerShape(6.dp))
                            .clickable { mfFormat = fmt }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(fmt, color = if (sel) AmberBright else TextSecondary, fontSize = 12.sp)
                            Text("$frames fr", color = if (sel) Amber else TextTertiary, fontSize = 10.sp)
                        }
                    }
                }
            }
            if (mfFormat.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Loading a 120 roll will default to ${Constants.MF_FRAME_COUNTS[mfFormat]} frames",
                    color = TextTertiary, fontSize = 10.sp)
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
                    Text("🔧 ${if (showAdapters) "Hide" else "Add"} Adapter Mounts${if (adapters.isNotEmpty()) " (${adapters.size})" else ""}", color = Amber, fontSize = 12.sp)
                }
                if (showAdapters) {
                    val options = Constants.MOUNT_GROUPS[mount]?.adapters ?: Constants.COMMON_MOUNTS.filter { it != mount }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { m ->
                            val on = adapters.contains(m)
                            FilterChip(selected = on, onClick = { adapters = if (on) adapters - m else adapters + m },
                                label = { Text(m, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberDark, selectedLabelColor = TextPrimary,
                                    containerColor = Bg4, labelColor = TextSecondary))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Camera", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(Camera(id = ed?.id ?: uid(), name = name, brand = brand, format = format,
                mfFormat = mfFormat, lensSystem = lensSystem, condition = condition,
                mount = mount, adapterMounts = adapters, notes = notes))
        })
    }
}

@Composable
fun LensSheet(ed: Lens?, onDismiss: () -> Unit, onSave: (Lens) -> Unit) {
    var name        by remember { mutableStateOf(ed?.name ?: "") }
    var brand       by remember { mutableStateOf(ed?.brand ?: "") }
    var focalLength by remember { mutableStateOf(ed?.focalLength ?: "50") }
    var maxAperture by remember { mutableStateOf(ed?.maxAperture ?: "1.8") }
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
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Lens", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(Lens(id = ed?.id ?: uid(), name = name, brand = brand, focalLength = focalLength,
                maxAperture = maxAperture, mount = mount, condition = condition))
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
fun FilmInfoDialog(film: FilmStock, onDismiss: () -> Unit, onEdit: () -> Unit, onLoad: (() -> Unit)? = null) {
    val expKey = film.expiryDate.let { if (it.length == 7) "$it-01" else it }
    val (exLabel, exColor, _) = expiryStatus(expKey)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Bg3,
        title = { Text(film.name.ifBlank { "Film" }, color = AmberBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (film.brand.isNotBlank()) InfoRow("Brand", film.brand)
                InfoRow("Type", film.type)
                InfoRow("ISO", film.iso.toString())
                InfoRow("Storage", film.storage)
                if (film.quantity > 1) InfoRow("Quantity", "${film.quantity} rolls")
                if (exLabel.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Expiry", color = TextTertiary, fontSize = 12.sp)
                    VaultTag(exLabel, textColor = exColor)
                }
                if (film.notes.isNotBlank()) Text(film.notes, color = TextSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onLoad != null) VaultButton("Load Film", small = true, onClick = onLoad)
            else VaultTag("📷 In Camera", textColor = BlueInfo)
            VaultButton("Edit", small = true, ghost = true, onClick = onEdit)
        }},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = TextSecondary) } }
    )
}

@Composable private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = TextTertiary, fontSize = 12.sp, modifier = Modifier.width(80.dp))
        Text(value, color = TextPrimary, fontSize = 12.sp)
    }
}

@Composable
fun LoadRollSheetFromStash(
    film: FilmStock,
    cameras: List<Camera>,
    lenses: List<Lens>,
    rolls: List<Roll> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (Roll) -> Unit
) {
    // Cameras that currently have a roll loaded (not yet developed)
    val busyCameraIds = remember(rolls) {
        rolls.filter { !it.developed }.map { it.cameraId }.toSet()
    }

    var cameraId  by remember { mutableStateOf("") }
    var lensId    by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())) }

    val cameraName = cameras.find { it.id == cameraId }?.name ?: ""
    val lensName   = lenses.find  { it.id == lensId }?.name ?: ""
    val selCamera  = cameras.find { it.id == cameraId }
    val isBusy     = cameraId.isNotBlank() && cameraId in busyCameraIds

    VaultSheet("Load ${film.name}", onDismiss) {
        // Film info summary
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VaultTag(film.type.split(" ").first())
            VaultTag("ISO ${film.iso}")
            if (film.storage.isNotBlank()) VaultTag(film.storage)
        }
        Spacer(Modifier.height(14.dp))

        // Show cameras with busy indicator
        val cameraDisplayNames = cameras.map { cam ->
            if (cam.id in busyCameraIds) "${cam.name} 📷" else cam.name
        }
        VaultDropdown("Camera", if (cameraName.isBlank()) "" else if (cameraId in busyCameraIds) "$cameraName 📷" else cameraName,
            cameraDisplayNames,
            { displayName ->
                val cleanName = displayName.removeSuffix(" 📷")
                cameraId = cameras.find { it.name == cleanName }?.id ?: ""; lensId = ""
            })
        // Warning if camera already has a roll
        if (isBusy) {
            Spacer(Modifier.height(4.dp))
            Text("⚠ This camera already has a roll loaded. Load anyway for MF cameras with multiple backs.",
                color = OrangeWarn, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))

        if (selCamera?.lensSystem == "interchangeable") {
            val compatLenses = lenses.filter { lens ->
                Constants.mountCompat(selCamera.mount, lens.mount, selCamera.adapterMounts) != "incompatible"
            }
            val lensOptions = listOf("— No lens —") + compatLenses.map { l ->
                val compat = Constants.mountCompat(selCamera.mount, l.mount, selCamera.adapterMounts)
                "${l.name} [${if (compat == "native") "native" else "via adapter"}]"
            }
            VaultDropdown("Lens", if (lensId.isBlank()) "— No lens —" else lensName, lensOptions,
                { sel -> lensId = if (sel.startsWith("—")) "" else compatLenses.find { l -> sel.startsWith(l.name) }?.id ?: "" })
            Spacer(Modifier.height(10.dp))
        }

        VaultTextField(startDate, { startDate = it }, "Load Date (YYYY-MM-DD)")
        Spacer(Modifier.height(16.dp))

        VaultButton(
            text = if (isBusy) "Load Anyway (MF / multiple backs)" else "Load into Camera",
            modifier = Modifier.fillMaxWidth(),
            ghost = isBusy,
            onClick = {
                if (cameraId.isNotBlank()) {
                    onSave(Roll(
                        id = uid(), filmId = film.id, cameraId = cameraId,
                        cameraLensId = lensId, startDate = startDate
                    ))
                }
            }
        )
    }
}
