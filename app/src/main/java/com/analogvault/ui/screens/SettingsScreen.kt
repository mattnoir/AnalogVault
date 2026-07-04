@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.analogvault.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.components.*
import com.analogvault.ui.theme.*
import com.analogvault.util.Constants

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val owmKey    by vm.owmKey.collectAsState()
    val currency  by vm.currency.collectAsState()
    val isMetric  by vm.isMetric.collectAsState()
    val customIsos by vm.customIsos.collectAsState()
    val highRefresh by vm.highRefresh.collectAsState()
    val remindersEnabled  by vm.remindersEnabled.collectAsState()
    val remindExpiry      by vm.remindExpiry.collectAsState()
    val remindUndeveloped by vm.remindUndeveloped.collectAsState()
    val remindChemicals   by vm.remindChemicals.collectAsState()

    // Enabling reminders needs POST_NOTIFICATIONS on API 33+; the worker also
    // re-checks before posting, so a denied permission just means silence.
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.saveRemindersEnabled(true) }

    var owmDraft      by remember(owmKey)   { mutableStateOf(owmKey) }
    var currencyDraft by remember(currency) { mutableStateOf(currency) }
    var customIsoInput by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", color = AmberBright, fontSize = 22.sp)

        // ── Weather ──────────────────────────────────────────────────────────
        SectionCard("Weather (OpenWeatherMap)") {
            Text(
                "Get a free API key at openweathermap.org/api — paste it here to enable the weather screen and auto weather in shot logs.",
                color = TextSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                VaultTextField(
                    owmDraft, { owmDraft = it }, "OWM API Key",
                    modifier = Modifier.weight(1f)
                )
                VaultButton("Save", small = true, onClick = { vm.saveOwmKey(owmDraft) })
            }
        }

        // ── Display ──────────────────────────────────────────────────────────
        SectionCard("Display & Units") {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Currency symbol", color = TextSecondary, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
                VaultTextField(
                    currencyDraft, { currencyDraft = it.take(3) }, "",
                    modifier = Modifier.width(60.dp)
                )
                VaultButton("Save", small = true, onClick = { vm.saveCurrency(currencyDraft) })
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Units", color = TextSecondary, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
                Text(if (isMetric) "Metric" else "Imperial",
                    color = TextPrimary, fontSize = 13.sp)
                Switch(
                    checked = isMetric,
                    onCheckedChange = { vm.saveMetric(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Amber,
                        checkedTrackColor = AmberDark
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Prefer 120 Hz display", color = TextSecondary, fontSize = 13.sp)
                    Text("Smoother scrolling on LTPO panels; uses more battery",
                        color = TextTertiary, fontSize = 11.sp)
                }
                Switch(
                    checked = highRefresh,
                    onCheckedChange = { vm.saveHighRefresh(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Amber,
                        checkedTrackColor = AmberDark
                    )
                )
            }
        }

        // ── Reminders ────────────────────────────────────────────────────────
        SectionCard("Reminders") {
            Text(
                "A daily check that notifies you about film nearing expiry, finished " +
                "rolls sitting undeveloped, and ageing mixed chemistry.",
                color = TextSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enable reminders", color = TextSecondary, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
                Switch(
                    checked = remindersEnabled,
                    onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.saveRemindersEnabled(on)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = AmberDark)
                )
            }
            if (remindersEnabled) {
                ReminderToggle("Film expiry (60-day warning)", remindExpiry) { vm.saveRemindExpiry(it) }
                ReminderToggle("Undeveloped rolls (3+ weeks)", remindUndeveloped) { vm.saveRemindUndeveloped(it) }
                ReminderToggle("Chemistry age (60+ days mixed)", remindChemicals) { vm.saveRemindChemicals(it) }
            }
        }

        // ── Custom ISOs ──────────────────────────────────────────────────────
        SectionCard("Custom ISO Values") {
            Text(
                "Add non-standard ISO values (e.g. 1000, 3400) for unusual films. " +
                "These appear alongside standard values in shot and film ISO pickers.",
                color = TextSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                VaultTextField(
                    customIsoInput,
                    { customIsoInput = it.filter(Char::isDigit) },
                    "ISO (e.g. 1000)",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                VaultButton("Add", small = true, onClick = {
                    customIsoInput.toIntOrNull()?.let { vm.addCustomIso(it) }
                    customIsoInput = ""
                })
            }
            if (customIsos.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                TagRow {
                    customIsos.forEach { iso ->
                        InputChip(
                            selected = false,
                            onClick = { vm.removeCustomIso(iso) },
                            label = { Text("ISO $iso", fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(Icons.Default.Delete, null,
                                    tint = TextTertiary, modifier = Modifier.size(14.dp))
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = Bg4,
                                labelColor = TextPrimary
                            )
                        )
                    }
                }
                Text("Tap chip to remove", color = TextTertiary, fontSize = 11.sp)
            }
        }

        // ── Standard ISOs reminder ───────────────────────────────────────────
        SectionCard("Standard ISOs") {
            Text(
                "Built-in ISO range: ${Constants.ISOS.first()}–${Constants.ISOS.last()}. " +
                "Add custom values above for anything outside this range.",
                color = TextSecondary, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ReminderToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = TextTertiary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = AmberDark),
            modifier = Modifier.height(24.dp)
        )
    }
}
