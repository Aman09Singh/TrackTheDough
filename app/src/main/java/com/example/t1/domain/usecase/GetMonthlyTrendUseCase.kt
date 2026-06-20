package com.example.t1.domain.usecase

import com.example.t1.domain.model.MonthlySpend
import com.example.t1.domain.model.TransactionType
import com.example.t1.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class GetMonthlyTrendUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    operator fun invoke(): Flow<List<MonthlySpend>> =
        repository.observeAll().map { transactions ->
            (5 downTo 0).map { monthsBack ->
                val startCal = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -monthsBack)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = (startCal.clone() as Calendar).apply {
                    add(Calendar.MONTH, 1)
                    add(Calendar.MILLISECOND, -1)
                }
                val start = startCal.timeInMillis
                val end = endCal.timeInMillis
                val label = SimpleDateFormat("MMM yy", Locale.getDefault()).format(Date(start))
                val monthTxs = transactions.filter { it.timestamp in start..end }
                MonthlySpend(
                    label = label,
                    totalDebit = monthTxs.filter { it.type == TransactionType.DEBIT }.sumOf { it.amount },
                    totalCredit = monthTxs.filter { it.type == TransactionType.CREDIT }.sumOf { it.amount },
                )
            }
        }
}
