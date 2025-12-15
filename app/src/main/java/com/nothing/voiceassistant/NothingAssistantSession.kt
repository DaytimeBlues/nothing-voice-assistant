package com.nothing.voiceassistant

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.nothing.voiceassistant.audio.AudioRecorder
import com.nothing.voiceassistant.data.Recording
import com.nothing.voiceassistant.data.RecordingDatabase
import com.nothing.voiceassistant.drive.UploadWorker
import com.nothing.voiceassistant.glyph.GlyphController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The main session that handles voice recording when the assistant is triggered.
 * 
 * This session:
 * 1. Shows an overlay UI (works even on lock screen)
 * 2. Immediately starts recording audio
 * 3. Animates the Glyph interface (if available)
 * 4. Saves recording and queues for upload when dismissed
 * 
 * Key Android 16 features used:
 * - Microphone access exemption via VoiceInteractionService
 * - Lock screen overlay without special permissions
 * - Immediate hardware access on trigger
 */
class NothingAssistantSession(context: Context) : VoiceInteractionSession(context) {
    
    companion object {
        private const val TAG = "NothingAssistant"
        private const val MAX_RECORDING_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    }
    
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    
    // WakeLock to keep device awake during recording
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var glyphController: GlyphController
    private lateinit var database: RecordingDatabase
    
    private var recordingStartTime: Long = 0
    private var currentRecordingFile: File? = null
    private var isRecording = false
    
    // UI Elements
    private var overlayView: View? = null
    private var timerTextView: TextView? = null
    private var stopButton: ImageButton? = null
    
    // Timer runnable for updating the recording duration display
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                updateTimerDisplay()
                handler.postDelayed(this, 1000)
            }
        }
    }
    
    // Auto-stop after max duration
    private val autoStopRunnable = Runnable {
        Log.d(TAG, "Max recording duration reached, auto-stopping")
        stopRecordingAndSave()
        hide()
    }
    
    /**
     * Create the overlay UI that appears when assistant is triggered.
     * This UI is displayed over the lock screen automatically.
     */
    override fun onCreateContentView(): View {
        Log.d(TAG, "Creating assistant overlay view")
        
        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.overlay_assistant, null)
        
        // Find UI elements
        timerTextView = overlayView?.findViewById(R.id.timerText)
        stopButton = overlayView?.findViewById(R.id.stopButton)
        
        // Set up stop button
        stopButton?.setOnClickListener {
            Log.d(TAG, "Stop button clicked")
            stopRecordingAndSave()
            hide()
        }
        
        // Initialize WakeLock
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "NothingVoiceAssistant:RecordingWakeLock"
        )
        wakeLock?.setReferenceCounted(false)
        
        return overlayView!!
    }
    
    /**
     * Called when the assistant UI is shown (triggered by power button).
     * This is where we START recording - the exemption is active NOW.
     */
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(TAG, "Assistant session shown, flags=$showFlags")
        
        // Acquire WakeLock to prevent sleep during recording
        wakeLock?.acquire(MAX_RECORDING_DURATION_MS + 1000)
        Log.d(TAG, "WakeLock acquired")
        
        // Initialize components
        audioRecorder = AudioRecorder(context)
        glyphController = GlyphController(context)
        database = RecordingDatabase.getInstance(context)
        
        // Start recording immediately - we have microphone exemption!
        startRecording()
        
        // Start Glyph breathing animation
        glyphController.startListeningAnimation()
        
        // Start timer display
        handler.post(timerRunnable)
        
        // Schedule auto-stop
        handler.postDelayed(autoStopRunnable, MAX_RECORDING_DURATION_MS)
    }
    
    /**
     * Called when the assistant is being dismissed.
     * This happens when user taps outside, presses back, or we call hide().
     */
    override fun onHide() {
        Log.d(TAG, "Assistant session hiding")
        
        // Stop timeout callbacks
        handler.removeCallbacks(timerRunnable)
        handler.removeCallbacks(autoStopRunnable)
        
        if (isRecording) {
            stopRecordingAndSave()
        }
        
        glyphController.stopAnimation()
        
        // Release WakeLock
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
        
        // Clean up coroutines
        sessionScope.cancel()
        
        super.onHide()
    }
    
    /**
     * Called when the session is on the lock screen.
     * We adjust UI accordingly (hide sensitive info if any).
     */
    override fun onLockscreenShown() {
        super.onLockscreenShown()
        Log.d(TAG, "Session shown on lock screen")
        // Recording works the same on lock screen!
    }
    
    /**
     * Start audio recording to a new file.
     */
    private fun startRecording() {
        try {
            // Create recording file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "recording_$timestamp.m4a"
            val recordingsDir = File(context.getExternalFilesDir(null), "recordings")
            recordingsDir.mkdirs()
            currentRecordingFile = File(recordingsDir, fileName)
            
            // Start recording
            audioRecorder.startRecording(currentRecordingFile!!)
            recordingStartTime = System.currentTimeMillis()
            isRecording = true
            
            Log.d(TAG, "Recording started: ${currentRecordingFile?.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            // Show error in UI
            timerTextView?.text = "Error: ${e.message}"
        }
    }
    
    /**
     * Stop recording and save to database, then queue for upload.
     */
    private fun stopRecordingAndSave() {
        if (!isRecording) return
        isRecording = false
        
        try {
            audioRecorder.stopRecording()
            val duration = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
            
            Log.d(TAG, "Recording stopped. Duration: ${duration}s, File: ${currentRecordingFile?.absolutePath}")
            
            // Save to database
            currentRecordingFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    sessionScope.launch(Dispatchers.IO) {
                        try {
                            // Use NonCancellable to ensure this completes even if session is destroyed
                            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                val recording = Recording(
                                    filePath = file.absolutePath,
                                    timestamp = System.currentTimeMillis(),
                                    duration = duration,
                                    isUploaded = false,
                                    driveFileId = null,
                                    transcript = null,
                                    isTranscribed = false
                                )
                                
                                val recordingId = database.recordingDao().insert(recording)
                                Log.d(TAG, "Recording saved to database with ID: $recordingId")
                                
                                // Queue for upload via WorkManager
                                UploadWorker.enqueueUpload(context, recordingId)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save recording details", e)
                        }
                    }
                } else {
                    Log.w(TAG, "Recording file is empty or doesn't exist")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
        }
    }
    
    /**
     * Update the timer display with current recording duration.
     */
    private fun updateTimerDisplay() {
        val elapsedMs = System.currentTimeMillis() - recordingStartTime
        val seconds = (elapsedMs / 1000) % 60
        val minutes = (elapsedMs / 1000) / 60
        timerTextView?.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
