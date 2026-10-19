package com.example.cameraapp

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

enum class CameraMode { PHOTO, VIDEO }

@Composable
fun CameraScreen(
    hasAudioPermission: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var selectedMode by remember { mutableStateOf(CameraMode.PHOTO) }
    var cameraIndex by remember { mutableIntStateOf(0) }
    var availableCameraSelectors by remember { mutableStateOf<List<Pair<CameraSelector, String>>>(emptyList()) }

    var isRecording by remember { mutableStateOf(false) }
    var isBgRecordingActive by remember { mutableStateOf(false) }
    var recordingDurationNanos by remember { mutableLongStateOf(0L) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    // Flash, Torch, Grid & Background Recording Toggle
    var flashState by remember { mutableStateOf(FlashState.OFF) }
    var isTorchOn by remember { mutableStateOf(false) }
    var showGridLines by remember { mutableStateOf(false) }
    var enableBackgroundRecordingMode by remember { mutableStateOf(false) }

    // Timer Mode
    var selectedTimer by remember { mutableStateOf(TimerMode.OFF) }
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var isCountingDown by remember { mutableStateOf(false) }

    // Aspect Ratio & Video Quality
    var selectedAspectRatio by remember { mutableStateOf(AspectRatioMode.RATIO_4_3) }
    var selectedVideoQuality by remember { mutableStateOf(VideoQualityMode.FHD_1080P) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    // Zoom & Focus State
    var currentZoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(10f) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    // Shutter Visual Blink Effect
    val shutterAlpha = remember { Animatable(0f) }

    // Focus Ring Animation
    var isFocusing by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (isFocusing) 1f else 1.5f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "FocusScale"
    )
    val focusAlpha by animateFloatAsState(
        targetValue = if (focusPoint != null) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "FocusAlpha"
    )

    // Last Captured Thumbnail
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }

    // Dual / Concurrent Camera
    var isDualCameraActive by remember { mutableStateOf(false) }
    var isConcurrentSupported by remember { mutableStateOf(false) }
    val secondaryPreviewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val recorder = remember(selectedVideoQuality) {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(selectedVideoQuality.quality))
            .build()
    }
    val videoCapture = remember(recorder) { VideoCapture.withOutput(recorder) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Auto-fetch latest saved gallery photo on launch
    LaunchedEffect(Unit) {
        val latest = CameraHelper.getLatestMediaUri(context)
        if (latest != null) {
            lastCapturedUri = latest
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraInfos = cameraProvider.availableCameraInfos

            val concurrentInfos = cameraProvider.availableConcurrentCameraInfos
            isConcurrentSupported = concurrentInfos.isNotEmpty()

            val selectors = cameraInfos.mapIndexed { index, info ->
                val selector = CameraSelector.Builder().addCameraFilter { infos ->
                    infos.filter { it == info }
                }.build()
                val label = CameraHelper.getCameraLabel(info) + " (#${index + 1})"
                Pair(selector, label)
            }

            if (selectors.isNotEmpty()) {
                availableCameraSelectors = selectors
            } else {
                availableCameraSelectors = listOf(
                    Pair(CameraSelector.DEFAULT_BACK_CAMERA, "Back Camera"),
                    Pair(CameraSelector.DEFAULT_FRONT_CAMERA, "Front Camera")
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Bind Primary & Secondary Camera
    LaunchedEffect(cameraIndex, selectedMode, availableCameraSelectors, selectedVideoQuality, isDualCameraActive, enableBackgroundRecordingMode) {
        if (availableCameraSelectors.isEmpty() || enableBackgroundRecordingMode) return@LaunchedEffect

        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val currentSelectorPair = availableCameraSelectors.getOrNull(cameraIndex)
            ?: availableCameraSelectors.first()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        try {
            cameraProvider.unbindAll()

            if (isDualCameraActive && isConcurrentSupported) {
                val concurrentInfos = cameraProvider.availableConcurrentCameraInfos
                if (concurrentInfos.isNotEmpty()) {
                    val primaryInfo = concurrentInfos.first().firstOrNull()
                    val secondaryInfo = concurrentInfos.first().getOrNull(1)

                    if (primaryInfo != null && secondaryInfo != null) {
                        val primarySelector = CameraSelector.Builder().addCameraFilter { infos -> infos.filter { it == primaryInfo } }.build()
                        val secondarySelector = CameraSelector.Builder().addCameraFilter { infos -> infos.filter { it == secondaryInfo } }.build()

                        val secPreview = Preview.Builder().build().also {
                            it.setSurfaceProvider(secondaryPreviewView.surfaceProvider)
                        }

                        val camera = cameraProvider.bindToLifecycle(lifecycleOwner, primarySelector, preview, imageCapture)
                        cameraProvider.bindToLifecycle(lifecycleOwner, secondarySelector, secPreview)

                        boundCamera = camera
                        camera.cameraInfo.zoomState.value?.let { zoom ->
                            minZoomRatio = zoom.minZoomRatio
                            maxZoomRatio = zoom.maxZoomRatio
                        }
                    }
                }
            } else {
                val camera = if (selectedMode == CameraMode.PHOTO) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        currentSelectorPair.first,
                        preview,
                        imageCapture
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        currentSelectorPair.first,
                        preview,
                        videoCapture
                    )
                }

                boundCamera = camera
                camera.cameraInfo.zoomState.value?.let { zoom ->
                    minZoomRatio = zoom.minZoomRatio
                    maxZoomRatio = zoom.maxZoomRatio.coerceAtMost(10f)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Torch Toggle Effect
    LaunchedEffect(isTorchOn, boundCamera) {
        boundCamera?.cameraControl?.enableTorch(isTorchOn)
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
        }
    }

    // Trigger Photo / Video Action
    fun executeCaptureAction() {
        if (selectedTimer.seconds > 0) {
            coroutineScope.launch {
                isCountingDown = true
                countdownRemaining = selectedTimer.seconds
                while (countdownRemaining > 0) {
                    delay(1000)
                    countdownRemaining--
                }
                isCountingDown = false

                if (selectedMode == CameraMode.PHOTO) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    coroutineScope.launch {
                        shutterAlpha.snapTo(0.8f)
                        shutterAlpha.animateTo(0f, animationSpec = tween(200))
                    }
                    CameraHelper.capturePhoto(
                        context = context,
                        imageCapture = imageCapture,
                        flashState = flashState,
                        onPhotoSaved = { uri -> lastCapturedUri = uri },
                        onError = {}
                    )
                }
            }
        } else {
            if (selectedMode == CameraMode.PHOTO) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                coroutineScope.launch {
                    shutterAlpha.snapTo(0.8f)
                    shutterAlpha.animateTo(0f, animationSpec = tween(200))
                }
                CameraHelper.capturePhoto(
                    context = context,
                    imageCapture = imageCapture,
                    flashState = flashState,
                    onPhotoSaved = { uri -> lastCapturedUri = uri },
                    onError = {}
                )
            }
        }
    }

    val currentCameraLabel = availableCameraSelectors.getOrNull(cameraIndex)?.second ?: "Camera"

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Full Screen Live Preview Frame & Gestures
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(boundCamera) {
                    detectTapGestures { offset ->
                        focusPoint = offset
                        isFocusing = true
                        val factory = previewView.meteringPointFactory
                        val point = factory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                        boundCamera?.cameraControl?.startFocusAndMetering(action)

                        coroutineScope.launch {
                            delay(300)
                            isFocusing = false
                            delay(1700)
                            focusPoint = null
                        }
                    }
                }
                .pointerInput(boundCamera, currentZoomRatio) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val newZoom = (currentZoomRatio * zoom).coerceIn(minZoomRatio, maxZoomRatio)
                        currentZoomRatio = newZoom
                        boundCamera?.cameraControl?.setZoomRatio(newZoom)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Full Screen Preview View
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // 3x3 Rule of Thirds Camera Grid Lines Overlay
                if (showGridLines) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val gridColor = Color.White.copy(alpha = 0.35f)

                        drawLine(gridColor, start = Offset(w / 3, 0f), end = Offset(w / 3, h), strokeWidth = 1.5f)
                        drawLine(gridColor, start = Offset(2 * w / 3, 0f), end = Offset(2 * w / 3, h), strokeWidth = 1.5f)

                        drawLine(gridColor, start = Offset(0f, h / 3), end = Offset(w, h / 3), strokeWidth = 1.5f)
                        drawLine(gridColor, start = Offset(0f, 2 * h / 3), end = Offset(w, 2 * h / 3), strokeWidth = 1.5f)
                    }
                }
            }

            // Dual Camera Secondary Picture-in-Picture (PiP)
            if (isDualCameraActive) {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 160.dp)
                        .padding(12.dp)
                        .align(Alignment.TopEnd)
                        .offset(y = 90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                ) {
                    AndroidView(
                        factory = { secondaryPreviewView },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Focus Ring Target
            focusPoint?.let { point ->
                Box(
                    modifier = Modifier
                        .offset { IntOffset(point.x.roundToInt() - 36, point.y.roundToInt() - 36) }
                        .size(72.dp)
                        .scale(focusScale)
                        .alpha(focusAlpha)
                        .border(2.dp, Color.Yellow, CircleShape)
                )
            }

            // Shutter Flash Blink Effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = shutterAlpha.value))
            )

            // Animated Timer Countdown Text
            if (isCountingDown) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$countdownRemaining",
                        color = Color.Yellow,
                        fontSize = 96.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Top Header Glassmorphism Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 12.dp, end = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash / Torch Toggle
            IconButton(
                onClick = {
                    if (selectedMode == CameraMode.PHOTO) {
                        flashState = when (flashState) {
                            FlashState.OFF -> FlashState.ON
                            FlashState.ON -> FlashState.AUTO
                            FlashState.AUTO -> FlashState.OFF
                        }
                    } else {
                        isTorchOn = !isTorchOn
                    }
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(2.dp)
            ) {
                val icon = if (selectedMode == CameraMode.PHOTO) {
                    when (flashState) {
                        FlashState.OFF -> Icons.Default.FlashOff
                        FlashState.ON -> Icons.Default.FlashOn
                        FlashState.AUTO -> Icons.Default.FlashAuto
                    }
                } else {
                    if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff
                }
                Icon(imageVector = icon, contentDescription = "Flash Toggle", tint = Color.White)
            }

            // Background Recording Mode Toggle
            IconButton(
                onClick = {
                    enableBackgroundRecordingMode = !enableBackgroundRecordingMode
                    val statusText = if (enableBackgroundRecordingMode)
                        "Background Recording Enabled (অ্যাপ থেকে বের হলেও রেকর্ড হবে)"
                    else
                        "Background Recording Disabled"
                    Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .background(if (enableBackgroundRecordingMode) Color.Green else Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenShare,
                    contentDescription = "Background Recording Mode",
                    tint = if (enableBackgroundRecordingMode) Color.Black else Color.White
                )
            }

            // Camera Name Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White
            ) {
                Text(
                    text = currentCameraLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Grid Lines Toggle
            IconButton(
                onClick = { showGridLines = !showGridLines },
                modifier = Modifier
                    .background(if (showGridLines) Color.Yellow else Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = "Grid",
                    tint = if (showGridLines) Color.Black else Color.White
                )
            }

            // Dual Camera Toggle
            IconButton(
                onClick = {
                    if (isConcurrentSupported) {
                        isDualCameraActive = !isDualCameraActive
                    } else {
                        Toast.makeText(context, "Dual Concurrent Camera is not supported on this device", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .background(if (isDualCameraActive) Color.Yellow else Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusWeak,
                    contentDescription = "Dual Camera",
                    tint = if (isDualCameraActive) Color.Black else Color.White
                )
            }

            // Settings Menu
            Box {
                IconButton(
                    onClick = { showSettingsMenu = true },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .padding(2.dp)
                ) {
                    Icon(imageVector = Icons.Default.AspectRatio, contentDescription = "Settings", tint = Color.White)
                }

                DropdownMenu(
                    expanded = showSettingsMenu,
                    onDismissRequest = { showSettingsMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Aspect Ratio: 4:3") },
                        onClick = {
                            selectedAspectRatio = AspectRatioMode.RATIO_4_3
                            showSettingsMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Aspect Ratio: 16:9") },
                        onClick = {
                            selectedAspectRatio = AspectRatioMode.RATIO_16_9
                            showSettingsMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Aspect Ratio: 1:1") },
                        onClick = {
                            selectedAspectRatio = AspectRatioMode.RATIO_1_1
                            showSettingsMenu = false
                        }
                    )
                    if (selectedMode == CameraMode.VIDEO) {
                        DropdownMenuItem(
                            text = { Text("Video Quality: 720p HD") },
                            onClick = {
                                selectedVideoQuality = VideoQualityMode.HD_720P
                                showSettingsMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Video Quality: 1080p FHD") },
                            onClick = {
                                selectedVideoQuality = VideoQualityMode.FHD_1080P
                                showSettingsMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Video Quality: 4K UHD") },
                            onClick = {
                                selectedVideoQuality = VideoQualityMode.UHD_4K
                                showSettingsMenu = false
                            }
                        )
                    }
                }
            }

            // Extra Camera Selector Button
            if (availableCameraSelectors.size > 1 && !isRecording && !isDualCameraActive) {
                IconButton(
                    onClick = {
                        cameraIndex = (cameraIndex + 1) % availableCameraSelectors.size
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .padding(2.dp)
                ) {
                    Icon(imageVector = Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                }
            }
        }

        // Background Recording Ready Badge / Recording Indicator
        if (enableBackgroundRecordingMode && !isBgRecordingActive) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Green.copy(alpha = 0.9f),
                contentColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 95.dp)
            ) {
                Text(
                    text = "🟢 BG Recording Ready",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }
        }

        if (isRecording || isBgRecordingActive) {
            val seconds = TimeUnit.NANOSECONDS.toSeconds(recordingDurationNanos) % 60
            val minutes = TimeUnit.NANOSECONDS.toMinutes(recordingDurationNanos)
            val timeString = String.format(Locale.US, "%02d:%02d", minutes, seconds)

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 95.dp)
                    .background(Color.Red.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.White, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBgRecordingActive) "BG Rec Active" else timeString,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        // Bottom Glassmorphism Control Center
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(bottom = 24.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom Slider Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format(Locale.US, "%.1fx", currentZoomRatio),
                    color = Color.Yellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Slider(
                    value = currentZoomRatio,
                    onValueChange = { zoom ->
                        currentZoomRatio = zoom
                        boundCamera?.cameraControl?.setZoomRatio(zoom)
                    },
                    valueRange = minZoomRatio..maxZoomRatio,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Yellow,
                        activeTrackColor = Color.Yellow,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // Mode Selector: PHOTO / VIDEO
            if (!isRecording && !isBgRecordingActive) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "PHOTO",
                        color = if (selectedMode == CameraMode.PHOTO) Color.Yellow else Color.White,
                        fontWeight = if (selectedMode == CameraMode.PHOTO) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clickable { selectedMode = CameraMode.PHOTO }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "VIDEO",
                        color = if (selectedMode == CameraMode.VIDEO) Color.Yellow else Color.White,
                        fontWeight = if (selectedMode == CameraMode.VIDEO) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clickable { selectedMode = CameraMode.VIDEO }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Bottom Action Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Gallery Preview Thumbnail
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.DarkGray)
                        .border(2.dp, Color.White, RoundedCornerShape(14.dp))
                        .clickable {
                            lastCapturedUri?.let { uri ->
                                CameraHelper.openInGallery(context, uri)
                            } ?: Toast.makeText(context, "No gallery item available", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (lastCapturedUri != null) {
                        AsyncImage(
                            model = lastCapturedUri,
                            contentDescription = "Last Captured",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = "Gallery",
                            tint = Color.White
                        )
                    }
                }

                // Shutter / Trigger Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.semantics(mergeDescendants = true) {}
                ) {
                    if (selectedMode == CameraMode.PHOTO) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .border(4.dp, Color.White, CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                                .clickable { executeCaptureAction() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Take Photo",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .border(4.dp, if (isRecording || isBgRecordingActive) Color.Red else Color.White, CircleShape)
                                .background(if (isRecording || isBgRecordingActive) Color.Red.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.25f))
                                .clickable {
                                    if (enableBackgroundRecordingMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        // Unbind UI camera to free hardware encoder for background service
                                        try {
                                            val providerFuture = ProcessCameraProvider.getInstance(context)
                                            providerFuture.addListener({
                                                providerFuture.get().unbindAll()
                                            }, ContextCompat.getMainExecutor(context))
                                        } catch (e: Exception) {}

                                        val intent = Intent(context, BackgroundVideoService::class.java).apply {
                                            action = if (isBgRecordingActive)
                                                BackgroundVideoService.ACTION_STOP_RECORDING
                                            else
                                                BackgroundVideoService.ACTION_START_RECORDING
                                            putExtra("hasAudio", hasAudioPermission)
                                        }
                                        ContextCompat.startForegroundService(context, intent)
                                        isBgRecordingActive = !isBgRecordingActive
                                    } else {
                                        if (isRecording) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            activeRecording?.stop()
                                            activeRecording = null
                                            isRecording = false
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            CameraHelper.startVideoRecording(
                                                context = context,
                                                videoCapture = videoCapture,
                                                hasAudioPermission = hasAudioPermission,
                                                onEvent = { recordEvent ->
                                                    if (recordEvent is VideoRecordEvent.Status) {
                                                        recordingDurationNanos = recordEvent.recordingStats.recordedDurationNanos
                                                    } else if (recordEvent is VideoRecordEvent.Start) {
                                                        isRecording = true
                                                    } else if (recordEvent is VideoRecordEvent.Finalize) {
                                                        isRecording = false
                                                        recordingDurationNanos = 0L
                                                    }
                                                },
                                                onRecordingStarted = { recording ->
                                                    activeRecording = recording
                                                },
                                                onVideoSaved = { uri ->
                                                    lastCapturedUri = uri
                                                }
                                            )
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRecording || isBgRecordingActive) Icons.Default.Stop else Icons.Default.Videocam,
                                contentDescription = if (isRecording || isBgRecordingActive) "Stop Recording" else "Record Video",
                                tint = if (isRecording || isBgRecordingActive) Color.Red else Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.size(52.dp))
            }
        }
    }
}
