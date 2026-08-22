import SwiftUI

public struct ProductListView: View {
    @ObservedObject var dataStore: DataStore
    @StateObject private var viewModel = ProductViewModel()

    @State private var showAddProductSheet = false
    @State private var productToEdit: Product? = nil
    @State private var productToAdjust: Product? = nil
    @State private var productToDeleteWithPin: Product? = nil
    @State private var showScannerSheet = false

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Stock Financial Valuation Banner
                let metrics = viewModel.stockMetrics(products: dataStore.products)
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("VALEUR STOCK (ACHAT)")
                            .font(.caption2)
                            .bold()
                            .foregroundColor(.secondary)
                        Text("\(Int(metrics.totalCost)) CFA")
                            .font(.subheadline)
                            .bold()
                    }
                    Spacer()
                    VStack(alignment: .center, spacing: 2) {
                        Text("VALEUR VENTE")
                            .font(.caption2)
                            .bold()
                            .foregroundColor(.secondary)
                        Text("\(Int(metrics.totalSale)) CFA")
                            .font(.subheadline)
                            .bold()
                            .foregroundColor(.blue)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text("MARGE ESTIMÉE")
                            .font(.caption2)
                            .bold()
                            .foregroundColor(.secondary)
                        Text("+\(Int(metrics.potentialMargin)) CFA")
                            .font(.subheadline)
                            .bold()
                            .foregroundColor(.green)
                    }
                }
                .padding(12)
                .background(Color(.systemGray6))
                .cornerRadius(12)
                .padding(.horizontal, 16)
                .padding(.top, 8)

                // List of Products
                let filtered = viewModel.filteredProducts(products: dataStore.products)
                List {
                    ForEach(filtered) { product in
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(product.name)
                                    .font(.headline)

                                HStack(spacing: 8) {
                                    Text("\(Int(product.salePrice)) CFA")
                                        .font(.subheadline)
                                        .bold()
                                        .foregroundColor(.blue)

                                    if product.costPrice > 0 {
                                        Text("Achat: \(Int(product.costPrice)) CFA")
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }

                                    if let code = product.barcode, !code.isEmpty {
                                        Text("• \(code)")
                                            .font(.caption2)
                                            .foregroundColor(.secondary)
                                    }
                                }
                            }

                            Spacer()

                            // Stock Indicator & Quick Adjust
                            Button(action: { productToAdjust = product }) {
                                HStack(spacing: 4) {
                                    Image(systemName: "cube.box.fill")
                                    Text("\(product.stockQuantity)")
                                        .bold()
                                }
                                .font(.caption)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(product.stockQuantity <= product.alertStock ? Color.orange.opacity(0.15) : Color.green.opacity(0.15))
                                .foregroundColor(product.stockQuantity <= product.alertStock ? .orange : .green)
                                .cornerRadius(8)
                            }
                            .buttonStyle(.plain)
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            productToEdit = product
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(role: .destructive) {
                                if dataStore.settings.pinLockEnabled {
                                    productToDeleteWithPin = product
                                } else {
                                    dataStore.deleteProduct(product)
                                }
                            } label: {
                                Label("Supprimer", systemImage: "trash")
                            }

                            Button {
                                productToEdit = product
                            } label: {
                                Label("Modifier", systemImage: "pencil")
                            }
                            .tint(.blue)
                        }
                    }
                }
                .listStyle(PlainListStyle())
            }
            .navigationTitle("Catalogue & Stock")
            .searchable(text: $viewModel.searchQuery, prompt: "Rechercher par nom ou code-barres...")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack {
                        Button(action: { showScannerSheet = true }) {
                            Image(systemName: "barcode.viewfinder")
                        }
                        Button(action: { showAddProductSheet = true }) {
                            Image(systemName: "plus.circle.fill")
                                .font(.title3)
                        }
                    }
                }
            }
            .sheet(isPresented: $showAddProductSheet) {
                AddEditProductView(dataStore: dataStore)
            }
            .sheet(item: $productToEdit) { product in
                AddEditProductView(dataStore: dataStore, existingProduct: product)
            }
            .sheet(item: $productToAdjust) { product in
                StockAdjustDialogView(product: product) { delta in
                    dataStore.updateStock(productId: product.id, delta: delta)
                }
            }
            .sheet(item: $productToDeleteWithPin) { product in
                PinAuthSheetView(
                    title: "Autorisation Admin pour Supprimer un Produit",
                    correctPin: dataStore.settings.adminPin,
                    onSuccess: {
                        dataStore.deleteProduct(product)
                    }
                )
            }
            .sheet(isPresented: $showScannerSheet) {
                CameraBarcodeScannerView { code in
                    viewModel.searchQuery = code
                }
            }
        }
    }
}

