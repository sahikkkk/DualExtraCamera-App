package com.example.cameraapp

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
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
import java.util.concurrent.Executor

class BackgroundVideoService : LifecycleService() {

    private var activeRecording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private val mainExecutor: Executor by lazy { ContextCompat.getMainExecutor(this) }

    companion object {
        const val ACTION_START_RECORDING = "com.example.cameraapp.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.cameraapp.STOP_RECORDING"
        const val CHANNEL_ID = "bg_camera_recording_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "BgVideoService"

        @Volatile
        var isServiceRecording: Boolean = false
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
                if (isServiceRecording) {
                    Log.w(TAG, "Already recording, ignore START")
                    return START_STICKY
                }
                val hasAudio = intent.getBooleanExtra("hasAudio", true)
                val qualityName = intent.getStringExtra("quality") ?: "HD"
                val lensFacing = intent.getIntExtra("lensFacing", CameraSelector.LENS_FACING_BACK)
                startForegroundRecording(hasAudio, qualityName, lensFacing)
            }
            ACTION_STOP_RECORDING -> stopForegroundRecording()
        }
        return START_STICKY
    }

    private fun startForegroundRecording(hasAudio: Boolean, qualityName: String, lensFacing: Int) {
        val notification = buildNotification("Recording video in background…")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "startForeground fallback failed", e2)
                stopSelf()
                return
            }
        }

        isServiceRecording = true
        // Small delay so previous UI binding can fully release
        mainExecutor.execute {
            Handler(mainLooper).postDelayed({
                setupCameraAndRecord(hasAudio, qualityName, lensFacing)
            }, 250)
        }
    }

    private fun setupCameraAndRecord(hasAudio: Boolean, qualityName: String, lensFacing: Int) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                val quality = when (qualityName.uppercase(Locale.US)) {
                    "FHD", "1080P" -> Quality.FHD
                    "UHD", "4K" -> Quality.UHD
                    else -> Quality.HD
                }

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(quality))
                    .build()
                val capture = VideoCapture.withOutput(recorder)
                videoCapture = capture

                val selector = when (lensFacing) {
                    CameraSelector.LENS_FACING_FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.bindToLifecycle(this, selector, capture)

                val name = "VID_BG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(System.currentTimeMillis())

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DualExtraCamera")
                    }
                }

                val outputOptions = MediaStoreOutputOptions.Builder(
                    contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).setContentValues(contentValues).build()

                var pending = capture.output.prepareRecording(this, outputOptions)

                val audioGranted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasAudio && audioGranted) {
                    try {
                        pending = pending.withAudioEnabled()
                    } catch (_: SecurityException) {
                        Log.w(TAG, "Audio not available, recording silent")
                    }
                }

                activeRecording = pending.start(mainExecutor) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            updateNotification("Recording…")
                        }
                        is VideoRecordEvent.Finalize -> {
                            isServiceRecording = false
                            if (event.hasError()) {
                                Log.e(TAG, "Finalize error: ${event.error}")
                                updateNotification("Recording failed: ${event.error}")
                            } else {
                                updateNotification("Video saved")
                            }
                            stopForegroundRecording()
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "setupCameraAndRecord failed", e)
                isServiceRecording = false
                updateNotification("Failed to start camera")
                stopForegroundRecording()
            }
        }, mainExecutor)
    }

    private fun stopForegroundRecording() {
        try {
            activeRecording?.stop()
        } catch (_: Exception) {
        }
        activeRecording = null
        videoCapture = null
        isServiceRecording = false
        try {
            val provider = ProcessCameraProvider.getInstance(this).get()
            provider.unbindAll()
        } catch (_: Exception) {
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Background Video Recording")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_menu_camera)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_media_pause,
                "Stop",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, BackgroundVideoService::class.java).apply {
                        action = ACTION_STOP_RECORDING
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Video Recording",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for background camera video recording"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopForegroundRecording()
        super.onDestroy()
    }
}
