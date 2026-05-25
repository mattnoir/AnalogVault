package com.analogvault.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.analogvault.data.backup.BackupResult
import com.analogvault.ui.BackupViewModel
import com.analogvault.ui.components.SectionTitle
import com.analogvault.ui.components.VaultButton
import com.analogvault.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BackupScreen() {
    val vm: BackupViewModel = hiltViewModel()
    val context = LocalContext.current
    val result        by vm.result.collectAsState()
    val busy          by vm.busy.collectAsState()
    var includePhotos by remember { mutableStateOf(true) }

    // SAF launchers
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.export(context, it, includePhotos) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.import(context, it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionTitle("Backup & Restore")

        // Info card
        InfoCard(
            icon = Icons.Default.Info,
            text = "Backup saves all your film stocks, cameras, lenses, rolls, shot logs, " +
                    "chemicals, and settings to a single JSON file you can store anywhere. " +
                    "Photo thumbnails are not included (they stay on-device).",
            color = BlueInfo
        )

        Spacer(Modifier.height(24.dp))

        // Export section
        SectionCard(title = "Export / Backup") {
            Text(
                "Creates a .analogvault.json file. Choose any folder — " +
                        "Downloads, Google Drive, a USB drive, whatever you like.",
                color = TextSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Switch(
                    checked = includePhotos,
                    onCheckedChange = { includePhotos = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Amber,
                        checkedTrackColor = AmberDark
                    )
                )
                androidx.compose.material3.Text(
                    if (includePhotos) "Include shot photos (larger file)" else "Exclude photos (smaller file)",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            VaultButton(
                text = if (busy) "Exporting…" else "⬆  Export Backup",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    exportLauncher.launch("analogvault_$timestamp.json")
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Import section
        SectionCard(title = "Import / Restore") {
            Text(
                "Opens a previously exported .json file. " +
                        "Existing records with the same ID are overwritten; " +
                        "everything else is merged in — nothing is deleted.",
                color = TextSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(14.dp))
            VaultButton(
                text = if (busy) "Importing…" else "⬇  Import Backup",
                modifier = Modifier.fillMaxWidth(),
                ghost = true,
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
            )
        }

        // Result banner
        result?.let { res ->
            Spacer(Modifier.height(20.dp))
            ResultBanner(res, onDismiss = { vm.clearResult() })
        }

        Spacer(Modifier.height(32.dp))

        // Notes
        SectionCard(title = "Notes") {
            NoteRow("File format", "JSON — human-readable, version-tagged")
            NoteRow("Photo thumbs", "Not included — stored as local file paths")
            NoteRow("Merge behaviour", "Import is additive; same ID = overwrite")
            NoteRow("OWM API key", "Included in backup")
            NoteRow("Restore to new device", "Install the app, import the file, done")
        }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(com.analogvault.ui.theme.Bg2)
            .border(1.dp, com.analogvault.ui.theme.Border, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text(title, color = Amber, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun InfoCard(icon: ImageVector, text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Text(text, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ResultBanner(result: BackupResult, onDismiss: () -> Unit) {
    val (bg, border, textColor, icon) = when (result) {
        is BackupResult.Success -> listOf(GreenOk.copy(alpha = 0.1f), GreenOk.copy(alpha = 0.4f), GreenOk, "✓")
        is BackupResult.Error   -> listOf(RedErr.copy(alpha = 0.1f),  RedErr.copy(alpha = 0.4f),  RedErr,  "⚠")
    }
    val message = when (result) {
        is BackupResult.Success -> result.message
        is BackupResult.Error   -> result.message
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg as Color)
            .border(1.dp, border as Color, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$icon  $message", color = textColor as Color, fontSize = 13.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, null, tint = textColor, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun NoteRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f, fill = false),
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
