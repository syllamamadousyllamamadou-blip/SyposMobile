package com.sypos.mobile.util

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LicenseStatus(
    val isValid: Boolean,
    val licenseType: String,
    val message: String,
    val expiryTimestamp: Long = 0L, // 0L = Illimité
    val shopName: String = "",
    val targetDeviceId: String = ""
)

object LicenseManager {

    // SYPOS 2048-bit RSA Public Key (X.509 ASN.1 DER Base64)
    private const val RSA_PUBLIC_KEY_B64 = 
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy4fzpl4lsQkScqSwHrAx" +
        "CiUEndXd1Kop9u/kKdGtxmvvJzxD15KN71TJ8ZVw/ds749Pz3yCdzlU7io5fDshu" +
        "VLYNVCXIpOJUtjGdtxVNCv9l0+bpAUKLZpPEHGgyXlDGbVE8G6bOQXXt57CoFdTQ" +
        "iGm7iBuQ2jcI6tW2Y0BZztforF57YX133ls9Eex5aM7pjzQcY31hrlLDKN3+1CD+" +
        "/XFSfPw5WfcLlOYm0x6FzFkFhG6s5qSNB1cfQ5yjScXvHGXoRa5Eo/MH4BXkU2vT" +
        "IZS5Y4+gqdc4T6drGyzQ2uUUtsII8zNSh0gRZAja452p4EuwpyrcgcopZG497gHp" +
        "5QIDAQAB"

    @Volatile
    private var cachedPublicKey: PublicKey? = null

    private fun getPublicKey(): PublicKey {
        cachedPublicKey?.let { return it }
        val keyBytes = Base64.decode(RSA_PUBLIC_KEY_B64, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val key = keyFactory.generatePublic(spec)
        cachedPublicKey = key
        return key
    }

    /**
     * Validates a cryptographic license key string or scanned QR code token against the device.
     */
    fun validateKey(context: Context, rawKey: String): LicenseStatus {
        val cleanToken = rawKey.trim()
            .replace("`", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")

        if (cleanToken.isBlank()) {
            return LicenseStatus(
                isValid = false,
                licenseType = "Non Activé",
                message = "Veuillez saisir ou scanner une clé de licence valide."
            )
        }

        if (!cleanToken.startsWith("SYP1.")) {
            return LicenseStatus(
                isValid = false,
                licenseType = "Format Invalide",
                message = "Clé invalide. Le format de licence SYPOS doit commencer par 'SYP1.'."
            )
        }

        val parts = cleanToken.split(".")
        if (parts.size != 3) {
            return LicenseStatus(
                isValid = false,
                licenseType = "Format Invalide",
                message = "Structure de licence corrompue (sections manquantes)."
            )
        }

        val payloadB64 = parts[1]
        val sigB64 = parts[2]

        val payloadBytes: ByteArray
        val sigBytes: ByteArray
        try {
            payloadBytes = Base64.decode(payloadB64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            sigBytes = Base64.decode(sigB64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        } catch (e: Exception) {
            return LicenseStatus(
                isValid = false,
                licenseType = "Erreur Décodage",
                message = "Impossible de décoder les données de la licence."
            )
        }

        // 1. Cryptographic RSA-2048 verification
        val isSignatureValid = try {
            val pubKey = getPublicKey()
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(pubKey)
            verifier.update(payloadBytes)
            verifier.verify(sigBytes)
        } catch (e: Exception) {
            false
        }

        if (!isSignatureValid) {
            return LicenseStatus(
                isValid = false,
                licenseType = "Signature Invalide",
                message = "Signature de licence invalide ou falsifiée. Contactez l'administrateur SYPOS."
            )
        }

        // 2. Parse Payload JSON
        val payloadStr = String(payloadBytes, Charsets.UTF_8)
        val json = try {
            JSONObject(payloadStr)
        } catch (e: Exception) {
            return LicenseStatus(
                isValid = false,
                licenseType = "JSON Corrompu",
                message = "Données internes de licence illisibles."
            )
        }

        val devId = json.optString("devId", "").trim().uppercase(Locale.ROOT)
        val shopName = json.optString("shop", "Boutique SYPOS")
        val plan = json.optString("plan", "LIFETIME").uppercase(Locale.ROOT)
        val expiryMs = json.optLong("exp", 0L)

        // 3. Hardware / Device Binding Verification
        val currentDevId = DeviceSecurityHelper.getDeviceId(context).trim().uppercase(Locale.ROOT)
        if (devId != currentDevId) {
            return LicenseStatus(
                isValid = false,
                licenseType = "Appareil Incompatible",
                message = "Cette clé a été générée pour l'appareil ($devId) et ne peut pas être utilisée sur cet appareil ($currentDevId).",
                targetDeviceId = devId
            )
        }

        // 4. Expiration check
        val now = System.currentTimeMillis()
        if (expiryMs > 0L && now > expiryMs) {
            val expStr = SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(Date(expiryMs))
            return LicenseStatus(
                isValid = false,
                licenseType = "Licence Expirée",
                message = "Votre licence a expiré le $expStr. Veuillez contacter SYPOS pour renouveler votre abonnement.",
                expiryTimestamp = expiryMs,
                shopName = shopName,
                targetDeviceId = devId
            )
        }

        val typeLabel = when (plan) {
            "LIFETIME" -> "Licence Définitive (Illimitée à Vie)"
            "ANNUAL" -> "Licence Annuelle Pro (1 An)"
            "TRIAL" -> "Licence d'Essai (30 Jours)"
            else -> "Licence Commerciale Valide"
        }

        return LicenseStatus(
            isValid = true,
            licenseType = typeLabel,
            message = "Licence $typeLabel activée avec succès !",
            expiryTimestamp = expiryMs,
            shopName = shopName,
            targetDeviceId = devId
        )
    }

    /**
     * Checks if the given license is expired.
     */
    fun isLicenseExpired(expiryTimestamp: Long): Boolean {
        if (expiryTimestamp <= 0L) return false // 0 = Illimité / À Vie
        return System.currentTimeMillis() > expiryTimestamp
    }

    /**
     * Verifies system clock anti-rollback: detects if the user turned their system clock back.
     */
    fun isClockTampered(lastKnownTimestamp: Long): Boolean {
        if (lastKnownTimestamp <= 0L) return false
        val current = System.currentTimeMillis()
        // Allow a small 5-minute tolerance for minor network time sync fluctuations
        return current < (lastKnownTimestamp - 5 * 60 * 1000L)
    }
}
