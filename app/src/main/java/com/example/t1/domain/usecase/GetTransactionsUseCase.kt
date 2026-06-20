package com.example.t1.domain.usecase

import com.example.t1.domain.model.Transaction
import com.example.t1.domain.model.TransactionFilter
import com.example.t1.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetTransactionsUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    operator fun invoke(filter: TransactionFilter = TransactionFilter()): Flow<List<Transaction>> =
        repository.observeAll().map { transactions ->
            transactions.filter { tx ->
                (filter.accountNumber == null || tx.accountNumber == filter.accountNumber) &&
                (filter.category == null || tx.category == filter.category) &&
                (filter.paymentMethod == null || tx.paymentMethod == filter.paymentMethod) &&
                (filter.type == null || tx.type == filter.type)
            }
        }
}
