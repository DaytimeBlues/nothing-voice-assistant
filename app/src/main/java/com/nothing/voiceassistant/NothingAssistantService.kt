package com.nothing.voiceassistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * Main VoiceInteractionService that receives power button long-press events.
 * 
 * This service is bound by the system when the user sets this app as their
 * default Digital Assistant. It remains dormant until triggered.
 * 
 * Android 16 (API 36) grants this service special exemptions:
 * - Immediate microphone access even from lock screen
 * - Exempt from ForegroundServiceStartNotAllowedException
 * - Can display UI over Keyguard (lock screen)
 */
class NothingAssistantService : VoiceInteractionService() {
    
    companion object {
        private const val TAG = "NothingAssistant"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceInteractionService created - ready for power button trigger")
    }
    
    /**
     * Called when the service is ready to be used.
     * This is where we could initialize any persistent resources.
     */
    override fun onReady() {
        super.onReady()
        Log.d(TAG, "VoiceInteractionService ready")
    }
    
    /**
     * Called when the user triggers the assistant via long-press power button.
     * The actual session handling is done by NothingAssistantSessionService.
     */
    override fun onLaunchVoiceAssistFromKeyguard() {
        super.onLaunchVoiceAssistFromKeyguard()
        Log.d(TAG, "Assistant launched from keyguard (lock screen)")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VoiceInteractionService destroyed")
    }
}
