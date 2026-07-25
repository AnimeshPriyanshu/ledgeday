package com.vaultledger.feature.transactions

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaultledger.domain.model.Transaction
import com.vaultledger.ui.util.DateFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionList(
    transactions: List<Transaction>,
    onTransactionClick: (String) -> Unit,
    onTransactionLongClick: (Transaction) -> Unit,
    onAddTransactionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = remember(transactions) {
        transactions.groupBy { dateKey(it.createdAt) }
            .entries
            .sortedByDescending { it.key }
            .flatMap { (date, txns) ->
                listOf(DateHeader(date)) + txns.sortedByDescending { it.createdAt }
            }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        items(grouped, key = { item ->
            when (item) {
                is DateHeader -> "header_${item.label}"
                is Transaction -> "txn_${item.id}"
            }
        }) { item ->
            when (item) {
                is DateHeader -> {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                is Transaction -> {
                    TransactionRow(
                        transaction = item,
                        onClick = { onTransactionClick(item.id) },
                        onLongClick = { onTransactionLongClick(item) },
                    )
                }
            }
        }
    }
}

private data class DateHeader(val label: String)

private val sectionFormatter = DateTimeFormatter
    .ofPattern("EEEE, MMMM d, yyyy")
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())

private fun dateKey(epochMillis: Long): String {
    return sectionFormatter.format(Instant.ofEpochMilli(epochMillis))
}
