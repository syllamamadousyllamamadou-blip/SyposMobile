import SwiftUI

public enum ActiveHistorySheet: Identifiable {
    case customDate
    case ticketDetails(Ticket)
    case cancelPin(Ticket)

    public var id: String {
        switch self {
        case .customDate: return "customDate"
        case .ticketDetails(let t): return "details_\(t.id)"
        case .cancelPin(let t): return "cancelPin_\(t.id)"
        }
    }
}

public struct HistoryView: View {
    @ObservedObject var dataStore: DataStore
    @StateObject private var viewModel = HistoryViewModel()

    @State private var activeSheet: ActiveHistorySheet? = nil
    @State private var ticketToCancelDirectly: Ticket? = nil
    @State private var showCancelConfirmation = false

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Date Filter Chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(DateRangeFilter.allCases, id: \.self) { filter in
                            Button(action: {
                                viewModel.selectedFilter = filter
                                if filter == .custom {
                                    activeSheet = .customDate
                                }
                            }) {
                                Text(filter.rawValue)
                                    .font(.subheadline)
                                    .bold()
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background(viewModel.selectedFilter == filter ? Color.blue : Color(.systemGray6))
                                    .foregroundColor(viewModel.selectedFilter == filter ? .white : .primary)
                                    .cornerRadius(18)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                }
                .background(Color(.systemBackground))

                let filtered = viewModel.filteredTickets(tickets: dataStore.tickets)
                let total = viewModel.totalSales(for: filtered)

                // Summary Total Header
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(filtered.count) ticket(s) trouvé(s)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text("Total : \(Int(total)) CFA")
                            .font(.headline)
                            .bold()
                            .foregroundColor(.blue)
                    }

                    Spacer()

                    Button(action: {
                        if let url = PdfExportManager.exportSalesPdf(tickets: filtered, settings: dataStore.settings) {
                            PdfExportManager.shareFile(url: url)
                        }
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.down.doc.fill")
                            Text("Export PDF")
                                .bold()
                        }
                        .font(.caption)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.green)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)

                // List of Tickets
                if filtered.isEmpty {
                    VStack(spacing: 12) {
                        Spacer()
                        Image(systemName: "doc.text.magnifyingglass")
                            .font(.system(size: 48))
                            .foregroundColor(.secondary)
                        Text("Aucune vente trouvée pour cette période")
                            .font(.headline)
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                } else {
                    List {
                        ForEach(filtered) { ticket in
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text(ticket.ticketNumber)
                                            .font(.headline)

                                        Text(ticket.status.displayName)
                                            .font(.caption2)
                                            .bold()
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(statusColor(ticket.status).opacity(0.15))
                                            .foregroundColor(statusColor(ticket.status))
                                            .cornerRadius(6)
                                    }

                                    HStack(spacing: 6) {
                                        let formatter = DateFormatter()
                                        formatter.dateFormat = "dd/MM/yyyy HH:mm"
                                        Text(formatter.string(from: ticket.date))
                                            .font(.caption)
                                            .foregroundColor(.secondary)

                                        if let method = ticket.paymentMethod {
                                            Text("• \(method.displayName)")
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                        }
                                    }
                                }

                                Spacer()

                                VStack(alignment: .trailing, spacing: 4) {
                                    Text("\(Int(ticket.totalAmount)) CFA")
                                        .font(.headline)
                                        .bold()

                                    Text("\(ticket.items.count) article(s)")
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                }
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                activeSheet = .ticketDetails(ticket)
                            }
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                if ticket.status != .cancelled {
                                    Button(role: .destructive) {
                                        if dataStore.settings.pinLockEnabled {
                                            activeSheet = .cancelPin(ticket)
                                        } else {
                                            ticketToCancelDirectly = ticket
                                            showCancelConfirmation = true
                                        }
                                    } label: {
                                        Label("Annuler", systemImage: "xmark.circle")
                                    }
                                }

                                Button {
                                    BluetoothPrinterManager.shared.printTicket(ticket: ticket, customerName: nil, settings: dataStore.settings) { _ in }
                                } label: {
                                    Label("Imprimer", systemImage: "printer")
                                }
                                .tint(.blue)
                            }
                        }
                    }
                    .listStyle(PlainListStyle())
                }
            }
            .navigationTitle("Historique des Ventes")
            .searchable(text: $viewModel.searchQuery, prompt: "Rechercher par N° de ticket...")
            .confirmationDialog("Annuler ce ticket ?", isPresented: $showCancelConfirmation, titleVisibility: .visible) {
                Button("Confirmer l'annulation (Restocker)", role: .destructive) {
                    if let t = ticketToCancelDirectly {
                        dataStore.cancelTicket(ticketId: t.id)
                        ticketToCancelDirectly = nil
                    }
                }
                Button("Ne rien faire", role: .cancel) {
                    ticketToCancelDirectly = nil
                }
            } message: {
                Text("L'annulation remettra automatiquement les articles du ticket en stock.")
            }
            .sheet(item: $activeSheet) { sheetType in
                switch sheetType {
                case .customDate:
                    CustomDateRangeSheetView(
                        startDate: $viewModel.customStartDate,
                        endDate: $viewModel.customEndDate
                    )

                case .ticketDetails(let ticket):
                    ReceiptSheetView(ticket: ticket, dataStore: dataStore)

                case .cancelPin(let ticket):
                    PinAuthSheetView(
                        title: "Autorisation Admin pour Annuler la Vente",
                        correctPin: dataStore.settings.adminPin,
                        onSuccess: {
                            activeSheet = nil
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                                dataStore.cancelTicket(ticketId: ticket.id)
                            }
                        }
                    )
                }
            }
        }
    }

    private func statusColor(_ status: TicketStatus) -> Color {
        switch status {
        case .paid: return .green
        case .onHold: return .orange
        case .credit: return .purple
        case .cancelled: return .red
        }
    }
}

public struct CustomDateRangeSheetView: View {
    @Binding public var startDate: Date
    @Binding public var endDate: Date
    @Environment(\.presentationMode) var presentationMode

    public var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Date de début")) {
                    DatePicker("Du", selection: $startDate, displayedComponents: [.date])
                }

                Section(header: Text("Date de fin")) {
                    DatePicker("Au", selection: $endDate, displayedComponents: [.date])
                }
            }
            .navigationTitle("Filtrer par Dates")
            .navigationBarItems(trailing: Button("Appliquer") {
                presentationMode.wrappedValue.dismiss()
            })
        }
    }
}
