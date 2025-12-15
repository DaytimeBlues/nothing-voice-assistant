package com.nothing.voiceassistant.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nothing.voiceassistant.R
import com.nothing.voiceassistant.data.Recording
import com.nothing.voiceassistant.data.RecordingDatabase
import com.nothing.voiceassistant.databinding.ActivityRecordingsBinding
import com.nothing.voiceassistant.drive.UploadWorker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * Activity displaying list of all recordings with playback and management.
 */
class RecordingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRecordingsBinding
    private lateinit var database: RecordingDatabase
    private lateinit var adapter: RecordingsAdapter
    private var mediaPlayer: MediaPlayer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        database = RecordingDatabase.getInstance(this)
        
        setupToolbar()
        setupRecyclerView()
        observeRecordings()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener { 
            finish() 
        }
    }
    
    private fun setupRecyclerView() {
        adapter = RecordingsAdapter(
            onPlayClick = { recording -> playRecording(recording) },
            onRetryClick = { recording -> retryUpload(recording) },
            onDeleteClick = { recording -> confirmDelete(recording) }
        )
        
        binding.recordingsList.layoutManager = LinearLayoutManager(this)
        binding.recordingsList.adapter = adapter
    }
    
    private fun observeRecordings() {
        lifecycleScope.launch {
            database.recordingDao().getAllRecordings().collectLatest { recordings ->
                adapter.submitList(recordings)
                
                // Toggle empty state
                binding.emptyState.visibility = if (recordings.isEmpty()) View.VISIBLE else View.GONE
                binding.recordingsList.visibility = if (recordings.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
    
    private fun playRecording(recording: Recording) {
        val file = File(recording.filePath)
        if (!file.exists()) {
            return
        }
        
        // Stop any current playback
        mediaPlayer?.release()
        
        mediaPlayer = MediaPlayer().apply {
            setDataSource(recording.filePath)
            prepare()
            start()
        }
    }
    
    private fun retryUpload(recording: Recording) {
        UploadWorker.enqueueUpload(this, recording.id)
    }
    
    private fun confirmDelete(recording: Recording) {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteRecording(recording)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun deleteRecording(recording: Recording) {
        lifecycleScope.launch {
            // Delete local file
            File(recording.filePath).delete()
            
            // Delete from database
            database.recordingDao().delete(recording)
        }
    }
    
    /**
     * Adapter for recordings list.
     */
    inner class RecordingsAdapter(
        private val onPlayClick: (Recording) -> Unit,
        private val onRetryClick: (Recording) -> Unit,
        private val onDeleteClick: (Recording) -> Unit
    ) : RecyclerView.Adapter<RecordingsAdapter.ViewHolder>() {
        
        private var recordings = listOf<Recording>()
        
        fun submitList(list: List<Recording>) {
            recordings = list
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recording, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(recordings[position])
        }
        
        override fun getItemCount() = recordings.size
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val dateText: TextView = itemView.findViewById(R.id.recordingDate)
            private val durationText: TextView = itemView.findViewById(R.id.recordingDuration)
            private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
            private val statusText: TextView = itemView.findViewById(R.id.statusText)
            private val transcriptPreview: TextView = itemView.findViewById(R.id.transcriptPreview)
            private val playButton: ImageButton = itemView.findViewById(R.id.playButton)
            private val retryButton: ImageButton = itemView.findViewById(R.id.retryButton)
            private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
            
            fun bind(recording: Recording) {
                dateText.text = recording.getDisplayName()
                durationText.text = recording.getDurationString()
                
                // Set status indicator color
                val statusColor = when {
                    recording.errorMessage != null -> R.color.status_error
                    recording.isUploaded && recording.isTranscribed -> R.color.status_uploaded
                    recording.isUploaded -> R.color.status_uploaded
                    else -> R.color.status_pending
                }
                statusIndicator.setBackgroundResource(R.drawable.recording_dot)
                statusIndicator.backgroundTintList = itemView.context.getColorStateList(statusColor)
                
                // Set status text
                statusText.text = when {
                    recording.errorMessage != null -> recording.errorMessage
                    recording.isTranscribed -> getString(R.string.transcribed)
                    recording.isUploaded -> getString(R.string.uploaded)
                    else -> getString(R.string.upload_pending)
                }
                
                // Show transcript preview if available
                if (!recording.transcript.isNullOrBlank()) {
                    transcriptPreview.text = recording.transcript
                    transcriptPreview.visibility = View.VISIBLE
                } else {
                    transcriptPreview.visibility = View.GONE
                }
                
                // Show retry button if there was an error
                retryButton.visibility = if (recording.errorMessage != null || !recording.isUploaded) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                
                // Set click listeners
                playButton.setOnClickListener { onPlayClick(recording) }
                retryButton.setOnClickListener { onRetryClick(recording) }
                deleteButton.setOnClickListener { onDeleteClick(recording) }
            }
        }
    }
}
