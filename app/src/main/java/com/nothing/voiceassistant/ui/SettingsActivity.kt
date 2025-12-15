package com.nothing.voiceassistant.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.nothing.voiceassistant.R
import com.nothing.voiceassistant.data.RecordingDatabase
import com.nothing.voiceassistant.databinding.ActivitySettingsBinding
import com.nothing.voiceassistant.drive.DriveUploadManager
import com.nothing.voiceassistant.drive.UploadWorker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main settings and setup activity.
 * 
 * Handles:
 * - Google Sign-In for Drive sync
 * - Microphone permission request
 * - Shows setup instructions
 * - Displays sync status
 */
class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SettingsActivity"
    }
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var driveManager: DriveUploadManager
    private lateinit var database: RecordingDatabase
    
    // Permission launcher
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Microphone permission granted")
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
        }
    }
    
    // Google Sign-In launcher
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleSignInResult(result.data)
        } else {
            Log.w(TAG, "Sign-in cancelled or failed")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        driveManager = DriveUploadManager(this)
        database = RecordingDatabase.getInstance(this)
        
        setupUI()
        requestMicPermissionIfNeeded()
        updateSignInUI()
        observePendingUploads()
    }
    
    override fun onResume() {
        super.onResume()
        updateSignInUI()
    }
    
    private fun setupUI() {
        binding.signInButton.setOnClickListener {
            if (driveManager.isSignedIn()) {
                // Sign out
                lifecycleScope.launch {
                    driveManager.signOut()
                    updateSignInUI()
                }
            } else {
                // Sign in
                signInLauncher.launch(driveManager.getSignInIntent())
            }
        }
        
        binding.syncButton.setOnClickListener {
            syncNow()
        }
        
        binding.viewRecordingsButton.setOnClickListener {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }
    }
    
    private fun requestMicPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    private fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            if (account != null) {
                driveManager.initializeDriveService(account)
                Log.d(TAG, "Signed in: ${account.email}")
                updateSignInUI()
                
                // Trigger sync of pending uploads
                syncNow()
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Sign-in failed: ${e.statusCode}", e)
            Toast.makeText(this, "Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateSignInUI() {
        val account = driveManager.getSignedInAccount()
        
        if (account != null) {
            binding.signInStatus.text = getString(R.string.signed_in_as, account.email)
            binding.signInButton.text = getString(R.string.sign_out)
            binding.syncButton.visibility = View.VISIBLE
        } else {
            binding.signInStatus.text = getString(R.string.not_signed_in)
            binding.signInButton.text = getString(R.string.sign_in_google)
            binding.syncButton.visibility = View.GONE
        }
    }
    
    private fun syncNow() {
        if (!driveManager.isSignedIn()) {
            Toast.makeText(this, R.string.not_signed_in, Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.syncButton.text = getString(R.string.syncing)
        binding.syncButton.isEnabled = false
        
        // Enqueue all pending uploads
        UploadWorker.enqueuePendingUploads(this)
        
        // Re-enable button after a delay
        binding.syncButton.postDelayed({
            binding.syncButton.text = getString(R.string.sync_now)
            binding.syncButton.isEnabled = true
        }, 2000)
    }
    
    private fun observePendingUploads() {
        lifecycleScope.launch {
            database.recordingDao().getPendingUploadCount().collectLatest { count ->
                binding.pendingStatus.text = if (count > 0) {
                    getString(R.string.pending_uploads, count)
                } else {
                    getString(R.string.all_synced)
                }
            }
        }
    }
}
