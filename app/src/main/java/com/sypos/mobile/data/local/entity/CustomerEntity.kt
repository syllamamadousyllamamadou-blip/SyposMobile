package com.sypos.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val totalSpent: Double = 0.0,
    val debt: Double = 0.0,
    val visitCount: Int = 0,
    val lastPurchaseDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
