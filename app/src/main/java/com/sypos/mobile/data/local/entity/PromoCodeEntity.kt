package com.sypos.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "promo_codes")
data class PromoCodeEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val code: String, // ex: "PROMO10", "SOLDES"
    val discountPercent: Double, // ex: 10.0 for 10%
    val maxUsage: Int = 100,
    val currentUsage: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
