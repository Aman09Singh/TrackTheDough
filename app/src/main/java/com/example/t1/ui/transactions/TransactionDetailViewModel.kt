package com.example.t1.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.t1.domain.model.Category
import com.example.t1.domain.model.Transaction
import com.example.t1.domain.repository.TransactionRepository
import com.example.t1.domain.usecase.UpdateTransactionCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TransactionRepository,
    private val updateCategory: UpdateTransactionCategoryUseCase,
) : ViewModel() {

    private val transactionId: Long = checkNotNull(savedStateHandle["id"])

    val transaction: StateFlow<Transaction?> = repository.observeById(transactionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCategory(category: Category) {
        viewModelScope.launch {
            updateCategory(transactionId, category)
        }
    }
}
