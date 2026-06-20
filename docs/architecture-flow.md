# Architecture & Flow Diagrams

## 1. Layer Diagram

```
+---------------------------------------------------------------------+
|                          UI LAYER                                   |
|                                                                     |
|  MainActivity           AppNavGraph           BottomNavBar          |
|  T1Application                                                      |
|                                                                     |
|  +----------------+  +----------------------+  +----------------+  |
|  | HomeScreen     |  | TransactionListScreen|  | AnalyticsScreen|  |
|  | HomeViewModel  |  | TransactionListVM    |  | AnalyticsVM    |  |
|  +----------------+  +----------------------+  +----------------+  |
|                                                                     |
|  TransactionDetailScreen   PermissionScreen                        |
|                                                                     |
|  ui/common: TransactionRow, CategoryBadge, FormatUtils             |
|  ui/theme:  T1Theme, Color.kt, Type.kt                             |
+-------------------------------|-------------------------------------+
                                | (invoke use cases / observe flows)
+-------------------------------|-------------------------------------+
|                       DOMAIN LAYER                                  |
|                                                                     |
|  model: Transaction, Category, PaymentMethod, TransactionType,     |
|         TransactionFilter, CategorySpend, MonthlySpend             | 
|                                                                     |
|  repository (interface): TransactionRepository                     |
|                                                                     |
|  use cases:                                                         |
|    GetTransactionsUseCase       GetSpendingByCategoryUseCase       |
|    GetMonthlyTrendUseCase        AutoCategorizer                   |
|    ImportSmsHistoryUseCase                                         |
+-------------------------------|-------------------------------------+
                                | (DAO calls / content resolver)
+-------------------------------|-------------------------------------+
|                        DATA LAYER                                   |
|                                                                     |
|  db: AppDatabase, TransactionEntity, TransactionDao                |
|  repository: TransactionRepositoryImpl  (implements domain iface)  |
|  sms: SmsParser, SmsImporter, SmsBroadcastReceiver                 |
|                                                                     |
|  di: AppModule (Room, DataStore), RepositoryModule (Binds impl)    |
+---------------------------------------------------------------------+

  Dependency direction: UI --> Domain <-- Data
  (Domain has no Android imports; Data depends on Domain interfaces)
```

---

## 2. SMS Capture Flows

### 2a. Real-time Flow (live SMS)

```
  Bank sends SMS
       |
       v
+------------------+
| Android System   |  broadcasts SMS_RECEIVED intent
+------------------+
       |
       v
+------------------------------+
| SmsBroadcastReceiver         |
|  goAsync() -> coroutine      |
|  builds smsId = addr_ts      |
+------------------------------+
       |
       | repository.existsBySmsId(smsId)
       |   YES --> return (dedup)
       |   NO  --> continue
       v
+------------------+
| SmsParser        |
|  parse(body, ts) |
+------------------+
       |
       | ParsedSms (amount, type, accountNumber,
       |            merchant, paymentMethod)
       |   null --> log "not financial", return
       v
+------------------+
| AutoCategorizer  |
|  categorize(...) |
+------------------+
       |
       | Category enum value
       v
+-----------------------------+
| TransactionRepository       |
|  insert(Transaction)        |   --> TransactionRepositoryImpl
+-----------------------------+        --> TransactionDao.insert()
       |                                   --> Room SQLite write
       |
       v
+-----------------------------+
| Room emits new list         |  (invalidation tracker wakes up)
| TransactionDao.observeAll() |
+-----------------------------+
       |
       v  (Flow<List<Transaction>> propagates up the chain)
+-----------------------------+
| HomeViewModel               |
|  transactions StateFlow     |  recompose triggers
+-----------------------------+
       |
       v
+-----------------------------+
| HomeScreen (Compose)        |
|  collectAsStateWithLifecycle|  UI redraws transaction list
+-----------------------------+
       |
       v
+-----------------------------+
| NotificationManagerCompat   |  postNotification() -- parallel
|  shows system notification  |
+-----------------------------+
```

### 2b. Historical Import Flow (on app open)

```
  App opens -> MainActivity.onCreate()
       |
       v
+-----------------------------+
| LaunchedEffect(Unit)        |  checks READ_SMS permission
| HomeScreen                  |
+-----------------------------+
       |
       | has permission? NO --> PermissionScreen shown first
       | has permission? YES --> continue
       v
+-----------------------------+
| HomeViewModel               |
|  startImportIfNeeded()      |
+-----------------------------+
       |
       v
+-----------------------------+
| ImportSmsHistoryUseCase     |
|  invoke()                   |  thin wrapper, delegates entirely
+-----------------------------+
       |
       v
+------------------------------------------+
| SmsImporter.importAll()                  |
|  ContentResolver.query(                  |
|    Telephony.Sms.Inbox.CONTENT_URI,      |  reads SMS inbox
|    [ADDRESS, BODY, DATE], ORDER BY DATE) |
+------------------------------------------+
       |
       | iterates each row in cursor
       v
  +-------------------------------------+
  | for each SMS row:                   |
  |   smsId = "${address}_${date}"      |
  |                                     |
  |   repository.existsBySmsId(smsId)?  |
  |     YES --> skip (already stored)   |
  |     NO  --> continue                |
  |                                     |
  |   SmsParser.parse(body, date)       |
  |     null --> skip (not financial)   |
  |                                     |
  |   AutoCategorizer.categorize(...)   |
  |                                     |
  |   repository.insert(Transaction)    |
  +-------------------------------------+
       |
       v
  Room emits updated list --> StateFlow --> UI recompose
  (same tail as real-time flow above)
```

---

## 3. UI Navigation Map

