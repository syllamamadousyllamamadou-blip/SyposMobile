import SwiftUI

public enum ActiveSettingsSheet: Identifiable {
    case addPromo
    case resetPin

    public var id: String {
        switch self {
        case .addPromo: return "addPromo"
        case .resetPin: return "resetPin"
        }
    }
}

public struct SettingsView: View {
    @ObservedObject var dataStore: DataStore
    @ObservedObject private var printerManager = BluetoothPrinterManager.shared

    @State private var activeSheet: ActiveSettingsSheet? = nil
    @State private var newPromoCode = ""
    @State private var newPromoDiscount = "10"
    @State private var newPromoMaxUsage = "50"

    @State private var testPrintMessage: String? = nil
    @State private var isTestingPrinter = false
    @State private var showResetConfirmation = false

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

                // Section 4: Thermal Bluetooth Printer
                Section(header: HStack {
                    Text("Imprimante Thermique Bluetooth")
                    Spacer()
                    Button(printerManager.isScanning ? "Scan en cours..." : "Rechercher") {
                        printerManager.startScanning()
                    }
                    .font(.caption)
                    .disabled(printerManager.isScanning)
                }) {
                    Toggle("Impression automatique des tickets", isOn: $dataStore.settings.autoPrintReceipt)

                    if printerManager.discoveredPrinters.isEmpty {
                        Text(printerManager.isScanning ? "Recherche d'imprimantes Bluetooth à proximité..." : "Aucune imprimante détectée. Cliquez sur 'Rechercher'.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(printerManager.discoveredPrinters) { device in
                            let isConnected = printerManager.connectedPrinter?.identifier == device.peripheral.identifier && printerManager.isConnected
                            HStack {
                                Image(systemName: "printer.fill")
                                    .foregroundColor(isConnected ? .green : .secondary)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(device.name)
                                        .font(.subheadline)
                                        .bold()
                                    if isConnected {
                                        Text("Connectée & Mémorisée par défaut")
                                            .font(.caption2)
                                            .foregroundColor(.green)
                                    }
                                }

                                Spacer()

                                if isConnected {
                                    Button(action: {
                                        printerManager.disconnect()
                                        dataStore.settings.bluetoothPrinterUUID = nil
                                        dataStore.settings.bluetoothPrinterName = nil
                                    }) {
                                        Text("Déconnecter")
                                            .font(.caption)
                                            .foregroundColor(.red)
                                    }
                                    .buttonStyle(.bordered)
                                } else {
                                    Button("Connecter") {
                                        printerManager.connect(to: device)
                                        dataStore.settings.bluetoothPrinterUUID = device.peripheral.identifier.uuidString
                                        dataStore.settings.bluetoothPrinterName = device.name
                                    }
                                    .buttonStyle(.borderedProminent)
                                }
                            }
                        }
                    }

                    if let msg = testPrintMessage {
                        Text(msg)
                            .font(.caption)
                            .bold()
                            .foregroundColor(.blue)
                    }

                    Button(action: testPrinter) {
                        HStack {
                            Image(systemName: "checkmark.seal.fill")
                            Text(isTestingPrinter ? "Envoi du test..." : "Tester l'impression (Ticket Test)")
                        }
                    }
                    .disabled(isTestingPrinter)
                }

                // Section 5: Promo Codes
                Section(header: HStack {
                    Text("Codes Promo")
                    Spacer()
                    Button("Ajouter") { activeSheet = .addPromo }
                        .font(.caption)
                }) {
                    if dataStore.promoCodes.isEmpty {
                        Text("Aucun code promo actif.")
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

                // Section 6: VAT
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

                // Section 8: Database Maintenance
                Section(header: Text("Maintenance de la Caisse")) {
                    Button(action: {
                        dataStore.loadDemoData()
                    }) {
                        HStack {
                            Image(systemName: "tray.and.arrow.down.fill")
                            Text("Charger Données de Démonstration (Test)")
                        }
                    }

                    Button(action: {
                        if dataStore.settings.pinLockEnabled {
                            activeSheet = .resetPin
                        } else {
                            showResetConfirmation = true
                        }
                    }) {
                        HStack {
                            Image(systemName: "trash.fill")
                            Text("Purger & Réinitialiser Toute la Caisse")
                        }
                        .foregroundColor(.red)
                    }
                }
            }
            .navigationTitle("Paramètres")
            .confirmationDialog("Réinitialiser toute la caisse ?", isPresented: $showResetConfirmation, titleVisibility: .visible) {
                Button("Tout effacer définitivement", role: .destructive) {
                    dataStore.resetAllData()
                }
                Button("Annuler", role: .cancel) {}
            } message: {
                Text("Cette action supprimera tous les produits, tickets et dépenses.")
            }
            .sheet(item: $activeSheet) { sheetType in
                switch sheetType {
                case .addPromo:
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
                            leading: Button("Annuler") { activeSheet = nil },
                            trailing: Button("Créer") {
                                if !newPromoCode.isEmpty {
                                    let disc = Double(newPromoDiscount) ?? 10.0
                                    let maxU = Int(newPromoMaxUsage) ?? 50
                                    let promo = PromoCode(code: newPromoCode, discountPercent: disc, maxUsage: maxU)
                                    dataStore.addPromoCode(promo)
                                    newPromoCode = ""
                                    activeSheet = nil
                                }
                            }.disabled(newPromoCode.isEmpty)
                        )
                    }

                case .resetPin:
                    PinAuthSheetView(
                        title: "Autorisation Admin pour Réinitialiser la Caisse",
                        correctPin: dataStore.settings.adminPin,
                        onSuccess: {
                            activeSheet = nil
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                                dataStore.resetAllData()
                            }
                        }
                    )
                }
            }
        }
    }

    private func testPrinter() {
        isTestingPrinter = true
        testPrintMessage = "Envoi du ticket de test..."
        printerManager.testPrinter(settings: dataStore.settings) { result in
            isTestingPrinter = false
            switch result {
            case .success:
                testPrintMessage = "✅ Test d'impression réussi !"
            case .failure(let err):
                testPrintMessage = "❌ Erreur: \(err.localizedDescription)"
            }
        }
    }
}
