package com.example.t1.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.t1.domain.model.AccountSummary
import com.example.t1.domain.model.Transaction
import com.example.t1.domain.usecase.GetAccountSummaryUseCase
import com.example.t1.domain.usecase.ImportSmsHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val totalDebit: Double,
        val totalCredit: Double,
        val recentTransactions: List<Transaction>,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val importSmsHistory: ImportSmsHistoryUseCase,
    private val getAccountSummary: GetAccountSummaryUseCase,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getAccountSummary().collect { summary ->
                _uiState.value = HomeUiState.Ready(
                    totalDebit = summary.totalDebit,
                    totalCredit = summary.totalCredit,
                    recentTransactions = summary.recentTransactions,
                )
            }
        }
    }

    fun startImportIfNeeded() {
        viewModelScope.launch {
            val alreadyImported =
                dataStore.data.first()[ImportSmsHistoryUseCase.SMS_IMPORTED_KEY] ?: false
            if (!alreadyImported && hasSmsPermissions()) {
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
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
}
