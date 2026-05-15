package com.analogvault

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.analogvault.ui.MainViewModel
import com.analogvault.ui.screens.*
import com.analogvault.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AnalogVaultApp : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnalogVaultTheme {
                VaultApp()
            }
        }
    }
}

// ─── Nav destinations ─────────────────────────────────────────────────────────

private enum class Tab(val label: String, val icon: ImageVector) {
    STASH("Stash", Icons.Default.Inventory),
    ACTIVE("Active", Icons.Default.CameraRoll),
    DARK("Darkroom", Icons.Default.Science),
    METER("Meter", Icons.Default.WbSunny),
    WEATHER("Weather", Icons.Default.Cloud),
    STATS("Stats", Icons.Default.BarChart),
    BACKUP("Backup", Icons.Default.CloudDownload)
}

// ─── Root composable ──────────────────────────────────────────────────────────

@Composable
fun VaultApp() {
    val vm: MainViewModel = hiltViewModel()
    val rolls by vm.rolls.collectAsState()

    var currentTab by remember { mutableStateOf(Tab.STASH) }
    val activeCount = rolls.count { !it.developed }

    Scaffold(
        containerColor = Bg,
        contentColor = TextPrimary,
        topBar = {
            Column(
                modifier = Modifier
                    .background(Bg2)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Analog Vault", color = AmberBright, fontSize = 22.sp)
                Text("FILM & GEAR TRACKER", color = TextTertiary, fontSize = 9.sp,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(0.15f, androidx.compose.ui.unit.TextUnitType.Em))
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Bg2,
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Tab.entries.forEach { tab ->
                    val label = if (tab == Tab.ACTIVE && activeCount > 0) "Active($activeCount)" else tab.label
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text(label, fontSize = 8.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AmberBright,
                            selectedTextColor = AmberBright,
                            unselectedIconColor = TextTertiary,
                            unselectedTextColor = TextTertiary,
                            indicatorColor = AmberDark.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Bg)
        ) {
            when (currentTab) {
                Tab.STASH   -> StashScreen(vm)
                Tab.ACTIVE  -> ActiveScreen(vm)
                Tab.DARK    -> DarkroomScreen(vm)
                Tab.METER   -> MeterScreen(vm)
                Tab.WEATHER -> WeatherScreen(vm)
                Tab.STATS   -> StatsScreen(vm)
                Tab.BACKUP  -> BackupScreen()
            }
        }
    }
}
