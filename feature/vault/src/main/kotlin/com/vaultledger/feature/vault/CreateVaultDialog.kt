package com.vaultledger.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vaultledger.ui.common.Dimensions
import com.vaultledger.ui.common.VaultColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateVaultDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, color: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var descriptionError by remember { mutableStateOf<String?>(null) }
    var selectedColor by remember { mutableStateOf(VaultColors.defaultHex()) }

    fun validate(): Boolean {
        var valid = true
        nameError = when {
            name.isBlank() -> { valid = false; "Name is required" }
            name.length > Dimensions.MaxNameLength -> { valid = false; "Maximum ${Dimensions.MaxNameLength} characters" }
            else -> null
        }
        descriptionError = when {
            description.length > Dimensions.MaxDescriptionLength -> { valid = false; "Maximum ${Dimensions.MaxDescriptionLength} characters" }
            else -> null
        }
        return valid
    }

    fun resetErrors() {
        nameError = null
        descriptionError = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Vault") },
        text = {
            Column {
                Text(
                    text = "Enter the details for your new vault.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(Dimensions.SpacingMedium))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { error -> { Text(error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Dimensions.SpacingSmall + 4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        descriptionError = null
                    },
                    label = { Text("Description (optional)") },
                    isError = descriptionError != null,
                    supportingText = descriptionError?.let { error -> { Text(error) } },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Dimensions.SpacingMedium))
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(Dimensions.SpacingSmall))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VaultColors.all.forEachIndexed { index, color ->
                        val hex = VaultColors.hexStrings[index]
                        val isSelected = hex == selectedColor
                        Box(
                            modifier = Modifier
                                .size(Dimensions.ColorPickerItemSize)
                                .clip(CircleShape)
                                .background(color)
                                .semantics { contentDescription = "Color picker option ${index + 1}" }
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                                    }
                                )
                                .clickable {
                                    selectedColor = hex
                                    resetErrors()
                                },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (validate()) onCreate(name.trim(), description.trim(), selectedColor)
                },
                enabled = name.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
