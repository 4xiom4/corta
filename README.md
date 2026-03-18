# Corta App 🛡️

**Corta** is a privacy-focused Call Screening and SMS Filtering application for Android, built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. It helps users reclaim their peace of mind by automatically identifying and blocking spam calls and messages.

## Key Features

- 🛡️ **Call Screening**: Block spam and telemarketing calls before your phone even rings.
- 📱 **SMS Filtering**: Automatically filter out unwanted text messages and spam.
- 📞 **Default Dialer Support**: Use Corta as your primary phone application for a unified and clean experience.
- 💬 **Default SMS Support**: Management of all your text messages with built-in spam protection.
- 🔒 **Privacy First**: Designed with privacy in mind, processing data locally whenever possible.
- 🇨🇱 **Subtel Integration**: Leverages official data to provide accurate identification of telecommunications services.

## Technical Stack

- **Language**: Kotlin
- **Framework**: Kotlin Multiplatform (KMP)
- **UI**: Compose Multiplatform / Jetpack Compose
- **Database**: SQLDelight (Local caching and spam database)
- **Architecture**: Clean Architecture with Domain-Driven Design (DDD) principles.

## Getting Started

### Prerequisites

- Android Studio Koala or newer.
- JDK 17+.
- Android SDK 24+ (Android 7.0+).

### Build & Run

1. Clone the repository:
   ```bash
   git clone https://github.com/4xiom4/corta.git
   ```
2. Open the project in Android Studio.
3. Wait for Gradle sync to complete.
4. Run the `composeApp` module on an Android device or emulator.

## Project Structure

- `composeApp/`: Contains the Android-specific UI and application logic.
- `shared/`: Multiplatform module containing the core business logic, domain models, and data repositories.
  - `commonMain`: Shared code across platforms.
  - `androidMain`: Android-specific implementations of shared interfaces.

## Permissions

Corta requires several permissions to provide its core functionality:
- `READ_PHONE_STATE` & `CALL_SCREENING`: For identifying and blocking incoming calls.
- `READ_SMS` & `RECEIVE_SMS`: For filtering spam messages.
- `READ_CALL_LOG` & `WRITE_CALL_LOG`: To manage and display your call history.

---

Made with ❤️ by [4xiom4](https://github.com/4xiom4)
