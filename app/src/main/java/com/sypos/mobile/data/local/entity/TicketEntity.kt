package com.sypos.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val ticketNumber: String = "",
    val date: Long = 0L,
    val status: TicketStatus = TicketStatus.PAID,
    val orderType: OrderType = OrderType.DINE_IN,
    val subTotal: Double = 0.0,
    val taxAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val discount: Double = 0.0,
    val paymentMethod: PaymentMethod? = null,
    val amountPaid: Double = 0.0,
    val changeReturned: Double = 0.0,
    val customerId: String? = null,
    val sellerName: String? = null,
    val note: String? = null,
    val splitCashAmount: Double? = null,
    val splitOtherAmount: Double? = null,
    val splitOtherMethod: PaymentMethod? = null,
    val totalItemsCount: Int = 1,
    val itemsSummary: String = "",
    val totalPurchaseCost: Double = 0.0
)

enum class TicketStatus {
    PAID, ON_HOLD, CANCELLED, CREDIT
}

enum class OrderType {
    DINE_IN, TAKEAWAY, DELIVERY
}

enum class PaymentMethod {
    CASH, WAVE, ORANGE_MONEY, MTN, MOOV, CARD, SPLIT, CREDIT
}
