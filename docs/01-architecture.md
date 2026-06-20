# Architecture

## System Overview

The app is a **local-first SMS finance tracker**. There is no backend server.
All data originates from bank/wallet SMS messages on the device, is parsed locally,
stored in a Room database, and presented through Jetpack Compose screens.

---

## Architectural Pattern: MVVM + Clean Architecture

**Why MVVM + Clean Architecture over plain MVVM?**

Plain MVVM works for small apps but collapses when business logic grows. The SMS
parser, categorization rules, and aggregation queries are non-trivial domain logic
that must be independently testable without Android framework dependencies.
Clean Architecture enforces this boundary through three layers:

```
┌─────────────────────────────────────────┐
│              UI Layer                   │  ← Compose screens + ViewModels
│  (depends only on Domain, never Data)  │
└────────────────────┬────────────────────┘
                     │ calls use cases
┌────────────────────▼────────────────────┐
│            Domain Layer                 │  ← Use cases + domain models
│      (zero Android dependencies)        │     (pure Kotlin)
└────────────────────┬────────────────────┘
                     │ calls repository interfaces
┌────────────────────▼────────────────────┐
│             Data Layer                  │  ← Room, SmsParser, DataStore
│     (implements domain interfaces)      │
└─────────────────────────────────────────┘
```

**Key rule:** Dependency arrows always point inward. Domain knows nothing about
Room or Android. Data implements domain interfaces (Dependency Inversion).

---

## Full System Data Flow

```
                    ┌─────────────────────────────────┐
  Bank sends SMS ──►│  SmsBroadcastReceiver            │
                    │  (registered in AndroidManifest) │
                    └──────────────┬──────────────────┘
                                   │ raw SMS string
                    ┌──────────────▼──────────────────┐
                    │  SmsParser (Data Layer)          │
                    │  - Regex patterns per bank fmt   │
                    │  - Extracts: amount, type,       │
                    │    account, merchant, timestamp  │
                    └──────────────┬──────────────────┘
                                   │ ParsedSms domain model
                    ┌──────────────▼──────────────────┐
                    │  AutoCategorizer (Domain Layer)  │
                    │  - Keyword → Category mapping    │
                    │  - Falls back to OTHERS          │
                    └──────────────┬──────────────────┘
                                   │ Transaction domain model
                    ┌──────────────▼──────────────────┐
                    │  TransactionRepository (Data)    │
                    │  - Inserts into Room DB          │
                    │  - Deduplicates by smsId         │
                    └──────────────┬──────────────────┘
                                   │
                    ┌──────────────▼──────────────────┐
                    │       Room Database              │
                    │  transactions + categories tables│
                    └──────────────┬──────────────────┘
                                   │ Flow<List<Transaction>>
                    ┌──────────────▼──────────────────┐
                    │  Use Cases (Domain Layer)        │
                    │  GetTransactionsUseCase          │
                    │  GetSpendingByCategoryUseCase    │
                    │  GetAccountSummaryUseCase        │
                    └──────────────┬──────────────────┘
                                   │ domain models
                    ┌──────────────▼──────────────────┐
                    │  ViewModels (UI Layer)           │
                    │  - UiState as StateFlow          │
                    │  - Exposed to Compose via        │
                    │    collectAsStateWithLifecycle() │
                    └──────────────┬──────────────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                    ▼
        HomeScreen       TransactionListScreen    AnalyticsScreen
    (balance summary,    (filterable list,        (date-range
     recent txns)         manual categorize)       charts)
```

---

## Package Structure

