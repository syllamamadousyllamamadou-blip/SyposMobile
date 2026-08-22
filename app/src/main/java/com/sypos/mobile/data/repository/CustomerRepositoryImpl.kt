package com.sypos.mobile.data.repository

import com.sypos.mobile.data.local.dao.CustomerDao
import com.sypos.mobile.data.local.entity.CustomerEntity
import com.sypos.mobile.domain.repository.CustomerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CustomerRepositoryImpl @Inject constructor(
    private val customerDao: CustomerDao
) : CustomerRepository {

    override fun getAllCustomers(): Flow<List<CustomerEntity>> {
        return customerDao.getAllCustomers()
    }

    override fun getCustomersWithDebt(): Flow<List<CustomerEntity>> {
        return customerDao.getCustomersWithDebt()
    }

    override fun searchCustomers(query: String): Flow<List<CustomerEntity>> {
        return customerDao.searchCustomers(query)
    }

    override suspend fun getCustomerById(id: String): CustomerEntity? {
        return customerDao.getCustomerById(id)
    }

    override suspend fun insertCustomer(customer: CustomerEntity) {
        customerDao.insertCustomer(customer)
    }

    override suspend fun updateCustomer(customer: CustomerEntity) {
        customerDao.updateCustomer(customer)
    }

    override suspend fun addDebt(customerId: String, amount: Double) {
        customerDao.addDebt(customerId, amount)
    }

    override suspend fun settleDebt(customerId: String, amount: Double) {
        customerDao.reduceDebt(customerId, amount)
    }

    override suspend fun deleteCustomer(customer: CustomerEntity) {
        customerDao.deleteCustomer(customer)
    }
}
