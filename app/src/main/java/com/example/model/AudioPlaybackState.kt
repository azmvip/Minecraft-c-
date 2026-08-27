package com.example.model

import com.example.extractor.ExtractionErrorType

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
    ERROR
}

data class AudioPlaybackState(
    val urlInput: String = "",
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackStatus: PlaybackStatus = PlaybackStatus.IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val title: String? = null,
    val channel: String? = null,
    val thumbnailUrl: String? = null,
    val audioStreamUrl: String? = null,
    val bitrateKbps: Int = 0,
    val audioFormat: String? = null,
    val volume: Float = 1.0f,
    val playbackSpeed: Float = 1.0f,
    val errorMessage: String? = null,
    val errorType: ExtractionErrorType? = null,
    val isOnline: Boolean = true
) {
    val progress: Float
        get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val hasActiveTrack: Boolean
        get() = !title.isNullOrBlank() || audioStreamUrl != null
}
