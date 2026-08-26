import SwiftUI

public enum ActiveCustomerSheet: Identifiable {
    case addCustomer
    case editCustomer(Customer)
    case settleDebt(Customer)
    case deleteCustomerPin(Customer)

    public var id: String {
        switch self {
        case .addCustomer: return "addCustomer"
        case .editCustomer(let c): return "edit_\(c.id)"
        case .settleDebt(let c): return "settle_\(c.id)"
        case .deleteCustomerPin(let c): return "deletePin_\(c.id)"
        }
    }
}

public struct CustomerListView: View {
    @ObservedObject var dataStore: DataStore
    @StateObject private var viewModel = CustomerViewModel()

    @State private var activeSheet: ActiveCustomerSheet? = nil
    @State private var customerToDeleteDirectly: Customer? = nil
    @State private var showDeleteConfirmation = false

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Debt Summary Banner
                let totalDebt = viewModel.totalDebt(customers: dataStore.customers)
                if totalDebt > 0 {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("TOTAL CRÉANCES CLIENTS")
                                .font(.caption2)
                                .bold()
                                .foregroundColor(.secondary)
                            Text("\(Int(totalDebt)) CFA à recouvrer")
                                .font(.headline)
                                .bold()
                                .foregroundColor(.red)
                        }
                        Spacer()
                        Toggle("Dettes uniquement", isOn: $viewModel.onlyDebtors)
                            .labelsHidden()
                    }
                    .padding(12)
                    .background(Color.red.opacity(0.1))
                    .cornerRadius(12)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                }

                let filtered = viewModel.filteredCustomers(customers: dataStore.customers)

                if filtered.isEmpty {
                    VStack(spacing: 12) {
                        Spacer()
                        Image(systemName: "person.crop.circle.badge.plus")
                            .font(.system(size: 48))
                            .foregroundColor(.secondary)
                        Text(dataStore.customers.isEmpty ? "Aucun client enregistré" : "Aucun client trouvé")
                            .font(.headline)
                            .foregroundColor(.secondary)
                        Button("Ajouter un client") {
                            activeSheet = .addCustomer
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                        Spacer()
                    }
                } else {
                    List {
                        ForEach(filtered) { customer in
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(customer.name)
                                        .font(.headline)

                                    if let phone = customer.phone, !phone.isEmpty {
                                        Text(phone)
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                }

                                Spacer()

                                if customer.totalDebt > 0 {
                                    VStack(alignment: .trailing, spacing: 4) {
                                        Text("\(Int(customer.totalDebt)) CFA")
                                            .font(.subheadline)
                                            .bold()
                                            .foregroundColor(.red)

                                        Button(action: { activeSheet = .settleDebt(customer) }) {
                                            Text("Encaisser")
                                                .font(.caption2)
                                                .bold()
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(Color.green)
                                                .foregroundColor(.white)
                                                .cornerRadius(6)
                                        }
                                        .buttonStyle(.plain)
                                    }
                                } else {
                                    Text("À jour")
                                        .font(.caption)
                                        .foregroundColor(.green)
                                }
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                activeSheet = .editCustomer(customer)
                            }
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                Button(role: .destructive) {
                                    if dataStore.settings.pinLockEnabled {
                                        activeSheet = .deleteCustomerPin(customer)
                                    } else {
                                        customerToDeleteDirectly = customer
                                        showDeleteConfirmation = true
                                    }
                                } label: {
                                    Label("Supprimer", systemImage: "trash")
                                }

                                Button {
                                    activeSheet = .editCustomer(customer)
                                } label: {
                                    Label("Modifier", systemImage: "pencil")
                                }
                                .tint(.blue)
                            }
                        }
                    }
                    .listStyle(PlainListStyle())
                }
            }
            .navigationTitle("Clients & Dettes")
            .searchable(text: $viewModel.searchQuery, prompt: "Rechercher par nom ou numéro...")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { activeSheet = .addCustomer }) {
                        Image(systemName: "person.badge.plus")
                    }
                }
            }
            .confirmationDialog("Supprimer ce client ?", isPresented: $showDeleteConfirmation, titleVisibility: .visible) {
                Button("Supprimer définitivement", role: .destructive) {
                    if let c = customerToDeleteDirectly {
                        dataStore.deleteCustomer(c)
                        customerToDeleteDirectly = nil
                    }
                }
                Button("Annuler", role: .cancel) {
                    customerToDeleteDirectly = nil
                }
            } message: {
                Text("Voulez-vous vraiment supprimer la fiche de \"\(customerToDeleteDirectly?.name ?? "")\" ?")
            }
            .sheet(item: $activeSheet) { sheetType in
                switch sheetType {
                case .addCustomer:
                    AddEditCustomerSheetView(dataStore: dataStore)

                case .editCustomer(let customer):
                    AddEditCustomerSheetView(dataStore: dataStore, existingCustomer: customer)

                case .settleDebt(let customer):
                    SettleDebtSheetView(customer: customer) { amount in
                        dataStore.settleDebt(customerId: customer.id, amount: amount)
                    }

                case .deleteCustomerPin(let customer):
                    PinAuthSheetView(
                        title: "Autorisation Admin pour Supprimer un Client",
                        correctPin: dataStore.settings.adminPin,
                        onSuccess: {
                            activeSheet = nil
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                                dataStore.deleteCustomer(customer)
                            }
                        }
                    )
                }
            }
        }
    }
}

