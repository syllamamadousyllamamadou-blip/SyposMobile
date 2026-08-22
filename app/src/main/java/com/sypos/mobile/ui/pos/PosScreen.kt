package com.sypos.mobile.ui.pos

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sypos.mobile.data.local.entity.CategoryEntity
import com.sypos.mobile.data.local.entity.ProductEntity
import com.sypos.mobile.data.local.entity.TicketEntity
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    viewModel: PosViewModel = hiltViewModel(),
    onNavigateToScanner: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAddProductWithBarcode: (String) -> Unit = {},
    onLockApp: () -> Unit = {},
    scannedBarcode: String? = null,
    onBarcodeProcessed: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val heldTickets by viewModel.heldTickets.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showCartSheet by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showHeldTicketsDialog by remember { mutableStateOf(false) }

    // Auto-dismiss floating top notification after 2.5s
    LaunchedEffect(uiState.notificationBanner) {
        if (uiState.notificationBanner != null) {
            delay(2500)
            viewModel.clearNotification()
        }
    }

    // Auto-add product when barcode scanned
    LaunchedEffect(scannedBarcode) {
        if (!scannedBarcode.isNullOrBlank()) {
            viewModel.onBarcodeScanned(scannedBarcode)
            onBarcodeProcessed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(settings.shopName.ifBlank { "SYPOS Caisse" }, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                actions = {
                    // Barcode Scanner button
                    IconButton(onClick = onNavigateToScanner) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner Code-barres", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Held Tickets Badge Button
                    BadgedBox(
                        badge = {
                            if (heldTickets.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text("${heldTickets.size}")
                                }
                            }
                        }
                    ) {
                        IconButton(onClick = { showHeldTicketsDialog = true }) {
                            Icon(Icons.Default.PauseCircleFilled, contentDescription = "Tickets en attente")
                        }
                    }

                    // Quick Lock Button (if PIN enabled)
                    if (settings.pinLockEnabled) {
                        IconButton(onClick = onLockApp) {
                            Icon(Icons.Default.Lock, contentDescription = "Verrouiller la Caisse", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Settings Button
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres")
                    }
                }
            )
        },
        bottomBar = {
            // Floating Checkout Bar at bottom if items in cart
            AnimatedVisibility(
                visible = uiState.cartItems.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${uiState.totalItemsCount} article(s)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(Locale.FRANCE, "%,.0f CFA", uiState.totalAmount),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = { showCartSheet = true },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Voir le Panier", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Discrete Floating Top Notification Banner (Doesn't hide products or cart)
                AnimatedVisibility(
                    visible = uiState.notificationBanner != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.notificationBanner ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            IconButton(
                                onClick = { viewModel.clearNotification() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Search Bar with Camera Barcode Scanner
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    placeholder = { Text("Rechercher un produit ou scanner...") },
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

                // Category Chips Carousel
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { viewModel.onCategorySelect(null) },
                            label = { Text("Tous les produits") },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    items(categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { viewModel.onCategorySelect(cat.id) },
                            label = { Text(cat.name) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Product Grid
                if (products.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Aucun produit trouvé.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = if (uiState.cartItems.isNotEmpty()) 88.dp else 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(products, key = { it.id }) { product ->
                            val cartItem = uiState.cartItems.find { it.product.id == product.id }
                            val category = categories.find { it.id == product.categoryId }
                            PosProductCard(
                                product = product,
                                category = category,
                                quantityInCart = cartItem?.quantity ?: 0,
                                allowNegativeStock = settings.allowNegativeStock,
                                onAddToCart = { viewModel.addToCart(product) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Unrecognized Barcode Dialog
    uiState.unrecognizedBarcode?.let { unknownCode ->
        AlertDialog(
            onDismissRequest = { viewModel.clearUnrecognizedBarcode() },
            icon = { Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Produit Non Répertorié", fontWeight = FontWeight.Bold) },
            text = {
                Text("Le code-barres \"$unknownCode\" n'est pas encore enregistré dans votre catalogue. Souhaitez-vous créer ce produit maintenant ?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearUnrecognizedBarcode()
                        onNavigateToAddProductWithBarcode(unknownCode)
                    }
                ) {
                    Text("Créer le produit")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearUnrecognizedBarcode() }) {
                    Text("Annuler")
                }
            }
        )
    }

    var showHoldNoteDialog by remember { mutableStateOf(false) }
    var showClearCartPinDialog by remember { mutableStateOf(false) }
    var itemToRemoveWithPin by remember { mutableStateOf<String?>(null) }

    // Cart Bottom Sheet
    if (showCartSheet) {
        CartBottomSheet(
            uiState = uiState,
            businessMode = settings.businessMode,
            customers = customers,
            onIncreaseQuantity = { viewModel.increaseQuantity(it) },
            onDecreaseQuantity = { viewModel.decreaseQuantity(it) },
            onRemoveItem = { productId ->
                if (settings.pinLockEnabled) {
                    itemToRemoveWithPin = productId
                } else {
                    viewModel.removeFromCart(productId)
                }
            },
            onOrderTypeSelect = { viewModel.setOrderType(it) },
            onCustomerSelect = { viewModel.setSelectedCustomer(it) },
            onApplyPromoCode = { code, cb -> viewModel.applyPromoCode(code, cb) },
            onRemovePromoCode = { viewModel.removePromoCode() },
            onHoldCart = {
                showCartSheet = false
                showHoldNoteDialog = true
            },
            onClearCart = {
                if (settings.pinLockEnabled) {
                    showClearCartPinDialog = true
                } else {
                    viewModel.clearCart()
                    showCartSheet = false
                }
            },
            onProceedToPayment = {
                showCartSheet = false
                showPaymentDialog = true
            },
            onDismiss = { showCartSheet = false }
        )
    }

    // Admin PIN Protection for Clearing Cart
    if (showClearCartPinDialog) {
        com.sypos.mobile.ui.auth.PinAuthDialog(
            title = "Autorisation Admin pour Vider le Panier",
            correctPin = settings.adminPin,
            onDismiss = { showClearCartPinDialog = false },
            onSuccess = {
                viewModel.clearCart()
                showClearCartPinDialog = false
                showCartSheet = false
                Toast.makeText(context, "🗑️ Panier vidé avec succès", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Admin PIN Protection for Item Removal from Cart
    itemToRemoveWithPin?.let { productId ->
        com.sypos.mobile.ui.auth.PinAuthDialog(
            title = "Autorisation Admin pour Supprimer un Article",
            correctPin = settings.adminPin,
            onDismiss = { itemToRemoveWithPin = null },
            onSuccess = {
                viewModel.removeFromCart(productId)
                itemToRemoveWithPin = null
                Toast.makeText(context, "Article supprimé du panier", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Hold Cart Note Dialog
    if (showHoldNoteDialog) {
        HoldCartNoteDialog(
            onDismiss = { showHoldNoteDialog = false },
            onConfirm = { note ->
                viewModel.holdCurrentCart(note)
                showHoldNoteDialog = false
            }
        )
    }

    // Payment Dialog
    if (showPaymentDialog) {
        PaymentDialog(
            totalAmount = uiState.totalAmount,
            customerName = uiState.selectedCustomer?.name,
            onDismiss = { showPaymentDialog = false },
            onConfirmPayment = { method, amountReceived ->
                viewModel.processPayment(context, method, amountReceived) {
                    showPaymentDialog = false
                }
            }
        )
    }

    // Receipt Dialog (on success)
    if (uiState.showReceipt && uiState.lastCompletedTicket != null) {
        ReceiptDialog(
            ticket = uiState.lastCompletedTicket!!,
            items = uiState.lastCompletedItems,
            customerName = uiState.selectedCustomer?.name,
            settings = settings,
            onDismiss = { viewModel.closeReceipt() }
        )
    }

    // Held Tickets Modal
    if (showHeldTicketsDialog) {
        HeldTicketsDialog(
            tickets = heldTickets,
            onResumeTicket = { ticket ->
                viewModel.resumeHeldTicket(ticket)
                showHeldTicketsDialog = false
            },
            onDismiss = { showHeldTicketsDialog = false }
        )
    }
}

@Composable
fun PosProductCard(
    product: ProductEntity,
    category: CategoryEntity?,
    quantityInCart: Int,
    allowNegativeStock: Boolean,
    onAddToCart: () -> Unit
) {
    val isOutOfStock = product.stockQuantity <= 0
    val isMaxStockReached = !allowNegativeStock && quantityInCart >= product.stockQuantity && product.stockQuantity > 0
    val isCardClickable = allowNegativeStock || (!isOutOfStock && !isMaxStockReached)

    val cardColor = if (quantityInCart > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    else if (isOutOfStock && !allowNegativeStock) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    else MaterialTheme.colorScheme.surface

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOutOfStock && !allowNegativeStock) 0.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isCardClickable) { onAddToCart() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category tag and in-cart badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val defaultColor = MaterialTheme.colorScheme.primary
                val catColor = remember(category?.colorHex, defaultColor) {
                    val hex = category?.colorHex
                    if (hex != null) {
                        try {
                            Color(android.graphics.Color.parseColor(hex))
                        } catch (e: Exception) {
                            defaultColor
                        }
                    } else defaultColor
                }

                Surface(
                    color = catColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = category?.name ?: "Général",
                        style = MaterialTheme.typography.labelSmall,
                        color = catColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (quantityInCart > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "$quantityInCart",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Product Name
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isOutOfStock && !allowNegativeStock) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
            )

            // Stock status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val stockText = when {
                    product.stockQuantity <= 0 -> "ÉPUISÉ"
                    product.stockQuantity <= product.alertStock -> "Reste ${product.stockQuantity}"
                    else -> "${product.stockQuantity} en rayon"
                }
                val stockColor = when {
                    product.stockQuantity <= 0 -> Color(0xFFEF4444)
                    product.stockQuantity <= product.alertStock -> Color(0xFFF59E0B)
                    else -> Color(0xFF10B981)
                }

                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(stockColor)
                )
                Text(
                    text = stockText,
                    style = MaterialTheme.typography.labelSmall,
                    color = stockColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Price in CFA
            Text(
                text = String.format(Locale.FRANCE, "%,.0f CFA", product.salePrice),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun HeldTicketsDialog(
    tickets: List<TicketEntity>,
    onResumeTicket: (TicketEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Commandes en Attente (${tickets.size})", fontWeight = FontWeight.Bold) },
        text = {
            if (tickets.isEmpty()) {
                Text("Aucune commande en attente actuellement.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(tickets, key = { it.id }) { ticket ->
                        val dateStr = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(ticket.date))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResumeTicket(ticket) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(ticket.ticketNumber, fontWeight = FontWeight.Bold)
                                    if (!ticket.note.isNullOrBlank()) {
                                        Text(
                                            text = "📌 ${ticket.note}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "${ticket.orderType.name} • $dateStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { onResumeTicket(ticket) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Reprendre")
                                }
                            }
                        }
                    }
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
fun HoldCartNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mettre le Panier en Pause", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Spécifiez un nom de client, numéro de table ou repère pour retrouver facilement ce panier :", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Ex: Table 4, Client M. Koffi, Commande #12...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(noteText.ifBlank { null }) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Mettre en Attente")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
