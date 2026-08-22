package com.sypos.mobile.ui.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.util.LicenseManager
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LicenseDialog(
    settings: ShopSettings,
    isInitialSetup: Boolean = false,
    onSaveLicense: (String, String, Long) -> Unit,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf(settings.licenseKey) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isInitialSetup) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(if (isInitialSetup) "Installation SYPOS — Activation" else "Activation Licence SYPOS", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isInitialSetup) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Bienvenue sur SYPOS Mobile ! Veuillez sélectionner votre période de validité ou saisir votre clé pour initialiser votre système de caisse.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else if (settings.isLicensed) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            Column {
                                Text("Application Activée", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Text(settings.licenseType, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1B5E20))
                                if (settings.licenseExpiryDate > 0L) {
                                    val expStr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(settings.licenseExpiryDate))
                                    Text("Expire le : $expStr", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Sélectionnez une option d'activation rapide ou saisissez une clé :",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )

                // Quick License Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { keyInput = "SYPOS-TRIAL-30D" },
                        label = { Text("Essai 30j") },
                        modifier = Modifier.weight(1f)
                    )
                    SuggestionChip(
                        onClick = { keyInput = "SYPOS-1AN-2026" },
                        label = { Text("1 An") },
                        modifier = Modifier.weight(1f)
                    )
                    SuggestionChip(
                        onClick = { keyInput = "SYPOS-PRO-VIP-2026" },
                        label = { Text("Illimité") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it.uppercase()
                        errorMessage = null
                    },
                    label = { Text("Clé d'Activation") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val status = LicenseManager.validateKey(keyInput)
                    if (status.isValid) {
                        onSaveLicense(keyInput.trim(), status.licenseType, status.expiryTimestamp)
                        Toast.makeText(context, "✅ ${status.message}", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        errorMessage = status.message
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Activer & Démarrer")
            }
        },
        dismissButton = if (!isInitialSetup) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Fermer")
                }
            }
        } else null
    )
}
