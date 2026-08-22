package com.sypos.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "ticket_items")
data class TicketItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val ticketId: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Double,
    val total: Double
)
