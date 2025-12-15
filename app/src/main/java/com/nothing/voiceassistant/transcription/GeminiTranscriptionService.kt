package com.nothing.voiceassistant.transcription

import android.content.Context
import android.util.Base64
import android.util.Log
import com.nothing.voiceassistant.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Transcription service using Google Gemini API.
 * 
 * Uses the Gemini 1.5 Flash model which supports audio input.
 * Free tier: 1500 requests/day - much more generous than Speech-to-Text!
 */
class GeminiTranscriptionService(private val context: Context) {
    
    companion object {
        private const val TAG = "GeminiTranscription"
        // Using Gemini 2.5 Flash - latest and best for audio transcription
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
        
        // API key loaded from BuildConfig (set in secrets.properties, which is gitignored)
        private val API_KEY = BuildConfig.GEMINI_API_KEY
    }
    
    /**
     * Transcribe an audio file using Gemini.
     * 
     * @param audioFile The audio file to transcribe (m4a, wav, mp3, etc.)
     * @return The transcribed text
     * @throws Exception if transcription fails, containing the API error message
     */
    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting transcription for: ${audioFile.name}")
        
        // Read and encode the audio file as base64
        val audioBytes = audioFile.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        
        // Determine MIME type
        val mimeType = when {
            audioFile.name.endsWith(".m4a") -> "audio/mp4"
            audioFile.name.endsWith(".mp3") -> "audio/mp3"
            audioFile.name.endsWith(".wav") -> "audio/wav"
            else -> "audio/mp4"
        }
        
        // Build the request JSON
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        // Audio part
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", mimeType)
                                put("data", base64Audio)
                            })
                        })
                        // Text prompt
                        put(JSONObject().apply {
                            put("text", "Please transcribe this audio recording. Only output the transcribed text, nothing else. If the audio is unclear or silent, respond with '[Inaudible]'.")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 8192)
            })
        }
        
        // Make the API request
        val url = URL("$GEMINI_API_URL?key=$API_KEY")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 60000  // 60 seconds
                readTimeout = 60000
            }
            
            // Send request
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }
            
            // Read response
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream
                val errorMsg = if (errorStream != null) {
                    errorStream.bufferedReader().use { it.readText() }
                } else {
                    "Error $responseCode"
                }
                Log.e(TAG, "API Error: $errorMsg")
                throw Exception("Gemini API Error ($responseCode): $errorMsg")
            }
            
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            
            // Parse the response
            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "")
                    Log.d(TAG, "Transcription successful: ${text.take(100)}...")
                    return@withContext text.trim()
                }
            }
            
            Log.w(TAG, "No text in response")
            return@withContext "[No speech detected]"
            
        } finally {
            connection.disconnect()
        }
    }
}
