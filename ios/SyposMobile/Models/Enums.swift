import Foundation

public enum PaymentMethod: String, Codable, CaseIterable {
    case cash = "CASH"
    case wave = "WAVE"
    case orangeMoney = "ORANGE_MONEY"
    case mtn = "MTN"
    case moov = "MOOV"
    case card = "CARD"
    case credit = "CREDIT"

    public var displayName: String {
        switch self {
        case .cash: return "Espèces (Cash)"
        case .wave: return "Wave Money"
        case .orangeMoney: return "Orange Money"
        case .mtn: return "MTN MoMo"
        case .moov: return "Moov Money"
        case .card: return "Carte Bancaire"
        case .credit: return "Vente à Crédit"
        }
    }
}

public enum OrderType: String, Codable, CaseIterable {
    case takeaway = "TAKEAWAY"
    case dineIn = "DINE_IN"
    case delivery = "DELIVERY"

    public var displayName: String {
        switch self {
        case .takeaway: return "À Emporter"
        case .dineIn: return "Sur Place"
        case .delivery: return "Livraison"
        }
    }
}

public enum TicketStatus: String, Codable, CaseIterable {
    case paid = "PAID"
    case onHold = "ON_HOLD"
    case credit = "CREDIT"
    case cancelled = "CANCELLED"

    public var displayName: String {
        switch self {
        case .paid: return "Payé"
        case .onHold: return "En Attente"
        case .credit: return "Crédit"
        case .cancelled: return "Annulé"
        }
    }
}

public enum BusinessMode: String, Codable, CaseIterable {
    case supermarket = "SUPERMARKET"
    case restaurant = "RESTAURANT"

    public var displayName: String {
        switch self {
        case .supermarket: return "Supermarché / Boutique"
        case .restaurant: return "Restaurant / Snack"
        }
    }
}

public enum UserRole: String, Codable, CaseIterable {
    case admin = "ADMIN"
    case cashier = "CASHIER"
}

public enum DateRangeFilter: String, CaseIterable {
    case all = "Tout"
    case today = "Aujourd'hui"
    case yesterday = "Hier"
    case thisWeek = "Cette Semaine"
    case thisMonth = "Ce Mois"
    case custom = "Personnalisé"
}
