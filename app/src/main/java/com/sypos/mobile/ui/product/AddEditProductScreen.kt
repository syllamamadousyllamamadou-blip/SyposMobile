package com.sypos.mobile.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sypos.mobile.data.local.entity.ProductEntity
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    viewModel: ProductViewModel = hiltViewModel(),
    productId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    scannedBarcode: String? = null
) {
    val categories by viewModel.categories.collectAsState()
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var salePrice by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var stockQuantity by remember { mutableStateOf("") }
    var alertStock by remember { mutableStateOf("5") }
    var barcode by remember { mutableStateOf(scannedBarcode ?: "") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    // Load existing product if editing
    LaunchedEffect(productId) {
        if (productId != null && !isLoaded) {
            val existing = viewModel.getProductById(productId)
            if (existing != null) {
                name = existing.name
                salePrice = if (existing.salePrice > 0) existing.salePrice.toString() else ""
                costPrice = if (existing.costPrice > 0) existing.costPrice.toString() else ""
                stockQuantity = existing.stockQuantity.toString()
                alertStock = existing.alertStock.toString()
                barcode = existing.barcode ?: ""
                selectedCategoryId = existing.categoryId
            }
            isLoaded = true
        }
    }

    // Effect to update barcode if scanned from camera
    LaunchedEffect(scannedBarcode) {
        if (!scannedBarcode.isNullOrBlank()) {
            barcode = scannedBarcode
        }
    }

    val saleVal = salePrice.toDoubleOrNull() ?: 0.0
    val costVal = costPrice.toDoubleOrNull() ?: 0.0
    val profitVal = saleVal - costVal
    val profitMargin = if (saleVal > 0) (profitVal / saleVal) * 100 else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (productId == null) "Nouveau Produit" else "Modifier le Produit") },
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nom du produit
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom du Produit *") },
                placeholder = { Text("Ex: Coca Cola 33cl, Savon...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Catégorie
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Catégorie", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showAddCategoryDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nouvelle")
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("Sans catégorie") },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Prix
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tarification (FCFA)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = salePrice,
                            onValueChange = { salePrice = it },
                            label = { Text("Prix de vente *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = costPrice,
                            onValueChange = { costPrice = it },
                            label = { Text("Coût d'achat") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    if (saleVal > 0) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (profitVal >= 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bénéfice estimé / unité :",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (profitVal >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                                Text(
                                    text = String.format(Locale.FRANCE, "%,.0f CFA (%.1f%%)", profitVal, profitMargin),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (profitVal >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                        }
                    }
                }
            }

            // Gestion du Stock
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Stock & Alertes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = stockQuantity,
                            onValueChange = { stockQuantity = it },
                            label = { Text("Quantité en stock *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = alertStock,
                            onValueChange = { alertStock = it },
                            label = { Text("Stock d'alerte") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Code-barres
            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                label = { Text("Code-barres (Optionnel)") },
                placeholder = { Text("Scanner ou saisir manuellement") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = onNavigateToScanner) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scanner", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (name.isNotBlank() && salePrice.isNotBlank()) {
                        val product = ProductEntity(
                            id = productId ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            salePrice = salePrice.toDoubleOrNull() ?: 0.0,
                            costPrice = costPrice.toDoubleOrNull() ?: 0.0,
                            stockQuantity = stockQuantity.toIntOrNull() ?: 0,
                            alertStock = alertStock.toIntOrNull() ?: 5,
                            barcode = barcode.takeIf { it.isNotBlank() },
                            categoryId = selectedCategoryId,
                            colorHex = categories.find { it.id == selectedCategoryId }?.colorHex
                        )
                        viewModel.saveProduct(product)
                        onNavigateBack()
                    }
                },
                enabled = name.isNotBlank() && salePrice.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    if (productId == null) "Enregistrer le Produit" else "Mettre à Jour le Produit",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { newCat ->
                viewModel.addCategory(newCat)
                selectedCategoryId = newCat.id
            }
        )
    }
}
