package com.sypos.mobile.data.repository

import com.sypos.mobile.data.local.dao.CustomerDao
import com.sypos.mobile.data.local.dao.ProductDao
import com.sypos.mobile.data.local.dao.TicketDao
import com.sypos.mobile.data.local.entity.TicketEntity
import com.sypos.mobile.data.local.entity.TicketItemEntity
import com.sypos.mobile.data.local.entity.TicketStatus
import com.sypos.mobile.domain.repository.TicketRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TicketRepositoryImpl @Inject constructor(
    private val ticketDao: TicketDao,
    private val productDao: ProductDao,
    private val customerDao: CustomerDao
) : TicketRepository {

    override fun getAllTickets(): Flow<List<TicketEntity>> {
        return ticketDao.getAllTickets()
    }

    override fun getTicketsByStatus(status: TicketStatus): Flow<List<TicketEntity>> {
        return ticketDao.getTicketsByStatus(status)
    }

    override fun getTicketsByDateRange(startDate: Long, endDate: Long): Flow<List<TicketEntity>> {
        return ticketDao.getTicketsByDateRange(startDate, endDate)
    }

    override suspend fun getTicketById(id: String): TicketEntity? {
        return ticketDao.getTicketById(id)
    }

    override fun getItemsForTicket(ticketId: String): Flow<List<TicketItemEntity>> {
        return ticketDao.getItemsForTicket(ticketId)
    }

    override suspend fun getItemsForTicketSync(ticketId: String): List<TicketItemEntity> {
        return ticketDao.getItemsForTicketSync(ticketId)
    }

    override suspend fun createSale(ticket: TicketEntity, items: List<TicketItemEntity>) {
        ticketDao.insertFullTicket(ticket, items)
        
        // Decrease stock for each item
        for (item in items) {
            productDao.decreaseStock(item.productId, item.quantity)
        }

        // If ticket is on credit and assigned to a customer, add debt
        if (ticket.status == TicketStatus.CREDIT && !ticket.customerId.isNullOrBlank()) {
            val remainingDebt = ticket.totalAmount - ticket.amountPaid
            if (remainingDebt > 0) {
                customerDao.addDebt(ticket.customerId, remainingDebt)
            }
        }
    }

    override suspend fun holdTicket(ticket: TicketEntity, items: List<TicketItemEntity>) {
        ticketDao.insertFullTicket(ticket.copy(status = TicketStatus.ON_HOLD), items)
    }

    override suspend fun updateTicketStatus(ticketId: String, status: TicketStatus) {
        ticketDao.updateTicketStatus(ticketId, status)
    }

    override suspend fun cancelTicket(ticketId: String) {
        val ticket = ticketDao.getTicketById(ticketId) ?: return
        if (ticket.status == TicketStatus.CANCELLED) return

        // Restore stock if it was a paid or credit ticket
        if (ticket.status == TicketStatus.PAID || ticket.status == TicketStatus.CREDIT) {
            val items = ticketDao.getItemsForTicketSync(ticketId)
            for (item in items) {
                productDao.increaseStock(item.productId, item.quantity)
            }
            if (ticket.status == TicketStatus.CREDIT && !ticket.customerId.isNullOrBlank()) {
                val debtToRemove = ticket.totalAmount - ticket.amountPaid
                if (debtToRemove > 0) {
                    customerDao.reduceDebt(ticket.customerId, debtToRemove)
                }
            }
        }

        ticketDao.updateTicketStatus(ticketId, TicketStatus.CANCELLED)
    }

    override suspend fun deleteTicket(ticketId: String) {
        ticketDao.deleteTicketItems(ticketId)
        ticketDao.deleteTicket(ticketId)
    }
}
