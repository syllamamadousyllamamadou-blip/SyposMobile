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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE tickets ADD COLUMN totalItemsCount INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE tickets ADD COLUMN itemsSummary TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE tickets ADD COLUMN totalPurchaseCost REAL NOT NULL DEFAULT 0.0")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE products ADD COLUMN imageUri TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideSyposDatabase(@ApplicationContext context: Context): SyposDatabase {
        return Room.databaseBuilder(
            context,
            SyposDatabase::class.java,
            "sypos_database"
        )
        .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
        .fallbackToDestructiveMigration()
        .build()
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
