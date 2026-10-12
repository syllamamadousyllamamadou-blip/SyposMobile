package com.sypos.mobile.ui.customer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.entity.CustomerEntity
import com.sypos.mobile.util.CurrencyUtils.formatAmount
import java.net.URLEncoder

@Composable
fun AddEditCustomerDialog(
    customer: CustomerEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (CustomerEntity) -> Unit
) {
    var name by remember { mutableStateOf(customer?.name ?: "") }
    var phone by remember { mutableStateOf(customer?.phone ?: "") }
    var email by remember { mutableStateOf(customer?.email ?: "") }
    var notes by remember { mutableStateOf(customer?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (customer == null) "Nouveau Client (CRM)" else "Modifier la Fiche Client",
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
                    label = { Text("Numéro WhatsApp / Téléphone *") },
                    placeholder = { Text("Ex: 77 123 45 67") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optionnel)") },
                    placeholder = { Text("Ex: client@gmail.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes & Préférences") },
                    placeholder = { Text("Ex: Préfère livraison le matin") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        val updated = customer?.copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            email = email.trim().takeIf { it.isNotBlank() },
                            notes = notes.trim().takeIf { it.isNotBlank() }
                        ) ?: CustomerEntity(
                            name = name.trim(),
                            phone = phone.trim(),
                            email = email.trim().takeIf { it.isNotBlank() },
                            notes = notes.trim().takeIf { it.isNotBlank() }
                        )
                        onConfirm(updated)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (customer == null) "Ajouter au CRM" else "Enregistrer")
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
fun CustomerRelanceDialog(
    context: Context,
    customer: CustomerEntity,
    settings: ShopSettings,
    onDismiss: () -> Unit
) {
    fun sendWhatsApp(message: String) {
        val cleanPhone = (customer.phone ?: "").replace(Regex("[^0-9]"), "")
        val encodedMsg = URLEncoder.encode(message, "UTF-8")
        val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Relance WhatsApp — ${customer.name}", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choisissez un modèle de message personnalisé :",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Fidelite / Remerciement
                OutlinedButton(
                    onClick = {
                        val msg = "Bonjour ${customer.name} ! 😊\nToute l'équipe de *${settings.shopName}* vous remercie chaleureusement pour votre fidélité (${customer.visitCount} commandes chez nous). C'est un réel plaisir de vous servir ! À très bientôt en boutique. 🛍️"
                        sendWhatsApp(msg)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🎁 Remerciement & Fidélité", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                }

                // 2. Promo / Nouveaux arrivages
                OutlinedButton(
                    onClick = {
                        val msg = "Bonjour ${customer.name} ! 📢\nDe nouveaux arrivages et promotions sont disponibles cette semaine chez *${settings.shopName}*.\nPassez nous voir ou contactez-nous pour réserver vos articles favoris ! ✨"
                        sendWhatsApp(msg)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = Color(0xFF0284C7))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📢 Nouveaux Arrivages & Offres", color = Color(0xFF0284C7), fontWeight = FontWeight.Bold)
                }

                // 3. Reactivation Inactif
                OutlinedButton(
                    onClick = {
                        val msg = "Bonjour ${customer.name} ! 💖\nVous nous manquez chez *${settings.shopName}* ! Nous avons rentré de nouveaux produits qui pourraient vous intéresser. Au plaisir de vous revoir très bientôt !"
                        sendWhatsApp(msg)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFF59E0B))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("⏳ Réactivation (Client Inactif)", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

@Composable
fun DebtPaymentDialog(
    customer: CustomerEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Payer la dette", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Dette actuelle : ${customer.debt.formatAmount("FCFA")}")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Montant payé") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val amount = amountText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    if (amount > 0) {
                        onConfirm(amount)
                    }
                },
                enabled = amount > 0 && amount <= customer.debt,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Valider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
