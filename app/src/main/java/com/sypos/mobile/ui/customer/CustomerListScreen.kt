package com.sypos.mobile.ui.customer

import com.sypos.mobile.util.CurrencyUtils.formatAmount
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sypos.mobile.data.local.entity.CustomerEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    viewModel: CustomerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val customers by viewModel.displayedCustomers.collectAsState()
    val allCustomers by viewModel.allCustomers.collectAsState()
    val totalCustomerSales by viewModel.totalCustomerSales.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterMode by viewModel.filterMode.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToRelance by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToDelete by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToPayDebt by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToViewHistory by remember { mutableStateOf<CustomerEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Base de Données Clients (CRM)", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                actions = {
                    IconButton(onClick = { com.sypos.mobile.util.ExportHelper.exportCustomersToCsv(context, allCustomers) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Exporter CSV")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = "Ajouter Client") },
                text = { Text("Nouveau Client") },
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
            // CRM Summary Header Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Chiffre d'Affaires Clients",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = totalCustomerSales.formatAmount(settings.currency),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${allCustomers.size} client(s)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = { Text("Rechercher un client ou numéro...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // CRM Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filterMode == CustomerFilter.ALL,
                        onClick = { viewModel.onFilterSelected(CustomerFilter.ALL) },
                        label = { Text("Tous (${allCustomers.size})") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = filterMode == CustomerFilter.VIP,
                        onClick = { viewModel.onFilterSelected(CustomerFilter.VIP) },
                        label = { Text("⭐  Top VIP") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = filterMode == CustomerFilter.RECENT,
                        onClick = { viewModel.onFilterSelected(CustomerFilter.RECENT) },
                        label = { Text("🆕 Récents (7j)") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = filterMode == CustomerFilter.INACTIVE,
                        onClick = { viewModel.onFilterSelected(CustomerFilter.INACTIVE) },
                        label = { Text("⏳ Inactifs (+30j)") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            if (customers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Aucun client correspondant" else "Aucun client enregistré",
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
                    items(customers, key = { it.id }) { customer ->
                        CustomerCrmCard(
                            customer = customer,
                            settings = settings,
                            onRelanceClick = { customerToRelance = customer },
                            onPayDebtClick = { customerToPayDebt = customer },
                            onEditClick = { customerToEdit = customer },
                            onDeleteClick = { customerToDelete = customer },
                            onViewHistoryClick = { customerToViewHistory = customer }
                        )
                    }
                }
            }
        }
    }

    // Add Customer Dialog
    if (showAddDialog) {
        AddEditCustomerDialog(
            customer = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { newCustomer ->
                viewModel.saveCustomer(newCustomer)
            }
        )
    }

    // Edit Customer Dialog
    customerToEdit?.let { customer ->
        AddEditCustomerDialog(
            customer = customer,
            onDismiss = { customerToEdit = null },
            onConfirm = { updated ->
                viewModel.saveCustomer(updated)
            }
        )
    }

    // Relance WhatsApp Dialog
    customerToRelance?.let { customer ->
        CustomerRelanceDialog(
            context = context,
            customer = customer,
            settings = settings,
            onDismiss = { customerToRelance = null }
        )
    }

    // Debt Payment Dialog
    customerToPayDebt?.let { customer ->
        DebtPaymentDialog(
            customer = customer,
            onDismiss = { customerToPayDebt = null },
            onConfirm = { amount ->
                viewModel.payDebt(customer, amount)
                customerToPayDebt = null
            }
        )
    }

    // Delete Confirmation Dialog
    customerToDelete?.let { customer ->
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            title = { Text("Supprimer le client") },
            text = { Text("Êtes-vous certain de vouloir supprimer la fiche de \"${customer.name}\" ?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(customer)
                        customerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }
    
    // View History Dialog
    customerToViewHistory?.let { customer ->
        CustomerHistoryDialog(
            customer = customer,
            viewModel = viewModel,
            currency = settings.currency,
            onDismiss = { customerToViewHistory = null }
        )
    }
}

@Composable
fun CustomerCrmCard(
    customer: CustomerEntity,
    settings: com.sypos.mobile.data.local.ShopSettings,
    onRelanceClick: () -> Unit,
    onPayDebtClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onViewHistoryClick: () -> Unit = {}
) {
    val isVip = (customer.totalSpent >= 50000) || (customer.visitCount >= 5)
    val lastVisitStr = if (customer.lastPurchaseDate != null && customer.lastPurchaseDate > 0) {
        val days = ((System.currentTimeMillis() - customer.lastPurchaseDate) / (1000 * 60 * 60 * 24)).toInt()
        when {
            days == 0 -> "Aujourd'hui"
            days == 1 -> "Hier"
            days < 7 -> "Il y a $days jours"
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(customer.lastPurchaseDate))
        }
    } else {
        "Aucun achat"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable { onViewHistoryClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isVip) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "⭐  VIP",
                                    color = Color(0xFF92400E),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (!customer.phone.isNullOrBlank()) {
                        Text(
                            text = "📞 ${customer.phone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!customer.email.isNullOrBlank()) {
                        Text(
                            text = "✉️ ${customer.email}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = customer.totalSpent.formatAmount(settings.currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF10B981)
                    )
                    Text(
                        text = "${customer.visitCount} commande(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (customer.debt > 0) {
                        Text(
                            text = "Dette: ${customer.debt.formatAmount(settings.currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🕒 Dernier passage : $lastVisitStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!customer.notes.isNullOrBlank()) {
                    Text(
                        text = "📍  ${customer.notes.replace("\n", " | ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRelanceClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Relance", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                }

                if (customer.debt > 0) {
                    Button(
                        onClick = onPayDebtClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Payer Dette", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                    }
                }

                if (settings.currentUserRole == com.sypos.mobile.data.local.UserRole.ADMIN) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
