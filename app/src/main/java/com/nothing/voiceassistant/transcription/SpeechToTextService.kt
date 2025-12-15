package com.nothing.voiceassistant.transcription

import android.content.Context
import android.util.Log
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.speech.v1.*
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Service for transcribing audio using Google Cloud Speech-to-Text API.
 * 
 * FREE TIER: 60 minutes/month
 * PRICING: $0.016/minute after free tier
 * 
 * Requirements:
 * 1. Create a Google Cloud project
 * 2. Enable Speech-to-Text API
 * 3. Create a Service Account and download JSON key
 * 4. Place key file at: res/raw/speech_credentials.json
 * 
 * If credentials file is not present, transcription will be skipped.
 */
class SpeechToTextService(private val context: Context) {
    
    companion object {
        private const val TAG = "SpeechToTextService"
        
        // Audio configuration for M4A files from MediaRecorder
        private const val SAMPLE_RATE_HERTZ = 44100
        private const val LANGUAGE_CODE = "en-US"  // Change as needed
        
        // Max audio duration for synchronous transcription (1 minute)
        // Longer files require async API
        private const val MAX_SYNC_DURATION_SECONDS = 60
    }
    
    private var speechClient: SpeechClient? = null
    private var isInitialized = false
    
    /**
     * Initialize the Speech-to-Text client.
     * 
     * @return true if initialization successful, false if credentials not found
     */
    private fun initialize(): Boolean {
        if (isInitialized) return true
        
        try {
            // Try to load credentials from res/raw
            val credentialsStream: InputStream? = try {
                val resId = context.resources.getIdentifier(
                    "speech_credentials", "raw", context.packageName
                )
                if (resId != 0) {
                    context.resources.openRawResource(resId)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Credentials file not found in res/raw")
                null
            }
            
            if (credentialsStream == null) {
                Log.w(TAG, "Speech-to-Text credentials not configured. Skipping transcription.")
                Log.w(TAG, "To enable: Add res/raw/speech_credentials.json from Google Cloud Console")
                return false
            }
            
            val credentials = ServiceAccountCredentials.fromStream(credentialsStream)
            credentialsStream.close()
            
            val settings = SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()
            
            speechClient = SpeechClient.create(settings)
            isInitialized = true
            
            Log.d(TAG, "Speech-to-Text client initialized")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Speech-to-Text", e)
            return false
        }
    }
    
    /**
     * Transcribe an audio file to text.
     * 
     * @param audioFile The M4A audio file to transcribe
     * @return The transcribed text, or empty string if failed
     */
    suspend fun transcribeAudio(audioFile: File): String = withContext(Dispatchers.IO) {
        if (!initialize()) {
            Log.w(TAG, "Skipping transcription - not initialized")
            return@withContext ""
        }
        
        if (!audioFile.exists()) {
            Log.e(TAG, "Audio file not found: ${audioFile.absolutePath}")
            return@withContext ""
        }
        
        try {
            val audioBytes = ByteString.copyFrom(audioFile.readBytes())
            
            // Configure recognition
            val config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.MP3) // Works for M4A/AAC
                .setSampleRateHertz(SAMPLE_RATE_HERTZ)
                .setLanguageCode(LANGUAGE_CODE)
                .setEnableAutomaticPunctuation(true)
                .setModel("latest_long") // Works best for longer audio
                .build()
            
            val audio = RecognitionAudio.newBuilder()
                .setContent(audioBytes)
                .build()
            
            Log.d(TAG, "Sending audio for transcription: ${audioFile.name} (${audioFile.length()} bytes)")
            
            // For short audio (<1 min), use synchronous API
            // For longer audio, we'd use longRunningRecognize
            val response = speechClient?.recognize(config, audio)
            
            val transcript = response?.resultsList?.joinToString(" ") { result ->
                result.alternativesList.firstOrNull()?.transcript ?: ""
            } ?: ""
            
            Log.d(TAG, "Transcription complete: ${transcript.take(100)}...")
            
            transcript.trim()
            
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            ""
        }
    }
    
    /**
     * Transcribe long audio (>1 minute) using async API.
     * 
     * Note: This requires the audio to be uploaded to Google Cloud Storage first.
     * For simplicity, we'll use the sync API with chunking for most recordings.
     */
    suspend fun transcribeLongAudio(gcsUri: String): String = withContext(Dispatchers.IO) {
        if (!initialize()) {
            return@withContext ""
        }
        
        try {
            val config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.MP3)
                .setSampleRateHertz(SAMPLE_RATE_HERTZ)
                .setLanguageCode(LANGUAGE_CODE)
                .setEnableAutomaticPunctuation(true)
                .build()
            
            val audio = RecognitionAudio.newBuilder()
                .setUri(gcsUri)
                .build()
            
            val response = speechClient?.longRunningRecognizeAsync(config, audio)
            val results = response?.get()
            
            results?.resultsList?.joinToString(" ") { result ->
                result.alternativesList.firstOrNull()?.transcript ?: ""
            } ?: ""
            
        } catch (e: Exception) {
            Log.e(TAG, "Long transcription failed", e)
            ""
        }
    }
    
    /**
     * Clean up resources.
     */
    fun shutdown() {
        speechClient?.close()
        speechClient = null
        isInitialized = false
    }
}
