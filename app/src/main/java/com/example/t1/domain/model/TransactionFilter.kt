package com.example.t1.domain.model

data class TransactionFilter(
    val accountNumber: String? = null,
    val category: Category? = null,
    val paymentMethod: PaymentMethod? = null,
    val type: TransactionType? = null,
) {
    val isActive: Boolean
        get() = accountNumber != null || category != null || paymentMethod != null || type != null
}
