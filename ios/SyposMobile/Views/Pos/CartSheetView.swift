import SwiftUI

public struct CartSheetView: View {
    @ObservedObject var viewModel: PosViewModel
    @ObservedObject var dataStore: DataStore

    public var onClearCartRequested: () -> Void
    public var onRemoveItemRequested: (String) -> Void
    public var onHoldRequested: () -> Void
    public var onCheckoutRequested: () -> Void
    @Environment(\.presentationMode) var presentationMode

    @State private var promoCodeInput = ""
    @State private var promoMessage: String? = nil

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                List {
                    // Restaurant Order Type Selector
                    if dataStore.settings.businessMode == .restaurant {
                        Section(header: Text("Type de Commande")) {
                            Picker("Type", selection: $viewModel.selectedOrderType) {
                                ForEach(OrderType.allCases, id: \.self) { type in
                                    Text(type.displayName).tag(type)
                                }
                            }
                            .pickerStyle(SegmentedPickerStyle())
                        }
                    }

                    // Customer Selection
                    Section(header: Text("Client Associé")) {
                        Picker("Client", selection: $viewModel.selectedCustomer) {
                            Text("Client Comptoir (Anonyme)").tag(nil as Customer?)
                            ForEach(dataStore.customers) { customer in
                                Text(customer.name).tag(customer as Customer?)
                            }
                        }
                    }

                    // Items
                    Section(header: Text("Articles au Panier (\(viewModel.totalItemCount))")) {
                        ForEach(viewModel.cart) { item in
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(item.product.name)
                                        .font(.headline)
                                    Text("\(Int(item.product.salePrice)) CFA / unité")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }

                                Spacer()

                                HStack(spacing: 12) {
                                    Button(action: { viewModel.decreaseQuantity(productId: item.product.id) }) {
                                        Image(systemName: "minus.circle.fill")
                                            .font(.title2)
                                            .foregroundColor(.secondary)
                                    }
                                    .buttonStyle(.plain)

                                    Text("\(item.quantity)")
                                        .font(.headline)
                                        .frame(minWidth: 24)

                                    Button(action: { viewModel.increaseQuantity(productId: item.product.id) }) {
                                        Image(systemName: "plus.circle.fill")
                                            .font(.title2)
                                            .foregroundColor(.blue)
                                    }
                                    .buttonStyle(.plain)
                                }

                                Button(action: { onRemoveItemRequested(item.product.id) }) {
                                    Image(systemName: "trash")
                                        .foregroundColor(.red)
                                        .padding(.leading, 8)
                                }
                                .buttonStyle(.plain)
                            }
                            .padding(.vertical, 4)
                        }
                    }

                    // Promo Code Section
                    Section(header: Text("Code Promotionnel")) {
                        if let promo = viewModel.appliedPromoCode {
                            HStack {
                                Label("Code: \(promo.code) (-\(Int(promo.discountPercent))%)", systemImage: "tag.fill")
                                    .foregroundColor(.green)
                                    .font(.subheadline.weight(.bold))
                                Spacer()
                                Button("Retirer") { viewModel.removePromoCode() }
                                    .foregroundColor(.red)
                            }
                        } else {
                            HStack {
                                TextField("Code Promo (ex: SOLDES10)", text: $promoCodeInput)
                                    .autocapitalization(.allCharacters)

                                Button("Appliquer") {
                                    viewModel.applyPromoCode(code: promoCodeInput) { success, msg in
                                        promoMessage = msg
                                        if success { promoCodeInput = "" }
                                    }
                                }
                                .disabled(promoCodeInput.isEmpty)
                            }
                            if let msg = promoMessage {
                                Text(msg)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }

                    // Financial Summary
                    Section(header: Text("Récapitulatif Financier")) {
                        HStack {
                            Text("Sous-Total")
                            Spacer()
                            Text("\(Int(viewModel.subTotal)) CFA")
                        }

                        if viewModel.discountAmount > 0 {
                            HStack {
                                Text("Remise Promo")
                                Spacer()
                                Text("-\(Int(viewModel.discountAmount)) CFA")
                                    .foregroundColor(.green)
                            }
                        }

                        if dataStore.settings.taxEnabled && viewModel.taxAmount > 0 {
                            HStack {
                                Text("TVA (\(Int(dataStore.settings.taxRatePercent))%)")
                                Spacer()
                                Text("\(Int(viewModel.taxAmount)) CFA")
                            }
                        }

                        HStack {
                            Text("TOTAL NET A PAYER")
                                .bold()
                            Spacer()
                            Text("\(Int(viewModel.totalAmount)) CFA")
                                .font(.title3)
                                .bold()
                                .foregroundColor(.blue)
                        }
                    }
                }
                .listStyle(InsetGroupedListStyle())

                // Actions Bottom Bar
                HStack(spacing: 12) {
                    Button(action: onHoldRequested) {
                        HStack {
                            Image(systemName: "pause.circle.fill")
                            Text("En Attente")
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.orange.opacity(0.15))
                        .foregroundColor(.orange)
                        .cornerRadius(12)
                    }

                    Button(action: onCheckoutRequested) {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                            Text("Encaisser (\(Int(viewModel.totalAmount)) CFA)")
                                .bold()
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                    }
                }
                .padding(16)
                .background(Color(.systemBackground))
            }
            .navigationTitle("Panier de Vente")
            .navigationBarItems(
                leading: Button("Fermer") { presentationMode.wrappedValue.dismiss() },
                trailing: Button("Vider", action: onClearCartRequested).foregroundColor(.red)
            )
        }
    }
}

