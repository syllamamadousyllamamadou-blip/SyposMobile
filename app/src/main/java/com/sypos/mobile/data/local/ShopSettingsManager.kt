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
    val shopName: String = "KAYO COMMERCE",
    val shopAddress: String = "Marché Central, Boutique N° 12",
    val shopPhone: String = "+221 77 000 00 00",
    val receiptFooter: String = "Merci de votre visite et à très bientôt !",
    val currency: String = "CFA",
    val businessMode: BusinessMode = BusinessMode.SUPERMARKET,
    val currentUserRole: UserRole = UserRole.ADMIN,
    val sellerName: String = "Vendeur 1",
    val savedSellers: String = "", // Comma-separated list of seller names
    val showPublisherSignature: Boolean = true,
    val publisherSignatureText: String = "Solution: KAYO MOBILE 0758245530",
    val taxEnabled: Boolean = false,
    val taxRatePercent: Double = 18.0,
    val allowNegativeStock: Boolean = false,
    val autoPrintReceipt: Boolean = true,
    val adminPin: String = "1234",
    val cashierPin: String = "0000",
    val pinLockEnabled: Boolean = false,
    val bluetoothPrinterAddress: String? = null,
    val bluetoothPrinterName: String? = null,
    val isLicensed: Boolean = false,
    val licenseKey: String = "",
    val licenseType: String = "Non Activé",
    val licenseExpiryDate: Long = 0L, // 0L = Illimité
    val lastKnownTimestamp: Long = 0L,
    val shopLogoUri: String? = null,
    val currentShopId: String = ""
)

