package com.sypos.mobile.ui.pos

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.entity.TicketEntity
import com.sypos.mobile.data.local.entity.TicketItemEntity
import com.sypos.mobile.util.BluetoothPrinterHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReceiptDialog(
    ticket: TicketEntity,
    items: List<TicketItemEntity>,
    customerName: String?,
    settings: ShopSettings = ShopSettings(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(ticket.date))
    var isPrinting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Vente Validée !",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
            }
        },
        text = {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header Reçu
                    Text(
                        text = settings.shopName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (settings.shopAddress.isNotBlank()) {
                        Text(
                            text = settings.shopAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = "Ticket: ${ticket.ticketNumber} • $dateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (customerName != null) {
                        Text(
                            text = "Client: $customerName",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Articles list
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.quantity}x ${item.productName}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = String.format(Locale.FRANCE, "%,.0f CFA", item.total),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Subtotal, Discount & Tax
                    if (ticket.discount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Remise :", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "-${String.format(Locale.FRANCE, "%,.0f CFA", ticket.discount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (ticket.taxAmount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sous-Total HT :", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = String.format(Locale.FRANCE, "%,.0f CFA", ticket.subTotal - ticket.discount),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("TVA (${settings.taxRatePercent.toInt()}%) :", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = String.format(Locale.FRANCE, "%,.0f CFA", ticket.taxAmount),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Total Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (ticket.taxAmount > 0) "TOTAL TTC" else "TOTAL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format(Locale.FRANCE, "%,.0f CFA", ticket.totalAmount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Règlement :", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = ticket.paymentMethod?.name ?: "ESPECES",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (ticket.amountPaid > 0 || ticket.paymentMethod == com.sypos.mobile.data.local.entity.PaymentMethod.CASH) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Montant remis :", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = String.format(Locale.FRANCE, "%,.0f CFA", ticket.amountPaid),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (ticket.changeReturned > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Monnaie rendue :", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = String.format(Locale.FRANCE, "%,.0f CFA", ticket.changeReturned),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    if (settings.receiptFooter.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = settings.receiptFooter,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Bluetooth Print Button
                if (settings.bluetoothPrinterAddress != null) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                isPrinting = true
                                val result = BluetoothPrinterHelper.printTicket(
                                    context = context,
                                    deviceAddress = settings.bluetoothPrinterAddress,
                                    ticket = ticket,
                                    items = items,
                                    customerName = customerName,
                                    settings = settings
                                )
                                isPrinting = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Ticket imprimé avec succès !", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Erreur d'impression : ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isPrinting,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPrinting) "Impression..." else "Imprimer")
                    }
                }

                // WhatsApp Share Button
                Button(
                    onClick = { shareReceiptText(context, ticket, items, customerName, settings) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WhatsApp")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("Nouvelle Vente")
            }
        }
    )
}

fun shareReceiptText(
    context: Context,
    ticket: TicketEntity,
    items: List<TicketItemEntity>,
    customerName: String?,
    settings: ShopSettings = ShopSettings()
) {
    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(ticket.date))
    val sb = StringBuilder()
    sb.appendLine("🧾 *${settings.shopName.uppercase(Locale.getDefault())}*")
    if (settings.shopAddress.isNotBlank()) sb.appendLine("📍 ${settings.shopAddress}")
    if (settings.shopPhone.isNotBlank()) sb.appendLine("📞 ${settings.shopPhone}")
    sb.appendLine("--------------------------------")
    sb.appendLine("Ticket N°: ${ticket.ticketNumber}")
    sb.appendLine("Date: $dateStr")
    if (customerName != null) {
        sb.appendLine("Client: $customerName")
    }
    sb.appendLine("--------------------------------")
    items.forEach { item ->
        sb.appendLine("${item.quantity}x ${item.productName} = ${String.format(Locale.FRANCE, "%,.0f CFA", item.total)}")
    }
    sb.appendLine("--------------------------------")
    if (ticket.discount > 0) {
        sb.appendLine("Remise: -${String.format(Locale.FRANCE, "%,.0f CFA", ticket.discount)}")
    }
    if (ticket.taxAmount > 0) {
        sb.appendLine("Sous-Total HT: ${String.format(Locale.FRANCE, "%,.0f CFA", ticket.subTotal - ticket.discount)}")
        sb.appendLine("TVA (${settings.taxRatePercent.toInt()}%): ${String.format(Locale.FRANCE, "%,.0f CFA", ticket.taxAmount)}")
    }
    val totalLabel = if (ticket.taxAmount > 0) "*TOTAL TTC" else "*TOTAL"
    sb.appendLine("$totalLabel : ${String.format(Locale.FRANCE, "%,.0f CFA", ticket.totalAmount)}*")
    sb.appendLine("Paiement: ${ticket.paymentMethod?.name ?: "ESPECES"}")
    if (ticket.amountPaid > 0 || ticket.paymentMethod == com.sypos.mobile.data.local.entity.PaymentMethod.CASH) {
        sb.appendLine("Montant remis: ${String.format(Locale.FRANCE, "%,.0f CFA", ticket.amountPaid)}")
    }
    if (ticket.changeReturned > 0) {
        sb.appendLine("Monnaie rendue: ${String.format(Locale.FRANCE, "%,.0f CFA", ticket.changeReturned)}")
    }
    if (settings.receiptFooter.isNotBlank()) {
        sb.appendLine("\n${settings.receiptFooter}")
    }

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, sb.toString())
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Partager le ticket")
    context.startActivity(shareIntent)
}
