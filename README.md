# SOS Көмек

Android app for people facing harassment, threats, violence or other dangerous situations (Kazakhstan). The interface is available in Russian and Kazakh.

## How to get the APK

1. In Android Studio, choose **File → Open** and select the `SOSKomek` folder. Wait for Gradle Sync to finish.
2. Choose **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone and install it (allow installation from unknown sources).

You can also run it directly with the ▶ Run button on a connected phone or emulator.

Requirements: Android 8.0+ (minSdk 26). The build uses AGP 8.10, Kotlin 2.0.21 and Gradle 8.11.1.

## Features

- **SOS**: hold the button for 3 seconds (a circular countdown shows progress), pick a reason or press "Cancel". The app then gets your location (only with your consent) and sends an SMS to up to 3 trusted contacts. The SMS includes your name, the reason, time, coordinates and a map link. After that the app offers to call 102 or 103, chosen by reason; you confirm the call yourself. A help-status screen shows progress, and "I'm safe" closes the alarm.
- **Offline**: SMS goes through the SIM and needs no internet. The event is saved and synced by WorkManager once the network is back.
- **Protection against false alarms**: the 3-second hold, a confirmation dialog, a repeat warning within 5 minutes, and a block on duplicate sends within 30 seconds.
- **Profile**: name, phone (verified with an SMS code), photo, blood type, medical info and allergies, language and theme. Up to 3 trusted contacts, each with a relationship type and permission switches for SOS messages and location.
- **Incident map** (OpenStreetMap, no API key needed): colored markers with an approximate-area circle of about 400 m. Filters by category, distance and period. Anonymous reports that automatically strip personal data (phones, e-mail, links, names, addresses), pre-moderation, complaints, and anti-spam limits (at most 3 reports a day, at least 10 minutes apart, no duplicates).
- **Security**: personal and medical data plus SOS history are encrypted with AES-256-GCM using an Android Keystore key. Cloud backup is disabled. Only HTTPS is allowed (`network_security_config.xml`).
- SOS history can be deleted one event at a time or all at once. "Delete all my data" wipes everything.
- Privacy policy and terms of use in Russian and Kazakh, light and dark themes, and permission explanations in plain language.

## Server (optional)

The app works on its own with no server: SOS goes over SMS and the map uses local data. Out of the box the map shows **demo incidents**, which are labeled and can be turned off in Profile → Settings.

For a shared incident map and moderation, set your server address in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://api.example.kz\"")
```

The expected API is described in `data/RemoteApi.kt` (`POST /v1/sos-events`, `POST /v1/incidents`, `POST /v1/incidents/{id}/complaints`, `GET /v1/incidents`). Your name, phone number and medical data are never sent to the server.

## Before publishing on Google Play

- Replace the debug key in `buildTypes.release` with your own signing key.
- Google Play restricts the `SEND_SMS` and `CALL_PHONE` permissions: you need to fill in the Permissions Declaration form. The app already falls back without them: it opens the SMS app with the text filled in, and the phone dialer.
- For push notifications to contacts, connect Firebase Cloud Messaging to your server.
