package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.AeroDatabase
import com.example.model.Playlist
import com.example.model.PlaylistTrack
import com.example.model.Track
import com.example.service.AudioService
import com.example.synth.LocalMusicSynthesizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AeroViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "AeroViewModel"
    private val database = AeroDatabase.getDatabase(application)
    private val musicDao = database.musicDao()

    // Folder and ignore configuration state using SharedPreferences
    private val prefs = application.getSharedPreferences("aero_player_prefs", Context.MODE_PRIVATE)

    private val _scannedFolders = MutableStateFlow<List<String>>(
        prefs.getString("scanned_paths", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    )
    val scannedFolders = _scannedFolders.asStateFlow()

    private val _ignoredFolders = MutableStateFlow<List<String>>(
        prefs.getString("ignored_paths", "Android/data,.thumbnails,WhatsApp/Media")?.split(",")?.filter { it.isNotBlank() }
            ?: listOf("Android/data", ".thumbnails", "WhatsApp/Media")
    )
    val ignoredFolders = _ignoredFolders.asStateFlow()

    private val _currentTheme = MutableStateFlow<String>(
        prefs.getString("selected_theme", "azul") ?: "azul"
    )
    val currentTheme = _currentTheme.asStateFlow()

    fun setTheme(themeId: String) {
        _currentTheme.value = themeId
        prefs.edit().putString("selected_theme", themeId).apply()
    }

    fun clearDatabaseCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _scannedFolders.value = emptyList()
            _ignoredFolders.value = listOf("Android/data", ".thumbnails", "WhatsApp/Media")
            prefs.edit().putString("scanned_paths", "").putString("ignored_paths", "Android/data,.thumbnails,WhatsApp/Media").apply()
            musicDao.clearScannedTracks()
            ensureSyntheticTracksExist()
        }
    }

    fun addScannedFolder(path: String) {
        if (path.isBlank()) return
        val newList = (_scannedFolders.value + path.trim()).distinct()
        _scannedFolders.value = newList
        prefs.edit().putString("scanned_paths", newList.joinToString(",")).apply()
    }

    fun removeScannedFolder(path: String) {
        val newList = _scannedFolders.value - path
        _scannedFolders.value = newList
        prefs.edit().putString("scanned_paths", newList.joinToString(",")).apply()
    }

    fun addIgnoredFolder(path: String) {
        if (path.isBlank()) return
        val newList = (_ignoredFolders.value + path.trim()).distinct()
        _ignoredFolders.value = newList
        prefs.edit().putString("ignored_paths", newList.joinToString(",")).apply()
    }

    fun removeIgnoredFolder(path: String) {
        val newList = _ignoredFolders.value - path
        _ignoredFolders.value = newList
        prefs.edit().putString("ignored_paths", newList.joinToString(",")).apply()
    }

    // Service Connection State
    private var audioService: AudioService? = null
    private var isBound = false
    private var serviceCollectorJob: Job? = null

    // Player States mirrored from Service (or local fallbacks if unbound)
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<List<Track>>(emptyList())
    val currentPlaylist: StateFlow<List<Track>> = _currentPlaylist.asStateFlow()

    // Search Query
    val searchQuery = MutableStateFlow("")

    // Shuffle & Repeat
    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _isRepeatSingle = MutableStateFlow(false)
    val isRepeatSingle: StateFlow<Boolean> = _isRepeatSingle.asStateFlow()

    // Tracks observed reactively from Room Database
    val allTracks: StateFlow<List<Track>> = musicDao.getAllTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTracks: StateFlow<List<Track>> = musicDao.getFavoriteTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = musicDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Tracks based on current search query
    val filteredTracks: StateFlow<List<Track>> = combine(allTracks, searchQuery) { tracks, query ->
        if (query.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Playlist Tracks
    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    val selectedPlaylistTracks: StateFlow<List<Track>> = _selectedPlaylist.flatMapLatest { playlist ->
        if (playlist == null) {
            flowOf(emptyList())
        } else {
            musicDao.getPlaylistTracks(playlist.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Service Connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AudioService.AudioBinder
            audioService = binder?.getService()
            isBound = true
            Log.d(TAG, "AudioService Connected successfully")

            // Collect real-time states from AudioService
            serviceCollectorJob?.cancel()
            serviceCollectorJob = viewModelScope.launch {
                audioService?.let { srv ->
                    // Set active settings
                    srv.setShuffleEnabled(_isShuffle.value)
                    srv.setRepeatEnabled(_isRepeatSingle.value)

                    launch { srv.isPlaying.collect { _isPlaying.value = it } }
                    launch { srv.currentTrack.collect { _currentTrack.value = it } }
                    launch { srv.currentPosition.collect { _currentPosition.value = it } }
                    launch { srv.playlist.collect { _currentPlaylist.value = it } }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "AudioService Disconnected")
            isBound = false
            audioService = null
            serviceCollectorJob?.cancel()
        }
    }

    init {
        // Bind to AudioService on startup
        bindAudioService()

        // Generate synthetic tracks and scan media
        viewModelScope.launch {
            ensureSyntheticTracksExist()
            scanLocalMusicStore()
        }
    }

    private fun bindAudioService() {
        val intent = Intent(getApplication(), AudioService::class.java)
        // Start service first so it is not bound-only is temporary
        getApplication<Application>().startService(intent)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Preloads beautiful synthetic ambient waves if database is empty of tracks.
     */
    private suspend fun ensureSyntheticTracksExist() {
        withContext(Dispatchers.IO) {
            val currentTracks = musicDao.getAllTracks().first()
            val hasSynthetic = currentTracks.any { it.isSynthetic }
            if (!hasSynthetic) {
                Log.d(TAG, "Database doesn't contain synthetic songs. Generating...")
                val syntheticTracks = LocalMusicSynthesizer.generateSyntheticSongs(getApplication())
                musicDao.insertTracks(syntheticTracks)
                Log.d(TAG, "Synthetic songs generated and inserted successfully!")
            }
        }
    }

    /**
     * Scans MediaStore for local audio files and commits them to Room database cache.
     */
    fun scanLocalMusic() {
        viewModelScope.launch {
            scanLocalMusicStore()
        }
    }

    private suspend fun scanLocalMusicStore() {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Scanning MediaStore for local music files...")
            val scannedTracks = mutableListOf<Track>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            try {
                val cursor: Cursor? = getApplication<Application>().contentResolver.query(
                    uri, projection, selection, null, null
                )

                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                    while (c.moveToNext()) {
                        val mediaStoreTitle = c.getString(titleCol) ?: "Canción Desconocida"
                        val mediaStoreArtist = c.getString(artistCol) ?: "Artista Desconocido"
                        val mediaStoreAlbum = c.getString(albumCol) ?: "Álbum Desconocido"
                        val duration = c.getLong(durationCol)
                        val path = c.getString(dataCol) ?: ""

                        // Directory inclusions & exclusions filter checks
                        val isCorrectLocation = if (_scannedFolders.value.isEmpty()) {
                            true
                        } else {
                            _scannedFolders.value.any { path.contains(it, ignoreCase = true) }
                        }

                        val isIgnored = _ignoredFolders.value.any { path.contains(it, ignoreCase = true) }

                        if (isCorrectLocation && !isIgnored && path.isNotEmpty() && duration > 2000) {
                            // Extract deep metadata directly from the storage file
                            val meta = extractMetadataAndCover(path)
                            val finalTitle = if (!meta.title.isNullOrBlank()) meta.title else mediaStoreTitle
                            val finalArtist = if (!meta.artist.isNullOrBlank()) meta.artist else mediaStoreArtist
                            val finalAlbum = if (!meta.album.isNullOrBlank()) meta.album else mediaStoreAlbum

                            scannedTracks.add(
                                Track(
                                    title = finalTitle,
                                    artist = finalArtist,
                                    album = finalAlbum,
                                    duration = duration,
                                    path = path,
                                    isFavorite = false,
                                    isSynthetic = false,
                                    genre = meta.genre,
                                    year = meta.year,
                                    coverPath = meta.coverPath
                                )
                            )
                        }
                    }
                }

                if (scannedTracks.isNotEmpty()) {
                    Log.d(TAG, "Scanned ${scannedTracks.size} music tracks from MediaStore")
                    // Clear previous non-synthetic tracks and rewrite
                    musicDao.clearScannedTracks()
                    musicDao.insertTracks(scannedTracks)
                } else {
                    Log.d(TAG, "No local MediaStore tracks found or filtered out.")
                    // Also clear non-synthetic cache if no files match
                    musicDao.clearScannedTracks()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to scan MediaStore", e)
            }
        }
    }

    private fun extractMetadataAndCover(path: String): MetadataResult {
        val retriever = android.media.MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var genre: String? = null
        var year: String? = null
        var duration: Long? = null
        var coverPath: String? = null

        try {
            retriever.setDataSource(path)
            title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
            genre = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)
            year = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR) 
                ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DATE)
            val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) {
                duration = durStr.toLongOrNull()
            }

            // Extract album art byte array
            val embeddedPic = retriever.embeddedPicture
            if (embeddedPic != null && embeddedPic.isNotEmpty()) {
                val coversDir = java.io.File(getApplication<Application>().cacheDir, "covers")
                if (!coversDir.exists()) {
                    coversDir.mkdirs()
                }
                val fileName = "cover_${path.hashCode()}.jpg"
                val file = java.io.File(coversDir, fileName)
                java.io.FileOutputStream(file).use { fos ->
                    fos.write(embeddedPic)
                }
                coverPath = file.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting metadata from file at $path", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }

        return MetadataResult(title, artist, album, genre, year, duration, coverPath)
    }

    private data class MetadataResult(
        val title: String?,
        val artist: String?,
        val album: String?,
        val genre: String?,
        val year: String?,
        val duration: Long?,
        val coverPath: String?
    )

    // Manual metadata editor
    fun updateTrackMetadata(
        track: Track,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newYear: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = track.copy(
                title = newTitle.trim(),
                artist = newArtist.trim(),
                album = newAlbum.trim(),
                genre = if (newGenre.isBlank()) null else newGenre.trim(),
                year = if (newYear.isBlank()) null else newYear.trim()
            )
            musicDao.updateTrack(updated)
            
            // Sync with current playing track if updated
            if (_currentTrack.value?.id == track.id) {
                _currentTrack.value = updated
            }
        }
    }

    // Playback control wrappers
    fun playTrack(track: Track, fromList: List<Track> = emptyList()) {
        viewModelScope.launch {
            // Find its index in the target list
            val targetList = if (fromList.isNotEmpty()) fromList else allTracks.value
            val index = targetList.indexOfFirst { it.id == track.id }
            val useIndex = if (index != -1) index else 0

            audioService?.setTrackList(targetList, playImmediately = true, startIndex = useIndex)
                ?: Log.e(TAG, "AudioService is null, cannot play track!")
        }
    }

    fun playPlaylist(playlist: Playlist, tracks: List<Track>) {
        if (tracks.isNotEmpty()) {
            _selectedPlaylist.value = playlist
            audioService?.setTrackList(tracks, playImmediately = true, startIndex = 0)
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            audioService?.launchPause()
        } else {
            audioService?.launchPlay()
        }
    }

    fun next() {
        audioService?.playNext()
    }

    fun previous() {
        audioService?.playPrevious()
    }

    fun seekTo(positionMs: Long) {
        audioService?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        val newShuffle = !_isShuffle.value
        _isShuffle.value = newShuffle
        audioService?.setShuffleEnabled(newShuffle)
    }

    fun toggleRepeatSingle() {
        val newRepeat = !_isRepeatSingle.value
        _isRepeatSingle.value = newRepeat
        audioService?.setRepeatEnabled(newRepeat)
    }

    // Favorites
    fun toggleFavorite(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = track.copy(isFavorite = !track.isFavorite)
            musicDao.updateTrack(updated)
            // If the playing track was updated, mirror that change too
            if (_currentTrack.value?.id == track.id) {
                _currentTrack.value = updated
            }
        }
    }

    // Playlists
    fun createPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (name.isNotBlank()) {
                val newPl = Playlist(name = name.trim())
                musicDao.insertPlaylist(newPl)
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_selectedPlaylist.value?.id == playlist.id) {
                _selectedPlaylist.value = null
            }
            musicDao.clearPlaylistTracks(playlist.id)
            musicDao.deletePlaylist(playlist.id)
        }
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    fun addTrackToPlaylist(track: Track, playlist: Playlist) {
        viewModelScope.launch(Dispatchers.IO) {
            musicDao.addTrackToPlaylist(PlaylistTrack(playlistId = playlist.id, trackId = track.id))
        }
    }

    fun removeTrackFromPlaylist(track: Track, playlist: Playlist) {
        viewModelScope.launch(Dispatchers.IO) {
            musicDao.removeTrackFromPlaylist(playlistId = playlist.id, trackId = track.id)
        }
    }

    // Bulk deletion from library and optionally filesystem storage
    fun deleteTracks(tracks: List<Track>, deletePhysicalFiles: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (deletePhysicalFiles) {
                for (track in tracks) {
                    if (!track.isSynthetic) {
                        try {
                            val file = java.io.File(track.path)
                            if (file.exists()) {
                                file.delete()
                                Log.d(TAG, "Fichero borrado físicamente: ${track.path}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "No se pudo borrar el fichero físico: ${track.path}", e)
                        }
                    }
                }
            }
            val ids = tracks.map { it.id }
            musicDao.deleteTracksById(ids)
            Log.d(TAG, "Borrado de pistas del cache de Room: $ids")
        }
    }

    // Interactive playback queue controllers (for queue editing)
    fun removeTrackFromQueue(index: Int) {
        audioService?.removeQueueTrack(index)
    }

    fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
        audioService?.moveQueueTrack(fromIndex, toIndex)
    }

    // Custom lyrics editing per track
    fun saveLyrics(track: Track, newLyrics: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = track.copy(lyrics = newLyrics)
            musicDao.updateTrack(updated)
            if (_currentTrack.value?.id == track.id) {
                _currentTrack.value = updated
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Unbind from AudioService
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
        serviceCollectorJob?.cancel()
    }
}
