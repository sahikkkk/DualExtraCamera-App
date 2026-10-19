package com.example.cameraapp

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.text.SimpleDateFormat
import java.util.Locale

class BackgroundVideoService : LifecycleService() {

    private var activeRecording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    companion object {
        const val ACTION_START_RECORDING = "com.example.cameraapp.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.cameraapp.STOP_RECORDING"
        const val CHANNEL_ID = "bg_camera_recording_channel"
        const val NOTIFICATION_ID = 1001

        var isServiceRecording = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val hasAudio = intent.getBooleanExtra("hasAudio", true)
                startForegroundRecording(hasAudio)
            }
            ACTION_STOP_RECORDING -> {
                stopForegroundRecording()
            }
        }
        return START_STICKY
    }

    private fun startForegroundRecording(hasAudio: Boolean) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, BackgroundVideoService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Background Video Recording")
            .setContentText("Recording video in background...")
            .setSmallIcon(R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_media_pause, "Stop Recording", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isServiceRecording = true
        setupCameraAndRecord(hasAudio)
    }

    private fun setupCameraAndRecord(hasAudio: Boolean) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()

                val capture = VideoCapture.withOutput(recorder)
                this.videoCapture = capture

                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    capture
                )

                val name = "VID_BG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(System.currentTimeMillis())

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ExtraCameraApp")
                    }
                }

                val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
                    contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).setContentValues(contentValues).build()

                var pendingRecording = capture.output.prepareRecording(this, mediaStoreOutputOptions)

                val audioGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasAudio && audioGranted) {
                    try {
                        pendingRecording = pendingRecording.withAudioEnabled()
                    } catch (e: SecurityException) {
                        // Fallback if permission security exception occurs
                    }
                }

                activeRecording = pendingRecording.start(
                    ContextCompat.getMainExecutor(this)
                ) { recordEvent: VideoRecordEvent ->
                    if (recordEvent is VideoRecordEvent.Finalize) {
                        isServiceRecording = false
                        if (!recordEvent.hasError()) {
                            Toast.makeText(this, "Background Video saved to gallery!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Background video error code: ${recordEvent.error}", Toast.LENGTH_LONG).show()
                        }
                        stopForegroundRecording()
                    }
                }

            } catch (e: Exception) {
                isServiceRecording = false
                stopForegroundRecording()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopForegroundRecording() {
        try {
            activeRecording?.stop()
            activeRecording = null
        } catch (e: Exception) {
            // Ignore stop exceptions
        }
        isServiceRecording = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Background Video Recording Channel"
            val descriptionText = "Channel for background camera video recording"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundRecording()
    }
}
