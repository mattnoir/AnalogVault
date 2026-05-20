package com.analogvault.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.data.model.*
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*
import com.analogvault.ui.uid
import com.analogvault.util.Constants

// ─── Format options (replaces "shots") ───────────────────────────────────────
val FILM_FORMATS_DISPLAY = listOf("135 (35mm)", "120", "220", "4x5", "8x10", "Super 8", "110", "126", "Instant")

@Composable
fun StashScreen(vm: MainViewModel) {
    // Collect once at top level — each tab only reads what it needs
    val films       by vm.films.collectAsState()
    val cameras     by vm.cameras.collectAsState()
    val lenses      by vm.lenses.collectAsState()
    val accessories by vm.accessories.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Film", "Cameras", "Lenses", "Accessories")

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = Bg2,
            contentColor = Amber,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[tab]), color = Amber
                )
            }
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i },
                    text = { Text(t, fontSize = 12.sp) },
                    selectedContentColor = Amber,
                    unselectedContentColor = TextTertiary)
            }
        }
        when (tab) {
            0 -> FilmStashTab(films, vm)
            1 -> CameraStashTab(cameras, vm)
            2 -> LensStashTab(lenses, vm)
            3 -> AccessoryStashTab(accessories, vm)
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
                    Icon(imageVector = Icons.Default.FilterList, contentDescription = null, tint = if (showFilter || filterType != "All" || filterExp) Amber else TextTertiary, Modifier.size(18.dp))
                }
                VaultButton("+ Add", small = true, onClick = { editing = null; showSheet = true })
            }
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
        items(displayFilms, key = { it.id }) { film ->
            // Compute expiry once per item, not every frame
            val expKey = remember(film.expiryDate) { film.expiryDate.let { if (it.length == 7) "$it-01" else it } }
            val (exLabel, exColor, _) = remember(expKey) { expiryStatus(expKey) }
            FilmCard(film, exLabel, exColor,
                onTap = { viewingFilm = film },
                onEdit = { editing = film; showSheet = true },
                onDelete = { confirmDelete = film }
            )
        }
        item(key = "spacer") { Spacer(Modifier.height(4.dp)) }
    }

    if (showSheet) FilmSheet(editing, onDismiss = { showSheet = false; editing = null }) { vm.upsertFilm(it); showSheet = false; editing = null }
    confirmDelete?.let { ConfirmDialog("Delete \"${it.name}\"?", onConfirm = { vm.deleteFilm(it); confirmDelete = null }, onDismiss = { confirmDelete = null }) }
    viewingFilm?.let { FilmInfoDialog(it, onDismiss = { viewingFilm = null }, onEdit = { viewingFilm = null; editing = it; showSheet = true }, onLoad = { viewingFilm = null; loadingFilm = it }) }
    loadingFilm?.let { FilmLoadHint(it.name, onDismiss = { loadingFilm = null }) }
}

@Composable
private fun FilmCard(
    film: FilmStock, exLabel: String, exColor: androidx.compose.ui.graphics.Color,
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
                    VaultTag(film.shots.toString())  // shots = format string now
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
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = RedErr.copy(alpha = 0.7f), Modifier.size(16.dp))
                }
            }
        }
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
                    Icon(imageVector = Icons.Default.FilterList, contentDescription = null, tint = if (showFilter || filterFormat != "All") Amber else TextTertiary, Modifier.size(18.dp))
                }
                VaultButton("+ Add", small = true, onClick = { editing = null; showSheet = true })
            }
        }
        if (showFilter) item(key = "filter") {
            FilterBar(sortBy, listOf("Name", "Brand", "Format", "Condition"), { sortBy = it },
                filterFormat, formatOptions, { filterFormat = it })
        }
        if (displayCameras.isEmpty()) item(key = "empty") { EmptyState(if (cameras.isEmpty()) "No cameras yet" else "No cameras match filter") }
        items(displayCameras, key = { it.id }) { cam ->
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
                        IconButton(onClick = { editing = cam; showSheet = true }, Modifier.size(32.dp)) { Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp)) }
                        IconButton(onClick = { confirmDelete = cam }, Modifier.size(32.dp)) { Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = RedErr.copy(alpha = 0.7f), Modifier.size(16.dp)) }
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
                    Icon(imageVector = Icons.Default.FilterList, contentDescription = null, tint = if (showFilter || filterMount != "All") Amber else TextTertiary, Modifier.size(18.dp))
                }
                VaultButton("+ Add", small = true, onClick = { editing = null; showSheet = true })
            }
        }
        if (showFilter) item(key = "filter") {
            FilterBar(sortBy, listOf("Name", "Brand", "Focal", "Aperture", "Condition"), { sortBy = it },
                filterMount, mountOptions, { filterMount = it })
        }
        if (displayLenses.isEmpty()) item(key = "empty") { EmptyState(if (lenses.isEmpty()) "No lenses yet" else "No lenses match filter") }
        items(displayLenses, key = { it.id }) { lens ->
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
                        IconButton(onClick = { editing = lens; showSheet = true }, Modifier.size(32.dp)) { Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp)) }
                        IconButton(onClick = { confirmDelete = lens }, Modifier.size(32.dp)) { Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = RedErr.copy(alpha = 0.7f), Modifier.size(16.dp)) }
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
                    Icon(imageVector = Icons.Default.FilterList, contentDescription = null, tint = if (showFilter || filterType != "All") Amber else TextTertiary, Modifier.size(18.dp))
                }
                VaultButton("+ Add", small = true, onClick = { editing = null; showSheet = true })
            }
        }
        if (showFilter) item(key = "filter") {
            FilterBar(sortBy, listOf("Name", "Brand", "Type", "Condition"), { sortBy = it },
                filterType, typeOptions, { filterType = it })
        }
        if (displayAcc.isEmpty()) item(key = "empty") { EmptyState(if (accessories.isEmpty()) "No accessories yet" else "No accessories match filter") }
        items(displayAcc, key = { it.id }) { acc ->
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
                        IconButton(onClick = { editing = acc; showSheet = true }, Modifier.size(32.dp)) { Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp)) }
                        IconButton(onClick = { confirmDelete = acc }, Modifier.size(32.dp)) { Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = RedErr.copy(alpha = 0.7f), Modifier.size(16.dp)) }
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

