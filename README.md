# FlamEdge Application

Real-time Android edge detection pipeline powered by Camera2, JNI, OpenCV (C++), and OpenGL ES 2.0, plus a lightweight TypeScript web viewer for sharing processed frames.

## Features

- Camera2 preview at 640×480 with buffer reuse and background threading.
- JNI bridge that feeds NV21 frames into native C++ for OpenCV processing (Canny edges).
- OpenGL ES 2.0 renderer that uploads processed RGBA textures and presents them via `GLSurfaceView`.
- Toggle between raw camera feed and processed edge output.
- FPS smoothing overlay on the Android UI.
- TypeScript web viewer that displays a captured processed frame with metadata.
- Front/back camera switch button.

## Project Layout

```
.
├── app/                 # Android application module (Camera2, JNI, GL)
├── jni/                 # Placeholder for additional native modules (optional)
├── gl/                  # Placeholder for GLSL shaders or utilities (optional)
├── web/                 # TypeScript web viewer
├── docs/                # Screenshots, GIFs, diagrams
├── build.gradle         # Root Gradle configuration
├── settings.gradle      # Gradle settings
└── README.md
```

## Android Setup

### Prerequisites

- Android Studio Giraffe (or newer) with NDK side-by-side r25c (`25.2.9519653`).
- CMake 3.22.1 (bundled with Android Studio).
- OpenCV Android SDK extracted (point Gradle/CMake to the package or drop the AAR inside `app/libs`).
- A physical device or emulator with camera support (Android 7.0+, API 24).

### Building

1. Open the project in Android Studio (`File > Open > edge-viewer`).
2. Ensure `local.properties` points to your Android SDK and NDK installations.
3. Import the OpenCV module or update `CMakeLists.txt` with the correct include/library directories.
   - Set `OpenCV_DIR` (env var or CMake arg) to your OpenCV Android SDK path.
     Example (Windows PowerShell):
     - `$env:OpenCV_DIR="C:/dev/OpenCV-android-sdk/sdk/native/jni"`
     Or in Android Studio CMake config variables: `OpenCV_DIR=C:/dev/OpenCV-android-sdk/sdk/native/jni`
4. Sync Gradle and build the app (`Build > Make Project` or `./gradlew assembleDebug`).
5. Run on a device (`Run > Run 'app'`).

### Runtime Flow

1. `CameraController` captures YUV_420_888 frames with `ImageReader`.
2. `YuvConverter` packs planes into a direct NV21 `ByteBuffer`.
3. `NativeBridge.processFrame` forwards the buffer to C++ via JNI.
4. Native code converts NV21 → BGR → grayscale → Canny edges and outputs RGBA pixels.
5. `PipelineRenderer` uploads the RGBA buffer into an OpenGL texture for display.
6. `MainActivity` toggles between raw and processed views and keeps an FPS overlay up to date.
7. Switch camera button lets you toggle between back and front camera.

### FPS & Toggle

- Use the on-screen button to switch between raw texture (TextureView) and processed edges (GLSurfaceView).
- FPS is smoothed over 10 samples and shown in the top-left corner.

## Native / OpenCV Notes

- The native module expects direct `ByteBuffer` inputs for zero-copy NV21 data.
- Update `OpenCV_DIR` in `CMakeLists.txt`/CMake cache to your SDK location.
- Tests rely on `cv::cvtColor` and `cv::Canny` with thresholds `(80, 160)`; adjust as needed.

### Windows path tips
- Use forward slashes or escape spaces properly. For example:
  `C:/Users/First Last/Dev/OpenCV-android-sdk/sdk/native/jni` (space is OK)


## Web Viewer

### Prerequisites

- Node.js 18+
- npm 9+

### Commands

```bash
cd web
npm install
npm run build   # Compiles TS and copies static assets to dist/
npm start       # Serves dist/ via http-server (default http://localhost:8080)
```

