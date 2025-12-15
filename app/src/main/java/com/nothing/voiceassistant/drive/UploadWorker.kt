package com.nothing.voiceassistant.drive

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.nothing.voiceassistant.data.RecordingDatabase
import com.nothing.voiceassistant.transcription.GeminiTranscriptionService
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker for background upload and transcription.
 * 
 * This worker:
 * 1. Uploads pending recordings to Google Drive
 * 2. Transcribes audio using Speech-to-Text
 * 3. Uploads transcript alongside audio
 * 
 * Uses exponential backoff for retries on network failures.
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "UploadWorker"
        private const val KEY_RECORDING_ID = "recording_id"
        private const val UNIQUE_WORK_PREFIX = "upload_"
        
        /**
         * Enqueue an upload task for a specific recording.
         */
        fun enqueueUpload(context: Context, recordingId: Long) {
            val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf(KEY_RECORDING_ID to recordingId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "$UNIQUE_WORK_PREFIX$recordingId",
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )
            
            Log.d(TAG, "Enqueued upload for recording $recordingId")
        }
        
        /**
         * Enqueue upload tasks for ALL pending recordings.
         * Called when app opens or network becomes available.
         */
        fun enqueuePendingUploads(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<SyncAllWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "sync_all",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            
            Log.d(TAG, "Enqueued sync-all task")
        }
    }
    
    override suspend fun doWork(): Result {
        val recordingId = inputData.getLong(KEY_RECORDING_ID, -1)
        if (recordingId == -1L) {
            Log.e(TAG, "No recording ID provided")
            return Result.failure()
        }
        
        Log.d(TAG, "Starting upload for recording $recordingId")
        
        val database = RecordingDatabase.getInstance(applicationContext)
        val recording = database.recordingDao().getRecordingById(recordingId)
        
        if (recording == null) {
            Log.e(TAG, "Recording $recordingId not found")
            return Result.failure()
        }
        
        if (recording.isUploaded && recording.isTranscribed) {
            Log.d(TAG, "Recording $recordingId already processed")
            return Result.success()
        }
        
        // Check if signed in
        val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
        if (account == null) {
            Log.w(TAG, "Not signed in, will retry later")
            return Result.retry()
        }
        
        val driveManager = DriveUploadManager(applicationContext)
        driveManager.initializeDriveService(account)
        
        val audioFile = File(recording.filePath)
        if (!audioFile.exists()) {
            Log.e(TAG, "Audio file not found: ${recording.filePath}")
            database.recordingDao().setError(recordingId, "Audio file not found")
            return Result.failure()
        }
        
        // Step 1: Upload audio file (if not already uploaded)
        if (!recording.isUploaded) {
            when (val uploadResult = driveManager.uploadAudioFile(audioFile)) {
                is DriveUploadManager.UploadResult.Success -> {
                    database.recordingDao().markUploaded(
                        recordingId,
                        uploadResult.fileId,
                        uploadResult.webLink
                    )
                    Log.d(TAG, "Upload successful: ${uploadResult.fileId}")
                }
                is DriveUploadManager.UploadResult.Error -> {
                    Log.e(TAG, "Upload failed: ${uploadResult.message}")
                    database.recordingDao().setError(recordingId, uploadResult.message)
                    return Result.retry()
                }
            }
        }
        
        // Step 2: Transcribe audio (if not already transcribed)
        val updatedRecording = database.recordingDao().getRecordingById(recordingId)
        if (updatedRecording != null && !updatedRecording.isTranscribed) {
            try {
                val geminiService = GeminiTranscriptionService(applicationContext)
                val transcript = geminiService.transcribe(audioFile)
                
                if (!transcript.isNullOrBlank()) {
                    // Save transcript to database
                    database.recordingDao().markTranscribed(recordingId, transcript)
                    
                    // Upload transcript to Drive alongside audio
                    driveManager.uploadTranscript(transcript, audioFile.name)
                    
                    Log.d(TAG, "Transcription successful: ${transcript.take(100)}...")
                } else {
                    Log.w(TAG, "Empty transcription result")
                    database.recordingDao().markTranscribed(recordingId, "[No speech detected]")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                // Don't fail the whole task, just log the error
                database.recordingDao().setError(
                    recordingId, 
                    "Transcription failed: ${e.message}"
                )
            }
        }
        
        Log.d(TAG, "Work completed for recording $recordingId")
        return Result.success()
    }
}

/**
 * Worker that syncs all pending recordings.
 */
class SyncAllWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "SyncAllWorker"
    }
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting sync-all task")
        
        val database = RecordingDatabase.getInstance(applicationContext)
        val pendingRecordings = database.recordingDao().getPendingUploads()
        
        Log.d(TAG, "Found ${pendingRecordings.size} pending uploads")
        
        for (recording in pendingRecordings) {
            UploadWorker.enqueueUpload(applicationContext, recording.id)
        }
        
        return Result.success()
    }
}
