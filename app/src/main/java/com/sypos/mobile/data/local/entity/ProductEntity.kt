package com.sypos.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val salePrice: Double,
    val costPrice: Double = 0.0,
    val stockQuantity: Int = 0,
    val alertStock: Int = 5,
    val barcode: String? = null,
    val categoryId: String? = null,
    val colorHex: String? = null
)
