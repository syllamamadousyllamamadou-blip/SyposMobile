package com.sypos.mobile.ui.customer

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sypos.mobile.data.local.entity.CustomerEntity

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.sypos.mobile.data.local.entity.TicketStatus
import com.sypos.mobile.util.CurrencyUtils.formatAmount
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomerHistoryDialog(
    customer: CustomerEntity,
    viewModel: CustomerViewModel,
    currency: String,
    onDismiss: () -> Unit
) {
    val tickets by remember(customer.id) { viewModel.getTicketsForCustomer(customer.id) }.collectAsState(initial = emptyList())
    val df = SimpleDateFormat("dd/MM/yy HH:mm", Locale.FRANCE)
    val context = LocalContext.current

    val exportCustomerData = {
        try {
            val json = JSONObject()
            json.put("id", customer.id)
            json.put("name", customer.name)
            json.put("phone", customer.phone)
            json.put("debt", customer.debt)
            json.put("totalSpent", customer.totalSpent)
            json.put("visitCount", customer.visitCount)
            json.put("notes", customer.notes)

            val ticketsArray = JSONArray()
            tickets.forEach { ticket ->
                val tJson = JSONObject()
                tJson.put("ticketNumber", ticket.ticketNumber)
                tJson.put("date", ticket.date)
                tJson.put("totalAmount", ticket.totalAmount)
                tJson.put("amountPaid", ticket.amountPaid)
                tJson.put("status", ticket.status.name)
                tJson.put("itemsSummary", ticket.itemsSummary)
                ticketsArray.put(tJson)
            }
            json.put("tickets", ticketsArray)

            val fileName = "client_${customer.name.replace(" ", "_")}.json"
            val file = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(file).use { output ->
                output.write(json.toString(4).toByteArray())
            }
            Toast.makeText(context, "Fichier enregistré: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erreur d'exportation", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Historique de ${customer.name}", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Total Dépensé : ${customer.totalSpent.formatAmount(currency)}", fontWeight = FontWeight.Bold)
                Text("Nombre de Visites : ${customer.visitCount}")
                if (customer.debt > 0) {
                    Text("Dette Actuelle : ${customer.debt.formatAmount(currency)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                if (tickets.isNotEmpty()) {
                    Text("Achats Récents & Dettes :", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    tickets.forEach { ticket ->
                        val isCredit = ticket.status == TicketStatus.CREDIT
                        val amountDue = if (isCredit) ticket.totalAmount - ticket.amountPaid else 0.0
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isCredit) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "${df.format(Date(ticket.date))} - ${ticket.ticketNumber}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCredit) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Montant: ${ticket.totalAmount.formatAmount(currency)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (isCredit) {
                                    Text(
                                        text = "Reste à payer: ${amountDue.formatAmount(currency)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (!customer.notes.isNullOrBlank()) {
                    Text("Notes :", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(customer.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        },
        dismissButton = {
            TextButton(onClick = exportCustomerData) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Exporter JSON")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
