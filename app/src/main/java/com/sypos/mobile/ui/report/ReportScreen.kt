package com.sypos.mobile.ui.report

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import com.sypos.mobile.data.local.entity.ExpenseEntity
import com.sypos.mobile.data.local.entity.PaymentMethod
import com.sypos.mobile.ui.history.CustomDateRangeDialog
import com.sypos.mobile.ui.history.DateRangeFilter
import com.sypos.mobile.util.BluetoothPrinterHelper
import com.sypos.mobile.util.ZReportData
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: ReportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val summary by viewModel.reportSummary.collectAsState()
    val stockValuation by viewModel.stockValuation.collectAsState()
    val selectedDateFilter by viewModel.selectedDateFilter.collectAsState()
    val expenses by viewModel.displayedExpenses.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showZReportDialog by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }

    fun openAndPrintZReport() {
        if (!settings.bluetoothPrinterAddress.isNullOrBlank()) {
            viewModel.printZReport(context) { result ->
                if (result.isSuccess) {
                    Toast.makeText(context, "✅ Rapport Z envoyé à l'imprimante !", Toast.LENGTH_SHORT).show()
                }
            }
        }
        showZReportDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bilan & Caisse", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                actions = {
                    FilledTonalButton(
                        onClick = { openAndPrintZReport() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rapport Z", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddExpenseDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Nouvelle Dépense") },
                text = { Text("Dépense / Sortie") },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Z Report Quick Banner
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            }
                            Column {
                                Text("Clôture de Caisse (Rapport Z)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Bilan journalier & impression ticket", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Button(
                            onClick = { openAndPrintZReport() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Générer")
                        }
                    }
                }
            }

            // Period Filter Selector Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Période d'Analyse :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(DateRangeFilter.values()) { filter ->
                            val isSelected = selectedDateFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (filter == DateRangeFilter.CUSTOM) {
                                        showCustomDateDialog = true
                                    } else {
                                        viewModel.setDateFilter(filter)
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
                }
            }

            // Financial Summary Cards
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Bilan Financier (${selectedDateFilter.label})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${summary.totalTicketsCount} ventes",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Total Sales
                            KpiMiniCard(
                                title = "Ventes / Recettes",
                                amount = summary.totalSales,
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                color = Color(0xFF10B981),
                                modifier = Modifier.weight(1f)
                            )
                            // Total Expenses
                            KpiMiniCard(
                                title = "Dépenses Sorties",
                                amount = summary.totalExpenses,
                                icon = Icons.AutoMirrored.Filled.TrendingDown,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Net Profit
                        Surface(
                            color = if (summary.netBalance >= 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("BÉNÉFICE NET (SOLDE DE CAISSE)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (summary.netBalance >= 0) Color(0xFF2E7D32) else Color(0xFFC62828))
                                    Text(
                                        text = String.format(Locale.FRANCE, "%,.0f CFA", summary.netBalance),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (summary.netBalance >= 0) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                    )
                                }
                                Icon(
                                    imageVector = if (summary.netBalance >= 0) Icons.Default.AccountBalanceWallet else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (summary.netBalance >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section: Stock Valuation (Valorisation du Stock)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Valorisation Globale du Stock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Valeur Achat (Revient)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = String.format(Locale.FRANCE, "%,.0f CFA", stockValuation.totalPurchaseValue),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Valeur Vente (Marchande)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = String.format(Locale.FRANCE, "%,.0f CFA", stockValuation.totalSaleValue),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Marge Bénéficiaire Potentielle", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = String.format(Locale.FRANCE, "%,.0f CFA", stockValuation.potentialProfitMargin),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "${stockValuation.totalStockUnits} pièces en stock (${stockValuation.totalArticlesCount} réf)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Payment Methods Breakdown
            if (summary.breakdown.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Répartition par Mode de Paiement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            summary.breakdown.forEach { item ->
                                val label = when (item.method) {
                                    PaymentMethod.CASH -> "Espèces (Cash)"
                                    PaymentMethod.WAVE -> "Wave Money"
                                    PaymentMethod.ORANGE_MONEY -> "Orange Money"
                                    PaymentMethod.MTN -> "MTN Mobile Money"
                                    PaymentMethod.MOOV -> "Moov Money"
                                    PaymentMethod.CARD -> "Carte Bancaire"
                                    PaymentMethod.CREDIT -> "Vente à Crédit"
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "$label (${item.count})", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = String.format(Locale.FRANCE, "%,.0f CFA", item.totalAmount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Expenses List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Journal des Dépenses (${expenses.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showAddExpenseDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ajouter")
                    }
                }
            }

            if (expenses.isEmpty()) {
                item {
                    Text("Aucune dépense enregistrée pour cette période.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(expenses, key = { it.id }) { expense ->
                    ExpenseItemRow(
                        expense = expense,
                        onDelete = { expenseToDelete = expense }
                    )
                }
            }
        }
    }

    if (showAddExpenseDialog) {
        AddExpenseDialog(
            onDismiss = { showAddExpenseDialog = false },
            onConfirm = { amount, desc, cat ->
                viewModel.addExpense(amount, desc, cat)
            }
        )
    }

    if (showZReportDialog) {
        val zData = viewModel.generateTodayZReport()
        ZReportDialog(
            report = zData,
            settings = settings,
            onPrint = {
                viewModel.printZReport(context) { result ->
                    if (result.isSuccess) {
                        Toast.makeText(context, "✅ Rapport Z imprimé !", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "❌ Erreur: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showZReportDialog = false }
        )
    }

    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            onDismiss = { showCustomDateDialog = false },
            onConfirm = { start, end ->
                viewModel.setCustomDateRange(start, end)
                showCustomDateDialog = false
            }
        )
    }

    expenseToDelete?.let { expense ->
        if (settings.pinLockEnabled) {
            com.sypos.mobile.ui.auth.PinAuthDialog(
                title = "Autorisation Admin pour Supprimer une Dépense",
                correctPin = settings.adminPin,
                onDismiss = { expenseToDelete = null },
                onSuccess = {
                    viewModel.deleteExpense(expense)
                    Toast.makeText(context, "Dépense supprimée", Toast.LENGTH_SHORT).show()
                    expenseToDelete = null
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { expenseToDelete = null },
                title = { Text("Supprimer la dépense ?") },
                text = { Text("Voulez-vous supprimer \"${expense.description}\" (${String.format(Locale.US, "%,.0f", expense.amount).replace(',', ' ')} CFA) ?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteExpense(expense)
                            expenseToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Supprimer")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { expenseToDelete = null }) {
                        Text("Annuler")
                    }
                }
            )
        }
    }
}

@Composable
fun KpiMiniCard(
    title: String,
    amount: Double,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(title, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            }
            Text(
                text = String.format(Locale.FRANCE, "%,.0f CFA", amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

@Composable
fun ExpenseItemRow(
    expense: ExpenseEntity,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd/MM/yy HH:mm", Locale.FRANCE).format(Date(expense.date))

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.description, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "$dateStr ${if (!expense.category.isNullOrBlank()) "• " + expense.category else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "-${String.format(Locale.FRANCE, "%,.0f CFA", expense.amount)}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
