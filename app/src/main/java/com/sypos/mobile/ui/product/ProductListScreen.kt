package com.sypos.mobile.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sypos.mobile.data.local.UserRole
import com.sypos.mobile.data.local.entity.ProductEntity
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    viewModel: ProductViewModel = hiltViewModel(),
    onAddProductClick: () -> Unit,
    onEditProductClick: (String) -> Unit,
    onNavigateToScanner: () -> Unit = {},
    scannedBarcode: String? = null
) {
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var productToAdjustStock by remember { mutableStateOf<ProductEntity?>(null) }

    LaunchedEffect(scannedBarcode) {
        if (!scannedBarcode.isNullOrBlank()) {
            viewModel.onSearchQueryChange(scannedBarcode)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catalogue & Stock", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                actions = {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(onClick = { com.sypos.mobile.util.ExportHelper.exportProductsToCsv(context, products) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Exporter CSV")
                    }
                    IconButton(onClick = { showAddCategoryDialog = true }) {
                        Icon(Icons.Default.Category, contentDescription = "Gérer Catégories")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProductClick,
                icon = { Icon(Icons.Filled.Add, contentDescription = "Ajouter Produit") },
                text = { Text("Nouveau Produit") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar with Camera Barcode Scanner
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = { Text("Rechercher par nom ou code-barres...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer")
                            }
                        }
                        IconButton(onClick = onNavigateToScanner) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Stock Valuation Summary Card
            val totalSaleVal = products.sumOf { it.stockQuantity.coerceAtLeast(0) * it.salePrice }
            val totalPurchaseVal = products.sumOf { it.stockQuantity.coerceAtLeast(0) * it.costPrice }
            val totalStockQty = products.sumOf { it.stockQuantity.coerceAtLeast(0) }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Valeur Totale du Stock (Vente)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = String.format(Locale.FRANCE, "%,.0f CFA", totalSaleVal),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Articles / Pièces", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "$totalStockQty pièces (${products.size} réf)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Category Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { viewModel.onCategorySelect(null) },
                        label = { Text("Tous (${products.size})") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                items(categories) { cat ->
                    val isSelected = selectedCategoryId == cat.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onCategorySelect(if (isSelected) null else cat.id) },
                        label = { Text(cat.name) },
                        leadingIcon = {
                            if (cat.colorHex != null) {
                                val color = try {
                                    Color(android.graphics.Color.parseColor(cat.colorHex))
                                } catch (e: Exception) {
                                    Color.Gray
                                }
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "Aucun produit dans le catalogue." else "Aucun résultat trouvé.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(products, key = { it.id }) { product ->
                        val category = categories.find { it.id == product.categoryId }
                        ProductListItem(
                            product = product,
                            categoryName = category?.name,
                            categoryColorHex = category?.colorHex,
                            onEditClick = { onEditProductClick(product.id) },
                            onStockAdjustClick = { productToAdjustStock = product },
                            onDeleteClick = { productToDelete = product }
                        )
                    }
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { viewModel.addCategory(it) }
        )
    }

    val settings by viewModel.settings.collectAsState()

    productToDelete?.let { product ->
        if (settings.pinLockEnabled) {
            com.sypos.mobile.ui.auth.PinAuthDialog(
                title = "Autorisation Admin pour Supprimer un Produit",
                correctPin = settings.adminPin,
                onDismiss = { productToDelete = null },
                onSuccess = {
                    viewModel.deleteProduct(product)
                    productToDelete = null
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { productToDelete = null },
                title = { Text("Supprimer le produit ?") },
                text = { Text("Voulez-vous vraiment supprimer \"${product.name}\" du catalogue ?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteProduct(product)
                            productToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Supprimer")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { productToDelete = null }) {
                        Text("Annuler")
                    }
                }
            )
        }
    }

    productToAdjustStock?.let { product ->
        StockAdjustDialog(
            product = product,
            onDismiss = { productToAdjustStock = null },
            onConfirm = { delta ->
                viewModel.updateStock(product.id, delta)
                productToAdjustStock = null
            }
        )
    }
}

@Composable
fun ProductListItem(
    product: ProductEntity,
    categoryName: String?,
    categoryColorHex: String?,
    onEditClick: () -> Unit,
    onStockAdjustClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isOutOfStock = product.stockQuantity <= 0
    val isLowStock = product.stockQuantity in 1..product.alertStock

    val badgeColor = when {
        isOutOfStock -> Color(0xFFEF4444)
        isLowStock -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val badgeText = when {
        isOutOfStock -> "Épuisé"
        isLowStock -> "Stock faible (${product.stockQuantity})"
        else -> "En stock (${product.stockQuantity})"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val defaultContainer = MaterialTheme.colorScheme.primaryContainer
            val tagColor = remember(categoryColorHex, defaultContainer) {
                if (categoryColorHex != null) {
                    try {
                        Color(android.graphics.Color.parseColor(categoryColorHex))
                    } catch (e: Exception) {
                        defaultContainer
                    }
                } else defaultContainer
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tagColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ShoppingBag,
                    contentDescription = null,
                    tint = tagColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (categoryName != null) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.FRANCE, "%,.0f CFA", product.salePrice),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Quick actions
            Row {
                IconButton(onClick = onStockAdjustClick) {
                    Icon(Icons.Default.AddBusiness, contentDescription = "Réapprovisionner", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun StockAdjustDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var quantityStr by remember { mutableStateOf("") }
    var isAddition by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajuster le stock : ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Stock actuel : ${product.stockQuantity}", style = MaterialTheme.typography.bodyMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isAddition,
                        onClick = { isAddition = true },
                        label = { Text("Entrée (+)") },
                        shape = RoundedCornerShape(8.dp)
                    )
                    FilterChip(
                        selected = !isAddition,
                        onClick = { isAddition = false },
                        label = { Text("Sortie (-)") },
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it },
                    label = { Text("Quantité") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 5, 10, 20).forEach { qty ->
                        SuggestionChip(
                            onClick = { quantityStr = qty.toString() },
                            label = { Text("+$qty") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityStr.toIntOrNull() ?: 0
                    if (qty > 0) {
                        onConfirm(if (isAddition) qty else -qty)
                    }
                },
                enabled = (quantityStr.toIntOrNull() ?: 0) > 0
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
