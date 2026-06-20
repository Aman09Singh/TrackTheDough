# Implementation Plan

Organized into 5 phases. Each phase is independently shippable — earlier phases
produce a running app, later phases add features on top. Tasks within a phase
are ordered by dependency.

---

## Phase 1 — Project Foundation

**Goal:** Runnable app with correct package structure, DI wired, database created,
navigation skeleton. No business logic yet.

### Tasks

**1.1 — Add Hilt**
- Add `hilt-android` + `hilt-compiler` (kapt or KSP) to `libs.versions.toml` and `app/build.gradle.kts`
- Add `@HiltAndroidApp` to a new `T1Application` class
- Register `T1Application` in `AndroidManifest.xml`
- Add `hilt-android-gradle-plugin` to root `build.gradle.kts`

**1.2 — Add Room**
- Add `room-runtime`, `room-ktx`, `room-compiler` to version catalog
- Create `TransactionEntity.kt` in `data/db/` with all columns from schema
- Create `TransactionDao.kt` with placeholder `@Insert` and `@Query("SELECT * FROM transactions")` returning `Flow<List<TransactionEntity>>`
- Create `AppDatabase.kt` (`@Database`, `version = 1`)
- Create `Converters.kt` for `TransactionType`, `Category`, and `PaymentMethod` enums
- Create `AppModule.kt` in `di/` — provides `AppDatabase` and `TransactionDao` via Hilt `@Provides`

**1.3 — Add DataStore**
- Add `datastore-preferences` to version catalog
- Provide `DataStore<Preferences>` singleton in `AppModule.kt`

**1.4 — Domain models**
- Create `Transaction.kt` (domain model — no Room annotations)
- Create `TransactionType.kt` (enum: `DEBIT`, `CREDIT`)
- Create `Category.kt` (enum: 15 values + `displayName` property + `icon` resource ref)
- Create `PaymentMethod.kt` (enum: `UPI`, `DEBIT_CARD`, `CREDIT_CARD`, `NET_BANKING`, `WALLET`, `ATM_WITHDRAWAL`, `UNKNOWN` + `displayName` + `icon` resource ref)
- Create `TransactionRepository.kt` interface in `domain/repository/`

**1.5 — Repository wiring**
- Create `TransactionRepositoryImpl.kt` in `data/repository/`
  - Implements `TransactionRepository`
  - Maps `TransactionEntity` ↔ `Transaction` (mapper functions)
- Create `RepositoryModule.kt` in `di/` — binds interface to impl

**1.6 — Navigation skeleton**
- Add `navigation-compose` to version catalog
- Create `AppNavGraph.kt` with 4 routes: `Home`, `TransactionList`, `Analytics`, `TransactionDetail(id)`
- Create stub `HomeScreen`, `TransactionListScreen`, `AnalyticsScreen`, `TransactionDetailScreen` composables (each shows just a `Text("ScreenName")`)
- Wire `AppNavGraph` into `MainActivity`

**1.7 — Bottom navigation bar**
- Add a `BottomNavBar` composable with 3 tabs: Home, Transactions, Analytics
- Wire tab selections to nav graph routes

### Phase 1 Testing
- Unit test: `TransactionEntity` ↔ `Transaction` mapper functions (pure Kotlin, no Android)
- Unit test: `Category.displayName` returns expected strings for all 15 enum values
- Unit test: `PaymentMethod.displayName` returns expected strings for all 7 enum values
- Build smoke test: `./gradlew assembleDebug` passes, app launches on emulator

---

## Phase 2 — SMS Integration

**Goal:** App reads SMS, parses transactions, stores them. The data pipeline works
end-to-end even if the UI just shows a raw list.

### Tasks

**2.1 — SMS permissions**
- Add `READ_SMS` and `RECEIVE_SMS` to `AndroidManifest.xml`
- Create `PermissionScreen.kt` — explains why permissions are needed, requests them
- Add permission check at app startup: if not granted → navigate to `PermissionScreen`
- Handle "denied" and "permanently denied" states with distinct UI messages

