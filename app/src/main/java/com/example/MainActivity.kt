package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

// --- DATA STRUCTURES ---

enum class ScreenMode {
    Simplistic,
    Complicated,
    Simulation
}

data class AppEntry(
    val packageName: String,
    val label: String,
    val isMock: Boolean = false,
    val emoji: String = "📺",
    val customColor: Long = 0xFF1E2530,
    val category: String = "Aplikace"
)

data class TvChannel(
    val name: String,
    val program: String,
    val time: String,
    val durationPercent: Float,
    val emoji: String,
    val color: Color,
    val freq: Float
)

// --- VIEWMODEL ---

class MainViewModel : ViewModel() {
    private val _screenMode = MutableStateFlow(ScreenMode.Simplistic)
    val screenMode: StateFlow<ScreenMode> = _screenMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val allApps: StateFlow<List<AppEntry>> = _allApps.asStateFlow()

    private val _isLepsitvInstalled = MutableStateFlow(false)
    val isLepsitvInstalled: StateFlow<Boolean> = _isLepsitvInstalled.asStateFlow()

    private val _simulatedChannelIndex = MutableStateFlow(0)
    val simulatedChannelIndex: StateFlow<Int> = _simulatedChannelIndex.asStateFlow()

    fun setScreenMode(mode: ScreenMode) {
        _screenMode.value = mode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSimulatedChannelIndex(index: Int) {
        _simulatedChannelIndex.value = index
    }

    fun loadApps(packageManager: PackageManager, currentPackageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val standardIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val tvIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                }

                val standardApps = packageManager.queryIntentActivities(standardIntent, 0)
                val tvApps = packageManager.queryIntentActivities(tvIntent, 0)

                val mergedRealApps = (standardApps + tvApps)
                    .distinctBy { it.activityInfo.packageName }
                    .filter { it.activityInfo.packageName != currentPackageName }
                    .map { resolveInfo ->
                        AppEntry(
                            packageName = resolveInfo.activityInfo.packageName,
                            label = resolveInfo.loadLabel(packageManager).toString(),
                            isMock = false
                        )
                    }

                _isLepsitvInstalled.value = mergedRealApps.any { it.packageName == "cz.gozet.lepsitv" }

                // Czech Mock Applications to establish beautiful TV Launcher App Grid (4 elements wide)
                val mockTvApps = listOf(
                    AppEntry("com.google.android.youtube", "YouTube", true, "📺", 0xFFD32F2F, "Video"),
                    AppEntry("com.netflix.mediaclient", "Netflix", true, "🍿", 0xFFE50914, "Zábava"),
                    AppEntry("com.hbo.hbonow", "HBO Max", true, "🔮", 0xFF5C25D9, "Zábava"),
                    AppEntry("com.disney.disneyplus", "Disney+", true, "🏰", 0xFF143094, "Zábava"),
                    AppEntry("com.spotify.music", "Spotify", true, "🎵", 0xFF1DB954, "Hudba"),
                    AppEntry("cz.ceskatelevize.ivysilani", "ČT iVysílání", true, "🇨🇿", 0xFF1E3C72, "Televize"),
                    AppEntry("cz.novatv.voyo", "Voyo", true, "🌀", 0xFF007AE6, "Zábava"),
                    AppEntry("com.rakuten.tv", "Rakuten TV", true, "📽️", 0xFFC62828, "Video"),
                    AppEntry("org.xbmc.kodi", "Kodi", true, "💿", 0xFF1976D2, "Média"),
                    AppEntry("org.videolan.vlc", "VLC Player", true, "🟠", 0xFFF57C00, "Média"),
                    AppEntry("tv.twitch.android.app", "Twitch", true, "🎮", 0xFF7B1FA2, "Hry"),
                    AppEntry("com.amazon.amazonvideo.livingroom", "Prime Video", true, "🍿", 0xFF0288D1, "Zábava"),
                    AppEntry("com.plexapp.android", "Plex", true, "🎞️", 0xFFF5B041, "Média"),
                    AppEntry("com.google.android.youtube.kids", "YouTube Kids", true, "🧸", 0xFFFBC02D, "Děti"),
                    AppEntry("cz.telly.tv", "Telly TV", true, "📡", 0xFF311B92, "Televize"),
                    AppEntry("cz.o2.o2tv", "O2 TV", true, "🦕", 0xFF0D47A1, "Televize"),
                    AppEntry("cz.skylink.livetv", "Skylink Live TV", true, "🛰️", 0xFFC2185B, "Televize"),
                    AppEntry("cz.sledovanitv.android", "SledováníTV", true, "📺", 0xFF388E3C, "Televize")
                )

                // Combine both - prioritize real packages if they actually exist on the user's OS
                val combined = ArrayList<AppEntry>()
                combined.addAll(mergedRealApps)

                mockTvApps.forEach { mock ->
                    if (mergedRealApps.none { it.packageName == mock.packageName }) {
                        combined.add(mock)
                    }
                }

                _allApps.value = combined.sortedBy { it.label.lowercase() }
            } catch (e: Exception) {
                // Safe fallback
                _allApps.value = emptyList()
            }
        }
    }
}

