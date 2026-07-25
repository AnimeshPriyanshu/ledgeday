package com.vaultledger.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.vaultledger.ui.common.Dimensions
import com.vaultledger.ui.util.CurrencyFormatter

@Preview(showBackground = true)
@Composable
private fun BalanceHeaderPositivePreview() {
    BalanceHeader(balance = 15000L)
}

@Preview(showBackground = true)
@Composable
private fun BalanceHeaderNegativePreview() {
    BalanceHeader(balance = -5000L)
}

@Preview(showBackground = true)
@Composable
private fun BalanceHeaderZeroPreview() {
    BalanceHeader(balance = 0L)
}

@Composable
fun BalanceHeader(
    balance: Long,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, textColor) = when {
        balance > 0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        balance < 0 -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = Dimensions.ListHorizontalPadding, vertical = Dimensions.BalanceVerticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Balance",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.7f),
            )
            Text(
                text = CurrencyFormatter.format(balance),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
        }
    }
}
