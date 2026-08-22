package com.sypos.mobile.ui.customer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sypos.mobile.data.local.entity.CustomerEntity
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    viewModel: CustomerViewModel = hiltViewModel()
) {
    val customers by viewModel.displayedCustomers.collectAsState()
    val allCustomers by viewModel.allCustomers.collectAsState()
    val totalDebt by viewModel.totalDebtAmount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val onlyDebtors by viewModel.onlyDebtors.collectAsState()

    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToSettle by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToDelete by remember { mutableStateOf<CustomerEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clients & Carnet de Dettes", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
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
            // Total Outstanding Debt Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (totalDebt > 0) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                            text = "Créances à recouvrer",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (totalDebt > 0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.FRANCE, "%,.0f CFA", totalDebt),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (totalDebt > 0) Color(0xFFC62828) else MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(
                        color = if (totalDebt > 0) Color(0xFFEF4444).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${allCustomers.count { it.totalDebt > 0 }} client(s) endetté(s)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (totalDebt > 0) Color(0xFFC62828) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = { Text("Rechercher un client ou téléphone...") },
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
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Filter Chips (Tous vs Endettés)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !onlyDebtors,
                    onClick = { viewModel.toggleOnlyDebtors(false) },
                    label = { Text("Tous (${allCustomers.size})") },
                    shape = RoundedCornerShape(10.dp)
                )
                FilterChip(
                    selected = onlyDebtors,
                    onClick = { viewModel.toggleOnlyDebtors(true) },
                    label = { Text("Avec Dettes (${allCustomers.count { it.totalDebt > 0 }})") },
                    shape = RoundedCornerShape(10.dp)
                )
            }

            if (customers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PeopleOutline, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                        Text(
                            text = if (searchQuery.isBlank()) "Aucun client enregistré." else "Aucun client trouvé.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(customers, key = { it.id }) { customer ->
                        CustomerCard(
                            customer = customer,
                            onEditClick = { customerToEdit = customer },
                            onSettleClick = { customerToSettle = customer },
                            onDeleteClick = { customerToDelete = customer }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditCustomerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { viewModel.saveCustomer(it) }
        )
    }

    customerToEdit?.let { customer ->
        AddEditCustomerDialog(
            customer = customer,
            onDismiss = { customerToEdit = null },
            onConfirm = {
                viewModel.saveCustomer(it)
                customerToEdit = null
            }
        )
    }

    customerToSettle?.let { customer ->
        SettleDebtDialog(
            customer = customer,
            onDismiss = { customerToSettle = null },
            onConfirm = { amount ->
                viewModel.settleCustomerDebt(customer.id, amount)
                customerToSettle = null
            }
        )
    }

    val settings by viewModel.settings.collectAsState()

    customerToDelete?.let { customer ->
        if (settings.pinLockEnabled) {
            com.sypos.mobile.ui.auth.PinAuthDialog(
                title = "Autorisation Admin pour Supprimer un Client",
                correctPin = settings.adminPin,
                onDismiss = { customerToDelete = null },
                onSuccess = {
                    viewModel.deleteCustomer(customer)
                    customerToDelete = null
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { customerToDelete = null },
                title = { Text("Supprimer le client ?") },
                text = { Text("Voulez-vous supprimer \"${customer.name}\" du carnet client ?") },
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
    }
}

@Composable
fun CustomerCard(
    customer: CustomerEntity,
    onEditClick: () -> Unit,
    onSettleClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val hasDebt = customer.totalDebt > 0

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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (customer.phone != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = customer.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (hasDebt) {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Dette : ${String.format(Locale.FRANCE, "%,.0f CFA", customer.totalDebt)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "À jour (0 CFA)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasDebt) {
                    Button(
                        onClick = onSettleClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Régler", style = MaterialTheme.typography.labelMedium)
                    }
                }

                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
