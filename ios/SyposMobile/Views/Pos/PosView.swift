import SwiftUI

public enum ActivePosSheet: Identifiable {
    case cart
    case payment
    case receipt(Ticket)
    case scanner
    case holdNote
    case heldCartsList
    case editCartItem(CartItem)
    case clearCartPin
    case removeItemPin(String)

    public var id: String {
        switch self {
        case .cart: return "cart"
        case .payment: return "payment"
        case .receipt(let t): return "receipt_\(t.id)"
        case .scanner: return "scanner"
        case .holdNote: return "holdNote"
        case .heldCartsList: return "heldCartsList"
        case .editCartItem(let item): return "edit_\(item.id)_\(item.unitPrice)"
        case .clearCartPin: return "clearCartPin"
        case .removeItemPin(let id): return "remove_\(id)"
        }
    }
}

public struct PosView: View {
    @ObservedObject var dataStore: DataStore
    @StateObject private var viewModel = PosViewModel()

    @State private var activeSheet: ActivePosSheet? = nil

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

                if filteredProducts.isEmpty {
                    VStack(spacing: 12) {
                        Spacer()
                        Image(systemName: "cube.box")
                            .font(.system(size: 48))
                            .foregroundColor(.secondary)
                        Text(dataStore.products.isEmpty ? "Aucun produit dans le catalogue" : "Aucun résultat trouvé")
                            .font(.headline)
                            .foregroundColor(.secondary)
                        if dataStore.products.isEmpty {
                            Text("Ajoutez des produits dans l'onglet Catalogue pour commencer à encaisser.")
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 32)
                        }
                        Spacer()
                    }
                } else {
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

                            Button(action: { activeSheet = .cart }) {
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
                    HStack(spacing: 12) {
                        // Held Carts Button with Badge
                        let heldCount = dataStore.heldTickets.count
                        Button(action: { activeSheet = .heldCartsList }) {
                            ZStack(alignment: .topTrailing) {
                                Image(systemName: "pause.circle.fill")
                                    .font(.title3)
                                    .foregroundColor(heldCount > 0 ? .orange : .secondary)

                                if heldCount > 0 {
                                    Text("\(heldCount)")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(.white)
                                        .padding(4)
                                        .background(Color.red)
                                        .clipShape(Circle())
                                        .offset(x: 8, y: -8)
                                }
                            }
                        }

                        Button(action: { activeSheet = .scanner }) {
                            Image(systemName: "barcode.viewfinder")
                                .font(.title3)
                        }
                    }
                }
            }
            .sheet(item: $activeSheet) { sheetType in
                switch sheetType {
                case .cart:
                    CartSheetView(
                        viewModel: viewModel,
                        dataStore: dataStore,
                        onClearCartRequested: {
                            if dataStore.settings.pinLockEnabled {
                                activeSheet = .clearCartPin
                            } else {
                                viewModel.clearCart()
                                activeSheet = nil
                            }
                        },
                        onRemoveItemRequested: { id in
                            if dataStore.settings.pinLockEnabled {
                                activeSheet = .removeItemPin(id)
                            } else {
                                viewModel.removeFromCart(productId: id)
                            }
                        },
                        onHoldRequested: {
                            activeSheet = .holdNote
                        },
                        onCheckoutRequested: {
                            activeSheet = .payment
                        },
                        onEditItemRequested: { item in
                            activeSheet = .editCartItem(item)
                        }
                    )

                case .payment:
                    PaymentSheetView(
                        totalAmount: viewModel.totalAmount,
                        customerName: viewModel.selectedCustomer?.name,
                        onConfirmPayment: { method, amt in
                            let ticket = viewModel.processPayment(method: method, amountPaid: amt)
                            activeSheet = .receipt(ticket)
                        }
                    )

                case .receipt(let ticket):
                    ReceiptSheetView(ticket: ticket, dataStore: dataStore)

                case .scanner:
                    CameraBarcodeScannerView { scannedCode in
                        if let found = dataStore.products.first(where: { $0.barcode == scannedCode }) {
                            viewModel.addToCart(product: found)
                        } else {
                            viewModel.searchQuery = scannedCode
                        }
                    }

                case .holdNote:
                    HoldNoteDialogView { note in
                        viewModel.holdCart(note: note)
                        activeSheet = nil
                    }

                case .heldCartsList:
                    HeldCartsSheetView(dataStore: dataStore) { ticket in
                        viewModel.resumeHeldCart(ticket: ticket)
                        activeSheet = .cart
                    }

                case .editCartItem(let item):
                    EditCartItemSheetView(item: item) { customPrice, discount in
                        viewModel.updateCartItemPrice(productId: item.product.id, customPrice: customPrice)
                        viewModel.updateCartItemDiscount(productId: item.product.id, discountPercent: discount)
                        activeSheet = .cart
                    }

                case .clearCartPin:
                    PinAuthSheetView(
                        title: "Autorisation Admin pour Vider le Panier",
                        correctPin: dataStore.settings.adminPin,
                        onSuccess: {
                            viewModel.clearCart()
                            activeSheet = nil
                        }
                    )

                case .removeItemPin(let id):
                    PinAuthSheetView(
                        title: "Autorisation Admin pour Supprimer un Article",
                        correctPin: dataStore.settings.adminPin,
                        onSuccess: {
                            viewModel.removeFromCart(productId: id)
                            activeSheet = .cart
                        }
                    )
                }
            }
        }
    }
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
