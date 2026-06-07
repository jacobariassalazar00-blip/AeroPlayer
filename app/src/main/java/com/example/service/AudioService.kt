package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

class AudioService : Service(), AudioManager.OnAudioFocusChangeListener {

    private val binder = AudioBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    // Track Queue State
    private var playlistTracks = listOf<Track>()
    private var currentTrackIndex = -1
    private var isShuffle = false
    private var isRepeatUnit = false // false: NO repeat, true: repeat single track

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _playlist = MutableStateFlow<List<Track>>(emptyList())
    val playlist: StateFlow<List<Track>> = _playlist.asStateFlow()

    // Service scope for background updates
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionUpdateJob: Job? = null

    // Media Session
    private var mediaSession: MediaSessionCompat? = null

    companion object {
        private const val TAG = "AudioService"
        const val CHANNEL_ID = "AeroAudioChannel"
        const val NOTIFICATION_ID = 4120

        const val ACTION_PLAY_PAUSE = "com.example.aeroplayer.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.aeroplayer.NEXT"
        const val ACTION_PREVIOUS = "com.example.aeroplayer.PREVIOUS"
        const val ACTION_STOP = "com.example.aeroplayer.STOP"
    }

    inner class AudioBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate service")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        setupMediaSession()

        // Setup MediaPlayer
        initMediaPlayer()
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnCompletionListener {
                Log.d(TAG, "MediaPlayer completed track")
                onTrackCompleted()
            }
            setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer Error: what=$what extra=$extra")
                true
            }
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "AeroPlayerSession").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { launchPlay() }
                override fun onPause() { launchPause() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPrevious() }
                override fun onSeekTo(pos: Long) { seekTo(pos) }
            })
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")
        when (action) {
            ACTION_PLAY_PAUSE -> {
                if (isPlaying.value) {
                    launchPause()
                } else {
                    launchPlay()
                }
            }
            ACTION_NEXT -> {
                playNext()
            }
            ACTION_PREVIOUS -> {
                playPrevious()
            }
            ACTION_STOP -> {
                stopPlayback()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // Playback control API
    fun setTrackList(tracks: List<Track>, playImmediately: Boolean = false, startIndex: Int = 0) {
        playlistTracks = tracks
        _playlist.value = tracks
        if (tracks.isNotEmpty() && startIndex in tracks.indices) {
            currentTrackIndex = startIndex
            val track = tracks[startIndex]
            loadTrack(track, playImmediately)
        } else {
            currentTrackIndex = -1
            _currentTrack.value = null
        }
    }

    fun playTrack(track: Track) {
        val index = playlistTracks.indexOfFirst { it.id == track.id }
        if (index != -1) {
            currentTrackIndex = index
            loadTrack(track, true)
        } else {
            // Track is not in current list, put it in list single, or insert it
            setTrackList(listOf(track), true, 0)
        }
    }

    private fun loadTrack(track: Track, playImmediately: Boolean) {
        mediaPlayer?.reset()
        _currentTrack.value = track
        _currentPosition.value = 0L

        try {
            mediaPlayer?.setDataSource(track.path)
            mediaPlayer?.prepare()
            Log.d(TAG, "Track loaded: ${track.title} from: ${track.path}")
            if (playImmediately) {
                launchPlay()
            } else {
                updatePlaybackState()
                updateNotification()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load audio source: ${track.path}", e)
            mediaPlayer?.reset()
            _isPlaying.value = false
            updatePlaybackState()
        }
    }

    fun launchPlay() {
        if (requestAudioFocus()) {
            if (_currentTrack.value == null && playlistTracks.isNotEmpty()) {
                currentTrackIndex = 0
                loadTrack(playlistTracks[0], true)
                return
            }

            mediaPlayer?.start()
            _isPlaying.value = true
            startPositionUpdater()
            updatePlaybackState()
            startForegroundServiceNotification()
        }
    }

    fun launchPause() {
        mediaPlayer?.pause()
        _isPlaying.value = false
        stopPositionUpdater()
        updatePlaybackState()
        updateNotification()
        // Allow removing notification from foreground when paused, but keep it in the background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            stopForeground(false)
        }
    }

    fun playNext() {
        if (playlistTracks.isEmpty()) return

        if (isShuffle) {
            currentTrackIndex = (playlistTracks.indices).random()
        } else {
            currentTrackIndex = (currentTrackIndex + 1) % playlistTracks.size
        }

        loadTrack(playlistTracks[currentTrackIndex], true)
    }

    fun playPrevious() {
        if (playlistTracks.isEmpty()) return

        if (mediaPlayer != null && mediaPlayer!!.currentPosition > 3000) {
            // Seek to beginning if song has played > 3 seconds
            seekTo(0)
            return
        }

        if (isShuffle) {
            currentTrackIndex = (playlistTracks.indices).random()
        } else {
            currentTrackIndex = if (currentTrackIndex - 1 < 0) {
                playlistTracks.size - 1
            } else {
                currentTrackIndex - 1
            }
        }

        loadTrack(playlistTracks[currentTrackIndex], true)
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        _currentPosition.value = positionMs
        updatePlaybackState()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        isShuffle = enabled
    }

    fun setRepeatEnabled(enabled: Boolean) {
        isRepeatUnit = enabled
    }

    fun stopPlayback() {
        mediaPlayer?.stop()
        _isPlaying.value = false
        stopPositionUpdater()
        updatePlaybackState()
        stopSelf()
    }

    private fun onTrackCompleted() {
        if (isRepeatUnit) {
            // Loop single track
            _currentTrack.value?.let { loadTrack(it, true) }
        } else {
            // Go to next track
            playNext()
        }
    }

    // Audio Focus Handling
    private fun requestAudioFocus(): Boolean {
        audioManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attr)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            return audioManager!!.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            return audioManager!!.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost")
                launchPause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost transient")
                launchPause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost duck - lower volume")
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                mediaPlayer?.setVolume(1.0f, 1.0f)
                if (isPlaying.value) {
                    mediaPlayer?.start()
                }
            }
        }
    }

    // Position updates
    private fun startPositionUpdater() {
        positionUpdateJob?.cancel()
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _currentPosition.value = mp.currentPosition.toLong()
                    }
                }
                delay(200) // update twice per second
            }
        }
    }

    private fun stopPositionUpdater() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    // Media Session State updates
    private fun updatePlaybackState() {
        val state = if (isPlaying.value) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, _currentPosition.value, 1.0f, SystemClock.elapsedRealtime())
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    // Notifications & Foreground Service
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aero Background Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles multimedia del reproductor AeroPlayer"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        if (currentTrack.value == null) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val track = currentTrack.value
        val title = track?.title ?: "Sin canción"
        val artist = track?.artist ?: "Desconocido"

        // Open main activity intent
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingOpenIntent = PendingIntent.getActivity(this, 100, openIntent, flags)

        // Actions intents
        val playPauseIntent = Intent(this, AudioService::class.java).setAction(ACTION_PLAY_PAUSE)
        val nextIntent = Intent(this, AudioService::class.java).setAction(ACTION_NEXT)
        val prevIntent = Intent(this, AudioService::class.java).setAction(ACTION_PREVIOUS)
        val stopIntent = Intent(this, AudioService::class.java).setAction(ACTION_STOP)

        val pendingPlayPause = PendingIntent.getService(this, 101, playPauseIntent, flags)
        val pendingNext = PendingIntent.getService(this, 102, nextIntent, flags)
        val pendingPrev = PendingIntent.getService(this, 103, prevIntent, flags)
        val pendingStop = PendingIntent.getService(this, 104, stopIntent, flags)

        val playPauseIcon = if (isPlaying.value) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(track?.album ?: "Álbum")
            .setOngoing(isPlaying.value)
            .setContentIntent(pendingOpenIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(style)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", pendingPrev)
            .addAction(playPauseIcon, if (isPlaying.value) "Pausa" else "Reproducir", pendingPlayPause)
            .addAction(android.R.drawable.ic_media_next, "Siguiente", pendingNext)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cerrar", pendingStop)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy service")
        serviceScope.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        mediaSession = null
        abandonAudioFocus()
    }
}
