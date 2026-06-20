package com.example.t1.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.t1.domain.model.Transaction
import com.example.t1.domain.repository.TransactionRepository
import com.example.t1.domain.usecase.AutoCategorizer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TransactionRepository
    @Inject lateinit var parser: SmsParser
    @Inject lateinit var categorizer: AutoCategorizer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?: return

        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: return
        val timestamp = messages.firstOrNull()?.timestampMillis ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }

        if (body.isBlank()) return

        Log.d(TAG, "SMS from $sender: $body")

        val smsId = "${sender}_${timestamp}"

        val pendingResult = goAsync()
        scope.launch {
            try {
                if (repository.existsBySmsId(smsId)) {
                    Log.d(TAG, "Already imported: $smsId")
                    return@launch
                }

                val parsed = parser.parse(body, timestamp)
                if (parsed == null) {
                    Log.d(TAG, "Not a financial SMS, skipped")
                    return@launch
                }

                val category = categorizer.categorize(parsed.merchant, body, parsed.type)
                Log.d(TAG, "Inserting: amount=${parsed.amount} type=${parsed.type} method=${parsed.paymentMethod} merchant=${parsed.merchant} category=$category")

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
                        timestamp = timestamp,
                    )
                )
                Log.d(TAG, "Transaction saved successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }
}
