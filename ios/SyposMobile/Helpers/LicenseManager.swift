import Foundation

public struct LicenseStatus {
    public var isValid: Bool
    public var licenseType: String
    public var expiryDate: TimeInterval // 0 for lifetime
    public var message: String
}

public class LicenseManager {
    public static func validateKey(_ inputKey: String) -> LicenseStatus {
        let cleaned = inputKey.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()

        if cleaned.isEmpty {
            return LicenseStatus(isValid: false, licenseType: "Non Activé", expiryDate: 0, message: "Veuillez entrer une clé d'activation")
        }

        let now = Date().timeIntervalSince1970

        if cleaned == "SYPOS-TRIAL-30D" {
            let thirtyDays = now + (30 * 24 * 3600)
            return LicenseStatus(isValid: true, licenseType: "Licence Essai (30 Jours)", expiryDate: thirtyDays, message: "Licence d'Essai 30 Jours activée avec succès")
        }

        if cleaned == "SYPOS-1AN-2026" {
            let oneYear = now + (365 * 24 * 3600)
            return LicenseStatus(isValid: true, licenseType: "Licence Annuelle (1 An)", expiryDate: oneYear, message: "Licence Annuelle Pro activée avec succès")
        }

        if cleaned == "SYPOS-PRO-VIP-2026" || cleaned == "SYPOS-LIFETIME-PRO" {
            return LicenseStatus(isValid: true, licenseType: "Licence Définitive (Illimitée)", expiryDate: 0, message: "Licence Illimitée à Vie activée avec succès")
        }

        if cleaned.starts(with: "SYPOS-") && cleaned.count >= 12 {
            let oneYear = now + (365 * 24 * 3600)
            return LicenseStatus(isValid: true, licenseType: "Licence Commerciale Valide", expiryDate: oneYear, message: "Clé d'activation valide !")
        }

        return LicenseStatus(isValid: false, licenseType: "Clé Invalide", expiryDate: 0, message: "Clé de licence invalide ou expirée")
    }

    public static func isLicenseExpired(expiryTimestamp: TimeInterval) -> Bool {
        if expiryTimestamp <= 0 { return false } // Lifetime
        return Date().timeIntervalSince1970 > expiryTimestamp
    }
}
