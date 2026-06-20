package com.example.t1.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EnumDisplayNameTest {

    @Test
    fun `Category has 15 values`() {
        assertThat(Category.entries).hasSize(15)
    }

    @Test
    fun `every Category has a non-blank displayName`() {
        Category.entries.forEach { category ->
            assertThat(category.displayName).isNotEmpty()
        }
    }

    @Test
    fun `Category displayNames are correct`() {
        assertThat(Category.RENT.displayName).isEqualTo("Rent")
        assertThat(Category.MAINTENANCE.displayName).isEqualTo("Maintenance")
        assertThat(Category.GROCERIES.displayName).isEqualTo("Groceries")
        assertThat(Category.FOOD_ORDER.displayName).isEqualTo("Food Order")
        assertThat(Category.FOOD_DINING.displayName).isEqualTo("Food & Dining")
        assertThat(Category.TRANSPORT.displayName).isEqualTo("Transport")
        assertThat(Category.TRAVEL.displayName).isEqualTo("Travel")
        assertThat(Category.SHOPPING.displayName).isEqualTo("Shopping")
        assertThat(Category.RECURRING.displayName).isEqualTo("Recurring")
        assertThat(Category.UTILITIES.displayName).isEqualTo("Utilities")
        assertThat(Category.HEALTHCARE.displayName).isEqualTo("Healthcare")
        assertThat(Category.SELF_CARE.displayName).isEqualTo("Self Care")
        assertThat(Category.EDUCATION.displayName).isEqualTo("Education")
        assertThat(Category.INCOME.displayName).isEqualTo("Income")
        assertThat(Category.OTHERS.displayName).isEqualTo("Others")
    }

    @Test
    fun `PaymentMethod has 7 values`() {
        assertThat(PaymentMethod.entries).hasSize(7)
    }

    @Test
    fun `every PaymentMethod has a non-blank displayName`() {
        PaymentMethod.entries.forEach { method ->
            assertThat(method.displayName).isNotEmpty()
        }
    }

    @Test
    fun `PaymentMethod displayNames are correct`() {
        assertThat(PaymentMethod.UPI.displayName).isEqualTo("UPI")
        assertThat(PaymentMethod.DEBIT_CARD.displayName).isEqualTo("Debit Card")
        assertThat(PaymentMethod.CREDIT_CARD.displayName).isEqualTo("Credit Card")
        assertThat(PaymentMethod.NET_BANKING.displayName).isEqualTo("Net Banking")
        assertThat(PaymentMethod.WALLET.displayName).isEqualTo("Wallet")
        assertThat(PaymentMethod.ATM_WITHDRAWAL.displayName).isEqualTo("ATM Withdrawal")
        assertThat(PaymentMethod.UNKNOWN.displayName).isEqualTo("Unknown")
    }
}
