package com.example.t1.data.repository

import com.example.t1.data.db.TransactionEntity
import com.example.t1.domain.model.Category
import com.example.t1.domain.model.PaymentMethod
import com.example.t1.domain.model.Transaction
import com.example.t1.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransactionMappersTest {

    private val entity = TransactionEntity(
        id = 1L,
        smsId = "sms-001",
        amount = 499.00,
        type = "DEBIT",
        accountNumber = "XX1234",
        merchant = "Swiggy",
        description = "Rs.499 debited from a/c XX1234 for UPI to SWIGGY",
        category = "FOOD_ORDER",
        isManualCategory = false,
        paymentMethod = "UPI",
        timestamp = 1_700_000_000_000L,
    )

    private val domain = Transaction(
        id = 1L,
        smsId = "sms-001",
        amount = 499.00,
        type = TransactionType.DEBIT,
        accountNumber = "XX1234",
        merchant = "Swiggy",
        description = "Rs.499 debited from a/c XX1234 for UPI to SWIGGY",
        category = Category.FOOD_ORDER,
        isManualCategory = false,
        paymentMethod = PaymentMethod.UPI,
        timestamp = 1_700_000_000_000L,
    )

    @Test
    fun `entity maps to domain correctly`() {
        val result = entity.toDomain()
        assertThat(result).isEqualTo(domain)
    }

    @Test
    fun `domain maps to entity correctly`() {
        val result = domain.toEntity()
        assertThat(result).isEqualTo(entity)
    }

    @Test
    fun `round-trip entity to domain to entity is lossless`() {
        val result = entity.toDomain().toEntity()
        assertThat(result).isEqualTo(entity)
    }

    @Test
    fun `null merchant survives round-trip`() {
        val withNullMerchant = entity.copy(merchant = null)
        assertThat(withNullMerchant.toDomain().merchant).isNull()
        assertThat(withNullMerchant.toDomain().toEntity().merchant).isNull()
    }

    @Test
    fun `isManualCategory true survives round-trip`() {
        val manual = entity.copy(isManualCategory = true)
        assertThat(manual.toDomain().isManualCategory).isTrue()
    }
}
