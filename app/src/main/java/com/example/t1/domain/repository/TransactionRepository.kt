package com.example.t1.domain.repository

import com.example.t1.domain.model.Category
import com.example.t1.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeAll(): Flow<List<Transaction>>
    suspend fun insert(transaction: Transaction)
    suspend fun insertAll(transactions: List<Transaction>)
    suspend fun updateCategory(id: Long, category: Category, isManualCategory: Boolean)
    suspend fun existsBySmsId(smsId: String): Boolean
}
