package com.example.t1.domain.usecase

import com.example.t1.domain.model.AccountSummary
import com.example.t1.domain.model.TransactionType
import com.example.t1.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetAccountSummaryUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    operator fun invoke(): Flow<AccountSummary> =
        repository.observeAll().map { transactions ->
            AccountSummary(
                totalDebit = transactions
                    .filter { it.type == TransactionType.DEBIT }
                    .sumOf { it.amount },
                totalCredit = transactions
                    .filter { it.type == TransactionType.CREDIT }
                    .sumOf { it.amount },
                recentTransactions = transactions.take(10),
            )
        }
}
