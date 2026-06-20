package com.example.t1.data.sms

import android.content.Context
import android.provider.Telephony
import com.example.t1.domain.model.Transaction
import com.example.t1.domain.repository.TransactionRepository
import com.example.t1.domain.usecase.AutoCategorizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SmsImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: SmsParser,
    private val categorizer: AutoCategorizer,
    private val repository: TransactionRepository,
) {
    suspend fun importAll() = withContext(Dispatchers.IO) {
        val cursor = try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                ),
                null, null,
                "${Telephony.Sms.DATE} DESC",
            )
        } catch (e: SecurityException) {
            return@withContext // Permission not yet granted
        } ?: return@withContext

        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                val smsId = it.getLong(idIdx).toString()
                val address = it.getString(addressIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val date = it.getLong(dateIdx)

                if (!isFinancialSender(address)) continue
                if (repository.existsBySmsId(smsId)) continue

                val parsed = parser.parse(body, date) ?: continue
                val category = categorizer.categorize(parsed.merchant, body, parsed.type)

                repository.insert(
                    Transaction(
                        smsId = smsId,
                        amount = parsed.amount,
                        type = parsed.type,
                        accountNumber = parsed.accountNumber,
                        merchant = parsed.merchant,
                        description = body,
                        category = category,
                        isManualCategory = false,
                        paymentMethod = parsed.paymentMethod,
                        timestamp = date,
                    )
                )
            }
        }
    }

    private fun isFinancialSender(address: String): Boolean {
        val upper = address.uppercase()
        return FINANCIAL_SENDER_KEYWORDS.any { it in upper }
    }

    companion object {
        private val FINANCIAL_SENDER_KEYWORDS = setOf(
            "BANK", "HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "INDUS",
            "YES", "PNB", "BOB", "UNION", "CANARA", "IDFC", "FEDERAL",
            "PAYTM", "PHONEPE", "GPAY", "AMAZON", "MOBIKWIK", "FREECHARGE",
            "BAJAJ", "CITIBANK", "HSBC", "STANDARD", "RBL",
        )
    }
}
