package com.example.t1.domain.usecase

import com.example.t1.domain.model.Category
import com.example.t1.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class AutoCategorizerTest {

    private lateinit var categorizer: AutoCategorizer

    @Before
    fun setUp() {
        categorizer = AutoCategorizer()
    }

    @Test
    fun `swiggy merchant maps to FOOD_ORDER`() {
        assertThat(categorizer.categorize("Swiggy", "Rs 499 debited")).isEqualTo(Category.FOOD_ORDER)
    }

    @Test
    fun `zomato merchant maps to FOOD_ORDER`() {
        assertThat(categorizer.categorize("Zomato", "Rs 320 debited")).isEqualTo(Category.FOOD_ORDER)
    }

    @Test
    fun `restaurant keyword in description maps to FOOD_DINING`() {
        assertThat(categorizer.categorize(null, "Rs 800 debited at THE GRAND RESTAURANT")).isEqualTo(Category.FOOD_DINING)
    }

    @Test
    fun `uber maps to TRANSPORT`() {
        assertThat(categorizer.categorize("Uber", "Rs 150 debited")).isEqualTo(Category.TRANSPORT)
    }

    @Test
    fun `bigbasket maps to GROCERIES`() {
        assertThat(categorizer.categorize("BigBasket", "Rs 1200 debited")).isEqualTo(Category.GROCERIES)
    }

    @Test
    fun `amazon maps to SHOPPING`() {
        assertThat(categorizer.categorize("Amazon", "Rs 2000 debited")).isEqualTo(Category.SHOPPING)
    }

    @Test
    fun `netflix maps to RECURRING`() {
        assertThat(categorizer.categorize("Netflix", "Rs 649 debited")).isEqualTo(Category.RECURRING)
    }

    @Test
    fun `airtel maps to UTILITIES`() {
        assertThat(categorizer.categorize("Airtel", "Rs 299 debited for Airtel recharge")).isEqualTo(Category.UTILITIES)
    }

    @Test
    fun `rent keyword maps to RENT`() {
        assertThat(categorizer.categorize(null, "Rs 15000 debited rent payment")).isEqualTo(Category.RENT)
    }

    @Test
    fun `society keyword maps to MAINTENANCE`() {
        assertThat(categorizer.categorize(null, "Rs 2500 paid housing society fee")).isEqualTo(Category.MAINTENANCE)
    }

    @Test
    fun `apollo pharmacy maps to HEALTHCARE`() {
        assertThat(categorizer.categorize("Apollo", "Rs 500 debited at apollo pharmacy")).isEqualTo(Category.HEALTHCARE)
    }

    @Test
    fun `nykaa maps to SELF_CARE`() {
        assertThat(categorizer.categorize("Nykaa", "Rs 800 debited")).isEqualTo(Category.SELF_CARE)
    }

    @Test
    fun `irctc maps to TRAVEL`() {
        assertThat(categorizer.categorize("IRCTC", "Rs 1500 debited for IRCTC booking")).isEqualTo(Category.TRAVEL)
    }

    @Test
    fun `udemy maps to EDUCATION`() {
        assertThat(categorizer.categorize("Udemy", "Rs 499 debited")).isEqualTo(Category.EDUCATION)
    }

    @Test
    fun `salary credit maps to INCOME`() {
        assertThat(
            categorizer.categorize(null, "Rs 75000 credited salary COMPANY NAME", TransactionType.CREDIT)
        ).isEqualTo(Category.INCOME)
    }

    @Test
    fun `salary keyword on DEBIT does NOT map to INCOME`() {
        // Salary refund or reversal shouldn't auto-categorize as INCOME on debit
        val result = categorizer.categorize(null, "Rs 500 debited salary advance", TransactionType.DEBIT)
        assertThat(result).isNotEqualTo(Category.INCOME)
    }

    @Test
    fun `unknown merchant falls back to OTHERS`() {
        assertThat(categorizer.categorize("XYZPQR", "Rs 100 debited")).isEqualTo(Category.OTHERS)
    }

    @Test
    fun `null merchant with no matching keyword falls back to OTHERS`() {
        assertThat(categorizer.categorize(null, "Rs 200 debited")).isEqualTo(Category.OTHERS)
    }
}
