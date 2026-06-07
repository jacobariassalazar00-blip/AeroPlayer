package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
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

    // Active View Tab: 0 = Biblioteca, 1 = Listas de reproducción, 2 = Reproduciendo ahora
    var activeTab by remember { mutableStateOf(0) }

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

            // 2. Translucent Navigation Header Tabs (Vista style ribbon tabs)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .background(Color(0x1F001D3A), RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AeroTabButton(
                    label = "Biblioteca",
                    icon = Icons.Default.MusicNote,
                    selected = (activeTab == 0),
                    onClick = { activeTab = 0 }
                )
                AeroTabButton(
                    label = "Colecciones",
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    selected = (activeTab == 1),
                    onClick = { activeTab = 1 }
                )
                AeroTabButton(
                    label = "Reproducción",
                    icon = Icons.Default.PlayCircle,
                    selected = (activeTab == 2),
                    onClick = { activeTab = 2 }
                )
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

            // 3. Central Dynamic Screen View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                when (activeTab) {
                    0 -> LibraryView(
                        filteredTracks = filteredTracks,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.searchQuery.value = it },
                        onTrackSelect = { track -> viewModel.playTrack(track) },
                        onFavoriteToggle = { track -> viewModel.toggleFavorite(track) },
                        onAddPlaylist = { track -> trackForPlaylistSelector = track }
                    )
                    1 -> PlaylistView(
                        playlists = playlists,
                        selectedPlaylist = selectedPlaylist,
                        selectedPlaylistTracks = selectedPlaylistTracks,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        onSelectPlaylist = { viewModel.selectPlaylist(it) },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onTrackSelect = { track, list -> viewModel.playTrack(track, list) },
                        onRemoveFromPlaylist = { track, pl -> viewModel.removeTrackFromPlaylist(track, pl) },
                        onCreatePlaylistClick = { showCreatePlaylistDialog = true }
                    )
                    2 -> NowPlayingView(
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
            }

            // 4. Persistent Bottom Player Controls Bar (Glowing Windows Media Player circular orb deck)
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
                onMaximizedClick = { activeTab = 2 }
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
}

/**
 * Windows Glass styled custom titlebar resembling top boundaries of Vista/Win7 windows
 */
@Composable
fun AeroTitleBar(
    actions: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
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
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // WMP classic icon badge logo
            Box(
                modifier = Modifier
                    .size(26.dp)
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
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "AeroPlayer   WMP 12 Reimagined",
                color = Color(0xFFE4F3FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            actions()
            Spacer(modifier = Modifier.width(6.dp))

            // Glossy classic titlebar system control buttons (close/min placeholders)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(20.dp, 16.dp).background(Color(0x25FFFFFF), RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.size(20.dp, 16.dp).background(Color(0x25FFFFFF), RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.size(24.dp, 16.dp).background(Color(0x60FF4242), RoundedCornerShape(2.dp)))
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
@Composable
fun LibraryView(
    filteredTracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onTrackSelect: (Track) -> Unit,
    onFavoriteToggle: (Track) -> Unit,
    onAddPlaylist: (Track) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Transparent Aero Search box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("search_input"),
            placeholder = { Text("Buscar canción, artista, álbum...", color = Color(0x8DDFEFFF)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF88C9FF)) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = Color.LightGray)
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
            shape = RoundedCornerShape(12.dp)
        )

        // Glass scrollable library panel
        AeroGlassCard(modifier = Modifier.weight(1f)) {
            if (filteredTracks.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFF5ED0FF))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No se encontraron canciones.",
                        color = Color(0xFFCBE3FB),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Copia tus archivos MP3 a la memoria interna de tu dispositivo y haz clic en el botón de actualización arriba.",
                        color = Color(0xAAFFFFFF),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Check if they are synthetic songs to notify
                    val syntheticCount = filteredTracks.count { it.isSynthetic }
                    if (syntheticCount == filteredTracks.size) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp)
                                    .background(Color(0x1F3F51B5), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0x3F88AADD), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "💡 Reproduciendo chismes de muestra. Para tus propios MP3s locales, copia archivos a carpetas del dispositivo y pulsa Sincronizar en la esquina superior.",
                                    color = Color(0xFFBAE5FF),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    items(filteredTracks, key = { it.id }) { track ->
                        val isPlayingThis = (currentTrack?.id == track.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isPlayingThis) Color(0x4F00C8FF) else Color(0x08FFFFFF)
                                )
                                .border(
                                    1.dp,
                                    if (isPlayingThis) Color(0x805ED8FF) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onTrackSelect(track) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left music play indicator icon or badge
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isPlayingThis) Color(0x7000FFCC) else Color(0x20FFFFFF),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlayingThis && isPlaying) Icons.Filled.VolumeUp else Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isPlayingThis) Color.White else Color(0xFF7ED2FF)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))

                            // Middle title and subtitle descriptions
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artist} • ${track.album}",
                                    color = Color(0xFFA1CADF),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Right Action controls: Favorite and Playlist managers
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Duration text badge
                                Text(
                                    text = track.durationText,
                                    color = Color(0xFF7ED2FF),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )

                                // Favorite toggler
                                IconButton(
                                    onClick = { onFavoriteToggle(track) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = "Favorito",
                                        tint = if (track.isFavorite) Color(0xFFFF2E63) else Color(0x90FFFFFF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Playlist context adder
                                IconButton(
                                    onClick = { onAddPlaylist(track) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistAdd,
                                        contentDescription = "Añadir a lista",
                                        tint = Color(0xCCFFFFFF),
                                        modifier = Modifier.size(18.dp)
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
                                    Icon(
                                        imageVector = if (isPlayingThis && isPlaying) Icons.Filled.VolumeUp else Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = if (isPlayingThis) Color.White else Color(0xFF5ECEFF),
                                        modifier = Modifier.size(16.dp)
                                    )
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
 * TAB 2: Now Playing central panel view which contains spinning CD disc and audio visualizer mechanics
 */
@Composable
fun NowPlayingView(
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
    AeroGlassCard(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
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
                // Large Spinning CD visualizer panel
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${currentTrack.artist} • ${currentTrack.album}",
                        color = Color(0xFFBAE5FF),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.weight(0.8f))

                // Procedural Bars Equalizer visualizer
                VisualizerCanvas(isPlaying = isPlaying)

                Spacer(modifier = Modifier.weight(0.8f))

                // Miniature Glass playback statistics (playing info bits)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x1F000000), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MUESTRA", color = Color(0x8DDFEFFF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (currentTrack.isSynthetic) "PCM WAV" else "LOCAL MP3",
                            color = Color(0xFF4CB8FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CANALES", color = Color(0x8DDFEFFF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (currentTrack.isSynthetic) "MONO" else "ESTÉREO",
                            color = Color(0xFF4CB8FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FRECUENCIA", color = Color(0x8DDFEFFF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (currentTrack.isSynthetic) "22,050 KHZ" else "44,100 KHZ",
                            color = Color(0xFF4CB8FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
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
    val duration = currentTrack?.duration ?: 0L
    val durationText = currentTrack?.durationText ?: "0:00"

    val elapsedMinutes = (currentPosition / 1000) / 60
    val elapsedSeconds = (currentPosition / 1000) % 60
    val elapsedText = String.format("%d:%02d", elapsedMinutes, elapsedSeconds)

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
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
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
                // Gloss effect on top portion
                val topBrush = Brush.verticalGradient(
                    colors = listOf(Color(0x35FFFFFF), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.35f
                )
                drawRect(brush = topBrush, size = Size(size.width, size.height * 0.35f))
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
