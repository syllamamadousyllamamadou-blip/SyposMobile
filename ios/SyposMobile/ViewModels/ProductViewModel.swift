import Foundation
import Combine

public class ProductViewModel: ObservableObject {
    @Published public var searchQuery: String = ""
    @Published public var selectedCategoryId: String? = nil

    private var dataStore: DataStore

    public init(dataStore: DataStore = .shared) {
        self.dataStore = dataStore
    }

    public func filteredProducts(products: [Product]) -> [Product] {
        products.filter { prod in
            let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
            let matchesQuery = query.isEmpty ||
                prod.name.localizedCaseInsensitiveContains(query) ||
                (prod.barcode?.localizedCaseInsensitiveContains(query) ?? false)

            let matchesCat = selectedCategoryId == nil || prod.categoryId == selectedCategoryId
            return matchesQuery && matchesCat
        }
    }

    public func stockMetrics(products: [Product]) -> (totalCost: Double, totalSale: Double, potentialMargin: Double) {
        let cost = products.reduce(0.0) { $0 + ($1.costPrice * Double($1.stockQuantity)) }
        let sale = products.reduce(0.0) { $0 + ($1.salePrice * Double($1.stockQuantity)) }
        return (cost, sale, sale - cost)
    }
}
