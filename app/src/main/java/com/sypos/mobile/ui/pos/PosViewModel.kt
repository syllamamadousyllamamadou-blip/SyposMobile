package com.sypos.mobile.ui.pos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.ShopSettingsManager
import com.sypos.mobile.data.local.entity.*
import com.sypos.mobile.domain.repository.CategoryRepository
import com.sypos.mobile.domain.repository.CustomerRepository
import com.sypos.mobile.domain.repository.ProductRepository
import com.sypos.mobile.domain.repository.PromoCodeRepository
import com.sypos.mobile.domain.repository.TicketRepository
import com.sypos.mobile.util.BluetoothPrinterHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class CartItem(
    val product: ProductEntity,
    var quantity: Int = 1,
    var customPrice: Double? = null,
    var discountPercent: Double = 0.0
) {
    val unitPrice: Double
        get() = customPrice ?: product.salePrice

    val total: Double
        get() {
            val base = unitPrice * quantity
            return base * (1.0 - (discountPercent / 100.0))
        }
}

data class PosUiState(
    val cartItems: List<CartItem> = emptyList(),
    val selectedOrderType: OrderType = OrderType.TAKEAWAY,
    val selectedCustomer: CustomerEntity? = null,
    val appliedPromoCode: PromoCodeEntity? = null,
    val globalDiscountPercent: Double = 0.0,
    val taxRatePercent: Double = 18.0,
    val taxEnabled: Boolean = false,
    val lastCompletedTicket: TicketEntity? = null,
    val lastCompletedItems: List<TicketItemEntity> = emptyList(),
    val showReceipt: Boolean = false,
    val isPaymentSuccess: Boolean = false,
    val unrecognizedBarcode: String? = null,
    val notificationBanner: String? = null
) {
    val subTotal: Double
        get() = cartItems.sumOf { it.total }

    val effectiveDiscountPercent: Double
        get() = maxOf(globalDiscountPercent, appliedPromoCode?.discountPercent ?: 0.0)

    val discountAmount: Double
        get() = subTotal * (effectiveDiscountPercent / 100.0)

    val netAfterDiscount: Double
        get() = (subTotal - discountAmount).coerceAtLeast(0.0)

    val taxAmount: Double
        get() = if (taxEnabled) netAfterDiscount * (taxRatePercent / 100.0) else 0.0

    val totalAmount: Double
        get() = netAfterDiscount + taxAmount

    val totalItemsCount: Int
        get() = cartItems.sumOf { it.quantity }
}

