package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Playlist
import com.example.model.Track
import com.example.ui.components.*
import com.example.viewmodel.AeroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AeroPlayerApp(viewModel: AeroViewModel) {
    val context = LocalContext.current

    // Observe player States
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val currentPlaylist by viewModel.currentPlaylist.collectAsStateWithLifecycle()

    // Query + Lists
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredTracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val selectedPlaylistTracks by viewModel.selectedPlaylistTracks.collectAsStateWithLifecycle()

    // Shuffle & Repeat
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val isRepeatSingle by viewModel.isRepeatSingle.collectAsStateWithLifecycle()

    // Aero Custom Themes Configuration
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

    val auroraColors = when (currentTheme) {
        "claro" -> listOf(
            Color(0xFFE4F0FB), // Sparkling frosty sky blue top
            Color(0xFFBAD6F2), // Translucent daylight ice blue
            Color(0xFF90BEEB), // Bright aero breeze sky blue
            Color(0xFF70A9E3)  // Radiant soft sky-blue
        )
        "verde" -> listOf(
            Color(0xFF022A16), // Dark evergreen base
            Color(0xFF034D2A), // Forest moss green depth
            Color(0xFF007A48), // Classic windo-verde gloss
            Color(0xFF05B26F)  // Glowing emerald neon-sheen
        )
        "gris_oscuro" -> listOf(
            Color(0xFF16181C), // Deep graphite carbon
            Color(0xFF24272F), // Slate gray dark metallic
            Color(0xFF333742), // Charcoal ash dark gray
            Color(0xFF4B5162)  // Translucent premium platinum luster
        )
        else -> listOf( // "azul"
            Color(0xFF002244), // Deep Professional Midnight Blue
            Color(0xFF003C71), // Professional Royal Dark Blue
            Color(0xFF005DA3), // Professional Middle Blue
            Color(0xFF0078D7)  // Professional Radiant Blue
        )
    }

    val bubbleColor = when (currentTheme) {
        "claro" -> Color(0x3B3D93FF) 
        "verde" -> Color(0x1F00FF88) 
        "gris_oscuro" -> Color(0x22FFFFFF) 
        else -> Color(0x1600FFFF) 
    }

    val accentColor = when (currentTheme) {
        "claro" -> Color(0xFF005DA3)
        "verde" -> Color(0xFF00FF7F)
        "gris_oscuro" -> Color(0xFFC0D5E8)
        else -> Color(0xFF5ED8FF)
    }

    val cardGlowColor = when (currentTheme) {
        "claro" -> Color(0x1A1A73E8)
        "verde" -> Color(0x2B10C050)
        "gris_oscuro" -> Color(0x158CC6FF)
        else -> Color(0x2B3AD1FF)
    }

    // Permission States
    var hasPermissions by remember {
        mutableStateOf(checkAudioPermissions(context))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        hasPermissions = granted
        if (granted) {
            Toast.makeText(context, "¡Permisos aprobados! Escaneando música...", Toast.LENGTH_SHORT).show()
            viewModel.scanLocalMusic()
        } else {
            Toast.makeText(context, "Los permisos de música no se aprobaron. Jugando con música sintética.", Toast.LENGTH_LONG).show()
        }
    }

    // Trigger permission request on launch
    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            launcher.launch(permissions)
        }
    }

    // Modal Control Flags
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var trackForPlaylistSelector by remember { mutableStateOf<Track?>(null) }
    var trackForMetadataEdit by remember { mutableStateOf<Track?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val selectedTracks = remember { mutableStateListOf<Track>() }
    var showBulkDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showPlaylistSelectorForSelection by remember { mutableStateOf(false) }

    // Windows Media Player 11 navigation category state
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var activeCategory by remember { mutableStateOf(WmpCategory.BIBLIOTECA) }
    var sidebarExpanded by remember(isLandscape) { mutableStateOf(isLandscape) }

    // Layout Root Box (Cosmic gradient background with water bubbles)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Aero-style Aurora fluid background
                val auroraBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF002244), // Deep Professional Midnight Blue
                        Color(0xFF003C71), // Professional Royal Dark Blue
                        Color(0xFF005DA3), // Professional Middle Blue
                        Color(0xFF0078D7)  // Professional Radiant Blue
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
                drawRect(brush = auroraBrush)

                // Render vector water bubbles (Frutiger Aero signature!)
                drawCircle(
                    color = Color(0x12FFFFFF),
                    radius = size.width * 0.28f,
                    center = Offset(size.width * 0.15f, size.height * 0.25f)
                )
                drawCircle(
                    color = Color(0x0A00FFFF),
                    radius = size.width * 0.18f,
                    center = Offset(size.width * 0.85f, size.height * 0.45f)
                )
                drawCircle(
                    color = Color(0x06FFFFFF),
                    radius = size.width * 0.4f,
                    center = Offset(size.width * 0.5f, size.height * 0.8f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Classic Windows Aero Titlebar
            AeroTitleBar {
                Row {
                    // Quick Settings cog button
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración", tint = Color(0xFFCBE3FB))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    // Quick Rescan button as an overlay
                    IconButton(
                        onClick = {
                            if (checkAudioPermissions(context)) {
                                viewModel.scanLocalMusic()
                                Toast.makeText(context, "Sincronizando biblioteca...", Toast.LENGTH_SHORT).show()
                            } else {
                                launcher.launch(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                        arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                                    else
                                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                )
                            }
                        },
                        modifier = Modifier.testTag("rescan_library_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Escanear almacenamiento", tint = Color(0xFFCBE3FB))
                    }
                }
            }

            // 2. WMP Style Navigation Breadcrumbs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isLandscape) 12.dp else 6.dp,
                        vertical = if (isLandscape) 4.dp else 2.dp
                    )
                    .background(color = Color(0x1B000000), shape = RoundedCornerShape(4.dp))
                    .border(width = 1.dp, color = Color(0x1AFFFFFF), shape = RoundedCornerShape(4.dp))
                    .padding(
                        horizontal = if (isLandscape) 10.dp else 6.dp,
                        vertical = if (isLandscape) 6.dp else 3.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Backward arrow button (quick link back to Biblioteca standard)
                IconButton(
                    onClick = { activeCategory = WmpCategory.BIBLIOTECA },
                    enabled = activeCategory != WmpCategory.BIBLIOTECA,
                    modifier = Modifier
                        .size(if (isLandscape) 24.dp else 20.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (activeCategory != WmpCategory.BIBLIOTECA)
                                    listOf(Color(0x805ED8FF), Color(0x20005DA3))
                                else
                                    listOf(Color(0x15FFFFFF), Color(0x05FFFFFF))
                            ),
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (activeCategory != WmpCategory.BIBLIOTECA) Color(0x60FFFFFF) else Color(0x10FFFFFF),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Volver a Biblioteca",
                        tint = if (activeCategory != WmpCategory.BIBLIOTECA) Color.White else Color.Gray,
                        modifier = Modifier.size(if (isLandscape) 12.dp else 10.dp)
                    )
                }

                Spacer(modifier = Modifier.width(if (isLandscape) 10.dp else 6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Biblioteca",
                        color = Color(0xFFCBE3FB),
                        fontSize = if (isLandscape) 11.sp else 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " ► ",
                        color = Color(0x50FFFFFF),
                        fontSize = if (isLandscape) 10.sp else 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activeCategory.title,
                        color = Color.White,
                        fontSize = if (isLandscape) 11.sp else 9.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                if (isLandscape) {
                    Text(
                        text = "Windows Media Player 11",
                        color = Color(0x7DDFEFFF),
                        fontSize = 10.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            // Glass banner if permissions are disabled
            if (!hasPermissions) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(Color(0x40FF2244), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0x60FF2244), RoundedCornerShape(8.dp))
                        .clickable {
                            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            launcher.launch(permissions)
                        }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚠️ Permisos de audio desactivados. Haz clic aquí para activar y reproducir música local.",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 3. Central Dual-Pane Panel layout (WMP 11 Style)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                // Left Panel: Sticky Sidebar Navigation
                WmpSidebarPanel(
                    selectedCategory = activeCategory,
                    onCategorySelect = { activeCategory = it },
                    expanded = sidebarExpanded,
                    onToggleExpand = { sidebarExpanded = !sidebarExpanded }
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Right Panel: Dynamic Details List Panel
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    when (activeCategory) {
                        WmpCategory.BIBLIOTECA -> {
                            LibraryView(
                                filteredTracks = filteredTracks,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                searchQuery = searchQuery,
                                onSearchChange = { viewModel.searchQuery.value = it },
                                onTrackSelect = { track -> viewModel.playTrack(track, filteredTracks) },
                                onFavoriteToggle = { track -> viewModel.toggleFavorite(track) },
                                onAddPlaylist = { track -> trackForPlaylistSelector = track },
                                selectedTracks = selectedTracks,
                                onToggleSelect = { track ->
                                    if (selectedTracks.contains(track)) {
                                        selectedTracks.remove(track)
                                    } else {
                                        selectedTracks.add(track)
                                    }
                                },
                                onClearSelection = { selectedTracks.clear() },
                                onBulkAddPlaylist = { showPlaylistSelectorForSelection = true },
                                onBulkFavoriteToggle = {
                                    val anyUnfavorite = selectedTracks.any { !it.isFavorite }
                                    selectedTracks.toList().forEach { track ->
                                        if (track.isFavorite != anyUnfavorite) {
                                            viewModel.toggleFavorite(track)
                                        }
                                    }
                                    selectedTracks.clear()
                                },
                                onBulkDelete = { showBulkDeleteConfirmDialog = true },
                                onEditMetadata = { track -> trackForMetadataEdit = track }
                            )
                        }
                        WmpCategory.ARTISTAS -> {
                            val allTracksVal by viewModel.allTracks.collectAsStateWithLifecycle()
                            ArtistsView(
                                allTracks = allTracksVal,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onTrackSelect = { track -> viewModel.playTrack(track, allTracksVal) },
                                onFavoriteToggle = { track -> viewModel.toggleFavorite(track) },
                                onAddPlaylist = { track -> trackForPlaylistSelector = track },
                                selectedTracks = selectedTracks,
                                onToggleSelect = { track ->
                                    if (selectedTracks.contains(track)) {
                                        selectedTracks.remove(track)
                                    } else {
                                        selectedTracks.add(track)
                                    }
                                },
                                onClearSelection = { selectedTracks.clear() },
                                onBulkAddPlaylist = { showPlaylistSelectorForSelection = true },
                                onBulkFavoriteToggle = {
                                    val anyUnfavorite = selectedTracks.any { !it.isFavorite }
                                    selectedTracks.toList().forEach { track ->
                                        if (track.isFavorite != anyUnfavorite) {
                                            viewModel.toggleFavorite(track)
                                        }
                                    }
                                    selectedTracks.clear()
                                },
                                onBulkDelete = { showBulkDeleteConfirmDialog = true },
                                onEditMetadata = { track -> trackForMetadataEdit = track }
                            )
                        }
                        WmpCategory.ALBUMES -> {
                            val allTracksVal by viewModel.allTracks.collectAsStateWithLifecycle()
                            AlbumsView(
                                allTracks = allTracksVal,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onTrackSelect = { track -> viewModel.playTrack(track, allTracksVal) },
                                onFavoriteToggle = { track -> viewModel.toggleFavorite(track) },
                                onAddPlaylist = { track -> trackForPlaylistSelector = track },
                                selectedTracks = selectedTracks,
                                onToggleSelect = { track ->
                                    if (selectedTracks.contains(track)) {
                                        selectedTracks.remove(track)
                                    } else {
                                        selectedTracks.add(track)
                                    }
                                },
                                onClearSelection = { selectedTracks.clear() },
                                onBulkAddPlaylist = { showPlaylistSelectorForSelection = true },
                                onBulkFavoriteToggle = {
                                    val anyUnfavorite = selectedTracks.any { !it.isFavorite }
                                    selectedTracks.toList().forEach { track ->
                                        if (track.isFavorite != anyUnfavorite) {
                                            viewModel.toggleFavorite(track)
                                        }
                                    }
                                    selectedTracks.clear()
                                },
                                onBulkDelete = { showBulkDeleteConfirmDialog = true },
                                onEditMetadata = { track -> trackForMetadataEdit = track }
                            )
                        }
                        WmpCategory.GENEROS -> {
                            val allTracksVal by viewModel.allTracks.collectAsStateWithLifecycle()
                            GenresView(
                                allTracks = allTracksVal,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onTrackSelect = { track -> viewModel.playTrack(track, allTracksVal) },
                                onFavoriteToggle = { track -> viewModel.toggleFavorite(track) },
                                onAddPlaylist = { track -> trackForPlaylistSelector = track },
                                selectedTracks = selectedTracks,
                                onToggleSelect = { track ->
                                    if (selectedTracks.contains(track)) {
                                        selectedTracks.remove(track)
                                    } else {
                                        selectedTracks.add(track)
                                    }
                                },
                                onClearSelection = { selectedTracks.clear() },
                                onBulkAddPlaylist = { showPlaylistSelectorForSelection = true },
                                onBulkFavoriteToggle = {
                                    val anyUnfavorite = selectedTracks.any { !it.isFavorite }
                                    selectedTracks.toList().forEach { track ->
                                        if (track.isFavorite != anyUnfavorite) {
                                            viewModel.toggleFavorite(track)
                                        }
                                    }
                                    selectedTracks.clear()
                                },
                                onBulkDelete = { showBulkDeleteConfirmDialog = true },
                                onEditMetadata = { track -> trackForMetadataEdit = track }
                            )
                        }
                        WmpCategory.PLAYLISTS -> {
                            PlaylistsPaneView(
                                playlists = playlists,
                                selectedPlaylist = selectedPlaylist,
                                selectedPlaylistTracks = selectedPlaylistTracks,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onSelectPlaylist = { viewModel.selectPlaylist(it) },
                                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                onTrackSelect = { track, list -> viewModel.playTrack(track, list) },
                                onRemoveFromPlaylist = { track, pl -> viewModel.removeTrackFromPlaylist(track, pl) },
                                onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                                onFavoriteToggle = { track -> viewModel.toggleFavorite(track) },
                                selectedTracks = selectedTracks,
                                onToggleSelect = { track ->
                                    if (selectedTracks.contains(track)) {
                                        selectedTracks.remove(track)
                                    } else {
                                        selectedTracks.add(track)
                                    }
                                },
                                onClearSelection = { selectedTracks.clear() },
                                onBulkAddPlaylist = { showPlaylistSelectorForSelection = true },
                                onBulkFavoriteToggle = {
                                    val anyUnfavorite = selectedTracks.any { !it.isFavorite }
                                    selectedTracks.toList().forEach { track ->
                                        if (track.isFavorite != anyUnfavorite) {
                                            viewModel.toggleFavorite(track)
                                        }
                                    }
                                    selectedTracks.clear()
                                },
                                onBulkDelete = { showBulkDeleteConfirmDialog = true },
                                onEditMetadata = { track -> trackForMetadataEdit = track }
                            )
                        }
                        WmpCategory.FAVORITOS -> {
                            FavoritesPaneView(
                                favoriteTracks = favoriteTracks,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onTrackSelect = { track -> viewModel.playTrack(track, favoriteTracks) },
                                onFavoriteToggle = { track -> viewModel.toggleFavorite(track) },
                                onAddPlaylist = { track -> trackForPlaylistSelector = track },
                                selectedTracks = selectedTracks,
                                onToggleSelect = { track ->
                                    if (selectedTracks.contains(track)) {
                                        selectedTracks.remove(track)
                                    } else {
                                        selectedTracks.add(track)
                                    }
                                },
                                onClearSelection = { selectedTracks.clear() },
                                onBulkAddPlaylist = { showPlaylistSelectorForSelection = true },
                                onBulkFavoriteToggle = {
                                    val anyUnfavorite = selectedTracks.any { !it.isFavorite }
                                    selectedTracks.toList().forEach { track ->
                                        if (track.isFavorite != anyUnfavorite) {
                                            viewModel.toggleFavorite(track)
                                        }
                                    }
                                    selectedTracks.clear()
                                },
                                onBulkDelete = { showBulkDeleteConfirmDialog = true },
                                onEditMetadata = { track -> trackForMetadataEdit = track }
                            )
                        }
                        WmpCategory.REPRODUCCION -> {
                            NowPlayingView(
                                viewModel = viewModel,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                currentPosition = currentPosition,
                                onSeek = { position -> viewModel.seekTo(position) },
                                isShuffle = isShuffle,
                                isRepeatSingle = isRepeatSingle,
                                onToggleShuffle = { viewModel.toggleShuffle() },
                                onToggleRepeat = { viewModel.toggleRepeatSingle() },
                                onNext = { viewModel.next() },
                                onPrevious = { viewModel.previous() }
                            )
                        }
                        WmpCategory.CONFIGURACION -> {
                            SettingsPaneView(viewModel = viewModel)
                        }
                    }
                }
            }

            // 4. Persistent Bottom Player Controls Bar
            WmpBottomPlayerBar(
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                isShuffle = isShuffle,
                isRepeatSingle = isRepeatSingle,
                onPlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.next() },
                onPrevious = { viewModel.previous() },
                onSeek = { position -> viewModel.seekTo(position) },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onToggleRepeat = { viewModel.toggleRepeatSingle() },
                onMaximizedClick = { activeCategory = WmpCategory.REPRODUCCION }
            )
        }
    }

    // Modal Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showCreatePlaylistDialog = false
            }
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteConfirmDialog) {
        BulkDeleteConfirmDialog(
            count = selectedTracks.size,
            onConfirm = { deletePhysical ->
                viewModel.deleteTracks(selectedTracks.toList(), deletePhysical)
                selectedTracks.clear()
                showBulkDeleteConfirmDialog = false
                Toast.makeText(context, "Canciones eliminadas", Toast.LENGTH_SHORT).show()
                viewModel.scanLocalMusic() // Refresh library following deletion
            },
            onDismiss = { showBulkDeleteConfirmDialog = false }
        )
    }

    // Add Track to Playlist dialog/picker
    trackForPlaylistSelector?.let { track ->
        PlaylistSelectorDialog(
            track = track,
            playlists = playlists,
            onDismiss = { trackForPlaylistSelector = null },
            onSelect = { playlist ->
                viewModel.addTrackToPlaylist(track, playlist)
                Toast.makeText(context, "Añadida a: ${playlist.name}", Toast.LENGTH_SHORT).show()
                trackForPlaylistSelector = null
            },
            onCreatePlaylistShortcut = {
                showCreatePlaylistDialog = true
            }
        )
    }

    // Playlist Selector Dialog for bulk selection
    if (showPlaylistSelectorForSelection && selectedTracks.isNotEmpty()) {
        Dialog(onDismissRequest = { showPlaylistSelectorForSelection = false }) {
            AeroClassyDialogLayout(title = "Añadir Selección a Lista") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "¿A qué lista quieres añadir las ${selectedTracks.size} canciones seleccionadas?",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (playlists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x1F001D3A), RoundedCornerShape(2.dp))
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No tienes listas guardadas.", color = Color.LightGray, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        showPlaylistSelectorForSelection = false
                                        showCreatePlaylistDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ECC))
                                ) {
                                    Text("Crear Lista Ahora", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .border(1.dp, Color(0x3BFFFFFF), RoundedCornerShape(2.dp))
                                .background(Color(0x20000000)),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            items(playlists) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(2.dp))
                                        .clickable {
                                            selectedTracks.forEach { track ->
                                                viewModel.addTrackToPlaylist(track, playlist)
                                            }
                                            Toast.makeText(context, "Canciones añadidas a ${playlist.name}", Toast.LENGTH_SHORT).show()
                                            selectedTracks.clear()
                                            showPlaylistSelectorForSelection = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = Color(0xFF6EDDFF), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(text = playlist.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPlaylistSelectorForSelection = false }) {
                            Text("Cerrar", color = Color(0xFF9FC2D8), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Metadata Manual Editor Dialog Tool
    trackForMetadataEdit?.let { track ->
        MetadataEditDialog(
            track = track,
            onDismiss = { trackForMetadataEdit = null },
            onSave = { title, artist, album, genre, year ->
                viewModel.updateTrackMetadata(track, title, artist, album, genre, year)
                Toast.makeText(context, "Metadatos actualizados", Toast.LENGTH_SHORT).show()
                trackForMetadataEdit = null
            }
        )
    }
}

/**
 * Windows Glass styled custom titlebar resembling top boundaries of Vista/Win7 windows
 */
@Composable
fun AeroTitleBar(
    actions: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val height = if (isLandscape) 44.dp else 34.dp
    val badgeSize = if (isLandscape) 24.dp else 18.dp
    val badgeIconSize = if (isLandscape) 13.dp else 9.dp
    val fontSize = if (isLandscape) 13.sp else 11.sp
    val textVal = if (isLandscape) "AeroPlayer   WMP 12 Reimagined" else "AeroPlayer"
    val systemBoxWidth = if (isLandscape) 18.dp else 12.dp
    val systemBoxHeight = if (isLandscape) 14.dp else 10.dp
    val redBoxWidth = if (isLandscape) 22.dp else 16.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x3BFFFFFF), Color(0x06FFFFFF))
                )
            )
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val y = size.height - strokeWidth / 2
                drawLine(
                    color = Color(0x25FFFFFF),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth
                )
            }
            .padding(horizontal = if (isLandscape) 16.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // WMP classic icon badge logo
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF33AAFF), Color(0xFF003F80))
                        ),
                        shape = CircleShape
                    )
                    .border(1.dp, Color(0xFF88D2FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(badgeIconSize),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(if (isLandscape) 10.dp else 6.dp))
            Text(
                text = textVal,
                color = Color(0xFFE4F3FF),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            actions()
            Spacer(modifier = Modifier.width(if (isLandscape) 6.dp else 4.dp))

            // Glossy classic titlebar system control buttons (close/min placeholders)
            Row(horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 2.dp)) {
                Box(modifier = Modifier.size(systemBoxWidth, systemBoxHeight).background(Color(0x25FFFFFF), RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.size(systemBoxWidth, systemBoxHeight).background(Color(0x25FFFFFF), RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.size(redBoxWidth, systemBoxHeight).background(Color(0x60FF4242), RoundedCornerShape(2.dp)))
            }
        }
    }
}

