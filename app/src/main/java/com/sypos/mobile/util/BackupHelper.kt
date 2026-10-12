package com.sypos.mobile.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.ShopSettingsManager
import com.sypos.mobile.data.local.SyposDatabase
import com.sypos.mobile.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupHelper {

    suspend fun createBackupJson(
        database: SyposDatabase,
        settings: ShopSettings
    ): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "KAYO Mobile")
        root.put("version", "2.3")
        root.put("timestamp", System.currentTimeMillis())

        // 1. Settings
        val setObj = JSONObject().apply {
            put("shopName", settings.shopName)
            put("shopAddress", settings.shopAddress)
            put("shopPhone", settings.shopPhone)
            put("sellerName", settings.sellerName)
            put("receiptFooter", settings.receiptFooter)
            put("taxEnabled", settings.taxEnabled)
            put("taxRatePercent", settings.taxRatePercent)
            put("adminPin", settings.adminPin)

        }
        root.put("settings", setObj)

        // 2. Categories
        val cats = database.categoryDao().getAllCategories().first()
        val catsArr = JSONArray()
        cats.forEach { c ->
            catsArr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("colorHex", c.colorHex)
            })
        }
        root.put("categories", catsArr)

        // 3. Products
        val prods = database.productDao().getAllProducts().first()
        val prodsArr = JSONArray()
        prods.forEach { p ->
            prodsArr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("categoryId", p.categoryId)
                put("costPrice", p.costPrice)
                put("salePrice", p.salePrice)
                put("stockQuantity", p.stockQuantity)
                put("alertStock", p.alertStock)
                put("barcode", p.barcode)
            })
        }
        root.put("products", prodsArr)

        // 4. Customers
        val custs = database.customerDao().getAllCustomers().first()
        val custsArr = JSONArray()
        custs.forEach { c ->
            custsArr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("email", c.email)
                put("notes", c.notes)
                put("totalSpent", c.totalSpent)
                put("visitCount", c.visitCount)
                put("lastPurchaseDate", c.lastPurchaseDate ?: 0L)
                put("createdAt", c.createdAt)
            })
        }
        root.put("customers", custsArr)

        // 5. Expenses
        val exps = database.expenseDao().getAllExpenses().first()
        val expsArr = JSONArray()
        exps.forEach { e ->
            expsArr.put(JSONObject().apply {
                put("id", e.id)
                put("description", e.description)
                put("amount", e.amount)
                put("date", e.date)
                put("category", e.category)
            })
        }
        root.put("expenses", expsArr)

        // 6. Tickets
        val tickets = database.ticketDao().getAllTickets().first()
        val ticketsArr = JSONArray()
        tickets.forEach { t ->
            val items = database.ticketDao().getItemsForTicketSync(t.id)
            val tObj = JSONObject().apply {
                put("id", t.id)
                put("ticketNumber", t.ticketNumber)
                put("date", t.date)
                put("status", t.status.name)
                put("orderType", t.orderType.name)
                put("subTotal", t.subTotal)
                put("taxAmount", t.taxAmount)
                put("totalAmount", t.totalAmount)
                put("discount", t.discount)
                put("paymentMethod", t.paymentMethod?.name)
                put("amountPaid", t.amountPaid)
                put("changeReturned", t.changeReturned)
                put("customerId", t.customerId)
                put("sellerName", t.sellerName)
                put("note", t.note)

                val itemsArr = JSONArray()
                items.forEach { it ->
                    itemsArr.put(JSONObject().apply {
                        put("id", it.id)
                        put("ticketId", it.ticketId)
                        put("productId", it.productId)
                        put("productName", it.productName)
                        put("quantity", it.quantity)
                        put("unitPrice", it.unitPrice)
                        put("total", it.total)
                    })
                }
                put("items", itemsArr)
            }
            ticketsArr.put(tObj)
        }
        root.put("tickets", ticketsArr)

        root.toString(2)
    }

    suspend fun exportAndShareBackup(
        context: Context,
        database: SyposDatabase,
        settings: ShopSettings
    ) = withContext(Dispatchers.IO) {
        val jsonStr = createBackupJson(database, settings)
        val dir = File(context.cacheDir, "backups")
        if (!dir.exists()) dir.mkdirs()

        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "KAYO_Backup_$dateStr.json")
        FileOutputStream(file).use { out ->
            out.write(jsonStr.toByteArray(Charsets.UTF_8))
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Sauvegarde de la base KAYO Mobile (${settings.shopName}) - $dateStr")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Partager / Enregistrer la Sauvegarde"))
    }

    suspend fun restoreBackupFromUri(
        context: Context,
        uri: Uri,
        database: SyposDatabase,
        settingsManager: ShopSettingsManager
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext false
            return@withContext restoreBackupFromJson(content, database, settingsManager)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun restoreBackupFromJson(
        jsonString: String,
        database: SyposDatabase,
        settingsManager: ShopSettingsManager
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)

            // 1. Settings
            if (root.has("settings")) {
                val setObj = root.getJSONObject("settings")
                val cur = settingsManager.settings.value
                val updated = cur.copy(
                    shopName = setObj.optString("shopName", cur.shopName),
                    shopAddress = setObj.optString("shopAddress", cur.shopAddress),
                    shopPhone = setObj.optString("shopPhone", cur.shopPhone),
                    sellerName = setObj.optString("sellerName", cur.sellerName),
                    receiptFooter = setObj.optString("receiptFooter", cur.receiptFooter),
                    taxEnabled = setObj.optBoolean("taxEnabled", cur.taxEnabled),
                    taxRatePercent = setObj.optDouble("taxRatePercent", cur.taxRatePercent),
                    adminPin = setObj.optString("adminPin", cur.adminPin),

                )
                settingsManager.updateSettings(updated)
            }

            // 2. Categories
            if (root.has("categories")) {
                val arr = root.getJSONArray("categories")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    database.categoryDao().insertCategory(
                        CategoryEntity(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            colorHex = obj.optString("colorHex", obj.optString("color", "#0D9488"))
                        )
                    )
                }
            }

            // 3. Products
            if (root.has("products")) {
                val arr = root.getJSONArray("products")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    database.productDao().insertProduct(
                        ProductEntity(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            categoryId = obj.optString("categoryId").takeIf { it.isNotBlank() },
                            costPrice = obj.optDouble("costPrice", 0.0),
                            salePrice = obj.getDouble("salePrice"),
                            stockQuantity = obj.optInt("stockQuantity", 0),
                            alertStock = obj.optInt("alertStock", 5),
                            barcode = obj.optString("barcode").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }

            // 4. Customers
            if (root.has("customers")) {
                val arr = root.getJSONArray("customers")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    database.customerDao().insertCustomer(
                        CustomerEntity(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            phone = obj.optString("phone").takeIf { it.isNotBlank() },
                            email = obj.optString("email").takeIf { it.isNotBlank() },
                            notes = obj.optString("notes").takeIf { it.isNotBlank() },
                            totalSpent = obj.optDouble("totalSpent", 0.0),
                            visitCount = obj.optInt("visitCount", 0),
                            lastPurchaseDate = obj.optLong("lastPurchaseDate", 0L).takeIf { it > 0 },
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // 5. Expenses
            if (root.has("expenses")) {
                val arr = root.getJSONArray("expenses")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    database.expenseDao().insertExpense(
                        ExpenseEntity(
                            id = obj.getString("id"),
                            description = obj.optString("description", obj.optString("reason", "")),
                            amount = obj.getDouble("amount"),
                            date = obj.getLong("date"),
                            category = obj.optString("category", "Général")
                        )
                    )
                }
            }

            // 6. Tickets
            if (root.has("tickets")) {
                val arr = root.getJSONArray("tickets")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val statusStr = obj.optString("status", "PAID")
                    val status = try { TicketStatus.valueOf(statusStr) } catch (e: Exception) { TicketStatus.PAID }
                    val orderTypeStr = obj.optString("orderType", "TAKEAWAY")
                    val orderType = try { OrderType.valueOf(orderTypeStr) } catch (e: Exception) { OrderType.TAKEAWAY }
                    val methodStr = obj.optString("paymentMethod", "CASH")
                    val method = try { PaymentMethod.valueOf(methodStr) } catch (e: Exception) { PaymentMethod.CASH }

                    val ticket = TicketEntity(
                        id = obj.getString("id"),
                        ticketNumber = obj.getString("ticketNumber"),
                        date = obj.getLong("date"),
                        status = status,
                        orderType = orderType,
                        subTotal = obj.optDouble("subTotal", 0.0),
                        taxAmount = obj.optDouble("taxAmount", 0.0),
                        totalAmount = obj.getDouble("totalAmount"),
                        discount = obj.optDouble("discount", 0.0),
                        paymentMethod = method,
                        amountPaid = obj.optDouble("amountPaid", 0.0),
                        changeReturned = obj.optDouble("changeReturned", 0.0),
                        customerId = obj.optString("customerId").takeIf { it.isNotBlank() },
                        sellerName = obj.optString("sellerName").takeIf { it.isNotBlank() },
                        note = obj.optString("note").takeIf { it.isNotBlank() }
                    )

                    val items = mutableListOf<TicketItemEntity>()
                    if (obj.has("items")) {
                        val itArr = obj.getJSONArray("items")
                        for (j in 0 until itArr.length()) {
                            val itObj = itArr.getJSONObject(j)
                            items.add(
                                TicketItemEntity(
                                    id = itObj.optString("id", java.util.UUID.randomUUID().toString()),
                                    ticketId = ticket.id,
                                    productId = itObj.getString("productId"),
                                    productName = itObj.getString("productName"),
                                    quantity = itObj.getInt("quantity"),
                                    unitPrice = itObj.getDouble("unitPrice"),
                                    total = itObj.getDouble("total")
                                )
                            )
                        }
                    }

                    database.ticketDao().insertFullTicket(ticket, items)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
