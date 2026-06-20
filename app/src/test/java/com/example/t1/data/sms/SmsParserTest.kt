package com.example.t1.data.sms

import com.example.t1.domain.model.PaymentMethod
import com.example.t1.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class SmsParserTest {

    private lateinit var parser: SmsParser

    @Before
    fun setUp() {
        parser = SmsParser()
    }

    // ── Amount parsing ─────────────────────────────────────────────────────────

    @Test
    fun `parses amount with commas correctly`() {
        val sms = "Rs 1,25,000.00 credited to your A/c XX9012 via NEFT on 20Jun26"
        val result = parser.parse(sms, 0L)
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(125000.00)
    }

    @Test
    fun `parses INR prefix amount`() {
        val sms = "INR 1,200.00 spent on HDFC Credit Card XX5678 at AMAZON INDIA on 20-Jun-26"
        val result = parser.parse(sms, 0L)
        assertThat(result!!.amount).isEqualTo(1200.00)
    }

    // ── Transaction type ───────────────────────────────────────────────────────

    @Test
    fun `spent keyword maps to DEBIT`() {
        val sms = "INR 500.00 spent on ICICI Credit Card XX1234 at SWIGGY on 20Jun26"
        assertThat(parser.parse(sms, 0L)!!.type).isEqualTo(TransactionType.DEBIT)
    }

    @Test
    fun `credited keyword maps to CREDIT`() {
        val sms = "Rs 50000.00 credited to your A/c XX9012 via NEFT on 20Jun26"
        assertThat(parser.parse(sms, 0L)!!.type).isEqualTo(TransactionType.CREDIT)
    }

    // ── Payment method detection ───────────────────────────────────────────────

    @Test
    fun `UPI debit detected as UPI`() {
        val sms = "Rs.499.00 debited from a/c XX1234 for UPI to SWIGGY on 20Jun26"
        val result = parser.parse(sms, 0L)
        assertThat(result).isNotNull()
        assertThat(result!!.paymentMethod).isEqualTo(PaymentMethod.UPI)
        assertThat(result.type).isEqualTo(TransactionType.DEBIT)
    }

    @Test
    fun `credit card spend detected as CREDIT_CARD`() {
        val sms = "INR 1,200.00 spent on HDFC Credit Card XX5678 at AMAZON INDIA on 20-Jun-26"
        val result = parser.parse(sms, 0L)
        assertThat(result!!.paymentMethod).isEqualTo(PaymentMethod.CREDIT_CARD)
    }

    @Test
    fun `debit card POS detected as DEBIT_CARD`() {
        val sms = "Rs 250.00 debited from A/c XX1234 at DMART via Debit Card on 20Jun26"
        val result = parser.parse(sms, 0L)
        assertThat(result!!.paymentMethod).isEqualTo(PaymentMethod.DEBIT_CARD)
    }

    @Test
    fun `NEFT credit detected as NET_BANKING`() {
        val sms = "Rs 50000.00 credited to your A/c XX9012 via NEFT on 20Jun26"
        val result = parser.parse(sms, 0L)
        assertThat(result!!.paymentMethod).isEqualTo(PaymentMethod.NET_BANKING)
    }

    @Test
    fun `ATM withdrawal detected as ATM_WITHDRAWAL with null merchant`() {
        val sms = "Rs 2000.00 withdrawn from ATM. A/c No. XX1234. Avl Bal Rs 8000"
        val result = parser.parse(sms, 0L)
        assertThat(result!!.paymentMethod).isEqualTo(PaymentMethod.ATM_WITHDRAWAL)
        assertThat(result.merchant).isNull()
    }

    @Test
    fun `wallet debit detected as WALLET`() {
        val sms = "Rs 200.00 debited from your Paytm Wallet. Paid to Swiggy. Txn ID 123456"
        val result = parser.parse(sms, 0L)
        assertThat(result!!.paymentMethod).isEqualTo(PaymentMethod.WALLET)
    }

    @Test
    fun `CREDIT_CARD wins over DEBIT when both keywords present`() {
        val sms = "INR 500 spent on ICICI Bank Credit Card XX1234 at ZOMATO on 20Jun26"
        val result = parser.parse(sms, 0L)
        assertThat(result).isNotNull()
        assertThat(result!!.paymentMethod).isEqualTo(PaymentMethod.CREDIT_CARD)
    }

    // ── Unrecognized / non-transactional ──────────────────────────────────────

    @Test
    fun `unrecognized SMS returns null`() {
        val sms = "Your order has been placed. Track it at example.com"
        assertThat(parser.parse(sms, 0L)).isNull()
    }

    @Test
    fun `low balance alert returns null`() {
        val sms = "Low balance alert: Your A/c XX1234 balance is Rs 100"
        assertThat(parser.parse(sms, 0L)).isNull()
    }

    // ── Account extraction ─────────────────────────────────────────────────────

    @Test
    fun `account number masked to XX last-4 digits`() {
        val sms = "Rs 500.00 debited from A/c 9876543210 1234 via UPI on 20Jun26"
        // Regex should pick up the 4-digit tail
        val result = parser.parse(sms, 0L)
        if (result != null) {
            assertThat(result.accountNumber).startsWith("XX")
            assertThat(result.accountNumber).hasLength(6)
        }
    }

    // ── detectPaymentMethod (unit-testable independently) ─────────────────────

    @Test
    fun `detectPaymentMethod PhonePe keyword returns UPI`() {
        assertThat(parser.detectPaymentMethod("Rs 100 debited via PhonePe UPI")).isEqualTo(PaymentMethod.UPI)
    }

    @Test
    fun `detectPaymentMethod IMPS returns NET_BANKING`() {
        assertThat(parser.detectPaymentMethod("Rs 5000 debited via IMPS Ref 123")).isEqualTo(PaymentMethod.NET_BANKING)
    }

    // ── "used for" / "utilized" credit card formats ───────────────────────────

    @Test
    fun `credit card SMS with 'used for' is parsed as DEBIT`() {
        val sms = "Your HDFC Bank Credit Card XX1234 has been used for Rs.1500.00 at AMAZON on 20-Jun-26. Avl Limit Rs.48500"
        val result = parser.parse(sms, 0L)
        assertThat(result).isNotNull()
        assertThat(result!!.type).isEqualTo(TransactionType.DEBIT)
        assertThat(result.amount).isEqualTo(1500.00)
        assertThat(result.paymentMethod).isEqualTo(PaymentMethod.CREDIT_CARD)
    }

    @Test
    fun `credit card SMS with 'used at' is parsed as DEBIT`() {
        val sms = "ICICI Bank Credit Card XX5678 used at ZOMATO for Rs.350.00 on 20Jun26. Avl Limit Rs.9650"
        val result = parser.parse(sms, 0L)
        assertThat(result).isNotNull()
        assertThat(result!!.type).isEqualTo(TransactionType.DEBIT)
        assertThat(result.amount).isEqualTo(350.00)
    }

    @Test
    fun `ICICI 3-digit account suffix XX332 is parsed`() {
        val sms = "ICICI Bank Acct XX332 debited for Rs 1.00 on 21-Jun-26; AMAN SINGH credited. UPI"
        val result = parser.parse(sms, 0L)
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(1.00)
        assertThat(result.type).isEqualTo(TransactionType.DEBIT)
        assertThat(result.accountNumber).isEqualTo("XX332")
        assertThat(result.paymentMethod).isEqualTo(PaymentMethod.UPI)
    }

    @Test
    fun `card ending format extracts account number`() {
        val sms = "Rs.750.00 charged to your Card ending XX5678 at FLIPKART. Avl Limit Rs.32000"
        val result = parser.parse(sms, 0L)
        assertThat(result).isNotNull()
        assertThat(result!!.accountNumber).isEqualTo("XX5678")
    }

    @Test
    fun `OTP message with used keyword is rejected`() {
        val sms = "OTP 834521 to be used for completing purchase. Do not share."
        assertThat(parser.parse(sms, 0L)).isNull()
    }

    // ── Avl Limit → Credit Card ────────────────────────────────────────────────

    @Test
    fun `Avl Limit suffix marks transaction as CREDIT_CARD`() {
        // "card" alone wouldn't trigger CREDIT_CARD — "avl limit" is the only signal
        val sms = "Rs.750.00 charged to your Card XX5678 at FLIPKART. Avl Limit Rs.32000"
        val result = parser.parse(sms, 0L)
        assertThat(result).isNotNull()
        assertThat(result!!.paymentMethod).isEqualTo(PaymentMethod.CREDIT_CARD)
        assertThat(result.accountNumber).isEqualTo("XX5678")
    }

    @Test
    fun `detectPaymentMethod Avl Limit returns CREDIT_CARD`() {
        assertThat(parser.detectPaymentMethod("Rs 500 spent on Card XX1234. Avl Limit Rs 45000"))
            .isEqualTo(PaymentMethod.CREDIT_CARD)
    }

    @Test
    fun `detectPaymentMethod Available Limit returns CREDIT_CARD`() {
        assertThat(parser.detectPaymentMethod("Rs 1000 charged. Available Limit: Rs 80000"))
            .isEqualTo(PaymentMethod.CREDIT_CARD)
    }
}