// --- MAIN ACTIVITY ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                TVLauncherApp()
            }
        }
    }
}

// --- COMPOSE APPLICATION ---

@Composable
fun TVLauncherApp() {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()
    
    val screenMode by viewModel.screenMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val allApps by viewModel.allApps.collectAsState()
    val isLepsitvInstalled by viewModel.isLepsitvInstalled.collectAsState()
    val simulatedChannelIndex by viewModel.simulatedChannelIndex.collectAsState()

    // Query apps on launch
    LaunchedEffect(Unit) {
        viewModel.loadApps(context.packageManager, context.packageName)
    }

    // Capture TV Back action to exit menus safely
    BackHandler(enabled = screenMode != ScreenMode.Simplistic) {
        viewModel.setScreenMode(ScreenMode.Simplistic)
    }

    // Time state
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d. MMMM yyyy", Locale("cs", "CZ"))
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now).replaceFirstChar { it.uppercase() }
            delay(1000)
        }
    }

    // Modal state for Lepší.TV installation/simulation
    var showNotInstalledDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                // Subtle glowing background element simulating ambient home theater lights
                val brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1F2B47).copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.7f, size.height * 0.2f),
                    radius = size.width * 0.6f
                )
                drawRect(brush = brush)
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Crossfade(
            targetState = screenMode,
            animationSpec = tween(durationMillis = 350),
            label = "screen_transition"
        ) { mode ->
            when (mode) {
                ScreenMode.Simplistic -> {
                    SimplisticScreen(
                        currentTime = currentTime,
                        currentDate = currentDate,
                        onLaunchLepsitv = {
                            if (isLepsitvInstalled) {
                                launchLepsitvApp(context)
                            } else {
                                showNotInstalledDialog = true
                            }
                        },
                        onSwitchToComplicated = {
                            viewModel.setScreenMode(ScreenMode.Complicated)
                        }
                    )
                }
                ScreenMode.Complicated -> {
                    ComplicatedScreen(
                        currentTime = currentTime,
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        allApps = allApps,
                        onAppClick = { app ->
                            if (app.isMock) {
                                if (app.packageName == "cz.ceskatelevize.ivysilani" || 
                                    app.packageName == "cz.novatv.voyo" ||
                                    app.packageName == "cz.telly.tv" ||
                                    app.packageName == "cz.o2.o2tv" ||
                                    app.packageName == "cz.skylink.livetv" ||
                                    app.packageName == "cz.sledovanitv.android") {
                                    // Simulated Czech television apps launching
                                    Toast.makeText(context, "Spouštím simulované vysílání ${app.label}", Toast.LENGTH_SHORT).show()
                                    viewModel.setScreenMode(ScreenMode.Simulation)
                                } else {
                                    Toast.makeText(context, "Spouštím ${app.label} (Simulace)", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                launchRealApp(context, app.packageName)
                            }
                        },
                        onSwitchToDefault = {
                            viewModel.setScreenMode(ScreenMode.Simplistic)
                        }
                    )
                }
                ScreenMode.Simulation -> {
                    TvSimulationScreen(
                        channelIndex = simulatedChannelIndex,
                        onChannelSelect = { viewModel.setSimulatedChannelIndex(it) },
                        onBackToLauncher = { viewModel.setScreenMode(ScreenMode.Simplistic) }
                    )
                }
            }
        }

        // Elegant TV Dialog when Lepší.TV is not installed on system
        if (showNotInstalledDialog) {
            Dialog(onDismissRequest = { showNotInstalledDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .width(420.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(id = R.string.not_installed_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(id = R.string.not_installed_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Dialog Buttons
                        val buttonFocusRequester = remember { FocusRequester() }
                        
                        TVButton(
                            text = stringResource(id = R.string.go_to_playstore),
                            icon = Icons.Default.ShoppingCart,
                            onClick = {
                                showNotInstalledDialog = false
                                installLepsitvApp(context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(buttonFocusRequester)
                                .testTag("install_google_play_button")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TVButton(
                            text = stringResource(id = R.string.simulate_app),
                            icon = Icons.Default.PlayArrow,
                            onClick = {
                                showNotInstalledDialog = false
                                viewModel.setScreenMode(ScreenMode.Simulation)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("simulate_tv_button")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { showNotInstalledDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("close_simulation_button")
                        ) {
                            Text(
                                text = stringResource(id = R.string.close),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Auto focus Play Store button on opening dialog
                        LaunchedEffect(Unit) {
                            delay(100)
                            try {
                                buttonFocusRequester.requestFocus()
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SIMPLISTIC SCREEN (DEFAULT MODE) ---

@Composable
fun SimplisticScreen(
    currentTime: String,
    currentDate: String,
    onLaunchLepsitv: () -> Unit,
    onSwitchToComplicated: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    // Auto-focus Lepší.TV featured app as requested: "when the Launcher Is loaded let the tv App be active"
    LaunchedEffect(Unit) {
        delay(150)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- TOP BAR: Bare clock and date (no extra icons or signals) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Můj Domov",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currentTime,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = currentDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- CENTER BLOCK: Absolute distraction-free focusing on one & only Lepší.TV application ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            TVHeroCard(
                focusRequester = focusRequester,
                onClick = onLaunchLepsitv,
                title = stringResource(id = R.string.lepsitv_label),
                desc = stringResource(id = R.string.lepsitv_desc),
                modifier = Modifier.testTag("lepsitv_card")
            )
        }

        // --- BOTTOM NAVIGATION BAR: Single high contrast button to switch to advanced screen ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TVButton(
                text = stringResource(id = R.string.switch_to_complicated),
                icon = Icons.Default.List,
                onClick = onSwitchToComplicated,
                modifier = Modifier
                    .width(280.dp)
                    .testTag("switch_to_complicated_button")
            )
        }
    }
}

// --- COMPLICATED SCREEN (ADVANCED SEARCH & GRID) ---

@Composable
fun ComplicatedScreen(
    currentTime: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    allApps: List<AppEntry>,
    onAppClick: (AppEntry) -> Unit,
    onSwitchToDefault: () -> Unit
) {
    val searchFocusRequester = remember { FocusRequester() }
    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // --- HEADER BLOCK ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Lokalizované Aplikace",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = currentTime,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- SEARCH BAR (Mandatory SEARCH_BAR) & SWITCH BUTTONS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elegant M3 style Search TextField
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.search_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocusRequester)
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                    .testTag("search_bar_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            // Switch to Simplistic Default button
            TVButton(
                text = stringResource(id = R.string.switch_to_default),
                icon = Icons.Default.Home,
                onClick = onSwitchToDefault,
                modifier = Modifier
                    .width(240.dp)
                    .testTag("switch_to_default_button")
            )
        }

        // --- APP GRID Layout: 4x5 (4 columns, multiple rows showing standard layout) ---
        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = R.string.no_apps_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredApps) { app ->
                    TVAppTileCard(
                        app = app,
                        onClick = { onAppClick(app) }
                    )
                }
            }
        }
    }
}

// --- TV SIMULATION SCREEN (MOCK EXPERIENCE OVERLAY) ---

val simulatedChannels = listOf(
    TvChannel("Lepší.TV Live", "Exkluzivní filmové vysílání z digitální knihovny", "10:00 - 11:30", 0.45f, "📺", Color(0xFFE50914), 1.2f),
    TvChannel("ČT1 HD", "Snídaně s ČT a Události v regionech ČR", "08:00 - 09:30", 0.75f, "🇨🇿", Color(0xFF0D47A1), 1.8f),
    TvChannel("ČT2 HD", "Dokument: Přírodní skvosty české krajiny", "09:00 - 10:00", 0.20f, "🏔️", Color(0xFF1B5E20), 0.7f),
    TvChannel("ČT Sport HD", "MS v ledním hokeji: Česko vs. Švýcarsko", "08:30 - 11:00", 0.60f, "🏒", Color(0xFFD50000), 2.5f),
    TvChannel("TV Nova HD", "Televizní noviny s Lucií a Reyem", "07:30 - 09:15", 0.90f, "🎬", Color(0xFF00838F), 1.5f),
    TvChannel("Prima HD", "Zprávy z domova a oblíbené diskuzní pořady", "09:15 - 10:30", 0.15f, "🌻", Color(0xFFFF8F00), 0.9f),
    TvChannel("Barrandov", "Zábavný kabaret s českými humory a skeči", "09:00 - 09:45", 0.35f, "🏛️", Color(0xFF4A148C), 2.1f)
)

@Composable
fun TvSimulationScreen(
    channelIndex: Int,
    onChannelSelect: (Int) -> Unit,
    onBackToLauncher: () -> Unit
) {
    val activeChannel = simulatedChannels[channelIndex]
    
    // Wave animation clock
    val infiniteTransition = rememberInfiniteTransition(label = "signal")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04060A))
    ) {
        // --- LEFT CHANNEL SELECTOR MENU (D-Pad Navigation friendly) ---
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(Color(0xFF0C1017))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.Red, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SIMULACE LEPŠÍ.TV",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    letterSpacing = 1.sp
                )
            }

            Text(
                text = "Seznam Stanic",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(simulatedChannels) { idx, channel ->
                    val isSelected = idx == channelIndex
                    val itemInteractionSource = remember { MutableInteractionSource() }
                    val itemIsFocused by itemInteractionSource.collectIsFocusedAsState()
                    
                    val bgSelectedColor = if (isSelected) Color(0xFFE50914).copy(alpha = 0.25f) else Color.Transparent
                    val borderFocusColor = if (itemIsFocused) Color.White else if (isSelected) Color(0xFFE50914) else Color.White.copy(alpha = 0.05f)

                    Surface(
                        onClick = { onChannelSelect(idx) },
                        shape = RoundedCornerShape(8.dp),
                        color = bgSelectedColor,
                        border = BorderStroke(2.dp, borderFocusColor),
                        modifier = Modifier
                            .fillMaxWidth(),
                        interactionSource = itemInteractionSource
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = channel.emoji,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = channel.program.take(24) + "...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Exit button to return
            TVButton(
                text = "Ukončit",
                icon = Icons.Default.ArrowBack,
                onClick = onBackToLauncher,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // --- RIGHT SCREEN STATE: Fluid Animated Video signal stream simulation & EPG Details ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .drawBehind {
                    // Generate cinematic shifting wave graphics mapping TV receiving stream
                    val drawWidth = size.width
                    val drawHeight = size.height
                    
                    // Base Dark Background
                    drawRect(color = Color(0xFF0F1524))

                    // Simulated neon spectrum waves
                    val path1 = Path()
                    val path2 = Path()
                    val freq = activeChannel.freq
                    
                    path1.moveTo(0f, drawHeight / 2)
                    path2.moveTo(0f, drawHeight * 0.6f)

                    for (x in 0..drawWidth.toInt() step 5) {
                        val yOffset1 = Math.sin((x * 0.005 + waveOffset * 0.02) * freq).toFloat() * 120f
                        val yOffset2 = Math.cos((x * 0.007 - waveOffset * 0.03) * freq).toFloat() * 90f
                        
                        path1.lineTo(x.toFloat(), drawHeight / 2 + yOffset1)
                        path2.lineTo(x.toFloat(), drawHeight * 0.55f + yOffset2)
                    }

                    // Draw first colorful waveform of the simulated stream
                    drawPath(
                        path = path1,
                        color = activeChannel.color.copy(alpha = 0.35f),
                        style = Stroke(width = 8f)
                    )

                    // Draw second overlapping secondary wave for volumetric look
                    drawPath(
                        path = path2,
                        color = Color.White.copy(alpha = 0.15f),
                        style = Stroke(width = 4f)
                    )

                    // Dynamic scanning scanlines for CRT monitor texture feel
                    var scanlineY = 0f
                    while (scanlineY < drawHeight) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.2f),
                            start = Offset(0f, scanlineY),
                            end = Offset(drawWidth, scanlineY),
                            strokeWidth = 2f
                        )
                        scanlineY += 12f
                    }
                }
                .padding(32.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            // EPG (Electronic Program Guide) details overlay block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = activeChannel.emoji,
                            fontSize = 24.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            text = activeChannel.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = activeChannel.time,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB300)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = activeChannel.program,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Realistic EPG elapsed progress bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { activeChannel.durationPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = activeChannel.color,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(activeChannel.durationPercent * 100).toInt()}% dokončeno",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Lepší.TV Přehrávač",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

// --- COMPOSE UI COMPONENTS WITH D-PAD FOCUS HIGHLIGHTS ---

@Composable
fun TVHeroCard(
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    title: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    // Scale & Color animations representing active cursor focus
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "hero_scale"
    )
    val borderCol by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.tertiary else Color.Transparent,
        animationSpec = tween(200),
        label = "hero_border"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF161B24),
        border = BorderStroke(4.dp, borderCol),
        modifier = modifier
            .scale(scale)
            .width(520.dp)
            .height(290.dp)
            .focusRequester(focusRequester),
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Striking inner red fire gradient representing luxury television
                    val brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFD3121B).copy(alpha = 0.85f),
                            Color(0xFF8B0000).copy(alpha = 0.95f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                    drawRect(brush = brush)
                    
                    // Subtly layered decorative circle
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = size.width * 0.4f,
                        center = Offset(size.width * 0.9f, size.height * 0.1f)
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "HLAVNÍ TELEVIZE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.5.sp
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 22.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Spustit Lepší.TV online",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun TVButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "btn_scale"
    )
    val bgCol by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
        label = "btn_bg"
    )
    val textCol = if (isFocused) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgCol,
        modifier = modifier
            .scale(scale)
            .height(52.dp),
        interactionSource = interactionSource,
        border = BorderStroke(
            1.5.dp, 
            if (isFocused) Color.White else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textCol,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = textCol
            )
        }
    }
}

