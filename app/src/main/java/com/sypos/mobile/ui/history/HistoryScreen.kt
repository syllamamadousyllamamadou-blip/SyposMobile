package com.sypos.mobile.ui.history

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.entity.TicketEntity
import com.sypos.mobile.data.local.entity.TicketStatus
import com.sypos.mobile.ui.auth.PinAuthDialog
import com.sypos.mobile.ui.pos.shareReceiptText
import com.sypos.mobile.util.BluetoothPrinterHelper
import com.sypos.mobile.util.ExportHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateToScanner: () -> Unit = {}
) {
    val context = LocalContext.current
    val tickets by viewModel.filteredTickets.collectAsState()
    val allTickets by viewModel.allTickets.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val selectedDateFilter by viewModel.selectedDateFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var ticketToCancel by remember { mutableStateOf<TicketEntity?>(null) }
    var showPinAuthForCancel by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    val totalPaidSales = tickets.filter { it.status == TicketStatus.PAID }.sumOf { it.totalAmount }
    val totalPaidCount = tickets.count { it.status == TicketStatus.PAID }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique des Ventes", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                actions = {
                    // PDF Download Button
                    FilledTonalButton(
                        onClick = {
                            isExportingPdf = true
                            viewModel.exportSalesPdf(context) { res ->
                                isExportingPdf = false
                                if (res.isFailure) {
                                    Toast.makeText(context, "Erreur PDF : ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isExportingPdf,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isExportingPdf) "..." else "PDF", fontWeight = FontWeight.Bold)
                    }

                    // CSV Button
                    IconButton(onClick = { ExportHelper.exportTicketsToCsv(context, tickets) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Exporter CSV")
                    }
                }
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
                placeholder = { Text("Rechercher un ticket N° ou client...") },
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
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Date Range Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                items(DateRangeFilter.values()) { filter ->
                    val isSelected = selectedDateFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (filter == DateRangeFilter.CUSTOM) {
                                showCustomDateDialog = true
                            } else {
                                viewModel.onDateFilterSelected(filter)
                            }
                        },
                        label = { Text(filter.label) },
                        leadingIcon = if (filter == DateRangeFilter.CUSTOM) {
                            { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Summary Card (Calculated for current filtered range)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Chiffre d'Affaires (${selectedDateFilter.label})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = String.format(Locale.FRANCE, "%,.0f CFA", totalPaidSales),
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
                            text = "$totalPaidCount ventes",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Status Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { viewModel.onStatusFilterSelected(null) },
                        label = { Text("Tous (${tickets.size})") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = selectedStatus == TicketStatus.PAID,
                        onClick = { viewModel.onStatusFilterSelected(TicketStatus.PAID) },
                        label = { Text("Payés") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = selectedStatus == TicketStatus.ON_HOLD,
                        onClick = { viewModel.onStatusFilterSelected(TicketStatus.ON_HOLD) },
                        label = { Text("En Attente") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = selectedStatus == TicketStatus.CREDIT,
                        onClick = { viewModel.onStatusFilterSelected(TicketStatus.CREDIT) },
                        label = { Text("Crédits") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                item {
                    FilterChip(
                        selected = selectedStatus == TicketStatus.CANCELLED,
                        onClick = { viewModel.onStatusFilterSelected(TicketStatus.CANCELLED) },
                        label = { Text("Annulés") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            if (tickets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("Aucun ticket trouvé pour cette période.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tickets, key = { it.id }) { ticket ->
                        val customer = customers.find { it.id == ticket.customerId }
                        TicketHistoryCard(
                            ticket = ticket,
                            customerName = customer?.name,
                            settings = settings,
                            viewModel = viewModel,
                            onCancelClick = {
                                ticketToCancel = ticket
                                showPinAuthForCancel = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Admin PIN Protection for Canceling Sales
    if (showPinAuthForCancel && ticketToCancel != null) {
        PinAuthDialog(
            title = "Autorisation Admin pour Annulation",
            correctPin = settings.adminPin,
            onDismiss = {
                showPinAuthForCancel = false
                ticketToCancel = null
            },
            onSuccess = {
                val ticket = ticketToCancel!!
                viewModel.cancelTicket(ticket.id)
                Toast.makeText(context, "✅ Vente ${ticket.ticketNumber} annulée et stock réapprovisionné.", Toast.LENGTH_LONG).show()
                showPinAuthForCancel = false
                ticketToCancel = null
            }
        )
    }

    // Custom Date Range Picker Dialog
    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            onDismiss = { showCustomDateDialog = false },
            onConfirm = { start, end ->
                viewModel.setCustomDateRange(start, end)
                showCustomDateDialog = false
            }
        )
    }
}

@Composable
fun TicketHistoryCard(
    ticket: TicketEntity,
    customerName: String?,
    settings: ShopSettings,
    viewModel: HistoryViewModel,
    onCancelClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }
    val items by viewModel.getItemsForTicket(ticket.id).collectAsState(initial = emptyList())

    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(ticket.date))

    val statusColor = when (ticket.status) {
        TicketStatus.PAID -> Color(0xFF10B981)
        TicketStatus.ON_HOLD -> Color(0xFFF59E0B)
        TicketStatus.CREDIT -> Color(0xFFEF4444)
        TicketStatus.CANCELLED -> Color(0xFF6B7280)
    }

    val statusLabel = when (ticket.status) {
        TicketStatus.PAID -> "Payé"
        TicketStatus.ON_HOLD -> "En Attente"
        TicketStatus.CREDIT -> "Crédit"
        TicketStatus.CANCELLED -> "Annulé"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Ticket number, date & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = ticket.ticketNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Customer, Seller & Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (customerName != null) {
                        Text(
                            text = "Client: $customerName",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    val seller = ticket.sellerName ?: settings.sellerName
                    Text(
                        text = "Vendeur: $seller • ${ticket.paymentMethod?.name ?: "N/A"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = String.format(Locale.FRANCE, "%,.0f CFA", ticket.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Expandable Items breakdown & Actions
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider()

                    Text("Détail des articles :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.quantity}x ${item.productName}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = String.format(Locale.FRANCE, "%,.0f CFA", item.total),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider()

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Bluetooth Print
                        if (settings.bluetoothPrinterAddress != null) {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        isPrinting = true
                                        val result = BluetoothPrinterHelper.printTicket(
                                            context = context,
                                            deviceAddress = settings.bluetoothPrinterAddress,
                                            ticket = ticket,
                                            items = items,
                                            customerName = customerName,
                                            settings = settings
                                        )
                                        isPrinting = false
                                        if (result.isSuccess) {
                                            Toast.makeText(context, "Ticket réimprimé !", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Erreur : ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isPrinting,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isPrinting) "..." else "Imprimer")
                            }
                        }

                        // WhatsApp Share
                        OutlinedButton(
                            onClick = { shareReceiptText(context, ticket, items, customerName, settings) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Partager")
                        }

                        // Cancel sale (Requires Admin PIN)
                        if (ticket.status == TicketStatus.PAID || ticket.status == TicketStatus.CREDIT) {
                            Button(
                                onClick = onCancelClick,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Annuler")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomDateRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    val cal = Calendar.getInstance()
    var startDay by remember { mutableStateOf(cal.get(Calendar.DAY_OF_MONTH).toString()) }
    var startMonth by remember { mutableStateOf((cal.get(Calendar.MONTH) + 1).toString()) }
    var startYear by remember { mutableStateOf(cal.get(Calendar.YEAR).toString()) }

    var endDay by remember { mutableStateOf(cal.get(Calendar.DAY_OF_MONTH).toString()) }
    var endMonth by remember { mutableStateOf((cal.get(Calendar.MONTH) + 1).toString()) }
    var endYear by remember { mutableStateOf(cal.get(Calendar.YEAR).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtrer par Intervalle de Dates", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Date Début (JJ / MM / AAAA) :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = startDay, onValueChange = { startDay = it }, label = { Text("Jour") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = startMonth, onValueChange = { startMonth = it }, label = { Text("Mois") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = startYear, onValueChange = { startYear = it }, label = { Text("Année") }, modifier = Modifier.weight(1.5f))
                }

                Text("Date Fin (JJ / MM / AAAA) :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = endDay, onValueChange = { endDay = it }, label = { Text("Jour") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endMonth, onValueChange = { endMonth = it }, label = { Text("Mois") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endYear, onValueChange = { endYear = it }, label = { Text("Année") }, modifier = Modifier.weight(1.5f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sCal = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, startDay.toIntOrNull() ?: 1)
                        set(Calendar.MONTH, (startMonth.toIntOrNull() ?: 1) - 1)
                        set(Calendar.YEAR, startYear.toIntOrNull() ?: 2026)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    val eCal = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, endDay.toIntOrNull() ?: 31)
                        set(Calendar.MONTH, (endMonth.toIntOrNull() ?: 12) - 1)
                        set(Calendar.YEAR, endYear.toIntOrNull() ?: 2026)
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }
                    onConfirm(sCal.timeInMillis, eCal.timeInMillis)
                }
            ) {
                Text("Appliquer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
