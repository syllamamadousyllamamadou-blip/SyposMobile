package com.sypos.mobile.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.ShopSettingsManager
import com.sypos.mobile.data.local.SyposDatabase
import com.sypos.mobile.data.local.UserRole
import com.sypos.mobile.data.local.entity.PromoCodeEntity
import com.sypos.mobile.domain.repository.PromoCodeRepository
import com.sypos.mobile.domain.repository.TicketRepository
import com.sypos.mobile.util.BackupHelper
import com.sypos.mobile.util.BluetoothPrinterDevice
import com.sypos.mobile.util.BluetoothPrinterHelper
import com.sypos.mobile.util.ExportHelper

import com.sypos.mobile.util.DeviceSecurityHelper
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.sypos.mobile.data.remote.CloudSyncManager

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val shopSettingsManager: ShopSettingsManager,
    private val ticketRepository: TicketRepository,
    private val promoCodeRepository: PromoCodeRepository,
    private val database: SyposDatabase,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    val settings: StateFlow<ShopSettings> = shopSettingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopSettings())

    val promoCodes: StateFlow<List<PromoCodeEntity>> = promoCodeRepository.getAllPromoCodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSettings(newSettings: ShopSettings) {
        viewModelScope.launch {
            shopSettingsManager.updateSettings(newSettings)
            val shopId = shopSettingsManager.getEffectiveShopId()
            if (shopId.isNotBlank()) {
                cloudSyncManager.pushSettings(shopId, newSettings)
            }
        }
    }

    fun startCloudSync(shopId: String) {
        val safeId = if (shopId.isNotBlank()) shopId else shopSettingsManager.getEffectiveShopId()
        if (safeId.isNotBlank()) {
            cloudSyncManager.startListening(safeId)
        }
    }

    fun setCurrentShopId(shopId: String) {
        shopSettingsManager.setCurrentShopId(shopId)
    }

    fun clearAllData() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.clearAllTables()
            // Reset settings logic could also be applied here if needed, but clearing tables is enough for now
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
            val shopId = shopSettingsManager.getEffectiveShopId()
            if (shopId.isNotBlank()) {
                cloudSyncManager.pushPromoCode(shopId, promo)
            }
        }
    }

    fun togglePromoCode(promo: PromoCodeEntity) {
        viewModelScope.launch {
            val updated = promo.copy(isActive = !promo.isActive)
            promoCodeRepository.updatePromoCode(updated)
            val shopId = shopSettingsManager.getEffectiveShopId()
            if (shopId.isNotBlank()) {
                cloudSyncManager.pushPromoCode(shopId, updated)
            }
        }
    }

    fun deletePromoCode(promo: PromoCodeEntity) {
        viewModelScope.launch {
            promoCodeRepository.deletePromoCode(promo)
            val shopId = shopSettingsManager.getEffectiveShopId()
            if (shopId.isNotBlank()) {
                cloudSyncManager.deletePromoCode(shopId, promo.id)
            }
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

    fun updateLastKnownTimestamp() {
        shopSettingsManager.updateLastKnownTimestamp()
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



    fun exportBackup(context: Context) {
        viewModelScope.launch {
            BackupHelper.exportAndShareBackup(context, database, settings.value)
        }
    }

    fun restoreBackup(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = BackupHelper.restoreBackupFromUri(context, uri, database, shopSettingsManager)
            onResult(success)
        }
    }

    fun startRemoteLicenseCheck(context: Context) {
        val deviceId = DeviceSecurityHelper.getDeviceId(context).trim().uppercase(java.util.Locale.ROOT)
        val db = FirebaseFirestore.getInstance()
        
        db.collection("licenses").document(deviceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Ignore errors, could be network offline
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val exp = snapshot.getLong("exp") ?: 0L
                    val plan = snapshot.getString("plan") ?: "TRIAL"
                    val token = snapshot.getString("token") ?: ""
                    
                    // Update local settings to match remote
                    val updated = settings.value.copy(
                        isLicensed = true,
                        licenseKey = token,
                        licenseType = plan,
                        licenseExpiryDate = exp
                    )
                    updateSettings(updated)
                } else {
                    // Revoke local license if removed from Firebase
                    if (settings.value.isLicensed) {
                        val updated = settings.value.copy(
                            isLicensed = false,
                            licenseExpiryDate = 0L
                        )
                        updateSettings(updated)
                    }
                }
            }
    }

    fun startFreeTrial(context: Context, onResult: (Boolean, String) -> Unit) {
        val deviceId = DeviceSecurityHelper.getDeviceId(context).trim().uppercase(java.util.Locale.ROOT)
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("licenses").document(deviceId)

        docRef.get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val plan = document.getString("plan")
                if (plan == "TRIAL") {
                    onResult(false, "Vous avez déjà utilisé votre période d'essai.")
                } else {
                    onResult(false, "Cet appareil est déjà enregistré.")
                }
            } else {
                val expDate = System.currentTimeMillis() + (7L * 24L * 60L * 60L * 1000L)
                val trialData = hashMapOf(
                    "plan" to "TRIAL",
                    "exp" to expDate,
                    "token" to ""
                )
                docRef.set(trialData).addOnSuccessListener {
                    onResult(true, "Essai de 7 jours activé avec succès !")
                }.addOnFailureListener {
                    onResult(false, "Erreur de connexion à Firebase.")
                }
            }
        }.addOnFailureListener {
            onResult(false, "Vérifiez votre connexion internet.")
        }
    }

    fun backupToFirebase(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = BackupHelper.createBackupJson(database, settings.value)
                val deviceId = DeviceSecurityHelper.getDeviceId(context).trim().uppercase(java.util.Locale.ROOT)
                val db = FirebaseFirestore.getInstance()
                
                val data = hashMapOf(
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "data" to json,
                    "shopName" to settings.value.shopName
                )
                
                db.collection("backups").document(deviceId).set(data)
                    .addOnSuccessListener {
                        onResult(true, "Sauvegarde Cloud réussie ! ☁️")
                    }
                    .addOnFailureListener { e ->
                        onResult(false, "Échec : ${e.message}")
                    }
            } catch (e: Exception) {
                onResult(false, "Erreur : ${e.message}")
            }
        }
    }

    fun restoreFromFirebase(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val deviceId = DeviceSecurityHelper.getDeviceId(context).trim().uppercase(java.util.Locale.ROOT)
                val db = FirebaseFirestore.getInstance()
                
                db.collection("backups").document(deviceId).get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val json = document.getString("data")
                            if (!json.isNullOrBlank()) {
                                viewModelScope.launch {
                                    val success = BackupHelper.restoreBackupFromJson(json, database, shopSettingsManager)
                                    if (success) {
                                        onResult(true, "Restauration Cloud terminée avec succès !")
                                    } else {
                                        onResult(false, "Le fichier de sauvegarde est corrompu.")
                                    }
                                }
                            } else {
                                onResult(false, "Aucune donnée de sauvegarde trouvée.")
                            }
                        } else {
                            onResult(false, "Aucune sauvegarde Cloud trouvée pour cet appareil.")
                        }
                    }
                    .addOnFailureListener { e ->
                        onResult(false, "Échec de récupération : ${e.message}")
                    }
            } catch (e: Exception) {
                onResult(false, "Erreur : ${e.message}")
            }
        }
    }

    fun forceSyncAllDataToCloud(context: Context, onResult: (Boolean, String) -> Unit) {
        val shopId = shopSettingsManager.getEffectiveShopId()
        if (shopId.isBlank()) {
            onResult(false, "Vous n'êtes pas connecté à un compte.")
            return
        }
        cloudSyncManager.forceSyncAllLocalDataToCloud(shopId) { success ->
            if (success) {
                onResult(true, "Toutes les données ont été synchronisées vers le Cloud.")
            } else {
                onResult(false, "Une erreur s'est produite lors de la synchronisation.")
            }
        }
    }
}





