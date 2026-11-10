# Test report – v1.0.0

**Nothing in this project has been compiled or executed yet.** It was written in an environment without the Android SDK, Gradle, or Maven access.

| Area | Status |
|---|---|
| `./gradlew clean` / `test` / `assembleDebug` | **Not run.** Run via GitHub Actions or locally. |
| `./gradlew assembleRelease` | **Not run.** Needs your keystore. |
| APK existence, size, package id, minSdk, install | **Not verified.** No APK exists yet. |
| Unit tests (JVM) | **Written, not run:** ESC/POS bytes, imaging/dithering, formatting, text classification, barcode validation, page ranges, endpoint parsing, default-printer resolver, dispatcher (failure preserved, retry, no silent reroute, ask-every-time, copies). These use fakes (simulated). |
| Instrumented tests | **Written, not run:** Room round-trip, cascade delete, payload handling, package name. |
| Default-printer persistence via DataStore | Not tested (DataStore is exercised only in-app). |
| Light/dark theme, startup, PDF render, QR/barcode raster | **Not tested.** |
| Real printer hardware (Bluetooth / USB / Wi-Fi) | **Not tested.** No hardware was available. |

Fill in this table with real results after your first CI run and hardware tests.
