import Foundation
import UIKit
import CommonCrypto
import Security

public struct LicenseStatus {
    public var isValid: Bool
    public var licenseType: String
    public var expiryDate: TimeInterval // 0 for lifetime
    public var message: String
    public var targetDeviceId: String
    
    public init(isValid: Bool, licenseType: String, expiryDate: TimeInterval, message: String, targetDeviceId: String = "") {
        self.isValid = isValid
        self.licenseType = licenseType
        self.expiryDate = expiryDate
        self.message = message
        self.targetDeviceId = targetDeviceId
    }
}

public class LicenseManager {
    
    // RSA 2048-bit Public Key in Base64 (X.509 ASN.1 DER format)
    private static let rsaPublicKeyB64 = 
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy4fzpl4lsQkScqSwHrAx" +
        "CiUEndXd1Kop9u/kKdGtxmvvJzxD15KN71TJ8ZVw/ds749Pz3yCdzlU7io5fDshu" +
        "VLYNVCXIpOJUtjGdtxVNCv9l0+bpAUKLZpPEHGgyXlDGbVE8G6bOQXXt57CoFdTQ" +
        "iGm7iBuQ2jcI6tW2Y0BZztforF57YX133ls9Eex5aM7pjzQcY31hrlLDKN3+1CD+" +
        "/XFSfPw5WfcLlOYm0x6FzFkFhG6s5qSNB1cfQ5yjScXvHGXoRa5Eo/MH4BXkU2vT" +
        "IZS5Y4+gqdc4T6drGyzQ2uUUtsII8zNSh0gRZAja452p4EuwpyrcgcopZG497gHp" +
        "5QIDAQAB"

    /**
     * Gets the unique hardware-bound Device ID for iOS.
     * Format: SYPOS-DEV-XXXX-YYYY
     */
    public static func getDeviceId() -> String {
        let vendorUUID = UIDevice.current.identifierForVendor?.uuidString ?? "IOS_DEVICE_UUID"
        let model = UIDevice.current.model
        let name = UIDevice.current.systemName
        let raw = "\(vendorUUID)|\(model)|\(name)|APPLE_IOS"
        
        let hash = sha256Hex(raw).uppercased()
        let p1 = hash.prefix(4)
        let p2 = hash.dropFirst(4).prefix(4)
        return "SYPOS-DEV-\(p1)-\(p2)"
    }

    /**
     * Validates a cryptographic license key string.
     */
    public static func validateKey(_ inputKey: String) -> LicenseStatus {
        let cleaned = inputKey
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "`", with: "")
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "\n", with: "")

        if cleaned.isEmpty {
            return LicenseStatus(isValid: false, licenseType: "Non Activé", expiryDate: 0, message: "Veuillez entrer une clé d'activation")
        }

        if !cleaned.starts(with: "SYP1.") {
            return LicenseStatus(isValid: false, licenseType: "Format Invalide", expiryDate: 0, message: "Format de clé invalide (doit commencer par SYP1.)")
        }

        let parts = cleaned.components(separatedBy: ".")
        if parts.count != 3 {
            return LicenseStatus(isValid: false, licenseType: "Format Invalide", expiryDate: 0, message: "Clé corrompue ou incomplète")
        }

        guard let payloadData = base64URLDecode(parts[1]),
              let sigData = base64URLDecode(parts[2]) else {
            return LicenseStatus(isValid: false, licenseType: "Erreur Décodage", expiryDate: 0, message: "Impossible de décoder les données de licence")
        }

        // Cryptographic Verification
        let isSigValid = verifyRSASignature(data: payloadData, signature: sigData)
        if !isSigValid {
            return LicenseStatus(isValid: false, licenseType: "Signature Invalide", expiryDate: 0, message: "Signature cryptographique invalide. Clé non authentifiée.")
        }

        guard let json = try? JSONSerialization.jsonObject(with: payloadData) as? [String: Any],
              let devId = (json["devId"] as? String)?.uppercased() else {
            return LicenseStatus(isValid: false, licenseType: "JSON Invalide", expiryDate: 0, message: "Données internes de licence illisibles")
        }

        let currentDevId = getDeviceId().uppercased()
        if devId != currentDevId {
            return LicenseStatus(
                isValid: false,
                licenseType: "Appareil Incompatible",
                expiryDate: 0,
                message: "Cette clé est destinée à l'appareil (\(devId)) et non à cet appareil (\(currentDevId))",
                targetDeviceId: devId
            )
        }

        let expMs = json["exp"] as? Double ?? 0.0
        let plan = (json["plan"] as? String ?? "LIFETIME").uppercased()
        let expirySeconds = expMs > 0 ? (expMs / 1000.0) : 0

        let now = Date().timeIntervalSince1970
        if expirySeconds > 0 && now > expirySeconds {
            return LicenseStatus(
                isValid: false,
                licenseType: "Licence Expirée",
                expiryDate: expirySeconds,
                message: "Cette licence a expiré. Veuillez contacter SYPOS.",
                targetDeviceId: devId
            )
        }

        let label: String
        switch plan {
        case "LIFETIME": label = "Licence Définitive (Illimitée à Vie)"
        case "ANNUAL": label = "Licence Annuelle Pro (1 An)"
        case "TRIAL": label = "Licence Essai (30 Jours)"
        default: label = "Licence Commerciale Valide"
        }

        return LicenseStatus(
            isValid: true,
            licenseType: label,
            expiryDate: expirySeconds,
            message: "Licence activée avec succès !",
            targetDeviceId: devId
        )
    }

    public static func isLicenseExpired(expiryTimestamp: TimeInterval) -> Bool {
        if expiryTimestamp <= 0 { return false } // Lifetime
        return Date().timeIntervalSince1970 > expiryTimestamp
    }

    private static func sha256Hex(_ string: String) -> String {
        guard let data = string.data(using: .utf8) else { return "00000000" }
        var digest = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        data.withUnsafeBytes {
            _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &digest)
        }
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    private static func base64URLDecode(_ string: String) -> Data? {
        var base64 = string
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        while base64.count % 4 != 0 {
            base64.append("=")
        }
        return Data(base64Encoded: base64)
    }

    private static func verifyRSASignature(data: Data, signature: Data) -> Bool {
        guard let keyData = Data(base64Encoded: rsaPublicKeyB64) else { return false }
        
        let attributes: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
            kSecAttrKeyClass as String: kSecAttrKeyClassPublic,
            kSecAttrKeySizeInBits as String: 2048
        ]
        
        var error: Unmanaged<CFError>?
        guard let secKey = SecKeyCreateWithData(keyData as CFData, attributes as CFDictionary, &error) else {
            return false
        }
        
        let isVerified = SecKeyVerifySignature(
            secKey,
            .rsaSignatureMessagePKCS1v15SHA256,
            data as CFData,
            signature as CFData,
            &error
        )
        return isVerified
    }
}
