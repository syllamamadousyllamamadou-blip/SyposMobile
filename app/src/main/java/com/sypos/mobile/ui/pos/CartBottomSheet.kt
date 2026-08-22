package com.sypos.mobile.ui.pos

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sypos.mobile.data.local.BusinessMode
import com.sypos.mobile.data.local.entity.CustomerEntity
import com.sypos.mobile.data.local.entity.OrderType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartBottomSheet(
    uiState: PosUiState,
    businessMode: BusinessMode = BusinessMode.SUPERMARKET,
    customers: List<CustomerEntity>,
    onIncreaseQuantity: (String) -> Unit,
    onDecreaseQuantity: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onOrderTypeSelect: (OrderType) -> Unit,
    onCustomerSelect: (CustomerEntity?) -> Unit,
    onApplyPromoCode: (String, (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onRemovePromoCode: () -> Unit = {},
    onHoldCart: () -> Unit,
    onClearCart: () -> Unit,
    onProceedToPayment: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showCustomerDropdown by remember { mutableStateOf(false) }
    var promoInput by remember { mutableStateOf("") }
    var showPromoInput by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title & Clear Cart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Panier en cours (${uiState.totalItemsCount})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                if (uiState.cartItems.isNotEmpty()) {
                    TextButton(onClick = onClearCart) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Vider", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Order Type Segmented Tabs (Shown ONLY in Restaurant mode)
            if (businessMode == BusinessMode.RESTAURANT) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        OrderType.TAKEAWAY to "À Emporter",
                        OrderType.DINE_IN to "Sur Place",
                        OrderType.DELIVERY to "Livraison"
                    ).forEach { (type, label) ->
                        val isSelected = uiState.selectedOrderType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { onOrderTypeSelect(type) },
                            label = { Text(label) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Customer Selector Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedCard(
                    onClick = { showCustomerDropdown = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = uiState.selectedCustomer?.name ?: "Client Comptoir (Passager)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }

                DropdownMenu(
                    expanded = showCustomerDropdown,
                    onDismissRequest = { showCustomerDropdown = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("Client Comptoir (Passager)") },
                        onClick = {
                            onCustomerSelect(null)
                            showCustomerDropdown = false
                        }
                    )
                    customers.forEach { customer ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(customer.name, fontWeight = FontWeight.Bold)
                                    if (customer.totalDebt > 0) {
                                        Text(
                                            "Dette: ${String.format(Locale.FRANCE, "%,.0f CFA", customer.totalDebt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onCustomerSelect(customer)
                                showCustomerDropdown = false
                            }
                        )
                    }
                }
            }

            // Cart Items List
            if (uiState.cartItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Le panier est vide.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.cartItems, key = { it.product.id }) { item ->
                        CartItemRow(
                            item = item,
                            onIncrease = { onIncreaseQuantity(item.product.id) },
                            onDecrease = { onDecreaseQuantity(item.product.id) },
                            onRemove = { onRemoveItem(item.product.id) }
                        )
                    }
                }

                // Promo Code Section
                if (uiState.appliedPromoCode != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("Code : ${uiState.appliedPromoCode.code} (-${uiState.appliedPromoCode.discountPercent.toInt()}%)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = onRemovePromoCode, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Retirer", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                } else if (showPromoInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = promoInput,
                            onValueChange = { promoInput = it.uppercase() },
                            placeholder = { Text("Ex: PROMO10") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (promoInput.isNotBlank()) {
                                    onApplyPromoCode(promoInput) { success, msg ->
                                        if (success) {
                                            showPromoInput = false
                                            promoInput = ""
                                        } else {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Appliquer")
                        }
                    }
                } else {
                    TextButton(
                        onClick = { showPromoInput = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ajouter un code promo", style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider()

                // Subtotal, Discount & Tax
                if (uiState.discountAmount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Remise appliquée :", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "-${String.format(Locale.FRANCE, "%,.0f CFA", uiState.discountAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (uiState.taxEnabled && uiState.taxAmount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sous-Total HT :", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = String.format(Locale.FRANCE, "%,.0f CFA", uiState.netAfterDiscount),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("TVA (${uiState.taxRatePercent.toInt()}%) :", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = String.format(Locale.FRANCE, "%,.0f CFA", uiState.taxAmount),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Total Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (uiState.taxEnabled) "TOTAL TTC" else "TOTAL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = String.format(Locale.FRANCE, "%,.0f CFA", uiState.totalAmount),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Action Buttons (Hold Cart & Pay)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onHoldCart,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.PauseCircleOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("En Attente")
                    }

                    Button(
                        onClick = onProceedToPayment,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Payer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    val isMaxStock = item.quantity >= item.product.stockQuantity

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.product.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "${String.format(Locale.FRANCE, "%,.0f", item.unitPrice)} CFA / u  (Stock: ${item.product.stockQuantity})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quantity adjust buttons (+ / -)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onDecrease() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Remove, contentDescription = "Moins", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }

                Text(
                    text = "${item.quantity}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Surface(
                    shape = CircleShape,
                    color = if (isMaxStock) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(enabled = !isMaxStock) { onIncrease() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Plus",
                            tint = if (isMaxStock) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = String.format(Locale.FRANCE, "%,.0f CFA", item.total),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
