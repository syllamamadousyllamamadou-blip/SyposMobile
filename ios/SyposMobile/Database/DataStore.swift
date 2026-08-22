import Foundation
import Combine

public class DataStore: ObservableObject {
    public static let shared = DataStore()

    @Published public var products: [Product] = []
    @Published public var categories: [ProductCategory] = []
    @Published public var tickets: [Ticket] = []
    @Published public var customers: [Customer] = []
    @Published public var expenses: [Expense] = []
    @Published public var promoCodes: [PromoCode] = []
    @Published public var settings: ShopSettings = ShopSettings()
    @Published public var currentUserRole: UserRole = .admin

    private let fileManager = FileManager.default
    private var cancellables = Set<AnyCancellable>()

    public init() {
        loadAll()
        setupAutoSave()
    }

    private var documentsDirectory: URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    private func fileURL(for name: String) -> URL {
        documentsDirectory.appendingPathComponent("\(name).json")
    }

    public func loadAll() {
        products = load([Product].self, from: "products") ?? defaultProducts()
        categories = load([ProductCategory].self, from: "categories") ?? defaultCategories()
        tickets = load([Ticket].self, from: "tickets") ?? []
        customers = load([Customer].self, from: "customers") ?? defaultCustomers()
        expenses = load([Expense].self, from: "expenses") ?? []
        promoCodes = load([PromoCode].self, from: "promocodes") ?? defaultPromoCodes()
        settings = load(ShopSettings.self, from: "settings") ?? ShopSettings()
    }

    public func saveAll() {
        save(products, to: "products")
        save(categories, to: "categories")
        save(tickets, to: "tickets")
        save(customers, to: "customers")
        save(expenses, to: "expenses")
        save(promoCodes, to: "promocodes")
        save(settings, to: "settings")
    }

    private func setupAutoSave() {
        $products.sink { [weak self] _ in self?.save(self?.products, to: "products") }.store(in: &cancellables)
        $categories.sink { [weak self] _ in self?.save(self?.categories, to: "categories") }.store(in: &cancellables)
        $tickets.sink { [weak self] _ in self?.save(self?.tickets, to: "tickets") }.store(in: &cancellables)
        $customers.sink { [weak self] _ in self?.save(self?.customers, to: "customers") }.store(in: &cancellables)
        $expenses.sink { [weak self] _ in self?.save(self?.expenses, to: "expenses") }.store(in: &cancellables)
        $promoCodes.sink { [weak self] _ in self?.save(self?.promoCodes, to: "promocodes") }.store(in: &cancellables)
        $settings.sink { [weak self] _ in self?.save(self?.settings, to: "settings") }.store(in: &cancellables)
    }

    private func save<T: Encodable>(_ object: T?, to filename: String) {
        guard let object = object else { return }
        do {
            let data = try JSONEncoder().encode(object)
            try data.write(to: fileURL(for: filename))
        } catch {
            print("Error saving \(filename): \(error)")
        }
    }

    private func load<T: Decodable>(_ type: T.Type, from filename: String) -> T? {
        let url = fileURL(for: filename)
        guard fileManager.fileExists(atPath: url.path) else { return nil }
        do {
            let data = try Data(contentsOf: url)
            return try JSONDecoder().decode(type, from: data)
        } catch {
            print("Error loading \(filename): \(error)")
            return nil
        }
    }

    // MARK: - Business Logic Operations

    public func addOrUpdateProduct(_ product: Product) {
        if let index = products.firstIndex(where: { $0.id == product.id }) {
            products[index] = product
        } else {
            products.append(product)
        }
    }

    public func deleteProduct(_ product: Product) {
        products.removeAll { $0.id == product.id }
    }

    public func updateStock(productId: String, delta: Int) {
        if let index = products.firstIndex(where: { $0.id == productId }) {
            products[index].stockQuantity = max(0, products[index].stockQuantity + delta)
        }
    }

    public func recordSale(ticket: Ticket) {
        tickets.insert(ticket, at: 0)

        // Decrement stock for paid / credit sales
        if ticket.status == .paid || ticket.status == .credit {
            for item in ticket.items {
                if let index = products.firstIndex(where: { $0.id == item.productId }) {
                    if !settings.allowNegativeStock {
                        products[index].stockQuantity = max(0, products[index].stockQuantity - item.quantity)
                    } else {
                        products[index].stockQuantity -= item.quantity
                    }
                }
            }
        }

        // Add debt if credit sale
        if ticket.status == .credit, let customerId = ticket.customerId {
            if let index = customers.firstIndex(where: { $0.id == customerId }) {
                customers[index].totalDebt += ticket.totalAmount
            }
        }
    }

