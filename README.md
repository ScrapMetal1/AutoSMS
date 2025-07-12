# Auto SMS Messenger 📱

An Android app that automatically sends custom SMS messages at a user-defined time, repeating daily. Built using Kotlin with Android Studio and WorkManager.

## 🚀 Features

- Schedule SMS to be sent at any chosen time
- Automatically repeats daily
- Modern WorkManager-based scheduling
- Persists across device reboots
- Contact picker integration
- Material Design 3 UI
- Built using Kotlin DSL (`build.gradle.kts`)

## 🛠 Tech Stack

- Kotlin
- WorkManager
- Room Database
- ViewModel & LiveData
- RecyclerView with DiffUtil
- Material Design 3
- ViewBinding

## ⚠️ Permissions Required

- `SEND_SMS`
- `POST_NOTIFICATIONS` (Android 13+)
- `RECEIVE_BOOT_COMPLETED` (optional if reboot rescheduling is added)

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
