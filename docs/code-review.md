# Code Review — SMS Finance Tracker

## 1. Coding Standards Compliance

### 1.1 Architecture violations

**`HomeViewModel` imports `TransactionRepository` directly (bypasses domain layer)**
File: `ui/home/HomeViewModel.kt`, lines 11, 57–67

`HomeViewModel` injects `TransactionRepository` and calls `repository.observeAll()` directly rather than going through a use case. This breaks Clean Architecture — the UI layer should only touch the domain layer through use cases. A `GetTransactionsUseCase` already exists and is the correct entry point.

Fix: remove the `repository` parameter and inject `GetTransactionsUseCase` instead:
```kotlin
// Before
combine(repository.observeAll(), _timeRange) { all, range -> ... }

// After
combine(getTransactions(), _timeRange) { all, range -> ... }
```

**`AnalyticsViewModel` calls `repository.observeAll()` directly three times**
File: `ui/analytics/AnalyticsViewModel.kt`, lines 29, 56–64, 66–78

`totalCredit` and `paymentBreakdown` both bypass use cases and query `repository.observeAll()` with inline filter logic duplicated from `GetSpendingByCategoryUseCase`. This same domain logic belongs in dedicated use cases (`GetTotalCreditUseCase`, `GetPaymentBreakdownUseCase`) or the existing `GetSpendingByCategoryUseCase` extended to cover credits.

**`TransactionListViewModel` imports `TransactionRepository` directly**
File: `ui/transactions/TransactionListViewModel.kt`, lines 8, 22, 45–47

`accounts` is computed by calling `repository.observeAll()` inline with a `.map` transform. This is a domain query that belongs in a use case (e.g. `GetAccountsUseCase`).

### 1.2 Kotlin style issues

**`detectPaymentMethod` takes `body: String` but converts to lowercase internally, while all callers pass already-lowercased strings**
File: `data/sms/SmsParser.kt`, lines 37–51

The private method `isNonTransactional` (line 53) takes a pre-lowercased `lower` parameter. `detectPaymentMethod` is `public` and applies `.lowercase()` itself (line 38). This inconsistency means the caller in `parse()` at line 23 passes the original `body` to `detectPaymentMethod`, while passing the already-lowercased `lower` to `detectType` and `isNonTransactional`. The public API contract is unclear. Pick one convention: either all helpers take raw body and lowercase internally, or a single `lowercase()` call at the top of `parse()` is reused everywhere.

**`THIS_WEEK` cutoff is a rolling 7-day window, not the current calendar week**
File: `ui/home/HomeViewModel.kt`, lines 41

```kotlin
THIS_WEEK -> cal.timeInMillis - 7L * 24 * 60 * 60 * 1000
```
`THIS_WEEK` subtracts exactly 7 × 24 hours from now rather than snapping to Monday 00:00. The label says "This Week", which users will expect to mean the current calendar week. `TODAY` and `THIS_MONTH` both snap to midnight/start-of-month, so this is inconsistent with the rest of the enum.

**`SimpleDateFormat` used in `GetMonthlyTrendUseCase` and `FormatUtils.kt` — not thread-safe**
Files: `domain/usecase/GetMonthlyTrendUseCase.kt` line 34; `ui/common/FormatUtils.kt` lines 21, 27, 30

`SimpleDateFormat` instances are created on every call but are not reused. While not a correctness bug by itself, the idiomatic modern replacement is `java.time.format.DateTimeFormatter` (API 26+, satisfied by minSdk 32). Use `DateTimeFormatter` for all date formatting.