```
com.example.t1
│
├── data
│   ├── db
│   │   ├── AppDatabase.kt           ← Room @Database
│   │   ├── TransactionDao.kt        ← @Dao queries
│   │   ├── TransactionEntity.kt     ← @Entity (Room row)
│   │   └── Converters.kt            ← TypeConverters (enum, timestamp)
│   ├── repository
│   │   └── TransactionRepositoryImpl.kt
│   └── sms
│       ├── SmsBroadcastReceiver.kt  ← RECEIVE_SMS broadcast
│       ├── SmsImporter.kt           ← reads SMS inbox history
│       └── SmsParser.kt             ← regex-based parser
│
├── domain
│   ├── model
│   │   ├── Transaction.kt           ← core domain model
│   │   ├── TransactionType.kt       ← DEBIT / CREDIT
│   │   ├── Category.kt              ← enum of 15 categories
│   │   └── PaymentMethod.kt         ← UPI / DEBIT_CARD / CREDIT_CARD / NET_BANKING / WALLET / ATM_WITHDRAWAL / UNKNOWN
│   ├── repository
│   │   └── TransactionRepository.kt ← interface (no Room import)
│   └── usecase
│       ├── GetTransactionsUseCase.kt
│       ├── GetTransactionsByDateRangeUseCase.kt
│       ├── GetSpendingByCategoryUseCase.kt
│       ├── GetAccountSummaryUseCase.kt
│       ├── UpdateTransactionCategoryUseCase.kt
│       └── ImportSmsHistoryUseCase.kt
│
├── ui
│   ├── home
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── transactions
│   │   ├── TransactionListScreen.kt
│   │   ├── TransactionListViewModel.kt
│   │   ├── TransactionDetailScreen.kt
│   │   └── TransactionDetailViewModel.kt
│   ├── analytics
│   │   ├── AnalyticsScreen.kt
│   │   └── AnalyticsViewModel.kt
│   ├── permissions
│   │   └── PermissionScreen.kt      ← onboarding SMS permission grant
│   ├── navigation
│   │   └── AppNavGraph.kt
│   └── common
│       ├── components/              ← shared Composables
│       └── UiState.kt               ← sealed interface Loading/Success/Error
│
└── di
    ├── AppModule.kt                 ← Room, DataStore bindings
    ├── RepositoryModule.kt          ← interface → impl bindings
    └── UseCaseModule.kt
```

---

## Database Schema

### transactions table

| Column          | Type    | Notes                                  |
|-----------------|---------|----------------------------------------|
| id              | INTEGER | PK, autoincrement                      |
| sms_id          | TEXT    | unique — dedup guard (from SMS inbox)  |
| amount          | REAL    | always positive                        |
| type            | TEXT    | "DEBIT" or "CREDIT"                    |
| account_number  | TEXT    | last 4 digits, e.g. "XX1234"          |
| merchant        | TEXT?   | parsed payee/merchant name             |
| description     | TEXT    | raw SMS body                           |
| category        | TEXT    | Category enum name                     |
| is_manual_cat   | INTEGER | 1 if user manually set category        |
| payment_method  | TEXT    | PaymentMethod enum name                |
| timestamp       | INTEGER | epoch millis (parsed from SMS or received time) |

**Why store raw SMS as `description`?**
Parser accuracy is never 100%. Storing the source lets us re-parse on rule
improvements without losing data.

**Why `is_manual_cat`?**
When the user manually sets a category, we must not overwrite it on re-parse.
This flag gates that.

### Categories (enum, not a table)

`RENT`, `MAINTENANCE`, `GROCERIES`, `FOOD_ORDER`, `FOOD_DINING`,
`TRANSPORT`, `TRAVEL`, `SHOPPING`, `RECURRING`, `UTILITIES`,
`HEALTHCARE`, `SELF_CARE`, `EDUCATION`, `INCOME`, `OTHERS`

Keeping categories as a Kotlin enum (not a DB table) keeps queries simple and
avoids joins. Categories are stable enough that a code change + migration is
acceptable when adding new ones.

### PaymentMethod (enum, not a table)

| Value             | Detected when SMS contains                                              |
|-------------------|-------------------------------------------------------------------------|
| `UPI`             | "UPI", "UPI Ref", "BHIM", "PhonePe", "GPay", "Google Pay", "UPI ID"   |
| `DEBIT_CARD`      | "debit card", "Debit Card", "DC", "POS" (and NOT credit card keywords) |
| `CREDIT_CARD`     | "credit card", "Credit Card", "CC", "credit limit"                     |
| `NET_BANKING`     | "NEFT", "IMPS", "RTGS", "fund transfer", "online transfer"             |
| `WALLET`          | "wallet", "Amazon Pay balance", "Paytm Wallet" (without UPI keyword)   |
| `ATM_WITHDRAWAL`  | "ATM", "cash withdrawal", "ATM Ref"                                    |
| `UNKNOWN`         | Fallback when no signal matches                                         |

**Why keep ATM_WITHDRAWAL separate from DEBIT_CARD?**
ATM withdrawals are cash — they don't map to a merchant or spending category
the way card purchases do. Separating them lets the analytics screen exclude or
highlight cash spend distinctly.

