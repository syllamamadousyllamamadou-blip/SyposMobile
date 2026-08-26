import SwiftUI

public enum ActiveReportSheet: Identifiable {
    case addExpense
    case zReport(ZReportSummary)
    case deleteExpensePin(Expense)

    public var id: String {
        switch self {
        case .addExpense: return "addExpense"
        case .zReport(let z): return "zReport_\(z.dateText)_\(z.totalSales)"
        case .deleteExpensePin(let e): return "deleteExpensePin_\(e.id)"
        }
    }
}

public struct ReportView: View {
    @ObservedObject var dataStore: DataStore
    @StateObject private var viewModel = ReportViewModel()

    @State private var activeSheet: ActiveReportSheet? = nil
    @State private var expenseToDeleteDirectly: Expense? = nil
    @State private var showDeleteExpenseConfirmation = false

    public var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    // Date Filter Chips
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach([DateRangeFilter.today, DateRangeFilter.yesterday, DateRangeFilter.thisWeek, DateRangeFilter.thisMonth, DateRangeFilter.all], id: \.self) { filter in
                                Button(action: { viewModel.selectedFilter = filter }) {
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
                    }

                    let zData = viewModel.calculateZReport(tickets: dataStore.tickets, expenses: dataStore.expenses)

                    // Top Financial Summary Cards
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        KpiCard(title: "Chiffre d'Affaires", value: "\(Int(zData.totalSales)) CFA", icon: "chart.line.uptrend.xyaxis", color: .blue)
                        KpiCard(title: "Cash Net en Caisse", value: "\(Int(zData.netCashInDrawer)) CFA", icon: "banknote.fill", color: .green)
                        KpiCard(title: "Dépenses / Sorties", value: "\(Int(zData.totalExpenses)) CFA", icon: "arrow.down.circle.fill", color: .red)
                        KpiCard(title: "Tickets Validés", value: "\(zData.ticketsCount)", icon: "doc.text.fill", color: .orange)
                    }
                    .padding(.horizontal, 16)

                    // Trigger Z-Report Button
                    Button(action: { activeSheet = .zReport(zData) }) {
                        HStack {
                            Image(systemName: "printer.fill")
                            Text("Générer & Imprimer le Rapport Z de Caisse")
                                .bold()
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                        .padding(.horizontal, 16)
                    }

                    // Payment Breakdown Section
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Ventilation des Encaissements")
                            .font(.headline)
                            .padding(.horizontal, 16)

                        VStack(spacing: 8) {
                            PaymentBreakdownRow(name: "Espèces (Cash)", amount: zData.cashSales, color: .green)
                            PaymentBreakdownRow(name: "Wave Money", amount: zData.waveSales, color: .blue)
                            PaymentBreakdownRow(name: "Orange Money", amount: zData.orangeMoneySales, color: .orange)
                            PaymentBreakdownRow(name: "MTN MoMo", amount: zData.mtnSales, color: .yellow)
                            PaymentBreakdownRow(name: "Moov Money", amount: zData.moovSales, color: .blue)
                            PaymentBreakdownRow(name: "Carte Bancaire", amount: zData.cardSales, color: .purple)
                            PaymentBreakdownRow(name: "Ventes à Crédit", amount: zData.creditSales, color: .red)
                        }
                        .padding()
                        .background(Color(.systemBackground))
                        .cornerRadius(14)
                        .padding(.horizontal, 16)
                    }

                    // Expenses List Section
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Text("Dépenses de la Période")
                                .font(.headline)
                            Spacer()
                            Button(action: { activeSheet = .addExpense }) {
                                HStack(spacing: 4) {
                                    Image(systemName: "plus.circle.fill")
                                    Text("Ajouter")
                                }
                                .font(.subheadline)
                                .foregroundColor(.blue)
                            }
                        }
                        .padding(.horizontal, 16)

                        let expList = viewModel.filteredExpenses(expenses: dataStore.expenses)
                        if expList.isEmpty {
                            Text("Aucune dépense enregistrée pour cette période.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .padding(.horizontal, 16)
                        } else {
                            VStack(spacing: 6) {
                                ForEach(expList) { exp in
                                    HStack {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(exp.description)
                                                .font(.subheadline)
                                                .bold()
                                            let formatter = DateFormatter()
                                            let _ = formatter.dateFormat = "dd/MM HH:mm"
                                            Text(formatter.string(from: exp.date))
                                                .font(.caption2)
                                                .foregroundColor(.secondary)
                                        }
                                        Spacer()
                                        Text("-\(Int(exp.amount)) CFA")
                                            .font(.subheadline)
                                            .bold()
                                            .foregroundColor(.red)

                                        Button(action: {
                                            if dataStore.settings.pinLockEnabled {
                                                activeSheet = .deleteExpensePin(exp)
                                            } else {
                                                expenseToDeleteDirectly = exp
                                                showDeleteExpenseConfirmation = true
                                            }
                                        }) {
                                            Image(systemName: "trash")
                                                .foregroundColor(.red)
                                                .padding(.leading, 8)
                                        }
                                        .buttonStyle(.plain)
                                    }
                                    .padding(.vertical, 6)
                                    Divider()
                                }
                            }
                            .padding()
                            .background(Color(.systemBackground))
                            .cornerRadius(14)
                            .padding(.horizontal, 16)
                        }
                    }
                }
                .padding(.vertical, 12)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Bilan & Rapport Z")
            .confirmationDialog("Supprimer cette dépense ?", isPresented: $showDeleteExpenseConfirmation, titleVisibility: .visible) {
                Button("Supprimer définitivement", role: .destructive) {
                    if let e = expenseToDeleteDirectly {
                        dataStore.deleteExpense(e)
                        expenseToDeleteDirectly = nil
                    }
                }
                Button("Annuler", role: .cancel) {
                    expenseToDeleteDirectly = nil
                }
            }
            .sheet(item: $activeSheet) { sheetType in
                switch sheetType {
                case .addExpense:
                    AddExpenseSheetView(dataStore: dataStore)

                case .zReport(let zData):
                    ZReportSheetView(zData: zData, dataStore: dataStore)

                case .deleteExpensePin(let exp):
                    PinAuthSheetView(
                        title: "Autorisation Admin pour Supprimer une Dépense",
                        correctPin: dataStore.settings.adminPin,
                        onSuccess: {
                            activeSheet = nil
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                                dataStore.deleteExpense(exp)
                            }
                        }
                    )
                }
            }
        }
    }
}

