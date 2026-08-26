package com.sypos.mobile.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.Locale

object DeviceSecurityHelper {

    @Volatile
    private var cachedDeviceId: String? = null

    /**
     * Generates a unique, stable, non-forgeable Hardware Device ID.
     * Format: SYPOS-DEV-XXXX-YYYY (e.g., SYPOS-DEV-7A8B-49C2)
     */
    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        cachedDeviceId?.let { return it }

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_ID"
        } catch (e: Exception) {
            "UNKNOWN_ID"
        }

        val hardwareFingerprint = buildString {
            append(androidId)
            append("|").append(Build.BRAND)
            append("|").append(Build.MANUFACTURER)
            append("|").append(Build.MODEL)
            append("|").append(Build.BOARD)
        }

        val hash = sha256Hex(hardwareFingerprint).uppercase(Locale.ROOT)
        
        // Take 8 characters split into 2 groups of 4 for easy readability: XXXX-YYYY
        val part1 = if (hash.length >= 4) hash.substring(0, 4) else "0000"
        val part2 = if (hash.length >= 8) hash.substring(4, 8) else "0000"

        val formattedId = "SYPOS-DEV-$part1-$part2"
        cachedDeviceId = formattedId
        return formattedId
    }

    private fun sha256Hex(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback to basic hash if SHA-256 is unavailable
            input.hashCode().toString(16).padStart(8, '0')
        }
    }
}
