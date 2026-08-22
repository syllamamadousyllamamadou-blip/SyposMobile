package com.sypos.mobile.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.ShopSettingsManager
import com.sypos.mobile.data.local.entity.CategoryEntity
import com.sypos.mobile.data.local.entity.ProductEntity
import com.sypos.mobile.domain.repository.CategoryRepository
import com.sypos.mobile.domain.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val shopSettingsManager: ShopSettingsManager
) : ViewModel() {

    val settings: StateFlow<ShopSettings> = shopSettingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopSettings())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductEntity>> = combine(
        productRepository.getAllProducts(),
        _searchQuery,
        _selectedCategoryId
    ) { allProducts, query, categoryId ->
        allProducts.filter { product ->
            val matchesQuery = query.isBlank() ||
                    product.name.contains(query, ignoreCase = true) ||
                    (product.barcode != null && product.barcode.contains(query, ignoreCase = true))
            val matchesCategory = categoryId == null || product.categoryId == categoryId
            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelect(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    suspend fun getProductById(id: String): ProductEntity? {
        return productRepository.getProductById(id)
    }

    fun saveProduct(product: ProductEntity) {
        viewModelScope.launch {
            productRepository.insertProduct(product)
        }
    }

    fun updateStock(productId: String, delta: Int) {
        viewModelScope.launch {
            if (delta > 0) {
                productRepository.increaseStock(productId, delta)
            } else if (delta < 0) {
                productRepository.decreaseStock(productId, -delta)
            }
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            productRepository.deleteProduct(product)
        }
    }

    fun addCategory(category: CategoryEntity) {
        viewModelScope.launch {
            categoryRepository.insertCategory(category)
        }
    }
}
