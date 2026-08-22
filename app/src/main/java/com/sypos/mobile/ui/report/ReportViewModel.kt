package com.sypos.mobile.ui.report

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.ShopSettingsManager
import com.sypos.mobile.data.local.entity.ExpenseEntity
import com.sypos.mobile.data.local.entity.PaymentMethod
import com.sypos.mobile.data.local.entity.ProductEntity
import com.sypos.mobile.data.local.entity.TicketEntity
import com.sypos.mobile.data.local.entity.TicketStatus
import com.sypos.mobile.domain.repository.ExpenseRepository
import com.sypos.mobile.domain.repository.ProductRepository
import com.sypos.mobile.domain.repository.TicketRepository
import com.sypos.mobile.ui.history.DateRangeFilter
import com.sypos.mobile.util.BluetoothPrinterHelper
import com.sypos.mobile.util.ZReportData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class PaymentBreakdown(
    val method: PaymentMethod,
    val totalAmount: Double,
    val count: Int
)

data class StockValuationSummary(
    val totalPurchaseValue: Double = 0.0,
    val totalSaleValue: Double = 0.0,
    val potentialProfitMargin: Double = 0.0,
    val totalArticlesCount: Int = 0,
    val totalStockUnits: Int = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0
)

data class ReportSummary(
    val totalSales: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netBalance: Double = 0.0,
    val totalTicketsCount: Int = 0,
    val breakdown: List<PaymentBreakdown> = emptyList()
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val expenseRepository: ExpenseRepository,
    private val productRepository: ProductRepository,
    private val shopSettingsManager: ShopSettingsManager
) : ViewModel() {

    val settings: StateFlow<ShopSettings> = shopSettingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopSettings())

    private val _selectedDateFilter = MutableStateFlow(DateRangeFilter.TODAY)
    val selectedDateFilter: StateFlow<DateRangeFilter> = _selectedDateFilter.asStateFlow()

    private val _customStartDate = MutableStateFlow<Long?>(null)
    val customStartDate: StateFlow<Long?> = _customStartDate.asStateFlow()

    private val _customEndDate = MutableStateFlow<Long?>(null)
    val customEndDate: StateFlow<Long?> = _customEndDate.asStateFlow()

    val allExpenses: StateFlow<List<ExpenseEntity>> = expenseRepository.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTickets: StateFlow<List<TicketEntity>> = ticketRepository.getAllTickets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProducts: StateFlow<List<ProductEntity>> = productRepository.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Stock Valuation KPI
    val stockValuation: StateFlow<StockValuationSummary> = allProducts.map { products ->
        val purchaseVal = products.sumOf { (it.stockQuantity.coerceAtLeast(0)) * it.costPrice }
        val saleVal = products.sumOf { (it.stockQuantity.coerceAtLeast(0)) * it.salePrice }
        val margin = saleVal - purchaseVal
        val totalUnits = products.sumOf { it.stockQuantity.coerceAtLeast(0) }
        val lowStock = products.count { it.stockQuantity in 1..it.alertStock }
        val outOfStock = products.count { it.stockQuantity <= 0 }

        StockValuationSummary(
            totalPurchaseValue = purchaseVal,
            totalSaleValue = saleVal,
            potentialProfitMargin = margin,
            totalArticlesCount = products.size,
            totalStockUnits = totalUnits,
            lowStockCount = lowStock,
            outOfStockCount = outOfStock
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StockValuationSummary())

    val reportSummary: StateFlow<ReportSummary> = combine(
        allTickets,
        allExpenses,
        _selectedDateFilter
    ) { tickets, expenses, dateFilter ->
        val (start, end) = getDateBounds(dateFilter, _customStartDate.value, _customEndDate.value)

        val filteredTickets = tickets.filter { ticket ->
            val isPaid = ticket.status == TicketStatus.PAID
            val matchDate = (start == null || ticket.date >= start) && (end == null || ticket.date <= end)
            isPaid && matchDate
        }

        val filteredExpenses = expenses.filter { exp ->
            (start == null || exp.date >= start) && (end == null || exp.date <= end)
        }

        val totalSales = filteredTickets.sumOf { it.totalAmount }
        val totalExpenses = filteredExpenses.sumOf { it.amount }
        val netBalance = totalSales - totalExpenses

        val breakdown = PaymentMethod.values().map { method ->
            val matching = filteredTickets.filter { it.paymentMethod == method }
            PaymentBreakdown(
                method = method,
                totalAmount = matching.sumOf { it.totalAmount },
                count = matching.size
            )
        }.filter { it.totalAmount > 0 }

        ReportSummary(
            totalSales = totalSales,
            totalExpenses = totalExpenses,
            netBalance = netBalance,
            totalTicketsCount = filteredTickets.size,
            breakdown = breakdown
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportSummary())

    val displayedExpenses: StateFlow<List<ExpenseEntity>> = combine(
        allExpenses,
        _selectedDateFilter
    ) { expenses, dateFilter ->
        val (start, end) = getDateBounds(dateFilter, _customStartDate.value, _customEndDate.value)
        expenses.filter { (start == null || it.date >= start) && (end == null || it.date <= end) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDateFilter(filter: DateRangeFilter) {
        _selectedDateFilter.value = filter
    }

    fun setCustomDateRange(start: Long?, end: Long?) {
        _customStartDate.value = start
        _customEndDate.value = end
        _selectedDateFilter.value = DateRangeFilter.CUSTOM
    }

    fun addExpense(amount: Double, description: String, category: String?) {
        viewModelScope.launch {
            val expense = ExpenseEntity(
                date = System.currentTimeMillis(),
                amount = amount,
                description = description.trim(),
                category = category?.trim()
            )
            expenseRepository.insertExpense(expense)
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            expenseRepository.deleteExpense(expense)
        }
    }

    fun generateTodayZReport(): ZReportData {
        val (start, end) = getDateBounds(DateRangeFilter.TODAY, null, null)
        val todayTickets = allTickets.value.filter { it.status == TicketStatus.PAID && (start == null || it.date >= start) && (end == null || it.date <= end) }
        val todayExpenses = allExpenses.value.filter { (start == null || it.date >= start) && (end == null || it.date <= end) }
        val creditTickets = allTickets.value.filter { it.status == TicketStatus.CREDIT && (start == null || it.date >= start) && (end == null || it.date <= end) }

        val totalSales = todayTickets.sumOf { it.totalAmount }
        val totalExp = todayExpenses.sumOf { it.amount }
        val cash = todayTickets.filter { it.paymentMethod == PaymentMethod.CASH }.sumOf { it.totalAmount }
        val wave = todayTickets.filter { it.paymentMethod == PaymentMethod.WAVE }.sumOf { it.totalAmount }
        val om = todayTickets.filter { it.paymentMethod == PaymentMethod.ORANGE_MONEY }.sumOf { it.totalAmount }
        val mtn = todayTickets.filter { it.paymentMethod == PaymentMethod.MTN }.sumOf { it.totalAmount }
        val moov = todayTickets.filter { it.paymentMethod == PaymentMethod.MOOV }.sumOf { it.totalAmount }
        val card = todayTickets.filter { it.paymentMethod == PaymentMethod.CARD }.sumOf { it.totalAmount }
        val credit = creditTickets.sumOf { it.totalAmount }

        val netCash = (cash - totalExp)

        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())

        return ZReportData(
            dateText = dateStr,
            totalSales = totalSales,
            ticketsCount = todayTickets.size,
            totalExpenses = totalExp,
            cashSales = cash,
            waveSales = wave,
            orangeMoneySales = om,
            mtnSales = mtn,
            moovSales = moov,
            cardSales = card,
            creditSales = credit,
            netCashInDrawer = netCash
        )
    }

    fun printZReport(context: Context, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val printerAddress = settings.value.bluetoothPrinterAddress
            if (printerAddress.isNullOrBlank()) {
                onResult(Result.failure(Exception("Aucune imprimante Bluetooth configurée dans les Paramètres")))
                return@launch
            }
            val zData = generateTodayZReport()
            val result = BluetoothPrinterHelper.printZReport(context, printerAddress, zData, settings.value)
            onResult(result)
        }
    }

    private fun getDateBounds(
        filter: DateRangeFilter,
        customStart: Long?,
        customEnd: Long?
    ): Pair<Long?, Long?> {
        val cal = Calendar.getInstance()
        return when (filter) {
            DateRangeFilter.ALL -> Pair(null, null)
            DateRangeFilter.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            DateRangeFilter.YESTERDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            DateRangeFilter.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_WEEK, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            DateRangeFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            DateRangeFilter.CUSTOM -> {
                Pair(customStart, customEnd)
            }
        }
    }
}
