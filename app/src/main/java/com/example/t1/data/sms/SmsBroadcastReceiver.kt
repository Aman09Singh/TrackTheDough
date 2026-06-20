package com.example.t1.data.sms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.t1.MainActivity
import com.example.t1.T1Application
import com.example.t1.domain.model.Transaction
import com.example.t1.domain.model.TransactionType
import com.example.t1.domain.repository.TransactionRepository
import com.example.t1.domain.usecase.AutoCategorizer
import com.example.t1.ui.common.toRupeeString
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

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: return
        val timestamp = messages.firstOrNull()?.timestampMillis ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }

        if (body.isBlank()) return

        Log.i(TAG, "▶ SMS received from=$sender body=${body.take(80)}")

        val smsId = "${sender}_${timestamp}"
        val pendingResult = goAsync()

        scope.launch {
            try {
                if (repository.existsBySmsId(smsId)) {
                    Log.i(TAG, "⏭ Already imported: $smsId")
                    return@launch
                }

                val parsed = parser.parse(body, timestamp)
                if (parsed == null) {
                    Log.i(TAG, "✗ Not a financial SMS — sender=$sender body=${body.take(80)}")
                    return@launch
                }

                val category = categorizer.categorize(parsed.merchant, body, parsed.type)
                Log.i(TAG, "✓ Parsed: amount=${parsed.amount} type=${parsed.type} method=${parsed.paymentMethod} merchant=${parsed.merchant} category=$category")

                val transaction = Transaction(
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
                repository.insert(transaction)
                Log.i(TAG, "✓ Transaction saved: $smsId")

                postNotification(context, transaction)
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error processing SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(context: Context, transaction: Transaction) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, transaction.smsId.hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val amountStr = transaction.amount.toRupeeString()
        val direction = if (transaction.type == TransactionType.DEBIT) "debited" else "credited"
        val title = if (transaction.merchant != null) "$amountStr $direction at ${transaction.merchant}"
                    else "$amountStr $direction"

        val notification = NotificationCompat.Builder(context, T1Application.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("Categorized as ${transaction.category.displayName}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(transaction.smsId.hashCode(), notification)
    }

    companion object {
        const val TAG = "T1_SMS"
    }
}
