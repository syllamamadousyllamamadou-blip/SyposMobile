package com.sypos.mobile.ui.report

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
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
import com.sypos.mobile.util.BluetoothPrinterHelper
import com.sypos.mobile.util.ZReportData
import java.util.Locale

@Composable
fun ZReportDialog(
    report: ZReportData,
    settings: ShopSettings,
    onPrint: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Assessment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Rapport Z (Clôture Caisse)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
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
                    Text(
                        text = settings.shopName.ifBlank { "SYPOS COMMERCE" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Date de clôture : ${report.dateText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Chiffre d'Affaires & Ventes
                    ZReportRow("Chiffre d'Affaires", String.format(Locale.FRANCE, "%,.0f CFA", report.totalSales), isBold = true)
                    ZReportRow("Nombre de ventes", "${report.ticketsCount} tickets")
                    ZReportRow("Total Dépenses", "-${String.format(Locale.FRANCE, "%,.0f CFA", report.totalExpenses)}", valueColor = MaterialTheme.colorScheme.error)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("Ventilation des Encaissements :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    ZReportRow("• Espèces (Cash)", String.format(Locale.FRANCE, "%,.0f CFA", report.cashSales))
                    if (report.waveSales > 0) {
                        ZReportRow("• Wave", String.format(Locale.FRANCE, "%,.0f CFA", report.waveSales))
                    }
                    if (report.orangeMoneySales > 0) {
                        ZReportRow("• Orange Money", String.format(Locale.FRANCE, "%,.0f CFA", report.orangeMoneySales))
                    }
                    if (report.mtnSales > 0) {
                        ZReportRow("• MTN MoMo", String.format(Locale.FRANCE, "%,.0f CFA", report.mtnSales))
                    }
                    if (report.moovSales > 0) {
                        ZReportRow("• Moov Money", String.format(Locale.FRANCE, "%,.0f CFA", report.moovSales))
                    }
                    if (report.cardSales > 0) {
                        ZReportRow("• Carte Bancaire", String.format(Locale.FRANCE, "%,.0f CFA", report.cardSales))
                    }
                    if (report.creditSales > 0) {
                        ZReportRow("• Ventes à Crédit", String.format(Locale.FRANCE, "%,.0f CFA", report.creditSales))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Net Cash in Drawer
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            ZReportRow(
                                "ESPECES EN CAISSE",
                                String.format(Locale.FRANCE, "%,.0f CFA", report.netCashInDrawer),
                                isBold = true,
                                valueColor = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "(Cash total encaissé - Dépenses cash)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Print Button
                if (settings.bluetoothPrinterAddress != null) {
                    FilledTonalButton(
                        onClick = onPrint,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Imprimer")
                    }
                }

                // WhatsApp Share Button
                Button(
                    onClick = { shareZReportText(context, report, settings) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WhatsApp")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

@Composable
fun ZReportRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}

fun shareZReportText(
    context: Context,
    report: ZReportData,
    settings: ShopSettings
) {
    val sb = StringBuilder()
    sb.appendLine("📊 *RAPPORT Z - CLÔTURE DE CAISSE*")
    sb.appendLine("*${settings.shopName.uppercase(Locale.getDefault())}*")
    if (settings.shopAddress.isNotBlank()) sb.appendLine("📍 ${settings.shopAddress}")
    sb.appendLine("Date: ${report.dateText}")
    sb.appendLine("--------------------------------")
    sb.appendLine("💰 *Chiffre d'Affaires : ${String.format(Locale.FRANCE, "%,.0f CFA", report.totalSales)}*")
    sb.appendLine("🧾 Ventes réalisées : ${report.ticketsCount} tickets")
    sb.appendLine("📉 Total Dépenses : -${String.format(Locale.FRANCE, "%,.0f CFA", report.totalExpenses)}")
    sb.appendLine("--------------------------------")
    sb.appendLine("*VENTILATION DES ENCAISSEMENTS :*")
    sb.appendLine("💵 Espèces (Cash) : ${String.format(Locale.FRANCE, "%,.0f CFA", report.cashSales)}")
    if (report.waveSales > 0) sb.appendLine("🌊 Wave : ${String.format(Locale.FRANCE, "%,.0f CFA", report.waveSales)}")
    if (report.orangeMoneySales > 0) sb.appendLine("🍊 Orange Money : ${String.format(Locale.FRANCE, "%,.0f CFA", report.orangeMoneySales)}")
    if (report.mtnSales > 0) sb.appendLine("💛 MTN MoMo : ${String.format(Locale.FRANCE, "%,.0f CFA", report.mtnSales)}")
    if (report.moovSales > 0) sb.appendLine("💙 Moov Money : ${String.format(Locale.FRANCE, "%,.0f CFA", report.moovSales)}")
    if (report.cardSales > 0) sb.appendLine("💳 Carte Bancaire : ${String.format(Locale.FRANCE, "%,.0f CFA", report.cardSales)}")
    if (report.creditSales > 0) sb.appendLine("📝 Ventes à Crédit : ${String.format(Locale.FRANCE, "%,.0f CFA", report.creditSales)}")
    sb.appendLine("--------------------------------")
    sb.appendLine("🔒 *ESPÈCES EN CAISSE : ${String.format(Locale.FRANCE, "%,.0f CFA", report.netCashInDrawer)}*")
    sb.appendLine("_(Cash encaissé - Dépenses en espèces)_")

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, sb.toString())
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Partager le Rapport Z")
    context.startActivity(shareIntent)
}
