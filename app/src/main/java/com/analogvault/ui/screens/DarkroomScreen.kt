package com.analogvault.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.analogvault.data.model.Chemical
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*
import com.analogvault.ui.uid
import com.analogvault.util.Constants

@Composable
fun DarkroomScreen(vm: MainViewModel) {
    val chemicals by vm.chemicals.collectAsState()
    var showSheet     by remember { mutableStateOf(false) }
    var editing       by remember { mutableStateOf<Chemical?>(null) }
    var confirmDelete by remember { mutableStateOf<Chemical?>(null) }
    var setRollsDialog by remember { mutableStateOf<Chemical?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionTitle("Darkroom Chemistry", "${chemicals.size} chemicals") }
        if (chemicals.isEmpty()) item { EmptyState("No chemicals tracked yet") }
        items(chemicals, key = { it.id }) { chem ->
            val used    = vm.rolledCount(chem)
            val maxR    = chem.maxRolls.toIntOrNull()
            val pct     = if (maxR != null && maxR > 0) (used.toFloat() / maxR).coerceIn(0f, 1f) else null
            val alert   = when {
                maxR != null && used >= maxR           -> "exhausted"
                maxR != null && used.toFloat()/maxR >= 0.8f -> "warn"
                else -> null
            }
            val adjTime = if (chem.timeAdjPerRoll.isNotBlank() && chem.baseDevTime.isNotBlank()) {
                val base = chem.baseDevTime.toDoubleOrNull() ?: 0.0
                val adj  = chem.timeAdjPerRoll.toDoubleOrNull() ?: 0.0
                "%.2f".format(base + used * adj)
            } else null

            VaultCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(chem.name.ifBlank { "Unnamed" }, color = TextPrimary, fontSize = 15.sp)
                        Text("${chem.type}${if (chem.dilution.isNotBlank()) " · ${chem.dilution}" else ""}", color = TextSecondary, fontSize = 12.sp)
                    }
                    when (alert) {
                        "exhausted" -> VaultTag("⚠ Exhausted", textColor = RedErr)
                        "warn"      -> VaultTag("⚠ Near limit", textColor = OrangeWarn)
                        else        -> if (maxR != null) VaultTag("OK", textColor = GreenOk)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VaultTag("$used roll${if (used != 1) "s" else ""} used")
                    if (maxR != null) VaultTag("cap: $maxR")
                    if (chem.volume.isNotBlank()) VaultTag("${chem.volume}${chem.volumeUnit}")
                    if (chem.mixDate.isNotBlank()) VaultTag("mixed ${formatDate(chem.mixDate)}")
                }
                if (pct != null) {
                    Spacer(Modifier.height(6.dp))
                    val barColor = when {
                        pct >= 1f   -> RedErr
                        pct >= 0.8f -> OrangeWarn
                        else        -> GreenOk
                    }
                    VaultProgressBar(pct, color = barColor)
                }
                if (adjTime != null) {
                    Spacer(Modifier.height(4.dp))
                    val base = chem.baseDevTime.toDoubleOrNull() ?: 0.0
                    val diff = "%.2f".format(adjTime.toDouble() - base)
                    Text("⏱ Adjusted dev time: $adjTime min (+$diff for $used rolls)", color = Amber, fontSize = 11.sp)
                }
                if (chem.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(chem.notes, color = TextTertiary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultButton("Edit", small = true, ghost = true, onClick = { editing = chem; showSheet = true })
                    VaultButton("Set Rolls", small = true, ghost = true, onClick = { setRollsDialog = chem })
                    VaultButton("Delete", small = true, danger = true, onClick = { confirmDelete = chem })
                }
            }
        }
        item {
            VaultButton("+ Add Chemistry", modifier = Modifier.fillMaxWidth(), onClick = { editing = null; showSheet = true })
        }
    }

    if (showSheet) {
        ChemSheet(editing, onDismiss = { showSheet = false; editing = null }) {
            vm.upsertChemical(it); showSheet = false; editing = null
        }
    }
    confirmDelete?.let { chem ->
        ConfirmDialog("Delete \"${chem.name}\"?", onConfirm = { vm.deleteChemical(chem); confirmDelete = null }, onDismiss = { confirmDelete = null })
    }
    setRollsDialog?.let { chem ->
        SetRollsDialog(chem, vm.rolledCount(chem),
            onConfirm = { count -> vm.setChemicalRolls(chem.id, count); setRollsDialog = null },
            onDismiss = { setRollsDialog = null })
    }
}

