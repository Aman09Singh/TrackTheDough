package com.example.t1.domain.usecase

import com.example.t1.domain.model.CategorySpend
import com.example.t1.domain.model.TransactionType
import com.example.t1.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetSpendingByCategoryUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    operator fun invoke(startMillis: Long, endMillis: Long): Flow<List<CategorySpend>> =
        repository.observeAll().map { transactions ->
            val filtered = transactions.filter {
                it.type == TransactionType.DEBIT && it.timestamp in startMillis..endMillis
            }
            val total = filtered.sumOf { it.amount }
            if (total == 0.0) return@map emptyList()

            filtered.groupBy { it.category }
                .map { (cat, txs) ->
                    val amount = txs.sumOf { it.amount }
                    CategorySpend(
                        category = cat,
                        totalAmount = amount,
                        percentage = (amount / total).toFloat(),
                    )
                }
                .sortedByDescending { it.totalAmount }
        }
}
