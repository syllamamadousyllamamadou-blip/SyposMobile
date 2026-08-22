package com.sypos.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sypos.mobile.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE totalDebt > 0 ORDER BY totalDebt DESC")
    fun getCustomersWithDebt(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("UPDATE customers SET totalDebt = totalDebt + :amount WHERE id = :customerId")
    suspend fun addDebt(customerId: String, amount: Double)

    @Query("UPDATE customers SET totalDebt = MAX(0.0, totalDebt - :amount) WHERE id = :customerId")
    suspend fun reduceDebt(customerId: String, amount: Double)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)
}
