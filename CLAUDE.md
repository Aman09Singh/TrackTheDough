# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

**SMS Finance Tracker** — an Android app that reads bank transaction SMS messages,
parses them automatically, categorizes spending, and lets users see where their
money is going across time ranges.

Single-module, local-first (no backend). All data lives in Room on-device.
Package `com.example.t1`, `minSdk 32`, `targetSdk`/`compileSdk 36`, JDK 11.

### Design docs (read before implementing)
- [`docs/01-architecture.md`](docs/01-architecture.md) — layers, data flow diagrams, DB schema, SMS parsing strategy
- [`docs/02-tech-stack.md`](docs/02-tech-stack.md) — every library choice with reasoning
- [`docs/03-implementation-plan.md`](docs/03-implementation-plan.md) — 5 phased tasks + testing plan

## Commands

Use the Gradle wrapper (`./gradlew` on POSIX, `gradlew.bat` on Windows/PowerShell — this is a Windows machine).

- Build debug APK: `./gradlew assembleDebug`
- Full build + checks: `./gradlew build`
- Install on connected device/emulator: `./gradlew installDebug`
- Unit tests (JVM, `app/src/test`): `./gradlew test`
- Single unit test: `./gradlew test --tests "com.example.t1.ExampleUnitTest"` (append `.methodName` for one method)
- Instrumented tests (require a device/emulator, `app/src/androidTest`): `./gradlew connectedAndroidTest`
- Clean: `./gradlew clean`

There is no standalone lint/format step configured beyond Android's built-in `lint` task (`./gradlew lint`).

## Architecture & conventions

- **Dependencies are managed through the Gradle version catalog** at `gradle/libs.versions.toml`, not hardcoded in build files. Add or bump versions there (under `[versions]`/`[libraries]`/`[plugins]`) and reference them as `libs.*` in `app/build.gradle.kts`. Do not inline version strings in build scripts.
- **Compose BOM** (`androidx-compose-bom`) controls all Compose library versions — individual Compose artifacts are declared without versions and inherit from the BOM. Bump the BOM rather than individual Compose libs.
- Build scripts are Kotlin DSL (`.kts`).
- UI theming lives in `app/src/main/java/com/example/t1/ui/theme/` (`Theme.kt`, `Color.kt`, `Type.kt`); the app theme composable is `T1Theme`.
- `local.properties` (holds `sdk.dir`) is machine-specific and git-ignored — do not edit or commit it.

## Tech Stack

### Architecture
- **Pattern:** MVVM + Clean Architecture (UI → Domain → Data layers)
- **State management:** `StateFlow` / `SharedFlow` in `ViewModel`; Compose `collectAsStateWithLifecycle()` at the UI layer
- **Navigation:** Jetpack Compose Navigation (`androidx.navigation:navigation-compose`)

### Dependency Injection
- **Hilt** (`com.google.dagger:hilt-android`) — official Google DI, integrates with `ViewModel` via `@HiltViewModel`

### Networking
- **Retrofit 2** + **OkHttp 4** for HTTP; **Kotlinx Serialization** (`kotlinx-serialization-json`) as the converter (not Gson/Moshi)
- Add `@Serializable` to all data-transfer objects

### Async / Reactive
- **Kotlin Coroutines** + **Kotlin Flow** — use `viewModelScope` for ViewModel-scoped coroutines; `flow {}` builders in the data layer
- No RxJava

### Local Storage
- **Room** (`androidx.room`) for structured data; `@Database`, `@Dao`, `@Entity` pattern
- **DataStore Preferences** (`androidx.datastore:datastore-preferences`) for key-value settings

### Image Loading
- **Coil 3** (`io.coil-kt.coil3:coil-compose`) — Kotlin-first, Compose-native

### Testing
- **JUnit 4** (unit tests), **MockK** for mocking Kotlin code, **Turbine** for `Flow` assertions
- **Compose UI Testing** (`androidx.compose.ui:ui-test-junit4`) for instrumented tests
- **Truth** (`com.google.truth:truth`) for assertions

### Code Style
- Kotlin official style (`kotlin.code.style=official` already set in `gradle.properties`)
- Target Kotlin 2.x idioms: data classes, sealed interfaces for UI state, `when` expressions, extension functions

## Role
You are an expert Android Development Architect and Developer specializing in Kotlin and Android Application Development.

## Core User Flows

1. **Passive capture:** User makes a payment → bank sends SMS → `SmsBroadcastReceiver` intercepts → parsed + auto-categorized → stored in Room.
2. **Home:** Total debited/credited summary + recent transaction list.
3. **Transaction list:** Filterable by account, category, type (debit/credit).
4. **Manual categorize:** Tap a transaction → pick category → persisted with `isManualCategory = true` (never overwritten by re-parse).
5. **Analytics:** Pick a date range → see spending by category (bar chart) + monthly trend (line chart).
