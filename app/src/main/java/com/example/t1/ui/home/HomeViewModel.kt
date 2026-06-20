package com.example.t1.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.t1.domain.model.Transaction
import com.example.t1.domain.repository.TransactionRepository
import com.example.t1.domain.usecase.ImportSmsHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class HomeTimeRange(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    ALL("All Time");

    fun cutoffMillis(): Long {
        val cal = Calendar.getInstance()
        return when (this) {
            TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            THIS_WEEK -> cal.timeInMillis - 7L * 24 * 60 * 60 * 1000
            THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            ALL -> 0L
        }
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val importSmsHistory: ImportSmsHistoryUseCase,
    private val repository: TransactionRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _timeRange = MutableStateFlow(HomeTimeRange.TODAY)
    val timeRange: StateFlow<HomeTimeRange> = _timeRange.asStateFlow()

    val transactions: StateFlow<List<Transaction>> =
        combine(repository.observeAll(), _timeRange) { all, range ->
            all.filter { it.timestamp >= range.cutoffMillis() }
                .distinctBy { it.id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTimeRange(range: HomeTimeRange) {
        _timeRange.value = range
    }

    fun startImportIfNeeded() {
        viewModelScope.launch {
            if (hasSmsPermissions()) {
                try {
                    importSmsHistory()
                } catch (_: Exception) {
                    // Import failure is non-fatal; live SMS capture still works
                }
            }
        }
    }

    private fun hasSmsPermissions(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED
}
