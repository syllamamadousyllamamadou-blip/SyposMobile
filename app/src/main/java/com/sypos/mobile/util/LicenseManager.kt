package com.sypos.mobile.util

import java.util.Calendar
import java.util.Locale

data class LicenseStatus(
    val isValid: Boolean,
    val licenseType: String,
    val message: String,
    val expiryTimestamp: Long = 0L // 0L = Illimité
)

object LicenseManager {

    private const val LICENSE_SALT = "SYPOS_SECRET_SALT_2026_MOBILE"

    /**
     * Validates a license key and determines its validity duration.
     * Supported keys:
     * - Trial (30 jours): SYPOS-TRIAL-30D, SYPOS-DEMO-30J
     * - 1 An: SYPOS-1AN-2026, SYPOS-1YEAR-PRO, SYPOS-ANNUEL-2026
     * - Illimitée: SYPOS-PRO-VIP-2026, SYPOS-PRO-8899-7744, SYPOS-LIFETIME-GOLD, SYPOS-COMMERCE-PRO
     */
    fun validateKey(key: String): LicenseStatus {
        val cleanKey = key.trim().uppercase(Locale.getDefault())

        if (cleanKey.isBlank()) {
            return LicenseStatus(false, "Invalide", "Veuillez saisir une clé de licence")
        }

        val now = System.currentTimeMillis()

        // 1. Trial Keys (30 Days)
        if (cleanKey.contains("TRIAL") || cleanKey.contains("DEMO") || cleanKey == "SYPOS-TRIAL-30D") {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 30)
            return LicenseStatus(
                isValid = true,
                licenseType = "Licence d'Essai (30 Jours)",
                message = "Licence d'essai 30 jours activée",
                expiryTimestamp = cal.timeInMillis
            )
        }

        // 2. 1 Year Keys (365 Days)
        if (cleanKey.contains("1AN") || cleanKey.contains("1YEAR") || cleanKey.contains("ANNUEL") || cleanKey == "SYPOS-1AN-2026") {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 365)
            return LicenseStatus(
                isValid = true,
                licenseType = "Licence Annuelle (1 An)",
                message = "Licence Pro Annuelle activée avec succès",
                expiryTimestamp = cal.timeInMillis
            )
        }

        // 3. Master Lifetime Keys
        if (cleanKey == "SYPOS-PRO-2026-VIP" || 
            cleanKey == "SYPOS-PRO-8899-7744" || 
            cleanKey == "SYPOS-COMMERCE-PRO" ||
            cleanKey == "SYPOS-SUPERMARCHE-2026" ||
            cleanKey == "SYPOS-LIFETIME-GOLD"
        ) {
            return LicenseStatus(
                isValid = true,
                licenseType = "Licence Commerciale Illimitée",
                message = "Licence Illimitée activée avec succès",
                expiryTimestamp = 0L
            )
        }

        val parts = cleanKey.split("-")
        if (parts.size != 4 || parts[0] != "SYPOS") {
            return LicenseStatus(false, "Invalide", "Format de clé invalide (Ex: SYPOS-XXXX-YYYY-ZZZZ)")
        }

        // Algorithmic validation based on checksum
        val p1 = parts[1]
        val p2 = parts[2]
        val p3 = parts[3]

        if (p1.length < 3 || p2.length < 3 || p3.length < 3) {
            return LicenseStatus(false, "Invalide", "Longueur des blocs de licence incorrecte")
        }

        val checksum = (p1.hashCode() + p2.hashCode() + LICENSE_SALT.hashCode()) and 0xFFFF
        val hexCheck = checksum.toString(16).uppercase(Locale.getDefault()).padStart(4, '0')

        val isValid = p3.contains(hexCheck.take(2)) || (p1.sumOf { it.code } + p2.sumOf { it.code }) % 7 == 0

        return if (isValid) {
            LicenseStatus(
                isValid = true,
                licenseType = "Licence PRO Entreprise (Illimitée)",
                message = "Licence validée avec succès",
                expiryTimestamp = 0L
            )
        } else {
            LicenseStatus(false, "Invalide", "Clé de licence invalide ou expirée")
        }
    }

    fun isLicenseExpired(expiryTimestamp: Long): Boolean {
        if (expiryTimestamp <= 0L) return false // Illimité
        return System.currentTimeMillis() > expiryTimestamp
    }
}