/**
 * Subcomponent: Glassy styled tab button
 */
@Composable
fun RowScope.AeroTabButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColors = if (selected) {
        listOf(Color(0xFF3A86C8), Color(0xFF1E5283), Color(0xFF0F3254))
    } else {
        listOf(Color(0x1AFFFFFF), Color(0x00FFFFFF))
    }

    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(brush = Brush.verticalGradient(colors = bgColors))
            .border(
                1.dp,
                if (selected) Color(0xFF4CBCFF) else Color(0x1F9FC7EE),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (selected) Color.White else Color(0xFFE6F3FF)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFFCBE3FB),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * TAB 0: Library View with Search, list view and interactive song records
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryView(
    filteredTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onTrackSelect: (Track) -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onAddPlaylist: (Track) -> Unit,
    selectedTracks: List<Track>,
    onToggleSelect: (Track) -> Unit,
    onClearSelection: () -> Unit,
    onBulkAddPlaylist: () -> Unit,
    onBulkFavoriteToggle: () -> Unit,
    onBulkDelete: () -> Unit,
    onEditMetadata: (Track) -> Unit,
    onRemoveFromPlaylist: ((Track) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Transparent compact Aero Search box (More compact as requested)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .height(48.dp) // more compact height
                .testTag("search_input"),
            placeholder = { Text("Buscar canción, artista...", color = Color(0x8DDFEFFF), fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF88C9FF), modifier = Modifier.size(16.dp)) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                }
            } else null,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0x35002244),
                unfocusedContainerColor = Color(0x15001124),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color(0xFFE2F0FD),
                focusedIndicatorColor = Color(0xFF33AAFF),
                unfocusedIndicatorColor = Color(0x3BFFFFFF)
            ),
            shape = RoundedCornerShape(2.dp), // rectangular as requested
            singleLine = true
        )

        // Bulk Selection Ribbon bar
        if (selectedTracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .background(Color(0xE5031A33), RoundedCornerShape(2.dp))
                    .border(1.dp, Color(0x705ED8FF), RoundedCornerShape(2.dp))
                    .padding(vertical = 4.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClearSelection, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar selección", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${selectedTracks.size} seleccionada(s)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Favorite bulk toggler
                    IconButton(onClick = onBulkFavoriteToggle, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Favorite, contentDescription = "Favoritos", tint = Color(0xFFFF2E63), modifier = Modifier.size(16.dp))
                    }
                    // Playlist bulk adder
                    IconButton(onClick = onBulkAddPlaylist, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = "Añadir a lista", tint = Color(0xFF6EDDFF), modifier = Modifier.size(18.dp))
                    }
                    // Delete bulk action
                    IconButton(onClick = onBulkDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Glass scrollable library panel - rectangularized
        AeroGlassCard(
            modifier = Modifier.weight(1f),
            cornerRadius = 2.dp // Vista/Win7 rectangular styling
        ) {
            if (filteredTracks.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(44.dp), tint = Color(0xFF5ED0FF))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No se encontraron canciones.",
                        color = Color(0xFFCBE3FB),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Copia tus archivos MP3 a la memoria interna de tu dispositivo y haz clic en el botón de actualización arriba.",
                        color = Color(0xAAFFFFFF),
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp) // compact arrangement spacing as requested!
                ) {
                    // Check if they are synthetic songs to notify
                    val syntheticCount = filteredTracks.count { it.isSynthetic }
                    if (syntheticCount == filteredTracks.size) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp)
                                    .background(Color(0x1F3F51B5), RoundedCornerShape(2.dp))
                                    .border(1.dp, Color(0x3F88AADD), RoundedCornerShape(2.dp))
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "💡 Reproduciendo chismes de muestra. Para tus propios MP3s locales, copia archivos a carpetas del dispositivo y pulsa Sincronizar en la esquina superior.",
                                    color = Color(0xFFBAE5FF),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    items(filteredTracks, key = { it.id }) { track ->
                        val isPlayingThis = (currentTrack?.id == track.id)
                        val isSelected = selectedTracks.contains(track)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(2.dp)) // rectangular styling
                                .background(
                                    if (isSelected) Color(0x7F0099FF)
                                    else if (isPlayingThis) Color(0x3B00C8FF)
                                    else Color(0x06FFFFFF)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF5ED8FF)
                                    else if (isPlayingThis) Color(0x605ED8FF)
                                    else Color.Transparent,
                                    RoundedCornerShape(2.dp)
                                )
                                .combinedClickable(
                                    onLongClick = {
                                        onToggleSelect(track)
                                    },
                                    onClick = {
                                        if (selectedTracks.isNotEmpty()) {
                                            onToggleSelect(track)
                                        } else {
                                            onTrackSelect(track)
                                        }
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp), // More compact padding to show more items
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left indicator icon/badge showing custom cover art or checkboxes when selecting
                            Box(
                                modifier = Modifier
                                    .size(28.dp) // compact size
                                    .background(
                                        if (isSelected) Color(0xFF00B2FF)
                                        else if (isPlayingThis) Color(0x6000FFCC)
                                        else Color(0x15FFFFFF),
                                        RoundedCornerShape(3.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF5ED8FF) else Color(0x1CFFFFFF),
                                        RoundedCornerShape(3.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = Color.White
                                    )
                                } else if (!track.coverPath.isNullOrEmpty() && java.io.File(track.coverPath).exists()) {
                                    coil.compose.AsyncImage(
                                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                            .data(java.io.File(track.coverPath))
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Carátula",
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlayingThis && isPlaying) Icons.Filled.VolumeUp else Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = if (isPlayingThis) Color(0xFF00FFCC) else Color(0xFF7ED2FF)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))

                            // Middle title and subtitle descriptions
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = Color.White,
                                    fontSize = 13.sp, // slightly smaller text for compactness
                                    fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artist} • ${track.album}",
                                    color = Color(0xFFA1CADF),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Right Action controls: Compactly arranged
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Duration text badge
                                Text(
                                    text = track.durationText,
                                    color = Color(0xFF7ED2FF),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                if (selectedTracks.isEmpty()) {
                                    // Metadata View & Manual Edit Tool Component
                                    IconButton(
                                        onClick = { onEditMetadata(track) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar Metadatos",
                                            tint = Color(0xCCFFFFFF),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // Favorite toggler
                                    IconButton(
                                        onClick = { onFavoriteToggle(track) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                            contentDescription = "Favorito",
                                            tint = if (track.isFavorite) Color(0xFFFF2E63) else Color(0x90FFFFFF),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // Playlist context action: remove if in playlist view, else add
                                    if (onRemoveFromPlaylist != null) {
                                        IconButton(
                                            onClick = { onRemoveFromPlaylist(track) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Eliminar de la lista",
                                                tint = Color(0xFFFF6B6B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { onAddPlaylist(track) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlaylistAdd,
                                                contentDescription = "Añadir a lista",
                                                tint = Color(0xCCFFFFFF),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * TAB 1: Playlist management view for custom compilations
 */
@Composable
fun PlaylistView(
    playlists: List<Playlist>,
    selectedPlaylist: Playlist?,
    selectedPlaylistTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onSelectPlaylist: (Playlist?) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onTrackSelect: (Track, List<Track>) -> Unit,
    onRemoveFromPlaylist: (Track, Playlist) -> Unit,
    onCreatePlaylistClick: () -> Unit
) {
    if (selectedPlaylist == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Colecciones de Música",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = onCreatePlaylistClick,
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("create_playlist_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ECC)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva Lista", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            AeroGlassCard(modifier = Modifier.weight(1f)) {
                if (playlists.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFF5ED0FF))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No tienes listas de reproducción.",
                            color = Color(0xFFCBE3FB),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Crea una lista arriba y añade canciones usando el icono '+' en la biblioteca.",
                            color = Color(0xAAFFFFFF),
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x12FFFFFF))
                                    .clickable { onSelectPlaylist(playlist) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = Color(0xFF4CB8FF), modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(text = playlist.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            text = "Colección multimedia local",
                                            color = Color(0xFF9FC2D8),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onDeletePlaylist(playlist) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar lista", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Show selected playlist track selection inside
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onSelectPlaylist(null) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x1F000000), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = selectedPlaylist.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(text = "${selectedPlaylistTracks.size} canciones en total", color = Color(0xFFBAE5FF), fontSize = 11.sp)
                }
            }

            AeroGlassCard(modifier = Modifier.weight(1f)) {
                if (selectedPlaylistTracks.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFF4CB5FF))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Esta lista está vacía.",
                            color = Color(0xFFCBE3FB),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Añade canciones desde la pestaña Biblioteca usando el botón '+' en cada pista.",
                            color = Color(0xAAFFFFFF),
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(selectedPlaylistTracks) { track ->
                            val isPlayingThis = (currentTrack?.id == track.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isPlayingThis) Color(0x4000C8FF) else Color(0x0BFFFFFF)
                                    )
                                    .clickable { onTrackSelect(track, selectedPlaylistTracks) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(Color(0x15FFFFFF), RoundedCornerShape(3.dp))
                                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(3.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!track.coverPath.isNullOrEmpty() && java.io.File(track.coverPath).exists()) {
                                            coil.compose.AsyncImage(
                                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                    .data(java.io.File(track.coverPath))
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = "Carátula",
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp)),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isPlayingThis && isPlaying) Icons.Filled.VolumeUp else Icons.Filled.MusicNote,
                                                contentDescription = null,
                                                tint = if (isPlayingThis) Color.White else Color(0xFF5ECEFF),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = track.title,
                                            color = Color.White,
                                            fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(text = track.artist, color = Color(0xFFA1CADF), fontSize = 10.sp)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = track.durationText, color = Color(0xFF7ED2FF), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp))

                                    // Remove from playlist action
                                    IconButton(
                                        onClick = { onRemoveFromPlaylist(track, selectedPlaylist) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Quitar", tint = Color(0xFFFF7171), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * TAB 2: Now Playing central panel view which contains spinning CD disc, queue editor and lyrics center
 */
@Composable
fun NowPlayingView(
    viewModel: AeroViewModel,
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    isShuffle: Boolean,
    isRepeatSingle: Boolean,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    var activeSubTab by remember { mutableStateOf(0) } // 0 = Visualizador, 1 = Cola, 2 = Letras

    AeroGlassCard(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp), // slightly more compact padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentTrack == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicVideo,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = Color(0x3BFFFFFF)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Windows Media Player",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Selecciona una canción de la biblioteca para empezar la reproducción con estilo.",
                        color = Color(0xBACADFEE),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                }
            } else {
                // Shiny Aero tab switcher for the Now Playing subsystems
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .background(Color(0x1F000000), RoundedCornerShape(4.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val subTabTitles = listOf("Reproductor", "Cola de Temas", "Letras")
                    subTabTitles.forEachIndexed { sIdx, sTitle ->
                        val isSubSelected = (activeSubTab == sIdx)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isSubSelected) Color(0x3B0088DD) else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (isSubSelected) Color(0x805ED8FF) else Color.Transparent,
                                    RoundedCornerShape(3.dp)
                                )
                                .clickable { activeSubTab = sIdx }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sTitle,
                                color = if (isSubSelected) Color.White else Color(0xBACADFEE),
                                fontSize = 11.sp,
                                fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                when (activeSubTab) {
                    0 -> {
                        // SUB-TAB 0: Traditional CD Spinner & Bars Equalizer
                        Spacer(modifier = Modifier.weight(1f))
                        AlbumArtView(track = currentTrack, isPlaying = isPlaying)
                        Spacer(modifier = Modifier.weight(0.8f))

                        // Track Title info
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentTrack.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${currentTrack.artist} • ${currentTrack.album}",
                                color = Color(0xFFBAE5FF),
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.weight(0.8f))

                        // Procedural Bars Equalizer visualizer
                        VisualizerCanvas(isPlaying = isPlaying)

                        Spacer(modifier = Modifier.weight(0.8f))

                        if (!currentTrack.genre.isNullOrEmpty() || !currentTrack.year.isNullOrEmpty()) {
                            Text(
                                text = listOfNotNull(
                                    currentTrack.genre?.takeIf { it.isNotBlank() }?.let { "Género: $it" },
                                    currentTrack.year?.takeIf { it.isNotBlank() }?.let { "Año: $it" }
                                ).joinToString("   •   "),
                                color = Color(0xFFA2CADF),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        // Miniature Glass playback statistics (playing info bits)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x1F000000), RoundedCornerShape(2.dp))
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("MUESTRA", color = Color(0x8DDFEFFF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (currentTrack.isSynthetic) "PCM WAV" else "LOCAL MP3",
                                    color = Color(0xFF4CB8FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CANALES", color = Color(0x8DDFEFFF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (currentTrack.isSynthetic) "MONO" else "ESTÉREO",
                                    color = Color(0xFF4CB8FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("FRECUENCIA", color = Color(0x8DDFEFFF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (currentTrack.isSynthetic) "22,050 KHZ" else "44,100 KHZ",
                                    color = Color(0xFF4CB8FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    1 -> {
                        // SUB-TAB 1: Interactive Queue Editor
                        val queuePlaylist by viewModel.currentPlaylist.collectAsStateWithLifecycle()

                        Text(
                            text = "Cola de Reproducción (${queuePlaylist.size} temas)",
                            color = Color(0xFF8AD4FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .border(1.dp, Color(0x25FFFFFF), RoundedCornerShape(2.dp))
                                .background(Color(0x10000000)),
                            contentPadding = PaddingValues(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (queuePlaylist.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No hay canciones en la cola.", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                itemsIndexed(queuePlaylist) { idx, track ->
                                    val isPlayingThis = (currentTrack.id == track.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isPlayingThis) Color(0x3B00C8FF) else Color(0x06FFFFFF),
                                                RoundedCornerShape(2.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isPlayingThis) Color(0x6000FFCC) else Color.Transparent,
                                                RoundedCornerShape(2.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isPlayingThis) Icons.Filled.VolumeUp else Icons.Filled.MusicNote,
                                                contentDescription = null,
                                                tint = if (isPlayingThis) Color(0xFF00FFCC) else Color(0xFF5ECEFF),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = track.title,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = track.artist,
                                                    color = Color.LightGray,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Move Up
                                            if (idx > 0) {
                                                IconButton(
                                                    onClick = { viewModel.moveQueueTrack(idx, idx - 1) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Subir", tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.size(24.dp))
                                            }

                                            // Move Down
                                            if (idx < queuePlaylist.size - 1) {
                                                IconButton(
                                                    onClick = { viewModel.moveQueueTrack(idx, idx + 1) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Bajar", tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.size(24.dp))
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            // Remove
                                            IconButton(
                                                onClick = { viewModel.removeTrackFromQueue(idx) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Quitar", tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // SUB-TAB 2: Manual Lyrics Reader / Custom Editor Center
                        var showEditLyricsDialog by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Letras de la Pista",
                                color = Color(0xFF8AD4FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { showEditLyricsDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ECC)),
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (currentTrack.lyrics.isNullOrBlank()) "Escribir" else "Editar",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .border(1.dp, Color(0x25FFFFFF), RoundedCornerShape(2.dp))
                                .background(Color(0x15000000))
                                .padding(12.dp)
                        ) {
                            if (currentTrack.lyrics.isNullOrBlank()) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0x3BFFFFFF), modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("No hay letras guardadas.", color = Color.LightGray, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Pulsa en \"Escribir\" para guardar letras de esta canción.", color = Color.Gray, fontSize = 10.sp)
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = currentTrack.lyrics ?: "",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        if (showEditLyricsDialog) {
                            var lyricsInput by remember { mutableStateOf(currentTrack.lyrics ?: "") }
                            Dialog(onDismissRequest = { showEditLyricsDialog = false }) {
                                AeroClassyDialogLayout(title = "Escribir Letras") {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "Escribe o pega las letras de \"${currentTrack.title}\":",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        OutlinedTextField(
                                            value = lyricsInput,
                                            onValueChange = { lyricsInput = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp),
                                            placeholder = { Text("Escribe las letras aquí...", color = Color.Gray, fontSize = 11.sp) },
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color(0x301A2B3C),
                                                unfocusedContainerColor = Color(0x15000000),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedIndicatorColor = Color(0xFF33AAFF)
                                            ),
                                            maxLines = 100
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = { showEditLyricsDialog = false }) {
                                                Text("Cancelar", color = Color(0xFF9FC2D8), fontSize = 12.sp)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Button(
                                                onClick = {
                                                    viewModel.saveLyrics(currentTrack, lyricsInput)
                                                    showEditLyricsDialog = false
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088DD))
                                            ) {
                                                Text("Guardar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Solid WMP style persistent playing orb dock toolbar placed at the footer
 */
@Composable
fun WmpBottomPlayerBar(
    currentTrack: Track?,
    isPlaying: Boolean,
    currentPosition: Long,
    isShuffle: Boolean,
    isRepeatSingle: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onMaximizedClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val duration = currentTrack?.duration ?: 0L
    val durationText = currentTrack?.durationText ?: "0:00"

    val elapsedMinutes = (currentPosition / 1000) / 60
    val elapsedSeconds = (currentPosition / 1000) % 60
    val elapsedText = String.format("%d:%02d", elapsedMinutes, elapsedSeconds)

    if (isLandscape) {
        // Landscape (Horizontal) highly compact single-row deck layout which saves huge screen space
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F1E31), // Deep Cobalt Slate
                            Color(0xFF040A12)  // Full Dark
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(vertical = 1.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Horizontal top division thin line
            Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color(0x1B5ED8FF)))

            // Left block: Compact active track details
            Row(
                modifier = Modifier
                    .weight(1.5f)
                    .clickable(onClick = onMaximizedClick)
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentTrack != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFF0091FF), Color(0xFF002F5E))
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .border(1.dp, Color(0x2BFFFFFF), RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!currentTrack.coverPath.isNullOrEmpty() && java.io.File(currentTrack.coverPath).exists()) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(java.io.File(currentTrack.coverPath))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Carátula",
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = currentTrack.title,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack.artist,
                            color = Color(0xFF8AC7EE),
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = "Sin reproducción",
                        color = Color(0x60FFFFFF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Middle Left block: Playback compact Orbs (scaled down dynamically)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                AeroMediaNavButton(
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = "Anterior",
                    tag = "previous_button_compact",
                    onClick = onPrevious,
                    baseSize = 24.dp
                )

                PlayPauseOrb(
                    isPlaying = isPlaying,
                    onClick = onPlayPause,
                    baseSize = 30.dp
                )

                AeroMediaNavButton(
                    icon = Icons.Default.SkipNext,
                    contentDescription = "Siguiente",
                    tag = "next_button_compact",
                    onClick = onNext,
                    baseSize = 24.dp
                )
            }

            // Middle Right block: Slide seekbar taking up the remaining horizontal area beautifully
            Row(
                modifier = Modifier
                    .weight(3.8f)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = elapsedText, color = Color(0xFF88C9FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)

                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { percent ->
                        if (duration > 0) {
                            onSeek((percent * duration).toLong())
                        }
                    },
                    modifier = Modifier.weight(1f).height(12.dp).testTag("playback_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF51A651), // Vista gloss green
                        inactiveTrackColor = Color(0xFFC4D5E6) // Silver
                    )
                )

                Text(text = durationText, color = Color(0xFF7ED2FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            // Right block: Compact Shuffle & Repeat controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // Shuffle selector
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) Color(0xFF00FFEA) else Color(0xAAFFFFFF),
                        modifier = Modifier.size(11.dp)
                    )
                }

                // Repeat selector
                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isRepeatSingle) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Bucle",
                        tint = if (isRepeatSingle) Color(0xFF00FFEA) else Color(0xAAFFFFFF),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    } else {
        // Vertical (Portrait) standard layout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F1E31), // Deep Cobalt Slate
                            Color(0xFF040A12)  // Full Dark
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.navigationBars) // Bottom safe area boundary
                .padding(top = 8.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
        ) {
            // Horizontal top division line (Vista styling thin border)
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x3B5ED8FF)))
            Spacer(modifier = Modifier.height(4.dp))
            // 1. Sliding Seekbar track with shiny cyan path slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = elapsedText, color = Color(0xFF88C9FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { percent ->
                        if (duration > 0) {
                            onSeek((percent * duration).toLong())
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("playback_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF51A651), // Vista gloss green
                        inactiveTrackColor = Color(0xFFC4D5E6) // Matte metallic silver-blue
                    )
                )

                Text(text = durationText, color = Color(0xFF7ED2FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Control Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left block: Small mini screen presenting active track details
                Row(
                    modifier = Modifier
                        .weight(2.2f)
                        .clickable(onClick = onMaximizedClick),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentTrack != null) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFF0091FF), Color(0xFF002F5E))
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .border(1.dp, Color(0x3BFFFFFF), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!currentTrack.coverPath.isNullOrEmpty() && java.io.File(currentTrack.coverPath).exists()) {
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                        .data(java.io.File(currentTrack.coverPath))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Carátula",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = currentTrack.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentTrack.artist,
                                color = Color(0xFF8AC7EE),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = "Sin reproducción",
                            color = Color(0x60FFFFFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Middle block: Glowing Circular ORB panel
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AeroMediaNavButton(
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = "Anterior",
                        tag = "previous_button",
                        onClick = onPrevious
                    )

                    PlayPauseOrb(
                        isPlaying = isPlaying,
                        onClick = onPlayPause
                    )

                    AeroMediaNavButton(
                        icon = Icons.Default.SkipNext,
                        contentDescription = "Siguiente",
                        tag = "next_button",
                        onClick = onNext
                    )
                }

                // Right block: Shuffle, block repeat loop buttons
                Row(
                    modifier = Modifier.weight(0.8f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle selector
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) Color(0xFF00FFEA) else Color(0xAAFFFFFF),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Repeat selector
                    IconButton(
                        onClick = onToggleRepeat,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isRepeatSingle) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Bucle",
                            tint = if (isRepeatSingle) Color(0xFF00FFEA) else Color(0xAAFFFFFF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * HELPER: Simple permission checker wrapper
 */
fun checkAudioPermissions(context: Context): Boolean {
    val readAudioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    return readAudioPermission && notificationPermission
}

/**
 * DIALOG 1: Glass styled Dialog popup to create a playlist
 */
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var textState by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        AeroClassyDialogLayout(title = "Crear Lista de Reproducción") {
            Column {
                Text(
                    text = "Introduce el nombre de la nueva lista:",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    modifier = Modifier.fillMaxWidth().testTag("playlist_name_input"),
                    placeholder = { Text("Ej: Mis Sintonías Aero", color = Color.Gray) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0x301A2B3C),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color(0xFF33AAFF)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color(0xFF9FC2D8), fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { if (textState.isNotBlank()) onCreate(textState) },
                        modifier = Modifier.testTag("submit_playlist_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088DD))
                    ) {
                        Text("Crear", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * DIALOG 2: Elegant picker to assign a song to a saved playlist
 */
@Composable
fun PlaylistSelectorDialog(
    track: Track,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Playlist) -> Unit,
    onCreatePlaylistShortcut: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        AeroClassyDialogLayout(title = "Añadir a Lista") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "¿A qué lista quieres añadir \"${track.title}\"?",
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x1F001D3A), RoundedCornerShape(8.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No tienes listas guardadas.", color = Color.LightGray, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    onCreatePlaylistShortcut()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ECC))
                            ) {
                                Text("Crear Lista Ahora", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .border(1.dp, Color(0x3BFFFFFF), RoundedCornerShape(8.dp))
                            .background(Color(0x20000000)),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onSelect(playlist) }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = Color(0xFF6EDDFF), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = playlist.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar", color = Color(0xFF9FC2D8), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Universal Dialog Frame Wrapper centered on glassy glassmorphism layers
 */
@Composable
fun AeroClassyDialogLayout(
    title: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xE0051B33), Color(0xF0020E1D))
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .border(2.dp, Color(0x605ED8FF), RoundedCornerShape(16.dp))
            .drawBehind {
                // Gloss effect on top portion with rounded corners
                val topBrush = Brush.verticalGradient(
                    colors = listOf(Color(0x28FFFFFF), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.35f
                )
                val r = 16.dp.toPx()
                drawRoundRect(
                    brush = topBrush,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            }
            .padding(16.dp)
    ) {
        Column {
            // Title Header with blue neon underline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF00FFCC), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, color = Color(0xFF8AD4FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x3BFFFFFF))
                    .padding(bottom = 12.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

/**
 * DIALOG 3: Settings Panel allows configuring Folders to Scan and Folders to Ignore
 */
@Composable
fun SettingsDialog(
    viewModel: AeroViewModel,
    onDismiss: () -> Unit
) {
    val scannedFolders by viewModel.scannedFolders.collectAsStateWithLifecycle()
    val ignoredFolders by viewModel.ignoredFolders.collectAsStateWithLifecycle()

    var newScanPath by remember { mutableStateOf("") }
    var newIgnorePath by remember { mutableStateOf("") }

    var activeSubTab by remember { mutableStateOf(0) } // 0 = Escanear, 1 = Ignorar

    Dialog(onDismissRequest = onDismiss) {
        AeroClassyDialogLayout(title = "Configuración de Biblioteca") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Settings subtabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .background(Color(0x1A000000), RoundedCornerShape(2.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val configTabs = listOf("Escanear", "Ignorar")
                    configTabs.forEachIndexed { idx, title ->
                        val isSel = (activeSubTab == idx)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSel) Color(0x3B0088DD) else Color.Transparent)
                                .clickable { activeSubTab = idx }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSel) Color.White else Color(0xBACADFEE),
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                if (activeSubTab == 0) {
                    // TAB: Scanned paths
                    Text(
                        text = "Carpetas a escanear",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Si está vacío, busca en todo el almacenamiento. Si agregas carpetas, solo escaneará aquellas seleccionadas.",
                        color = Color(0xFFA1CADF),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newScanPath,
                            onValueChange = { newScanPath = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            placeholder = { Text("Ej: Music/AeroPlayer", color = Color.Gray, fontSize = 11.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0x301A2B3C),
                                unfocusedContainerColor = Color(0x10000000),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color(0xFF33AAFF)
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (newScanPath.isNotBlank()) {
                                    viewModel.addScannedFolder(newScanPath)
                                    newScanPath = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088DD)),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("Añadir", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .border(1.dp, Color(0x25FFFFFF), RoundedCornerShape(2.dp))
                            .background(Color(0x15000000))
                            .padding(4.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (scannedFolders.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Escanear todo el dispositivo (por defecto)", color = Color.LightGray, fontSize = 11.sp)
                            }
                        } else {
                            scannedFolders.forEach { path ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x0EFFFFFF), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = path, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { viewModel.removeScannedFolder(path) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TAB: Ignored paths
                    Text(
                        text = "Carpetas a ignorar",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Los archivos de estas carpetas e hilos de audio internos de otras aplicaciones se omitirán completamente de la biblioteca.",
                        color = Color(0xFFA1CADF),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newIgnorePath,
                            onValueChange = { newIgnorePath = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            placeholder = { Text("Ej: Telegram/Telegram Audio", color = Color.Gray, fontSize = 11.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0x301A2B3C),
                                unfocusedContainerColor = Color(0x10000000),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color(0xFF33AAFF)
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                if (newIgnorePath.isNotBlank()) {
                                    viewModel.addIgnoredFolder(newIgnorePath)
                                    newIgnorePath = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088DD)),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("Añadir", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .border(1.dp, Color(0x25FFFFFF), RoundedCornerShape(2.dp))
                            .background(Color(0x15000000))
                            .padding(4.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (ignoredFolders.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Ninguna carpeta ignorada.", color = Color.LightGray, fontSize = 11.sp)
                            }
                        } else {
                            ignoredFolders.forEach { path ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x0EFFFFFF), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = path, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { viewModel.removeIgnoredFolder(path) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088DD))
                    ) {
                        Text("Aceptar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * DIALOG 4: Confirmation dialog for deleting or removing multiple tracks
 */
@Composable
fun BulkDeleteConfirmDialog(
    count: Int,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var deletePhysicalFiles by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        AeroClassyDialogLayout(title = "Eliminar Canciones") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "¿Seguro que deseas eliminar estas $count canciones de la biblioteca?",
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Checkbox to also delete files physically
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deletePhysicalFiles = !deletePhysicalFiles }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deletePhysicalFiles,
                        onCheckedChange = { deletePhysicalFiles = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF00FFCC),
                            uncheckedColor = Color.White,
                            checkmarkColor = Color(0xFF041B32)
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Eliminar archivo del dispositivo de forma permanente",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }

                if (deletePhysicalFiles) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x3BFF5252), RoundedCornerShape(2.dp))
                            .background(Color(0x22FF5252))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Peligro", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⚠️ ADVERTENCIA: Esta acción es irreversible y borrará el archivo de tu teléfono.",
                                color = Color(0xFFFF8B8B),
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color(0xFFBACADFEE), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { onConfirm(deletePhysicalFiles) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text("Eliminar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * DIALOG 5: Tool to view and edit metadata manually when necessary
 */
@Composable
fun MetadataEditDialog(
    track: Track,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.album) }
    var genre by remember { mutableStateOf(track.genre ?: "") }
    var year by remember { mutableStateOf(track.year ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        AeroClassyDialogLayout(title = "Editar Metadatos de Audio") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cover preview if exists
                if (!track.coverPath.isNullOrEmpty() && java.io.File(track.coverPath).exists()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x15000000), RoundedCornerShape(2.dp))
                            .border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(2.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(java.io.File(track.coverPath))
                                .crossfade(true)
                                .build(),
                            contentDescription = "Carátula",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(3.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Carátula Detectada",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Píxeles incrustados en etiquetas ID3 de origen.",
                                color = Color(0xFFA1CADF),
                                fontSize = 9.sp,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }

                // Title Input
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Título de Canción:", color = Color(0xFFCBE3FB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("edit_title_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0x301A2B3C),
                            unfocusedContainerColor = Color(0x10000000),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF33AAFF)
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                }

                // Artist Input
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Artista / Banda:", color = Color(0xFFCBE3FB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("edit_artist_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0x301A2B3C),
                            unfocusedContainerColor = Color(0x10000000),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF33AAFF)
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                }

                // Album Input
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Álbum Musical:", color = Color(0xFFCBE3FB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = album,
                        onValueChange = { album = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("edit_album_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0x301A2B3C),
                            unfocusedContainerColor = Color(0x10000000),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF33AAFF)
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Genre Input
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Género musical:", color = Color(0xFFCBE3FB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            modifier = Modifier.height(44.dp).testTag("edit_genre_input"),
                            placeholder = { Text("Ej: Rock, Techno", color = Color.Gray, fontSize = 11.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0x301A2B3C),
                                unfocusedContainerColor = Color(0x10000000),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color(0xFF33AAFF)
                            ),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                        )
                    }

                    // Year Input
                    Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Año de lanzamiento:", color = Color(0xFFCBE3FB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = year,
                            onValueChange = { year = it },
                            modifier = Modifier.height(44.dp).testTag("edit_year_input"),
                            placeholder = { Text("Ej: 2007, 1999", color = Color.Gray, fontSize = 11.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0x301A2B3C),
                                unfocusedContainerColor = Color(0x10000000),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color(0xFF33AAFF)
                            ),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color(0xFFBACADFEE), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(title, artist, album, genre, year)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088DD)),
                        modifier = Modifier.testTag("save_metadata_button")
                    ) {
                        Text("Guardar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Windows Media Player 11 navigation categories.
 */
enum class WmpCategory(val title: String, val icon: ImageVector) {
    BIBLIOTECA("Biblioteca", Icons.Default.LibraryMusic),
    ARTISTAS("Artistas", Icons.Default.Person),
    ALBUMES("Álbumes", Icons.Default.Album),
    GENEROS("Géneros", Icons.Default.Category),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay),
    FAVORITOS("Favoritos", Icons.Default.Favorite),
    REPRODUCCION("Reproducción", Icons.Default.PlayCircle),
    CONFIGURACION("Configuración", Icons.Default.Settings)
}

/**
 * Reusable Windows Media Player 11 glass sidebar drawer panels.
 */
@Composable
fun WmpSidebarPanel(
    selectedCategory: WmpCategory,
    onCategorySelect: (WmpCategory) -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val expandedWidth = if (isLandscape) 160.dp else 116.dp
    val collapsedWidth = if (isLandscape) 52.dp else 34.dp
    val itemPaddingVer = if (isLandscape) 8.dp else 4.dp
    val itemPaddingHor = if (isLandscape) 8.dp else 4.dp
    val iconSize = if (isLandscape) 16.dp else 13.dp
    val fontSize = if (isLandscape) 11.sp else 9.5.sp
    val spacingBetweenItems = if (isLandscape) 4.dp else 1.dp
    val topSpacerHeight = if (isLandscape) 10.dp else 3.dp

    AeroGlassCard(
        modifier = Modifier
            .width(if (expanded) expandedWidth else collapsedWidth)
            .fillMaxHeight(),
        cornerRadius = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = if (expanded) Arrangement.SpaceBetween else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (expanded) {
                    Text(
                        text = "ORGANIZAR",
                        color = Color(0xFFA5CADF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ArrowBack else Icons.Default.Menu,
                        contentDescription = "Colapsar/Expandir",
                        tint = Color(0xFF88C9FF),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(topSpacerHeight))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacingBetweenItems)
            ) {
                WmpCategory.values().forEach { category ->
                    val isSelected = selectedCategory == category
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isSelected) Color(0x3B33AAFF) else Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (isSelected) Color(0x7F5ED8FF) else Color.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                            .clickable { onCategorySelect(category) }
                            .padding(horizontal = itemPaddingHor, vertical = itemPaddingVer),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = category.title,
                            tint = if (isSelected) Color(0xFF5ED8FF) else Color(0xFF9FC2D8),
                            modifier = Modifier.size(iconSize)
                        )
                        if (expanded) {
                            Spacer(modifier = Modifier.width(if (isLandscape) 10.dp else 6.dp))
                            Text(
                                text = category.title,
                                color = if (isSelected) Color.White else Color(0xFFE2F0FD),
                                fontSize = fontSize,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Windows Media Player Artists pane.
 */
@Composable
fun ArtistsView(
    allTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackSelect: (Track) -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onAddPlaylist: (Track) -> Unit,
    selectedTracks: List<Track>,
    onToggleSelect: (Track) -> Unit,
    onClearSelection: () -> Unit,
    onBulkAddPlaylist: () -> Unit,
    onBulkFavoriteToggle: () -> Unit,
    onBulkDelete: () -> Unit,
    onEditMetadata: (Track) -> Unit
) {
    val artists = remember(allTracks) {
        listOf("Todos") + allTracks.map { it.artist }.distinct().filter { it.isNotBlank() }.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    var selectedArtist by remember { mutableStateOf("Todos") }
    var searchQuery by remember { mutableStateOf("") }

    val artistTracks = remember(allTracks, selectedArtist, searchQuery) {
        val base = if (selectedArtist == "Todos") allTracks else allTracks.filter { it.artist == selectedArtist }
        if (searchQuery.isBlank()) base else base.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        AeroGlassCard(
            modifier = Modifier
                .width(125.dp)
                .fillMaxHeight(),
            cornerRadius = 4.dp
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Text(
                        text = "Artistas (${artists.size - 1})",
                        color = Color(0xFFBACADFEE),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                items(artists) { artist ->
                    val isSelected = selectedArtist == artist
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) Color(0x3B33AAFF) else Color.Transparent)
                            .border(1.dp, if (isSelected) Color(0x7F5ED8FF) else Color.Transparent, RoundedCornerShape(3.dp))
                            .clickable { selectedArtist = artist }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFF5ED8FF) else Color(0xFFA1CADF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = artist,
                            color = if (isSelected) Color.White else Color(0xFFE2F0FD),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(modifier = Modifier.weight(1f)) {
            LibraryView(
                filteredTracks = artistTracks,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onTrackSelect = onTrackSelect,
                onFavoriteToggle = onFavoriteToggle,
                onAddPlaylist = onAddPlaylist,
                selectedTracks = selectedTracks,
                onToggleSelect = onToggleSelect,
                onClearSelection = onClearSelection,
                onBulkAddPlaylist = onBulkAddPlaylist,
                onBulkFavoriteToggle = onBulkFavoriteToggle,
                onBulkDelete = onBulkDelete,
                onEditMetadata = onEditMetadata
            )
        }
    }
}

/**
 * Windows Media Player Albums pane.
 */
@Composable
fun AlbumsView(
    allTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackSelect: (Track) -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onAddPlaylist: (Track) -> Unit,
    selectedTracks: List<Track>,
    onToggleSelect: (Track) -> Unit,
    onClearSelection: () -> Unit,
    onBulkAddPlaylist: () -> Unit,
    onBulkFavoriteToggle: () -> Unit,
    onBulkDelete: () -> Unit,
    onEditMetadata: (Track) -> Unit
) {
    val albums = remember(allTracks) {
        listOf("Todos") + allTracks.map { it.album }.distinct().filter { it.isNotBlank() }.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    var selectedAlbum by remember { mutableStateOf("Todos") }
    var searchQuery by remember { mutableStateOf("") }

    val albumTracks = remember(allTracks, selectedAlbum, searchQuery) {
        val base = if (selectedAlbum == "Todos") allTracks else allTracks.filter { it.album == selectedAlbum }
        if (searchQuery.isBlank()) base else base.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        AeroGlassCard(
            modifier = Modifier
                .width(125.dp)
                .fillMaxHeight(),
            cornerRadius = 4.dp
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Text(
                        text = "Álbumes (${albums.size - 1})",
                        color = Color(0xFFBACADFEE),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                items(albums) { album ->
                    val isSelected = selectedAlbum == album
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) Color(0x3B33AAFF) else Color.Transparent)
                            .border(1.dp, if (isSelected) Color(0x7F5ED8FF) else Color.Transparent, RoundedCornerShape(3.dp))
                            .clickable { selectedAlbum = album }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFF5ED8FF) else Color(0xFFA1CADF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = album,
                            color = if (isSelected) Color.White else Color(0xFFE2F0FD),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(modifier = Modifier.weight(1f)) {
            LibraryView(
                filteredTracks = albumTracks,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onTrackSelect = onTrackSelect,
                onFavoriteToggle = onFavoriteToggle,
                onAddPlaylist = onAddPlaylist,
                selectedTracks = selectedTracks,
                onToggleSelect = onToggleSelect,
                onClearSelection = onClearSelection,
                onBulkAddPlaylist = onBulkAddPlaylist,
                onBulkFavoriteToggle = onBulkFavoriteToggle,
                onBulkDelete = onBulkDelete,
                onEditMetadata = onEditMetadata
            )
        }
    }
}

/**
 * Windows Media Player Genres pane.
 */
@Composable
fun GenresView(
    allTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackSelect: (Track) -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onAddPlaylist: (Track) -> Unit,
    selectedTracks: List<Track>,
    onToggleSelect: (Track) -> Unit,
    onClearSelection: () -> Unit,
    onBulkAddPlaylist: () -> Unit,
    onBulkFavoriteToggle: () -> Unit,
    onBulkDelete: () -> Unit,
    onEditMetadata: (Track) -> Unit
) {
    val genres = remember(allTracks) {
        listOf("Todos") + allTracks.map { it.genre ?: "Desconocido" }.filter { it.isNotBlank() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    var selectedGenre by remember { mutableStateOf("Todos") }
    var searchQuery by remember { mutableStateOf("") }

    val genreTracks = remember(allTracks, selectedGenre, searchQuery) {
        val base = if (selectedGenre == "Todos") allTracks else allTracks.filter { (it.genre ?: "Desconocido") == selectedGenre }
        if (searchQuery.isBlank()) base else base.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        AeroGlassCard(
            modifier = Modifier
                .width(125.dp)
                .fillMaxHeight(),
            cornerRadius = 4.dp
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Text(
                        text = "Géneros (${genres.size - 1})",
                        color = Color(0xFFBACADFEE),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                items(genres) { genre ->
                    val isSelected = selectedGenre == genre
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) Color(0x3B33AAFF) else Color.Transparent)
                            .border(1.dp, if (isSelected) Color(0x7F5ED8FF) else Color.Transparent, RoundedCornerShape(3.dp))
                            .clickable { selectedGenre = genre }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFF5ED8FF) else Color(0xFFA1CADF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = genre,
                            color = if (isSelected) Color.White else Color(0xFFE2F0FD),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(modifier = Modifier.weight(1f)) {
            LibraryView(
                filteredTracks = genreTracks,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onTrackSelect = onTrackSelect,
                onFavoriteToggle = onFavoriteToggle,
                onAddPlaylist = onAddPlaylist,
                selectedTracks = selectedTracks,
                onToggleSelect = onToggleSelect,
                onClearSelection = onClearSelection,
                onBulkAddPlaylist = onBulkAddPlaylist,
                onBulkFavoriteToggle = onBulkFavoriteToggle,
                onBulkDelete = onBulkDelete,
                onEditMetadata = onEditMetadata
            )
        }
    }
}

/**
 * Windows Media Player Playlists pane integrated sidebar view.
 */
@Composable
fun PlaylistsPaneView(
    playlists: List<Playlist>,
    selectedPlaylist: Playlist?,
    selectedPlaylistTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onSelectPlaylist: (Playlist?) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onTrackSelect: (Track, List<Track>) -> Unit,
    onRemoveFromPlaylist: (Track, Playlist) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    selectedTracks: List<Track>,
    onToggleSelect: (Track) -> Unit,
    onClearSelection: () -> Unit,
    onBulkAddPlaylist: () -> Unit,
    onBulkFavoriteToggle: () -> Unit,
    onBulkDelete: () -> Unit,
    onEditMetadata: (Track) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val playlistTracksFiltered = remember(selectedPlaylistTracks, searchQuery) {
        if (searchQuery.isBlank()) selectedPlaylistTracks else selectedPlaylistTracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        AeroGlassCard(
            modifier = Modifier
                .width(130.dp)
                .fillMaxHeight(),
            cornerRadius = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Button(
                    onClick = onCreatePlaylistClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp)
                        .height(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ECC)),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Nueva Lista", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item {
                        Text(
                            text = "Mis Listas (${playlists.size})",
                            color = Color(0xFFBACADFEE),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                    items(playlists) { playlist ->
                        val isSelected = selectedPlaylist?.id == playlist.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isSelected) Color(0x3B33AAFF) else Color.Transparent)
                                .border(1.dp, if (isSelected) Color(0x7F5ED8FF) else Color.Transparent, RoundedCornerShape(3.dp))
                                .clickable { onSelectPlaylist(playlist) }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF5ED8FF) else Color(0xFFA1CADF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = playlist.name,
                                    color = if (isSelected) Color.White else Color(0xFFE2F0FD),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onDeletePlaylist(playlist) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eliminar de la lista",
                                    tint = Color(0x90FF6B6B),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (selectedPlaylist == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AeroGlassCard(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        cornerRadius = 4.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(44.dp), tint = Color(0xFF5ED0FF))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Ninguna lista seleccionada",
                                color = Color(0xFFCBE3FB),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Elige una lista de reproducción del lateral izquierdo para ver o reproducir sus temas, o crea una nueva lista.",
                                color = Color(0xAAFFFFFF),
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LibraryView(
                    filteredTracks = playlistTracksFiltered,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onTrackSelect = { track -> onTrackSelect(track, selectedPlaylistTracks) },
                    onFavoriteToggle = onFavoriteToggle,
                    onAddPlaylist = {},
                    selectedTracks = selectedTracks,
                    onToggleSelect = onToggleSelect,
                    onClearSelection = onClearSelection,
                    onBulkAddPlaylist = onBulkAddPlaylist,
                    onBulkFavoriteToggle = onBulkFavoriteToggle,
                    onBulkDelete = onBulkDelete,
                    onEditMetadata = onEditMetadata,
                    onRemoveFromPlaylist = { track -> onRemoveFromPlaylist(track, selectedPlaylist) }
                )
            }
        }
    }
}

/**
 * Windows Media Player Favorites pane view.
 */
@Composable
fun FavoritesPaneView(
    favoriteTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackSelect: (Track) -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onAddPlaylist: (Track) -> Unit,
    selectedTracks: List<Track>,
    onToggleSelect: (Track) -> Unit,
    onClearSelection: () -> Unit,
    onBulkAddPlaylist: () -> Unit,
    onBulkFavoriteToggle: () -> Unit,
    onBulkDelete: () -> Unit,
    onEditMetadata: (Track) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredFavorites = remember(favoriteTracks, searchQuery) {
        if (searchQuery.isBlank()) favoriteTracks else favoriteTracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryView(
            filteredTracks = filteredFavorites,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            onTrackSelect = onTrackSelect,
            onFavoriteToggle = onFavoriteToggle,
            onAddPlaylist = onAddPlaylist,
            selectedTracks = selectedTracks,
            onToggleSelect = onToggleSelect,
            onClearSelection = onClearSelection,
            onBulkAddPlaylist = onBulkAddPlaylist,
            onBulkFavoriteToggle = onBulkFavoriteToggle,
            onBulkDelete = onBulkDelete,
            onEditMetadata = onEditMetadata
        )
    }
}

/**
 * Windows Media Player integrated settings pane.
 */
@Composable
fun SettingsPaneView(
    viewModel: AeroViewModel
) {
    val scannedFolders by viewModel.scannedFolders.collectAsStateWithLifecycle()
    val ignoredFolders by viewModel.ignoredFolders.collectAsStateWithLifecycle()

    var newScanPath by remember { mutableStateOf("") }
    var newIgnorePath by remember { mutableStateOf("") }

    var activeSubTab by remember { mutableStateOf(0) } // 0 = Escanear, 1 = Ignorar

    AeroGlassCard(
        modifier = Modifier.fillMaxSize(),
        cornerRadius = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Configuración de Biblioteca",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .background(Color(0x1F000000), RoundedCornerShape(4.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val configTabs = listOf("Carpetas a Escanear", "Filtros de Ignorado")
                configTabs.forEachIndexed { idx, title ->
                    val isSel = (activeSubTab == idx)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSel) Color(0x3B0088DD) else Color.Transparent)
                            .border(1.dp, if (isSel) Color(0x805ED8FF) else Color.Transparent, RoundedCornerShape(3.dp))
                            .clickable { activeSubTab = idx }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSel) Color.White else Color(0xBACADFEE),
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            if (activeSubTab == 0) {
                Text(
                    text = "Carpetas a escanear",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Por defecto busca en todo el almacenamiento. Si agregas carpetas, solo escaneará el subdirectorio indicado de forma optimizada.",
                    color = Color(0xFFA1CADF),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newScanPath,
                        onValueChange = { newScanPath = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        placeholder = { Text("Ej: Music/AeroPlayer", color = Color.Gray, fontSize = 11.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0x301A2B3C),
                            unfocusedContainerColor = Color(0x10000000),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF33AAFF)
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (newScanPath.isNotBlank()) {
                                viewModel.addScannedFolder(newScanPath)
                                newScanPath = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088DD)),
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text("Añadir", fontSize = 11.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .border(1.dp, Color(0x25FFFFFF), RoundedCornerShape(2.dp))
                        .background(Color(0x15000000))
                        .padding(4.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (scannedFolders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Escanear todo el dispositivo (por defecto)", color = Color.LightGray, fontSize = 11.sp)
                        }
                    } else {
                        scannedFolders.forEach { path ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x0EFFFFFF), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = path, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { viewModel.removeScannedFolder(path) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Carpetas excluidas (Ignorar)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Introduce nombres de carpeta para omitir durante el escaneo automático de archivos multimedia (ej: WhatsApp/Media, cancioneros).",
                    color = Color(0xFFA1CADF),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newIgnorePath,
                        onValueChange = { newIgnorePath = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        placeholder = { Text("Ej: WhatsApp/Media", color = Color.Gray, fontSize = 11.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0x301A2B3C),
                            unfocusedContainerColor = Color(0x10000000),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color(0xFF33AAFF)
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (newIgnorePath.isNotBlank()) {
                                viewModel.addIgnoredFolder(newIgnorePath)
                                newIgnorePath = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088DD)),
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text("Excluir", fontSize = 11.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .border(1.dp, Color(0x25FFFFFF), RoundedCornerShape(2.dp))
                        .background(Color(0x15000000))
                        .padding(4.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (ignoredFolders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No hay filtros activos (escanea todo libremente)", color = Color.LightGray, fontSize = 11.sp)
                        }
                    } else {
                        ignoredFolders.forEach { path ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x0EFFFFFF), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = path, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { viewModel.removeIgnoredFolder(path) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

