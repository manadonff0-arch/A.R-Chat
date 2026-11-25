package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

class VoiceAudioHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var startTimeMs: Long = 0L

    private var player: MediaPlayer? = null
    private var playingMessageId: String? = null

    fun startRecording(): Result<Unit> = runCatching {
        stopRecording()
        val outFile = File(context.cacheDir, "ar_voice_${System.currentTimeMillis()}.m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mr.setAudioEncodingBitRate(24000)
        mr.setAudioSamplingRate(16000)
        mr.setOutputFile(outFile.absolutePath)
        mr.prepare()
        mr.start()
        recorder = mr
        recordingFile = outFile
        startTimeMs = System.currentTimeMillis()
    }

    fun stopRecording(): Pair<String, Long>? {
        val mr = recorder ?: return null
        val file = recordingFile
        val durationSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).coerceAtLeast(1L)
        return try {
            mr.stop()
            mr.release()
            recorder = null
            val bytes = file?.takeIf { it.exists() }?.readBytes() ?: return null
            file.delete()
            if (bytes.size > 190_000) {
                Base64.encodeToString(bytes.copyOf(190_000), Base64.NO_WRAP) to durationSec
            } else {
                Base64.encodeToString(bytes, Base64.NO_WRAP) to durationSec
            }
        } catch (_: Exception) {
            runCatching { mr.release() }
            recorder = null
            null
        }
    }

    fun cancelRecording() {
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
        recordingFile?.delete()
        recordingFile = null
    }

    fun playBase64Audio(
        messageId: String,
        base64Audio: String,
        speed: Float = 1.0f,
        onComplete: () -> Unit
    ): Boolean {
        return try {
            stopPlayback()
            val bytes = Base64.decode(base64Audio, Base64.NO_WRAP)
            val tempFile = File(context.cacheDir, "play_$messageId.m4a")
            tempFile.writeBytes(bytes)
            val mp = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                playbackParams = playbackParams.setSpeed(speed)
                setOnCompletionListener {
                    playingMessageId = null
                    tempFile.delete()
                    onComplete()
                }
                start()
            }
            player = mp
            playingMessageId = messageId
            true
        } catch (_: Exception) {
            playingMessageId = null
            false
        }
    }

    fun stopPlayback() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        playingMessageId = null
    }

    fun setSpeakerphone(enabled: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = enabled
    }
}

object MediaPickerHelper {
    fun uriToCompressedBase64(context: Context, uri: Uri, maxDim: Int = 512, maxBytes: Int = 48_000): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val original = BitmapFactory.decodeStream(stream) ?: return null
                val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
                val targetW = (original.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (original.height * ratio).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(original, targetW, targetH, true)
                var quality = 78
                var bytes: ByteArray
                do {
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    bytes = out.toByteArray()
                    quality -= 12
                } while (bytes.size > maxBytes && quality >= 24)
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun readDocumentMetadata(context: Context, uri: Uri): String {
        var name = "Document"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: "Document"
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val kb = (size / 1024L).coerceAtLeast(1L)
        return "$name • ${kb} KB • $mime"
    }
}
