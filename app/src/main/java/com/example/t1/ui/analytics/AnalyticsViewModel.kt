package com.example.t1.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.t1.domain.model.Category
import com.example.t1.domain.model.CategorySpend
import com.example.t1.domain.model.MonthlySpend
import com.example.t1.domain.model.PaymentMethod
import com.example.t1.domain.model.TransactionType
import com.example.t1.domain.repository.TransactionRepository
import com.example.t1.domain.usecase.GetMonthlyTrendUseCase
import com.example.t1.domain.usecase.GetSpendingByCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val getSpendingByCategory: GetSpendingByCategoryUseCase,
    private val getMonthlyTrend: GetMonthlyTrendUseCase,
    private val repository: TransactionRepository,
) : ViewModel() {

    private val _startMillis = MutableStateFlow(startOfCurrentMonth())
    private val _endMillis = MutableStateFlow(System.currentTimeMillis())

    val startMillis: StateFlow<Long> = _startMillis.asStateFlow()
    val endMillis: StateFlow<Long> = _endMillis.asStateFlow()

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory.asStateFlow()

    val spendingByCategory: StateFlow<List<CategorySpend>> =
        combine(_startMillis, _endMillis) { s, e -> s to e }
            .flatMapLatest { (start, end) -> getSpendingByCategory(start, end) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredSpending: StateFlow<List<CategorySpend>> =
        combine(spendingByCategory, _selectedCategory) { items, cat ->
            if (cat == null) items else items.filter { it.category == cat }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalSpend: StateFlow<Double> = spendingByCategory
        .map { it.sumOf { spend -> spend.totalAmount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val totalCredit: StateFlow<Double> =
        combine(_startMillis, _endMillis) { s, e -> s to e }
            .flatMapLatest { (start, end) ->
                repository.observeAll().map { transactions ->
                    transactions
                        .filter { it.type == TransactionType.CREDIT && it.timestamp in start..end }
                        .sumOf { it.amount }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val paymentBreakdown: StateFlow<List<Pair<PaymentMethod, Double>>> =
        combine(_startMillis, _endMillis) { s, e -> s to e }
            .flatMapLatest { (start, end) ->
                repository.observeAll().map { transactions ->
                    transactions
                        .filter { it.type == TransactionType.DEBIT && it.timestamp in start..end }
                        .groupBy { it.paymentMethod }
                        .map { (method, txs) -> method to txs.sumOf { it.amount } }
                        .filter { it.second > 0 }
                        .sortedByDescending { it.second }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthlyTrend: StateFlow<List<MonthlySpend>> = getMonthlyTrend()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDateRange(startMillis: Long, endMillis: Long) {
        _startMillis.value = startMillis
        _endMillis.value = endMillis
    }

    fun setCategory(category: Category?) {
        _selectedCategory.value = category
    }

    private fun startOfCurrentMonth(): Long =
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