struct KpiCard: View {
    let title: String
    let value: String
    let icon: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon)
                    .foregroundColor(color)
                Spacer()
            }
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
            Text(value)
                .font(.headline)
                .bold()
                .foregroundColor(color)
        }
        .padding(14)
        .background(Color(.systemBackground))
        .cornerRadius(14)
        .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 2)
    }
}

struct PaymentBreakdownRow: View {
    let name: String
    let amount: Double
    let color: Color

    var body: some View {
        HStack {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
            Text(name)
                .font(.subheadline)
            Spacer()
            Text("\(Int(amount)) CFA")
                .font(.subheadline)
                .bold()
        }
    }
}

public struct AddExpenseSheetView: View {
    @ObservedObject var dataStore: DataStore
    @Environment(\.presentationMode) var presentationMode
    @State private var description: String = ""
    @State private var amount: String = ""

    public var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Détails de la Dépense")) {
                    TextField("Motif (ex: Facture électricité, transport...)", text: $description)
                    TextField("Montant (CFA)", text: $amount)
                        .keyboardType(.numberPad)
                }
            }
            .navigationTitle("Nouvelle Dépense")
            .navigationBarItems(
                leading: Button("Annuler") { presentationMode.wrappedValue.dismiss() },
                trailing: Button("Enregistrer") {
                    if let amt = Double(amount), !description.isEmpty {
                        dataStore.addExpense(amount: amt, description: description)
                        presentationMode.wrappedValue.dismiss()
                    }
                }.disabled(description.isEmpty || amount.isEmpty)
            )
        }
    }
}

public struct ZReportSheetView: View {
    public var zData: ZReportSummary
    @ObservedObject var dataStore: DataStore
    @Environment(\.presentationMode) var presentationMode

    @State private var printStatusMessage: String? = nil
    @State private var isPrinting = false

    public var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    VStack(spacing: 4) {
                        Text("RAPPORT Z DE CAISSE")
                            .font(.title2)
                            .bold()
                        Text(dataStore.settings.shopName.isEmpty ? "SYPOS COMMERCE" : dataStore.settings.shopName)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Text("Période : \(zData.dateText)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.top, 16)

                    Divider()

                    VStack(spacing: 8) {
                        HStack {
                            Text("TOTAL VENTES :")
                                .font(.headline)
                            Spacer()
                            Text("\(Int(zData.totalSales)) CFA")
                                .font(.headline)
                                .bold()
                        }
                        HStack {
                            Text("Nombre de tickets :")
                            Spacer()
                            Text("\(zData.ticketsCount)")
                        }
                        HStack {
                            Text("Total Dépenses :")
                            Spacer()
                            Text("-\(Int(zData.totalExpenses)) CFA")
                                .foregroundColor(.red)
                        }
                        Divider()
                        HStack {
                            Text("CASH NET EN CAISSE :")
                                .font(.title3)
                                .bold()
                            Spacer()
                            Text("\(Int(zData.netCashInDrawer)) CFA")
                                .font(.title3)
                                .bold()
                                .foregroundColor(.green)
                        }
                    }
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                    .padding(.horizontal, 16)

                    if let msg = printStatusMessage {
                        Text(msg)
                            .font(.caption)
                            .bold()
                            .foregroundColor(.blue)
                    }

                    VStack(spacing: 12) {
                        Button(action: {
                            isPrinting = true
                            printStatusMessage = "Envoi à l'imprimante Bluetooth..."
                            BluetoothPrinterManager.shared.printZReport(zData: zData, settings: dataStore.settings) { result in
                                isPrinting = false
                                switch result {
                                case .success:
                                    printStatusMessage = "✅ Rapport Z imprimé avec succès !"
                                case .failure(let err):
                                    printStatusMessage = "❌ Erreur: \(err.localizedDescription)"
                                }
                            }
                        }) {
                            HStack {
                                Image(systemName: "printer.fill")
                                Text(isPrinting ? "Impression en cours..." : "Imprimer sur Imprimante Thermique")
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(Color.blue)
                            .foregroundColor(.white)
                            .cornerRadius(12)
                        }
                        .disabled(isPrinting)

                        Button(action: {
                            if let url = PdfExportManager.exportZReportPdf(zData: zData, settings: dataStore.settings) {
                                PdfExportManager.shareFile(url: url)
                            }
                        }) {
                            HStack {
                                Image(systemName: "square.and.arrow.up")
                                Text("Partager Rapport Z (WhatsApp / PDF)")
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(Color.green)
                            .foregroundColor(.white)
                            .cornerRadius(12)
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }
            .navigationTitle("Clôture de Caisse")
            .navigationBarItems(trailing: Button("Fermer") { presentationMode.wrappedValue.dismiss() })
        }
    }
}