@Composable
fun SetRollsDialog(chem: Chemical, current: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg3,
        title = { Text("Rolls used for ${chem.name}", color = AmberBright) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, unfocusedBorderColor = Border, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.toIntOrNull() ?: current) }) { Text("Set", color = Amber) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
fun ChemSheet(ed: Chemical?, onDismiss: () -> Unit, onSave: (Chemical) -> Unit) {
    var name          by remember { mutableStateOf(ed?.name ?: "") }
    var type          by remember { mutableStateOf(ed?.type ?: Constants.CHEM_TYPES[0]) }
    var dilution      by remember { mutableStateOf(ed?.dilution ?: "") }
    var volume        by remember { mutableStateOf(ed?.volume ?: "") }
    var volumeUnit    by remember { mutableStateOf(ed?.volumeUnit ?: "ml") }
    var mixDate       by remember { mutableStateOf(ed?.mixDate ?: java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())) }
    var maxRolls      by remember { mutableStateOf(ed?.maxRolls ?: "") }
    var baseDevTime   by remember { mutableStateOf(ed?.baseDevTime ?: "") }
    var timeAdjPerRoll by remember { mutableStateOf(ed?.timeAdjPerRoll ?: "") }
    var notes         by remember { mutableStateOf(ed?.notes ?: "") }

    val allChemDb = Constants.DEV_DB + Constants.FIX_DB

    VaultSheet(if (ed != null) "Edit Chemistry" else "Add Chemistry", onDismiss) {
        AutoCompleteField(name, { name = it }, "Name / Brand", allChemDb, placeholder = "e.g. Kodak HC-110")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultDropdown("Type", type, Constants.CHEM_TYPES, { type = it }, modifier = Modifier.weight(1f))
            VaultTextField(dilution, { dilution = it }, "Dilution", modifier = Modifier.weight(1f), placeholder = "1:31")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(volume, { volume = it }, "Volume", modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            VaultDropdown("Unit", volumeUnit, Constants.CHEM_UNITS, { volumeUnit = it }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(mixDate, { mixDate = it }, "Mix Date", modifier = Modifier.weight(1f))
            VaultTextField(maxRolls, { maxRolls = it }, "Max Rolls", modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, placeholder = "25")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VaultTextField(baseDevTime, { baseDevTime = it }, "Base Dev (min)", modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, placeholder = "8")
            VaultTextField(timeAdjPerRoll, { timeAdjPerRoll = it }, "+Time/Roll", modifier = Modifier.weight(1f),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, placeholder = "0.1")
        }
        Spacer(Modifier.height(10.dp))
        VaultTextField(notes, { notes = it }, "Notes", singleLine = false, minLines = 2, placeholder = "Storage, replenishment…")
        Spacer(Modifier.height(16.dp))
        VaultButton("Save Chemistry", modifier = Modifier.fillMaxWidth(), onClick = {
            onSave(Chemical(id = ed?.id ?: uid(), name = name, type = type, dilution = dilution,
                volume = volume, volumeUnit = volumeUnit, mixDate = mixDate, maxRolls = maxRolls,
                baseDevTime = baseDevTime, timeAdjPerRoll = timeAdjPerRoll,
                manualRolls = ed?.manualRolls ?: -1, notes = notes))
        })
    }
}
