package com.example.t1.domain.usecase

import app.cash.turbine.test
import com.example.t1.domain.model.AccountSummary
import com.example.t1.domain.model.Category
import com.example.t1.domain.model.PaymentMethod
import com.example.t1.domain.model.Transaction
import com.example.t1.domain.model.TransactionType
import com.example.t1.domain.repository.TransactionRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class GetAccountSummaryUseCaseTest {

    private lateinit var repository: TransactionRepository
    private lateinit var useCase: GetAccountSummaryUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = GetAccountSummaryUseCase(repository)
    }

    @Test
    fun `empty list returns zero totals and empty recent`() = runTest {
        every { repository.observeAll() } returns flowOf(emptyList())

        useCase().test {
            val summary = awaitItem()
            assertThat(summary.totalDebit).isEqualTo(0.0)
            assertThat(summary.totalCredit).isEqualTo(0.0)
            assertThat(summary.recentTransactions).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sums debit and credit separately`() = runTest {
        val transactions = listOf(
            makeTransaction(500.0, TransactionType.DEBIT),
            makeTransaction(200.0, TransactionType.DEBIT),
            makeTransaction(1000.0, TransactionType.CREDIT),
        )
        every { repository.observeAll() } returns flowOf(transactions)

        useCase().test {
            val summary = awaitItem()
            assertThat(summary.totalDebit).isEqualTo(700.0)
            assertThat(summary.totalCredit).isEqualTo(1000.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recent transactions capped at 10`() = runTest {
        val transactions = (1..15).map { i ->
            makeTransaction(100.0 * i, TransactionType.DEBIT, id = i.toLong())
        }
        every { repository.observeAll() } returns flowOf(transactions)

        useCase().test {
            assertThat(awaitItem().recentTransactions).hasSize(10)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fewer than 10 transactions shows all`() = runTest {
        val transactions = (1..3).map { i ->
            makeTransaction(100.0, TransactionType.CREDIT, id = i.toLong())
        }
        every { repository.observeAll() } returns flowOf(transactions)

        useCase().test {
            assertThat(awaitItem().recentTransactions).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun makeTransaction(
        amount: Double,
        type: TransactionType,
        id: Long = 1L,
    ) = Transaction(
        id = id,
        smsId = "sms_$id",
        amount = amount,
        type = type,
        accountNumber = "XX1234",
        merchant = null,
        description = "test",
        category = Category.OTHERS,
        isManualCategory = false,
        paymentMethod = PaymentMethod.UPI,
        timestamp = 0L,
    )
}