@Composable
fun TVAppTileCard(
    app: AppEntry,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "tile_scale"
    )
    val borderCol by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.tertiary else Color.Transparent,
        label = "tile_border"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A1F2B),
        border = BorderStroke(3.dp, borderCol),
        modifier = Modifier
            .scale(scale)
            .height(110.dp),
        interactionSource = interactionSource
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TV Icon box on Left
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(app.customColor)),
                    contentAlignment = Alignment.Center
                ) {
                    if (app.isMock) {
                        Text(
                            text = app.emoji,
                            fontSize = 28.sp
                        )
                    } else {
                        // Dynamically render actual launched App Icon
                        AppIconLoader(packageName = app.packageName)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // App Details on Right
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = app.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun AppIconLoader(packageName: String) {
    val context = LocalContext.current
    var iconDrawable by remember(packageName) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(packageName) {
        val drawable = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (e: Exception) {
                null
            }
        }
        iconDrawable = drawable
    }

    if (iconDrawable != null) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView ->
                imageView.setImageDrawable(iconDrawable)
            },
            modifier = Modifier.fillMaxSize().padding(10.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF37474F)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
        }
    }
}

// --- UTILITY INTENT LAUNCHERS ---

private fun launchLepsitvApp(context: Context) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage("cz.gozet.lepsitv")
            ?: context.packageManager.getLeanbackLaunchIntentForPackage("cz.gozet.lepsitv")
        if (intent != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Aplikaci Lepší.TV nelze spustit", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Chyba při spouštění Lepší.TV", Toast.LENGTH_SHORT).show()
    }
}

private fun launchRealApp(context: Context, packageName: String) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Nelze spustit aplikaci $packageName", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Chyba při spouštění aplikace", Toast.LENGTH_SHORT).show()
    }
}

private fun installLepsitvApp(context: Context) {
    try {
        // Search first via TV Play Store market protocol
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=cz.gozet.lepsitv")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fall back to Web Play Store URL
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=cz.gozet.lepsitv")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (we: Exception) {
            Toast.makeText(context, "Obchod Google Play není dostupný", Toast.LENGTH_SHORT).show()
        }
    }
}
