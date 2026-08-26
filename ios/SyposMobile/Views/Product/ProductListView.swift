import SwiftUI

public enum ActiveProductSheet: Identifiable {
    case addProduct
    case editProduct(Product)
    case adjustStock(Product)
    case categoryManager
    case scanner
    case deletePin(Product)

    public var id: String {
        switch self {
        case .addProduct: return "addProduct"
        case .editProduct(let p): return "edit_\(p.id)"
        case .adjustStock(let p): return "adjust_\(p.id)"
        case .categoryManager: return "categoryManager"
        case .scanner: return "scanner"
        case .deletePin(let p): return "deletePin_\(p.id)"
        }
    }
}

public struct ProductListView: View {
    @ObservedObject var dataStore: DataStore
    @StateObject private var viewModel = ProductViewModel()

    @State private var activeSheet: ActiveProductSheet? = nil
    @State private var productToDeleteDirectly: Product? = nil
    @State private var showDeleteConfirmation = false

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Stock Financial Valuation Banner
                let metrics = viewModel.stockMetrics(products: dataStore.products)
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("VALEUR ACHAT")
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

                // Category Filter Chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        CategoryChip(
                            title: "Tous (\(dataStore.products.count))",
                            isSelected: viewModel.selectedCategoryId == nil,
                            action: { viewModel.selectedCategoryId = nil }
                        )

                        ForEach(dataStore.categories) { cat in
                            let count = dataStore.products.filter { $0.categoryId == cat.id }.count
                            CategoryChip(
                                title: "\(cat.name) (\(count))",
                                isSelected: viewModel.selectedCategoryId == cat.id,
                                action: { viewModel.selectedCategoryId = cat.id }
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }

                // List of Products
                let filtered = viewModel.filteredProducts(products: dataStore.products)
                if filtered.isEmpty {
                    VStack(spacing: 12) {
                        Spacer()
                        Image(systemName: "cart.badge.plus")
                            .font(.system(size: 48))
                            .foregroundColor(.secondary)
                        Text(dataStore.products.isEmpty ? "Votre catalogue est vide" : "Aucun produit correspondant")
                            .font(.headline)
                            .foregroundColor(.secondary)
                        Button("Ajouter un premier produit") {
                            activeSheet = .addProduct
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

                                // Stock Indicator & Quick Adjust Button
                                Button(action: { activeSheet = .adjustStock(product) }) {
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
                                activeSheet = .editProduct(product)
                            }
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                Button(role: .destructive) {
                                    if dataStore.settings.pinLockEnabled {
                                        activeSheet = .deletePin(product)
                                    } else {
                                        productToDeleteDirectly = product
                                        showDeleteConfirmation = true
                                    }
                                } label: {
                                    Label("Supprimer", systemImage: "trash")
                                }

                                Button {
                                    activeSheet = .editProduct(product)
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
            .navigationTitle("Catalogue & Stock")
            .searchable(text: $viewModel.searchQuery, prompt: "Rechercher par nom ou code-barres...")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 12) {
                        // Export CSV
                        Button(action: {
                            if let url = PdfExportManager.exportProductsCsv(products: dataStore.products, categories: dataStore.categories) {
                                PdfExportManager.shareFile(url: url)
                            }
                        }) {
                            Image(systemName: "arrow.down.doc.fill")
                        }

                        // Manage Categories
                        Button(action: { activeSheet = .categoryManager }) {
                            Image(systemName: "tag.fill")
                        }

                        // Barcode Scanner
                        Button(action: { activeSheet = .scanner }) {
                            Image(systemName: "barcode.viewfinder")
                        }

                        // Add Product
                        Button(action: { activeSheet = .addProduct }) {
                            Image(systemName: "plus.circle.fill")
                                .font(.title3)
                        }
                    }
                }
            }
            .confirmationDialog("Supprimer ce produit ?", isPresented: $showDeleteConfirmation, titleVisibility: .visible) {
                Button("Supprimer définitivement", role: .destructive) {
                    if let p = productToDeleteDirectly {
                        dataStore.deleteProduct(p)
                        productToDeleteDirectly = nil
                    }
                }
                Button("Annuler", role: .cancel) {
                    productToDeleteDirectly = nil
                }
            } message: {
                Text("Voulez-vous vraiment supprimer \"\(productToDeleteDirectly?.name ?? "")\" du catalogue ?")
            }
            .sheet(item: $activeSheet) { sheetType in
                switch sheetType {
                case .addProduct:
                    AddEditProductView(dataStore: dataStore)

                case .editProduct(let product):
                    AddEditProductView(dataStore: dataStore, existingProduct: product)

                case .adjustStock(let product):
                    StockAdjustDialogView(product: product) { delta in
                        dataStore.updateStock(productId: product.id, delta: delta)
                    }

                case .categoryManager:
                    CategoryManagementSheetView(dataStore: dataStore)

                case .scanner:
                    CameraBarcodeScannerView { code in
                        viewModel.searchQuery = code
                    }

                case .deletePin(let product):
                    PinAuthSheetView(
                        title: "Autorisation Admin pour Supprimer un Produit",
                        correctPin: dataStore.settings.adminPin,
                        onSuccess: {
                            activeSheet = nil
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                                dataStore.deleteProduct(product)
                            }
                        }
                    )
                }
            }
        }
    }
}

