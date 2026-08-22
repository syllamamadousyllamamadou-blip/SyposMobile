import Foundation

public struct Ticket: Identifiable, Codable, Equatable {
    public var id: String
    public var ticketNumber: String
    public var date: Date
    public var status: TicketStatus
    public var orderType: OrderType
    public var subTotal: Double
    public var taxAmount: Double
    public var totalAmount: Double
    public var discount: Double
    public var paymentMethod: PaymentMethod?
    public var amountPaid: Double
    public var changeReturned: Double
    public var customerId: String?
    public var sellerName: String?
    public var note: String?
    public var items: [TicketItem]

    public init(
        id: String = UUID().uuidString,
        ticketNumber: String,
        date: Date = Date(),
        status: TicketStatus = .paid,
        orderType: OrderType = .takeaway,
        subTotal: Double,
        taxAmount: Double = 0.0,
        totalAmount: Double,
        discount: Double = 0.0,
        paymentMethod: PaymentMethod? = nil,
        amountPaid: Double = 0.0,
        changeReturned: Double = 0.0,
        customerId: String? = nil,
        sellerName: String? = nil,
        note: String? = nil,
        items: [TicketItem] = []
    ) {
        self.id = id
        self.ticketNumber = ticketNumber
        self.date = date
        self.status = status
        self.orderType = orderType
        self.subTotal = subTotal
        self.taxAmount = taxAmount
        self.totalAmount = totalAmount
        self.discount = discount
        self.paymentMethod = paymentMethod
        self.amountPaid = amountPaid
        self.changeReturned = changeReturned
        self.customerId = customerId
        self.sellerName = sellerName
        self.note = note
        self.items = items
    }
}

public struct TicketItem: Identifiable, Codable, Equatable {
    public var id: String
    public var ticketId: String
    public var productId: String
    public var productName: String
    public var quantity: Int
    public var unitPrice: Double
    public var total: Double

    public init(
        id: String = UUID().uuidString,
        ticketId: String,
        productId: String,
        productName: String,
        quantity: Int,
        unitPrice: Double,
        total: Double
    ) {
        self.id = id
        self.ticketId = ticketId
        self.productId = productId
        self.productName = productName
        self.quantity = quantity
        self.unitPrice = unitPrice
        self.total = total
    }
}
