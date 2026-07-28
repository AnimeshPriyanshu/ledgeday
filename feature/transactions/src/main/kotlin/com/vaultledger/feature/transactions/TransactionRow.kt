package com.vaultledger.feature.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import com.vaultledger.ui.common.Dimensions
import com.vaultledger.ui.util.CurrencyFormatter
import com.vaultledger.ui.util.DateFormatter

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
    val isInflow = transaction.type == TransactionType.INFLOW
    val amountColor by animateColorAsState(
        targetValue = if (isInflow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        animationSpec = tween(durationMillis = 300),
        label = "amountColor",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (isInflow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        animationSpec = tween(durationMillis = 300),
        label = "indicatorColor",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimensions.ListHorizontalPadding, end = Dimensions.ListHorizontalPadding, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(indicatorColor.copy(alpha = 0.6f)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
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
            Text(
                text = formatTransactionAmount(transaction.type, transaction.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = amountColor,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Dimensions.ListHorizontalPadding),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
    }
}

private fun formatTransactionAmount(type: TransactionType, amount: Long): String {
    val prefix = if (type == TransactionType.INFLOW) "+" else "-"
    return "$prefix${CurrencyFormatter.format(amount)}"
}
