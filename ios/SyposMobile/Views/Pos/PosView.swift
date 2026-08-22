import SwiftUI

public struct PosView: View {
    @ObservedObject var dataStore: DataStore
    @StateObject private var viewModel = PosViewModel()

    @State private var showCartSheet = false
    @State private var showPaymentSheet = false
    @State private var showScannerSheet = false
    @State private var showHoldNoteDialog = false
    @State private var showClearCartPinDialog = false
    @State private var itemToRemoveWithPin: String? = nil
    @State private var lastProcessedTicket: Ticket? = nil
    @State private var showReceiptSheet = false

    public var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Category Filter Tabs
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        CategoryChip(
                            title: "Tous les articles",
                            isSelected: viewModel.selectedCategoryId == nil,
                            action: { viewModel.selectedCategoryId = nil }
                        )

                        ForEach(dataStore.categories) { cat in
                            CategoryChip(
                                title: cat.name,
                                isSelected: viewModel.selectedCategoryId == cat.id,
                                action: { viewModel.selectedCategoryId = cat.id }
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                }
                .background(Color(.systemBackground))

                // Product Grid
                let filteredProducts = dataStore.products.filter { prod in
                    let matchesCategory = viewModel.selectedCategoryId == nil || prod.categoryId == viewModel.selectedCategoryId
                    let matchesSearch = viewModel.searchQuery.isEmpty || prod.name.localizedCaseInsensitiveContains(viewModel.searchQuery) || (prod.barcode?.contains(viewModel.searchQuery) ?? false)
                    return matchesCategory && matchesSearch
                }

                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        ForEach(filteredProducts) { product in
                            ProductGridCard(product: product) {
                                viewModel.addToCart(product: product)
                            }
                        }
                    }
                    .padding(16)
                }

                // Bottom Cart Bar
                if !viewModel.cart.isEmpty {
                    VStack(spacing: 0) {
                        Divider()
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(viewModel.totalItemCount) article(s)")
                                    .font(.caption)
                                    .foregroundColor(.secondary)

                                Text("\(Int(viewModel.totalAmount)) CFA")
                                    .font(.title2)
                                    .bold()
                                    .foregroundColor(.primary)
                            }

                            Spacer()

                            Button(action: { showCartSheet = true }) {
                                HStack {
                                    Image(systemName: "cart.fill")
                                    Text("Voir Panier")
                                        .bold()
                                }
                                .padding(.horizontal, 20)
                                .padding(.vertical, 12)
                                .background(Color.blue)
                                .foregroundColor(.white)
                                .cornerRadius(12)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .background(Color(.systemBackground))
                    }
                }
            }
            .navigationTitle("Caisse POS")
            .searchable(text: $viewModel.searchQuery, prompt: "Rechercher un produit...")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showScannerSheet = true }) {
                        Image(systemName: "barcode.viewfinder")
                            .font(.title3)
                    }
                }
            }
            .sheet(isPresented: $showCartSheet) {
                CartSheetView(
                    viewModel: viewModel,
                    dataStore: dataStore,
                    onClearCartRequested: {
                        if dataStore.settings.pinLockEnabled {
                            showClearCartPinDialog = true
                        } else {
                            viewModel.clearCart()
                            showCartSheet = false
                        }
                    },
                    onRemoveItemRequested: { id in
                        if dataStore.settings.pinLockEnabled {
                            itemToRemoveWithPin = id
                        } else {
                            viewModel.removeFromCart(productId: id)
                        }
                    },
                    onHoldRequested: {
                        showCartSheet = false
                        showHoldNoteDialog = true
                    },
                    onCheckoutRequested: {
                        showCartSheet = false
                        showPaymentSheet = true
                    }
                )
            }
            .sheet(isPresented: $showPaymentSheet) {
                PaymentSheetView(
                    totalAmount: viewModel.totalAmount,
                    customerName: viewModel.selectedCustomer?.name,
                    onConfirmPayment: { method, amt in
                        let ticket = viewModel.processPayment(method: method, amountPaid: amt)
                        lastProcessedTicket = ticket
                        showPaymentSheet = false
                        showReceiptSheet = true
                    }
                )
            }
            .sheet(isPresented: $showReceiptSheet) {
                if let ticket = lastProcessedTicket {
                    ReceiptSheetView(ticket: ticket, dataStore: dataStore)
                }
            }
            .sheet(isPresented: $showScannerSheet) {
                CameraBarcodeScannerView { scannedCode in
                    if let found = dataStore.products.first(where: { $0.barcode == scannedCode }) {
                        viewModel.addToCart(product: found)
                    } else {
                        viewModel.searchQuery = scannedCode
                    }
                }
            }
            .sheet(isPresented: $showHoldNoteDialog) {
                HoldNoteDialogView { note in
                    viewModel.holdCart(note: note)
                }
            }
            .sheet(isPresented: $showClearCartPinDialog) {
                PinAuthSheetView(
                    title: "Autorisation Admin pour Vider le Panier",
                    correctPin: dataStore.settings.adminPin,
                    onSuccess: {
                        viewModel.clearCart()
                        showCartSheet = false
                    }
                )
            }
            .sheet(item: Binding(
                get: { itemToRemoveWithPin.map { IdentifiableString(id: $0) } },
                set: { itemToRemoveWithPin = $0?.id }
            )) { wrapper in
                PinAuthSheetView(
                    title: "Autorisation Admin pour Supprimer un Article",
                    correctPin: dataStore.settings.adminPin,
                    onSuccess: {
                        viewModel.removeFromCart(productId: wrapper.id)
                    }
                )
            }
        }
    }
}

public struct IdentifiableString: Identifiable {
    public var id: String
}

struct CategoryChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline)
                .bold()
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(isSelected ? Color.blue : Color(.systemGray6))
                .foregroundColor(isSelected ? .white : .primary)
                .cornerRadius(20)
        }
    }
}

struct ProductGridCard: View {
    let product: Product
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Circle()
                        .fill(product.stockQuantity <= product.alertStock ? Color.orange : Color.green)
                        .frame(width: 8, height: 8)
                    Text("\(product.stockQuantity) en stock")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                    Spacer()
                }

                Text(product.name)
                    .font(.headline)
                    .foregroundColor(.primary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .frame(height: 44, alignment: .topLeading)

                Spacer()

                Text("\(Int(product.salePrice)) CFA")
                    .font(.title3)
                    .bold()
                    .foregroundColor(.blue)
            }
            .padding(12)
            .frame(height: 140)
            .background(Color(.systemBackground))
            .cornerRadius(14)
            .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)
        }
    }
}