- Replace `web/src/assets/sampleFrame.ts` with a real `data:image/png;base64,` string captured from the Android app, or use the live device frame endpoint below.
- Live frames: open the web viewer with a query param pointing to the Android device endpoint:
  - `http://localhost:8080` device (ADB forward):
    - `http://localhost:8080/?url=http://localhost:8080/frame`
  - Device on same Wi‑Fi (example):
    - `http://localhost:8080/?url=http://192.168.1.50:8080/frame`
  - If the frame fails to load, the viewer gracefully shows a sample image and a hint to set `?url=`.

### ADB port forward (optional)

```bash
adb forward tcp:8080 tcp:8080
```

Then open:

```
http://localhost:8080/?url=http://localhost:8080/frame
```

Tip: Use `adb exec-out screencap -p > processed.png` or add a small export function to save the current RGBA frame and convert it to base64 for the web.

## Capturing Media

1. Connect device via USB.
2. Use `scrcpy --record docs/demo.mp4` while the processed view is visible.
3. Convert MP4 to GIF with `ffmpeg -i docs/demo.mp4 -vf scale=360:-1 docs/demo.gif`.
4. Capture stills via `adb exec-out screencap -p > docs/processed.png`.

## Screenshots

Embed your captured media here after placing files in `docs/`:

```markdown
![Processed Frame](docs/processed.png)

![Demo GIF](docs/demo.gif)
```

## Testing Checklist

- [ ] Build succeeds on Android (Debug).
- [ ] Processed view renders ≥ 10 FPS at 640×480.
- [ ] Toggle between raw/processed works without tearing.
- [ ] JNI library loads without `UnsatisfiedLinkError`.
- [ ] Web viewer renders the static frame and metadata.
- [ ] Web viewer displays live frames via `?url=http://<device-ip>:8080/frame` (or ADB forward).
- [ ] HTTP frame server serves `GET /frame` with PNG and CORS/no-cache headers.

## Future Enhancements

- Native texture updates via `AImageReader` + OpenGL shared context to avoid the Java hop.
- CameraX integration with `ImageAnalysis` for simplified lifecycle handling.
- WebSocket endpoint on the device to stream fresh frames to the web client.
- GPU-accelerated shaders for additional effects (invert, threshold).

## Architecture Overview

Android (app module):

- Camera capture: `CameraController` produces NV21 via `ImageReader`.
- JNI bridge: `NativeBridge.processFrame` sends NV21 to C++.
- Native C++: NV21 → grayscale → Canny → RGBA using OpenCV.
- OpenGL renderer: `PipelineRenderer` uploads RGBA to texture and renders.
- HTTP frame server: `HttpFrameServer` serves latest PNG at `/frame` with CORS/no-cache.
- UI controls: toggle raw/processed, switch camera, apply filters, show FPS.

Web (web module):

- TypeScript viewer: polls `?url` endpoint every second; falls back to a sample image.
- Minimal static site build with `tsc`; served via `http-server`.

## Submission Prep

1. Ensure meaningful, modular commits (no single “final commit”).
2. Push the project to a public or shareable private GitHub/GitLab repo.
3. Add screenshots/GIFs under `docs/` and reference them in this README.
4. Verify Android build, native `.so` load, processed FPS ≥ 10–15, and live web frames.
5. Fill the Flam Evaluation Form: `https://forms.gle/sBouUWUKxy7pf6mKA`.

## Setup Notes

- `local.properties` must include correct paths (Windows path escaping required):

```
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
OpenCV_DIR=E\:/opencv-android-sdk/OpenCV-android-sdk/sdk/native/jni
```

- Alternatively, set `OpenCV_DIR` via environment or Gradle CMake arguments.

## License

Proprietary License © Akshat Trivedi – see `LICENSE`.
Unauthorized copying, redistribution, or derivative works are prohibited.

## Ownership & Attribution

This work is owned by Akshat Trivedi. Any use or reference must attribute the work to "Akshat Trivedi". See `NOTICE` for details.
