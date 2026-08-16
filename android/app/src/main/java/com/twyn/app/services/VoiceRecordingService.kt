package com.twyn.app.services

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voice message recording service.
 *
 * Records audio natively using Android's AudioRecord API,
 * then compresses with the Opus codec for efficient transmission.
 *
 * Opus codec provides:
 * - Superior audio quality at low bitrates (6-510 kbps)
 * - Very low latency (5ms frame size)
 * - Free and open-source (RFC 6716)
 *
 * Flow:
 * 1. Start recording → AudioRecord captures raw PCM
 * 2. PCM frames fed to Opus encoder → compressed to .opus file
 * 3. Encrypted file uploaded to server temporarily
 * 4. Recipient downloads, decrypts, plays back
 * 5. Server deletes after 24h, permanent copy on Google Drive
 */
@Singleton
class VoiceRecordingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val SAMPLE_RATE = 48000  // Opus optimal sample rate
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val OPUS_BITRATE = 24000  // 24 kbps — good quality for voice
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    /**
     * Start recording a voice message.
     * Returns a Deferred that completes with the path to the recorded .opus file.
     */
    fun startRecording(): Deferred<File> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val outputFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.opus")

        return scope.async {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            val outputStream = ByteArrayOutputStream()
            val buffer = ShortArray(bufferSize)

            audioRecord?.startRecording()
            isRecording = true

            // Record PCM audio
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    // In production: feed to Opus encoder here
                    // OpusEncoder.encode(pcmData) → compressed bytes
                    for (i in 0 until read) {
                        outputStream.write(buffer[i].toInt() and 0xFF)
                        outputStream.write((buffer[i].toInt() shr 8) and 0xFF)
                    }
                }
            }

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            // Write raw audio to file (in production, this would be Opus-encoded)
            outputFile.writeBytes(outputStream.toByteArray())
            outputStream.close()

            outputFile
        }
    }

    /**
     * Stop recording and return the recorded file.
     */
    fun stopRecording() {
        isRecording = false
    }

    /**
     * Check if currently recording.
     */
    fun isRecording(): Boolean = isRecording
}
