# DualExtraCamera 📸🎥

**DualExtraCamera** is a modern, high-performance, open-source Android camera application built with **Jetpack Compose** and **CameraX**. It features multi/extra camera lens switching, seamless background video recording via Foreground Services, and professional photography controls.

---

## ✨ Features & Capabilities

- **Extra & Multi-Camera Switching**: Seamlessly discover and switch between Front, Back, Wide-Angle, Telephoto, and External/Extra camera lenses available on the device.
- **Background Video Recording**: Continue recording high-definition videos in the background even when minimizing the app, locking the screen, or opening other applications (powered by a dedicated Foreground Service with notification controls).
- **High-Quality Photo Capture**: Capture crisp JPEG photos saved directly to the device gallery (`Pictures/DualExtraCamera`).
- **Professional Video Recording**: Record MP4 videos with audio support in 720p HD, 1080p FHD, or 4K UHD resolutions, saved directly to `Movies/DualExtraCamera`.
- **Flash & Torch Control**: Auto/On/Off flash modes for photos and dedicated torch toggle for video recording.
- **Tap-to-Focus & Smooth Zoom**: Tap anywhere on the viewfinder for auto-focus and auto-exposure metering with an animated focus ring. Use pinch-to-zoom or the smooth zoom slider (1x - 10x).
- **Aspect Ratio & Quality Settings**: Switch between 4:3, 16:9, and 1:1 square aspect ratios.
- **Rule-of-Thirds Grid Lines**: Toggleable 3x3 grid lines overlay for perfect framing.
- **Self-Timer Countdown**: 3-second and 10-second timer countdown with animated visual indicators.
- **Quick Gallery Thumbnail Preview**: Instant thumbnail preview of the last captured photo or video in the corner; tapping opens it directly in the device gallery.
- **Dual / Concurrent Camera (PiP)**: Run dual cameras simultaneously in a Picture-in-Picture window (hardware permitting).

---

## 📱 What It Can Do & What It Cannot Do

### ✅ What It Can Do:
- Switch between all available physical and external camera lenses.
- Record videos in the background while using other apps or locking the screen.
- Save captured media directly to public device storage (`Pictures/` and `Movies/`).
- Provide manual focus, zoom, flash, and grid overlays.
- Run smoothly on 99.9% of Android devices (Android 7.0 Nougat to Android 15).

### ❌ What It Cannot Do (Hardware Limitations):
- **Concurrent Dual Camera on all devices**: Picture-in-Picture dual camera mode requires hardware support from the device's camera HAL. On devices lacking concurrent camera hardware, the app will notify you.
- **Raw DNG Capture**: Currently optimized for compressed JPEG and MP4 outputs.
- **Third-Party External USB UVC Webcams**: Works with standard Android Camera2/CameraX external camera APIs; custom proprietary USB UVC drivers require external bridge adapters.

---

## 🛠️ System & Device Requirements

- **Minimum SDK**: API 24 (Android 7.0 Nougat)
- **Target SDK**: API 35 (Android 15)
- **Permissions Required**: Camera, Record Audio, Storage/Media, Post Notifications, and Foreground Service.

---

## 📦 How to Build the APK

You can easily compile and generate the APK using **Android Studio**:

1. Clone or download this repository.
2. Open the project folder in **Android Studio** (let Gradle sync finish).
3. In the top menu, go to **`Build` > `Build Bundle(s) / APK(s)` > `Build APK(s)`**.
4. Once completed, click **`locate`** in the notification popup to find your generated `app-debug.apk`.

Alternatively, build via command line:
```bash
./gradlew assembleDebug
```

---

## 📄 License & Attribution

This project is **open source and free to edit, fork, and use**. 
However, **attribution is required**: any derivative works, modifications, or redistribution must prominently display credit to the original author (**sahikkkk** / **DualExtraCamera**). 

See the [LICENSE](LICENSE) file for full details.
