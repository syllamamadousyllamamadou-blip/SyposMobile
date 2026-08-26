package com.sypos.mobile.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.sypos.mobile.data.local.BusinessMode
import com.sypos.mobile.util.BluetoothPrinterDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val savedSettings by viewModel.settings.collectAsState()
    val promoCodes by viewModel.promoCodes.collectAsState()

    var shopName by remember(savedSettings) { mutableStateOf(savedSettings.shopName) }
    var shopAddress by remember(savedSettings) { mutableStateOf(savedSettings.shopAddress) }
    var shopPhone by remember(savedSettings) { mutableStateOf(savedSettings.shopPhone) }
    var receiptFooter by remember(savedSettings) { mutableStateOf(savedSettings.receiptFooter) }
    var sellerName by remember(savedSettings) { mutableStateOf(savedSettings.sellerName) }
    var showPublisherSignature by remember(savedSettings) { mutableStateOf(savedSettings.showPublisherSignature) }
    var businessMode by remember(savedSettings) { mutableStateOf(savedSettings.businessMode) }
    var taxEnabled by remember(savedSettings) { mutableStateOf(savedSettings.taxEnabled) }
    var taxRatePercent by remember(savedSettings) { mutableStateOf(savedSettings.taxRatePercent.toString()) }
    var allowNegativeStock by remember(savedSettings) { mutableStateOf(savedSettings.allowNegativeStock) }
    var autoPrintReceipt by remember(savedSettings) { mutableStateOf(savedSettings.autoPrintReceipt) }
    var adminPin by remember(savedSettings) { mutableStateOf(savedSettings.adminPin) }
    var cashierPin by remember(savedSettings) { mutableStateOf(savedSettings.cashierPin) }
    var pinLockEnabled by remember(savedSettings) { mutableStateOf(savedSettings.pinLockEnabled) }
    var selectedPrinterAddress by remember(savedSettings) { mutableStateOf(savedSettings.bluetoothPrinterAddress) }
    var selectedPrinterName by remember(savedSettings) { mutableStateOf(savedSettings.bluetoothPrinterName) }

    var pairedPrinters by remember { mutableStateOf<List<BluetoothPrinterDevice>>(emptyList()) }
    var showPrinterDropdown by remember { mutableStateOf(false) }
    var isTestingPrinter by remember { mutableStateOf(false) }
    var showAddPromoDialog by remember { mutableStateOf(false) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                pairedPrinters = viewModel.getPairedPrinters(context)
            }
        }
    )

    fun refreshPrintersWithPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            if (!hasConnect || !hasScan) {
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
                return
            }
        }
        pairedPrinters = viewModel.getPairedPrinters(context)
    }

    LaunchedEffect(Unit) {
        refreshPrintersWithPermission()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres de la Boutique", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Mode Métier (Supermarché vs Restaurant)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Type d'Activité & Métier", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = businessMode == BusinessMode.SUPERMARKET,
                            onClick = { businessMode = BusinessMode.SUPERMARKET },
                            label = { Text("🛒 Supermarché / Boutique") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = businessMode == BusinessMode.RESTAURANT,
                            onClick = { businessMode = BusinessMode.RESTAURANT },
                            label = { Text("🍽️ Restaurant / Snack") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = if (businessMode == BusinessMode.SUPERMARKET)
                            "• Mode Supermarché : Encaissement ultra-rapide sans options de table, contrôle strict des stocks."
                        else
                            "• Mode Restaurant : Sélection Sur place / À emporter / Livraison avec mention imprimée sur les tickets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section 2: Profil & En-tête Boutique + Nom du Vendeur
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("En-tête des Tickets & Équipe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text("Nom du Commerce / Boutique") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = sellerName,
                        onValueChange = { sellerName = it },
                        label = { Text("Nom du Caissier / Vendeur (Affiché sur tickets)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = shopAddress,
                        onValueChange = { shopAddress = it },
                        label = { Text("Adresse / Emplacement") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = shopPhone,
                        onValueChange = { shopPhone = it },
                        label = { Text("Numéro Téléphone Contact") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = receiptFooter,
                        onValueChange = { receiptFooter = it },
                        label = { Text("Message de bas de ticket (Ex: Merci de votre visite !)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Section 3: Signature Éditeur SYPOS (Discrète)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Signature Éditeur SYPOS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Solution: SYPOS MOBILE 0758245530 (Petit texte au bas du reçu)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = showPublisherSignature,
                            onCheckedChange = { showPublisherSignature = it }
                        )
                    }
                }
            }

            // Section 4: Gestion des Codes Promo
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.LocalOffer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Codes Promo & Réductions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { showAddPromoDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ajouter")
                        }
                    }

                    if (promoCodes.isEmpty()) {
                        Text("Aucun code promo créé. Cliquez sur Ajouter pour en créer un.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        promoCodes.forEach { promo ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${promo.code} (-${promo.discountPercent.toInt()}%)", fontWeight = FontWeight.Bold)
                                        Text("Utilisé : ${promo.currentUsage} / ${promo.maxUsage} fois", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = promo.isActive,
                                            onCheckedChange = { viewModel.togglePromoCode(promo) }
                                        )
                                        IconButton(onClick = { viewModel.deletePromoCode(promo) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 5: TVA
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Application de la TVA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = taxEnabled,
                            onCheckedChange = { taxEnabled = it }
                        )
                    }

                    if (taxEnabled) {
                        OutlinedTextField(
                            value = taxRatePercent,
                            onValueChange = { taxRatePercent = it },
                            label = { Text("Taux de TVA (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Section 6: Imprimante Thermique Bluetooth
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Imprimante Ticket Bluetooth", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { refreshPrintersWithPermission() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Impression automatique", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Imprime instantanément à chaque validation de vente", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoPrintReceipt,
                            onCheckedChange = { autoPrintReceipt = it }
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                refreshPrintersWithPermission()
                                showPrinterDropdown = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedPrinterName ?: "Sélectionner une imprimante appairée",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        DropdownMenu(
                            expanded = showPrinterDropdown,
                            onDismissRequest = { showPrinterDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Aucune (Désactiver l'impression)") },
                                onClick = {
                                    selectedPrinterAddress = null
                                    selectedPrinterName = null
                                    showPrinterDropdown = false
                                }
                            )
                            pairedPrinters.forEach { printer ->
                                DropdownMenuItem(
                                    text = { Text("🖨️ ${printer.name} (${printer.address})") },
                                    onClick = {
                                        selectedPrinterAddress = printer.address
                                        selectedPrinterName = printer.name
                                        showPrinterDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    if (selectedPrinterAddress != null) {
                        Button(
                            onClick = {
                                isTestingPrinter = true
                                viewModel.testPrinter(context, selectedPrinterAddress!!) { result ->
                                    isTestingPrinter = false
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "✅ Impression de test réussie !", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "❌ Erreur : ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isTestingPrinter,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isTestingPrinter) "Test en cours..." else "Tester l'impression du ticket")
                        }
                    }
                }
            }

            // Section 7: Sécurité Code PIN
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Sécurité & Rôles (Vendeur / Admin)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = pinLockEnabled,
                            onCheckedChange = { pinLockEnabled = it }
                        )
                    }

                    if (pinLockEnabled) {
                        Text(
                            text = "Le PIN Admin (1234) protège les Paramètres, les Bilans, et TOUTES les annulations / suppressions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = adminPin,
                                onValueChange = { if (it.length <= 6) adminPin = it },
                                label = { Text("PIN Admin") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = cashierPin,
                                onValueChange = { if (it.length <= 6) cashierPin = it },
                                label = { Text("PIN Caissier") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Section Licence SYPOS Mobile
            var showLicenseDialog by remember { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Licence SYPOS Mobile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = savedSettings.licenseType,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (savedSettings.licenseExpiryDate > 0L) {
                                val exp = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRANCE).format(java.util.Date(savedSettings.licenseExpiryDate))
                                Text("Expire le : $exp", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFFC62828))
                            } else {
                                Text("Licence Illimitée (À Vie)", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF2E7D32))
                            }
                        }

                        OutlinedButton(
                            onClick = { showLicenseDialog = true },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gérer")
                        }
                    }
                }
            }

            if (showLicenseDialog) {
                com.sypos.mobile.ui.auth.LicenseDialog(
                    settings = savedSettings,
                    isInitialSetup = false,
                    onSaveLicense = { key, type, expiry ->
                        viewModel.saveLicense(key, type, expiry)
                    },
                    onDismiss = { showLicenseDialog = false }
                )
            }

            // Save Button
            Button(
                onClick = {
                    val rate = taxRatePercent.toDoubleOrNull() ?: 18.0
                    val updated = savedSettings.copy(
                        shopName = shopName.trim(),
                        shopAddress = shopAddress.trim(),
                        shopPhone = shopPhone.trim(),
                        receiptFooter = receiptFooter.trim(),
                        sellerName = sellerName.trim().ifBlank { "Vendeur 1" },
                        showPublisherSignature = showPublisherSignature,
                        businessMode = businessMode,
                        taxEnabled = taxEnabled,
                        taxRatePercent = rate,
                        allowNegativeStock = allowNegativeStock,
                        autoPrintReceipt = autoPrintReceipt,
                        adminPin = adminPin.trim().ifBlank { "1234" },
                        cashierPin = cashierPin.trim().ifBlank { "0000" },
                        pinLockEnabled = pinLockEnabled,
                        bluetoothPrinterAddress = selectedPrinterAddress,
                        bluetoothPrinterName = selectedPrinterName
                    )
                    viewModel.updateSettings(updated)
                    Toast.makeText(context, "✅ Paramètres enregistrés avec succès !", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enregistrer les Modifications", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showAddPromoDialog) {
        AddPromoCodeDialog(
            onDismiss = { showAddPromoDialog = false },
            onConfirm = { code, discount, maxUsage ->
                viewModel.addPromoCode(code, discount, maxUsage)
                showAddPromoDialog = false
            }
        )
    }
}

@Composable
fun AddPromoCodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Int) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("10") }
    var maxUsageText by remember { mutableStateOf("50") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau Code Promo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Code (Ex: SOLDES20, PROMO)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it },
                    label = { Text("Pourcentage de réduction (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maxUsageText,
                    onValueChange = { maxUsageText = it },
                    label = { Text("Nombre maximum d'utilisations") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val discount = discountText.toDoubleOrNull() ?: 10.0
                    val maxUsage = maxUsageText.toIntOrNull() ?: 50
                    if (code.isNotBlank()) {
                        onConfirm(code, discount, maxUsage)
                    }
                },
                enabled = code.isNotBlank()
            ) {
                Text("Créer le Code")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
