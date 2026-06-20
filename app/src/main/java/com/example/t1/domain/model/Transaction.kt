package com.example.t1.domain.model

data class Transaction(
    val id: Long = 0,
    val smsId: String,
    val amount: Double,
    val type: TransactionType,
    val accountNumber: String,
    val merchant: String?,
    val description: String,
    val category: Category,
    val isManualCategory: Boolean,
    val paymentMethod: PaymentMethod,
    val timestamp: Long,
)
