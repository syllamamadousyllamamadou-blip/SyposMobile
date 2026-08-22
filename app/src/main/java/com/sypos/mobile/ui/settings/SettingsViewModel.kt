package com.sypos.mobile.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.ShopSettingsManager
import com.sypos.mobile.data.local.UserRole
import com.sypos.mobile.data.local.entity.PromoCodeEntity
import com.sypos.mobile.domain.repository.PromoCodeRepository
import com.sypos.mobile.domain.repository.TicketRepository
import com.sypos.mobile.util.BluetoothPrinterDevice
import com.sypos.mobile.util.BluetoothPrinterHelper
import com.sypos.mobile.util.ExportHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val shopSettingsManager: ShopSettingsManager,
    private val ticketRepository: TicketRepository,
    private val promoCodeRepository: PromoCodeRepository
) : ViewModel() {

    val settings: StateFlow<ShopSettings> = shopSettingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopSettings())

    val promoCodes: StateFlow<List<PromoCodeEntity>> = promoCodeRepository.getAllPromoCodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSettings(newSettings: ShopSettings) {
        viewModelScope.launch {
            shopSettingsManager.updateSettings(newSettings)
        }
    }

    fun setUserRole(role: UserRole) {
        val updated = settings.value.copy(currentUserRole = role)
        updateSettings(updated)
    }

    fun addPromoCode(code: String, discountPercent: Double, maxUsage: Int) {
        viewModelScope.launch {
            val promo = PromoCodeEntity(
                code = code.trim().uppercase(),
                discountPercent = discountPercent,
                maxUsage = maxUsage,
                currentUsage = 0,
                isActive = true
            )
            promoCodeRepository.insertPromoCode(promo)
        }
    }

    fun togglePromoCode(promo: PromoCodeEntity) {
        viewModelScope.launch {
            promoCodeRepository.updatePromoCode(promo.copy(isActive = !promo.isActive))
        }
    }

    fun deletePromoCode(promo: PromoCodeEntity) {
        viewModelScope.launch {
            promoCodeRepository.deletePromoCode(promo)
        }
    }

    fun saveLicense(key: String, type: String, expiry: Long = 0L) {
        val updated = settings.value.copy(
            isLicensed = true,
            licenseKey = key,
            licenseType = type,
            licenseExpiryDate = expiry
        )
        updateSettings(updated)
    }

    fun getPairedPrinters(context: Context): List<BluetoothPrinterDevice> {
        return BluetoothPrinterHelper.getPairedPrinters(context)
    }

    fun exportTickets(context: Context) {
        viewModelScope.launch {
            val tickets = ticketRepository.getAllTickets().first()
            ExportHelper.exportTicketsToCsv(context, tickets)
        }
    }

    fun testPrinter(context: Context, deviceAddress: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val currentSettings = settings.value
            val result = BluetoothPrinterHelper.testPrinter(context, deviceAddress, currentSettings.shopName)
            onResult(result)
        }
    }
}