@Singleton
class ShopSettingsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("KAYO_shop_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ShopSettings> = _settings.asStateFlow()

    private fun loadSettings(): ShopSettings {
        val modeStr = prefs.getString("businessMode", BusinessMode.SUPERMARKET.name) ?: BusinessMode.SUPERMARKET.name
        val roleStr = prefs.getString("currentUserRole", UserRole.ADMIN.name) ?: UserRole.ADMIN.name

        return ShopSettings(
            shopName = prefs.getString("shopName", "KAYO COMMERCE") ?: "KAYO COMMERCE",
            shopAddress = prefs.getString("shopAddress", "Marché Central") ?: "Marché Central",
            shopPhone = prefs.getString("shopPhone", "") ?: "",
            receiptFooter = prefs.getString("receiptFooter", "Merci de votre visite et à très bientôt !") ?: "Merci de votre visite et à très bientôt !",
            currency = prefs.getString("currency", "CFA") ?: "CFA",
            businessMode = try { BusinessMode.valueOf(modeStr) } catch (e: Exception) { BusinessMode.SUPERMARKET },
            currentUserRole = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.ADMIN },
            sellerName = prefs.getString("sellerName", "Vendeur 1") ?: "Vendeur 1",
            savedSellers = prefs.getString("savedSellers", "") ?: "",
            showPublisherSignature = prefs.getBoolean("showPublisherSignature", true),
            publisherSignatureText = prefs.getString("publisherSignatureText", "Solution: KAYO MOBILE 0758245530") ?: "Solution: KAYO MOBILE 0758245530",
            taxEnabled = prefs.getBoolean("taxEnabled", false),
            taxRatePercent = prefs.getFloat("taxRatePercent", 18.0f).toDouble(),
            allowNegativeStock = prefs.getBoolean("allowNegativeStock", false),
            autoPrintReceipt = prefs.getBoolean("autoPrintReceipt", true),
            adminPin = prefs.getString("adminPin", "1234") ?: "1234",
            cashierPin = prefs.getString("cashierPin", "0000") ?: "0000",
            pinLockEnabled = prefs.getBoolean("pinLockEnabled", false),
            bluetoothPrinterAddress = prefs.getString("bluetoothPrinterAddress", null),
            bluetoothPrinterName = prefs.getString("bluetoothPrinterName", null),
            isLicensed = prefs.getBoolean("isLicensed", false),
            licenseKey = prefs.getString("licenseKey", "") ?: "",
            licenseType = prefs.getString("licenseType", "Non Activé") ?: "Non Activé",
            licenseExpiryDate = prefs.getLong("licenseExpiryDate", 0L),
            lastKnownTimestamp = prefs.getLong("lastKnownTimestamp", 0L),
            shopLogoUri = prefs.getString("shopLogoUri", null),
            currentShopId = prefs.getString("currentShopId", "") ?: ""
        )
    }

    fun updateSettingsLocally(newSettings: ShopSettings) {
        // Just update local without triggering another push (actually updateSettings is what SettingsViewModel calls, but here we just do the same thing)
        updateSettings(newSettings)
    }

    fun updateSettings(newSettings: ShopSettings) {
        val currentNow = System.currentTimeMillis()
        val updatedTimestamp = if (currentNow > newSettings.lastKnownTimestamp) currentNow else newSettings.lastKnownTimestamp
        val settingsToSave = newSettings.copy(lastKnownTimestamp = updatedTimestamp)

        prefs.edit().apply {
            putString("shopName", settingsToSave.shopName)
            putString("shopAddress", settingsToSave.shopAddress)
            putString("shopPhone", settingsToSave.shopPhone)
            putString("receiptFooter", settingsToSave.receiptFooter)
            putString("currency", settingsToSave.currency)
            putString("businessMode", settingsToSave.businessMode.name)
            putString("currentUserRole", settingsToSave.currentUserRole.name)
            putString("sellerName", settingsToSave.sellerName)
            putString("savedSellers", settingsToSave.savedSellers)
            putBoolean("showPublisherSignature", settingsToSave.showPublisherSignature)
            putString("publisherSignatureText", settingsToSave.publisherSignatureText)
            putBoolean("taxEnabled", settingsToSave.taxEnabled)
            putFloat("taxRatePercent", settingsToSave.taxRatePercent.toFloat())
            putBoolean("allowNegativeStock", settingsToSave.allowNegativeStock)
            putBoolean("autoPrintReceipt", settingsToSave.autoPrintReceipt)
            putString("adminPin", settingsToSave.adminPin)
            putString("cashierPin", settingsToSave.cashierPin)
            putBoolean("pinLockEnabled", settingsToSave.pinLockEnabled)
            putString("bluetoothPrinterAddress", settingsToSave.bluetoothPrinterAddress)
            putString("bluetoothPrinterName", settingsToSave.bluetoothPrinterName)
            putBoolean("isLicensed", settingsToSave.isLicensed)
            putString("licenseKey", settingsToSave.licenseKey)
            putString("licenseType", settingsToSave.licenseType)
            putLong("licenseExpiryDate", settingsToSave.licenseExpiryDate)
            putLong("lastKnownTimestamp", updatedTimestamp)
            putString("shopLogoUri", settingsToSave.shopLogoUri)
            putString("currentShopId", settingsToSave.currentShopId)
            apply()
        }
        _settings.value = settingsToSave
    }

    fun updateLastKnownTimestamp() {
        val now = System.currentTimeMillis()
        if (now > _settings.value.lastKnownTimestamp) {
            prefs.edit().putLong("lastKnownTimestamp", now).apply()
            _settings.value = _settings.value.copy(lastKnownTimestamp = now)
        }
    }

    fun getEffectiveShopId(): String {
        val saved = _settings.value.currentShopId
        if (saved.isNotBlank()) return saved
        val authUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (!authUid.isNullOrBlank()) {
            setCurrentShopId(authUid)
            return authUid
        }
        return ""
    }

    fun setCurrentShopId(shopId: String) {
        if (shopId.isNotBlank() && shopId != _settings.value.currentShopId) {
            prefs.edit().putString("currentShopId", shopId).apply()
            _settings.value = _settings.value.copy(currentShopId = shopId)
        }
    }

    fun setUserRole(role: UserRole) {
        updateSettings(_settings.value.copy(currentUserRole = role))
    }
}

