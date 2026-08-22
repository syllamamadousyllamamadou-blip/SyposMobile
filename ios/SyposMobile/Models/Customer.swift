import Foundation

public struct Customer: Identifiable, Codable, Equatable {
    public var id: String
    public var name: String
    public var phone: String?
    public var address: String?
    public var totalDebt: Double
    public var note: String?

    public init(
        id: String = UUID().uuidString,
        name: String,
        phone: String? = nil,
        address: String? = nil,
        totalDebt: Double = 0.0,
        note: String? = nil
    ) {
        self.id = id
        self.name = name
        self.phone = phone
        self.address = address
        self.totalDebt = totalDebt
        self.note = note
    }
}

public struct Expense: Identifiable, Codable, Equatable {
    public var id: String
    public var date: Date
    public var amount: Double
    public var description: String
    public var category: String?

    public init(
        id: String = UUID().uuidString,
        date: Date = Date(),
        amount: Double,
        description: String,
        category: String? = nil
    ) {
        self.id = id
        self.date = date
        self.amount = amount
        self.description = description
        self.category = category
    }
}

public struct PromoCode: Identifiable, Codable, Equatable {
    public var id: String
    public var code: String
    public var discountPercent: Double
    public var maxUsage: Int
    public var currentUsage: Int
    public var isActive: BooleanLiteralType

    public init(
        id: String = UUID().uuidString,
        code: String,
        discountPercent: Double,
        maxUsage: Int = 50,
        currentUsage: Int = 0,
        isActive: Bool = true
    ) {
        self.id = id
        self.code = code
        self.discountPercent = discountPercent
        self.maxUsage = maxUsage
        self.currentUsage = currentUsage
        self.isActive = isActive
    }
}

public struct ShopSettings: Codable, Equatable {
    public var shopName: String
    public var shopAddress: String
    public var shopPhone: String
    public var receiptFooter: String
    public var sellerName: String
    public var showPublisherSignature: Bool
    public var publisherSignatureText: String
    public var businessMode: BusinessMode
    public var taxEnabled: Bool
    public var taxRatePercent: Double
    public var allowNegativeStock: Bool
    public var autoPrintReceipt: Bool
    public var adminPin: String
    public var cashierPin: String
    public var pinLockEnabled: Bool
    public var isLicensed: Bool
    public var licenseKey: String
    public var licenseType: String
    public var licenseExpiryDate: TimeInterval
    public var bluetoothPrinterUUID: String?
    public var bluetoothPrinterName: String?

    public init(
        shopName: String = "SYPOS COMMERCE",
        shopAddress: String = "Abidjan, Côte d'Ivoire",
        shopPhone: String = "+225 07 58 24 55 30",
        receiptFooter: String = "Merci de votre visite et à bientôt !",
        sellerName: String = "Vendeur 1",
        showPublisherSignature: Bool = true,
        publisherSignatureText: String = "Solution: SYPOS MOBILE 0758245530",
        businessMode: BusinessMode = .supermarket,
        taxEnabled: Bool = false,
        taxRatePercent: Double = 18.0,
        allowNegativeStock: Bool = false,
        autoPrintReceipt: Bool = false,
        adminPin: String = "1234",
        cashierPin: String = "0000",
        pinLockEnabled: Bool = true,
        isLicensed: Bool = false,
        licenseKey: String = "",
        licenseType: String = "Non Activé",
        licenseExpiryDate: TimeInterval = 0,
        bluetoothPrinterUUID: String? = nil,
        bluetoothPrinterName: String? = nil
    ) {
        self.shopName = shopName
        self.shopAddress = shopAddress
        self.shopPhone = shopPhone
        self.receiptFooter = receiptFooter
        self.sellerName = sellerName
        self.showPublisherSignature = showPublisherSignature
        self.publisherSignatureText = publisherSignatureText
        self.businessMode = businessMode
        self.taxEnabled = taxEnabled
        self.taxRatePercent = taxRatePercent
        self.allowNegativeStock = allowNegativeStock
        self.autoPrintReceipt = autoPrintReceipt
        self.adminPin = adminPin
        self.cashierPin = cashierPin
        self.pinLockEnabled = pinLockEnabled
        self.isLicensed = isLicensed
        self.licenseKey = licenseKey
        self.licenseType = licenseType
        self.licenseExpiryDate = licenseExpiryDate
        self.bluetoothPrinterUUID = bluetoothPrinterUUID
        self.bluetoothPrinterName = bluetoothPrinterName
    }
}
