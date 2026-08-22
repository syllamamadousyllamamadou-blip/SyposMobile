import SwiftUI

public struct SettingsView: View {
    @ObservedObject var dataStore: DataStore
    @ObservedObject private var printerManager = BluetoothPrinterManager.shared

    @State private var showAddPromoSheet = false
    @State private var newPromoCode = ""
    @State private var newPromoDiscount = "10"
    @State private var newPromoMaxUsage = "50"

    public var body: some View {
        NavigationView {
            Form {
                // Section 1: Business Mode
                Section(header: Text("Type d'Activité")) {
                    Picker("Mode Métier", selection: $dataStore.settings.businessMode) {
                        ForEach(BusinessMode.allCases, id: \.self) { mode in
                            Text(mode.displayName).tag(mode)
                        }
                    }
                    .pickerStyle(SegmentedPickerStyle())

                    Text(dataStore.settings.businessMode == .supermarket ?
                         "• Mode Supermarché : Encaissement rapide sans options de table." :
                         "• Mode Restaurant : Choix Sur place / À emporter / Livraison mentionné sur les tickets.")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                }

                // Section 2: Shop Profile & Seller
                Section(header: Text("En-tête des Reçus & Équipe")) {
                    TextField("Nom de la Boutique", text: $dataStore.settings.shopName)
                    TextField("Nom du Vendeur / Caissier", text: $dataStore.settings.sellerName)
                    TextField("Adresse / Emplacement", text: $dataStore.settings.shopAddress)
                    TextField("Numéro Téléphone", text: $dataStore.settings.shopPhone)
                        .keyboardType(.phonePad)
                    TextField("Pied de page (Message)", text: $dataStore.settings.receiptFooter)
                }

                // Section 3: SYPOS Publisher Signature
                Section(header: Text("Signature Éditeur")) {
                    Toggle("Signature SYPOS (Bas de ticket)", isOn: $dataStore.settings.showPublisherSignature)
                    if dataStore.settings.showPublisherSignature {
                        Text("Solution: SYPOS MOBILE 0758245530 (Petit texte)")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }

                // Section 4: Promo Codes
                Section(header: HStack {
                    Text("Codes Promo")
                    Spacer()
                    Button("Ajouter") { showAddPromoSheet = true }
                        .font(.caption)
                }) {
                    if dataStore.promoCodes.isEmpty {
                        Text("Aucun code promo créé.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(dataStore.promoCodes) { promo in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(promo.code) (-\(Int(promo.discountPercent))%)")
                                        .font(.subheadline)
                                        .bold()
                                    Text("Utilisé \(promo.currentUsage)/\(promo.maxUsage) fois")
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                Toggle("", isOn: Binding(
                                    get: { promo.isActive },
                                    set: { _ in dataStore.togglePromoCode(promo) }
                                ))
                                .labelsHidden()

                                Button(action: { dataStore.deletePromoCode(promo) }) {
                                    Image(systemName: "trash")
                                        .foregroundColor(.red)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }

                // Section 5: VAT
                Section(header: Text("Taxe sur la Valeur Ajoutée (TVA)")) {
                    Toggle("Activer la TVA", isOn: $dataStore.settings.taxEnabled)
                    if dataStore.settings.taxEnabled {
                        HStack {
                            Text("Taux de TVA (%)")
                            Spacer()
                            TextField("18", text: Binding(
                                get: { "\(Int(dataStore.settings.taxRatePercent))" },
                                set: { dataStore.settings.taxRatePercent = Double($0) ?? 18.0 }
                            ))
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                        }
                    }
                }

                // Section 6: Thermal Bluetooth Printer
                Section(header: HStack {
                    Text("Imprimante Ticket BLE")
                    Spacer()
                    Button("Rechercher") { printerManager.startScanning() }
                        .font(.caption)
                }) {
                    Toggle("Impression automatique", isOn: $dataStore.settings.autoPrintReceipt)

                    if printerManager.discoveredPrinters.isEmpty {
                        Text("Recherche d'imprimantes Bluetooth...")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(printerManager.discoveredPrinters) { device in
                            HStack {
                                Image(systemName: "printer.fill")
                                Text(device.name)
                                    .font(.subheadline)
                                Spacer()
                                if printerManager.connectedPrinter?.identifier == device.peripheral.identifier && printerManager.isConnected {
                                    Text("Connecté")
                                        .font(.caption)
                                        .foregroundColor(.green)
                                } else {
                                    Button("Connecter") {
                                        printerManager.connect(to: device)
                                    }
                                    .buttonStyle(.bordered)
                                }
                            }
                        }
                    }
                }

                // Section 7: PIN Security
                Section(header: Text("Sécurité & Rôles")) {
                    Toggle("Verrouillage par Code PIN", isOn: $dataStore.settings.pinLockEnabled)
                    if dataStore.settings.pinLockEnabled {
                        HStack {
                            Text("PIN Admin")
                            Spacer()
                            SecureField("1234", text: $dataStore.settings.adminPin)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                        }
                        HStack {
                            Text("PIN Caissier")
                            Spacer()
                            SecureField("0000", text: $dataStore.settings.cashierPin)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                        }
                    }
                }
            }
            .navigationTitle("Paramètres")
            .sheet(isPresented: $showAddPromoSheet) {
                NavigationView {
                    Form {
                        TextField("Code (ex: SOLDES20)", text: $newPromoCode)
                            .autocapitalization(.allCharacters)
                        TextField("Réduction (%)", text: $newPromoDiscount)
                            .keyboardType(.numberPad)
                        TextField("Nombre d'utilisations max", text: $newPromoMaxUsage)
                            .keyboardType(.numberPad)
                    }
                    .navigationTitle("Nouveau Code Promo")
                    .navigationBarItems(
                        leading: Button("Annuler") { showAddPromoSheet = false },
                        trailing: Button("Créer") {
                            if !newPromoCode.isEmpty {
                                let disc = Double(newPromoDiscount) ?? 10.0
                                let maxU = Int(newPromoMaxUsage) ?? 50
                                let promo = PromoCode(code: newPromoCode, discountPercent: disc, maxUsage: maxU)
                                dataStore.addPromoCode(promo)
                                newPromoCode = ""
                                showAddPromoSheet = false
                            }
                        }.disabled(newPromoCode.isEmpty)
                    )
                }
            }
        }
    }
}
