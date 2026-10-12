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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.sypos.mobile.data.local.BusinessMode
import com.sypos.mobile.util.BluetoothPrinterDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val savedSettings by viewModel.settings.collectAsState()
    val promoCodes by viewModel.promoCodes.collectAsState()

    var shopName by remember(savedSettings) { mutableStateOf(savedSettings.shopName) }
    var shopLogoUri by remember(savedSettings) { mutableStateOf(savedSettings.shopLogoUri) }
    var shopAddress by remember(savedSettings) { mutableStateOf(savedSettings.shopAddress) }
    var shopPhone by remember(savedSettings) { mutableStateOf(savedSettings.shopPhone) }
    var receiptFooter by remember(savedSettings) { mutableStateOf(savedSettings.receiptFooter) }
    var currency by remember(savedSettings) { mutableStateOf(savedSettings.currency) }
    var sellerName by remember(savedSettings) { mutableStateOf(savedSettings.sellerName) }
    var savedSellers by remember(savedSettings) { mutableStateOf(savedSettings.savedSellers) }
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
    var showCurrencyDropdown by remember { mutableStateOf(false) }
    var showSellerDropdown by remember { mutableStateOf(false) }
    var showAddPromoDialog by remember { mutableStateOf(false) }
    var selectedRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                selectedRestoreUri = uri
                showRestoreConfirmDialog = true
            }
        }
    )

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val file = java.io.File(context.filesDir, "shop_logo.jpg")
                    val outputStream = java.io.FileOutputStream(file)
                    inputStream?.copyTo(outputStream)
                    inputStream?.close()
                    outputStream.close()
                    shopLogoUri = file.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

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
                            label = { Text("🛒 Supermarché") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = businessMode == BusinessMode.RESTAURANT,
                            onClick = { businessMode = BusinessMode.RESTAURANT },
                            label = { Text("🍽️ Restaurant") },
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

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            label = { Text("Nom du Commerce / Boutique") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { logoPickerLauncher.launch("image/*") },
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        ) {
                            val logoFile = shopLogoUri?.let { java.io.File(it) }
                            if (logoFile != null && logoFile.exists()) {
                                val bitmap = android.graphics.BitmapFactory.decodeFile(logoFile.absolutePath)
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Logo",
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Image, contentDescription = "Logo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                Icon(Icons.Default.Image, contentDescription = "Logo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = showSellerDropdown,
                        onExpandedChange = { showSellerDropdown = !showSellerDropdown }
                    ) {
                        OutlinedTextField(
                            value = sellerName,
                            onValueChange = { sellerName = it },
                            label = { Text("Nom du Caissier Actif") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        val sellersList = savedSellers.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        if (sellersList.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = showSellerDropdown,
                                onDismissRequest = { showSellerDropdown = false }
                            ) {
                                sellersList.forEach { seller ->
                                    DropdownMenuItem(
                                        text = { Text(seller) },
                                        onClick = {
                                            sellerName = seller
                                            showSellerDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = savedSellers,
                        onValueChange = { savedSellers = it },
                        label = { Text("Liste des Caissiers (Séparés par virgule, ex: Ali, Marie)") },
                        leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Group, contentDescription = null) },
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

                    // Currency Picker
                    ExposedDropdownMenuBox(
                        expanded = showCurrencyDropdown,
                        onExpandedChange = { showCurrencyDropdown = !showCurrencyDropdown }
                    ) {
                        OutlinedTextField(
                            value = currency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Devise (Affichée sur les tickets/bilans)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCurrencyDropdown) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = showCurrencyDropdown,
                            onDismissRequest = { showCurrencyDropdown = false }
                        ) {
                            val currencies = listOf(
                                "FCFA" to "FCFA - Franc CFA (Bénin, Burkina, Côte d'Ivoire, Guinée-B., Mali, Niger, Sénégal, Togo)",
                                "FCFA " to "FCFA - Franc CFA (Cameroun, Centrafrique, Congo, Gabon, Guinée équa., Tchad)",
                                "GNF" to "GNF - Franc Guinéen (Guinée)",
                                "CDF" to "CDF - Franc Congolais (RD Congo)",
                                "MAD" to "MAD - Dirham (Maroc)",
                                "DZD" to "DZD - Dinar (Algérie)",
                                "TND" to "TND - Dinar (Tunisie)",
                                "MGA" to "MGA - Ariary (Madagascar)",
                                "BIF" to "BIF - Franc (Burundi)",
                                "RWF" to "RWF - Franc (Rwanda)",
                                "DJF" to "DJF - Franc (Djibouti)",
                                "KMF" to "KMF - Franc (Comores)",
                                "€" to "€ - Euro (Europe)",
                                "$" to "$ - Dollar (USA)"
                            )
                            currencies.forEach { (sym, desc) ->
                                DropdownMenuItem(
                                    text = { Text(desc) },
                                    onClick = {
                                        currency = sym
                                        showCurrencyDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Signature Éditeur KAYO (Discrète)
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
                                Text("Signature Éditeur KAYO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Solution: KAYO MOBILE 0758245530 (Petit texte au bas du reçu)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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


            // Section Sauvegarde & Restauration
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Sauvegarde & Restauration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "Exportez l'intégralité de vos données (catalogue, clients CRM, ventes, dépenses, paramètres) en fichier sécurisé ou restaurez-les en 1 clic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.exportBackup(context)
                                Toast.makeText(context, "📤 Préparation de la sauvegarde...", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sauvegarder", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                restoreLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restaurer", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Cloud Backup Buttons
                    var isCloudSyncing by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isCloudSyncing = true
                                viewModel.backupToFirebase(context) { success, msg ->
                                    isCloudSyncing = false
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = !isCloudSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF0EA5E9)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if(isCloudSyncing) "..." else "Cloud Backup", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                isCloudSyncing = true
                                viewModel.restoreFromFirebase(context) { success, msg ->
                                    isCloudSyncing = false
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = !isCloudSyncing,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cloud Restore", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Button(
                        onClick = {
                            Toast.makeText(context, "⏳ Synchronisation de toutes les données...", Toast.LENGTH_SHORT).show()
                            viewModel.forceSyncAllDataToCloud(context) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Forcer la Synchronisation Complète", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Section Licence KAYO Cloud
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
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Abonnement KAYO Cloud",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                            var planName by remember { mutableStateOf("Chargement...") }
                            var planColor by remember { mutableStateOf(androidx.compose.ui.graphics.Color.Gray) }
                            
                            LaunchedEffect(user?.uid) {
                                if (user != null) {
                                    com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                                        if (doc != null && doc.exists()) {
                                            val p = doc.getString("plan") ?: "TRIAL"
                                            if (p == "TRIAL") { planName = "Essai Gratuit"; planColor = androidx.compose.ui.graphics.Color(0xFFF57C00) }
                                            else if (p == "LIFETIME") { planName = "Licence à vie"; planColor = androidx.compose.ui.graphics.Color(0xFF2E7D32) }
                                            else if (p == "MONTHLY") { planName = "Mensuel"; planColor = androidx.compose.ui.graphics.Color(0xFF1976D2) }
                                            else if (p == "YEARLY") { planName = "Annuel"; planColor = androidx.compose.ui.graphics.Color(0xFF1976D2) }
                                            else if (p == "BLOCKED") { planName = "Bloqué"; planColor = androidx.compose.ui.graphics.Color(0xFFD32F2F) }
                                            else { planName = p; planColor = androidx.compose.ui.graphics.Color.Gray }
                                        }
                                    }
                                }
                            }

                            if (user != null) {
                                Text(
                                    text = user.email ?: "Compte connecté",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("Plan : $planName", style = MaterialTheme.typography.labelSmall, color = planColor)
                            } else {
                                Text("Non connecté", color = MaterialTheme.colorScheme.error)
                            }
                        }

                        OutlinedButton(
                            onClick = { 
                                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                                android.widget.Toast.makeText(context, "Déconnexion réussie.", android.widget.Toast.LENGTH_SHORT).show()
                                onLogout()
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Déconnecter", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    val rate = taxRatePercent.toDoubleOrNull() ?: 18.0
                    val updated = savedSettings.copy(
                        shopName = shopName.trim(),
                        shopLogoUri = shopLogoUri,
                        shopAddress = shopAddress.trim(),
                        shopPhone = shopPhone.trim(),
                        receiptFooter = receiptFooter.trim(),
                        currency = currency,
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
                        savedSellers = savedSellers.trim(),
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



    if (showRestoreConfirmDialog && selectedRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                selectedRestoreUri = null
            },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Confirmer la Restauration") },
            text = {
                Text("Attention : Cette action va importer et fusionner les articles, clients et ventes depuis le fichier sélectionné.\n\nVoulez-vous continuer ?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedRestoreUri?.let { uri ->
                            viewModel.restoreBackup(context, uri) { success ->
                                if (success) {
                                    Toast.makeText(context, "✅ Base de données restaurée avec succès !", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "❌ Échec de la restauration du fichier.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        showRestoreConfirmDialog = false
                        selectedRestoreUri = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Restaurer")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        selectedRestoreUri = null
                    }
                ) {
                    Text("Annuler")
                }
            }
        )
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
