package com.sypos.mobile.domain.repository

import com.sypos.mobile.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<ExpenseEntity>>
    fun getExpensesByDateRange(startDate: Long, endDate: Long): Flow<List<ExpenseEntity>>
    fun getTotalExpensesByDateRange(startDate: Long, endDate: Long): Flow<Double?>
    suspend fun insertExpense(expense: ExpenseEntity)
    suspend fun updateExpense(expense: ExpenseEntity)
    suspend fun deleteExpense(expense: ExpenseEntity)
}
