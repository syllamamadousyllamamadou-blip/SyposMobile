package com.sypos.mobile.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class BusinessMode {
    SUPERMARKET, // Mode Boutique / Supermarché (Strict stock, no order type)
    RESTAURANT   // Mode Restaurant / Fast-Food / Snack (Order types, flexible stock, prints order type)
}

enum class UserRole {
    ADMIN,   // Full privileges (settings, reports, pin, price edit, cancel)
    CASHIER  // Cashier only (POS, product search, daily sales view)
}

data class ShopSettings(
    val shopName: String = "SYPOS COMMERCE",
    val shopAddress: String = "Marché Central, Boutique N° 12",
    val shopPhone: String = "+221 77 000 00 00",
    val receiptFooter: String = "Merci de votre visite et à très bientôt !",
    val currency: String = "CFA",
    val businessMode: BusinessMode = BusinessMode.SUPERMARKET,
    val currentUserRole: UserRole = UserRole.ADMIN,
    val sellerName: String = "Vendeur 1",
    val showPublisherSignature: Boolean = true,
    val publisherSignatureText: String = "Solution: SYPOS MOBILE 0758245530",
    val taxEnabled: Boolean = false,
    val taxRatePercent: Double = 18.0,
    val allowNegativeStock: Boolean = false,
    val autoPrintReceipt: Boolean = true,
    val adminPin: String = "1234",
    val cashierPin: String = "0000",
    val pinLockEnabled: Boolean = false,
    val bluetoothPrinterAddress: String? = null,
    val bluetoothPrinterName: String? = null,
    val isLicensed: Boolean = true,
    val licenseKey: String = "SYPOS-PRO-8899-7744",
    val licenseType: String = "Licence Commerciale Illimitée",
    val licenseExpiryDate: Long = 0L // 0L = Illimité
)

@Singleton
class ShopSettingsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sypos_shop_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ShopSettings> = _settings.asStateFlow()

    private fun loadSettings(): ShopSettings {
        val modeStr = prefs.getString("businessMode", BusinessMode.SUPERMARKET.name) ?: BusinessMode.SUPERMARKET.name
        val roleStr = prefs.getString("currentUserRole", UserRole.ADMIN.name) ?: UserRole.ADMIN.name

        return ShopSettings(
            shopName = prefs.getString("shopName", "SYPOS COMMERCE") ?: "SYPOS COMMERCE",
            shopAddress = prefs.getString("shopAddress", "Marché Central") ?: "Marché Central",
            shopPhone = prefs.getString("shopPhone", "") ?: "",
            receiptFooter = prefs.getString("receiptFooter", "Merci de votre visite et à très bientôt !") ?: "Merci de votre visite et à très bientôt !",
            currency = prefs.getString("currency", "CFA") ?: "CFA",
            businessMode = try { BusinessMode.valueOf(modeStr) } catch (e: Exception) { BusinessMode.SUPERMARKET },
            currentUserRole = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.ADMIN },
            sellerName = prefs.getString("sellerName", "Vendeur 1") ?: "Vendeur 1",
            showPublisherSignature = prefs.getBoolean("showPublisherSignature", true),
            publisherSignatureText = prefs.getString("publisherSignatureText", "Solution: SYPOS MOBILE 0758245530") ?: "Solution: SYPOS MOBILE 0758245530",
            taxEnabled = prefs.getBoolean("taxEnabled", false),
            taxRatePercent = prefs.getFloat("taxRatePercent", 18.0f).toDouble(),
            allowNegativeStock = prefs.getBoolean("allowNegativeStock", false),
            autoPrintReceipt = prefs.getBoolean("autoPrintReceipt", true),
            adminPin = prefs.getString("adminPin", "1234") ?: "1234",
            cashierPin = prefs.getString("cashierPin", "0000") ?: "0000",
            pinLockEnabled = prefs.getBoolean("pinLockEnabled", false),
            bluetoothPrinterAddress = prefs.getString("bluetoothPrinterAddress", null),
            bluetoothPrinterName = prefs.getString("bluetoothPrinterName", null),
            isLicensed = prefs.getBoolean("isLicensed", true),
            licenseKey = prefs.getString("licenseKey", "SYPOS-PRO-8899-7744") ?: "SYPOS-PRO-8899-7744",
            licenseType = prefs.getString("licenseType", "Licence Commerciale Illimitée") ?: "Licence Commerciale Illimitée",
            licenseExpiryDate = prefs.getLong("licenseExpiryDate", 0L)
        )
    }

    fun updateSettings(newSettings: ShopSettings) {
        prefs.edit().apply {
            putString("shopName", newSettings.shopName)
            putString("shopAddress", newSettings.shopAddress)
            putString("shopPhone", newSettings.shopPhone)
            putString("receiptFooter", newSettings.receiptFooter)
            putString("currency", newSettings.currency)
            putString("businessMode", newSettings.businessMode.name)
            putString("currentUserRole", newSettings.currentUserRole.name)
            putString("sellerName", newSettings.sellerName)
            putBoolean("showPublisherSignature", newSettings.showPublisherSignature)
            putString("publisherSignatureText", newSettings.publisherSignatureText)
            putBoolean("taxEnabled", newSettings.taxEnabled)
            putFloat("taxRatePercent", newSettings.taxRatePercent.toFloat())
            putBoolean("allowNegativeStock", newSettings.allowNegativeStock)
            putBoolean("autoPrintReceipt", newSettings.autoPrintReceipt)
            putString("adminPin", newSettings.adminPin)
            putString("cashierPin", newSettings.cashierPin)
            putBoolean("pinLockEnabled", newSettings.pinLockEnabled)
            putString("bluetoothPrinterAddress", newSettings.bluetoothPrinterAddress)
            putString("bluetoothPrinterName", newSettings.bluetoothPrinterName)
            putBoolean("isLicensed", newSettings.isLicensed)
            putString("licenseKey", newSettings.licenseKey)
            putString("licenseType", newSettings.licenseType)
            putLong("licenseExpiryDate", newSettings.licenseExpiryDate)
            apply()
        }
        _settings.value = newSettings
    }

    fun setUserRole(role: UserRole) {
        updateSettings(_settings.value.copy(currentUserRole = role))
    }
}
