package com.nothing.voiceassistant.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Recording entities.
 */
@Dao
interface RecordingDao {
    
    /**
     * Insert a new recording.
     * @return The auto-generated ID of the inserted recording
     */
    @Insert
    suspend fun insert(recording: Recording): Long
    
    /**
     * Update an existing recording.
     */
    @Update
    suspend fun update(recording: Recording)
    
    /**
     * Delete a recording.
     */
    @Delete
    suspend fun delete(recording: Recording)
    
    /**
     * Get all recordings ordered by timestamp (newest first).
     */
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<Recording>>
    
    /**
     * Get a single recording by ID.
     */
    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): Recording?
    
    /**
     * Get recordings that haven't been uploaded yet.
     */
    @Query("SELECT * FROM recordings WHERE isUploaded = 0 ORDER BY timestamp ASC")
    suspend fun getPendingUploads(): List<Recording>
    
    /**
     * Get recordings that are uploaded but not transcribed.
     */
    @Query("SELECT * FROM recordings WHERE isUploaded = 1 AND isTranscribed = 0 ORDER BY timestamp ASC")
    suspend fun getPendingTranscriptions(): List<Recording>
    
    /**
     * Mark a recording as uploaded.
     */
    @Query("UPDATE recordings SET isUploaded = 1, driveFileId = :driveFileId, driveFileUrl = :driveUrl, errorMessage = NULL WHERE id = :id")
    suspend fun markUploaded(id: Long, driveFileId: String, driveUrl: String)
    
    /**
     * Mark a recording as transcribed.
     */
    @Query("UPDATE recordings SET isTranscribed = 1, transcript = :transcript, errorMessage = NULL WHERE id = :id")
    suspend fun markTranscribed(id: Long, transcript: String)
    
    /**
     * Set an error message for a recording.
     */
    @Query("UPDATE recordings SET errorMessage = :errorMessage WHERE id = :id")
    suspend fun setError(id: Long, errorMessage: String)
    
    /**
     * Get count of pending uploads.
     */
    @Query("SELECT COUNT(*) FROM recordings WHERE isUploaded = 0")
    fun getPendingUploadCount(): Flow<Int>
}
