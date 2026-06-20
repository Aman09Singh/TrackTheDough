package com.example.t1.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["sms_id"], unique = true)],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "sms_id") val smsId: String,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "account_number") val accountNumber: String,
    @ColumnInfo(name = "merchant") val merchant: String?,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "is_manual_cat") val isManualCategory: Boolean,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
)
