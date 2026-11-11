# Cyhai AI

Cyhai AI is an Android-first client shell for user-provided AI models. The first milestone focuses on the app structure, model-file registration, and repeatable APK builds.

## Current milestone (0.1.0)

- Dark mobile UI with Chat, History, Models, and Settings tabs.
- Android Storage Access Framework import flow; selected file URIs are persisted.
- Basic model catalog with file name, extension, category, and selected-model state.
- GitHub Actions workflow builds a debug APK and uploads it as an artifact.

## Important compatibility note

Importing a file is not the same as running it. Model formats require compatible inference runtimes and model architectures. This initial milestone does **not** include a text, vision, speech-recognition, or text-to-speech inference engine yet. The chat screen explicitly reports this rather than pretending to generate model output.

Planned adapter targets will be evaluated separately, for example:
- GGUF via a llama.cpp-based Android runtime.
- ONNX via ONNX Runtime Mobile.
- TFLite via TensorFlow Lite.
- Vision and audio models through runtimes that support their exact architectures.

Safetensors, PyTorch checkpoints, and arbitrary binary files cannot be assumed runnable on Android just because they can be selected.

## Build

The GitHub Actions workflow runs on pushes to `main` and can also be started manually. It installs Gradle 8.9 and Android SDK, runs:

```bash
gradle --no-daemon assembleDebug
```

The APK is uploaded as the `cyhai-debug-apk` artifact. Download it from the successful workflow run under the Actions tab.

## Install

Download the artifact ZIP, extract it, then install `app-debug.apk` on an Android device. Android may ask you to allow installation from the browser or file manager used to open the APK.

## Project details

- Application ID: `com.cyhai.ai`
- Minimum Android version: Android 8.0 (API 26)
- Target SDK: 35
- Kotlin + Jetpack Compose
