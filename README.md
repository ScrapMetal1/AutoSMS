# AutoSMS

AutoSMS is a lightweight, reliable Android application that allows users to schedule automated text messages. Whether you need to send birthday wishes, daily reminders, or periodic updates, AutoSMS handles it for you efficiently.

## Features

- 📅 **Schedule SMS**: Plan your messages in advance.
- 🔄 **Flexible Frequencies**:
  - One-time
  - Hourly
  - Daily
  - Weekly
  - Monthly
  - Custom Intervals (e.g., every 3 days)
- 👥 **Contact Integration**: Select recipients directly from your phone's contacts.
- 🚀 **Boot Persistence**: Schedules are automatically re-registered if your device restarts.
- 📱 **Modern UI**: Clean and intuitive Material Design interface.
- 🔋 **Battery Efficient**: Uses Android's native AlarmManager using exact alarms for timely delivery without draining your battery.

## Installation (APK)

1. **Download**: Get the latest APK from the [Releases](https://github.com/ScrapMetal1/AutoSMS/releases) page or build it yourself.
2. **Install**: Open the APK file on your Android device. You may need to enable "Install unknown apps" in your settings.
3. **Permissions**: improved reliability, the app requires the following permissions:
   - **Main Permissions**: Send SMS, Read Contacts.
   - **Optimization**: For best performance, allow the app to ignore battery optimizations so that scheduled messages are not delayed by the system's power saving mode.

## Building from Source

To build this project locally, you will need:
- [Android Studio](https://developer.android.com/studio) (Koala or newer recommended)
- JDK 17 or higher

### Steps

1. **Clone the Repository**
   ```bash
   git clone https://github.com/ScrapMetal1/AutoSMS.git
   ```

2. **Open in Android Studio**
   - Launch Android Studio.
   - Select **Open** and navigate to the cloned `AutoSMS` folder.

3. **Sync Gradle**
   - Wait for Android Studio to download dependencies and sync the project.

4. **Run the App**
   - Connect your Android device (Enable USB Debugging) or start an Emulator.
   - Click the green **Run** button (▶) in the toolbar.

## Usage

1. **Create a Schedule**: Tap the Floating Action Button (+) on the home screen.
2. **Select Recipient**: Pick a contact from your list or manually enter a phone number.
3. **Compose Message**: Type the content of your SMS.
4. **Set Time & Frequency**: Choose when the message should be sent and how often it should repeat.
5. **Save**: Tap "Save". The schedule will now appear on your home dashboard.

> **Note**: This app sends *actual* SMS messages via your carrier. Standard text messaging rates may apply depending on your mobile plan.

## Permissions Explained

- `SEND_SMS`: Required to send the automated messages.
- `READ_CONTACTS`: Allows you to pick a recipient from your address book.
- `RECEIVE_BOOT_COMPLETED`: Ensures your scheduled messages are re-activated after you restart your phone.
- `SCHEDULE_EXACT_ALARM`: Ensures precise delivery at the exact scheduled time.

## License

Copyright © 2025 Josh Zachariah. All rights reserved.
Unauthorized copying or distribution of this software is strictly prohibited.
