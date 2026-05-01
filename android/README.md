# Intuiti — Card Scanner (Android)

A native Android app that scans business cards, extracts the contact info, and
hands it to the system Contacts editor for review and save.

Built on the modern Android playbook:

- **Kotlin 2.1** with the Compose Compiler plugin
- **Jetpack Compose + Material 3** with Material You dynamic color (Android 12+)
- Edge-to-edge by default (`enableEdgeToEdge`) with proper system-bar insets
- Single-Activity architecture, `ViewModel` + `StateFlow`
- **Jetpack DataStore** for the API key
- **ML Kit Text Recognition** for on-device OCR
- **OkHttp + kotlinx.serialization** for the Claude API path
- `ActivityResultContracts.TakePicture` (FileProvider URI) and
  `PickVisualMedia` for image input — no custom camera, no `WRITE_EXTERNAL_STORAGE`
- `ContactsContract.Intents.Insert` to hand off to the system Contacts editor —
  the app never writes to the contacts provider directly

## Modes

| Mode      | When                                  | Engine                          | Privacy                                        |
|-----------|---------------------------------------|---------------------------------|------------------------------------------------|
| AI        | When an Anthropic API key is saved    | Claude (`claude-opus-4-7`) vision + JSON-schema response | Image is sent to the Anthropic API |
| On-device | No key, or the AI call fails          | Google ML Kit + heuristic parser | Image stays on the device                      |

If the AI call fails (bad key, rate limit, network), the app automatically
falls back to ML Kit for that scan and labels the result as a fallback.

## Build & run

This is a standard Gradle project. The fastest way to bootstrap:

1. Open the `android/` folder in **Android Studio** (Hedgehog or newer). It
   regenerates the `gradlew` wrapper script + jar on first sync.
2. Connect a physical device (API 26 / Android 8.0+) or start an emulator.
3. Run the **app** configuration.

Or from a shell with Gradle 8.10+ installed:

```bash
cd android
gradle wrapper        # one-time, generates ./gradlew + gradle-wrapper.jar
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

## Adding an API key

In the running app, tap the gear icon, paste a key from
[console.anthropic.com](https://console.anthropic.com/), and tap **Save key**.
The key is stored in DataStore (encrypted at rest by the OS, scoped to the
app's private storage). Tap **Clear key** to drop back to on-device mode.

## Project layout

```
android/
├── settings.gradle.kts
├── build.gradle.kts                 ← root project
├── gradle/
│   ├── libs.versions.toml           ← Gradle version catalog
│   └── wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/                     ← strings, themes, adaptive launcher icon
        └── java/com/intuiti/cardscanner/
            ├── CardScannerApplication.kt
            ├── MainActivity.kt
            ├── data/
            │   ├── ContactFields.kt        ← model + ExtractionResult/Source
            │   ├── SettingsRepository.kt   ← DataStore wrapper
            │   ├── CardExtractor.kt        ← chooses Claude or ML Kit
            │   ├── ClaudeExtractor.kt      ← OkHttp call + JSON-schema response
            │   └── MlKitExtractor.kt       ← ML Kit + regex/heuristic parser
            ├── ui/
            │   ├── theme/Theme.kt          ← Material 3 + dynamic color
            │   ├── ScannerViewModel.kt     ← StateFlow, phases
            │   └── CardScannerApp.kt       ← Compose UI (capture, review, settings)
            └── util/
                ├── ImageUtils.kt           ← EXIF rotation + downsample to JPEG
                └── ContactIntent.kt        ← builds the Insert-Contact intent
```

## Permissions

- `INTERNET` — only used when AI mode is enabled.
- `CAMERA` — requested at runtime when the user taps **Take a photo**. Declined?
  The app shows a snackbar and the user can still pick from the gallery.

The app does **not** request `READ_CONTACTS`, `WRITE_CONTACTS`, or storage
permissions: handing the contact off to the system editor avoids needing them.

## Bumping versions

Versions live in `gradle/libs.versions.toml`. Bump the relevant `[versions]`
entry; Gradle's version catalog will propagate it across both Gradle files.
