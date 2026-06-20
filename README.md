# TrackTheDough

TrackTheDough is an Android app that automatically reads bank transaction SMS messages, parses them, and helps you understand your spending — no manual entry required.

## Use Case

When you make a payment, your bank sends an SMS. TrackTheDough intercepts it in the background, extracts the amount, merchant, and account details, auto-categorizes the transaction, and stores everything locally on your device. You get a clean view of where your money is going without typing a single thing.

**Core flows:**
- **Passive capture** — SMS arrives → parsed automatically → saved to local DB
- **Home screen** — total debited/credited summary + recent transactions
- **Transaction list** — filter by account, category, or type (debit/credit)
- **Manual categorization** — tap any transaction to reassign its category
- **Analytics** — pick a date range, see spending by category (bar chart) + monthly trend (line chart)

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Jetpack Compose Navigation |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| Local DB | Room |
| Preferences | DataStore Preferences |
| Charts | Vico (`compose-m3`) |
| SMS Parsing | Custom Regex Parser |
| Testing | JUnit 4, MockK, Turbine, Truth, Compose UI Testing |
| Build | Gradle Version Catalog, Kotlin 2.x |

All data is stored on-device — there is no backend or network dependency for core functionality.

---

## Setting Up Dev

**Prerequisites**
- Android Studio Hedgehog or newer
- JDK 11
- Android SDK with API level 32+

**Steps**

1. Clone the repository:
   ```bash
   git clone <repo-url>
   cd T1
   ```

2. Open the project in Android Studio. It will sync Gradle automatically.

3. `local.properties` is machine-specific and git-ignored. Android Studio generates it automatically — do not create or edit it manually.

4. Connect a physical device or start an emulator (API 32+).

5. Build and install:
   ```powershell
   .\gradlew.bat installDebug
   ```

6. On the device, grant the **SMS** permission when prompted.

> **Physical device note:** Developer options must be enabled and USB debugging must be on. The app intercepts live bank SMS messages, so a real device with an active SIM gives the full experience; an emulator works for UI development only.

---

## Tests

**Unit tests** (JVM, no device needed):
```powershell
.\gradlew.bat test
```

Run a single test class:
```powershell
.\gradlew.bat test --tests "com.example.t1.SomeTest"
```

Run a single method:
```powershell
.\gradlew.bat test --tests "com.example.t1.SomeTest.methodName"
```

**Instrumented tests** (requires connected device or emulator):
```powershell
.\gradlew.bat connectedAndroidTest
```

**Full build + checks:**
```powershell
.\gradlew.bat build
```

**Lint:**
```powershell
.\gradlew.bat lint
```

---

## Built With

- [Jetpack Compose](https://developer.android.com/jetpack/compose) — modern declarative UI toolkit for Android
- [Hilt](https://dagger.dev/hilt/) — dependency injection for Android
- [Room](https://developer.android.com/training/data-storage/room) — SQLite abstraction with compile-time query validation
- [Kotlin Coroutines + Flow](https://kotlinlang.org/docs/coroutines-overview.html) — async and reactive streams
- [Vico](https://patrykandpatrick.com/vico/) — Compose-native charting library
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) — modern key-value storage
- [MockK](https://mockk.io/) — Kotlin-native mocking library
- [Turbine](https://github.com/cashapp/turbine) — Flow testing utility
- [Truth](https://truth.dev/) — fluent assertion library

---

## Authors

- **Aman Singh** — [amantrieseverything@gmail.com](mailto:aman09singh15@gmail.com)
