import SwiftUI

public struct HistoryView: View {
    @ObservedObject var dataStore: DataStore
    @StateObject private var viewModel = HistoryViewModel()

    @State private var showCustomDateSheet = false
    @State private var ticketToCancelWithPin: Ticket? = nil
    @State private var selectedTicketForDetails: Ticket? = nil

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
                                    showCustomDateSheet = true
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
                                    let _ = formatter.dateFormat = "dd/MM/yyyy HH:mm"
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
                            selectedTicketForDetails = ticket
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            if ticket.status != .cancelled {
                                Button(role: .destructive) {
                                    if dataStore.settings.pinLockEnabled {
                                        ticketToCancelWithPin = ticket
                                    } else {
                                        dataStore.cancelTicket(ticketId: ticket.id)
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
            .navigationTitle("Historique des Ventes")
            .searchable(text: $viewModel.searchQuery, prompt: "Rechercher par N° de ticket...")
            .sheet(isPresented: $showCustomDateSheet) {
                CustomDateRangeSheetView(
                    startDate: $viewModel.customStartDate,
                    endDate: $viewModel.customEndDate
                )
            }
            .sheet(item: $selectedTicketForDetails) { ticket in
                ReceiptSheetView(ticket: ticket, dataStore: dataStore)
            }
            .sheet(item: $ticketToCancelWithPin) { ticket in
                PinAuthSheetView(
                    title: "Autorisation Admin pour Annuler la Vente",
                    correctPin: dataStore.settings.adminPin,
                    onSuccess: {
                        dataStore.cancelTicket(ticketId: ticket.id)
                    }
                )
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
