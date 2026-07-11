# Forge Android App

`forge-app/` is the native Kotlin/Compose shell that wraps the Forge agent
engine (`src/loop.js`, `src/router.js`, `src/state.js`). The agent loop has
been ported to Kotlin under `app/src/main/java/com/forge/app/agent/` and runs
inside a foreground service, streaming live state to a Material You 3 UI.

## Build

Run from inside `forge-app/` (or pass `-p forge-app` to the wrapper from the
repo root):

```bash
# Debug APK
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Unit tests (JVM) — validates the ported Sense→Decide→Act→Check loop
./gradlew :app:testDebugUnitTest

# Install on a connected device/emulator
./gradlew :app:installDebug
```

# Notes
- Requires JDK 17+ to run Gradle (the Kotlin compile uses the 17 toolchain,
  auto-provisioned via the foojay resolver plugin).
- `local.properties` must point at the Android SDK:
  `sdk.dir=/Users/reyyan/Library/Android/sdk`
- Toolchain mirrors the generated `shg-ledger/` template: AGP 9.0.1,
  Kotlin 2.3.20, Gradle 9.1.0, Compose BOM 2026.03.01, Navigation3 1.0.1.
- Inference defaults to the bundled mock generator (`InferenceRouter(useMock = true)`)
  so the app is fully demonstrable offline. Flip to `false` in `AppContainer`
  to hit the live on-device Gemma endpoint at `localhost:8080` (mirrors `router.js`).