**2.2 — SmsParser**
- Create `SmsParser.kt` in `data/sms/`
- Define `ParsedSms` data class: `(amount, type, accountNumber, merchant?, paymentMethod, timestampMillis?)`
- Implement `fun parse(body: String, receivedAt: Long): ParsedSms?` — returns `null` for unrecognized
- Implement minimum 6 regex patterns covering:
  - UPI debit (keyword "UPI" in body)
  - Credit card spend ("Credit Card" / "CC")
  - Debit card POS spend ("Debit Card" / "POS")
  - NEFT/IMPS/RTGS credit and debit
  - ATM withdrawal ("ATM" / "cash withdrawal")
  - Wallet debit/credit
  - Low-balance alerts → return `null` (ignored)
- Implement `fun detectPaymentMethod(body: String): PaymentMethod` as a separate keyword-scan pass
  - Check order: CREDIT_CARD → ATM_WITHDRAWAL → UPI → DEBIT_CARD → NET_BANKING → WALLET → UNKNOWN
  - Reason: more-specific keywords win over less-specific (see `01-architecture.md`)
- Parse amount strings: strip commas, handle "Rs.", "INR", "₹" prefixes
- Parse merchant: trim trailing punctuation, normalize whitespace

**2.3 — AutoCategorizer**
- Create `AutoCategorizer.kt` in `domain/` (pure Kotlin)
- Define keyword → `Category` mapping, e.g.:
  - "swiggy", "zomato", "magic pin" → `FOOD_ORDER`
  - "restaurant", "cafe", "hotel" → `FOOD_DINING`
  - "uber", "ola", "rapido", "metro" → `TRANSPORT`
  - "bigbasket", "blinkit", "zepto", "instamart" → `GROCERIES`
  - "amazon", "flipkart", "myntra", "ajio" → `SHOPPING`
  - "netflix", "spotify", "hotstar", "youtube premium" → `RECURRING`
  - "electricity", "water", "gas", "broadband", "jio", "airtel" → `UTILITIES`
  - "rent" → `RENT`
  - "maintenance", "society" → `MAINTENANCE`
  - "pharmacy", "hospital", "clinic", "apollo", "medplus" → `HEALTHCARE`
  - "salon", "spa", "nykaa", "beauty" → `SELF_CARE`
  - "irctc", "makemytrip", "goibibo", "airline" → `TRAVEL`
  - "school", "college", "udemy", "coursera" → `EDUCATION`
  - "salary", "credited" (with no debit keyword) → `INCOME`
- `fun categorize(merchant: String?, description: String): Category`
- Falls back to `OTHERS` if no keyword matches

**2.4 — SmsBroadcastReceiver**
- Create `SmsBroadcastReceiver.kt` annotated `@AndroidEntryPoint`
- Register in `AndroidManifest.xml` for `android.provider.Telephony.SMS_RECEIVED`
- Inject `TransactionRepository` via Hilt
- On receive: extract SMS body + sender + timestamp → call `SmsParser.parse()` → call `AutoCategorizer.categorize()` → insert via repository
- Guard: skip if `ParsedSms` is null (non-financial SMS)
- Dedup: check `smsId` uniqueness before insert (repository handles this)

**2.5 — SmsImporter (historical import)**
- Create `SmsImporter.kt` in `data/sms/` — reads device SMS inbox via `ContentResolver`
- Query `content://sms/inbox` for messages from known bank sender IDs
- Parse + insert each, skipping duplicates by `smsId`
- Expose as `suspend fun importAll()` — called once on first launch (tracked via DataStore flag)
- Create `ImportSmsHistoryUseCase.kt` wrapping `SmsImporter`

**2.6 — First-launch import**
- In `HomeViewModel`, check DataStore flag `sms_imported`
- If false: call `ImportSmsHistoryUseCase` → set flag true
- Show a loading indicator on HomeScreen during import

