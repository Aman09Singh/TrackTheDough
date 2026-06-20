package com.example.t1.data.sms

import com.example.t1.domain.model.PaymentMethod
import com.example.t1.domain.model.TransactionType
import javax.inject.Inject

data class ParsedSms(
    val amount: Double,
    val type: TransactionType,
    val accountNumber: String,
    val merchant: String?,
    val paymentMethod: PaymentMethod,
)

class SmsParser @Inject constructor() {

    fun parse(body: String, receivedAt: Long): ParsedSms? {
        val lower = body.lowercase()
        if (isNonTransactional(lower)) return null

        val amount = extractAmount(body) ?: return null
        val type = detectType(lower) ?: return null
        val paymentMethod = detectPaymentMethod(lower)
        val accountNumber = extractAccount(body, paymentMethod) ?: return null
        val merchant = extractMerchant(body)

        return ParsedSms(
            amount = amount,
            type = type,
            accountNumber = accountNumber,
            merchant = merchant,
            paymentMethod = paymentMethod,
        )
    }

    // Separate pass — called independently so tests can verify detection priority.
    fun detectPaymentMethod(body: String): PaymentMethod {
        val lower = body.lowercase()
        return when {
            "credit card" in lower || "creditcard" in lower -> PaymentMethod.CREDIT_CARD
            "atm" in lower || "cash withdrawal" in lower || "cash withdrawn" in lower -> PaymentMethod.ATM_WITHDRAWAL
            "upi" in lower || "bhim" in lower || "phonepe" in lower
                    || "gpay" in lower || "google pay" in lower -> PaymentMethod.UPI
            " dc " in lower || "debit card" in lower || "pos " in lower -> PaymentMethod.DEBIT_CARD
            "neft" in lower || "imps" in lower || "rtgs" in lower
                    || "fund transfer" in lower || "online transfer" in lower -> PaymentMethod.NET_BANKING
            "wallet" in lower -> PaymentMethod.WALLET
            else -> PaymentMethod.UNKNOWN
        }
    }

    private fun isNonTransactional(lower: String): Boolean {
        val hasTransaction = "debited" in lower || "credited" in lower
                || "withdrawn" in lower || "spent" in lower || "charged" in lower
        if (!hasTransaction) return true

        return "otp" in lower && lower.indexOf("otp") < lower.indexOf("debit").takeIf { it >= 0 } ?: Int.MAX_VALUE
                || "low balance" in lower
                || "minimum balance" in lower
    }

    private fun extractAmount(body: String): Double? {
        val match = AMOUNT_REGEX.find(body) ?: return null
        return match.groupValues[1].replace(",", "").toDoubleOrNull()
    }

    private fun detectType(lower: String): TransactionType? = when {
        "debited" in lower || "spent" in lower
                || "withdrawn" in lower || "charged" in lower
                || "deducted" in lower -> TransactionType.DEBIT
        "credited" in lower || "received" in lower
                || "deposited" in lower -> TransactionType.CREDIT
        else -> null
    }

    private fun extractAccount(body: String, paymentMethod: PaymentMethod): String? {
        val match = ACCOUNT_REGEX.find(body)
        if (match != null) {
            return "XX${match.groupValues[1].takeLast(4)}"
        }
        // Wallet SMS often have no bank account number
        return if (paymentMethod == PaymentMethod.WALLET) "WALLET" else null
    }

    private fun extractMerchant(body: String): String? {
        // 1. Explicit "Merchant: NAME" label (highest confidence)
        MERCHANT_LABEL_REGEX.find(body)?.groupValues?.get(1)
            ?.trim()?.cleanMerchant()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        // 2. "Paid to NAME" — common in wallet SMS
        PAID_TO_REGEX.find(body)?.groupValues?.get(1)
            ?.trim()?.cleanMerchant()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        // 3. "UPI to NAME@handle" or "UPI to NAME" — UPI debit
        UPI_TO_REGEX.find(body)?.groupValues?.get(1)
            ?.substringBefore("@")?.trim()?.cleanMerchant()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        // 4. "at NAME" — card POS spend
        AT_REGEX.find(body)?.groupValues?.get(1)
            ?.trim()?.cleanMerchant()
            ?.takeIf { it.length >= 2 }
            ?.let { return it }

        return null
    }

    private fun String.cleanMerchant(): String =
        trim().trimEnd('.', ',', ';', '!')

    companion object {
        // Handles: Rs.500, Rs 1,200.00, INR500, INR 1,25,000.00, ₹500
        private val AMOUNT_REGEX = Regex(
            """(?:Rs\.?\s*|INR\s*|₹\s*)([\d,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE,
        )

        // Matches last 4-6 digits after a/c, account, acct, card identifiers
        private val ACCOUNT_REGEX = Regex(
            """(?:a/c|account|acct|A/C|card)\s*(?:no\.?\s*|number\s*)?[xX*\-]*(\d{4,6})\b""",
            RegexOption.IGNORE_CASE,
        )

        // "Merchant: NAME" or "Merchant Name: NAME"
        private val MERCHANT_LABEL_REGEX = Regex(
            """Merchant(?:\s+Name)?:\s*([A-Za-z0-9][A-Za-z0-9@.\- ]{1,39})""",
            RegexOption.IGNORE_CASE,
        )

        // "Paid to NAME" pattern
        private val PAID_TO_REGEX = Regex(
            """[Pp]aid\s+to\s+([A-Za-z0-9][A-Za-z0-9@.\- ]{1,39}?)(?:\.|,|\n|$)""",
        )

        // "UPI to NAME" — captures up to "@" sign or sentence end
        private val UPI_TO_REGEX = Regex(
            """UPI\s+to\s+([A-Za-z0-9][A-Za-z0-9@.\-]{1,39}?)(?:\s+on\b|\s+Ref|\.|,|\n|$)""",
            RegexOption.IGNORE_CASE,
        )

        // "at NAME" — card spend merchant
        private val AT_REGEX = Regex(
            """\bat\s+([A-Z][A-Za-z0-9 &.\-]{1,39}?)(?:\s+on\b|\s+dated\b|\.|,|\n|$)""",
        )
    }
}
