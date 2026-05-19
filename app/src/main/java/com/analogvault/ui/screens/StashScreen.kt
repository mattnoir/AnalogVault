package com.analogvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.ui.theme.BlueInfo
import androidx.compose.ui.unit.width
import com.analogvault.data.model.*
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*
import com.analogvault.util.Constants
import com.analogvault.ui.uid

@Composable
fun StashScreen(vm: MainViewModel) {
    val films       by vm.films.collectAsState()
    val cameras     by vm.cameras.collectAsState()
    val lenses      by vm.lenses.collectAsState()
    val accessories by vm.accessories.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Film", "Cameras", "Lenses", "Accessories")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = Bg2,
            contentColor = Amber,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[tab]),
                    color = Amber
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
    var showSheet by remember { mutableStateOf(false) }
    var editing   by remember { mutableStateOf<FilmStock?>(null) }
    var confirmDelete by remember { mutableStateOf<FilmStock?>(null) }
    var viewingFilm   by remember { mutableStateOf<FilmStock?>(null) }
    var loadingFilm   by remember { mutableStateOf<FilmStock?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Film Stash", color = Amber, fontSize = 18.sp)
                Text("${films.size} stocks", color = TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                VaultButton("+ Add", small = true, onClick = { editing = null; showSheet = true })
            }
        }
        if (films.isEmpty()) item { EmptyState("No film stocks added yet") }
        items(films, key = { it.id }) { film ->
            val (exLabel, exColor, _) = expiryStatus(film.expiryDate)
            VaultCard(onClick = { viewingFilm = film }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(film.name.ifBlank { "Unnamed" }, color = TextPrimary, fontSize = 15.sp)
                        Text(film.type, color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VaultTag("ISO ${film.iso}")
                            VaultTag("${film.shots} exp")
                            VaultTag(film.storage)
                            if (film.quantity > 1) VaultTag("×${film.quantity}", textColor = AmberBright)
                            if (exLabel.isNotBlank()) VaultTag(exLabel, textColor = exColor)
                        }
                        if (film.notes.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(film.notes, color = TextTertiary, fontSize = 11.sp, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    // Edit + Delete in same row as info, no separate row
                    Row {
                        IconButton(onClick = { editing = film; showSheet = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { confirmDelete = film }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, null, tint = RedErr.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }

    if (showSheet) {
        FilmSheet(editing, onDismiss = { showSheet = false; editing = null }) { vm.upsertFilm(it); showSheet = false; editing = null }
    }
    confirmDelete?.let { film ->
        ConfirmDialog("Delete \"${film.name}\"?", onConfirm = { vm.deleteFilm(film); confirmDelete = null }, onDismiss = { confirmDelete = null })
    }
    viewingFilm?.let { film ->
        FilmInfoDialog(
            film = film,
            onDismiss = { viewingFilm = null },
            onEdit    = { viewingFilm = null; editing = film; showSheet = true },
            onLoad    = { viewingFilm = null; loadingFilm = film }
        )
    }
    loadingFilm?.let { film ->
        // Navigate to Active tab would need a callback; for now show a note sheet
        // that pre-fills the film — simpler and keeps StashScreen self-contained
        FilmLoadHint(film.name, onDismiss = { loadingFilm = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmSheet(ed: FilmStock?, onDismiss: () -> Unit, onSave: (FilmStock) -> Unit) {
    var name     by remember { mutableStateOf(ed?.name ?: "") }
    var brand    by remember { mutableStateOf(ed?.brand ?: "") }
    var type     by remember { mutableStateOf(ed?.type ?: Constants.FILM_TYPES[0]) }
    var iso      by remember { mutableStateOf(ed?.iso?.toString() ?: "400") }

    // Auto-fill brand + ISO when a known film is selected from autocomplete
    fun onFilmSelected(filmName: String) {
        name = filmName
        Constants.FILM_METADATA[filmName]?.let { meta ->
            if (brand.isBlank()) brand = meta.first
            iso = meta.second.toString()
            type = meta.third
        }
    }
    var shots    by remember { mutableStateOf(ed?.shots?.toString() ?: "36") }
    var expiry   by remember { mutableStateOf(ed?.expiryDate ?: "") }
    var storage  by remember { mutableStateOf(ed?.storage ?: Constants.STORAGE_TYPES[0]) }
    var quantity by remember { mutableStateOf(ed?.quantity?.toString() ?: "1") }
    var notes    by remember { mutableStateOf(ed?.notes ?: "") }

    VaultSheet(title = if (ed != null) "Edit Film" else "Add Film", onDismiss = onDismiss) {
        AutoCompleteField(name, { onFilmSelected(it) }, "Film Stock", Constants.FILM_DB, placeholder = "e.g. Kodak Portra 400")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(brand, { brand = it }, "Brand", modifier = Modifier.weight(1f))
            VaultTextField(quantity, { quantity = it }, "Qty", modifier = Modifier.weight(0.4f), keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        }
        Spacer(Modifier.height(10.dp))
        VaultDropdown("Type", type, Constants.FILM_TYPES, { type = it })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultDropdown("ISO", iso, Constants.ISOS.map { it.toString() }, { iso = it }, modifier = Modifier.weight(1f))
            VaultDropdown("Exposures", shots, listOf("12","24","36","72"), { shots = it }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(expiry, { expiry = it }, "Expiry (YYYY-MM)", placeholder = "2026-12", modifier = Modifier.weight(1f))
            VaultDropdown("Storage", storage, Constants.STORAGE_TYPES, { storage = it }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2)
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Film", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(FilmStock(
                id = ed?.id ?: uid(), name = name, brand = brand, type = type,
                iso = iso.toIntOrNull() ?: 400, shots = shots.toIntOrNull() ?: 36,
                expiryDate = expiry, storage = storage, quantity = quantity.toIntOrNull() ?: 1, notes = notes
            ))
        })
    }
}

// ─── Camera Stash ─────────────────────────────────────────────────────────────

@Composable
fun CameraStashTab(cameras: List<Camera>, vm: MainViewModel) {
    var showSheet    by remember { mutableStateOf(false) }
    var editing      by remember { mutableStateOf<Camera?>(null) }
    var confirmDelete by remember { mutableStateOf<Camera?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Cameras", color = Amber, fontSize = 18.sp)
                Text("${cameras.size} bodies", color = TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                VaultButton("+ Add", small = true, onClick = { editing = null; showSheet = true })
            }
        }
        if (cameras.isEmpty()) item { EmptyState("No cameras added yet") }
        items(cameras, key = { it.id }) { cam ->
            VaultCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cam.name.ifBlank { "Unnamed" }, color = TextPrimary, fontSize = 15.sp)
                        Text("${cam.format} · ${cam.condition}", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (cam.mount.isNotBlank()) VaultTag(cam.mount, textColor = AmberBright)
                            VaultTag(cam.lensSystem)
                            cam.adapterMounts.take(2).forEach { VaultTag(it, textColor = BlueInfo) }
                        }
                    }
                    Row {
                        IconButton(onClick = { editing = cam; showSheet = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { confirmDelete = cam }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, null, tint = RedErr.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
item { Spacer(Modifier.height(4.dp)) }
    }

    if (showSheet) {
        CameraSheet(editing, onDismiss = { showSheet = false; editing = null }) { vm.upsertCamera(it); showSheet = false; editing = null }
    }
    confirmDelete?.let { cam ->
        ConfirmDialog("Delete \"${cam.name}\"?", onConfirm = { vm.deleteCamera(cam); confirmDelete = null }, onDismiss = { confirmDelete = null })
    }
}

@Composable
fun CameraSheet(ed: Camera?, onDismiss: () -> Unit, onSave: (Camera) -> Unit) {
    var name       by remember { mutableStateOf(ed?.name ?: "") }
    var brand      by remember { mutableStateOf(ed?.brand ?: "") }
    var format     by remember { mutableStateOf(ed?.format ?: "35mm") }
    var lensSystem by remember { mutableStateOf(ed?.lensSystem ?: "fixed") }
    var condition  by remember { mutableStateOf(ed?.condition ?: "Good") }
    var mount      by remember { mutableStateOf(ed?.mount ?: "") }
    var adapters   by remember { mutableStateOf(ed?.adapterMounts ?: emptyList()) }
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
                    val options = Constants.MOUNT_GROUPS[mount]?.adapters
                        ?: Constants.COMMON_MOUNTS.filter { it != mount }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.chunked(3).forEach { row ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { m ->
                                    val on = adapters.contains(m)
                                    FilterChip(
                                        selected = on,
                                        onClick = { adapters = if (on) adapters - m else adapters + m },
                                        label = { Text(m, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AmberDark,
                                            selectedLabelColor = TextPrimary,
                                            containerColor = Bg4,
                                            labelColor = TextSecondary
                                        )
                                    )
                                }
                            }
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
                lensSystem = lensSystem, condition = condition, mount = mount,
                adapterMounts = adapters, notes = notes))
        })
    }
}

// ─── Lens Stash ───────────────────────────────────────────────────────────────

@Composable
fun LensStashTab(lenses: List<Lens>, vm: MainViewModel) {
    var showSheet     by remember { mutableStateOf(false) }
    var editing       by remember { mutableStateOf<Lens?>(null) }
    var confirmDelete by remember { mutableStateOf<Lens?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Lenses", color = Amber, fontSize = 18.sp)
                Text("${lenses.size} optics", color = TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                VaultButton("+ Add", small = true, onClick = { editing = null; showSheet = true })
            }
        }
        if (lenses.isEmpty()) item { EmptyState("No lenses added yet") }
        items(lenses, key = { it.id }) { lens ->
            VaultCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lens.name.ifBlank { "Unnamed" }, color = TextPrimary, fontSize = 15.sp)
                        Text("${lens.focalLength}mm · f/${lens.maxAperture}", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (lens.mount.isNotBlank()) VaultTag(lens.mount, textColor = AmberBright)
                            VaultTag(lens.condition)
                        }
                    }
                    Row {
                        IconButton(onClick = { editing = lens; showSheet = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { confirmDelete = lens }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, null, tint = RedErr.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
item { Spacer(Modifier.height(4.dp)) }
    }

    if (showSheet) {
        LensSheet(editing, onDismiss = { showSheet = false; editing = null }) { vm.upsertLens(it); showSheet = false; editing = null }
    }
    confirmDelete?.let { lens ->
        ConfirmDialog("Delete \"${lens.name}\"?", onConfirm = { vm.deleteLens(lens); confirmDelete = null }, onDismiss = { confirmDelete = null })
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
            VaultTextField(focalLength, { focalLength = it }, "Focal (mm)", modifier = Modifier.weight(1f), keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
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

// ─── Accessory Stash ──────────────────────────────────────────────────────────

@Composable
fun AccessoryStashTab(accessories: List<Accessory>, vm: MainViewModel) {
    var showSheet     by remember { mutableStateOf(false) }
    var editing       by remember { mutableStateOf<Accessory?>(null) }
    var confirmDelete by remember { mutableStateOf<Accessory?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Accessories", color = Amber, fontSize = 18.sp)
                Text("${accessories.size} items", color = TextTertiary, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                VaultButton("+ Add", small = true, onClick = { editing = null; showSheet = true })
            }
        }
        if (accessories.isEmpty()) item { EmptyState("No accessories added yet") }
        items(accessories, key = { it.id }) { acc ->
            VaultCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(acc.name.ifBlank { "Unnamed" }, color = TextPrimary, fontSize = 15.sp)
                        Text(acc.type, color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (acc.brand.isNotBlank()) VaultTag(acc.brand)
                            VaultTag(acc.condition)
                        }
                    }
                    Row {
                        IconButton(onClick = { editing = acc; showSheet = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { confirmDelete = acc }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, null, tint = RedErr.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
item { Spacer(Modifier.height(4.dp)) }
    }

    if (showSheet) {
        AccessorySheet(editing, onDismiss = { showSheet = false; editing = null }) { vm.upsertAccessory(it); showSheet = false; editing = null }
    }
    confirmDelete?.let { acc ->
        ConfirmDialog("Delete \"${acc.name}\"?", onConfirm = { vm.deleteAccessory(acc); confirmDelete = null }, onDismiss = { confirmDelete = null })
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
fun FilmInfoDialog(
    film: FilmStock,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onLoad: () -> Unit
) {
    val (exLabel, exColor, expired) = expiryStatus(
        film.expiryDate.let { if (it.length == 7) "$it-01" else it }
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.analogvault.ui.theme.Bg3,
        title = { Text(film.name.ifBlank { "Film" }, color = com.analogvault.ui.theme.AmberBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (film.brand.isNotBlank()) InfoRow("Brand", film.brand)
                InfoRow("Type", film.type)
                InfoRow("ISO", film.iso.toString())
                InfoRow("Exposures", "${film.shots}")
                InfoRow("Storage", film.storage)
                if (film.quantity > 1) InfoRow("Quantity", "${film.quantity} rolls")
                if (exLabel.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Expiry", color = com.analogvault.ui.theme.TextTertiary, fontSize = 12.sp)
                        com.analogvault.ui.components.VaultTag(exLabel, textColor = exColor)
                    }
                }
                if (film.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(film.notes, color = com.analogvault.ui.theme.TextSecondary, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.analogvault.ui.components.VaultButton("Load Film", small = true, onClick = onLoad)
                com.analogvault.ui.components.VaultButton("Edit", small = true, ghost = true, onClick = onEdit)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = com.analogvault.ui.theme.TextSecondary)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = com.analogvault.ui.theme.TextTertiary, fontSize = 12.sp,
            modifier = Modifier.width(80.dp))
        Text(value, color = com.analogvault.ui.theme.TextPrimary, fontSize = 12.sp)
    }
}

@Composable
fun FilmLoadHint(filmName: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.analogvault.ui.theme.Bg3,
        title = { Text("Load $filmName", color = com.analogvault.ui.theme.AmberBright) },
        text = { Text("Go to the Rolls tab and tap '+ Load Film into Camera' to load this film.",
            color = com.analogvault.ui.theme.TextSecondary) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = com.analogvault.ui.theme.Amber) }
        }
    )
}