### Phase 2 Testing
- **Unit test `SmsParser`** — highest priority. Test each regex pattern against real-world SMS samples:
  - UPI debit → `paymentMethod == UPI`
  - Credit card spend → `paymentMethod == CREDIT_CARD`
  - Debit card POS → `paymentMethod == DEBIT_CARD`
  - NEFT/IMPS credit → `paymentMethod == NET_BANKING`
  - ATM withdrawal → `paymentMethod == ATM_WITHDRAWAL`, `merchant == null`
  - Wallet debit → `paymentMethod == WALLET`
  - SMS with "credit card" AND "debit" in same body → `paymentMethod == CREDIT_CARD` (priority test)
  - Amount with commas (e.g., "1,25,000.00")
  - Unrecognized SMS returns `null`
- **Unit test `AutoCategorizer`** — known merchants map to correct categories, unknown maps to `OTHERS`
- **Unit test `TransactionRepositoryImpl`** — use Room in-memory DB (`Room.inMemoryDatabaseBuilder`):
  - Insert deduplication by `smsId`
  - `getAll()` returns correct list
- Integration test `SmsBroadcastReceiver` (instrumented): send fake broadcast → verify DB row created

---

## Phase 3 — Core UI Screens

**Goal:** Users can see their transactions, totals, and manually recategorize.

### Tasks

**3.1 — HomeScreen ViewModel + UI**
- Create `HomeViewModel.kt` with `HomeUiState` sealed interface:
  ```
  sealed interface HomeUiState {
      object Loading : HomeUiState
      data class Success(
          val totalDebit: Double,
          val totalCredit: Double,
          val recentTransactions: List<Transaction>
      ) : HomeUiState
  }
  ```
- `GetAccountSummaryUseCase` — aggregates total debit/credit across all time
- HomeScreen shows:
  - Balance card: total credited − total debited
  - Two chips: total debit (red) / total credit (green)
  - Recent transactions list (last 10)

**3.2 — TransactionListScreen ViewModel + UI**
- `TransactionListUiState` with `List<Transaction>` + active filters
- Filter bar: by account number (chip selector), by category (chip selector), by payment method (chip selector: All / UPI / Debit Card / Credit Card / Net Banking / Wallet / ATM), by type (All / Debit / Credit)
- Each row: merchant name, amount (colored by type), category icon, relative date ("2 days ago")
- `GetTransactionsUseCase` — returns `Flow<List<Transaction>>`, accepts optional filters

**3.3 — TransactionDetailScreen**
- Shows full transaction details: amount, merchant, account, raw SMS body, timestamp
- Category selector: grid of `Category` chips — tapping one calls `UpdateTransactionCategoryUseCase`
- Once manually set, row shows a small "edited" indicator

**3.4 — UpdateTransactionCategoryUseCase**
- Takes `transactionId` + new `Category`
- Sets `isManualCategory = true` in DB
- Used from `TransactionDetailScreen`

**3.5 — Empty states and error states**
- All screens handle: Loading spinner, empty state illustration + copy, error snackbar

### Phase 3 Testing
- **Unit test `GetAccountSummaryUseCase`** — mock repository, assert correct debit/credit sums
- **Unit test `GetTransactionsUseCase`** — verify filter logic (type filter, category filter) with fake data
- **Unit test `UpdateTransactionCategoryUseCase`** — verify `isManualCategory` is set, verify manual category is not overwritten by re-parse
- **Compose UI test (instrumented)**:
  - HomeScreen: pre-seed DB → assert total amounts displayed correctly
  - TransactionListScreen: assert filter chips change visible rows

---

## Phase 4 — Analytics

**Goal:** Users can select a date range and see where money is going by category.

### Tasks

**4.1 — Add Vico**
- Add `vico-compose-m3` to version catalog and `app/build.gradle.kts`

**4.2 — Date range picker**
- Use Material 3 `DateRangePicker` composable
- Store selected range in `AnalyticsViewModel` as `startMillis`/`endMillis`
- Default: current calendar month

**4.3 — GetSpendingByCategoryUseCase**
- Room `@Query` with `GROUP BY category, SUM(amount) WHERE type = 'DEBIT' AND timestamp BETWEEN :start AND :end`
- Returns `List<CategorySpend(category, totalAmount, percentage)>`

**4.4 — GetTransactionsByDateRangeUseCase**
- Reuses `TransactionRepository` query filtered by timestamp range
- Powers both the analytics chart and the filtered transaction list on this screen

