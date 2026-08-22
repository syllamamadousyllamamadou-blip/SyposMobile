import Foundation
import Combine

public class CustomerViewModel: ObservableObject {
    @Published public var searchQuery: String = ""
    @Published public var onlyDebtors: Bool = false

    public func filteredCustomers(customers: [Customer]) -> [Customer] {
        customers.filter { c in
            let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
            let matchesQuery = query.isEmpty ||
                c.name.localizedCaseInsensitiveContains(query) ||
                (c.phone?.localizedCaseInsensitiveContains(query) ?? false)

            let matchesDebt = !onlyDebtors || c.totalDebt > 0
            return matchesQuery && matchesDebt
        }
    }

    public func totalDebt(customers: [Customer]) -> Double {
        customers.reduce(0) { $0 + $1.totalDebt }
    }
}
