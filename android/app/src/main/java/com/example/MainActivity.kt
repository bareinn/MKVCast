package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.ConnectionStatus
import com.example.ui.MKVCastViewModel
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.ExpandedPlayerSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.components.VideoOptionsDialog
import com.example.ui.screens.ConnectionScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.QueueScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AirflowBars
import com.example.ui.theme.AirflowBorder
import com.example.ui.theme.AirflowPanel
import com.example.ui.theme.AirflowText
import com.example.ui.theme.AirflowTextSecondary
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import com.example.ui.theme.MKVCastTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: MKVCastViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            MKVCastTheme {
                MKVCastApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_EXPANDED_PLAYER, false) == true) {
            viewModel.setPlayerExpanded(true)
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (viewModel.shouldHandleVolumeKeys()) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> { viewModel.changeVolume(0.05f); return true }
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> { viewModel.changeVolume(-0.05f); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (viewModel.shouldHandleVolumeKeys() &&
            (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) return true
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        const val EXTRA_OPEN_EXPANDED_PLAYER = "extra_open_expanded_player"
    }
}

@Composable
fun MKVCastApp(viewModel: MKVCastViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasRequestedNotificationPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Notification permission result handled gracefully */ }
    )

    val crashFile = remember { File(context.filesDir, "last_crash.txt") }
    var crashLogText by remember {
        mutableStateOf(
            if (crashFile.exists()) {
                try {
                    crashFile.readText().takeIf { it.isNotBlank() }
                } catch (_: Exception) {
                    null
                }
            } else null
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    viewModel.setAppForeground(true)
                }
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> {
                    viewModel.setAppForeground(false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val serverState by viewModel.serverState.collectAsState()
    val isServerReachable by viewModel.isServerReachable.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val optionsVideo by viewModel.optionsVideo.collectAsState()
    val optionsVideoInfo by viewModel.optionsVideoInfo.collectAsState()
    val isLoadingInfo by viewModel.isLoadingInfo.collectAsState()

    val videoToAddToPlaylist by viewModel.videoToAddToPlaylist.collectAsState()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()

    val currentFolderIndex by viewModel.currentFolderIndex.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Request POST_NOTIFICATIONS permission when playback begins for the first time on Android 13+
    val nowPlaying = serverState?.now
    LaunchedEffect(nowPlaying) {
        if (nowPlaying != null && !hasRequestedNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasRequestedNotificationPermission = true
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissUserMessage()
        }
    }

    // 0: Biblioteca, 1: Cola, 2: Playlists, 3: Ajustes
    var selectedTab by remember { mutableIntStateOf(0) }

    // Back handling logic
    BackHandler(enabled = isPlayerExpanded) {
        viewModel.setPlayerExpanded(false)
    }

    BackHandler(enabled = !isPlayerExpanded && optionsVideo != null) {
        viewModel.closeVideoOptions()
    }

    BackHandler(enabled = !isPlayerExpanded && optionsVideo == null && selectedTab == 0 && currentFolderIndex != null) {
        viewModel.navigateUp()
    }

    if (serverState == null || connectionStatus == ConnectionStatus.DISCONNECTED ||
        connectionStatus == ConnectionStatus.CONNECTING || connectionStatus == ConnectionStatus.ERROR ||
        connectionStatus == ConnectionStatus.AUTH_REQUIRED
    ) {
        ConnectionScreen(viewModel = viewModel)
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // MiniPlayer directly above navigation bar
                    val now = serverState?.now
                    val status = serverState?.status
                    if (now != null && status != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            MiniPlayer(
                                now = now,
                                status = status,
                                thumbnailUrl = viewModel.apiClient.getThumbnailUrl(now.r, now.p),
                                token = viewModel.apiClient.token,
                                onExpand = { viewModel.setPlayerExpanded(true) },
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onStop = { viewModel.stopPlayback() }
                            )
                        }
                    }

                    // Bottom Navigation Bar with 4 tabs
                    NavigationBar(
                        containerColor = AirflowBars,
                        contentColor = AirflowText,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = "Biblioteca"
                                )
                            },
                            label = { Text("Biblioteca") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF6D8DFF),
                                selectedTextColor = Color(0xFF6D8DFF),
                                indicatorColor = Color(0x336D8DFF),
                                unselectedIconColor = AirflowTextSecondary,
                                unselectedTextColor = AirflowTextSecondary
                            ),
                            modifier = Modifier.testTag("tab_library")
                        )

                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Queue,
                                    contentDescription = "Cola"
                                )
                            },
                            label = { Text("Cola") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF6D8DFF),
                                selectedTextColor = Color(0xFF6D8DFF),
                                indicatorColor = Color(0x336D8DFF),
                                unselectedIconColor = AirflowTextSecondary,
                                unselectedTextColor = AirflowTextSecondary
                            ),
                            modifier = Modifier.testTag("tab_queue")
                        )

                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = "Playlists"
                                )
                            },
                            label = { Text("Playlists") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF6D8DFF),
                                selectedTextColor = Color(0xFF6D8DFF),
                                indicatorColor = Color(0x336D8DFF),
                                unselectedIconColor = AirflowTextSecondary,
                                unselectedTextColor = AirflowTextSecondary
                            ),
                            modifier = Modifier.testTag("tab_playlists")
                        )

                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Ajustes"
                                )
                            },
                            label = { Text("Ajustes") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF6D8DFF),
                                selectedTextColor = Color(0xFF6D8DFF),
                                indicatorColor = Color(0x336D8DFF),
                                unselectedIconColor = AirflowTextSecondary,
                                unselectedTextColor = AirflowTextSecondary
                            ),
                            modifier = Modifier.testTag("tab_settings")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Tab Content
                when (selectedTab) {
                    0 -> LibraryScreen(viewModel = viewModel, state = serverState)
                    1 -> QueueScreen(viewModel = viewModel, state = serverState)
                    2 -> PlaylistsScreen(viewModel = viewModel, state = serverState)
                    3 -> SettingsScreen(viewModel = viewModel, state = serverState)
                }

                // Disconnection Banner
                AnimatedVisibility(
                    visible = !isServerReachable,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xE6FF6B6B))
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sin conexión con la PC. Reintentando...",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Video Options Dialog (Section 5)
        optionsVideo?.let { (r, file) ->
            val settings = serverState?.settings ?: com.example.data.model.ServerSettings()
            VideoOptionsDialog(
                r = r,
                video = file,
                videoInfo = optionsVideoInfo,
                isLoading = isLoadingInfo,
                settings = settings,
                onDismiss = { viewModel.closeVideoOptions() },
                onPlay = { audio, sub ->
                    viewModel.playVideo(r, file.p, audio = audio, sub = sub)
                    viewModel.closeVideoOptions()
                },
                onAddToQueue = {
                    viewModel.addToQueue(r, file.p)
                    viewModel.closeVideoOptions()
                },
                onAddToPlaylist = {
                    viewModel.closeVideoOptions()
                    viewModel.openAddToPlaylist(r, file.p)
                }
            )
        }

        // Add to Playlist Dialog
        videoToAddToPlaylist?.let { (r, p) ->
            AddToPlaylistDialog(
                playlists = serverState?.playlists ?: emptyList(),
                onSelectPlaylist = { playlistId ->
                    viewModel.addVideoToPlaylist(playlistId, r, p)
                },
                onCreateAndAdd = { name ->
                    viewModel.createPlaylist(name, initialItem = Pair(r, p))
                },
                onDismiss = { viewModel.closeAddToPlaylist() }
            )
        }

        // Expanded Player Sheet (Section 6)
        if (isPlayerExpanded) {
            val now = serverState?.now
            val status = serverState?.status
            val settings = serverState?.settings ?: com.example.data.model.ServerSettings()
            if (now != null && status != null) {
                ExpandedPlayerSheet(
                    viewModel = viewModel,
                    now = now,
                    status = status,
                    settings = settings,
                    thumbnailUrl = viewModel.apiClient.getThumbnailUrl(now.r, now.p),
                    token = viewModel.apiClient.token,
                    onDismiss = { viewModel.setPlayerExpanded(false) }
                )
            }
        }
    }

    if (crashLogText != null) {
        AlertDialog(
            onDismissRequest = {
                try { crashFile.delete() } catch (_: Exception) {}
                crashLogText = null
            },
            title = {
                Text(
                    text = "Registro de error anterior",
                    fontWeight = FontWeight.Bold,
                    color = AirflowText
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .background(Color(0xFF14171F), shape = RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = crashLogText ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Color(0xFFFFB74D)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("MKVCast Crash Log", crashLogText ?: "")
                        clipboard.setPrimaryClip(clip)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D8DFF))
                ) {
                    Text("Copiar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        try { crashFile.delete() } catch (_: Exception) {}
                        crashLogText = null
                    }
                ) {
                    Text("Cerrar", color = AirflowTextSecondary)
                }
            },
            containerColor = AirflowPanel
        )
    }
}
