package com.sypos.mobile.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.ShopSettingsManager
import com.sypos.mobile.data.local.entity.CustomerEntity
import com.sypos.mobile.data.local.entity.TicketEntity
import com.sypos.mobile.data.local.entity.TicketItemEntity
import com.sypos.mobile.data.local.entity.TicketStatus
import com.sypos.mobile.domain.repository.CustomerRepository
import com.sypos.mobile.domain.repository.TicketRepository
import com.sypos.mobile.util.PdfExportHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

enum class DateRangeFilter(val label: String) {
    ALL("Tout"),
    TODAY("Aujourd'hui"),
    YESTERDAY("Hier"),
    THIS_WEEK("Cette Semaine"),
    THIS_MONTH("Ce Mois"),
    CUSTOM("Personnalisé")
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val customerRepository: CustomerRepository,
    private val shopSettingsManager: ShopSettingsManager
) : ViewModel() {

    val settings: StateFlow<ShopSettings> = shopSettingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopSettings())

    private val _selectedStatus = MutableStateFlow<TicketStatus?>(null)
    val selectedStatus: StateFlow<TicketStatus?> = _selectedStatus.asStateFlow()

    private val _selectedDateFilter = MutableStateFlow(DateRangeFilter.ALL)
    val selectedDateFilter: StateFlow<DateRangeFilter> = _selectedDateFilter.asStateFlow()

    private val _customStartDate = MutableStateFlow<Long?>(null)
    val customStartDate: StateFlow<Long?> = _customStartDate.asStateFlow()

    private val _customEndDate = MutableStateFlow<Long?>(null)
    val customEndDate: StateFlow<Long?> = _customEndDate.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val allTickets: StateFlow<List<TicketEntity>> = ticketRepository.getAllTickets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = customerRepository.getAllCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTickets: StateFlow<List<TicketEntity>> = combine(
        allTickets,
        _selectedStatus,
        _selectedDateFilter,
        _searchQuery
    ) { tickets, status, dateFilter, query ->
        val (startTime, endTime) = getDateBounds(dateFilter, _customStartDate.value, _customEndDate.value)

        tickets.filter { ticket ->
            val matchStatus = status == null || ticket.status == status
            val matchDate = (startTime == null || ticket.date >= startTime) &&
                    (endTime == null || ticket.date <= endTime)
            val matchQuery = query.isBlank() ||
                    ticket.ticketNumber.contains(query, ignoreCase = true) ||
                    (ticket.customerId != null && customers.value.find { it.id == ticket.customerId }?.name?.contains(query, ignoreCase = true) == true)
            matchStatus && matchDate && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onStatusFilterSelected(status: TicketStatus?) {
        _selectedStatus.value = status
    }

    fun onDateFilterSelected(filter: DateRangeFilter) {
        _selectedDateFilter.value = filter
    }

    fun setCustomDateRange(start: Long?, end: Long?) {
        _customStartDate.value = start
        _customEndDate.value = end
        _selectedDateFilter.value = DateRangeFilter.CUSTOM
    }

    fun getItemsForTicket(ticketId: String): Flow<List<TicketItemEntity>> {
        return ticketRepository.getItemsForTicket(ticketId)
    }

    fun cancelTicket(ticketId: String) {
        viewModelScope.launch {
            ticketRepository.cancelTicket(ticketId)
        }
    }

    fun exportSalesPdf(context: Context, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val list = filteredTickets.value
            val statusLabel = selectedStatus.value?.name ?: "Toutes les Ventes"
            val dateLabel = _selectedDateFilter.value.label
            val result = PdfExportHelper.generateSalesPdf(context, list, settings.value, "Filtre: $statusLabel • $dateLabel")
            if (result.isSuccess) {
                PdfExportHelper.openOrSharePdf(context, result.getOrThrow())
                onResult(Result.success(Unit))
            } else {
                onResult(Result.failure(result.exceptionOrNull() ?: Exception("Erreur génération PDF")))
            }
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