public struct CategoryManagementSheetView: View {
    @ObservedObject var dataStore: DataStore
    @Environment(\.presentationMode) var presentationMode

    @State private var showAddCategory = false
    @State private var newCategoryName = ""
    @State private var selectedColorHex = "#3B82F6"

    private let availableColors = [
        "#3B82F6", "#10B981", "#F59E0B", "#EF4444",
        "#8B5CF6", "#EC4899", "#14B8A6", "#6366F1", "#64748B"
    ]

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                List {
                    ForEach(dataStore.categories) { cat in
                        HStack {
                            Circle()
                                .fill(Color(hex: cat.colorHex ?? "#3B82F6"))
                                .frame(width: 16, height: 16)

                            Text(cat.name)
                                .font(.headline)

                            Spacer()

                            let count = dataStore.products.filter { $0.categoryId == cat.id }.count
                            Text("\(count) article(s)")
                                .font(.caption)
                                .foregroundColor(.secondary)

                            Button(action: {
                                dataStore.deleteCategory(cat)
                            }) {
                                Image(systemName: "trash")
                                    .foregroundColor(.red)
                                    .padding(.leading, 8)
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.vertical, 4)
                    }
                }
                .listStyle(PlainListStyle())
            }
            .navigationTitle("Gestion des Catégories")
            .navigationBarItems(
                leading: Button("Fermer") { presentationMode.wrappedValue.dismiss() },
                trailing: Button(action: { showAddCategory = true }) {
                    Image(systemName: "plus.circle.fill")
                        .font(.title3)
                }
            )
            .sheet(isPresented: $showAddCategory) {
                NavigationView {
                    Form {
                        Section(header: Text("Nom de la Catégorie")) {
                            TextField("Ex: Boissons, Snacks, Cosmétiques...", text: $newCategoryName)
                        }

                        Section(header: Text("Couleur d'Identification")) {
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 44))], spacing: 12) {
                                ForEach(availableColors, id: \.self) { hex in
                                    ZStack {
                                        Circle()
                                            .fill(Color(hex: hex))
                                            .frame(width: 38, height: 38)

                                        if selectedColorHex == hex {
                                            Image(systemName: "checkmark")
                                                .font(.headline)
                                                .foregroundColor(.white)
                                        }
                                    }
                                    .onTapGesture {
                                        selectedColorHex = hex
                                    }
                                }
                            }
                            .padding(.vertical, 8)
                        }
                    }
                    .navigationTitle("Nouvelle Catégorie")
                    .navigationBarItems(
                        leading: Button("Annuler") { showAddCategory = false },
                        trailing: Button("Ajouter") {
                            let trimmed = newCategoryName.trimmingCharacters(in: .whitespacesAndNewlines)
                            if !trimmed.isEmpty {
                                let cat = ProductCategory(name: trimmed, colorHex: selectedColorHex)
                                dataStore.addOrUpdateCategory(cat)
                                newCategoryName = ""
                                showAddCategory = false
                            }
                        }.disabled(newCategoryName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    )
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

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }

        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue:  Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}
