# WToDo 🐾

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/API-24%2B-blue)](https://android-arsenal.com/api?level=24)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

WToDo is an offline-first, Persian-friendly task management app for Android built with Jetpack Compose and Material 3. Designed for users who value simplicity, speed, and full control over their data.

---

## ✨ Features

- **Modern Material 3 UI**: 100% Jetpack Compose with dynamic color support and light/dark/system themes.
- **Task Management**:
  - Subtasks with hierarchical progress tracking.
  - Categories with colors and icons.
  - Priority levels (Low, Medium, High) with visual cues.
  - Due dates with local notifications (reminders via WorkManager).
- **Productivity & Search**:
  - Search-as-you-type with Persian text normalization (Arabic ye/kaf unification).
  - Filtering by status (All, Active, Completed, Overdue) and date (Today, Tomorrow, Upcoming).
  - Sorting by manual order (drag & drop), creation date, due date, or priority.
- **Recurrence Engine**: Daily, Weekly, Monthly, and Yearly repeating tasks with drift-free month-end and leap-year handling.
- **Calendar**: Monthly view with locale-aware week start and per-day task overview.
- **Statistics**: Real completion rate, 7-day trend, and category distribution — computed from your data.
- **Rich Media**: Attach documents and images to tasks.
- **Data Safety**: Versioned JSON export/import with transactional restore (never overwrites existing data) and full validation.
- **Persian / RTL**: Complete Persian localization (fa-IR) with proper RTL layout.

## 🛠 Build

Requirements: Android Studio Ladybug (2024.2.1+) or newer, JDK 17, Android SDK 35.

```bash
./gradlew assembleDebug        # debug APK
./gradlew test                 # unit tests
./gradlew lint                 # static analysis
./gradlew assembleRelease      # release APK (unsigned — sign with your own key)
```

> **Signing**: Release builds are unsigned by design. To publish, configure your signing key in `app/build.gradle.kts` or sign the APK with `apksigner` (never commit keys).

## 🧪 Tests

Unit tests cover the database migrations, backup/import round trips and rollback, recurrence engine (drift, leap years, month-end), task filtering boundaries, and deep-link parsing.

## 🗺 Roadmap

- [ ] Multi-device sync (cloud support)
- [ ] Home screen widget
- [ ] Biometric lock for specific categories
- [ ] Export to PDF/CSV

## 📄 License

MIT — see [LICENSE](LICENSE).

## 🙏 Credits

- Icons by [Google Material Symbols](https://fonts.google.com/icons)
- Charts by [Vico](https://github.com/patrykandpatrick/vico)
- Developed by Farzad.Sh
