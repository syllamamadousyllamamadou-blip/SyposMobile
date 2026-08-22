import Foundation

public struct Product: Identifiable, Codable, Equatable, Hashable {
    public var id: String
    public var name: String
    public var salePrice: Double
    public var costPrice: Double
    public var stockQuantity: Int
    public var alertStock: Int
    public var barcode: String?
    public var categoryId: String?
    public var colorHex: String?

    public init(
        id: String = UUID().uuidString,
        name: String,
        salePrice: Double,
        costPrice: Double = 0.0,
        stockQuantity: Int = 0,
        alertStock: Int = 5,
        barcode: String? = nil,
        categoryId: String? = nil,
        colorHex: String? = nil
    ) {
        self.id = id
        self.name = name
        self.salePrice = salePrice
        self.costPrice = costPrice
        self.stockQuantity = stockQuantity
        self.alertStock = alertStock
        self.barcode = barcode
        self.categoryId = categoryId
        self.colorHex = colorHex
    }
}

public struct ProductCategory: Identifiable, Codable, Equatable, Hashable {
    public var id: String
    public var name: String
    public var colorHex: String?

    public init(id: String = UUID().uuidString, name: String, colorHex: String? = nil) {
        self.id = id
        self.name = name
        self.colorHex = colorHex
    }
}