public struct AddEditCustomerSheetView: View {
    @ObservedObject var dataStore: DataStore
    public var existingCustomer: Customer? = nil
    @Environment(\.presentationMode) var presentationMode

    @State private var name = ""
    @State private var phone = ""
    @State private var address = ""
    @State private var debt = "0"

    public var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Coordonnées Client")) {
                    TextField("Nom et Prénoms", text: $name)
                    TextField("Téléphone WhatsApp", text: $phone)
                        .keyboardType(.phonePad)
                    TextField("Adresse / Quartier", text: $address)
                }

                if existingCustomer == nil {
                    Section(header: Text("Dette Initiale (CFA)")) {
                        TextField("Montant dette éventuelle", text: $debt)
                            .keyboardType(.numberPad)
                    }
                }
            }
            .navigationTitle(existingCustomer == nil ? "Nouveau Client" : "Modifier Client")
            .navigationBarItems(
                leading: Button("Annuler") { presentationMode.wrappedValue.dismiss() },
                trailing: Button("Enregistrer") {
                    let d = Double(debt) ?? 0
                    let customer = Customer(
                        id: existingCustomer?.id ?? UUID().uuidString,
                        name: name.trimmingCharacters(in: .whitespacesAndNewlines),
                        phone: phone.trimmingCharacters(in: .whitespacesAndNewlines),
                        address: address.trimmingCharacters(in: .whitespacesAndNewlines),
                        totalDebt: existingCustomer?.totalDebt ?? d
                    )
                    dataStore.addOrUpdateCustomer(customer)
                    presentationMode.wrappedValue.dismiss()
                }.disabled(name.isEmpty)
            )
            .onAppear {
                if let c = existingCustomer {
                    name = c.name
                    phone = c.phone ?? ""
                    address = c.address ?? ""
                }
            }
        }
    }
}

public struct SettleDebtSheetView: View {
    public var customer: Customer
    public var onSettle: (Double) -> Void
    @Environment(\.presentationMode) var presentationMode
    @State private var amountInput: String = ""

    public var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("Règlement de dette pour \"\(customer.name)\"")
                    .font(.headline)
                    .padding(.top, 20)

                Text("Dette restante : \(Int(customer.totalDebt)) CFA")
                    .font(.title2)
                    .bold()
                    .foregroundColor(.red)

                TextField("Montant versé (CFA)", text: $amountInput)
                    .keyboardType(.numberPad)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .padding(.horizontal)

                Spacer()

                Button(action: {
                    if let amt = Double(amountInput), amt > 0 {
                        onSettle(amt)
                        presentationMode.wrappedValue.dismiss()
                    }
                }) {
                    Text("Valider l'Encaissement")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.green)
                        .cornerRadius(12)
                }
                .padding(.horizontal)
                .padding(.bottom)
            }
            .navigationTitle("Encaissement Dette")
            .navigationBarItems(leading: Button("Annuler") { presentationMode.wrappedValue.dismiss() })
        }
    }
}
