import SwiftUI

public struct CustomerListView: View {
    @ObservedObject var dataStore: DataStore
    @StateObject private var viewModel = CustomerViewModel()

    @State private var showAddCustomerSheet = false
    @State private var customerToEdit: Customer? = nil
    @State private var customerToSettle: Customer? = nil
    @State private var customerToDeleteWithPin: Customer? = nil

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

                                    Button(action: { customerToSettle = customer }) {
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
                            customerToEdit = customer
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(role: .destructive) {
                                if dataStore.settings.pinLockEnabled {
                                    customerToDeleteWithPin = customer
                                } else {
                                    dataStore.deleteCustomer(customer)
                                }
                            } label: {
                                Label("Supprimer", systemImage: "trash")
                            }

                            Button {
                                customerToEdit = customer
                            } label: {
                                Label("Modifier", systemImage: "pencil")
                            }
                            .tint(.blue)
                        }
                    }
                }
                .listStyle(PlainListStyle())
            }
            .navigationTitle("Clients & Dettes")
            .searchable(text: $viewModel.searchQuery, prompt: "Rechercher par nom ou numéro...")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showAddCustomerSheet = true }) {
                        Image(systemName: "person.badge.plus")
                    }
                }
            }
            .sheet(isPresented: $showAddCustomerSheet) {
                AddEditCustomerSheetView(dataStore: dataStore)
            }
            .sheet(item: $customerToEdit) { customer in
                AddEditCustomerSheetView(dataStore: dataStore, existingCustomer: customer)
            }
            .sheet(item: $customerToSettle) { customer in
                SettleDebtSheetView(customer: customer) { amount in
                    dataStore.settleDebt(customerId: customer.id, amount: amount)
                }
            }
            .sheet(item: $customerToDeleteWithPin) { customer in
                PinAuthSheetView(
                    title: "Autorisation Admin pour Supprimer un Client",
                    correctPin: dataStore.settings.adminPin,
                    onSuccess: {
                        dataStore.deleteCustomer(customer)
                    }
                )
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
