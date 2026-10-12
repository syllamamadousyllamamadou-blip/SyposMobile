package com.sypos.mobile.domain.repository

import com.sypos.mobile.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

interface CustomerRepository {
    fun getAllCustomers(): Flow<List<CustomerEntity>>
    fun getVipCustomers(): Flow<List<CustomerEntity>>
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>
    suspend fun getCustomerById(id: String): CustomerEntity?
    suspend fun insertCustomer(customer: CustomerEntity)
    suspend fun updateCustomer(customer: CustomerEntity)
    suspend fun recordPurchase(customerId: String, amount: Double, date: Long = System.currentTimeMillis())
    suspend fun addDebt(customerId: String, amount: Double)
    suspend fun payDebt(customerId: String, amount: Double)
    suspend fun deleteCustomer(customer: CustomerEntity)
}
