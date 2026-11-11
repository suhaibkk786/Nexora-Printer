# Nexora Printer

Android thermal-printer app (RawBT-style) built with Kotlin, Jetpack Compose (Material 3), MVVM + Clean Architecture, Hilt, Room, DataStore and an independent ESC/POS engine.

- **Application ID:** `nexora.printer` · **Version:** 1.0.0 (1) · **minSdk** 26 · **compile/targetSdk** 34 · **JDK** 17
- **Transports:** Bluetooth Classic (SPP), USB Host (bulk OUT), Wi-Fi/LAN (raw TCP, port 9100)
- **Paper:** 58 mm (384 dots) and 80 mm (576 dots), configurable per printer

> **Status: source complete, not yet compiled.** The project was authored in an environment with no Android SDK or network access to Gradle/Maven, so it has never been built or run on a device. Expect to fix a few compile errors on the first CI run. See `docs/TEST_REPORT.md` for exactly what is and is not verified.

## Build

CI (recommended): push to GitHub. `.github/workflows/android-build.yml` runs unit tests and `assembleDebug`, and uploads `Nexora-Printer-v1.0.0-debug.apk` as a workflow artifact (Actions → run → Artifacts).

Local:

```bash
# one-time: generate the Gradle wrapper (needs Gradle 8.9+ installed, or let Android Studio do it)
gradle wrapper --gradle-version 8.9
./gradlew clean test assembleDebug
# APK: app/build/outputs/apk/debug/Nexora-Printer-v1.0.0-debug.apk
adb install -r app/build/outputs/apk/debug/Nexora-Printer-v1.0.0-debug.apk
```

Instrumented tests (device/emulator): `./gradlew connectedDebugAndroidTest`.

### Signed release APK

Nothing secret is committed. Create a keystore once:

```bash
keytool -genkeypair -v -keystore nexora-release.jks -alias nexora -keyalg RSA -keysize 2048 -validity 10000
```

Local build:

```bash
export NEXORA_KEYSTORE_PATH=/abs/path/nexora-release.jks NEXORA_KEYSTORE_PASSWORD=... NEXORA_KEY_ALIAS=nexora NEXORA_KEY_PASSWORD=...
./gradlew assembleRelease
```

GitHub: add secrets `NEXORA_KEYSTORE_BASE64` (`base64 -w0 nexora-release.jks`), `NEXORA_KEYSTORE_PASSWORD`, `NEXORA_KEY_ALIAS`, `NEXORA_KEY_PASSWORD`, then run the **Android Release (manual)** workflow.

## Using it

1. **Printers → Add printer.** Bluetooth: pair in Android settings first (or scan), pick the device. USB: connect with an OTG cable, grant permission. Wi-Fi: enter IP and port (9100), test, save.
2. First printer saved becomes the default. Change it on the Dashboard or in Settings.
3. **Print** tab: text, image, PDF, QR, barcode, each with a live preview, paper width, copies and printer selection.
4. **Share** text, images or PDFs to Nexora Printer from any app. The system print dialog also lists saved printers (Android PrintService).
5. **Diagnostics** runs connection, ESC/POS, receipt, QR, barcode and image tests.

## Printing from a point-of-sale (Nexora POS, or any web POS)

Nexora Printer can take the place of RawBT. A POS page opens a link `rawbt:base64,<ESC/POS bytes>` (or `nexoraprint:base64,<...>`); the app decodes it, sends the bytes unchanged to the **default printer** and closes by itself, showing only a short "Printed on ..." message. Nothing is shown on screen and no POS change is needed.

1. Install the app, add the printer (Printers -> Add printer) and make it the default.
2. In the POS, make the printer of type **Android** (Nexora POS: Devices -> Printers).
3. The first time a ticket is sent, Android asks which app opens the link: choose **Nexora Printer** and tap **Always**. If RawBT is also installed, uninstall it or clear its "open by default" setting.

Jobs from the POS appear in History as type "POS ticket". With the mode "Ask every time", POS tickets still go to the default printer (or the first saved one), because nobody is at the screen to pick.

## Default printer modes

| Mode | Behaviour |
|---|---|
| Always use default | Prints to the default printer. |
| Last used | Prints to the printer last used successfully. |
| Ask every time | Shows a picker before each job. |
| Connection priority | Picks by preferred connection type order (USB/Wi-Fi/Bluetooth, reorderable). |

If the chosen printer cannot be reached, the job is **kept**, a clear error is shown with *Reconnect & retry* and *Other printer*, and **nothing is silently sent elsewhere**. A printer is shown "Connected" only after a real connection succeeded.

## Architecture

```
app/src/main/java/nexora/printer/
  core/        escpos (pure Kotlin engine) · bluetooth · usb · wifi · printer (manager, renderer) · utils
  data/        Room database, DataStore, repositories
  domain/      models, repository interfaces, PrinterResolver, PrintDispatcher
  presentation/ dashboard · printers · print · preview (shared components) · history · settings · diagnostics · navigation · theme
  service/     Android PrintService
  di/          Hilt modules
```

Notes on the spec's layout: the separate "Print preview" screen is implemented as live previews inside each Print tab (`presentation/preview` holds shared preview/option components).

## Multilingual printing

Text is encoded with the printer profile's charset and sent with ESC t code page. Lines containing Arabic/Hebrew, or any character the charset cannot encode, are rendered to a bitmap with Android's text engine (shaping, RTL, mixed-direction ordering) and printed as raster. UTF-8 is never assumed. Urdu (Nastaliq) depends on the device having a suitable font.

See `docs/FEATURES.md` and `docs/TEST_REPORT.md`.

## License

MIT

Developed by Nexora IT Technologies · https://www.nexoraittechnologies.com
