package com.analogvault

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
class AnalogVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Init osmdroid tile cache before any MapView is created
        com.analogvault.ui.components.initOsmdroid(this)
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Tell the LTPO display to stay at 120 Hz while this window is visible.
        //
        // Without this, the panel's adaptive-rate algorithm interprets uneven frame
        // delivery (normal during heavy flings on Mali) as an idle signal and drops
        // to 60/10 Hz.  That makes the *next* frame even harder to hit, creating a
        // feedback loop that shows up as ~40 fps during fast scroll on the S22 Ultra
        // even though the GPU is capable.  Fixed-rate displays (A13, etc.) never
        // enter this loop, which is why they appear smooth despite being less powerful.
        //
        // Note: the previous FLAG_HARDWARE_ACCELERATED + gradle.properties
        // "android.graphics.renderer=opengl" approach had no effect — HA is already
        // on by default for every API-26+ app, and that gradle key is not recognised
        // by the build system or HWUI.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply { preferredRefreshRate = 120f }
        }

        setContent { AnalogVaultTheme { VaultApp() } }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    DASH    ("Home",     Icons.Default.Home),
    STASH   ("Stash",   Icons.Default.Inventory),
    ACTIVE  ("Loaded",  Icons.Default.CameraRoll),
    METER   ("Meter",   Icons.Default.WbSunny),
    WEATHER ("Weather", Icons.Default.Cloud),
    MORE    ("More",    Icons.Default.MoreHoriz),
    // "More" sub-items — not shown in bottom bar directly
    DARK    ("Darkroom",Icons.Default.Science),
    STATS   ("Stats",   Icons.Default.BarChart),
    BACKUP  ("Backup",  Icons.Default.CloudDownload),
    SETTINGS("Settings",Icons.Default.Settings)
}

private val BOTTOM_TABS = listOf(Tab.DASH, Tab.STASH, Tab.ACTIVE, Tab.METER, Tab.WEATHER, Tab.MORE)
private val MORE_TABS   = listOf(Tab.DARK, Tab.STATS, Tab.BACKUP, Tab.SETTINGS)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultApp() {
    val vm: MainViewModel = hiltViewModel()
    val rolls by vm.rolls.collectAsState()
    val activeCount by remember { derivedStateOf { rolls.count { !it.finished && !it.developed } } }

    var activeSubTab by remember { mutableIntStateOf(0) }
    var initialRollId by remember { mutableStateOf<String?>(null) }
    var meterShutter  by remember { mutableStateOf("") }
    var meterAperture by remember { mutableStateOf("") }
    var meterIso      by remember { mutableStateOf("") }

    // Back stack: DASH is always the root, never popped
    val backStack = remember { androidx.compose.runtime.snapshots.SnapshotStateList<Tab>().also { it.add(Tab.DASH) } }
    val currentTab by remember { derivedStateOf { backStack.last() } }

    fun navigateTo(tab: Tab, subTab: Int = 0) {
        if (tab == Tab.ACTIVE) activeSubTab = subTab
        // Don't push duplicates — just bring to front
        if (backStack.last() != tab) backStack.add(tab)
    }

    // Back: pop the stack; if only root remains, let system handle (exit)
    BackHandler(enabled = backStack.size > 1) {
        backStack.removeLastOrNull()
    }

    val isMoreSub = currentTab in MORE_TABS

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Bg2, tonalElevation = 0.dp) {
                BOTTOM_TABS.forEach { tab ->
                    val selected = currentTab == tab || (tab == Tab.MORE && isMoreSub)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTo(tab) },
                        icon = {
                            BadgedBox(badge = {
                                if (tab == Tab.ACTIVE && activeCount > 0)
                                    Badge(containerColor = Amber) {
                                        Text(activeCount.toString(), color = Bg, fontSize = 10.sp)
                                    }
                            }) { Icon(tab.icon, tab.label) }
                        },
                        label = { Text(tab.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Amber,
                            selectedTextColor = Amber,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = AmberDark.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 12 }) togetherWith
                (fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { -it / 12 })
            },
            modifier = Modifier.padding(padding)
        ) { tab ->
            when (tab) {
                Tab.DASH     -> DashboardScreen(vm,
                    onNavigate = { tabIndex, subTab, rollId ->
                        if (rollId != null) initialRollId = rollId
                        val target = when (tabIndex) {
                            1 -> Tab.STASH;  2 -> Tab.ACTIVE; 3 -> Tab.DARK
                            4 -> Tab.METER;  5 -> Tab.WEATHER; 6 -> Tab.STATS
                            else -> Tab.DASH
                        }
                        navigateTo(target, subTab)
                    })
                Tab.STASH    -> StashScreen(vm)
                Tab.ACTIVE   -> ActiveScreen(
                    vm = vm,
                    initialSubTab = activeSubTab,
                    initialRollId = initialRollId.also { initialRollId = null },
                    meterShutter  = meterShutter,
                    meterAperture = meterAperture,
                    meterIso      = meterIso,
                    onMeterConsumed      = { meterShutter = ""; meterAperture = ""; meterIso = "" },
                    onNavigateToDarkroom = { navigateTo(Tab.DARK) }
                )
                Tab.METER    -> MeterScreen(vm, onUseInShot = { sh, ap, iso ->
                    meterShutter = sh; meterAperture = ap; meterIso = iso
                    navigateTo(Tab.ACTIVE, 0)
                })
                Tab.WEATHER  -> WeatherScreen(vm)
                Tab.MORE     -> MoreScreen(currentSub = null, onNavigate = { navigateTo(it) })
                Tab.DARK     -> DarkroomScreen(vm)
                Tab.STATS    -> StatsScreen(vm)
                Tab.BACKUP   -> BackupScreen()
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun MoreScreen(currentSub: Tab?, onNavigate: (Tab) -> Unit) {
    val items = listOf(
        Tab.DARK     to "Darkroom timers, develop logs and scan logs",
        Tab.STATS    to "Roll statistics, cost breakdown, shot map",
        Tab.BACKUP   to "Export and import your vault data",
        Tab.SETTINGS to "OWM key, currency, units, custom ISOs"
    )
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("More", color = AmberBright, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        items.forEach { (tab, subtitle) ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (currentSub == tab) AmberDark.copy(alpha = 0.2f) else Bg2)
                    .border(1.dp, if (currentSub == tab) Amber else Border,
                        RoundedCornerShape(10.dp))
                    .clickable { onNavigate(tab) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(tab.icon, null, tint = if (currentSub == tab) Amber else TextSecondary,
                    modifier = Modifier.size(28.dp))
                Column {
                    Text(tab.label, color = if (currentSub == tab) Amber else TextPrimary, fontSize = 16.sp)
                    Text(subtitle, color = TextTertiary, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
