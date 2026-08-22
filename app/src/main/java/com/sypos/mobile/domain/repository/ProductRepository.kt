package com.sypos.mobile.domain.repository

import com.sypos.mobile.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun getAllProducts(): Flow<List<ProductEntity>>
    fun getProductsByCategory(categoryId: String): Flow<List<ProductEntity>>
    fun searchProducts(query: String): Flow<List<ProductEntity>>
    suspend fun getProductById(id: String): ProductEntity?
    suspend fun getProductByBarcode(barcode: String): ProductEntity?
    suspend fun insertProduct(product: ProductEntity)
    suspend fun updateProduct(product: ProductEntity)
    suspend fun decreaseStock(productId: String, quantity: Int)
    suspend fun increaseStock(productId: String, quantity: Int)
    suspend fun deleteProduct(product: ProductEntity)
}
