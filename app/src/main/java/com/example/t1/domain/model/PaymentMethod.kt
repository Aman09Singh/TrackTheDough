package com.example.t1.domain.model

enum class PaymentMethod(val displayName: String) {
    UPI("UPI"),
    DEBIT_CARD("Debit Card"),
    CREDIT_CARD("Credit Card"),
    NET_BANKING("Net Banking"),
    WALLET("Wallet"),
    ATM_WITHDRAWAL("ATM Withdrawal"),
    UNKNOWN("Unknown"),
}