@Composable
fun FilmSheet(ed: FilmStock?, onDismiss: () -> Unit, onSave: (FilmStock) -> Unit) {
    var name     by remember { mutableStateOf(ed?.name ?: "") }
    var brand    by remember { mutableStateOf(ed?.brand ?: "") }
    var type     by remember { mutableStateOf(ed?.type ?: Constants.FILM_TYPES[0]) }
    var iso      by remember { mutableStateOf(ed?.iso?.toString() ?: "400") }
    var format   by remember { mutableStateOf(ed?.shots?.toString() ?: "135 (35mm)") }
    var expiry   by remember { mutableStateOf(ed?.expiryDate ?: "") }
    var storage  by remember { mutableStateOf(ed?.storage ?: Constants.STORAGE_TYPES[0]) }
    var quantity by remember { mutableStateOf(ed?.quantity?.toString() ?: "1") }
    var notes    by remember { mutableStateOf(ed?.notes ?: "") }

    // Expiry month/year picker state
    var showExpPicker by remember { mutableStateOf(false) }
    var pickerYear    by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) }
    var pickerMonth   by remember { mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1) }

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
            VaultDropdown("Format", format, FILM_FORMATS_DISPLAY, { format = it }, modifier = Modifier.weight(1f))
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
            // store format string in shots field (repurposed)
            onSave(FilmStock(id = ed?.id ?: uid(), name = name, brand = brand, type = type,
                iso = iso.toIntOrNull() ?: 400, shots = 36, // legacy field kept
                expiryDate = expiry, storage = storage, quantity = quantity.toIntOrNull() ?: 1, notes = notes))
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
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = null, tint = Amber)
                    }
                    Text(selYear.toString(), color = TextPrimary, fontSize = 18.sp)
                    IconButton(onClick = { selYear++ }) {
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Amber)
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Month grid
                val rows = months.chunked(3)
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { idx, m ->
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CameraSheet(ed: Camera?, onDismiss: () -> Unit, onSave: (Camera) -> Unit) {
    var name       by remember { mutableStateOf(ed?.name ?: "") }
    var brand      by remember { mutableStateOf(ed?.brand ?: "") }
    var format     by remember { mutableStateOf(ed?.format ?: "35mm") }
    var lensSystem by remember { mutableStateOf(ed?.lensSystem ?: "fixed") }
    var condition  by remember { mutableStateOf(ed?.condition ?: "Good") }
    var mount      by remember { mutableStateOf(ed?.mount ?: "") }
    var adapters   by remember { mutableStateOf(ed?.adapterMounts ?: emptyList<String>()) }
    var notes      by remember { mutableStateOf(ed?.notes ?: "") }
    var showAdapters by remember { mutableStateOf(adapters.isNotEmpty()) }

    VaultSheet(if (ed != null) "Edit Camera" else "Add Camera", onDismiss) {
        AutoCompleteField(name, { name = it }, "Model", Constants.CAMERA_DB, placeholder = "e.g. Nikon F3")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(brand, { brand = it }, "Brand", modifier = Modifier.weight(1f))
            VaultDropdown("Format", format, Constants.CAMERA_FORMATS, { format = it }, modifier = Modifier.weight(1f))
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
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                lensSystem = lensSystem, condition = condition, mount = mount, adapterMounts = adapters, notes = notes))
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
fun FilmInfoDialog(film: FilmStock, onDismiss: () -> Unit, onEdit: () -> Unit, onLoad: () -> Unit) {
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
            VaultButton("Load Film", small = true, onClick = onLoad)
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

@Composable fun FilmLoadHint(filmName: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Bg3,
        title = { Text("Load $filmName", color = AmberBright) },
        text = { Text("Go to the Rolls tab and tap '+ Load Film into Camera'.", color = TextSecondary) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = Amber) } }
    )
}
