package com.nothing.voiceassistant

import android.content.Intent
import android.speech.RecognitionService
import android.util.Log

/**
 * Stub RecognitionService required for VoiceInteractionService to be "qualified".
 * 
 * This is a minimal implementation that doesn't actually do speech recognition
 * (we use Google Cloud Speech-to-Text instead), but Android requires this
 * service to be present for the VoiceInteractionService to be fully valid.
 */
class NothingRecognitionService : RecognitionService() {
    
    companion object {
        private const val TAG = "NothingRecognition"
    }
    
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        Log.d(TAG, "onStartListening - not implemented (using Cloud STT)")
        // We don't use this for actual recognition
        // The VoiceInteractionSession handles recording directly
    }
    
    override fun onCancel(listener: Callback?) {
        Log.d(TAG, "onCancel")
    }
    
    override fun onStopListening(listener: Callback?) {
        Log.d(TAG, "onStopListening")
    }
}
