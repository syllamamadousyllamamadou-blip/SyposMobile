import SwiftUI

public struct ToolsView: View {
    @ObservedObject var dataStore: DataStore
    @State private var selectedTab = 0

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                Picker("Outils", selection: $selectedTab) {
                    Text("Étiquettes").tag(0)
                    Text("Fiches WhatsApp").tag(1)
                    Text("Livraisons").tag(2)
                }
                .pickerStyle(SegmentedPickerStyle())
                .padding(.horizontal, 16)
                .padding(.vertical, 8)

                if selectedTab == 0 {
                    BarcodeLabelTab(dataStore: dataStore)
                } else if selectedTab == 1 {
                    ProductPromoSheetTab(dataStore: dataStore)
                } else {
                    DeliveryNoteTab(dataStore: dataStore)
                }
            }
            .navigationTitle("Outils Pro")
        }
    }
}

enum LabelPresetMode: String, CaseIterable {
    case barcodeOnly = "Code Seul"
    case barcodeWithSerial = "+ N° Série"
    case nameAndPrice = "Rayon (Prix)"
    case full = "Complet"
}

struct BarcodeLabelTab: View {
    @ObservedObject var dataStore: DataStore

    @State private var selectedProduct: Product? = nil
    @State private var customName: String = ""
    @State private var customPrice: String = ""
    @State private var customBarcode: String = ""
    @State private var customSerial: String = ""

    @State private var selectedPreset: LabelPresetMode = .full
    @State private var printShopHeader = true
    @State private var printProductName = true
    @State private var printPrice = true
    @State private var printBarcodeRaster = true
    @State private var printSerialNumber = true

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Preset Selection
                VStack(alignment: .leading, spacing: 8) {
                    Text("Modèle d'Étiquette :")
                        .font(.caption)
                        .bold()
                        .foregroundColor(.secondary)

                    HStack(spacing: 8) {
                        ForEach(LabelPresetMode.allCases, id: \.self) { preset in
                            Button(action: { applyPreset(preset) }) {
                                Text(preset.rawValue)
                                    .font(.caption)
                                    .bold()
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(selectedPreset == preset ? Color.blue : Color(.systemGray6))
                                    .foregroundColor(selectedPreset == preset ? .white : .primary)
                                    .cornerRadius(8)
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)

                // Configuration Form
                VStack(spacing: 12) {
                    Picker("Produit du catalogue", selection: $selectedProduct) {
                        Text("Saisie manuelle ou choisir...").tag(nil as Product?)
                        ForEach(dataStore.products) { prod in
                            Text("\(prod.name) (\(Int(prod.salePrice)) CFA)").tag(prod as Product?)
                        }
                    }
                    .onChange(of: selectedProduct) { prod in
                        if let p = prod {
                            customName = p.name
                            customPrice = "\(Int(p.salePrice))"
                            customBarcode = p.barcode ?? ""
                            customSerial = p.barcode ?? ""
                        }
                    }

                    TextField("Nom de l'article", text: $customName)
                        .textFieldStyle(RoundedBorderTextFieldStyle())

                    HStack(spacing: 10) {
                        TextField("Prix Vente (CFA)", text: $customPrice)
                            .keyboardType(.numberPad)
                            .textFieldStyle(RoundedBorderTextFieldStyle())

                        TextField("Code-barres", text: $customBarcode)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                    }

                    TextField("N° Série / Réf Texte à imprimer", text: $customSerial)
                        .textFieldStyle(RoundedBorderTextFieldStyle())

                    Divider()

                    // Toggle individual switches
                    Toggle("En-tête Nom Boutique", isOn: $printShopHeader)
                    Toggle("Nom de l'article", isOn: $printProductName)
                    Toggle("Prix de Vente", isOn: $printPrice)
                    Toggle("Code-barres Scannable (Bitmap)", isOn: $printBarcodeRaster)
                    Toggle("Numéro de Série / Référence", isOn: $printSerialNumber)
                }
                .padding()
                .background(Color(.systemBackground))
                .cornerRadius(14)
                .padding(.horizontal, 16)

                // Preview Box
                VStack(spacing: 6) {
                    Text("Aperçu de l'Étiquette Thermique")
                        .font(.caption)
                        .bold()
                        .foregroundColor(.blue)

                    Divider()

                    if printShopHeader && !dataStore.settings.shopName.isEmpty {
                        Text(dataStore.settings.shopName.uppercased())
                            .font(.caption2)
                            .bold()
                    }

                    if printProductName && !customName.isEmpty {
                        Text(customName)
                            .font(.headline)
                            .bold()
                    }

                    if printPrice, let price = Double(customPrice), price > 0 {
                        Text("\(Int(price)) CFA")
                            .font(.title2)
                            .bold()
                            .foregroundColor(.blue)
                    }

                    if printBarcodeRaster && !customBarcode.isEmpty {
                        Text("||||||||||||||||||||||||||||||||||")
                            .font(.headline)
                            .tracking(2)
                    }

                    let serial = customSerial.isEmpty ? customBarcode : customSerial
                    if printSerialNumber && !serial.isEmpty {
                        Text("* \(serial) *")
                            .font(.caption)
                            .bold()
                    }
                }
                .padding()
                .frame(maxWidth: .infinity)
                .background(Color(.systemGray6))
                .cornerRadius(14)
                .padding(.horizontal, 16)

                // Print Button
                Button(action: printLabel) {
                    HStack {
                        Image(systemName: "printer.fill")
                        Text("Imprimer l'Étiquette")
                            .bold()
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(12)
                    .padding(.horizontal, 16)
                }
            }
            .padding(.vertical, 10)
        }
    }

    private func applyPreset(_ preset: LabelPresetMode) {
        selectedPreset = preset
        switch preset {
        case .barcodeOnly:
            printShopHeader = false
            printProductName = false
            printPrice = false
            printBarcodeRaster = true
            printSerialNumber = false
        case .barcodeWithSerial:
            printShopHeader = false
            printProductName = false
            printPrice = false
            printBarcodeRaster = true
            printSerialNumber = true
        case .nameAndPrice:
            printShopHeader = true
            printProductName = true
            printPrice = true
            printBarcodeRaster = false
            printSerialNumber = false
        case .full:
            printShopHeader = true
            printProductName = true
            printPrice = true
            printBarcodeRaster = true
            printSerialNumber = true
        }
    }

    private func printLabel() {
        let price = Double(customPrice) ?? 0.0
        let options = LabelPrintOptions(
            printShopHeader: printShopHeader,
            printProductName: printProductName,
            printPrice: printPrice,
            printBarcodeRaster: printBarcodeRaster,
            printSerialNumber: printSerialNumber,
            serialNumber: customSerial.isEmpty ? nil : customSerial
        )

        BluetoothPrinterManager.shared.printBarcodeLabel(
            productName: customName,
            price: price,
            barcode: customBarcode.isEmpty ? nil : customBarcode,
            settings: dataStore.settings,
            options: options
        ) { _ in }
    }
}

struct ProductPromoSheetTab: View {
    @ObservedObject var dataStore: DataStore
    @State private var title: String = ""
    @State private var normalPrice: String = ""
    @State private var promoPrice: String = ""
    @State private var details: String = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                VStack(spacing: 12) {
                    TextField("Titre du produit / Offre", text: $title)
                        .textFieldStyle(RoundedBorderTextFieldStyle())

                    HStack(spacing: 10) {
                        TextField("Prix Normal (CFA)", text: $normalPrice)
                            .keyboardType(.numberPad)
                            .textFieldStyle(RoundedBorderTextFieldStyle())

                        TextField("Prix Promo (CFA)", text: $promoPrice)
                            .keyboardType(.numberPad)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                    }

                    TextField("Description / Avantages...", text: $details)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                }
                .padding()
                .background(Color(.systemBackground))
                .cornerRadius(14)
                .padding(.horizontal, 16)

