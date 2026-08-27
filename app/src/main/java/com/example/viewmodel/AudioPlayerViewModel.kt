package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.extractor.ExtractionErrorType
import com.example.extractor.ExtractionResult
import com.example.extractor.YouTubeAudioExtractor
import com.example.model.AudioPlaybackState
import com.example.model.PlaybackStatus
import com.example.service.AudioPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AudioPlaybackState())
    val uiState: StateFlow<AudioPlaybackState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var progressTrackingJob: Job? = null
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressTracker()
            } else {
                stopProgressTracker()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val status = when (playbackState) {
                Player.STATE_IDLE -> PlaybackStatus.IDLE
                Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
                Player.STATE_READY -> PlaybackStatus.READY
                Player.STATE_ENDED -> PlaybackStatus.ENDED
                else -> PlaybackStatus.IDLE
            }

            val currentPos = mediaController?.currentPosition ?: 0L
            val duration = (mediaController?.duration ?: 0L).coerceAtLeast(0L)
            val buffered = mediaController?.bufferedPosition ?: 0L

            _uiState.update {
                it.copy(
                    playbackStatus = status,
                    currentPositionMs = currentPos,
                    durationMs = if (duration > 0) duration else it.durationMs,
                    bufferedPositionMs = buffered
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("AudioPlayerVM", "ExoPlayer playback error: ${error.errorCodeName}", error)
            _uiState.update {
                it.copy(
                    playbackStatus = PlaybackStatus.ERROR,
                    isPlaying = false,
                    isLoading = false,
                    errorMessage = "تعذر تشغيل البث الصوتي (${error.localizedMessage ?: "خطأ في الاتصال بالبث"}).",
                    errorType = ExtractionErrorType.EXTRACTION_FAILED
                )
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _uiState.update { it.copy(isOnline = true) }
        }

        override fun onLost(network: Network) {
            _uiState.update { it.copy(isOnline = false) }
        }
    }

    init {
        checkInitialNetworkState()
        registerNetworkCallback()
        initMediaController()
    }

    private fun checkInitialNetworkState() {
        val activeNetwork = connectivityManager?.activeNetwork
        val caps = connectivityManager?.getNetworkCapabilities(activeNetwork)
        val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _uiState.update { it.copy(isOnline = isConnected) }
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w("AudioPlayerVM", "Failed to register network callback", e)
        }
    }

    private fun initMediaController() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(playerListener)

                // Sync current state if already playing
                mediaController?.let { controller ->
                    _uiState.update {
                        it.copy(
                            isPlaying = controller.isPlaying,
                            currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                            durationMs = controller.duration.coerceAtLeast(0L),
                            volume = controller.volume
                        )
                    }
                    if (controller.isPlaying) {
                        startProgressTracker()
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioPlayerVM", "Failed to obtain MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    fun onUrlInputChanged(url: String) {
        _uiState.update { it.copy(urlInput = url, errorMessage = null, errorType = null) }
    }

    fun extractAndPlay(overrideUrl: String? = null) {
        val targetUrl = (overrideUrl ?: _uiState.value.urlInput).trim()

        if (targetUrl.isEmpty()) {
            _uiState.update {
                it.copy(
                    errorMessage = "يرجى إدخال رابط فيديو يوتيوب أولاً.",
                    errorType = ExtractionErrorType.INVALID_URL
                )
            }
            return
        }

        if (!_uiState.value.isOnline) {
            _uiState.update {
                it.copy(
                    errorMessage = "لا يوجد اتصال بالإنترنت. يرجى التأكد من اتصالك وإعادة المحاولة.",
                    errorType = ExtractionErrorType.NO_INTERNET
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    errorType = null,
                    urlInput = targetUrl
                )
            }

            when (val result = YouTubeAudioExtractor.extractAudio(targetUrl)) {
                is ExtractionResult.Success -> {
                    val data = result.data
                    val durationMs = data.durationSeconds * 1000L

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = data.title,
                            channel = data.uploader,
                            durationMs = durationMs,
                            thumbnailUrl = data.thumbnailUrl,
                            audioStreamUrl = data.streamUrl,
                            bitrateKbps = data.bitrateKbps,
                            audioFormat = data.format,
                            currentPositionMs = 0L
                        )
                    }

                    playStream(
                        streamUrl = data.streamUrl,
                        title = data.title,
                        artist = data.uploader,
                        artworkUrl = data.thumbnailUrl
                    )
                }
                is ExtractionResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.userFriendlyMessage,
                            errorType = result.type
                        )
                    }
                }
            }
        }
    }

    private fun playStream(streamUrl: String, title: String, artist: String, artworkUrl: String?) {
        val controller = mediaController ?: run {
            Log.w("AudioPlayerVM", "MediaController not ready yet")
            return
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)

        artworkUrl?.let {
            try {
                metadataBuilder.setArtworkUri(Uri.parse(it))
            } catch (e: Exception) {
                Log.w("AudioPlayerVM", "Invalid artwork URI", e)
            }
        }

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val validPos = positionMs.coerceIn(0L, _uiState.value.durationMs.coerceAtLeast(0L))
        controller.seekTo(validPos)
        _uiState.update { it.copy(currentPositionMs = validPos) }
    }

    fun seekRelative(offsetMs: Long) {
        val controller = mediaController ?: return
        val current = controller.currentPosition
        val target = (current + offsetMs).coerceIn(0L, _uiState.value.durationMs.coerceAtLeast(0L))
        controller.seekTo(target)
        _uiState.update { it.copy(currentPositionMs = target) }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        mediaController?.volume = clamped
        _uiState.update { it.copy(volume = clamped) }
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.playbackParameters = PlaybackParameters(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun stopPlayback() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        stopProgressTracker()
        _uiState.update {
            it.copy(
                isPlaying = false,
                currentPositionMs = 0L,
                playbackStatus = PlaybackStatus.IDLE
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null, errorType = null) }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressTrackingJob = viewModelScope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        val currentPos = controller.currentPosition.coerceAtLeast(0L)
                        val duration = controller.duration.coerceAtLeast(0L)
                        val buffered = controller.bufferedPosition.coerceAtLeast(0L)

                        _uiState.update {
                            it.copy(
                                currentPositionMs = currentPos,
                                durationMs = if (duration > 0) duration else it.durationMs,
                                bufferedPositionMs = buffered
                            )
                        }
                    }
                }
                delay(400)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    override fun onCleared() {
        super.onCleared()
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w("AudioPlayerVM", "Failed to unregister network callback", e)
        }
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        stopProgressTracker()
    }
}
