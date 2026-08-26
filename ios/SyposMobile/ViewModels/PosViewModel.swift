import Foundation
import Combine

public struct CartItem: Identifiable, Equatable {
    public var id: String { product.id }
    public var product: Product
    public var quantity: Int
    public var customPrice: Double?
    public var discountPercent: Double

    public init(product: Product, quantity: Int = 1, customPrice: Double? = nil, discountPercent: Double = 0.0) {
        self.product = product
        self.quantity = quantity
        self.customPrice = customPrice
        self.discountPercent = discountPercent
    }

    public var unitPrice: Double {
        customPrice ?? product.salePrice
    }

    public var total: Double {
        let base = unitPrice * Double(quantity)
        return base * (1.0 - (discountPercent / 100.0))
    }
}

public class PosViewModel: ObservableObject {
    @Published public var cart: [CartItem] = []
    @Published public var selectedCategoryId: String? = nil
    @Published public var searchQuery: String = ""
    @Published public var selectedCustomer: Customer? = nil
    @Published public var selectedOrderType: OrderType = .takeaway
    @Published public var appliedPromoCode: PromoCode? = nil
    @Published public var globalDiscountPercent: Double = 0.0

    private var dataStore: DataStore
    private var cancellables = Set<AnyCancellable>()

    public init(dataStore: DataStore = .shared) {
        self.dataStore = dataStore
    }

    public var subTotal: Double {
        cart.reduce(0) { $0 + $1.total }
    }

    public var effectiveDiscountPercent: Double {
        max(globalDiscountPercent, appliedPromoCode?.discountPercent ?? 0.0)
    }

    public var discountAmount: Double {
        subTotal * (effectiveDiscountPercent / 100.0)
    }

    public var netAfterDiscount: Double {
        max(0, subTotal - discountAmount)
    }

    public var taxAmount: Double {
        if dataStore.settings.taxEnabled {
            return netAfterDiscount * (dataStore.settings.taxRatePercent / 100.0)
        }
        return 0.0
    }

    public var totalAmount: Double {
        netAfterDiscount + taxAmount
    }

    public var totalItemCount: Int {
        cart.reduce(0) { $0 + $1.quantity }
    }

    public func addToCart(product: Product) {
        if let index = cart.firstIndex(where: { $0.product.id == product.id }) {
            if !dataStore.settings.allowNegativeStock && cart[index].quantity >= product.stockQuantity {
                return
            }
            cart[index].quantity += 1
        } else {
            if !dataStore.settings.allowNegativeStock && product.stockQuantity <= 0 {
                return
            }
            cart.append(CartItem(product: product, quantity: 1))
        }
    }

    public func increaseQuantity(productId: String) {
        if let index = cart.firstIndex(where: { $0.product.id == productId }) {
            let prod = cart[index].product
            if !dataStore.settings.allowNegativeStock && cart[index].quantity >= prod.stockQuantity {
                return
            }
            cart[index].quantity += 1
        }
    }

    public func decreaseQuantity(productId: String) {
        if let index = cart.firstIndex(where: { $0.product.id == productId }) {
            if cart[index].quantity > 1 {
                cart[index].quantity -= 1
            } else {
                cart.remove(at: index)
            }
        }
    }

    public func updateCartItemPrice(productId: String, customPrice: Double?) {
        if let index = cart.firstIndex(where: { $0.product.id == productId }) {
            cart[index].customPrice = customPrice
        }
    }

    public func updateCartItemDiscount(productId: String, discountPercent: Double) {
        if let index = cart.firstIndex(where: { $0.product.id == productId }) {
            cart[index].discountPercent = discountPercent
        }
    }

    public func removeFromCart(productId: String) {
        cart.removeAll { $0.product.id == productId }
    }

    public func clearCart() {
        cart.removeAll()
        appliedPromoCode = nil
        globalDiscountPercent = 0.0
        selectedCustomer = nil
    }

    public func applyPromoCode(code: String, completion: @escaping (Bool, String) -> Void) {
        let clean = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard let promo = dataStore.promoCodes.first(where: { $0.code.uppercased() == clean && $0.isActive }) else {
            completion(false, "Code promo invalide ou inactif")
            return
        }

        if promo.currentUsage >= promo.maxUsage {
            completion(false, "Ce code promo a atteint sa limite d'utilisation")
            return
        }

        appliedPromoCode = promo
        completion(true, "Code promo appliqué : -\(Int(promo.discountPercent))%")
    }

    public func removePromoCode() {
        appliedPromoCode = nil
    }

    public func holdCart(note: String) {
        guard !cart.isEmpty else { return }

        let number = "HOLD-\(Int(Date().timeIntervalSince1970) % 10000)"
        let items = cart.map {
            TicketItem(ticketId: number, productId: $0.product.id, productName: $0.product.name, quantity: $0.quantity, unitPrice: $0.unitPrice, total: $0.total)
        }

        let ticket = Ticket(
            ticketNumber: number,
            date: Date(),
            status: .onHold,
            orderType: selectedOrderType,
            subTotal: subTotal,
            taxAmount: taxAmount,
            totalAmount: totalAmount,
            discount: discountAmount,
            customerId: selectedCustomer?.id,
            sellerName: dataStore.settings.sellerName,
            note: note.isEmpty ? "Panier en attente" : note,
            items: items
        )

        dataStore.recordSale(ticket: ticket)
        clearCart()
    }

    public func resumeHeldCart(ticket: Ticket) {
        clearCart()
        for item in ticket.items {
            if let prod = dataStore.products.first(where: { $0.id == item.productId }) {
                cart.append(CartItem(product: prod, quantity: item.quantity, customPrice: item.unitPrice != prod.salePrice ? item.unitPrice : nil))
            } else {
                let prod = Product(id: item.productId, name: item.productName, salePrice: item.unitPrice, stockQuantity: 999)
                cart.append(CartItem(product: prod, quantity: item.quantity))
            }
        }
        selectedOrderType = ticket.orderType
        if let custId = ticket.customerId {
            selectedCustomer = dataStore.customers.first(where: { $0.id == custId })
        }
        dataStore.deleteHeldTicket(ticketId: ticket.id)
    }

    public func processPayment(method: PaymentMethod, amountPaid: Double) -> Ticket {
        let number = "T-\(Int(Date().timeIntervalSince1970) % 100000)"
        let change = method == .cash ? max(0, amountPaid - totalAmount) : 0.0

        let items = cart.map {
            TicketItem(ticketId: number, productId: $0.product.id, productName: $0.product.name, quantity: $0.quantity, unitPrice: $0.unitPrice, total: $0.total)
        }

        let ticket = Ticket(
            ticketNumber: number,
            date: Date(),
            status: method == .credit ? .credit : .paid,
            orderType: selectedOrderType,
            subTotal: subTotal,
            taxAmount: taxAmount,
            totalAmount: totalAmount,
            discount: discountAmount,
            paymentMethod: method,
            amountPaid: method == .cash ? amountPaid : totalAmount,
            changeReturned: change,
            customerId: selectedCustomer?.id,
            sellerName: dataStore.settings.sellerName,
            items: items
        )

        dataStore.recordSale(ticket: ticket)

        if let promo = appliedPromoCode {
            dataStore.incrementPromoUsage(code: promo.code)
        }

        if dataStore.settings.autoPrintReceipt {
            BluetoothPrinterManager.shared.printTicket(ticket: ticket, customerName: selectedCustomer?.name, settings: dataStore.settings) { _ in }
        }

        clearCart()
        return ticket
    }
}
