package com.sypos.mobile.ui.tools

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.entity.ProductEntity
import com.sypos.mobile.util.DeliveryNoteData
import com.sypos.mobile.util.LabelPrintOptions
import java.util.*

enum class ToolTab(val title: String) {
    BARCODE_LABEL("Étiquettes & Code-Barres"),
    PRODUCT_SHEET("Fiche Produit WhatsApp"),
    DELIVERY_NOTE("Bordereau Livraison")
}

enum class LabelPreset(val label: String) {
    BARCODE_ONLY("Code-barres Seul"),
    BARCODE_WITH_SERIAL("Code-barres + N° Série"),
    NAME_AND_PRICE("Nom + Prix Seul"),
    FULL_LABEL("Complet (Nom, Prix, Code)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel = hiltViewModel(),
    onNavigateToScanner: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val products by viewModel.products.collectAsState()
    var selectedTab by remember { mutableStateOf(ToolTab.BARCODE_LABEL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Outils Professionnels", fontWeight = FontWeight.Bold) },
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
        ) {
            // Segmented Tab Selector
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ToolTab.values().forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ToolTab.values().size)
                    ) {
                        Text(
                            text = when (tab) {
                                ToolTab.BARCODE_LABEL -> "Étiquettes"
                                ToolTab.PRODUCT_SHEET -> "Fiches Promo"
                                ToolTab.DELIVERY_NOTE -> "Livraisons"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                when (selectedTab) {
                    ToolTab.BARCODE_LABEL -> BarcodeLabelSection(
                        products = products,
                        settings = settings,
                        onPrint = { name, price, barcode, options ->
                            viewModel.printBarcodeLabel(context, name, price, barcode, options) { result ->
                                if (result.isSuccess) {
                                    Toast.makeText(context, "✅ Étiquette imprimée !", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "❌ Erreur : ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                    ToolTab.PRODUCT_SHEET -> ProductSheetSection(
                        products = products,
                        settings = settings
                    )
                    ToolTab.DELIVERY_NOTE -> DeliveryNoteSection(
                        settings = settings,
                        onPrintDelivery = { data ->
                            viewModel.printDeliveryNote(context, data) { result ->
                                if (result.isSuccess) {
                                    Toast.makeText(context, "✅ Bordereau de livraison imprimé !", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "❌ Erreur : ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BarcodeLabelSection(
    products: List<ProductEntity>,
    settings: ShopSettings,
    onPrint: (String, Double, String?, LabelPrintOptions) -> Unit
) {
    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var customName by remember { mutableStateOf("") }
    var customPrice by remember { mutableStateOf("") }
    var customBarcode by remember { mutableStateOf("") }
    var customSerial by remember { mutableStateOf("") }

    var selectedPreset by remember { mutableStateOf(LabelPreset.FULL_LABEL) }
    var printShopHeader by remember { mutableStateOf(false) }
    var printProductName by remember { mutableStateOf(true) }
    var printPrice by remember { mutableStateOf(true) }
    var printBarcodeRaster by remember { mutableStateOf(true) }
    var printSerialNumber by remember { mutableStateOf(true) }

    var showProductDropdown by remember { mutableStateOf(false) }

    fun applyPreset(preset: LabelPreset) {
        selectedPreset = preset
        when (preset) {
            LabelPreset.BARCODE_ONLY -> {
                printShopHeader = false
                printProductName = false
                printPrice = false
                printBarcodeRaster = true
                printSerialNumber = false
            }
            LabelPreset.BARCODE_WITH_SERIAL -> {
                printShopHeader = false
                printProductName = false
                printPrice = false
                printBarcodeRaster = true
                printSerialNumber = true
            }
            LabelPreset.NAME_AND_PRICE -> {
                printShopHeader = true
                printProductName = true
                printPrice = true
                printBarcodeRaster = false
                printSerialNumber = false
            }
            LabelPreset.FULL_LABEL -> {
                printShopHeader = true
                printProductName = true
                printPrice = true
                printBarcodeRaster = true
                printSerialNumber = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Card 1: Configuration Form
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Générateur d'Étiquettes Intelligent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // Select from Catalog Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showProductDropdown = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedProduct?.name ?: "Choisir un produit du catalogue")
                    }

                    DropdownMenu(
                        expanded = showProductDropdown,
                        onDismissRequest = { showProductDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        products.forEach { prod ->
                            DropdownMenuItem(
                                text = { Text("${prod.name} (${String.format(Locale.FRANCE, "%,.0f CFA", prod.salePrice)})") },
                                onClick = {
                                    selectedProduct = prod
                                    customName = prod.name
                                    customPrice = prod.salePrice.toString()
                                    customBarcode = prod.barcode ?: ""
                                    customSerial = prod.barcode ?: ""
                                    showProductDropdown = false
                                }
                            )
                        }
                    }
                }

                // Preset Buttons
                Text("Modèle d'Étiquette :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedPreset == LabelPreset.BARCODE_ONLY,
                        onClick = { applyPreset(LabelPreset.BARCODE_ONLY) },
                        label = { Text("Code Seul", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedPreset == LabelPreset.BARCODE_WITH_SERIAL,
                        onClick = { applyPreset(LabelPreset.BARCODE_WITH_SERIAL) },
                        label = { Text("+ N° Série", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedPreset == LabelPreset.NAME_AND_PRICE,
                        onClick = { applyPreset(LabelPreset.NAME_AND_PRICE) },
                        label = { Text("Rayon (Prix)", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedPreset == LabelPreset.FULL_LABEL,
                        onClick = { applyPreset(LabelPreset.FULL_LABEL) },
                        label = { Text("Complet", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Nom de l'Article") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customPrice,
                        onValueChange = { customPrice = it },
                        label = { Text("Prix Vente (CFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = customBarcode,
                        onValueChange = {
                            customBarcode = it
                            if (customSerial.isBlank()) customSerial = it
                        },
                        label = { Text("Code-barres / Donnée") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = customSerial,
                    onValueChange = { customSerial = it },
                    label = { Text("Numéro de Série / Réf Texte à afficher") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Advanced Element Toggles
                HorizontalDivider()
                Text("Éléments à imprimer sur l'étiquette :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Image Code-barres Scannable (Bitmap)", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = printBarcodeRaster, onCheckedChange = { printBarcodeRaster = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Numéro de Série / Réf Texte", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = printSerialNumber, onCheckedChange = { printSerialNumber = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Nom de l'Article", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = printProductName, onCheckedChange = { printProductName = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Prix de Vente", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = printPrice, onCheckedChange = { printPrice = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("En-tête Nom de Boutique", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = printShopHeader, onCheckedChange = { printShopHeader = it })
                }
            }
        }

        // Preview Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Aperçu de l'Étiquette Thermique", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (printShopHeader && settings.shopName.isNotBlank()) {
                    Text(settings.shopName.uppercase(Locale.getDefault()), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }

                if (printProductName && customName.isNotBlank()) {
                    Text(customName, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }

                val priceNum = customPrice.toDoubleOrNull() ?: 0.0
                if (printPrice && priceNum > 0) {
                    val formatted = String.format(Locale.US, "%,.0f", priceNum).replace(',', ' ') + " CFA"
                    Text(formatted, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }

                if (printBarcodeRaster && customBarcode.isNotBlank()) {
                    Text("|||||||||||||||||||||||||||||||||||||", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }

                val serialToDisplay = customSerial.ifBlank { customBarcode }
                if (printSerialNumber && serialToDisplay.isNotBlank()) {
                    Text("* $serialToDisplay *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Print Button
        val canPrint = (printBarcodeRaster && customBarcode.isNotBlank()) ||
                (printProductName && customName.isNotBlank()) ||
                (printPrice && (customPrice.toDoubleOrNull() ?: 0.0) > 0) ||
                (printSerialNumber && customSerial.isNotBlank())

        Button(
            onClick = {
                val price = customPrice.toDoubleOrNull() ?: 0.0
                val options = LabelPrintOptions(
                    printShopHeader = printShopHeader,
                    printProductName = printProductName,
                    printPrice = printPrice,
                    printBarcodeRaster = printBarcodeRaster,
                    printSerialNumber = printSerialNumber,
                    serialNumber = customSerial.ifBlank { null }
                )
                onPrint(customName, price, customBarcode.ifBlank { null }, options)
            },
            enabled = canPrint,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.Print, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Imprimer l'Étiquette sur Imprimante Thermique", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProductSheetSection(
    products: List<ProductEntity>,
    settings: ShopSettings
) {
    val context = LocalContext.current
    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var title by remember { mutableStateOf("") }
    var normalPrice by remember { mutableStateOf("") }
    var promoPrice by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var showProductDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Fiche Produit WhatsApp & Réseaux Sociaux", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showProductDropdown = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedProduct?.name ?: "Remplir depuis le catalogue")
                    }

                    DropdownMenu(
                        expanded = showProductDropdown,
                        onDismissRequest = { showProductDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        products.forEach { prod ->
                            DropdownMenuItem(
                                text = { Text(prod.name) },
                                onClick = {
                                    selectedProduct = prod
                                    title = prod.name
                                    normalPrice = prod.salePrice.toString()
                                    showProductDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre de l'Offre / Nom du Produit") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = normalPrice,
                        onValueChange = { normalPrice = it },
                        label = { Text("Prix Normal CFA") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = promoPrice,
                        onValueChange = { promoPrice = it },
                        label = { Text("Prix Promo CFA") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Détails / Caractéristiques / Avantages") },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // WhatsApp Share Button
        Button(
            onClick = {
                val sb = StringBuilder()
                sb.appendLine("🔥 *${title.uppercase(Locale.getDefault())}* 🔥")
                sb.appendLine("🏢 *${settings.shopName}*")
                if (settings.shopAddress.isNotBlank()) sb.appendLine("📍 ${settings.shopAddress}")
                sb.appendLine("--------------------------------")
                if (promoPrice.isNotBlank()) {
                    val pNorm = normalPrice.toDoubleOrNull() ?: 0.0
                    val pPromo = promoPrice.toDoubleOrNull() ?: 0.0
                    val pNormStr = String.format(Locale.US, "%,.0f", pNorm).replace(',', ' ') + " CFA"
                    val pPromoStr = String.format(Locale.US, "%,.0f", pPromo).replace(',', ' ') + " CFA"
                    sb.appendLine("❌ Prix Habituel : ~$pNormStr~")
                    sb.appendLine("✅ *PRIX PROMO : $pPromoStr*")
                } else if (normalPrice.isNotBlank()) {
                    val pNorm = normalPrice.toDoubleOrNull() ?: 0.0
                    val pNormStr = String.format(Locale.US, "%,.0f", pNorm).replace(',', ' ') + " CFA"
                    sb.appendLine("💰 *Prix : $pNormStr*")
                }
                if (description.isNotBlank()) {
                    sb.appendLine("--------------------------------")
                    sb.appendLine("📝 *Description :*")
                    sb.appendLine(description.trim())
                }
                sb.appendLine("--------------------------------")
                if (settings.shopPhone.isNotBlank()) {
                    sb.appendLine("📞 Commandez vite au : *${settings.shopPhone}*")
                }

                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, sb.toString())
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Partager la Fiche Produit"))
            },
            enabled = title.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Partager l'Offre sur WhatsApp & Réseaux", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DeliveryNoteSection(
    settings: ShopSettings,
    onPrintDelivery: (DeliveryNoteData) -> Unit
) {
    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }
    var deliveryAddress by remember { mutableStateOf("") }
    var itemsSummary by remember { mutableStateOf("") }
    var amountToCollect by remember { mutableStateOf("") }
    var deliveryFee by remember { mutableStateOf("1000") }
    var note by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Bordereau de Livraison / Colis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    label = { Text("Nom du Destinataire / Client") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = recipientPhone,
                    onValueChange = { recipientPhone = it },
                    label = { Text("Numéro Téléphone Destinataire") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = deliveryAddress,
                    onValueChange = { deliveryAddress = it },
                    label = { Text("Adresse / Lieu de Livraison") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = itemsSummary,
                    onValueChange = { itemsSummary = it },
                    label = { Text("Articles / Colis (Ex: 2x Robes, 1x Sac)") },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = amountToCollect,
                        onValueChange = { amountToCollect = it },
                        label = { Text("Montant Colis (CFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = deliveryFee,
                        onValueChange = { deliveryFee = it },
                        label = { Text("Frais Livraison (CFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Instructions Livreur (Optionnel)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Print Delivery Slip Button
        Button(
            onClick = {
                val amt = amountToCollect.toDoubleOrNull() ?: 0.0
                val fee = deliveryFee.toDoubleOrNull() ?: 0.0
                val data = DeliveryNoteData(
                    recipientName = recipientName.trim(),
                    recipientPhone = recipientPhone.trim(),
                    deliveryAddress = deliveryAddress.trim(),
                    itemsSummary = itemsSummary.trim(),
                    amountToCollect = amt,
                    deliveryFee = fee,
                    note = note.ifBlank { null }
                )
                onPrintDelivery(data)
            },
            enabled = recipientName.isNotBlank() && recipientPhone.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.LocalShipping, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Imprimer Bordereau de Livraison", fontWeight = FontWeight.Bold)
        }
    }
}
