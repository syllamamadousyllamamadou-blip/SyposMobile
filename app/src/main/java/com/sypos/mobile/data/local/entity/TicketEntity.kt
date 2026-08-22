package com.sypos.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val ticketNumber: String,
    val date: Long,
    val status: TicketStatus,
    val orderType: OrderType,
    val subTotal: Double,
    val taxAmount: Double,
    val totalAmount: Double,
    val discount: Double,
    val paymentMethod: PaymentMethod?,
    val amountPaid: Double,
    val changeReturned: Double,
    val customerId: String?,
    val sellerName: String? = null,
    val note: String? = null
)

enum class TicketStatus {
    PAID, ON_HOLD, CANCELLED, CREDIT
}

enum class OrderType {
    DINE_IN, TAKEAWAY, DELIVERY
}

enum class PaymentMethod {
    CASH, WAVE, ORANGE_MONEY, MTN, MOOV, CARD, CREDIT
}
