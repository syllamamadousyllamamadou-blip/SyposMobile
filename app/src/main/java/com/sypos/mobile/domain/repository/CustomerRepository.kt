package com.sypos.mobile.domain.repository

import com.sypos.mobile.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

interface CustomerRepository {
    fun getAllCustomers(): Flow<List<CustomerEntity>>
    fun getCustomersWithDebt(): Flow<List<CustomerEntity>>
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>
    suspend fun getCustomerById(id: String): CustomerEntity?
    suspend fun insertCustomer(customer: CustomerEntity)
    suspend fun updateCustomer(customer: CustomerEntity)
    suspend fun addDebt(customerId: String, amount: Double)
    suspend fun settleDebt(customerId: String, amount: Double)
    suspend fun deleteCustomer(customer: CustomerEntity)
}
