package com.sypos.mobile.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.ui.product.BarcodeScannerScreen
import com.sypos.mobile.util.DeviceSecurityHelper
import com.sypos.mobile.util.LicenseManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseDialog(
    settings: ShopSettings,
    isInitialSetup: Boolean = false,
    onSaveLicense: (String, String, Long) -> Unit,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val deviceId = remember { DeviceSecurityHelper.getDeviceId(context) }
    
    var keyInput by remember { mutableStateOf(settings.licenseKey) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }

    val isCurrentlyActive = settings.isLicensed && !LicenseManager.isLicenseExpired(settings.licenseExpiryDate)

    if (showQrScanner) {
        Dialog(
            onDismissRequest = { showQrScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                BarcodeScannerScreen(
                    onBarcodeScanned = { scannedToken ->
                        showQrScanner = false
                        keyInput = scannedToken
                        val status = LicenseManager.validateKey(context, scannedToken)
                        if (status.isValid) {
                            onSaveLicense(scannedToken, status.licenseType, status.expiryTimestamp)
                            Toast.makeText(context, "✅ ${status.message}", Toast.LENGTH_LONG).show()
                            if (!isInitialSetup) onDismiss()
                        } else {
                            errorMessage = status.message
                        }
                    },
                    onNavigateBack = {
                        showQrScanner = false
                    }
                )
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = { if (!isInitialSetup) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isInitialSetup, dismissOnClickOutside = !isInitialSetup),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = if (isInitialSetup) "Activation SYPOS Mobile" else "Gestion de la Licence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Protection Cryptographique Sécurisée",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Status Banner if already licensed
                if (isCurrentlyActive) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            Column {
                                Text("Licence Activée & Valide", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 13.sp)
                                Text(settings.licenseType, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1B5E20))
                                if (settings.licenseExpiryDate > 0L) {
                                    val expStr = SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(Date(settings.licenseExpiryDate))
                                    Text("Expire le : $expStr", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                } else {
                                    Text("Validité : Permanente (À vie)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                } else if (isInitialSetup) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Bienvenue sur SYPOS Mobile ! Pour déverrouiller votre caisse, transmettez votre ID Appareil à votre administrateur pour recevoir votre clé officielle.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // 2. Hardware Device ID Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "📱 ID UNIQUE DE CET APPAREIL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Display formatted Device ID
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = deviceId,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        // Action buttons: Copy & WhatsApp
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("SYPOS Device ID", deviceId)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "ID Appareil copié dans le presse-papier !", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copier", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    val message = "Bonjour SYPOS, voici mon ID Appareil pour activer ma licence :\n\n📱 *ID :* ${deviceId}\n🏪 *Boutique :* ${settings.shopName}"
                                    val sendIntent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(message))
                                    }
                                    try {
                                        context.startActivity(sendIntent)
                                    } catch (e: Exception) {
                                        // Generic share fallback
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, message)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Envoyer mon ID Appareil"))
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 3. Activation Methods (QR Code Scanner + Manual input)
                Text(
                    text = "Méthodes d'activation :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Scan QR Code Button
                FilledTonalButton(
                    onClick = {
                        errorMessage = null
                        showQrScanner = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scanner le QR Code de Licence")
                }

                // Manual Input
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        errorMessage = null
                    },
                    label = { Text("Ou coller la Clé de Licence (SYP1...)") },
                    placeholder = { Text("SYP1.eyJkZ...") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        if (keyInput.isNotBlank()) {
                            IconButton(onClick = { keyInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer")
                            }
                        }
                    },
                    maxLines = 3,
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
                    val status = LicenseManager.validateKey(context, keyInput)
                    if (status.isValid) {
                        onSaveLicense(keyInput.trim(), status.licenseType, status.expiryTimestamp)
                        Toast.makeText(context, "✅ ${status.message}", Toast.LENGTH_LONG).show()
                        if (!isInitialSetup) onDismiss()
                    } else {
                        errorMessage = status.message
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (isCurrentlyActive) "Mettre à jour" else "Activer la Caisse")
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
