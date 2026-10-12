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

    @Query("SELECT * FROM customers ORDER BY totalSpent DESC, name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE totalSpent >= 50000 OR visitCount >= 5 ORDER BY totalSpent DESC")
    fun getVipCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("UPDATE customers SET totalSpent = totalSpent + :amount, visitCount = visitCount + 1, lastPurchaseDate = :date WHERE id = :customerId")
    suspend fun recordPurchase(customerId: String, amount: Double, date: Long = System.currentTimeMillis())

    @Query("UPDATE customers SET debt = debt + :amount WHERE id = :customerId")
    suspend fun addDebt(customerId: String, amount: Double)

    @Query("UPDATE customers SET debt = debt - :amount WHERE id = :customerId")
    suspend fun payDebt(customerId: String, amount: Double)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)
}
