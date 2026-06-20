package com.example.t1.domain.model

data class AccountSummary(
    val totalDebit: Double,
    val totalCredit: Double,
    val recentTransactions: List<Transaction>,
)
