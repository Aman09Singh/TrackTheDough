package com.example.t1.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.t1.domain.model.Category
import com.example.t1.domain.model.MonthlySpend
import com.example.t1.ui.common.creditGreen
import com.example.t1.ui.common.toDateOnlyString
import com.example.t1.ui.common.toRupeeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val startMillis by viewModel.startMillis.collectAsStateWithLifecycle()
    val endMillis by viewModel.endMillis.collectAsStateWithLifecycle()
    val filteredSpending by viewModel.filteredSpending.collectAsStateWithLifecycle()
    val spendingByCategory by viewModel.spendingByCategory.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val paymentBreakdown by viewModel.paymentBreakdown.collectAsStateWithLifecycle()
    val monthlyTrend by viewModel.monthlyTrend.collectAsStateWithLifecycle()
    val totalSpend by viewModel.totalSpend.collectAsStateWithLifecycle()
    val totalCredit by viewModel.totalCredit.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DateRangePickerDialog(
            startMillis = startMillis,
            endMillis = endMillis,
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                viewModel.setDateRange(start, end)
                showDatePicker = false
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DateRangeHeader(
                startMillis = startMillis,
                endMillis = endMillis,
                onChangeClick = { showDatePicker = true },
            )
        }
        item {
            SpendSummaryCard(totalDebit = totalSpend, totalCredit = totalCredit)
        }
        item {
            SectionCard(title = "Spending by Category") {
                CategoryDropdown(
                    selected = selectedCategory,
                    options = Category.entries,
                    onSelect = viewModel::setCategory,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (filteredSpending.isEmpty()) {
                    Text(
                        if (selectedCategory != null)
                            "No debit transactions in this category for the selected period."
                        else
                            "No debit transactions in this period.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    LabeledBarChart(
                        items = filteredSpending.map {
                            Triple(it.category.displayName, it.totalAmount, MaterialTheme.colorScheme.primary)
                        },
                    )
                }
            }
        }
        if (paymentBreakdown.isNotEmpty()) {
            item {
                SectionCard(title = "Payment Methods") {
                    LabeledBarChart(
                        items = paymentBreakdown.map { (method, amount) ->
                            Triple(method.displayName, amount, MaterialTheme.colorScheme.secondary)
                        },
                    )
                }
            }
        }
        if (monthlyTrend.any { it.totalDebit > 0 || it.totalCredit > 0 }) {
            item {
                SectionCard(title = "Monthly Trend (6 months)") {
                    MonthlyTrendChart(months = monthlyTrend)
                }
            }
        }
    }
}

@Composable
private fun DateRangeHeader(
    startMillis: Long,
    endMillis: Long,
    onChangeClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Period", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text(
                "${startMillis.toDateOnlyString()} – ${endMillis.toDateOnlyString()}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        OutlinedButton(onClick = onChangeClick) {
            Text("Change")
        }
    }
}

@Composable
private fun SpendSummaryCard(totalDebit: Double, totalCredit: Double) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Total Debited",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    totalDebit.toRupeeString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Total Credited",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    totalCredit.toRupeeString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = creditGreen,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: Category?,
    options: List<Category>,
    onSelect: (Category?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.displayName ?: "All Categories",
            onValueChange = {},
            readOnly = true,
            label = { Text("Filter by category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All Categories") },
                onClick = { onSelect(null); expanded = false },
            )
            options.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.displayName) },
                    onClick = { onSelect(cat); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun LabeledBarChart(items: List<Triple<String, Double, Color>>) {
    val max = items.maxOfOrNull { it.second } ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, value, color) ->
            val fraction = (value / max).toFloat().coerceIn(0f, 1f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.width(108.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(color, RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    text = value.toRupeeString(),
                    modifier = Modifier
                        .width(84.dp)
                        .padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun MonthlyTrendChart(months: List<MonthlySpend>) {
    val maxDebit = months.maxOfOrNull { it.totalDebit } ?: 1.0
    val maxCredit = months.maxOfOrNull { it.totalCredit } ?: 1.0
    val max = maxOf(maxDebit, maxCredit, 1.0)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            months.forEach { month ->
                val debitFraction = (month.totalDebit / max).toFloat().coerceIn(0f, 1f)
                val creditFraction = (month.totalCredit / max).toFloat().coerceIn(0f, 1f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(debitFraction)
                                .background(
                                    MaterialTheme.colorScheme.error,
                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(creditFraction)
                                .background(
                                    creditGreen,
                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                ),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = month.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(color = MaterialTheme.colorScheme.error, label = "Debit")
            LegendDot(color = creditGreen, label = "Credit")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    startMillis: Long,
    endMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = startMillis,
        initialSelectedEndDateMillis = endMillis,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    val end = (state.selectedEndDateMillis ?: start) + 86_399_999L
                    onConfirm(start, end)
                },
                enabled = state.selectedStartDateMillis != null,
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}