public struct AddEditProductView: View {
    @ObservedObject var dataStore: DataStore
    public var existingProduct: Product? = nil
    @Environment(\.presentationMode) var presentationMode

    @State private var name: String = ""
    @State private var salePrice: String = ""
    @State private var costPrice: String = ""
    @State private var stockQuantity: String = "0"
    @State private var alertStock: String = "5"
    @State private var barcode: String = ""
    @State private var selectedCategoryId: String? = nil
    @State private var showScanner = false

    public var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Informations Principales")) {
                    TextField("Nom de l'article", text: $name)

                    HStack {
                        TextField("Code-barres / Référence", text: $barcode)
                        Button(action: { showScanner = true }) {
                            Image(systemName: "barcode.viewfinder")
                                .foregroundColor(.blue)
                        }
                    }

                    Picker("Catégorie", selection: $selectedCategoryId) {
                        Text("Aucune").tag(nil as String?)
                        ForEach(dataStore.categories) { cat in
                            Text(cat.name).tag(cat.id as String?)
                        }
                    }
                }

                Section(header: Text("Tarification (CFA)")) {
                    TextField("Prix de Vente", text: $salePrice)
                        .keyboardType(.numberPad)
                    TextField("Prix d'Achat (Optionnel)", text: $costPrice)
                        .keyboardType(.numberPad)
                }

                Section(header: Text("Gestion de Stock")) {
                    TextField("Quantité en stock", text: $stockQuantity)
                        .keyboardType(.numberPad)
                    TextField("Seuil d'alerte stock bas", text: $alertStock)
                        .keyboardType(.numberPad)
                }
            }
            .navigationTitle(existingProduct == nil ? "Nouveau Produit" : "Modifier Produit")
            .navigationBarItems(
                leading: Button("Annuler") { presentationMode.wrappedValue.dismiss() },
                trailing: Button("Enregistrer") { save() }.disabled(name.isEmpty || salePrice.isEmpty)
            )
            .onAppear {
                if let p = existingProduct {
                    name = p.name
                    salePrice = "\(Int(p.salePrice))"
                    costPrice = p.costPrice > 0 ? "\(Int(p.costPrice))" : ""
                    stockQuantity = "\(p.stockQuantity)"
                    alertStock = "\(p.alertStock)"
                    barcode = p.barcode ?? ""
                    selectedCategoryId = p.categoryId
                }
            }
            .sheet(isPresented: $showScanner) {
                CameraBarcodeScannerView { code in
                    barcode = code
                }
            }
        }
    }

    private func save() {
        let sale = Double(salePrice) ?? 0.0
        let cost = Double(costPrice) ?? 0.0
        let stock = Int(stockQuantity) ?? 0
        let alert = Int(alertStock) ?? 5

        let product = Product(
            id: existingProduct?.id ?? UUID().uuidString,
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            salePrice: sale,
            costPrice: cost,
            stockQuantity: stock,
            alertStock: alert,
            barcode: barcode.isEmpty ? nil : barcode.trimmingCharacters(in: .whitespacesAndNewlines),
            categoryId: selectedCategoryId
        )

        dataStore.addOrUpdateProduct(product)
        presentationMode.wrappedValue.dismiss()
    }
}

public struct StockAdjustDialogView: View {
    public var product: Product
    public var onConfirm: (Int) -> Void
    @Environment(\.presentationMode) var presentationMode
    @State private var deltaInput: String = "1"
    @State private var isAddition: Bool = true

    public var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("Ajuster le stock pour \"\(product.name)\"")
                    .font(.headline)
                    .padding(.top, 20)

                Text("Stock actuel : \(product.stockQuantity) unités")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                Picker("Opération", selection: $isAddition) {
                    Text("+ Entrée de Stock").tag(true)
                    Text("- Sortie de Stock").tag(false)
                }
                .pickerStyle(SegmentedPickerStyle())
                .padding(.horizontal)

                TextField("Quantité", text: $deltaInput)
                    .keyboardType(.numberPad)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .padding(.horizontal)

                Spacer()

                Button(action: {
                    let qty = Int(deltaInput) ?? 1
                    let delta = isAddition ? qty : -qty
                    onConfirm(delta)
                    presentationMode.wrappedValue.dismiss()
                }) {
                    Text("Valider l'Ajustement")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(isAddition ? Color.green : Color.orange)
                        .cornerRadius(12)
                }
                .padding(.horizontal)
                .padding(.bottom)
            }
            .navigationTitle("Ajustement de Stock")
            .navigationBarItems(leading: Button("Annuler") { presentationMode.wrappedValue.dismiss() })
        }
    }
}
