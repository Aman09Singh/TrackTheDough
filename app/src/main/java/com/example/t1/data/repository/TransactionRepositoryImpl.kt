package com.example.t1.data.repository

import com.example.t1.data.db.TransactionDao
import com.example.t1.data.db.TransactionEntity
import com.example.t1.domain.model.Category
import com.example.t1.domain.model.PaymentMethod
import com.example.t1.domain.model.Transaction
import com.example.t1.domain.model.TransactionType
import com.example.t1.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {

    override fun observeAll(): Flow<List<Transaction>> =
        dao.observeAll().map { it.map(TransactionEntity::toDomain) }

    override fun observeById(id: Long): Flow<Transaction?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun insert(transaction: Transaction) {
        dao.insert(transaction.toEntity())
    }

    override suspend fun insertAll(transactions: List<Transaction>) {
        dao.insertAll(transactions.map(Transaction::toEntity))
    }

    override suspend fun updateCategory(id: Long, category: Category, isManualCategory: Boolean) {
        dao.updateCategory(id, category.name, isManualCategory)
    }

    override suspend fun existsBySmsId(smsId: String): Boolean =
        dao.existsBySmsId(smsId)
}

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    smsId = smsId,
    amount = amount,
    type = TransactionType.valueOf(type),
    accountNumber = accountNumber,
    merchant = merchant,
    description = description,
    category = Category.valueOf(category),
    isManualCategory = isManualCategory,
    paymentMethod = PaymentMethod.valueOf(paymentMethod),
    timestamp = timestamp,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    smsId = smsId,
    amount = amount,
    type = type.name,
    accountNumber = accountNumber,
    merchant = merchant,
    description = description,
    category = category.name,
    isManualCategory = isManualCategory,
    paymentMethod = paymentMethod.name,
    timestamp = timestamp,
)
