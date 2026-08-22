package com.sypos.mobile.domain.repository

import com.sypos.mobile.data.local.entity.TicketEntity
import com.sypos.mobile.data.local.entity.TicketItemEntity
import com.sypos.mobile.data.local.entity.TicketStatus
import kotlinx.coroutines.flow.Flow

interface TicketRepository {
    fun getAllTickets(): Flow<List<TicketEntity>>
    fun getTicketsByStatus(status: TicketStatus): Flow<List<TicketEntity>>
    fun getTicketsByDateRange(startDate: Long, endDate: Long): Flow<List<TicketEntity>>
    suspend fun getTicketById(id: String): TicketEntity?
    fun getItemsForTicket(ticketId: String): Flow<List<TicketItemEntity>>
    suspend fun getItemsForTicketSync(ticketId: String): List<TicketItemEntity>
    suspend fun createSale(ticket: TicketEntity, items: List<TicketItemEntity>)
    suspend fun holdTicket(ticket: TicketEntity, items: List<TicketItemEntity>)
    suspend fun updateTicketStatus(ticketId: String, status: TicketStatus)
    suspend fun cancelTicket(ticketId: String)
    suspend fun deleteTicket(ticketId: String)
}
