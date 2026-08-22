package com.sypos.mobile.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sypos.mobile.data.local.entity.CustomerEntity
import java.util.Locale

@Composable
fun AddEditCustomerDialog(
    customer: CustomerEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (CustomerEntity) -> Unit
) {
    var name by remember { mutableStateOf(customer?.name ?: "") }
    var phone by remember { mutableStateOf(customer?.phone ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (customer == null) "Nouveau Client" else "Modifier le Client",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom complet du client *") },
                    placeholder = { Text("Ex: Moussa Diop") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Numéro de téléphone") },
                    placeholder = { Text("Ex: 77 123 45 67") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val updated = customer?.copy(name = name.trim(), phone = phone.trim().takeIf { it.isNotBlank() })
                            ?: CustomerEntity(name = name.trim(), phone = phone.trim().takeIf { it.isNotBlank() }, totalDebt = 0.0)
                        onConfirm(updated)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (customer == null) "Ajouter" else "Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun SettleDebtDialog(
    customer: CustomerEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.0f", customer.totalDebt)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Règlement de dette : ${customer.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Dette actuelle : ${String.format(Locale.FRANCE, "%,.0f CFA", customer.totalDebt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Montant remboursé (CFA)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick full amount shortcut
                TextButton(onClick = { amountText = customer.totalDebt.toInt().toString() }) {
                    Text("Payer la totalité (${String.format(Locale.FRANCE, "%,.0f CFA", customer.totalDebt)})")
                }
            }
        },
        confirmButton = {
            val amount = amountText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    if (amount > 0) {
                        onConfirm(amount)
                        onDismiss()
                    }
                },
                enabled = amount > 0,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Encaisser le règlement")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
