package com.nothing.voiceassistant.drive

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.google.api.services.drive.model.File as DriveFile

/**
 * Manages Google Drive authentication and file uploads.
 * 
 * Handles:
 * - Google Sign-In with Drive scope
 * - Uploading audio files to a dedicated folder
 * - Uploading transcript text files alongside audio
 */
class DriveUploadManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DriveUploadManager"
        private const val APP_NAME = "Nothing Voice Assistant"
        private const val FOLDER_NAME = "VoiceAssistant"
        private const val AUDIO_MIME_TYPE = "audio/mp4"
        private const val TEXT_MIME_TYPE = "text/plain"
        
        // Shared preferences for storing folder ID
        private const val PREFS_NAME = "drive_prefs"
        private const val KEY_FOLDER_ID = "folder_id"
    }
    
    private val signInClient: GoogleSignInClient
    private var driveService: Drive? = null
    
    init {
        // Configure Google Sign-In with Drive scope
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        
        signInClient = GoogleSignIn.getClient(context, signInOptions)
    }
    
    /**
     * Get the Intent to launch the Google Sign-In flow.
     */
    fun getSignInIntent(): Intent = signInClient.signInIntent
    
    /**
     * Check if the user is already signed in.
     */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
    }
    
    /**
     * Get the current signed-in account.
     */
    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }
    
    /**
     * Initialize the Drive service with the signed-in account.
     * Must be called after successful sign-in.
     */
    fun initializeDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account
        
        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
        .setApplicationName(APP_NAME)
        .build()
        
        Log.d(TAG, "Drive service initialized for ${account.email}")
    }
    
    /**
     * Sign out the current user.
     */
    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            signInClient.signOut()
            driveService = null
        }
    }
    
    /**
     * Upload an audio file to Google Drive.
     * 
     * @param audioFile The local audio file to upload
     * @return UploadResult with Drive file ID and web link, or error
     */
    suspend fun uploadAudioFile(audioFile: File): UploadResult = withContext(Dispatchers.IO) {
        val drive = driveService
        if (drive == null) {
            return@withContext UploadResult.Error("Not signed in to Google Drive")
        }
        
        if (!audioFile.exists()) {
            return@withContext UploadResult.Error("Audio file not found: ${audioFile.name}")
        }
        
        try {
            // Get or create the app folder
            val folderId = getOrCreateAppFolder(drive)
            
            // Create file metadata
            val fileMetadata = DriveFile().apply {
                name = audioFile.name
                parents = listOf(folderId)
            }
            
            // Create file content
            val mediaContent = FileContent(AUDIO_MIME_TYPE, audioFile)
            
            // Upload
            val uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, webViewLink")
                .execute()
            
            Log.d(TAG, "Uploaded: ${uploadedFile.name} -> ${uploadedFile.id}")
            
            UploadResult.Success(
                fileId = uploadedFile.id,
                webLink = uploadedFile.webViewLink ?: "https://drive.google.com/file/d/${uploadedFile.id}"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            UploadResult.Error("Upload failed: ${e.message}")
        }
    }
    
    /**
     * Upload/append a transcript to the daily log file in Google Drive.
     * Creates one file per day (e.g., "2025-12-15.txt") and appends each transcript.
     * 
     * @param transcript The transcript text content
     * @param audioFileName The name of the associated audio file (for timestamp)
     * @return UploadResult with Drive file ID and web link, or error
     */
    suspend fun uploadTranscript(transcript: String, audioFileName: String): UploadResult = withContext(Dispatchers.IO) {
        val drive = driveService
        if (drive == null) {
            return@withContext UploadResult.Error("Not signed in to Google Drive")
        }
        
        try {
            // Get or create the app folder
            val folderId = getOrCreateAppFolder(drive)
            
            // Daily file name (e.g., "2025-12-15.txt")
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val dailyFileName = "${dateFormat.format(java.util.Date())}.txt"
            
            // Time stamp for this entry (e.g., "14:32")
            val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            val timeStamp = timeFormat.format(java.util.Date())
            
            // Format the entry
            val entry = "\n--- $timeStamp ---\n$transcript\n"
            
            // Search for existing daily file
            val query = "name = '$dailyFileName' and '$folderId' in parents and trashed = false"
            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            
            val uploadedFile: com.google.api.services.drive.model.File
            
            if (result.files.isNotEmpty()) {
                // Append to existing daily file
                val existingFileId = result.files[0].id
                
                // Download existing content
                val existingContent = drive.files().get(existingFileId)
                    .executeMediaAsInputStream()
                    .bufferedReader()
                    .use { it.readText() }
                
                // Create temp file with appended content
                val tempFile = File(context.cacheDir, dailyFileName)
                tempFile.writeText(existingContent + entry)
                
                // Update the file
                val mediaContent = FileContent(TEXT_MIME_TYPE, tempFile)
                uploadedFile = drive.files().update(existingFileId, null, mediaContent)
                    .setFields("id, webViewLink")
                    .execute()
                
                tempFile.delete()
                Log.d(TAG, "Appended to daily log: $dailyFileName")
                
            } else {
                // Create new daily file with header
                val header = "# Voice Notes - ${dateFormat.format(java.util.Date())}\n"
                val tempFile = File(context.cacheDir, dailyFileName)
                tempFile.writeText(header + entry)
                
                val fileMetadata = DriveFile().apply {
                    name = dailyFileName
                    parents = listOf(folderId)
                }
                
                val mediaContent = FileContent(TEXT_MIME_TYPE, tempFile)
                uploadedFile = drive.files().create(fileMetadata, mediaContent)
                    .setFields("id, webViewLink")
                    .execute()
                
                tempFile.delete()
                Log.d(TAG, "Created new daily log: $dailyFileName")
            }
            
            UploadResult.Success(
                fileId = uploadedFile.id,
                webLink = uploadedFile.webViewLink ?: "https://drive.google.com/file/d/${uploadedFile.id}"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Transcript upload failed", e)
            UploadResult.Error("Transcript upload failed: ${e.message}")
        }
    }
    
    /**
     * Get or create the app folder in Google Drive.
     */
    private fun getOrCreateAppFolder(drive: Drive): String {
        // Check if we have a cached folder ID
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedFolderId = prefs.getString(KEY_FOLDER_ID, null)
        
        if (cachedFolderId != null) {
            // Verify the folder still exists
            try {
                drive.files().get(cachedFolderId).execute()
                return cachedFolderId
            } catch (e: Exception) {
                // Folder doesn't exist, create new one
                Log.d(TAG, "Cached folder not found, creating new one")
            }
        }
        
        // Search for existing folder
        val query = "name = '$FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val result = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .execute()
        
        val folderId = if (result.files.isNotEmpty()) {
            result.files[0].id
        } else {
            // Create new folder
            val folderMetadata = DriveFile().apply {
                name = FOLDER_NAME
                mimeType = "application/vnd.google-apps.folder"
            }
            
            val folder = drive.files().create(folderMetadata)
                .setFields("id")
                .execute()
            
            Log.d(TAG, "Created folder: $FOLDER_NAME -> ${folder.id}")
            folder.id
        }
        
        // Cache the folder ID
        prefs.edit().putString(KEY_FOLDER_ID, folderId).apply()
        
        return folderId
    }
    
    /**
     * Result of a file upload operation.
     */
    sealed class UploadResult {
        data class Success(val fileId: String, val webLink: String) : UploadResult()
        data class Error(val message: String) : UploadResult()
    }
}