public struct PaymentSheetView: View {
    public var totalAmount: Double
    public var customerName: String?
    public var onConfirmPayment: (PaymentMethod, Double) -> Void
    @Environment(\.presentationMode) var presentationMode

    @State private var selectedMethod: PaymentMethod = .cash
    @State private var amountReceivedInput: String = ""

    public var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                // Total to pay
                VStack(spacing: 6) {
                    Text("MONTANT TOTAL")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text("\(Int(totalAmount)) CFA")
                        .font(.system(size: 36, weight: .heavy))
                        .foregroundColor(.blue)
                    if let name = customerName {
                        Text("Client : \(name)")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                }
                .padding(.top, 20)

                // Payment Methods
                VStack(alignment: .leading, spacing: 10) {
                    Text("Sélectionner le mode de paiement :")
                        .font(.caption)
                        .bold()
                        .foregroundColor(.secondary)

                    ScrollView {
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                            ForEach(PaymentMethod.allCases, id: \.self) { method in
                                Button(action: {
                                    selectedMethod = method
                                    if method != .cash {
                                        amountReceivedInput = "\(Int(totalAmount))"
                                    }
                                }) {
                                    HStack {
                                        Text(method.displayName)
                                            .font(.subheadline)
                                            .bold()
                                            .foregroundColor(selectedMethod == method ? .white : .primary)
                                        Spacer()
                                        if selectedMethod == method {
                                            Image(systemName: "checkmark.circle.fill")
                                                .foregroundColor(.white)
                                        }
                                    }
                                    .padding()
                                    .frame(height: 54)
                                    .background(selectedMethod == method ? Color.blue : Color(.systemGray6))
                                    .cornerRadius(12)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)

                // Cash Amount Received & Change
                if selectedMethod == .cash {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Espèces reçues du client :")
                            .font(.caption)
                            .bold()
                            .foregroundColor(.secondary)

                        TextField("Montant remis en CFA", text: $amountReceivedInput)
                            .keyboardType(.numberPad)
                            .textFieldStyle(RoundedBorderTextFieldStyle())

                        let received = Double(amountReceivedInput) ?? 0
                        let change = max(0, received - totalAmount)

                        HStack {
                            Text("Monnaie à rendre :")
                                .font(.headline)
                            Spacer()
                            Text("\(Int(change)) CFA")
                                .font(.headline)
                                .foregroundColor(.green)
                                .bold()
                        }
                        .padding(.top, 4)
                    }
                    .padding(.horizontal, 16)
                }

                Spacer()

                Button(action: {
                    let amt = Double(amountReceivedInput) ?? totalAmount
                    onConfirmPayment(selectedMethod, amt)
                }) {
                    Text("Valider & Clôturer la Vente")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(Color.green)
                        .cornerRadius(12)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
            }
            .navigationTitle("Paiement & Encaissement")
            .navigationBarItems(leading: Button("Annuler") { presentationMode.wrappedValue.dismiss() })
        }
    }
}

public struct ReceiptSheetView: View {
    public var ticket: Ticket
    @ObservedObject var dataStore: DataStore
    @Environment(\.presentationMode) var presentationMode

    public var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 64))
                    .foregroundColor(.green)
                    .padding(.top, 20)

                Text("Vente Enregistrée !")
                    .font(.title2)
                    .bold()

                Text("Ticket N° \(ticket.ticketNumber) • \(Int(ticket.totalAmount)) CFA")
                    .font(.headline)
                    .foregroundColor(.secondary)

                VStack(spacing: 12) {
                    Button(action: {
                        BluetoothPrinterManager.shared.printTicket(ticket: ticket, customerName: nil, settings: dataStore.settings) { _ in }
                    }) {
                        HStack {
                            Image(systemName: "printer.fill")
                            Text("Imprimer le Ticket de Caisse")
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                    }

                    Button(action: {
                        if let url = PdfExportManager.exportSalesPdf(tickets: [ticket], settings: dataStore.settings) {
                            PdfExportManager.shareFile(url: url)
                        }
                    }) {
                        HStack {
                            Image(systemName: "square.and.arrow.up")
                            Text("Partager le Reçu (WhatsApp / PDF)")
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.green)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                    }
                }
                .padding(.horizontal, 20)

                Spacer()
            }
            .navigationTitle("Reçu de Vente")
            .navigationBarItems(trailing: Button("Terminer") { presentationMode.wrappedValue.dismiss() })
        }
    }
}

public struct HoldNoteDialogView: View {
    public var onConfirm: (String) -> Void
    @Environment(\.presentationMode) var presentationMode
    @State private var noteInput: String = ""

    public var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("Indiquez le nom du client ou le numéro de table pour retrouver facilement cette commande plus tard.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .padding(.horizontal)
                    .padding(.top, 20)

                TextField("Ex: Table 4, M. Diallo, Client Robe Bleue...", text: $noteInput)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .padding(.horizontal)

                Spacer()

                Button(action: {
                    onConfirm(noteInput)
                    presentationMode.wrappedValue.dismiss()
                }) {
                    Text("Mettre en Attente")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.orange)
                        .cornerRadius(12)
                }
                .padding(.horizontal)
                .padding(.bottom)
            }
            .navigationTitle("Mise en Attente")
            .navigationBarItems(leading: Button("Annuler") { presentationMode.wrappedValue.dismiss() })
        }
    }
}
