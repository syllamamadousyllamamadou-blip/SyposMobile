package com.sypos.mobile.di

import com.sypos.mobile.data.repository.CategoryRepositoryImpl
import com.sypos.mobile.data.repository.CustomerRepositoryImpl
import com.sypos.mobile.data.repository.ExpenseRepositoryImpl
import com.sypos.mobile.data.repository.ProductRepositoryImpl
import com.sypos.mobile.data.repository.TicketRepositoryImpl
import com.sypos.mobile.domain.repository.CategoryRepository
import com.sypos.mobile.domain.repository.CustomerRepository
import com.sypos.mobile.domain.repository.ExpenseRepository
import com.sypos.mobile.domain.repository.ProductRepository
import com.sypos.mobile.domain.repository.TicketRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProductRepository(
        productRepositoryImpl: ProductRepositoryImpl
    ): ProductRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(
        categoryRepositoryImpl: CategoryRepositoryImpl
    ): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTicketRepository(
        ticketRepositoryImpl: TicketRepositoryImpl
    ): TicketRepository

    @Binds
    @Singleton
    abstract fun bindCustomerRepository(
        customerRepositoryImpl: CustomerRepositoryImpl
    ): CustomerRepository

    @Binds
    @Singleton
    abstract fun bindExpenseRepository(
        expenseRepositoryImpl: ExpenseRepositoryImpl
    ): ExpenseRepository

    @Binds
    @Singleton
    abstract fun bindPromoCodeRepository(
        promoCodeRepositoryImpl: com.sypos.mobile.data.repository.PromoCodeRepositoryImpl
    ): com.sypos.mobile.domain.repository.PromoCodeRepository
}