```
  +------------------+
  | PermissionScreen |  (shown only if READ_SMS / RECEIVE_SMS not granted)
  |                  |  on grant --> popUpTo(PERMISSIONS) inclusive
  +------------------+
           |
           | navigate("home"), pop permissions off stack
           v

  +=========================================+
  |         Scaffold + BottomNavBar         |
  |  [Home]   [Transactions]   [Analytics]  |
  +=========================================+
       |              |               |
       |  (bottom     |  (bottom nav  |  (bottom nav
       |   nav tap)   |   tap)        |   tap)
       v              v               v
  +---------+  +------------------+  +-----------------+
  | Home    |  | TransactionList  |  | Analytics       |
  | Screen  |  | Screen           |  | Screen          |
  |         |  |                  |  |                 |
  | Time    |  | Search bar       |  | Date range      |
  | range   |  | Filter panel     |  | picker          |
  | chips   |  | (type, method,   |  | Spend summary   |
  |         |  |  category,       |  | Category chart  |
  | Summary |  |  account)        |  | Payment method  |
  | totals  |  | Lazy list of     |  | chart           |
  |         |  | TransactionRow   |  | Monthly trend   |
  | Lazy    |  |                  |  | (6 months)      |
  | list of |  +------------------+  +-----------------+
  | recent  |         |
  | txns    |         | tap row
  +---------+         |
       |              |
       | tap row      |
       |              v
       +--------> +------------------+
                  | Transaction      |  (push, no bottom nav)
                  | Detail Screen    |
                  |                  |
                  | Full SMS body    |
                  | Amount + type    |
                  | Category picker  |
                  | (manual override)|
                  |                  |
                  | [Back arrow]     |
                  | --> popBackStack |
                  +------------------+

  Bottom nav uses launchSingleTop + saveState/restoreState.
  PERMISSIONS and TRANSACTION_DETAIL hide the bottom nav bar.
  TRANSACTION_DETAIL route pattern: "transaction_detail/{id}" (Long arg).
```

---

## 4. Data Flow Inside HomeScreen

```
  Room SQLite (transactions table)
       |
       | invalidation on INSERT/UPDATE
       v
  TransactionDao.observeAll()
       |  Flow<List<TransactionEntity>>
       v
  TransactionRepositoryImpl.observeAll()
       |  .map { it.map(TransactionEntity::toDomain) }
       |  Flow<List<Transaction>>
       v
  HomeViewModel — combine()
       |
       |  combine(
       |    repository.observeAll(),   <-- Flow<List<Transaction>>
       |    _timeRange                 <-- MutableStateFlow<HomeTimeRange>
       |  ) { all, range ->
       |      all.filter { it.timestamp >= range.cutoffMillis() }
       |  }
       |
       |  .stateIn(
       |      viewModelScope,
       |      SharingStarted.WhileSubscribed(5_000),
       |      emptyList()
       |  )
       |
       v
  HomeViewModel.transactions: StateFlow<List<Transaction>>
       |
       | (Compose observes this in HomeScreen)
       v
  val transactions by viewModel.transactions
      .collectAsStateWithLifecycle()
       |
       | State<List<Transaction>> — Compose snapshot state
       v
  HomeScreen recomposition
       |
       +-- transactions.isEmpty() --> EmptyState composable
       |
       +-- else --> LazyColumn { items(transactions) { tx ->
                        TransactionRow(tx, onClick = ...)
                    }}

  Parallel flow for the time-range chips:
  _timeRange (MutableStateFlow) <-- viewModel.setTimeRange(range)
      ^                                       ^
      |                                       |
  FilterChip.onClick()              TimeRangeSelector composable
```

---

## 5. Room Schema — `transactions` table

| Column           | SQLite type | Kotlin type  | Constraints                              |
|------------------|-------------|--------------|------------------------------------------|
| `id`             | INTEGER     | Long         | PRIMARY KEY, AUTOINCREMENT               |
| `sms_id`         | TEXT        | String       | NOT NULL, UNIQUE INDEX (deduplication)   |
| `amount`         | REAL        | Double       | NOT NULL                                 |
| `type`           | TEXT        | String       | NOT NULL (`"DEBIT"` or `"CREDIT"`)       |
| `account_number` | TEXT        | String       | NOT NULL (e.g. `"XX1234"`, `"WALLET"`)   |
| `merchant`       | TEXT        | String?      | NULLABLE                                 |
| `description`    | TEXT        | String       | NOT NULL (raw SMS body)                  |
| `category`       | TEXT        | String       | NOT NULL (Category enum `.name`)         |
| `is_manual_cat`  | INTEGER     | Boolean      | NOT NULL (0/1; true = user-set, never overwritten by re-parse) |
| `payment_method` | TEXT        | String       | NOT NULL (PaymentMethod enum `.name`)    |
| `timestamp`      | INTEGER     | Long         | NOT NULL (Unix millis from SMS)          |

**Notes:**
- `type`, `category`, and `payment_method` store the Kotlin enum `.name` string. Mapping back to domain types uses `TransactionType.valueOf()`, `Category.valueOf()`, and `PaymentMethod.valueOf()` in `TransactionRepositoryImpl.toDomain()`.
- The unique index on `sms_id` enforces deduplication at the DB level. `INSERT OR IGNORE` (`OnConflictStrategy.IGNORE`) is used so duplicate inserts are silently dropped.
- `observeAll()` returns `Flow<List<TransactionEntity>>` ordered by `timestamp DESC`; Room's invalidation tracker triggers new emissions on every `INSERT` or `UPDATE`.
- `updateCategory` targets a single row by `id` and sets `is_manual_cat = 1`, which prevents any future re-parse from overwriting the user's choice.
