package com.nothing.voiceassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a voice recording.
 * 
 * Tracks local file path, upload status, and transcription status.
 */
@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** Local file path to the audio recording */
    val filePath: String,
    
    /** Unix timestamp when recording was made */
    val timestamp: Long,
    
    /** Duration of recording in seconds */
    val duration: Int,
    
    /** Whether the file has been uploaded to Google Drive */
    val isUploaded: Boolean = false,
    
    /** Google Drive file ID after upload (null if not uploaded) */
    val driveFileId: String? = null,
    
    /** URL to the file in Google Drive (null if not uploaded) */
    val driveFileUrl: String? = null,
    
    /** Transcription text (null if not transcribed) */
    val transcript: String? = null,
    
    /** Whether transcription has been completed */
    val isTranscribed: Boolean = false,
    
    /** Error message if upload/transcription failed */
    val errorMessage: String? = null
) {
    /**
     * Get a display-friendly name for this recording.
     */
    fun getDisplayName(): String {
        val dateFormat = java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date(timestamp))
    }
    
    /**
     * Get duration as MM:SS string.
     */
    fun getDurationString(): String {
        val minutes = duration / 60
        val seconds = duration % 60
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}
