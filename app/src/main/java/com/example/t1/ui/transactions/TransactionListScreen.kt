package com.example.t1.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.t1.domain.model.Category
import com.example.t1.domain.model.PaymentMethod
import com.example.t1.domain.model.TransactionFilter
import com.example.t1.domain.model.TransactionType
import com.example.t1.ui.common.TransactionRow

@Composable
fun TransactionListScreen(
    onTransactionClick: (Long) -> Unit,
    viewModel: TransactionListViewModel = hiltViewModel(),
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        FilterPanel(
            filter = filter,
            accounts = accounts,
            onFilterChange = viewModel::updateFilter,
        )
        HorizontalDivider()
        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (filter.isActive) "No transactions match these filters."
                           else "No transactions yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(transactions, key = { it.id }) { tx ->
                    TransactionRow(transaction = tx, onClick = { onTransactionClick(tx.id) })
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(
    filter: TransactionFilter,
    accounts: List<String>,
    onFilterChange: (TransactionFilter) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        // Type row
        FilterRow(label = "Type") {
            TypeChip("All", filter.type == null) { onFilterChange(filter.copy(type = null)) }
            TypeChip("Debit", filter.type == TransactionType.DEBIT) {
                onFilterChange(filter.copy(type = TransactionType.DEBIT))
            }
            TypeChip("Credit", filter.type == TransactionType.CREDIT) {
                onFilterChange(filter.copy(type = TransactionType.CREDIT))
            }
        }
        // Payment method row
        FilterRow(label = "Method") {
            TypeChip("All", filter.paymentMethod == null) {
                onFilterChange(filter.copy(paymentMethod = null))
            }
            PaymentMethod.entries.forEach { method ->
                TypeChip(method.displayName, filter.paymentMethod == method) {
                    onFilterChange(filter.copy(paymentMethod = method))
                }
            }
        }
        // Category row
        FilterRow(label = "Category") {
            TypeChip("All", filter.category == null) {
                onFilterChange(filter.copy(category = null))
            }
            Category.entries.forEach { cat ->
                TypeChip(cat.displayName, filter.category == cat) {
                    onFilterChange(filter.copy(category = cat))
                }
            }
        }
        // Account row — only shown when multiple accounts exist
        if (accounts.size > 1) {
            FilterRow(label = "Account") {
                TypeChip("All", filter.accountNumber == null) {
                    onFilterChange(filter.copy(accountNumber = null))
                }
                accounts.forEach { acc ->
                    TypeChip(acc, filter.accountNumber == acc) {
                        onFilterChange(filter.copy(accountNumber = acc))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(end = 8.dp),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    )
}