                Button(action: sharePromoSheet) {
                    HStack {
                        Image(systemName: "square.and.arrow.up.fill")
                        Text("Partager la Fiche sur WhatsApp")
                            .bold()
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(Color.green)
                    .foregroundColor(.white)
                    .cornerRadius(12)
                    .padding(.horizontal, 16)
                }
            }
            .padding(.vertical, 10)
        }
    }

    private func sharePromoSheet() {
        var text = "🔥 *\(title.uppercased())* 🔥\n"
        text += "🏢 *\(dataStore.settings.shopName)*\n"
        text += "--------------------------------\n"
        if !promoPrice.isEmpty {
            text += "❌ Prix Habituel : ~\(normalPrice) CFA~\n"
            text += "✅ *PRIX PROMO : \(promoPrice) CFA*\n"
        } else if !normalPrice.isEmpty {
            text += "💰 *Prix : \(normalPrice) CFA*\n"
        }
        if !details.isEmpty {
            text += "--------------------------------\n"
            text += "📝 *Détails :*\n\(details)\n"
        }
        text += "--------------------------------\n"
        if !dataStore.settings.shopPhone.isEmpty {
            text += "📞 Commandes au : *\(dataStore.settings.shopPhone)*\n"
        }

        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = windowScene.windows.first?.rootViewController else { return }

        let activityVC = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        rootVC.present(activityVC, animated: true)
    }
}

struct DeliveryNoteTab: View {
    @ObservedObject var dataStore: DataStore
    @State private var recipientName = ""
    @State private var recipientPhone = ""
    @State private var address = ""
    @State private var items = ""
    @State private var amount = ""
    @State private var deliveryFee = "1000"

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                VStack(spacing: 12) {
                    TextField("Nom du destinataire", text: $recipientName)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    TextField("Numéro téléphone", text: $recipientPhone)
                        .keyboardType(.phonePad)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    TextField("Lieu / Adresse de livraison", text: $address)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    TextField("Articles / Contenu du colis", text: $items)
                        .textFieldStyle(RoundedBorderTextFieldStyle())

                    HStack(spacing: 10) {
                        TextField("Montant colis (CFA)", text: $amount)
                            .keyboardType(.numberPad)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                        TextField("Frais livraison", text: $deliveryFee)
                            .keyboardType(.numberPad)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                    }
                }
                .padding()
                .background(Color(.systemBackground))
                .cornerRadius(14)
                .padding(.horizontal, 16)

                Button(action: {
                    // Trigger Delivery note thermal print
                }) {
                    HStack {
                        Image(systemName: "shippingbox.fill")
                        Text("Imprimer Bordereau de Livraison")
                            .bold()
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(12)
                    .padding(.horizontal, 16)
                }
            }
            .padding(.vertical, 10)
        }
    }
}
