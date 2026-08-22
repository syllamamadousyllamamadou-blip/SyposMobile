import Foundation
import Combine

public struct ZReportSummary {
    public var dateText: String
    public var totalSales: Double
    public var ticketsCount: Int
    public var totalExpenses: Double
    public var cashSales: Double
    public var waveSales: Double
    public var orangeMoneySales: Double
    public var mtnSales: Double
    public var moovSales: Double
    public var cardSales: Double
    public var creditSales: Double
    public var netCashInDrawer: Double
}

public class ReportViewModel: ObservableObject {
    @Published public var selectedFilter: DateRangeFilter = .today
    @Published public var customStartDate: Date = Calendar.current.date(byAdding: .day, value: -7, to: Date()) ?? Date()
    @Published public var customEndDate: Date = Date()

    private var dataStore: DataStore

    public init(dataStore: DataStore = .shared) {
        self.dataStore = dataStore
    }

    public func filteredTickets(tickets: [Ticket]) -> [Ticket] {
        let calendar = Calendar.current
        let now = Date()

        return tickets.filter { ticket in
            switch selectedFilter {
            case .all: return true
            case .today: return calendar.isDateInToday(ticket.date)
            case .yesterday: return calendar.isDateInYesterday(ticket.date)
            case .thisWeek: return calendar.isDate(ticket.date, equalTo: now, toGranularity: .weekOfYear)
            case .thisMonth: return calendar.isDate(ticket.date, equalTo: now, toGranularity: .month)
            case .custom:
                let start = calendar.startOfDay(for: customStartDate)
                let end = calendar.date(bySettingHour: 23, minute: 59, second: 59, of: customEndDate) ?? customEndDate
                return ticket.date >= start && ticket.date <= end
            }
        }
    }

    public func filteredExpenses(expenses: [Expense]) -> [Expense] {
        let calendar = Calendar.current
        let now = Date()

        return expenses.filter { exp in
            switch selectedFilter {
            case .all: return true
            case .today: return calendar.isDateInToday(exp.date)
            case .yesterday: return calendar.isDateInYesterday(exp.date)
            case .thisWeek: return calendar.isDate(exp.date, equalTo: now, toGranularity: .weekOfYear)
            case .thisMonth: return calendar.isDate(exp.date, equalTo: now, toGranularity: .month)
            case .custom:
                let start = calendar.startOfDay(for: customStartDate)
                let end = calendar.date(bySettingHour: 23, minute: 59, second: 59, of: customEndDate) ?? customEndDate
                return exp.date >= start && exp.date <= end
            }
        }
    }

    public func calculateZReport(tickets: [Ticket], expenses: [Expense]) -> ZReportSummary {
        let relevantTickets = filteredTickets(tickets: tickets).filter { $0.status == .paid || $0.status == .credit }
        let relevantExpenses = filteredExpenses(expenses: expenses)

        var cash: Double = 0
        var wave: Double = 0
        var om: Double = 0
        var mtn: Double = 0
        var moov: Double = 0
        var card: Double = 0
        var credit: Double = 0

        for t in relevantTickets {
            guard let method = t.paymentMethod else { continue }
            switch method {
            case .cash: cash += t.totalAmount
            case .wave: wave += t.totalAmount
            case .orangeMoney: om += t.totalAmount
            case .mtn: mtn += t.totalAmount
            case .moov: moov += t.totalAmount
            case .card: card += t.totalAmount
            case .credit: credit += t.totalAmount
            }
        }

        let totalSales = relevantTickets.reduce(0) { $0 + $1.totalAmount }
        let totalExpenses = relevantExpenses.reduce(0) { $0 + $1.amount }
        let netCash = max(0, cash - totalExpenses)

        let formatter = DateFormatter()
        formatter.dateFormat = "dd/MM/yyyy HH:mm"

        return ZReportSummary(
            dateText: formatter.string(from: Date()),
            totalSales: totalSales,
            ticketsCount: relevantTickets.count,
            totalExpenses: totalExpenses,
            cashSales: cash,
            waveSales: wave,
            orangeMoneySales: om,
            mtnSales: mtn,
            moovSales: moov,
            cardSales: card,
            creditSales: credit,
            netCashInDrawer: netCash
        )
    }

    // Stock Valuation Metrics
    public func stockMetrics(products: [Product]) -> (totalCost: Double, totalSale: Double, profitMargin: Double) {
        let totalCost = products.reduce(0.0) { $0 + ($1.costPrice * Double($1.stockQuantity)) }
        let totalSale = products.reduce(0.0) { $0 + ($1.salePrice * Double($1.stockQuantity)) }
        let margin = totalSale - totalCost
        return (totalCost, totalSale, margin)
    }
}