**`SmsBroadcastReceiver` leaks a `CoroutineScope` tied to no lifecycle**
File: `data/sms/SmsBroadcastReceiver.kt`, lines 32

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```
A `BroadcastReceiver` is re-instantiated each time an SMS arrives (Android recreates it), but the `scope` is an instance field that is never cancelled. Although in practice the scope is short-lived, this is not idiomatic. The correct pattern is to use `goAsync()` with a `GlobalScope.launch` (or a Hilt-injected `@ApplicationScoped` scope) so cancellation semantics are clear.

**`@ColumnInfo(name = "amount")` annotation is redundant**
File: `data/db/TransactionEntity.kt`, line 17

Room uses the property name as the column name when `@ColumnInfo` is not specified or when `name` matches the property name. Using `@ColumnInfo(name = "amount")` on a field named `amount` is a no-op and adds noise. Only columns with snake_case names that differ from the Kotlin property need explicit `@ColumnInfo(name = ...)`.

**`provideTransactionDao` is missing `@Singleton`**
File: `di/AppModule.kt`, line 29

```kotlin
@Provides
fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
```
The `AppDatabase` is a singleton but the DAO provider has no scope annotation. Hilt will call `db.transactionDao()` on every injection point. Room DAOs are cheap objects (no state), so this is not a memory bug, but it is inconsistent with the singleton database and should carry `@Singleton` for clarity.

---

## 2. Bugs & Correctness Issues

### Bug 1: `toDomain()` throws unchecked exception on unrecognised enum string
**File:** `data/repository/TransactionRepositoryImpl.kt`, lines 44, 47, 49

```kotlin
category = Category.valueOf(category),
type = TransactionType.valueOf(type),
paymentMethod = PaymentMethod.valueOf(paymentMethod),
```
`valueOf()` throws `IllegalArgumentException` if the stored string does not match any enum constant. This will crash the app if:
- A new enum value is added in a future release and then that APK's DB is opened by an older APK (downgrade scenario).
- Any corrupted row exists in the database.

**Correct behaviour:** degrade gracefully. Replace with:
```kotlin
category = runCatching { Category.valueOf(category) }.getOrDefault(Category.OTHERS),
type = runCatching { TransactionType.valueOf(type) }.getOrElse { TransactionType.DEBIT },
paymentMethod = runCatching { PaymentMethod.valueOf(paymentMethod) }.getOrDefault(PaymentMethod.UNKNOWN),
```

### Bug 2: `THIS_WEEK` computes a 7-day rolling window, not the current calendar week
**File:** `ui/home/HomeViewModel.kt`, line 41

```kotlin
THIS_WEEK -> cal.timeInMillis - 7L * 24 * 60 * 60 * 1000
```
This returns transactions from "the last 168 hours" rather than "since last Monday". Transactions from late last week appear; transactions from early this week at a different clock time may be excluded. The label "This Week" is misleading.

**Correct behaviour:**
```kotlin
THIS_WEEK -> {
    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    cal.timeInMillis
}
```

### Bug 3: `pendingResult.finish()` is only called inside the `try/finally` within `scope.launch`, but `goAsync()` requires it to be called even if the coroutine is never started
**File:** `data/sms/SmsBroadcastReceiver.kt`, lines 47–86

If `scope.launch` throws before the coroutine body executes (highly unlikely but theoretically possible with an OOM), `pendingResult.finish()` would never be called and Android would hold the wake lock indefinitely. A safer pattern calls `pendingResult.finish()` in a `finally` block that wraps the entire `scope.launch` call, or uses `GlobalScope.launch` with a structured `try/finally` at the outer level.

In practice this is not a real-world risk given the simplicity of the launch site, but it is worth noting for robustness.

### Bug 4: `AnalyticsViewModel._endMillis` is initialised at ViewModel construction time
**File:** `ui/analytics/AnalyticsViewModel.kt`, line 33

```kotlin
private val _endMillis = MutableStateFlow(System.currentTimeMillis())
```
This timestamp is captured once when the ViewModel is created and never updated unless the user opens the date picker. If the user leaves the app open overnight, "this month" still uses yesterday's `endMillis` and today's transactions are invisible until the user explicitly resets the range.

**Correct behaviour:** either refresh `_endMillis` to `System.currentTimeMillis()` on screen resume, or derive the end boundary dynamically when computing the filter.

### Bug 5: `extractAccount` returns `null` for non-wallet SMS with no account number, causing the whole parse to fail
**File:** `data/sms/SmsParser.kt`, lines 88–95

```kotlin
return if (paymentMethod == PaymentMethod.WALLET) "WALLET" else null
```
`parse()` returns `null` when `accountNumber` is `null` (line 24). This means any bank SMS that has a valid amount and transaction type but no recognisable account number pattern is silently dropped — including some legitimate formats used by smaller banks. The parser is more conservative than necessary: it could fall back to `"UNKNOWN"` and still persist the transaction.

---

## 3. Test Coverage Gaps

The existing `SmsParserTest` covers the parser unit well. The following areas have no tests at all:

| # | Missing test area | What to verify |
|---|-------------------|----------------|
| 1 | `AutoCategorizer` | Each category keyword maps to the correct `Category` enum; unknown merchants fall through to `OTHERS`; INCOME is only returned for CREDIT transactions with salary keywords, not for DEBIT. |
| 2 | `AutoCategorizer` keyword priority | "irctc" should match `TRAVEL` not `TRANSPORT`; "ola cab" should match `TRANSPORT` not a broader match. |
| 3 | `GetTransactionsUseCase` | Verify `TransactionFilter` combinations (type only, category only, all four fields set, no fields set returns all). Use a fake repository. |
| 4 | `GetSpendingByCategoryUseCase` | Verify percentage calculation sums to 1.0; returns empty list when no DEBIT transactions exist in range; correctly excludes CREDIT transactions. |
| 5 | `GetMonthlyTrendUseCase` | Returns exactly 6 entries; oldest month label is correct; months with no transactions have `totalDebit == 0.0` and `totalCredit == 0.0`. |
| 6 | `HomeViewModel` | `transactions` filters correctly when `_timeRange` changes (use `TestCoroutineScheduler` + Turbine); `startImportIfNeeded()` does not call `importSmsHistory` when permission check returns false. |
| 7 | `TransactionRepositoryImpl.toDomain()` | `Category.valueOf` / `TransactionType.valueOf` with an unrecognised string — currently throws, this is also the bug described in section 2. |
| 8 | `SmsImporter` | Verify duplicate `smsId` is skipped (call import twice with same synthetic cursor data, confirm `insert` is only called once). Requires mocking `ContentResolver` or using a fake. |
| 9 | `SmsParser` — merchant extraction | Each regex path: `MERCHANT_LABEL_REGEX`, `PAID_TO_REGEX`, `UPI_TO_REGEX`, `AT_REGEX`; UPI_TO strips the `@handle` suffix; `cleanMerchant()` strips trailing punctuation. |
| 10 | `SmsParser` — minimum balance filter | `"Your account has minimum balance Rs.100. Please top up."` should return null (already has a test for "low balance" but not "minimum balance" specifically with an amount). |
| 11 | `FormatUtils` | `toRupeeString()` for 0.0, large Indian-format numbers (1,00,000); `toRelativeDateString()` boundary conditions ("Just now" at < 60 s, "1m ago" at exactly 60 s). |

---

## 4. Suggested Future Features (Prioritised)

| # | Feature | User value | Effort | Layers touched |
|---|---------|-----------|--------|----------------|
| 1 | **Budget limits per category** | User sets a monthly cap (e.g. ₹3,000 for Food Order); app shows progress bar and notifies when 80%/100% spent. | M | Domain (new BudgetRepository), Data (new Room table), UI (new budget screen + Analytics card) |
| 2 | **Manual transaction entry** | Lets users log cash or non-SMS payments (e.g. UPI to friend) that banks don't send SMS for, keeping spending complete. | M | Domain (model extension), Data (DAO insert), UI (FAB + bottom sheet form) |
| 3 | **Export to CSV / PDF** | One-tap export of filtered transactions for expense reports or tax filing. | M | Domain (new ExportUseCase), UI (share intent), no DB changes |
| 4 | **Recurring transaction detection** | Automatically flags subscriptions that recur on a similar date each month (same merchant, similar amount), surfaces them in a "Recurring" summary card. | L | Domain (new RecurringDetector use case), UI (analytics card) |
| 5 | **Multi-account balance tracking** | Tracks per-account net balance from credited vs debited amounts, giving the user an approximate real-time balance view. | S | Domain (new GetAccountSummaryUseCase — model exists), UI (Home summary cards) |
| 6 | **Widget (Glance)** | Home screen widget showing today's spend vs budget so the user never has to open the app to stay aware. | M | UI only (Jetpack Glance), reads from existing repository |
| 7 | **Dark/light theme toggle** | Explicit in-app theme switch stored in DataStore, independent of system theme for users who prefer different settings per app. | S | UI (Theme.kt, settings screen), Data (DataStore key) |
| 8 | **Notification for budget breach** | Push notification when a category crosses its budget limit, triggered from the SMS ingestion path. | S | Domain (BudgetRepository check in SmsBroadcastReceiver), UI none |
| 9 | **Search by amount range** | Let users type "500–2000" in the search bar (or use a range slider) to find large transactions quickly. | S | Domain (extend TransactionFilter), UI (TransactionListScreen filter panel) |
| 10 | **Swipe-to-recategorise** | Swipe left on a TransactionRow to pop an inline category picker without navigating away, reducing friction for bulk categorisation. | M | UI only (SwipeToDismiss composable), calls existing `updateCategory` |
| 11 | **Bank-specific SMS parser profiles** | Let users select their bank from a list; load bank-specific regex profiles so edge-case formats (Kotak, Yes Bank, Axis) are handled reliably. | L | Data (SmsParser refactor + parser profiles), Domain (BankProfile model) |
| 12 | **Year-over-year analytics** | Show the same month across different years (e.g. Jun 2025 vs Jun 2026) to reveal spending trends, not just the last 6 months. | M | Domain (extend GetMonthlyTrendUseCase with year param), UI (AnalyticsScreen new chart) |

---

## 5. Technical Debt

### TD-1: Inline domain logic in ViewModels should move to use cases
**Where:** `AnalyticsViewModel` (lines 55–78), `HomeViewModel` (lines 64–67), `TransactionListViewModel` (lines 45–47)

All three ViewModels contain filter/transform logic that duplicates or bypasses the domain layer. Each inline `.filter { }` block inside a ViewModel is a test that can't be written without a Compose/Hilt test harness. Extract into use cases with `@Inject constructor(repository: TransactionRepository)` — they are then purely unit-testable with a fake repository.

### TD-2: `SmsParser` regex constants should be named more consistently and the `ACCOUNT_REGEX` comment is misleading
**Where:** `data/sms/SmsParser.kt`, line 138 comment

The comment says "Min 3 handles banks like ICICI that mask as 'XX332' (3-digit suffix)" — but the regex matches the raw digit group (e.g. `332`) and the surrounding code prepends `"XX"`. The comment conflates the regex match with the final formatted value. Fix the comment to say "captures 3–8 raw digits; caller prepends 'XX'".

### TD-3: `creditGreen` is defined in `TransactionRow.kt` but used across multiple files
**Where:** `ui/common/TransactionRow.kt` line 91; imported by `AnalyticsScreen.kt`

A colour constant living in a component file and imported across unrelated screens is fragile. Move `creditGreen` to `ui/theme/Color.kt` alongside the other colour definitions, which is where consumers naturally look for it.

### TD-4: `HomeTimeRange.cutoffMillis()` computes a new `Calendar` on every call
**Where:** `ui/home/HomeViewModel.kt`, lines 30–51

The `cutoffMillis()` function is called every time the `combine` block emits (i.e., on every DB change). It instantiates `Calendar.getInstance()` each call. This is cheap but avoidable — `cutoffMillis()` could be a lazy property or memoised. More importantly, `THIS_WEEK` is computed with a raw millisecond offset rather than calendar arithmetic (see Bug 2), making this function inconsistent internally.

### TD-5: `DataStore` is wired in `AppModule` but never injected anywhere
**Where:** `di/AppModule.kt`, lines 34–36

`provideDataStore` provides a `DataStore<Preferences>` singleton that no class currently injects. Either remove the binding until a feature needs it, or add a brief comment indicating it is reserved for a future settings screen. Dead DI bindings increase compile time (KSP processing) and confuse readers.

### TD-6: `AppDatabase` class is not visible in the reviewed files
Room requires an `@Database`-annotated class (`AppDatabase`) that lists entities and provides DAOs. This class must declare `version = 1` and a migration strategy. If no `fallbackToDestructiveMigration()` or `Migration` objects are configured, any schema change will crash existing installs. Confirm the database class sets an explicit migration strategy and document the current schema version.

### TD-7: No database migration strategy is evident from `AppModule`
**Where:** `di/AppModule.kt`, lines 25–27

```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "t1_database")
    .build()
```
No `.fallbackToDestructiveMigration()` or `.addMigrations(...)` call is present. When the schema changes (adding a column, adding a table for budgets), Room will throw an `IllegalStateException` on existing installs. At minimum, add `.fallbackToDestructiveMigration()` during development and replace with real migrations before shipping.
