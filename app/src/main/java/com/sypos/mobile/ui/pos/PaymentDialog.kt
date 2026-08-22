package com.sypos.mobile.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sypos.mobile.data.local.entity.PaymentMethod
import java.util.Locale

val FCFA_BILLS = listOf(500, 1000, 2000, 5000, 10000, 20000)

data class PaymentMethodOption(
    val method: PaymentMethod,
    val label: String,
    val color: Color,
    val iconName: String
)

val PAYMENT_METHODS = listOf(
    PaymentMethodOption(PaymentMethod.CASH, "Espèces", Color(0xFF10B981), "cash"),
    PaymentMethodOption(PaymentMethod.WAVE, "Wave", Color(0xFF1E88E5), "wave"),
    PaymentMethodOption(PaymentMethod.ORANGE_MONEY, "Orange Money", Color(0xFFFF6D00), "orange"),
    PaymentMethodOption(PaymentMethod.MTN, "MTN MoMo", Color(0xFFFFD600), "mtn"),
    PaymentMethodOption(PaymentMethod.MOOV, "Moov Money", Color(0xFF0091EA), "moov"),
    PaymentMethodOption(PaymentMethod.CARD, "Carte Bancaire", Color(0xFF5C6BC0), "card"),
    PaymentMethodOption(PaymentMethod.CREDIT, "Crédit / Dette", Color(0xFFE53935), "credit")
)

@Composable
fun PaymentDialog(
    totalAmount: Double,
    customerName: String?,
    onDismiss: () -> Unit,
    onConfirmPayment: (PaymentMethod, Double) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var amountReceivedText by remember { mutableStateOf(if (totalAmount > 0) String.format(Locale.US, "%.0f", totalAmount) else "") }

    val amountReceived = amountReceivedText.toDoubleOrNull() ?: 0.0
    val changeDue = (amountReceived - totalAmount).coerceAtLeast(0.0)
    val isCredit = selectedMethod == PaymentMethod.CREDIT
    val isExactPayment = selectedMethod != PaymentMethod.CASH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Encaissement", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = String.format(Locale.FRANCE, "%,.0f CFA", totalAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Payment Methods Selection Grid
                Text("Mode de règlement", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    items(PAYMENT_METHODS) { option ->
                        val isSelected = selectedMethod == option.method
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) option.color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, option.color) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clickable {
                                    selectedMethod = option.method
                                    if (option.method != PaymentMethod.CASH) {
                                        amountReceivedText = String.format(Locale.US, "%.0f", totalAmount)
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) option.color else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                // If Cash selected: Show input + Quick Bills + Change Returned
                if (selectedMethod == PaymentMethod.CASH) {
                    OutlinedTextField(
                        value = amountReceivedText,
                        onValueChange = { amountReceivedText = it },
                        label = { Text("Montant Reçu du Client (CFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Bills Shortcut
                    Text("Suggestions de billets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FCFA_BILLS.take(4).forEach { bill ->
                            SuggestionChip(
                                onClick = { amountReceivedText = bill.toString() },
                                label = { Text(String.format(Locale.FRANCE, "%,d", bill)) }
                            )
                        }
                    }

                    // Monnaie à rendre Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (amountReceived >= totalAmount) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (amountReceived >= totalAmount) "MONNAIE À RENDRE" else "MONTANT INSUFFISANT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (amountReceived >= totalAmount) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (amountReceived >= totalAmount)
                                    String.format(Locale.FRANCE, "%,.0f CFA", changeDue)
                                else
                                    String.format(Locale.FRANCE, "-%,.0f CFA", totalAmount - amountReceived),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (amountReceived >= totalAmount) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                } else if (selectedMethod == PaymentMethod.CREDIT) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Vente à crédit / Dette",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (customerName != null) "Client associé : $customerName (la dette lui sera affectée)."
                                else "⚠️ Aucun client sélectionné. Veuillez sélectionner un client dans le panier avant de valider le crédit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canSubmit = when (selectedMethod) {
                PaymentMethod.CASH -> amountReceived >= totalAmount
                PaymentMethod.CREDIT -> !customerName.isNullOrBlank()
                else -> true
            }

            Button(
                onClick = {
                    val finalPaid = if (selectedMethod == PaymentMethod.CASH) amountReceived else totalAmount
                    onConfirmPayment(selectedMethod, finalPaid)
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Valider la Vente")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
