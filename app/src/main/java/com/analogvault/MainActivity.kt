package com.analogvault

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import kotlinx.coroutines.launch

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
        setContent { AnalogVaultTheme { VaultApp() } }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    DASH("Home",       Icons.Default.Home),
    STASH("Stash",     Icons.Default.Inventory),
    ACTIVE("Rolls",    Icons.Default.CameraRoll),
    DARK("Darkroom",   Icons.Default.Science),
    METER("Meter",     Icons.Default.WbSunny),
    WEATHER("Weather", Icons.Default.Cloud),
    STATS("Stats",     Icons.Default.BarChart),
    BACKUP("Backup",   Icons.Default.CloudDownload)
}

// Tabs shown in bottom bar (most used); rest live in drawer
private val BOTTOM_TABS = listOf(Tab.DASH, Tab.ACTIVE, Tab.DARK, Tab.METER, Tab.STASH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultApp() {
    val vm: MainViewModel = hiltViewModel()
    val rolls by vm.rolls.collectAsState()
    val activeCount by remember { derivedStateOf { rolls.count { !it.developed } } }

    // Navigation state
    // Back stack: DASH is root, each nav push appends, back pops
    var backStack    by remember { mutableStateOf(listOf(Tab.DASH)) }
    var activeSubTab     by remember { mutableIntStateOf(0) }
    var meterShutter     by remember { mutableStateOf("") }
    var meterAperture    by remember { mutableStateOf("") }
    var meterIso         by remember { mutableStateOf("") }
    var initialRollId    by remember { mutableStateOf<String?>(null) }
    val currentTab = backStack.last()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun navigateTo(tab: Tab, subTab: Int = 0) {
        scope.launch { drawerState.close() }
        if (tab == Tab.ACTIVE) activeSubTab = subTab
        if (tab == currentTab) return
        backStack = if (tab == Tab.DASH) listOf(Tab.DASH)
        else (backStack.filter { it != tab } + tab).takeLast(10)
    }
    fun navigateToIndex(idx: Int, subTab: Int = 0, rollId: String? = null) {
        initialRollId = rollId
        Tab.entries.getOrNull(idx)?.let { navigateTo(it, subTab) }
    }

    // Back: pop stack; from DASH let system exit
    BackHandler(enabled = backStack.size > 1) {
        backStack = backStack.dropLast(1)
    }

    // Also close drawer on back if open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // Disable swipe-to-open on tabs where horizontal gestures are needed (map, camera)
    val gestureEnabled = false  // burger button only — swipe conflicts with map/camera/scroll

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gestureEnabled,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Bg2,
                drawerContentColor = TextPrimary,
                modifier = Modifier.width(280.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                // Header
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Analog Vault", color = AmberBright, fontSize = 20.sp)
                    Text("FILM & GEAR TRACKER", color = TextTertiary, fontSize = 9.sp)
                }
                HorizontalDivider(color = Border, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))

                Tab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    val label = if (tab == Tab.ACTIVE && activeCount > 0) "${tab.label} ($activeCount)" else tab.label
                    NavigationDrawerItem(
                        icon = { Icon(tab.icon, null, tint = if (selected) AmberBright else TextSecondary) },
                        label = { Text(label, color = if (selected) AmberBright else TextPrimary, fontSize = 14.sp) },
                        selected = selected,
                        onClick = { navigateTo(tab) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = AmberDark.copy(alpha = 0.25f),
                            unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }

                Spacer(Modifier.weight(1f))
                HorizontalDivider(color = Border, modifier = Modifier.padding(horizontal = 16.dp))
                // Back stack breadcrumb
                if (backStack.size > 1) {
                    Text(
                        backStack.joinToString(" › ") { it.label },
                        color = TextTertiary, fontSize = 10.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    ) {
        Scaffold(
            containerColor = Bg,
            contentColor = TextPrimary,
            topBar = {
                Column(
                    modifier = Modifier
                        .background(Bg2)
                        .statusBarsPadding()
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Amber)
                        }
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text("Analog Vault", color = AmberBright, fontSize = 20.sp)
                            Text("FILM & GEAR TRACKER", color = TextTertiary, fontSize = 8.sp)
                        }
                        // Current tab breadcrumb
                        if (backStack.size > 1) {
                            IconButton(onClick = { backStack = backStack.dropLast(1) }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                            }
                        }
                        Text(currentTab.label, color = TextSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(end = 8.dp))
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Bg2,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    BOTTOM_TABS.forEach { tab ->
                        val label = if (tab == Tab.ACTIVE && activeCount > 0) "Rolls($activeCount)" else tab.label
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { navigateTo(tab) },
                            icon = { Icon(tab.icon, null, modifier = Modifier.size(20.dp)) },
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
                    // "More" button opens drawer
                    NavigationBarItem(
                        selected = currentTab in listOf(Tab.WEATHER, Tab.STATS, Tab.BACKUP),
                        onClick = { scope.launch { drawerState.open() } },
                        icon = { Icon(Icons.Default.MoreHoriz, null, modifier = Modifier.size(20.dp)) },
                        label = { Text("More", fontSize = 8.sp) },
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
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(Bg)) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn(animationSpec = androidx.compose.animation.core.tween(100)) togetherWith fadeOut(animationSpec = androidx.compose.animation.core.tween(100)) },
                    label = "tab_transition"
                ) { tab ->
                when (tab) {
                    Tab.DASH    -> DashboardScreen(vm, onNavigate = { idx, sub, rollId -> navigateToIndex(idx, sub, rollId) })
                    Tab.STASH   -> StashScreen(vm)
                    Tab.ACTIVE  -> ActiveScreen(
                    vm = vm,
                    initialSubTab = activeSubTab,
                    initialRollId = initialRollId.also { initialRollId = null },
                    meterShutter = meterShutter.also { meterShutter = "" },
                    meterAperture = meterAperture.also { meterAperture = "" },
                    meterIso = meterIso.also { meterIso = "" }
                )
                    Tab.DARK    -> DarkroomScreen(vm)
                    Tab.METER   -> MeterScreen(vm, onUseInShot = { sh, ap, iso ->
                    meterShutter = sh; meterAperture = ap; meterIso = iso
                    navigateTo(Tab.ACTIVE, 0)
                })
                    Tab.WEATHER -> WeatherScreen(vm)
                    Tab.STATS   -> StatsScreen(vm)
                    Tab.BACKUP  -> BackupScreen()
                }
                } // AnimatedContent
            }
        }
    }
}
