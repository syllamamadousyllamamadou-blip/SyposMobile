package com.sypos.mobile.data.remote

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.sypos.mobile.util.DeviceSecurityHelper
import kotlinx.coroutines.tasks.await

data class AuthStatus(
    val isValid: Boolean,
    val message: String = "",
    val subscriptionPlan: String = "EXPIRED",
    val expiryTimestamp: Long = 0L,
    val shopId: String = ""
)

class CloudAuthManager {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    suspend fun verifyAndRegisterDevice(context: Context, shopName: String? = null, phoneNumber: String? = null): AuthStatus {
        val user = auth.currentUser ?: return AuthStatus(false, "Veuillez vous connecter avec votre Email.")
        val deviceId = DeviceSecurityHelper.getDeviceId(context)

        return try {
            val userDoc = db.collection("users").document(user.uid).get().await()
            
            if (!userDoc.exists()) {
                // New registration - give 7 days trial
                val expiry = System.currentTimeMillis() + (7L * 24L * 60L * 60L * 1000L)
                val newUserMap = hashMapOf(
                    "email" to user.email,
                    "plan" to "TRIAL",
                    "expiry" to expiry,
                    "allowedDevices" to 1L, // Default trial gives 1 device
                    "devices" to arrayListOf(deviceId),
                    "shopId" to user.uid,
                    "createdAt" to System.currentTimeMillis()
                )
                if (shopName != null) newUserMap["shopName"] = shopName
                if (phoneNumber != null) newUserMap["phoneNumber"] = phoneNumber
                db.collection("users").document(user.uid).set(newUserMap).await()
                return AuthStatus(true, "Essai de 7 jours activé.", "TRIAL", expiry, user.uid)
            }

            // User exists, resolve enterprise shopId
            val resolvedShopId = userDoc.getString("shopId") ?: userDoc.getString("companyId") ?: user.uid
            val plan = userDoc.getString("plan") ?: "EXPIRED"
            val expiry = userDoc.getLong("expiry") ?: 0L
            val allowedDevices = userDoc.getLong("allowedDevices") ?: 1L
            val devices = userDoc.get("devices") as? List<String> ?: emptyList()
            
            // Check expiry
            if (System.currentTimeMillis() > expiry && plan != "LIFETIME") {
                return AuthStatus(false, "Votre abonnement est terminé. Veuillez contacter KAYO au +2250758245530.", plan, expiry, resolvedShopId)
            }
            if (plan == "BLOCKED" || plan == "PAUSED") {
                return AuthStatus(false, "Votre compte est bloqué. Veuillez contacter KAYO au +2250758245530.", plan, expiry, resolvedShopId)
            }

            // Check device limits
            if (devices.contains(deviceId)) {
                // Device already registered, all good!
                return AuthStatus(true, "Appareil autorisé.", plan, expiry, resolvedShopId)
            } else {
                if (devices.size >= allowedDevices) {
                    return AuthStatus(false, "Limite d'appareils atteinte ($allowedDevices). Veuillez révoquer une ancienne caisse sur votre Web-Admin KAYO ou upgrader votre abonnement.", plan, expiry, resolvedShopId)
                } else {
                    // Register new device
                    db.collection("users").document(user.uid).update("devices", FieldValue.arrayUnion(deviceId)).await()
                    return AuthStatus(true, "Nouvel appareil enregistré avec succès.", plan, expiry, resolvedShopId)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            AuthStatus(false, "Erreur de connexion au serveur. Vérifiez votre connexion Internet.", "EXPIRED", 0L, user.uid)
        }
    }
}
