package com.nothing.voiceassistant.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Utility class for recording audio using MediaRecorder.
 * 
 * Uses AAC/M4A format for good quality and compression.
 * Compatible with Google Cloud Speech-to-Text API.
 */
class AudioRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioRecorder"
        
        // Audio settings for high quality voice recording
        private const val AUDIO_SAMPLE_RATE = 44100
        private const val AUDIO_BIT_RATE = 128000
        private const val AUDIO_CHANNELS = 1 // Mono for voice
    }
    
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var outputFile: File? = null
    
    /**
     * Start recording audio to the specified file.
     * 
     * @param file The output file to save the recording
     * @throws IllegalStateException if already recording
     * @throws Exception if MediaRecorder fails to start
     */
    fun startRecording(file: File) {
        if (isRecording) {
            throw IllegalStateException("Already recording")
        }
        
        outputFile = file
        
        try {
            // Create MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                // Audio source - VOICE_RECOGNITION is optimized for voice
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                
                // Output format - MPEG_4 container with AAC codec
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                
                // Quality settings
                setAudioSamplingRate(AUDIO_SAMPLE_RATE)
                setAudioEncodingBitRate(AUDIO_BIT_RATE)
                setAudioChannels(AUDIO_CHANNELS)
                
                // Output file
                setOutputFile(file.absolutePath)
                
                // Prepare and start
                prepare()
                start()
            }
            
            isRecording = true
            Log.d(TAG, "Recording started: ${file.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            releaseRecorder()
            throw e
        }
    }
    
    /**
     * Stop the current recording.
     * 
     * @return The recorded file, or null if not recording
     */
    fun stopRecording(): File? {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording")
            return null
        }
        
        return try {
            mediaRecorder?.apply {
                stop()
                reset()
            }
            isRecording = false
            Log.d(TAG, "Recording stopped: ${outputFile?.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            null
        } finally {
            releaseRecorder()
        }
    }
    
    /**
     * Get the current recording amplitude for visualization.
     * 
     * @return Amplitude value (0-32767), or 0 if not recording
     */
    fun getAmplitude(): Int {
        return if (isRecording) {
            try {
                mediaRecorder?.maxAmplitude ?: 0
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
    }
    
    /**
     * Check if currently recording.
     */
    fun isRecording(): Boolean = isRecording
    
    /**
     * Release MediaRecorder resources.
     */
    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        }
        mediaRecorder = null
    }
    
    /**
     * Clean up resources. Call when done with this recorder.
     */
    fun release() {
        if (isRecording) {
            stopRecording()
        }
        releaseRecorder()
    }
}
