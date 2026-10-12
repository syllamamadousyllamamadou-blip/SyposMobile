package com.sypos.mobile.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.ShopSettingsManager
import com.sypos.mobile.data.local.SyposDatabase
import com.sypos.mobile.data.local.entity.CustomerEntity
import com.sypos.mobile.data.local.entity.TicketStatus
import com.sypos.mobile.data.remote.CloudSyncManager
import com.sypos.mobile.domain.repository.CustomerRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CustomerFilter {
    ALL, VIP, RECENT, INACTIVE
}

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val shopSettingsManager: ShopSettingsManager,
    private val database: SyposDatabase,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    val settings: StateFlow<ShopSettings> = shopSettingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopSettings())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterMode = MutableStateFlow(CustomerFilter.ALL)
    val filterMode: StateFlow<CustomerFilter> = _filterMode.asStateFlow()

    val allCustomers: StateFlow<List<CustomerEntity>> = customerRepository.getAllCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCustomerSales: StateFlow<Double> = allCustomers.map { list ->
        list.sumOf { it.totalSpent }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val displayedCustomers: StateFlow<List<CustomerEntity>> = combine(
        allCustomers,
        _searchQuery,
        _filterMode
    ) { list, query, filter ->
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000)
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)

        list.filter { customer ->
            val matchesQuery = query.isBlank() ||
                    customer.name.contains(query, ignoreCase = true) ||
                    (customer.phone != null && customer.phone.contains(query))

            val matchesFilter = when (filter) {
                CustomerFilter.ALL -> true
                CustomerFilter.VIP -> (customer.totalSpent >= 50000) || (customer.visitCount >= 5)
                CustomerFilter.RECENT -> customer.lastPurchaseDate != null && customer.lastPurchaseDate >= sevenDaysAgo
                CustomerFilter.INACTIVE -> customer.lastPurchaseDate == null || customer.lastPurchaseDate < thirtyDaysAgo
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelected(filter: CustomerFilter) {
        _filterMode.value = filter
    }

    fun saveCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            customerRepository.insertCustomer(customer)
            val uid = shopSettingsManager.getEffectiveShopId()
            if (uid.isNotBlank()) {
                cloudSyncManager.pushCustomer(uid, customer)
            }
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            customerRepository.deleteCustomer(customer)
            val shopId = shopSettingsManager.getEffectiveShopId()
            if (shopId.isNotBlank()) {
                cloudSyncManager.deleteCustomer(shopId, customer.id)
            }
        }
    }

    fun payDebt(customer: CustomerEntity, amount: Double) {
        viewModelScope.launch {
            // Calculate new debt
            val newDebt = Math.max(0.0, customer.debt - amount)
            // Save history in notes
            val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            val noteEntry = "[$dateStr] Remboursement dette: $amount FCFA. Reste: $newDebt FCFA"
            val oldNotes = customer.notes ?: ""
            val newNotes = if (oldNotes.isBlank()) noteEntry else "$oldNotes\n$noteEntry"
            
            // Update customer entirely
            val updatedCustomer = customer.copy(debt = newDebt, notes = newNotes)
            customerRepository.updateCustomer(updatedCustomer)
            
            // Push customer update to Cloud
            val uid = shopSettingsManager.getEffectiveShopId()
            if (uid.isNotBlank()) {
                cloudSyncManager.pushCustomer(uid, updatedCustomer)
            }

            // Distribute payment across UNPAID (CREDIT) tickets
            var remainingPayment = amount
            val creditTickets = database.ticketDao().getTicketsByCustomerAndStatusSync(customer.id, TicketStatus.CREDIT)
            
            for (ticket in creditTickets) {
                if (remainingPayment <= 0) break
                val amountDue = ticket.totalAmount - ticket.amountPaid
                if (amountDue > 0) {
                    val payForThisTicket = Math.min(remainingPayment, amountDue)
                    remainingPayment -= payForThisTicket
                    val newAmountPaid = ticket.amountPaid + payForThisTicket
                    
                    val updatedTicket = ticket.copy(
                        amountPaid = newAmountPaid,
                        status = if (newAmountPaid >= ticket.totalAmount) TicketStatus.PAID else TicketStatus.CREDIT
                    )
                    database.ticketDao().updateTicket(updatedTicket)
                    
                    // Sync updated ticket to cloud
                    if (uid.isNotBlank()) {
                        val items = database.ticketDao().getItemsForTicketSync(ticket.id)
                        cloudSyncManager.pushSale(uid, updatedTicket, items, isNewSale = false)
                    }
                }
            }
        }
    }

    fun getTicketsForCustomer(customerId: String): Flow<List<com.sypos.mobile.data.local.entity.TicketEntity>> {
        return database.ticketDao().getTicketsByCustomerId(customerId)
    }
}
