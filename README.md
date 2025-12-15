# Nothing Voice Assistant

A voice recording assistant for the **Nothing Phone (2a)** that:
- **Activates via long-press power button** (VoiceInteractionService)
- **Records voice immediately** (even from lock screen)
- **Auto-uploads to Google Drive** (with offline queue)
- **Transcribes automatically** (Google Cloud Speech-to-Text)

Built for **Android 16 (API 36)** using the privileged VoiceInteractionService API.

---

## 📱 Features

| Feature | Description |
|---------|-------------|
| **Power Button Trigger** | Long-press power → instant recording |
| **Lock Screen Support** | Works without unlocking phone |
| **Google Drive Sync** | Auto-upload with offline queue |
| **Transcription** | Speech-to-text with Google Cloud |
| **Nothing Theme** | Dark UI matching Nothing OS |
| **Glyph Ready** | Placeholder for Glyph LED animation |

---

## 🚀 Quick Start

### 1. Clone & Open in Android Studio

```bash
git clone <repo-url>
cd nothing-voice-assistant
```

Open in Android Studio and let Gradle sync.

### 2. Google Cloud Setup (Required)

#### Enable APIs
1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a new project (or select existing)
3. Enable **Google Drive API**
4. Enable **Cloud Speech-to-Text API**

#### Create OAuth Client
1. Go to **APIs & Services → Credentials**
2. Click **Create Credentials → OAuth client ID**
3. Select **Android**
4. Enter package name: `com.nothing.voiceassistant`
5. Enter SHA-1 fingerprint (see below)

Get SHA-1 from debug keystore:
```bash
# Windows
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android

# Mac/Linux
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
```

#### Speech-to-Text Credentials (Optional)
If you want transcription:
1. Go to **IAM & Admin → Service Accounts**
2. Create a service account
3. Download JSON key
4. Rename to `speech_credentials.json`
5. Place in `app/src/main/res/raw/`

### 3. Build & Install

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Configure as Default Assistant

1. Open the app and sign in with Google
2. Go to **Settings → Apps → Default Apps**
3. Set **Digital Assistant** to **Voice Assistant**
4. Done! Long-press power to record.

---

## 📁 Project Structure

```
app/src/main/java/com/nothing/voiceassistant/
├── VoiceAssistantApp.kt           # Application class
├── NothingAssistantService.kt     # Main VoiceInteractionService
├── NothingAssistantSessionService.kt  # Session factory
├── NothingAssistantSession.kt     # Recording logic & UI
├── audio/
│   └── AudioRecorder.kt           # MediaRecorder wrapper
├── drive/
│   ├── DriveUploadManager.kt      # Google Drive API
│   └── UploadWorker.kt            # Background sync (WorkManager)
├── transcription/
│   └── SpeechToTextService.kt     # Google Cloud STT
├── data/
│   ├── Recording.kt               # Room entity
│   ├── RecordingDao.kt            # Database queries
│   └── RecordingDatabase.kt       # Room database
├── ui/
│   ├── SettingsActivity.kt        # Setup & sign-in
│   └── RecordingsActivity.kt      # View recordings
└── glyph/
    └── GlyphController.kt         # Glyph SDK placeholder
```

---

## 🔧 Configuration

### Change Language for Transcription

Edit `SpeechToTextService.kt`:
```kotlin
private const val LANGUAGE_CODE = "en-US"  // Change to your language
```

Supported languages: [Speech-to-Text Languages](https://cloud.google.com/speech-to-text/docs/languages)

### Adjust Recording Quality

Edit `AudioRecorder.kt`:
```kotlin
private const val AUDIO_SAMPLE_RATE = 44100  // Hz
private const val AUDIO_BIT_RATE = 128000    // bps
```

### Enable Glyph Interface

1. Apply at [Nothing Developer Programme](https://nothing.tech/developer)
2. Get API key
3. Uncomment in `AndroidManifest.xml`:
```xml
<uses-permission android:name="com.nothing.ketchum.permission.ENABLE" />
<meta-data android:name="NothingKey" android:value="YOUR_KEY" />
```
4. Implement actual SDK calls in `GlyphController.kt`

---

## 💰 Pricing

| Service | Free Tier | After Free |
|---------|-----------|------------|
| Google Drive | 15 GB | — |
| Speech-to-Text | 60 min/month | $0.016/min |

---

## 📋 Permissions

| Permission | Purpose |
|------------|---------|
| `RECORD_AUDIO` | Voice recording |
| `INTERNET` | Drive sync & transcription |
| `FOREGROUND_SERVICE_MICROPHONE` | Android 14+ mic access |
| `BIND_VOICE_INTERACTION` | Power button trigger |

---

## 🐛 Troubleshooting

### "Assistant not working after power press"
- Ensure app is set as default Digital Assistant
- Check if another assistant is intercepting

### "Upload failing"
- Verify Google Sign-In is complete
- Check internet connection
- View WorkManager status in app

### "Transcription empty"
- Verify `speech_credentials.json` is in `res/raw/`
- Check Google Cloud project billing is enabled

---

## 📄 License

MIT License - Feel free to modify and distribute.

---

## 🔗 References

- [VoiceInteractionService Docs](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)
- [Google Drive API](https://developers.google.com/drive/api/v3/about-sdk)
- [Speech-to-Text API](https://cloud.google.com/speech-to-text/docs)
- [Nothing Glyph SDK](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit)