**Detection order matters:** Check `CREDIT_CARD` before `DEBIT_CARD` because
some SMS bodies contain both "card" and "debit" (e.g., "your HDFC Credit Card
debit of Rs 500"). Credit card keywords are more specific so they win.

---

## SMS Parsing Strategy

Bank SMS formats vary widely. The parser uses **ordered regex patterns**:

1. Each pattern targets a specific bank/format family.
2. Patterns are tried in order; first match wins.
3. Required captures: `amount`, `type` (debit/credit keyword), `account`.
4. Optional captures: `merchant`, `datetime`.
5. Payment method is detected in a **separate pass** after the main pattern match —
   it scans the full SMS body for method-specific keywords (see table above).
   This keeps payment method detection independent of per-bank regex patterns.

**Example patterns (illustrative):**

```
// UPI debit: "Rs.500.00 debited from a/c XX1234 for UPI to SWIGGY"
// → amount=500.00, type=DEBIT, account=XX1234, merchant=SWIGGY, method=UPI
Pattern A: (?:Rs\.?|INR\s*)(\d[\d,]*\.?\d*)\s+(?:debited|Debited).*?a/c\s+(\w+).*?to\s+(.+?)(?:\s+on|$)

// Credit card spend: "INR 1,200.00 spent on HDFC Credit Card XX5678 at AMAZON"
// → amount=1200.00, type=DEBIT, account=XX5678, merchant=AMAZON, method=CREDIT_CARD
Pattern B: INR\s+([\d,]+\.?\d*)\s+spent.*?(\w{2}\d{4}).*?at\s+(.+?)(?:\s+on|$)

// Debit card POS: "Rs 250 debited from SBI Debit Card XX1234 at DMART"
// → amount=250, type=DEBIT, account=XX1234, merchant=DMART, method=DEBIT_CARD
Pattern C: (?:Rs\.?\s*|INR\s*)([\d,]+\.?\d*)\s+debited.*?Debit Card\s+(\w+).*?at\s+(.+?)(?:\s+on|$)

// NEFT/IMPS credit: "Rs 5000.00 credited to your a/c XX9012 via NEFT"
// → amount=5000.00, type=CREDIT, account=XX9012, merchant=null, method=NET_BANKING
Pattern D: (?:Rs\.?\s*|INR\s*)([\d,]+\.?\d*)\s+credited.*?a/c\s+(\w+)

// ATM withdrawal: "Rs 2000 withdrawn from ATM using Debit Card XX1234"
// → amount=2000, type=DEBIT, account=XX1234, merchant=null, method=ATM_WITHDRAWAL
Pattern E: (?:Rs\.?\s*|INR\s*)([\d,]+\.?\d*)\s+withdrawn.*?ATM.*?(\w{2,4}\d{4})
```

**Payment method detection pass (applied after pattern match):**
```kotlin
fun detectPaymentMethod(body: String): PaymentMethod {
    val lower = body.lowercase()
    return when {
        "credit card" in lower || " cc " in lower  -> CREDIT_CARD
        "atm" in lower || "cash withdrawal" in lower -> ATM_WITHDRAWAL
        "upi" in lower || "bhim" in lower || "phonepe" in lower
            || "gpay" in lower || "google pay" in lower -> UPI
        "debit card" in lower || " dc " in lower || "pos" in lower -> DEBIT_CARD
        "neft" in lower || "imps" in lower || "rtgs" in lower
            || "fund transfer" in lower               -> NET_BANKING
        "wallet" in lower                            -> WALLET
        else                                         -> UNKNOWN
    }
}
```

The `SmsParser` returns `null` for unrecognized formats (not a crash). Unrecognized
SMS bodies are stored as-is with category `OTHERS`, type derived from keyword
scan ("debit"/"credit" present anywhere in body), and method `UNKNOWN`.

---

## Permission Flow

Two permissions required:

| Permission          | When requested          | Why                                     |
|---------------------|-------------------------|-----------------------------------------|
| `READ_SMS`          | First app launch        | Read SMS inbox history on first import  |
| `RECEIVE_SMS`       | First app launch        | Listen for new SMS in real-time         |

Both are `DANGEROUS` permissions requiring runtime grant.

**Onboarding flow:** App starts → check permissions → if missing, show
`PermissionScreen` with clear rationale → request → on denial, show
"limited functionality" state (manual entry is future scope).

> **Play Store note:** `READ_SMS` requires explicit Google Play approval for
> production distribution. For personal sideloaded use this is unrestricted.
