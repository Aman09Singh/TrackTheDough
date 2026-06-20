# Tech Stack — Decisions & Reasoning

Every choice below is driven by three constraints:
1. `minSdk 32` — Android 12+, so no legacy shims needed.
2. **Local-first, no server** — all data lives on device.
3. **Kotlin-idiomatic** — prefer Kotlin-native libs over Java ports.

---

## Architecture

### MVVM + Clean Architecture
**Chosen over:** plain MVVM, MVC, MVI

MVI adds a unidirectional reducer which is powerful for complex state machines
but introduces boilerplate that isn't justified here. The state for each screen
is a single `UiState` sealed interface — MVVM handles this cleanly.

Clean Architecture is chosen because the SMS parser and categorization logic
are non-trivial, need to be unit-tested without Android, and will grow over time.
Strict layer separation pays off within the first sprint.

---

## UI

### Jetpack Compose + Material 3
**Already configured.** Compose is the only sensible choice for a new Android
project in 2024+. Material 3 (not Material 2) because `minSdk 32` means all
target devices run Android 12+ which has native Material You dynamic color support.

### Compose Navigation (`androidx.navigation:navigation-compose`)
**Chosen over:** Fragments + NavComponent, Decompose, Voyager

Official Jetpack library. Type-safe routes with Kotlin serialization (Navigation 2.8+).
No reason to use a third-party nav library when the official one is Compose-native.

### Vico Charts (`com.patrykandpatrick.vico:compose-m3`)
**Chosen over:** MPAndroidChart, custom Canvas drawing

The analytics screen needs bar/pie charts for spending by category.
MPAndroidChart is a View-based library — wrapping it in `AndroidView` works
but is a second-class Compose citizen. Vico is built on Compose Canvas with
Material 3 theming support. Custom Canvas drawing is too much scope for
what is essentially a commodity need.

---

## Dependency Injection

### Hilt (`com.google.dagger:hilt-android`)
**Chosen over:** Koin, manual DI

Hilt is the Google-recommended DI framework for Android. It integrates directly
with `ViewModel` (`@HiltViewModel`), `WorkManager`, and `BroadcastReceiver`
(via `@AndroidEntryPoint`). This matters because `SmsBroadcastReceiver`
needs injected dependencies — Hilt handles this with `@AndroidEntryPoint`,
whereas Koin requires manual service locator calls inside the receiver.

---

## Async / Reactive

### Kotlin Coroutines + Flow
**Chosen over:** RxJava, LiveData

Flow is the idiomatic Kotlin reactive stream. `Room` natively returns `Flow<List<T>>`
from `@Dao` queries — this means the UI automatically re-renders when the database
changes (new SMS inserted). No polling needed, no manual observer wiring.

`StateFlow` in `ViewModel` replaces `LiveData`. `collectAsStateWithLifecycle()`
in Compose is lifecycle-aware and replaces `LiveData.observe()`.

---

## Local Storage

### Room (`androidx.room`)
**Chosen over:** SQLite directly, Realm, ObjectBox

Room is the standard Android ORM. Direct SQLite is too verbose. Realm and ObjectBox
are third-party with their own query languages. Room gives us:
- `Flow<List<TransactionEntity>>` directly from `@Dao`
- Compile-time SQL validation
- `GROUP BY` / `SUM` aggregate queries for the analytics screen
- Seamless Hilt integration

### DataStore Preferences (`androidx.datastore:datastore-preferences`)
**Chosen over:** SharedPreferences

For storing user preferences (e.g., default date range, onboarding state).
DataStore is the modern replacement for SharedPreferences — it's non-blocking
(coroutines-based) and avoids the ANR risk of SharedPreferences on the main thread.
We only need key-value storage, so Preferences DataStore (not Proto DataStore) is sufficient.

---

## SMS Parsing

### Custom Regex Parser (no third-party library)
**Chosen over:** ML-based extraction, third-party SMS parse libs

Bank SMS formats are finite and well-structured. A set of 10–20 regex patterns
covers the vast majority of Indian bank formats (HDFC, SBI, ICICI, Axis, Kotak, UPI).
ML-based extraction is overkill, adds model size to the APK, and is harder to debug
when a new bank format appears. Regex patterns are readable, testable, and trivially
extensible.

The parser lives in the `data` layer and has zero Android dependencies, making it
trivially unit-testable with plain JUnit.

---

## Networking

### Retrofit 2 + OkHttp 4 + Kotlinx Serialization
**Status: deferred to Phase 3+**

Not needed for MVP — all data is local. Included in the stack for future use cases:
- Exchange rate API
- Cloud backup / sync
- Bank statement import

When added, Kotlinx Serialization is preferred over Gson because it is Kotlin-native,
works with `data class` without reflection, and is compatible with Kotlin 2.x.

---

## Image Loading

### Coil 3 (`io.coil-kt.coil3:coil-compose`)
**Status: deferred — no images in MVP**

Included in the planned stack for future use: bank logos, category icons from network.
Coil 3 is Kotlin Multiplatform-ready and Compose-native. Not added to `build.gradle.kts`
until actually needed.

---

## Testing

### JUnit 4
The existing instrumentation runner (`AndroidJUnitRunner`) targets JUnit 4.
Upgrading to JUnit 5 on Android requires additional setup (no first-class support).
JUnit 4 is sufficient.

### MockK (`io.mockk:mockk`)
**Chosen over:** Mockito

MockK is Kotlin-native — it mocks `object`s, `companion object`s, extension functions,
and coroutine `suspend` functions without the `open` keyword requirement that
Mockito demands on Kotlin classes (which are `final` by default).

### Turbine (`app.cash.turbine:turbine`)
Flow testing without Turbine requires manual coroutine test harnesses. Turbine provides
`flow.test { awaitItem() }` — concise and readable. Critical for testing `ViewModel`
state updates driven by Room `Flow` emissions.

### Truth (`com.google.truth:truth`)
More readable assertions than JUnit's `assertEquals`. `assertThat(result).isEqualTo(...)`.
Especially clear for collection assertions: `containsExactly`, `containsInOrder`.

### Compose UI Testing
`androidx.compose.ui:ui-test-junit4` — official Compose instrumented test library.
Used for screen-level tests: "given these transactions in DB, does HomeScreen show
the correct total?". Not used for every composable — only integration-level smoke tests.

---

## Build

### Gradle Version Catalog (`gradle/libs.versions.toml`)
All dependency versions are centralized here. Do not hardcode version strings in
`build.gradle.kts` files. This prevents version conflicts across modules as the
project grows.

### Kotlin 2.x + Compose Compiler Plugin
The `kotlin.compose` plugin (alias `libs.plugins.kotlin.compose`) replaces the
old `kotlinCompilerExtensionVersion` property. It automatically aligns the
Compose compiler with the Kotlin version — no manual version string management.
