package com.sypos.mobile.di

import android.content.Context
import androidx.room.Room
import com.sypos.mobile.data.local.SyposDatabase
import com.sypos.mobile.data.local.dao.CategoryDao
import com.sypos.mobile.data.local.dao.CustomerDao
import com.sypos.mobile.data.local.dao.ExpenseDao
import com.sypos.mobile.data.local.dao.ProductDao
import com.sypos.mobile.data.local.dao.PromoCodeDao
import com.sypos.mobile.data.local.dao.TicketDao
import com.sypos.mobile.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSyposDatabase(@ApplicationContext context: Context): SyposDatabase {
        return Room.databaseBuilder(
            context,
            SyposDatabase::class.java,
            "sypos_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideProductDao(database: SyposDatabase): ProductDao {
        return database.productDao()
    }

    @Provides
    @Singleton
    fun provideCategoryDao(database: SyposDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    @Singleton
    fun provideTicketDao(database: SyposDatabase): TicketDao {
        return database.ticketDao()
    }

    @Provides
    @Singleton
    fun provideCustomerDao(database: SyposDatabase): CustomerDao {
        return database.customerDao()
    }

    @Provides
    @Singleton
    fun provideExpenseDao(database: SyposDatabase): ExpenseDao {
        return database.expenseDao()
    }

    @Provides
    @Singleton
    fun provideUserDao(database: SyposDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    @Singleton
    fun providePromoCodeDao(database: SyposDatabase): PromoCodeDao {
        return database.promoCodeDao()
    }
}
