package com.example.t1.domain.usecase

import com.example.t1.domain.model.Category
import com.example.t1.domain.repository.TransactionRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class UpdateTransactionCategoryUseCaseTest {

    private lateinit var repository: TransactionRepository
    private lateinit var useCase: UpdateTransactionCategoryUseCase

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        useCase = UpdateTransactionCategoryUseCase(repository)
    }

    @Test
    fun `calls updateCategory with isManualCategory true`() = runTest {
        useCase(transactionId = 42L, category = Category.FOOD_ORDER)

        coVerify {
            repository.updateCategory(
                id = 42L,
                category = Category.FOOD_ORDER,
                isManualCategory = true,
            )
        }
    }

    @Test
    fun `isManualCategory is always true regardless of category`() = runTest {
        Category.entries.forEach { cat ->
            useCase(transactionId = 1L, category = cat)
            coVerify { repository.updateCategory(1L, cat, isManualCategory = true) }
        }
    }
}
