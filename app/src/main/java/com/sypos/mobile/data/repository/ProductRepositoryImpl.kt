package com.sypos.mobile.data.repository

import com.sypos.mobile.data.local.dao.ProductDao
import com.sypos.mobile.data.local.entity.ProductEntity
import com.sypos.mobile.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao
) : ProductRepository {

    override fun getAllProducts(): Flow<List<ProductEntity>> {
        return productDao.getAllProducts()
    }

    override fun getProductsByCategory(categoryId: String): Flow<List<ProductEntity>> {
        return productDao.getProductsByCategory(categoryId)
    }

    override fun searchProducts(query: String): Flow<List<ProductEntity>> {
        return productDao.searchProducts(query)
    }

    override suspend fun getProductById(id: String): ProductEntity? {
        return productDao.getProductById(id)
    }

    override suspend fun getProductByBarcode(barcode: String): ProductEntity? {
        return productDao.getProductByBarcode(barcode)
    }

    override suspend fun insertProduct(product: ProductEntity) {
        productDao.insertProduct(product)
    }

    override suspend fun updateProduct(product: ProductEntity) {
        productDao.updateProduct(product)
    }

    override suspend fun decreaseStock(productId: String, quantity: Int) {
        productDao.decreaseStock(productId, quantity)
    }

    override suspend fun increaseStock(productId: String, quantity: Int) {
        productDao.increaseStock(productId, quantity)
    }

    override suspend fun deleteProduct(product: ProductEntity) {
        productDao.deleteProduct(product)
    }
}
