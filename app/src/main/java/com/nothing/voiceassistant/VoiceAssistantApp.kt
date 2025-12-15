package com.nothing.voiceassistant

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

/**
 * Application class for Nothing Voice Assistant.
 * Initializes global components and WorkManager for background sync.
 */
class VoiceAssistantApp : Application(), Configuration.Provider {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    /**
     * WorkManager configuration for background upload tasks.
     * Uses default initialization with standard executor.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    
    companion object {
        lateinit var instance: VoiceAssistantApp
            private set
    }
}
