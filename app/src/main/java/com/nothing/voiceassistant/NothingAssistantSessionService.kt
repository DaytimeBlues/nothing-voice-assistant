package com.nothing.voiceassistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

/**
 * Factory service that creates VoiceInteractionSession instances.
 * 
 * Each time the user triggers the assistant (long-press power button),
 * Android calls onNewSession() to create a fresh session for that interaction.
 */
class NothingAssistantSessionService : VoiceInteractionSessionService() {
    
    companion object {
        private const val TAG = "NothingAssistant"
    }
    
    /**
     * Creates a new session when the assistant is triggered.
     * 
     * @param args Bundle containing context about the trigger (e.g., from lock screen)
     * @return A new NothingAssistantSession to handle this interaction
     */
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Log.d(TAG, "Creating new assistant session")
        return NothingAssistantSession(this)
    }
}
