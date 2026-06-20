package com.example.t1.domain.usecase

import com.example.t1.domain.model.Category
import com.example.t1.domain.repository.TransactionRepository
import javax.inject.Inject

class UpdateTransactionCategoryUseCase @Inject constructor(
    private val repository: TransactionRepository,
) {
    suspend operator fun invoke(transactionId: Long, category: Category) {
        repository.updateCategory(transactionId, category, isManualCategory = true)
    }
}
