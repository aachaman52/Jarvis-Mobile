# Jarvis Mobile MVP

Full requested MVP source, expanded from Day 1. **Not yet build-verified or device-verified. No APK is included.** This is not a claim that the app is finished or production-ready.

## Build an APK

The shortest route without setting up Android Studio:

1. Put the **contents** of this directory at the root of a GitHub repository, including `.github`.
2. Open Actions → Build Jarvis Mobile APK → Run workflow.
3. After a successful run, download `JarvisMobile-debug-apk`, extract `app-debug.apk`, and install it.
4. If the workflow fails, use the uploaded reports/build log to fix it before testing.

The workflow has been written but **not executed**. It builds, runs JVM tests, runs Android lint, and uploads the debug APK. It does not publish a release or run your model.

### Local Android Studio build

Required: JDK 17, Gradle 8.9, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1, Git. First build needs internet for Maven and llama.cpp.

The official Gradle wrapper JAR/scripts could not be fetched in the authoring environment. Install Gradle 8.9, then run `bash bootstrap-gradle.sh` (or `./bootstrap-gradle.ps1` on Windows) to generate them. Open this folder in Android Studio, set the SDK location, sync, and run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. Only arm64 phones are targeted; an x86 emulator cannot load this native library.

## First run

- Direct tools work with no model loaded.
- Settings → Select GGUF → pick a local instruction-tuned Q4 model → Load.
- Start with 2–3B, context 2048, output 384. No model is bundled or downloaded by the app.
- A model must be supported by the pinned llama.cpp release and include a supported embedded chat template. Arbitrary/newer GGUF architectures are not guaranteed.
- Import streams to private storage with GGUF magic, size and free-space checks. Successful replacement retains one imported model, not a growing collection. The file you selected outside the app is not deleted.
- Native loading can still fail under real memory pressure, and Android may kill the app under severe pressure. No mobile app can guarantee otherwise.
- No model automatically preloads at startup.

## Included

| Area | Implementation |
|---|---|
| Chat | Compose text input, history, streamed response, stop |
| Local model | Real llama.cpp JNI; serialized load/unload/generation; CPU only; mmap; max 4 threads |
| Routing | Rules first, then local model or optional cloud offer; RAM/battery/thermal/connectivity/privacy gates |
| Actions | All 10 requested tools, registry, strict field/type checks, permission gate |
| Model tools | Whole JSON object only; no code execution; confirmation before execution |
| Voice | Push-to-talk system recognizer; Android TTS; lifecycle cleanup |
| Memory | Room conversations, notes and preferences; last 8 messages offered to model |
| Bounds | Last 100 chat messages retained; context trimmed by native token count; oversized final request rejected |
| Settings | Context, output length, TTS, cloud/privacy/debug, model load/unload |
| Debug | Request, route, call, result, elapsed time, available RAM |
| Recommendation | DeviceProfile, ModelTier and replaceable recommendation interface only |
| Cloud | CloudModelProvider interface, injected registry, settings/privacy gates and per-request confirmation |

### Cloud is an extension point, not a configured service

No provider and no API key are included. `CloudRegistry.provider` remains null by default, so enabling the toggle alone does not send data anywhere. Inject a real provider in `JarvisApplication` when you choose one; supply credentials through your own secure configuration, never in committed source.

Cloud calls are off the main thread and receive only the explicitly approved current request, not notes or conversation history. Cloud text is never automatically executed as an Android action.

## Android limitations

- Timers and alarms use clock intents. The clock app decides whether to show confirmation; Jarvis reports dispatch, not guaranteed scheduling. No special exact-alarm permission is needed because the clock app owns the alarm.
- Opening apps matches launcher labels exactly (case-insensitive) or package names. Ambiguous labels request a package name.
- Media keys target Android's current media player. Some players ignore them. No Accessibility Service or notification-listener access is used.
- Offline voice is not universal. With privacy enabled, microphone input is refused unless Android exposes an on-device recognizer (API 31+). On Android 10/11 use text or explicitly disable privacy for system recognition.
- With privacy disabled, the system speech provider may use the network even though offline is preferred.
- Offline TTS requires a matching installed offline voice. Speech errors leave text usable.
- Privacy mode blocks cloud inference and network-dependent speech, not explicitly requested browser URLs/searches.
- Camera/microphone runtime permissions are requested only when needed. Missing flashlight, clock, browser, recognizer or permission produces an error rather than a claimed success.
- Battery temperature is labeled as battery temperature, not CPU temperature.

## Dependencies

- Compose Material 3/activity/lifecycle: official UI, ViewModel and lifecycle management.
- Coroutines: background work, serialized native ownership and cancellation.
- Room + kapt: SQLite storage plus compile-time DAO generation. No embeddings/search library.
- llama.cpp b5046 via CMake FetchContent: CPU GGUF runtime; source compiled into APK. No moving master branch.
- JUnit and desktop org.json: test-only routing/parser tests.

System APIs cover speech, TTS, camera torch, intents and media keys. There is no cloud SDK, network client, Accessibility service or wake-word engine.

## Verification status

- Source audit script and shell syntax check: run in authoring environment.
- Android build attempt: blocked, `gradle: command not found`.
- SDK/NDK, Kotlin compiler, physical phone and GGUF weights were unavailable.
- Fetching native headers/source was blocked. JNI compatibility with the pinned upstream release still needs the first real native build.
- JVM tests are included, **not run** here.
- APK installation, inference quality/speed, RAM peaks, permissions and Android interactions are **not verified**.

Run `node scripts/source-audit.mjs` for limited source checks. These do not replace compilation. See `ACCEPTANCE.md` for the phone test checklist.
