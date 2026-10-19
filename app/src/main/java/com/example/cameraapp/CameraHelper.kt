package com.example.cameraapp

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

enum class FlashState { OFF, ON, AUTO }

enum class TimerMode(val seconds: Int) {
    OFF(0),
    THREE_SEC(3),
    TEN_SEC(10)
}

enum class AspectRatioMode(val label: String) {
    RATIO_4_3("4:3"),
    RATIO_16_9("16:9"),
    RATIO_1_1("1:1")
}

enum class VideoQualityMode(val label: String, val quality: Quality) {
    HD_720P("720p HD", Quality.HD),
    FHD_1080P("1080p FHD", Quality.FHD),
    UHD_4K("4K UHD", Quality.UHD)
}

object CameraHelper {

    fun getLatestMediaUri(context: Context): Uri? {
        return try {
            val imageUri = queryLatest(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            val videoUri = queryLatest(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            videoUri ?: imageUri
        } catch (_: Exception) {
            null
        }
    }

    private fun queryLatest(context: Context, collection: Uri): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        return context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                ContentUris.withAppendedId(collection, id)
            } else null
        }
    }

    fun capturePhoto(
        context: Context,
        imageCapture: ImageCapture,
        flashState: FlashState,
        onPhotoSaved: (Uri) -> Unit,
        onError: (Exception) -> Unit
    ) {
        imageCapture.flashMode = when (flashState) {
            FlashState.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashState.ON -> ImageCapture.FLASH_MODE_ON
            FlashState.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }

        val name = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DualExtraCamera")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri
                    if (savedUri != null) {
                        Toast.makeText(context, "Photo saved to gallery", Toast.LENGTH_SHORT).show()
                        onPhotoSaved(savedUri)
                    } else {
                        val latestUri = getLatestMediaUri(context)
                        if (latestUri != null) onPhotoSaved(latestUri)
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(context, "Photo capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                    onError(exc)
                }
            }
        )
    }

    fun startVideoRecording(
        context: Context,
        videoCapture: VideoCapture<Recorder>,
        hasAudioPermission: Boolean,
        onEvent: (VideoRecordEvent) -> Unit,
        onRecordingStarted: (Recording) -> Unit,
        onVideoSaved: (Uri) -> Unit
    ) {
        val name = "VID_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DualExtraCamera")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        var pendingRecording = videoCapture.output
            .prepareRecording(context, mediaStoreOutputOptions)

        val audioGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudioPermission && audioGranted) {
            try {
                pendingRecording = pendingRecording.withAudioEnabled()
            } catch (_: SecurityException) {
                // Fallback to silent video if permission is restricted
            }
        }

        val activeRecording = pendingRecording.start(
            ContextCompat.getMainExecutor(context)
        ) { recordEvent: VideoRecordEvent ->
            onEvent(recordEvent)
            if (recordEvent is VideoRecordEvent.Finalize) {
                if (!recordEvent.hasError()) {
                    val savedUri = recordEvent.outputResults.outputUri
                    Toast.makeText(context, "Video saved to gallery", Toast.LENGTH_SHORT).show()
                    onVideoSaved(savedUri)
                } else {
                    Toast.makeText(context, "Video recording error: ${recordEvent.error}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        onRecordingStarted(activeRecording)
    }

    fun openInGallery(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot open gallery app", Toast.LENGTH_SHORT).show()
        }
    }

    fun getCameraLabel(cameraInfo: CameraInfo): String {
        return try {
            val lensFacing = cameraInfo.lensFacing
            when (lensFacing) {
                CameraSelector.LENS_FACING_BACK -> "Back Camera"
                CameraSelector.LENS_FACING_FRONT -> "Front Camera"
                CameraSelector.LENS_FACING_EXTERNAL -> "External Camera"
                else -> "Extra Camera"
            }
        } catch (_: Exception) {
            "Extra Camera"
        }
    }
}
