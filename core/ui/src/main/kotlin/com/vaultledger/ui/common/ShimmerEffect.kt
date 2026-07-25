package com.vaultledger.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    return Brush.linearGradient(
        colors = listOf(
            surfaceVariant,
            surface,
            surfaceVariant,
        ),
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim + 200f, 0f),
    )
}

@Composable
private fun ShimmerLine(
    modifier: Modifier = Modifier,
) {
    val brush = shimmerBrush()
    Box(
        modifier = modifier
            .height(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(brush),
    )
}

@Composable
fun ShimmerCardItem(
    modifier: Modifier = Modifier,
) {
    val brush = shimmerBrush()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.CardPadding),
        ) {
            // Title line
            ShimmerLine(modifier = Modifier.fillMaxWidth(0.55f))
            Spacer(modifier = Modifier.height(Dimensions.SpacingXSmall))
            // Description line 1
            ShimmerLine(modifier = Modifier.fillMaxWidth(0.85f))
            Spacer(modifier = Modifier.height(Dimensions.SpacingXSmall))
            // Description line 2
            ShimmerLine(modifier = Modifier.fillMaxWidth(0.4f))
        }
    }
}

@Composable
fun ShimmerVaultItem(
    modifier: Modifier = Modifier,
) {
    val brush = shimmerBrush()
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Color dot
            Box(
                modifier = Modifier
                    .size(Dimensions.ColorDotSize)
                    .clip(CircleShape)
                    .background(brush),
            )
            Spacer(modifier = Modifier.width(Dimensions.SpacingSmall + 4.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Vault name
                ShimmerLine(modifier = Modifier.fillMaxWidth(0.5f))
                Spacer(modifier = Modifier.height(Dimensions.SpacingXSmall))
                // Vault description
                ShimmerLine(modifier = Modifier.fillMaxWidth(0.7f))
            }
            Spacer(modifier = Modifier.width(Dimensions.SpacingSmall))
            // Balance
            ShimmerLine(modifier = Modifier.width(60.dp))
        }
    }
}

@Composable
fun ShimmerTransactionItem(
    modifier: Modifier = Modifier,
) {
    val brush = shimmerBrush()
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Category/type icon placeholder
            Box(
                modifier = Modifier
                    .size(Dimensions.IconSizeSmall)
                    .clip(CircleShape)
                    .background(brush),
            )
            Spacer(modifier = Modifier.width(Dimensions.SpacingSmall + 4.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Description
                ShimmerLine(modifier = Modifier.fillMaxWidth(0.6f))
                Spacer(modifier = Modifier.height(Dimensions.SpacingXSmall))
                // Date
                ShimmerLine(modifier = Modifier.fillMaxWidth(0.35f))
            }
            Spacer(modifier = Modifier.width(Dimensions.SpacingSmall))
            // Amount
            ShimmerLine(modifier = Modifier.width(50.dp))
        }
    }
}

@Composable
fun ShimmerList(
    modifier: Modifier = Modifier,
    itemCount: Int = 5,
    itemType: ShimmerItemType = ShimmerItemType.CARD,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimensions.ListHorizontalPadding),
        contentPadding = PaddingValues(vertical = Dimensions.ListContentPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
    ) {
        items(itemCount) {
            when (itemType) {
                ShimmerItemType.CARD -> ShimmerCardItem()
                ShimmerItemType.VAULT -> ShimmerVaultItem()
                ShimmerItemType.TRANSACTION -> ShimmerTransactionItem()
            }
        }
    }
}

enum class ShimmerItemType {
    CARD,
    VAULT,
    TRANSACTION,
}

@Preview(showBackground = true)
@Composable
private fun ShimmerCardPreview() {
    MaterialTheme {
        ShimmerCardItem(modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun ShimmerVaultPreview() {
    MaterialTheme {
        ShimmerVaultItem(modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun ShimmerTransactionPreview() {
    MaterialTheme {
        ShimmerTransactionItem(modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun ShimmerListPreview() {
    MaterialTheme {
        ShimmerList(itemCount = 3)
    }
}
