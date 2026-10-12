package com.sypos.mobile.data.remote

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.ShopSettingsManager
import com.sypos.mobile.data.local.SyposDatabase
import com.sypos.mobile.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncManager @Inject constructor(
    private val database: SyposDatabase,
    private val shopSettingsManager: ShopSettingsManager
) {
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val activeListeners = mutableListOf<ListenerRegistration>()
    @Volatile
    private var currentListeningShopId: String? = null

    fun stopListening() {
        synchronized(activeListeners) {
            for (listener in activeListeners) {
                try {
                    listener.remove()
                } catch (e: Exception) {
                    Log.w("CloudSyncManager", "Error removing listener", e)
                }
            }
            activeListeners.clear()
            currentListeningShopId = null
        }
    }

    fun startListening(shopId: String) {
        val safeShopId = shopId.trim()
        if (safeShopId.isBlank() || safeShopId == "UNKNOWN") {
            Log.w("CloudSyncManager", "startListening called with invalid shopId: '$shopId'")
            return
        }

        synchronized(activeListeners) {
            if (currentListeningShopId == safeShopId && activeListeners.isNotEmpty()) {
                Log.d("CloudSyncManager", "Already listening to shop: $safeShopId")
                return
            }
            stopListening()
            currentListeningShopId = safeShopId
        }

        Log.d("CloudSyncManager", "Starting complete cloud sync for shop: $safeShopId")
        val shopRef = db.collection("shops").document(safeShopId)

        // 1. Écoute des Catégories
        val catListener = shopRef.collection("categories")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("CloudSyncManager", "Categories listen failed: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                scope.launch {
                    for (change in snapshots.documentChanges) {
                        try {
                            val cat = change.document.toObject(CategoryEntity::class.java)
                            if (change.type == DocumentChange.Type.ADDED || change.type == DocumentChange.Type.MODIFIED) {
                                database.categoryDao().insertCategory(cat)
                            } else if (change.type == DocumentChange.Type.REMOVED) {
                                database.categoryDao().deleteCategory(cat)
                            }
                        } catch (ex: Exception) {
                            Log.e("CloudSyncManager", "Error processing category change", ex)
                        }
                    }
                }
            }

        // 2. Écoute des Produits
        val prodListener = shopRef.collection("products")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("CloudSyncManager", "Products listen failed: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                scope.launch {
                    for (change in snapshots.documentChanges) {
                        try {
                            val product = change.document.toObject(ProductEntity::class.java)
                            if (change.type == DocumentChange.Type.ADDED || change.type == DocumentChange.Type.MODIFIED) {
                                database.productDao().insertProduct(product)
                            } else if (change.type == DocumentChange.Type.REMOVED) {
                                database.productDao().deleteProduct(product)
                            }
                        } catch (ex: Exception) {
                            Log.e("CloudSyncManager", "Error processing product change", ex)
                        }
                    }
                }
            }

        // 3. Écoute des Ventes / Tickets et Articles associés
        val ticketListener = shopRef.collection("tickets")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("CloudSyncManager", "Tickets listen failed: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                scope.launch {
                    for (change in snapshots.documentChanges) {
                        try {
                            val ticket = change.document.toObject(TicketEntity::class.java)
                            if (change.type == DocumentChange.Type.ADDED || change.type == DocumentChange.Type.MODIFIED) {
                                database.ticketDao().insertTicket(ticket)
                                
                                // Charger les articles du ticket (depuis la sous-collection distantes)
                                try {
                                    val itemsSnap = change.document.reference.collection("items").get().await()
                                    if (!itemsSnap.isEmpty) {
                                        val items = itemsSnap.documents.mapNotNull { it.toObject(TicketItemEntity::class.java) }
                                        if (items.isNotEmpty()) {
                                            database.ticketDao().insertTicketItems(items)
                                        }
                                    }
                                } catch (itemEx: Exception) {
                                    Log.e("CloudSyncManager", "Error fetching items for ticket: ${ticket.id}", itemEx)
                                }
                            } else if (change.type == DocumentChange.Type.REMOVED) {
                                database.ticketDao().deleteTicketItems(ticket.id)
                                database.ticketDao().deleteTicket(ticket.id)
                            }
                        } catch (ex: Exception) {
                            Log.e("CloudSyncManager", "Error processing ticket change", ex)
                        }
                    }
                }
            }

        // 4. Écoute des Clients (CRM)
        val custListener = shopRef.collection("customers")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("CloudSyncManager", "Customers listen failed: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                scope.launch {
                    for (change in snapshots.documentChanges) {
                        try {
                            val customer = change.document.toObject(CustomerEntity::class.java)
                            if (change.type == DocumentChange.Type.ADDED || change.type == DocumentChange.Type.MODIFIED) {
                                database.customerDao().insertCustomer(customer)
                            } else if (change.type == DocumentChange.Type.REMOVED) {
                                database.customerDao().deleteCustomer(customer)
                            }
                        } catch (ex: Exception) {
                            Log.e("CloudSyncManager", "Error processing customer change", ex)
                        }
                    }
                }
            }

        // 5. Écoute des Dépenses
        val expListener = shopRef.collection("expenses")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("CloudSyncManager", "Expenses listen failed: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                scope.launch {
                    for (change in snapshots.documentChanges) {
                        try {
                            val exp = change.document.toObject(ExpenseEntity::class.java)
                            if (change.type == DocumentChange.Type.ADDED || change.type == DocumentChange.Type.MODIFIED) {
                                database.expenseDao().insertExpense(exp)
                            } else if (change.type == DocumentChange.Type.REMOVED) {
                                database.expenseDao().deleteExpense(exp)
                            }
                        } catch (ex: Exception) {
                            Log.e("CloudSyncManager", "Error processing expense change", ex)
                        }
                    }
                }
            }

        // 6. Écoute des Codes Promo
        val promoListener = shopRef.collection("promo_codes")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("CloudSyncManager", "Promo codes listen failed: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                scope.launch {
                    for (change in snapshots.documentChanges) {
                        try {
                            val promo = change.document.toObject(PromoCodeEntity::class.java)
                            if (change.type == DocumentChange.Type.ADDED || change.type == DocumentChange.Type.MODIFIED) {
                                database.promoCodeDao().insertPromoCode(promo)
                            } else if (change.type == DocumentChange.Type.REMOVED) {
                                database.promoCodeDao().deletePromoCode(promo)
                            }
                        } catch (ex: Exception) {
                            Log.e("CloudSyncManager", "Error processing promo code change", ex)
                        }
                    }
                }
            }

        // 7. Écoute des Paramètres Boutique distants
        val settingsListener = shopRef.collection("settings").document("config")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("CloudSyncManager", "Settings listen failed: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val cloudSettings = snapshot.toObject(ShopSettings::class.java)
                        if (cloudSettings != null) {
                            val current = shopSettingsManager.settings.value
                            // Si les paramètres cloud sont plus récents ou complètent les nôtres
                            if (cloudSettings.lastKnownTimestamp > current.lastKnownTimestamp || current.shopName == "KAYO COMMERCE") {
                                shopSettingsManager.updateSettingsLocally(
                                    cloudSettings.copy(
                                        currentUserRole = current.currentUserRole,
                                        bluetoothPrinterAddress = current.bluetoothPrinterAddress ?: cloudSettings.bluetoothPrinterAddress,
                                        bluetoothPrinterName = current.bluetoothPrinterName ?: cloudSettings.bluetoothPrinterName,
                                        currentShopId = safeShopId
                                    )
                                )
                            }
                        }
                    } catch (ex: Exception) {
                        Log.e("CloudSyncManager", "Error processing settings change", ex)
                    }
                }
            }

        synchronized(activeListeners) {
            activeListeners.add(catListener)
            activeListeners.add(prodListener)
            activeListeners.add(ticketListener)
            activeListeners.add(custListener)
            activeListeners.add(expListener)
            activeListeners.add(promoListener)
            activeListeners.add(settingsListener)
        }
    }

    /**
     * Enregistre une vente de façon atomique dans le Cloud :
     * 1. Écrit le document Ticket
     * 2. Écrit chaque ligne TicketItem dans la sous-collection `items`
     * 3. Décrémente le stock produit atomiquement avec FieldValue.increment(-quantité)
     *    SANS altérer ni écraser le prix unitaire ou d'autres attributs produit.
     */
    fun pushSale(shopId: String, ticket: TicketEntity, items: List<TicketItemEntity>, isNewSale: Boolean = true) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        val shopRef = db.collection("shops").document(safeShopId)

        db.runBatch { batch ->
            // 1. Écriture du Ticket
            val ticketRef = shopRef.collection("tickets").document(ticket.id)
            batch.set(ticketRef, ticket)

            // 2. Écriture des Articles
            for (item in items) {
                val itemRef = ticketRef.collection("items").document(item.id)
                batch.set(itemRef, item)
            }

            // 3. Décrémentation atomique du stock
            if (isNewSale) {
                for (item in items) {
                    val productRef = shopRef.collection("products").document(item.productId)
                    batch.set(
                        productRef,
                        hashMapOf("stockQuantity" to FieldValue.increment(-item.quantity.toLong())),
                        SetOptions.merge()
                    )
                }
            }
        }.addOnSuccessListener {
            Log.d("CloudSyncManager", "Sale pushed successfully to cloud (shop: $safeShopId)")
        }.addOnFailureListener { e ->
            Log.e("CloudSyncManager", "Error pushing sale to cloud", e)
        }
    }

    /**
     * Annule une vente et restaure atomiquement le stock dans le Cloud.
     */
    fun pushTicketCancellation(shopId: String, ticket: TicketEntity, items: List<TicketItemEntity>) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        val shopRef = db.collection("shops").document(safeShopId)

        db.runBatch { batch ->
            val ticketRef = shopRef.collection("tickets").document(ticket.id)
            batch.set(ticketRef, ticket)

            for (item in items) {
                val productRef = shopRef.collection("products").document(item.productId)
                batch.set(
                    productRef,
                    hashMapOf("stockQuantity" to FieldValue.increment(item.quantity.toLong())),
                    SetOptions.merge()
                )
            }
        }.addOnFailureListener { e ->
            Log.e("CloudSyncManager", "Error pushing ticket cancellation", e)
        }
    }

    fun pushProduct(shopId: String, product: ProductEntity) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        db.collection("shops").document(safeShopId).collection("products")
            .document(product.id).set(product)
    }

    fun deleteProduct(shopId: String, productId: String) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        db.collection("shops").document(safeShopId).collection("products")
            .document(productId).delete()
    }

    fun pushCategory(shopId: String, category: CategoryEntity) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        db.collection("shops").document(safeShopId).collection("categories")
            .document(category.id).set(category)
    }

    fun deleteCategory(shopId: String, categoryId: String) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        db.collection("shops").document(safeShopId).collection("categories")
            .document(categoryId).delete()
    }

    fun pushCustomer(shopId: String, customer: CustomerEntity) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        db.collection("shops").document(safeShopId).collection("customers")
            .document(customer.id).set(customer)
    }

    fun deleteCustomer(shopId: String, customerId: String) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        db.collection("shops").document(safeShopId).collection("customers")
            .document(customerId).delete()
    }

    fun pushCustomerPurchaseAndDebt(shopId: String, customerId: String, totalSpent: Double, debtAmount: Double) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        val customerRef = db.collection("shops").document(safeShopId).collection("customers").document(customerId)

        val updates = hashMapOf<String, Any>(
            "visitCount" to FieldValue.increment(1),
            "totalSpent" to FieldValue.increment(totalSpent),
            "lastPurchaseDate" to System.currentTimeMillis()
        )
        if (debtAmount != 0.0) {
            updates["debt"] = FieldValue.increment(debtAmount)
        }

        customerRef.set(updates, SetOptions.merge())
            .addOnFailureListener { e -> Log.e("CloudSyncManager", "Error updating customer stats", e) }
    }

    fun pushExpense(shopId: String, expense: ExpenseEntity) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        db.collection("shops").document(safeShopId).collection("expenses")
            .document(expense.id).set(expense)
    }

    fun deleteExpense(shopId: String, expenseId: String) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        db.collection("shops").document(safeShopId).collection("expenses")
            .document(expenseId).delete()
    }

    fun pushPromoCode(shopId: String, promoCode: PromoCodeEntity) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        db.collection("shops").document(safeShopId).collection("promo_codes")
            .document(promoCode.id).set(promoCode)
    }

    fun deletePromoCode(shopId: String, promoCodeId: String) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        db.collection("shops").document(safeShopId).collection("promo_codes")
            .document(promoCodeId).delete()
    }

    fun pushSettings(shopId: String, settings: ShopSettings) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) return
        db.collection("shops").document(safeShopId).collection("settings")
            .document("config").set(settings, SetOptions.merge())
    }

    /**
     * Synchronise immédiatement toutes les données locales vers le Cloud sans écraser les données distantes.
     */
    fun forceSyncAllLocalDataToCloud(shopId: String, onComplete: (Boolean) -> Unit) {
        val safeShopId = shopId.ifBlank { shopSettingsManager.getEffectiveShopId() }
        if (safeShopId.isBlank()) {
            onComplete(false)
            return
        }
        val shopRef = db.collection("shops").document(safeShopId)

        scope.launch {
            try {
                // 1. Catégories
                val categories = database.categoryDao().getAllCategories().first()
                for (chunk in categories.chunked(400)) {
                    db.runBatch { batch ->
                        chunk.forEach { cat ->
                            batch.set(shopRef.collection("categories").document(cat.id), cat, SetOptions.merge())
                        }
                    }.await()
                }

                // 2. Produits
                val products = database.productDao().getAllProducts().first()
                for (chunk in products.chunked(400)) {
                    db.runBatch { batch ->
                        chunk.forEach { product ->
                            batch.set(shopRef.collection("products").document(product.id), product, SetOptions.merge())
                        }
                    }.await()
                }

                // 3. Clients
                val customers = database.customerDao().getAllCustomers().first()
                for (chunk in customers.chunked(400)) {
                    db.runBatch { batch ->
                        chunk.forEach { customer ->
                            batch.set(shopRef.collection("customers").document(customer.id), customer, SetOptions.merge())
                        }
                    }.await()
                }

                // 4. Ventes et Articles
                val tickets = database.ticketDao().getAllTickets().first()
                for (chunk in tickets.chunked(100)) {
                    db.runBatch { batch ->
                        chunk.forEach { ticket ->
                            batch.set(shopRef.collection("tickets").document(ticket.id), ticket, SetOptions.merge())
                        }
                    }.await()

                    for (ticket in chunk) {
                        val items = database.ticketDao().getItemsForTicketSync(ticket.id)
                        if (items.isNotEmpty()) {
                            db.runBatch { batch ->
                                items.forEach { item ->
                                    batch.set(
                                        shopRef.collection("tickets").document(ticket.id).collection("items").document(item.id),
                                        item,
                                        SetOptions.merge()
                                    )
                                }
                            }.await()
                        }
                    }
                }

                // 5. Dépenses
                val expenses = database.expenseDao().getAllExpenses().first()
                for (chunk in expenses.chunked(400)) {
                    db.runBatch { batch ->
                        chunk.forEach { exp ->
                            batch.set(shopRef.collection("expenses").document(exp.id), exp, SetOptions.merge())
                        }
                    }.await()
                }

                // 6. Codes Promo
                val promos = database.promoCodeDao().getAllPromoCodes().first()
                for (chunk in promos.chunked(400)) {
                    db.runBatch { batch ->
                        chunk.forEach { promo ->
                            batch.set(shopRef.collection("promo_codes").document(promo.id), promo, SetOptions.merge())
                        }
                    }.await()
                }

                // 7. Paramètres
                pushSettings(safeShopId, shopSettingsManager.settings.value)

                withContext(Dispatchers.Main) {
                    onComplete(true)
                }
            } catch (e: Exception) {
                Log.e("CloudSyncManager", "Error in forceSyncAllLocalDataToCloud", e)
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }
}
