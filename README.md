# Auto SMS Messenger 📱

An Android app that automatically sends custom SMS messages at a user-defined time, repeating daily. Built using Kotlin with Android Studio and WorkManager.

## 🚀 Features

- Schedule SMS to be sent at any chosen time
- Automatically repeats daily
- **NEW: AI-Generated Messages using ChatGPT API**
- Modern WorkManager-based scheduling
- Persists across device reboots
- Contact picker integration
- Material Design 3 UI
- Built using Kotlin DSL (`build.gradle.kts`)

## 🤖 AI Message Generation

The app now supports AI-generated messages using OpenAI's ChatGPT API:

- **Message Styles**: Friendly, Professional, Funny, Romantic
- **Context-Aware**: Provide context for more personalized messages
- **Dynamic Generation**: Each SMS gets a unique AI-generated message
- **Fallback Support**: Falls back to custom messages if API is unavailable
- **Easy Setup**: Configure your OpenAI API key in Settings

### How to Use AI Messages:

1. Go to **Settings** → Configure your OpenAI API key
2. Create a new SMS schedule
3. Select **"AI Generated"** message type
4. Choose your preferred message style
5. Optionally provide context for personalization
6. The app will generate a unique message each time the SMS is sent

## 🛠 Tech Stack

- Kotlin
- WorkManager
- Room Database
- ViewModel & LiveData
- RecyclerView with DiffUtil
- Material Design 3
- ViewBinding
- **Retrofit & OkHttp** (for ChatGPT API)
- **OpenAI GPT-3.5-turbo** (for message generation)

## ⚠️ Permissions Required

- `SEND_SMS`
- `POST_NOTIFICATIONS` (Android 13+)
- `RECEIVE_BOOT_COMPLETED` (optional if reboot rescheduling is added)
- `INTERNET` (for ChatGPT API calls)

## 🧪 Testing

1. Run on a real device (emulators cannot send SMS).
2. Select time via TimePicker
3. Enter phone number & message
4. Tap "Schedule"
5. Wait for SMS and notification

## 📦 Build Instructions

1. Open in Android Studio
2. Allow all permissions
3. Connect physical Android device
4. Run the app

## 🛡 Disclaimer

This app sends SMS messages. Be mindful of carrier fees, user privacy, and abuse prevention.

## 📝 License

MIT License
