package com.sypos.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sypos.mobile.data.local.entity.TicketEntity
import com.sypos.mobile.data.local.entity.TicketItemEntity
import com.sypos.mobile.data.local.entity.TicketStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketDao {

    @Query("SELECT * FROM tickets ORDER BY date DESC")
    fun getAllTickets(): Flow<List<TicketEntity>>

    @Query("SELECT * FROM tickets WHERE status = :status ORDER BY date DESC")
    fun getTicketsByStatus(status: TicketStatus): Flow<List<TicketEntity>>

    @Query("SELECT * FROM tickets WHERE id = :id")
    suspend fun getTicketById(id: String): TicketEntity?

    @Query("SELECT * FROM tickets WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getTicketsByDateRange(startDate: Long, endDate: Long): Flow<List<TicketEntity>>

    @Query("SELECT * FROM ticket_items WHERE ticketId = :ticketId")
    fun getItemsForTicket(ticketId: String): Flow<List<TicketItemEntity>>

    @Query("SELECT * FROM ticket_items WHERE ticketId = :ticketId")
    suspend fun getItemsForTicketSync(ticketId: String): List<TicketItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicket(ticket: TicketEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicketItems(items: List<TicketItemEntity>)

    @Update
    suspend fun updateTicket(ticket: TicketEntity)

    @Query("UPDATE tickets SET status = :status WHERE id = :ticketId")
    suspend fun updateTicketStatus(ticketId: String, status: TicketStatus)

    @Query("DELETE FROM ticket_items WHERE ticketId = :ticketId")
    suspend fun deleteTicketItems(ticketId: String)

    @Query("DELETE FROM tickets WHERE id = :ticketId")
    suspend fun deleteTicket(ticketId: String)

    @Transaction
    suspend fun insertFullTicket(ticket: TicketEntity, items: List<TicketItemEntity>) {
        insertTicket(ticket)
        insertTicketItems(items)
    }
}