@HiltViewModel
class PosViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val ticketRepository: TicketRepository,
    private val customerRepository: CustomerRepository,
    private val promoCodeRepository: PromoCodeRepository,
    private val shopSettingsManager: ShopSettingsManager
) : ViewModel() {

    val settings: StateFlow<ShopSettings> = shopSettingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopSettings())

    private val _uiState = MutableStateFlow(PosUiState())
    val uiState: StateFlow<PosUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    init {
        viewModelScope.launch {
            settings.collect { currentSettings ->
                _uiState.update {
                    it.copy(
                        taxEnabled = currentSettings.taxEnabled,
                        taxRatePercent = currentSettings.taxRatePercent
                    )
                }
            }
        }
    }

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

    val customers: StateFlow<List<CustomerEntity>> = customerRepository.getAllCustomers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val heldTickets: StateFlow<List<TicketEntity>> = ticketRepository.getTicketsByStatus(TicketStatus.ON_HOLD)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelect(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun addToCart(product: ProductEntity) {
        val currentSettings = settings.value
        val currentList = _uiState.value.cartItems.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == product.id }

        if (!currentSettings.allowNegativeStock && product.stockQuantity <= 0) {
            showNotification("⚠️ ${product.name} est en rupture de stock !")
            return
        }

        if (index >= 0) {
            val existing = currentList[index]
            if (!currentSettings.allowNegativeStock && existing.quantity >= product.stockQuantity) {
                showNotification("⚠️ Stock max atteint pour ${product.name} (${product.stockQuantity} dispo)")
                return
            }
            currentList[index] = existing.copy(quantity = existing.quantity + 1)
        } else {
            currentList.add(CartItem(product = product, quantity = 1))
        }
        _uiState.update { it.copy(cartItems = currentList) }
        showNotification("🛒 +1 ${product.name}")
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            val trimmed = barcode.trim()
            val allProducts = productRepository.getAllProducts().first()

            // Robust multi-format matching
            val product = allProducts.find { p ->
                val pBarcode = p.barcode?.trim() ?: ""
                pBarcode.equals(trimmed, ignoreCase = true) ||
                        pBarcode.trimStart('0') == trimmed.trimStart('0') ||
                        p.id == trimmed
            }

            if (product != null) {
                addToCart(product)
            } else {
                _uiState.update { it.copy(unrecognizedBarcode = trimmed) }
                showNotification("❌ Produit non répertorié : $trimmed")
            }
        }
    }

    fun clearUnrecognizedBarcode() {
        _uiState.update { it.copy(unrecognizedBarcode = null) }
    }

    fun increaseQuantity(productId: String) {
        val currentSettings = settings.value
        val currentList = _uiState.value.cartItems.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            val item = currentList[index]
            if (!currentSettings.allowNegativeStock && item.quantity >= item.product.stockQuantity) {
                showNotification("⚠️ Stock maximum en rayon atteint (${item.product.stockQuantity})")
                return
            }
            currentList[index] = item.copy(quantity = item.quantity + 1)
            _uiState.update { it.copy(cartItems = currentList) }
        }
    }

    fun decreaseQuantity(productId: String) {
        val currentList = _uiState.value.cartItems.toMutableList()
        val index = currentList.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            val item = currentList[index]
            if (item.quantity > 1) {
                currentList[index] = item.copy(quantity = item.quantity - 1)
            } else {
                currentList.removeAt(index)
            }
            _uiState.update { it.copy(cartItems = currentList) }
        }
    }

    fun removeFromCart(productId: String) {
        val currentList = _uiState.value.cartItems.filter { it.product.id != productId }
        _uiState.update { it.copy(cartItems = currentList) }
    }

    fun setOrderType(orderType: OrderType) {
        _uiState.update { it.copy(selectedOrderType = orderType) }
    }

    fun setSelectedCustomer(customer: CustomerEntity?) {
        _uiState.update { it.copy(selectedCustomer = customer) }
    }

    fun setGlobalDiscount(percent: Double) {
        _uiState.update { it.copy(globalDiscountPercent = percent) }
    }

    fun applyPromoCode(code: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val promo = promoCodeRepository.getActivePromoByCode(code)
            if (promo != null) {
                _uiState.update { it.copy(appliedPromoCode = promo) }
                showNotification("🎟️ Code ${promo.code} appliqué (-${promo.discountPercent.toInt()}%)")
                onResult(true, "Code promo activé (-${promo.discountPercent.toInt()}%)")
            } else {
                onResult(false, "Code promo introuvable, inactif ou épuisé")
            }
        }
    }

    fun removePromoCode() {
        _uiState.update { it.copy(appliedPromoCode = null) }
    }

    fun clearCart() {
        _uiState.update {
            it.copy(
                cartItems = emptyList(),
                selectedCustomer = null,
                appliedPromoCode = null,
                globalDiscountPercent = 0.0
            )
        }
    }

    fun showNotification(message: String) {
        _uiState.update { it.copy(notificationBanner = message) }
    }

    fun clearNotification() {
        _uiState.update { it.copy(notificationBanner = null) }
    }

    fun holdCurrentCart(note: String? = null) {
        val currentState = _uiState.value
        if (currentState.cartItems.isEmpty()) return

        viewModelScope.launch {
            val currentSettings = settings.value
            val ticketNumber = generateTicketNumber()
            val ticketId = UUID.randomUUID().toString()
            val ticket = TicketEntity(
                id = ticketId,
                ticketNumber = ticketNumber,
                date = System.currentTimeMillis(),
                status = TicketStatus.ON_HOLD,
                orderType = currentState.selectedOrderType,
                subTotal = currentState.subTotal,
                taxAmount = currentState.taxAmount,
                totalAmount = currentState.totalAmount,
                discount = currentState.discountAmount,
                paymentMethod = null,
                amountPaid = 0.0,
                changeReturned = 0.0,
                customerId = currentState.selectedCustomer?.id,
                sellerName = currentSettings.sellerName,
                note = note?.trim()?.ifBlank { null }
            )

            val items = currentState.cartItems.map {
                TicketItemEntity(
                    ticketId = ticketId,
                    productId = it.product.id,
                    productName = it.product.name,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice,
                    total = it.total
                )
            }

            ticketRepository.holdTicket(ticket, items)
            clearCart()
            val noteMsg = if (!note.isNullOrBlank()) " ($note)" else ""
            showNotification("⏸️ Ticket mis en attente$noteMsg")
        }
    }

    fun resumeHeldTicket(ticket: TicketEntity) {
        viewModelScope.launch {
            val items = ticketRepository.getItemsForTicketSync(ticket.id)
            val allProducts = productRepository.getAllProducts().first()

            val cartItems = items.map { item ->
                val prod = allProducts.find { it.id == item.productId } ?: ProductEntity(
                    id = item.productId,
                    name = item.productName,
                    salePrice = item.unitPrice
                )
                CartItem(product = prod, quantity = item.quantity, customPrice = item.unitPrice)
            }

            val customer = if (ticket.customerId != null) customerRepository.getCustomerById(ticket.customerId) else null

            _uiState.update {
                it.copy(
                    cartItems = cartItems,
                    selectedOrderType = ticket.orderType,
                    selectedCustomer = customer
                )
            }

            ticketRepository.deleteTicket(ticket.id)
            val label = if (!ticket.note.isNullOrBlank()) ticket.note else ticket.ticketNumber
            showNotification("▶️ Reprise du panier : $label")
        }
    }

    fun processPayment(
        context: Context,
        paymentMethod: PaymentMethod,
        amountReceived: Double,
        onSuccess: (TicketEntity) -> Unit
    ) {
        val currentState = _uiState.value
        if (currentState.cartItems.isEmpty()) return

        viewModelScope.launch {
            val currentSettings = settings.value
            val ticketNumber = generateTicketNumber()
            val ticketId = UUID.randomUUID().toString()
            val total = currentState.totalAmount
            val change = if (paymentMethod == PaymentMethod.CASH) (amountReceived - total).coerceAtLeast(0.0) else 0.0
            val status = if (paymentMethod == PaymentMethod.CASH || amountReceived >= total) TicketStatus.PAID else TicketStatus.CREDIT

            val ticket = TicketEntity(
                id = ticketId,
                ticketNumber = ticketNumber,
                date = System.currentTimeMillis(),
                status = status,
                orderType = currentState.selectedOrderType,
                subTotal = currentState.subTotal,
                taxAmount = currentState.taxAmount,
                totalAmount = total,
                discount = currentState.discountAmount,
                paymentMethod = paymentMethod,
                amountPaid = amountReceived,
                changeReturned = change,
                customerId = currentState.selectedCustomer?.id,
                sellerName = currentSettings.sellerName,
                note = null
            )

            val items = currentState.cartItems.map {
                TicketItemEntity(
                    ticketId = ticketId,
                    productId = it.product.id,
                    productName = it.product.name,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice,
                    total = it.total
                )
            }

            ticketRepository.createSale(ticket, items)

            // Increment Promo code usage if used
            currentState.appliedPromoCode?.let { promo ->
                promoCodeRepository.incrementUsage(promo.id)
            }

            // Auto-print immediately if printer configured & autoPrintReceipt enabled
            if (currentSettings.autoPrintReceipt && !currentSettings.bluetoothPrinterAddress.isNullOrBlank()) {
                launch {
                    BluetoothPrinterHelper.printTicket(
                        context = context,
                        deviceAddress = currentSettings.bluetoothPrinterAddress,
                        ticket = ticket,
                        items = items,
                        customerName = currentState.selectedCustomer?.name,
                        settings = currentSettings
                    )
                }
            }

            _uiState.update {
                it.copy(
                    lastCompletedTicket = ticket,
                    lastCompletedItems = items,
                    showReceipt = true,
                    isPaymentSuccess = true
                )
            }
            onSuccess(ticket)
        }
    }

    fun closeReceipt() {
        _uiState.update {
            it.copy(
                showReceipt = false,
                lastCompletedTicket = null,
                lastCompletedItems = emptyList()
            )
        }
        clearCart()
    }

    private fun generateTicketNumber(): String {
        val dateStr = SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date())
        val randomDigits = (1000..9999).random()
        return "TK-$dateStr-$randomDigits"
    }
}
