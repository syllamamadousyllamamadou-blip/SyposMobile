import Foundation
import Combine

public class HistoryViewModel: ObservableObject {
    @Published public var selectedFilter: DateRangeFilter = .all
    @Published public var customStartDate: Date = Calendar.current.date(byAdding: .day, value: -7, to: Date()) ?? Date()
    @Published public var customEndDate: Date = Date()
    @Published public var searchQuery: String = ""

    private var dataStore: DataStore

    public init(dataStore: DataStore = .shared) {
        self.dataStore = dataStore
    }

    public func filteredTickets(tickets: [Ticket]) -> [Ticket] {
        let calendar = Calendar.current
        let now = Date()

        return tickets.filter { ticket in
            // Date Filter
            let matchesDate: Bool
            switch selectedFilter {
            case .all:
                matchesDate = true
            case .today:
                matchesDate = calendar.isDateInToday(ticket.date)
            case .yesterday:
                matchesDate = calendar.isDateInYesterday(ticket.date)
            case .thisWeek:
                matchesDate = calendar.isDate(ticket.date, equalTo: now, toGranularity: .weekOfYear)
            case .thisMonth:
                matchesDate = calendar.isDate(ticket.date, equalTo: now, toGranularity: .month)
            case .custom:
                let startOfDay = calendar.startOfDay(for: customStartDate)
                let endOfDay = calendar.date(bySettingHour: 23, minute: 59, second: 59, of: customEndDate) ?? customEndDate
                matchesDate = ticket.date >= startOfDay && ticket.date <= endOfDay
            }

            // Search Query
            let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
            let matchesSearch = query.isEmpty ||
                ticket.ticketNumber.localizedCaseInsensitiveContains(query) ||
                (ticket.sellerName?.localizedCaseInsensitiveContains(query) ?? false)

            return matchesDate && matchesSearch
        }
    }

    public func totalSales(for tickets: [Ticket]) -> Double {
        tickets.filter { $0.status == .paid || $0.status == .credit }.reduce(0) { $0 + $1.totalAmount }
    }
}
