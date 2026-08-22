package com.sypos.mobile.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.ShopSettingsManager
import com.sypos.mobile.data.local.entity.CustomerEntity
import com.sypos.mobile.domain.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val shopSettingsManager: ShopSettingsManager
) : ViewModel() {

    val settings: StateFlow<ShopSettings> = shopSettingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopSettings())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _onlyDebtors = MutableStateFlow(false)
    val onlyDebtors: StateFlow<Boolean> = _onlyDebtors.asStateFlow()

    val allCustomers: StateFlow<List<CustomerEntity>> = customerRepository.getAllCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalDebtAmount: StateFlow<Double> = allCustomers.map { list ->
        list.sumOf { it.totalDebt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val displayedCustomers: StateFlow<List<CustomerEntity>> = combine(
        allCustomers,
        _searchQuery,
        _onlyDebtors
    ) { list, query, onlyDebts ->
        list.filter { customer ->
            val matchesQuery = query.isBlank() ||
                    customer.name.contains(query, ignoreCase = true) ||
                    (customer.phone != null && customer.phone.contains(query))
            val matchesDebt = !onlyDebts || customer.totalDebt > 0
            matchesQuery && matchesDebt
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleOnlyDebtors(only: Boolean) {
        _onlyDebtors.value = only
    }

    fun saveCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            customerRepository.insertCustomer(customer)
        }
    }

    fun settleCustomerDebt(customerId: String, amount: Double) {
        viewModelScope.launch {
            customerRepository.settleDebt(customerId, amount)
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            customerRepository.deleteCustomer(customer)
        }
    }
}
