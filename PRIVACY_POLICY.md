# Privacy Policy for mpvGo

**Effective Date:** September 27, 2026  
**Application Name:** mpvGo  
**Package Name:** `io.github.arju22.mpvgo`  
**Hosted GitHub Pages Version:** [https://arju22.github.io/mpvGo/privacy-policy.html](https://arju22.github.io/mpvGo/privacy-policy.html)  
**License:** Apache License 2.0  

---

## 🔒 Summary (TL;DR)

- **Zero Data Collection:** We do not collect, transmit, store, sell, or share any personal information.
- **No Ads & No Analytics:** The application contains zero advertising SDKs, zero analytics tracking (no Google/Firebase Analytics), and zero telemetry.
- **Local Processing:** All media discovery, playback history, thumbnails, and settings remain 100% on your device.
- **100% Open Source:** mpvGo is free and open-source software. You can inspect the source code anytime on [GitHub](https://github.com/arju22/mpvGo).

---

## 1. Introduction

mpvGo ("the Application", "we", "us", or "our") is an open-source media player for Android built upon the `libmpv` playback engine. We are committed to maintaining the highest privacy standards. This Privacy Policy describes how mpvGo handles information on Android devices.

Because mpvGo operates strictly as a local, client-side media player utility, we do not operate user accounts, remote user databases, or data tracking services.

---

## 2. Information We Do NOT Collect

We do not collect or monitor any personal or device data:

- **Personal Information:** No names, email addresses, phone numbers, or physical addresses.
- **Device Identifiers:** No IMEI, Android ID, MAC address, advertising ID, or hardware fingerprints.
- **Usage Telemetry:** No analytics libraries, event trackers, or usage heatmaps.
- **Media Content:** We never upload, inspect, or transfer your video files, audio files, photos, or documents to external servers.

---

## 3. Android Permissions & Usage

To function properly as a media player, mpvGo requests the following Android permissions. Each permission is used strictly for on-device functionality:

| Permission | Category | Purpose |
| :--- | :--- | :--- |
| `READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE` | Storage | Allows the app to scan, browse, display thumbnails, read media tags (via MediaInfo), and play local audio/video files. |
| `MANAGE_EXTERNAL_STORAGE` *(Standard flavor only)* | All Files Access | Enables advanced file management (move, rename, delete files, subtitle loading across arbitrary directories, USB OTG drives). *Not present in Google Play Scoped Storage builds.* |
| `INTERNET` | Network | Required exclusively to play user-requested network streams (via "Open URL", HTTP/HLS/DASH/RTSP), network shares (SMB/WebDAV/FTP), and check for GitHub release updates (standard build only). |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Background Playback | Allows uninterrupted audio and video playback when navigating outside the app, using Picture-in-Picture (PiP), or turning off the screen. |
| `POST_NOTIFICATIONS` | Notifications | Displays ongoing playback controls (play, pause, seek, track info) on Android 13+. |
| `REQUEST_INSTALL_PACKAGES` *(Standard flavor only)* | Updates | Allows self-updating the APK when a user initiates an update from GitHub releases. Not used for third-party APKs. |

You may grant or revoke permissions at any time via Android System Settings (**Settings > Apps > mpvGo > Permissions**).

---

## 4. Local Data Storage & User Data Control

All app-related data is stored locally on your device:

- **Playback History & Positions:** Saved in a local SQLite/Room database on your device to let you resume playback where you left off.
- **App Preferences:** Playback speed, gestures, subtitle preferences, and mpv configuration scripts are saved in Android DataStore/SharedPreferences.
- **Data Deletion:** You can clear your playback history at any time in the app settings, or clear all application data via Android System Settings (**Settings > Apps > mpvGo > Storage > Clear Data**). Uninstalling the app permanently deletes all local data.

---

## 5. Network Streaming & Third-Party Services

When you stream content from an external URL (HTTP/HTTPS/HLS/RTSP/RTMP) or network share (SMB, FTP, WebDAV):
- mpvGo establishes a direct network connection from your device to the specified server.
- No intermediary proxy or logging servers are operated by mpvGo.
- The connection is governed by the privacy policy of the respective streaming host.
- Any network credentials entered are stored encrypted/locally on your device and sent only to the host you specified.

---

## 6. Crash Reporting

mpvGo provides an on-device crash dialog (`CrashActivity`) for diagnosing unexpected crashes. Crash logs are **never** transmitted automatically. If you choose to submit a bug report to our GitHub Issues page, you do so manually and can review or sanitize the log contents before submitting.

---

## 7. Children's Privacy

mpvGo does not collect personal information from any user, including children under the age of 13. It complies with COPPA and GDPR regulations.

---

## 8. Open Source & Transparency

mpvGo is licensed under the **Apache License 2.0**. Our complete source code is public and open for auditing:
- Repository: [https://github.com/arju22/mpvGo](https://github.com/arju22/mpvGo)

---

## 9. Contact Us

If you have any questions or feedback regarding this Privacy Policy:
- GitHub Issues: [https://github.com/arju22/mpvGo/issues](https://github.com/arju22/mpvGo/issues)
- GitHub Project: [https://github.com/arju22/mpvGo](https://github.com/arju22/mpvGo)