**4.5 — AnalyticsScreen UI**
- Date range selector at top (shows "Jun 2025" or "Jun 1 – Jun 19")
- Horizontal bar chart (Vico): category vs. spend amount
- Payment method breakdown: pie or donut chart showing UPI vs Credit Card vs Debit Card vs others for the period
- Total spend for period as a summary card
- Tapping a category bar navigates to `TransactionListScreen` pre-filtered by that category + date range
- Tapping a payment method segment navigates to `TransactionListScreen` pre-filtered by that payment method + date range

**4.6 — Monthly trend**
- Room query: `GROUP BY strftime('%Y-%m', datetime(timestamp/1000, 'unixepoch'))`, SUM amount
- Line chart (Vico) showing monthly spend over last 6 months

### Phase 4 Testing
- **Unit test `GetSpendingByCategoryUseCase`** — Room in-memory DB, insert known transactions, assert category sums
- **Unit test `GetTransactionsByDateRangeUseCase`** — assert boundary transactions (exactly at start/end millis) are included
- **Compose UI test**: AnalyticsScreen renders without crash with populated DB

---

## Phase 5 — Polish & Hardening

**Goal:** Production-quality UX, edge-case handling, complete test coverage.

### Tasks

**5.1 — SMS parser hardening**
- Collect real SMS samples from 5+ banks and add patterns
- Handle edge cases: multiple accounts in one SMS, OTP messages (must be ignored), promotional messages
- Add a debug mode: "why wasn't this SMS parsed?" screen showing which patterns were tried

**5.2 — Account management**
- Derive distinct accounts from parsed transactions (unique `accountNumber` values)
- Show account selector on HomeScreen to filter by account

**5.3 — Search**
- Full-text search on `merchant` and `description` fields
- Add to `TransactionListScreen` as a search bar

**5.4 — Notifications**
- When `SmsBroadcastReceiver` processes a new transaction, post a notification:
  "₹500 debited at Swiggy — categorized as Food & Dining"
- Tapping notification opens `TransactionDetailScreen`

**5.5 — App widget (stretch)**
- Glance widget showing current month spend vs. last month

**5.6 — Comprehensive testing pass**
- Achieve >80% unit test coverage on `domain/` and `data/sms/` packages
- Add property-based tests for `SmsParser` (fuzz merchant name variations)
- End-to-end instrumented test: simulate SMS broadcast → verify HomeScreen totals update

---

## Testing Strategy Summary

| Layer                  | Test type         | Framework              | What to test                              |
|------------------------|-------------------|------------------------|-------------------------------------------|
| `SmsParser`            | Unit              | JUnit 4 + Truth        | Each regex pattern, edge-case amounts     |
| `AutoCategorizer`      | Unit              | JUnit 4 + Truth        | Keyword → category mappings               |
| Domain use cases       | Unit              | JUnit 4 + MockK + Turbine | Flow emissions, aggregation correctness  |
| `TransactionRepositoryImpl` | Integration  | Room in-memory DB      | Insert, dedup, query, update              |
| `SmsBroadcastReceiver` | Instrumented      | AndroidJUnit4          | Broadcast → DB row created                |
| ViewModels             | Unit              | JUnit 4 + MockK + Turbine | UiState transitions, filter logic        |
| Compose screens        | Instrumented      | Compose UI Test        | Key screen states, critical user actions  |

**What we do NOT test:**
- Room-generated code (it is tested by Google)
- Hilt module bindings (they fail at build time if wrong)
- One-line mapper functions that are obviously correct

---

## Dependency Addition Order

Add libraries to `libs.versions.toml` and `app/build.gradle.kts` in this order,
matching the phase they are first needed:

| Phase | Library                                      |
|-------|----------------------------------------------|
| 1     | `hilt-android`, `hilt-compiler`, `room-*`, `datastore-preferences`, `navigation-compose` |
| 2     | *(no new libs — SmsParser is pure Kotlin)*   |
| 3     | *(no new libs)*                              |
| 4     | `vico-compose-m3`                            |
| 5     | `glance-appwidget` (if widget is built)      |
| Test  | `mockk`, `turbine`, `truth` (add in Phase 1) |
