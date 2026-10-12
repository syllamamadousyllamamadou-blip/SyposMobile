package com.sypos.mobile.ui.pos

import com.sypos.mobile.util.CurrencyUtils.formatAmount
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
import com.sypos.mobile.data.local.ShopSettings

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
    PaymentMethodOption(PaymentMethod.CREDIT, "À Crédit", Color(0xFFE53935), "credit"),
    PaymentMethodOption(PaymentMethod.SPLIT, "Fractionné", Color(0xFF8E24AA), "split")
)

@Composable
fun PaymentDialog(
    totalAmount: Double,
    settings: ShopSettings,
    customerName: String?,
    onDismiss: () -> Unit,
    onConfirmPayment: (PaymentMethod, Double, Double?, Double?, PaymentMethod?) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var amountReceivedText by remember { mutableStateOf(if (totalAmount > 0) String.format(Locale.US, "%.0f", totalAmount) else "") }
    
    var splitCashAmountText by remember { mutableStateOf("") }
    var splitOtherAmountText by remember { mutableStateOf("") }
    var splitOtherMethod by remember { mutableStateOf(PaymentMethod.WAVE) }
    
    var creditAcompteText by remember { mutableStateOf("") }

    val amountReceived = amountReceivedText.toDoubleOrNull() ?: 0.0
    val splitCashAmount = splitCashAmountText.toDoubleOrNull() ?: 0.0
    val splitOtherAmount = splitOtherAmountText.toDoubleOrNull() ?: 0.0
    val totalSplitReceived = splitCashAmount + splitOtherAmount

    val changeDue = when (selectedMethod) {
        PaymentMethod.SPLIT -> (totalSplitReceived - totalAmount).coerceAtLeast(0.0)
        PaymentMethod.CREDIT -> 0.0
        else -> (amountReceived - totalAmount).coerceAtLeast(0.0)
    }

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
                        text = totalAmount.formatAmount(settings.currency),
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
                if (!customerName.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Client associé : $customerName", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Payment Methods Selection Grid
                Text("Mode de règlement", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(140.dp)
                ) {
                    items(PAYMENT_METHODS) { option ->
                        val isSelected = selectedMethod == option.method
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) option.color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, option.color) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
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
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) option.color else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                // Cash specific input (Montant reçu + Quick bills)
                if (selectedMethod == PaymentMethod.CASH) {
                    Text("Montant reçu du client", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                    OutlinedTextField(
                        value = amountReceivedText,
                        onValueChange = { amountReceivedText = it },
                        label = { Text("Montant reçu") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Cash Bills shortcuts
                    Text("Billets rapides", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FCFA_BILLS.take(4).forEach { bill ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { amountReceivedText = bill.toString() }
                            ) {
                                Text(
                                    text = "$bill",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Change Due Display
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (amountReceived >= totalAmount) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
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
                                    changeDue.formatAmount(settings.currency)
                                else
                                    "-${(totalAmount - amountReceived).formatAmount(settings.currency)}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (amountReceived >= totalAmount) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                } else if (selectedMethod == PaymentMethod.SPLIT) {
                    Text("Paiement Fractionné (Espèces + Autre)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = splitCashAmountText,
                            onValueChange = { splitCashAmountText = it },
                            label = { Text("Espèces") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = splitOtherAmountText,
                            onValueChange = { splitOtherAmountText = it },
                            label = { Text("Autre Montant") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Select other method
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(PaymentMethod.WAVE, PaymentMethod.ORANGE_MONEY, PaymentMethod.CARD).forEach { meth ->
                            FilterChip(
                                selected = splitOtherMethod == meth,
                                onClick = { splitOtherMethod = meth },
                                label = { Text(meth.name, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (totalSplitReceived >= totalAmount) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (totalSplitReceived >= totalAmount) "TOTAL ATTEINT / MONNAIE À RENDRE" else "RESTE À PAYER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (totalSplitReceived >= totalAmount) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                            Text(
                                text = if (totalSplitReceived >= totalAmount)
                                    changeDue.formatAmount(settings.currency)
                                else
                                    (totalAmount - totalSplitReceived).formatAmount(settings.currency),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (totalSplitReceived >= totalAmount) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                } else if (selectedMethod == PaymentMethod.CREDIT) {
                    val creditAcompte = creditAcompteText.toDoubleOrNull() ?: 0.0
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (customerName.isNullOrBlank()) {
                                Text(
                                    text = "⚠️ Attention : Vous devez associer un client pour enregistrer une vente à crédit.",
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    text = "Vente à crédit enregistrée pour $customerName.",
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedTextField(
                                    value = creditAcompteText,
                                    onValueChange = { creditAcompteText = it },
                                    label = { Text("Acompte payé (Optionnel)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                val remainingDebt = (totalAmount - creditAcompte).coerceAtLeast(0.0)
                                Text("Le client devra la somme de ${remainingDebt.formatAmount(settings.currency)}.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val creditAcompte = creditAcompteText.toDoubleOrNull() ?: 0.0
            val canSubmit = when (selectedMethod) {
                PaymentMethod.CASH -> amountReceived >= totalAmount
                PaymentMethod.SPLIT -> totalSplitReceived >= totalAmount
                PaymentMethod.CREDIT -> !customerName.isNullOrBlank() && creditAcompte <= totalAmount
                else -> true
            }

            Button(
                onClick = {
                    val finalPaid = when (selectedMethod) {
                        PaymentMethod.CASH -> amountReceived
                        PaymentMethod.SPLIT -> totalSplitReceived
                        PaymentMethod.CREDIT -> creditAcompte
                        else -> totalAmount
                    }
                    val sCash = if (selectedMethod == PaymentMethod.SPLIT) splitCashAmount else null
                    val sOther = if (selectedMethod == PaymentMethod.SPLIT) splitOtherAmount else null
                    val sMethod = if (selectedMethod == PaymentMethod.SPLIT) splitOtherMethod else null
                    onConfirmPayment(selectedMethod, finalPaid, sCash, sOther, sMethod)
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

