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
    // Returns false if SMS permission was denied.
    // Uses the same smsId format as SmsBroadcastReceiver ("address_timestamp") so both
    // paths share the same deduplication key and the same SMS is never inserted twice.
    suspend fun importAll(): Boolean = withContext(Dispatchers.IO) {
        val cursor = try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                ),
                null, null,
                "${Telephony.Sms.DATE} DESC",
            )
        } catch (e: SecurityException) {
            return@withContext false
        } ?: return@withContext false

        cursor.use {
            val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                val address = it.getString(addressIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val date = it.getLong(dateIdx)

                // Composite key matches SmsBroadcastReceiver — prevents duplicates regardless
                // of which path first inserts the transaction.
                val smsId = "${address}_${date}"

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
        true
    }
}
