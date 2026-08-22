package com.sypos.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sypos.mobile.data.local.dao.*
import com.sypos.mobile.data.local.entity.*

@Database(
    entities = [
        ProductEntity::class,
        CategoryEntity::class,
        TicketEntity::class,
        TicketItemEntity::class,
        CustomerEntity::class,
        ExpenseEntity::class,
        UserEntity::class,
        PromoCodeEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SyposDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun ticketDao(): TicketDao
    abstract fun customerDao(): CustomerDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun userDao(): UserDao
    abstract fun promoCodeDao(): PromoCodeDao
}
