package com.sypos.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val date: Long = 0L,
    val amount: Double = 0.0,
    val description: String = "",
    val category: String? = null
)
