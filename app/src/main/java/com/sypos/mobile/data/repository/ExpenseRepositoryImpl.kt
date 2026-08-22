package com.sypos.mobile.data.repository

import com.sypos.mobile.data.local.dao.ExpenseDao
import com.sypos.mobile.data.local.entity.ExpenseEntity
import com.sypos.mobile.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao
) : ExpenseRepository {

    override fun getAllExpenses(): Flow<List<ExpenseEntity>> {
        return expenseDao.getAllExpenses()
    }

    override fun getExpensesByDateRange(startDate: Long, endDate: Long): Flow<List<ExpenseEntity>> {
        return expenseDao.getExpensesByDateRange(startDate, endDate)
    }

    override fun getTotalExpensesByDateRange(startDate: Long, endDate: Long): Flow<Double?> {
        return expenseDao.getTotalExpensesByDateRange(startDate, endDate)
    }

    override suspend fun insertExpense(expense: ExpenseEntity) {
        expenseDao.insertExpense(expense)
    }

    override suspend fun updateExpense(expense: ExpenseEntity) {
        expenseDao.updateExpense(expense)
    }

    override suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpense(expense)
    }
}
