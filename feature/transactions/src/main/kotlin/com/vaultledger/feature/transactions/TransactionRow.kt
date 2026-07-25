package com.vaultledger.feature.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import com.vaultledger.ui.common.Dimensions
import com.vaultledger.ui.util.CurrencyFormatter
import com.vaultledger.ui.util.DateFormatter

@OptIn(ExperimentalFoundationApi::class)
@Preview(showBackground = true)
@Composable
private fun TransactionRowInflowPreview() {
    TransactionRow(
        transaction = Transaction(
            id = "1",
            vaultId = "vault-1",
            type = TransactionType.INFLOW,
            amount = 15000L,
            description = "Salary deposit",
            createdAt = System.currentTimeMillis(),
        ),
        onClick = {},
        onLongClick = {},
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Preview(showBackground = true)
@Composable
private fun TransactionRowOutflowPreview() {
    TransactionRow(
        transaction = Transaction(
            id = "2",
            vaultId = "vault-1",
            type = TransactionType.OUTFLOW,
            amount = 3500L,
            description = "Grocery shopping",
            createdAt = System.currentTimeMillis(),
        ),
        onClick = {},
        onLongClick = {},
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    transaction: Transaction,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = Dimensions.ListHorizontalPadding, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = DateFormatter.format(transaction.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(Dimensions.SpacingSmall + 4.dp))
            Text(
                text = formatTransactionAmount(transaction.type, transaction.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (transaction.type == TransactionType.INFLOW) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = Dimensions.ListHorizontalPadding),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

private fun formatTransactionAmount(type: TransactionType, amount: Long): String {
    val prefix = if (type == TransactionType.INFLOW) "+" else "-"
    return "$prefix${CurrencyFormatter.format(amount)}"
}
