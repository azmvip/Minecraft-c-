package com.example.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.net.UnknownHostException
import java.util.regex.Pattern

data class ExtractedAudioData(
    val title: String,
    val uploader: String,
    val durationSeconds: Long,
    val streamUrl: String,
    val thumbnailUrl: String?,
    val format: String,
    val bitrateKbps: Int
)

sealed class ExtractionResult {
    data class Success(val data: ExtractedAudioData) : ExtractionResult()
    data class Error(val type: ExtractionErrorType, val userFriendlyMessage: String) : ExtractionResult()
}

enum class ExtractionErrorType {
    INVALID_URL,
    NO_INTERNET,
    VIDEO_UNAVAILABLE,
    EXTRACTION_FAILED
}

object YouTubeAudioExtractor {

    private const val TAG = "YouTubeAudioExtractor"

    private val YOUTUBE_URL_PATTERNS = listOf(
        Pattern.compile("^(https?://)?(www\\.)?(youtube\\.com|m\\.youtube\\.com|music\\.youtube\\.com)/watch\\?v=([a-zA-Z0-9_-]{11}).*"),
        Pattern.compile("^(https?://)?(www\\.)?youtu\\.be/([a-zA-Z0-9_-]{11}).*"),
        Pattern.compile("^(https?://)?(www\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11}).*"),
        Pattern.compile("^(https?://)?(www\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11}).*")
    )

    fun isValidYouTubeUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        return YOUTUBE_URL_PATTERNS.any { it.matcher(trimmed).find() } ||
                trimmed.contains("youtube.com") ||
                trimmed.contains("youtu.be")
    }

    suspend fun extractAudio(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()

        if (!isValidYouTubeUrl(cleanUrl)) {
            return@withContext ExtractionResult.Error(
                ExtractionErrorType.INVALID_URL,
                "الرابط المدخل غير صالح. يرجى إدخال رابط فيديو يوتيوب صحيح (مثل youtube.com/watch?v=... أو youtu.be/...)"
            )
        }

        try {
            Log.d(TAG, "Extracting audio stream for URL: $cleanUrl")
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, cleanUrl)

            val audioStreams: List<AudioStream> = streamInfo.audioStreams ?: emptyList()

            if (audioStreams.isEmpty()) {
                // If no audio-only stream is present, fallback to lowest video stream or throw
                val videoStreams = streamInfo.videoStreams ?: emptyList()
                if (videoStreams.isNotEmpty()) {
                    val fallback = videoStreams.first()
                    return@withContext ExtractionResult.Success(
                        ExtractedAudioData(
                            title = streamInfo.name.orEmpty().ifEmpty { "مقطع يوتيوب" },
                            uploader = streamInfo.uploaderName.orEmpty().ifEmpty { "غير معروف" },
                            durationSeconds = streamInfo.duration,
                            streamUrl = fallback.content,
                            thumbnailUrl = streamInfo.thumbnails.firstOrNull()?.url,
                            format = fallback.format?.name ?: "MP4",
                            bitrateKbps = fallback.bitrate
                        )
                    )
                }

                return@withContext ExtractionResult.Error(
                    ExtractionErrorType.EXTRACTION_FAILED,
                    "لم يتم العثور على أي مسار صوتي متاح لهذا الفيديو."
                )
            }

            // Pick the best quality audio stream (prefer high bitrate)
            val selectedAudioStream = audioStreams.maxByOrNull { it.bitrate } ?: audioStreams.first()

            val bestThumbnail = streamInfo.thumbnails.maxByOrNull { it.height * it.width }?.url
                ?: streamInfo.thumbnails.firstOrNull()?.url

            Log.d(TAG, "Audio stream extracted successfully: ${streamInfo.name}, bitrate: ${selectedAudioStream.bitrate}")

            ExtractionResult.Success(
                ExtractedAudioData(
                    title = streamInfo.name.orEmpty().ifEmpty { "مقطع يوتيوب" },
                    uploader = streamInfo.uploaderName.orEmpty().ifEmpty { "غير معروف" },
                    durationSeconds = streamInfo.duration,
                    streamUrl = selectedAudioStream.content,
                    thumbnailUrl = bestThumbnail,
                    format = selectedAudioStream.format?.name ?: "M4A",
                    bitrateKbps = selectedAudioStream.bitrate
                )
            )
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Network error during extraction", e)
            ExtractionResult.Error(
                ExtractionErrorType.NO_INTERNET,
                "لا يوجد اتصال بالإنترنت. يرجى التحقق من الشبكة وإعادة المحاولة."
            )
        } catch (e: IOException) {
            Log.e(TAG, "IO error during extraction", e)
            ExtractionResult.Error(
                ExtractionErrorType.NO_INTERNET,
                "تعذر الاتصال بالخادم. يرجى التحقق من اتصال الإنترنت."
            )
        } catch (e: ContentNotAvailableException) {
            Log.e(TAG, "Content not available", e)
            ExtractionResult.Error(
                ExtractionErrorType.VIDEO_UNAVAILABLE,
                "الفيديو غير متاح أو محمي بقيود العمر/المنطقة أو محذوف."
            )
        } catch (e: ParsingException) {
            Log.e(TAG, "Parsing error during extraction", e)
            ExtractionResult.Error(
                ExtractionErrorType.EXTRACTION_FAILED,
                "فشل استخراج البث الصوتي من هذا الرابط (تغيير في بنية صفحة يوتيوب أو قيود على الفيديو)."
            )
        } catch (e: ExtractionException) {
            Log.e(TAG, "Extraction exception", e)
            ExtractionResult.Error(
                ExtractionErrorType.EXTRACTION_FAILED,
                "فشل الاستخراج: ${e.localizedMessage ?: "حدث خطأ أثناء معالجة رابط الفيديو."}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during extraction", e)
            ExtractionResult.Error(
                ExtractionErrorType.EXTRACTION_FAILED,
                "حدث خطأ غير متوقع أثناء استخراج الصوت: ${e.localizedMessage ?: "حاول مجددًا"}"
            )
        }
    }
}
