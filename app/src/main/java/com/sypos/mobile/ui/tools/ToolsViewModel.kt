package com.sypos.mobile.ui.tools

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.ShopSettingsManager
import com.sypos.mobile.data.local.entity.ProductEntity
import com.sypos.mobile.domain.repository.ProductRepository
import com.sypos.mobile.util.BluetoothPrinterHelper
import com.sypos.mobile.util.DeliveryNoteData
import com.sypos.mobile.util.LabelPrintOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val shopSettingsManager: ShopSettingsManager
) : ViewModel() {

    val settings: StateFlow<ShopSettings> = shopSettingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopSettings())

    val products: StateFlow<List<ProductEntity>> = productRepository.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun printBarcodeLabel(
        context: Context,
        productName: String,
        price: Double,
        barcode: String?,
        options: LabelPrintOptions = LabelPrintOptions(),
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val address = settings.value.bluetoothPrinterAddress
            if (address.isNullOrBlank()) {
                onResult(Result.failure(Exception("Aucune imprimante Bluetooth sélectionnée dans les Paramètres")))
                return@launch
            }
            val result = BluetoothPrinterHelper.printBarcodeLabel(
                context = context,
                deviceAddress = address,
                productName = productName,
                price = price,
                barcode = barcode,
                settings = settings.value,
                options = options
            )
            onResult(result)
        }
    }

    fun printDeliveryNote(
        context: Context,
        deliveryData: DeliveryNoteData,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val address = settings.value.bluetoothPrinterAddress
            if (address.isNullOrBlank()) {
                onResult(Result.failure(Exception("Aucune imprimante Bluetooth sélectionnée dans les Paramètres")))
                return@launch
            }
            val result = BluetoothPrinterHelper.printDeliveryNote(
                context = context,
                deviceAddress = address,
                delivery = deliveryData,
                settings = settings.value
            )
            onResult(result)
        }
    }
}