    public func cancelTicket(ticketId: String) {
        guard let index = tickets.firstIndex(where: { $0.id == ticketId }) else { return }
        let ticket = tickets[index]
        if ticket.status == .cancelled { return }

        // Restock items
        for item in ticket.items {
            if let pIndex = products.firstIndex(where: { $0.id == item.productId }) {
                products[pIndex].stockQuantity += item.quantity
            }
        }

        // Deduct debt if it was credit
        if ticket.status == .credit, let customerId = ticket.customerId {
            if let cIndex = customers.firstIndex(where: { $0.id == customerId }) {
                customers[cIndex].totalDebt = max(0, customers[cIndex].totalDebt - ticket.totalAmount)
            }
        }

        tickets[index].status = .cancelled
    }

    public func deleteHeldTicket(ticketId: String) {
        tickets.removeAll { $0.id == ticketId }
    }

    public func addExpense(amount: Double, description: String, category: String? = nil) {
        let expense = Expense(amount: amount, description: description, category: category)
        expenses.insert(expense, at: 0)
    }

    public func deleteExpense(_ expense: Expense) {
        expenses.removeAll { $0.id == expense.id }
    }

    public func addOrUpdateCustomer(_ customer: Customer) {
        if let index = customers.firstIndex(where: { $0.id == customer.id }) {
            customers[index] = customer
        } else {
            customers.append(customer)
        }
    }

    public func deleteCustomer(_ customer: Customer) {
        customers.removeAll { $0.id == customer.id }
    }

    public func settleDebt(customerId: String, amount: Double) {
        if let index = customers.firstIndex(where: { $0.id == customerId }) {
            customers[index].totalDebt = max(0, customers[index].totalDebt - amount)
        }
    }

    public func addPromoCode(_ promo: PromoCode) {
        promoCodes.append(promo)
    }

    public func deletePromoCode(_ promo: PromoCode) {
        promoCodes.removeAll { $0.id == promo.id }
    }

    public func togglePromoCode(_ promo: PromoCode) {
        if let index = promoCodes.firstIndex(where: { $0.id == promo.id }) {
            promoCodes[index].isActive.toggle()
        }
    }

    public func incrementPromoUsage(code: String) {
        if let index = promoCodes.firstIndex(where: { $0.code.uppercased() == code.uppercased() }) {
            promoCodes[index].currentUsage += 1
            if promoCodes[index].currentUsage >= promoCodes[index].maxUsage {
                promoCodes[index].isActive = false
            }
        }
    }

    // MARK: - Initial Seed Data

    private func defaultCategories() -> [ProductCategory] {
        [
            ProductCategory(name: "Alimentation", colorHex: "#10B981"),
            ProductCategory(name: "Boissons", colorHex: "#3B82F6"),
            ProductCategory(name: "Hygiène", colorHex: "#EC4899"),
            ProductCategory(name: "Divers", colorHex: "#8B5CF6")
        ]
    }

    private func defaultProducts() -> [Product] {
        [
            Product(name: "Riz Parfumé 5kg", salePrice: 4500, costPrice: 3800, stockQuantity: 25, alertStock: 5, barcode: "618110012345"),
            Product(name: "Huile de Palme 1L", salePrice: 1200, costPrice: 950, stockQuantity: 40, alertStock: 8, barcode: "618110067890"),
            Product(name: "Savon de Marseille", salePrice: 500, costPrice: 350, stockQuantity: 60, alertStock: 10, barcode: "618110099999"),
            Product(name: "Coca Cola 33cl", salePrice: 400, costPrice: 280, stockQuantity: 48, alertStock: 12, barcode: "5449000000996")
        ]
    }

    private func defaultCustomers() -> [Customer] {
        [
            Customer(name: "M. Kouamé", phone: "0708091011", address: "Cocody", totalDebt: 0),
            Customer(name: "Mme Touré", phone: "0506070809", address: "Yopougon", totalDebt: 3500)
        ]
    }

    private func defaultPromoCodes() -> [PromoCode] {
        [
            PromoCode(code: "SOLDES10", discountPercent: 10, maxUsage: 100, currentUsage: 2, isActive: true),
            PromoCode(code: "PROMO20", discountPercent: 20, maxUsage: 50, currentUsage: 0, isActive: true)
        ]
    }
}
