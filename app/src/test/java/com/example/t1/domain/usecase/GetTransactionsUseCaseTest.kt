package com.example.t1.domain.usecase

import app.cash.turbine.test
import com.example.t1.domain.model.Category
import com.example.t1.domain.model.PaymentMethod
import com.example.t1.domain.model.Transaction
import com.example.t1.domain.model.TransactionFilter
import com.example.t1.domain.model.TransactionType
import com.example.t1.domain.repository.TransactionRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class GetTransactionsUseCaseTest {

    private lateinit var repository: TransactionRepository
    private lateinit var useCase: GetTransactionsUseCase

    private val debitUpi = makeTransaction(1L, TransactionType.DEBIT, PaymentMethod.UPI, Category.FOOD_ORDER, "XX1111")
    private val creditNeft = makeTransaction(2L, TransactionType.CREDIT, PaymentMethod.NET_BANKING, Category.INCOME, "XX2222")
    private val debitCard = makeTransaction(3L, TransactionType.DEBIT, PaymentMethod.CREDIT_CARD, Category.SHOPPING, "XX1111")

    @Before
    fun setUp() {
        repository = mockk()
        useCase = GetTransactionsUseCase(repository)
        every { repository.observeAll() } returns flowOf(listOf(debitUpi, creditNeft, debitCard))
    }

    @Test
    fun `no filter returns all transactions`() = runTest {
        useCase().test {
            assertThat(awaitItem()).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter by DEBIT type`() = runTest {
        useCase(TransactionFilter(type = TransactionType.DEBIT)).test {
            val result = awaitItem()
            assertThat(result).hasSize(2)
            assertThat(result.all { it.type == TransactionType.DEBIT }).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter by CREDIT type`() = runTest {
        useCase(TransactionFilter(type = TransactionType.CREDIT)).test {
            val result = awaitItem()
            assertThat(result).hasSize(1)
            assertThat(result.first().id).isEqualTo(2L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter by payment method UPI`() = runTest {
        useCase(TransactionFilter(paymentMethod = PaymentMethod.UPI)).test {
            val result = awaitItem()
            assertThat(result).hasSize(1)
            assertThat(result.first().id).isEqualTo(1L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter by category SHOPPING`() = runTest {
        useCase(TransactionFilter(category = Category.SHOPPING)).test {
            val result = awaitItem()
            assertThat(result).hasSize(1)
            assertThat(result.first().id).isEqualTo(3L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter by account number`() = runTest {
        useCase(TransactionFilter(accountNumber = "XX1111")).test {
            val result = awaitItem()
            assertThat(result).hasSize(2)
            assertThat(result.all { it.accountNumber == "XX1111" }).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `combined type and payment method filter`() = runTest {
        useCase(TransactionFilter(type = TransactionType.DEBIT, paymentMethod = PaymentMethod.CREDIT_CARD)).test {
            val result = awaitItem()
            assertThat(result).hasSize(1)
            assertThat(result.first().id).isEqualTo(3L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter with no matches returns empty list`() = runTest {
        useCase(TransactionFilter(paymentMethod = PaymentMethod.WALLET)).test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun makeTransaction(
        id: Long,
        type: TransactionType,
        paymentMethod: PaymentMethod,
        category: Category,
        accountNumber: String,
    ) = Transaction(
        id = id,
        smsId = "sms_$id",
        amount = 100.0,
        type = type,
        accountNumber = accountNumber,
        merchant = null,
        description = "test",
        category = category,
        isManualCategory = false,
        paymentMethod = paymentMethod,
        timestamp = 0L,
    )
}
