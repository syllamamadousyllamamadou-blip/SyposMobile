package com.sypos.mobile.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.sypos.mobile.data.local.BusinessMode
import com.sypos.mobile.data.local.entity.OrderType
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.entity.TicketEntity
import com.sypos.mobile.data.local.entity.TicketItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*

data class BluetoothPrinterDevice(
    val name: String,
    val address: String
)

data class ZReportData(
    val dateText: String,
    val totalSales: Double,
    val ticketsCount: Int,
    val totalExpenses: Double,
    val cashSales: Double,
    val waveSales: Double,
    val orangeMoneySales: Double,
    val mtnSales: Double,
    val moovSales: Double,
    val cardSales: Double,
    val creditSales: Double,
    val netCashInDrawer: Double
)

data class DeliveryNoteData(
    val recipientName: String,
    val recipientPhone: String,
    val deliveryAddress: String,
    val itemsSummary: String,
    val amountToCollect: Double,
    val deliveryFee: Double,
    val note: String? = null
)

data class LabelPrintOptions(
    val printShopHeader: Boolean = false,
    val printProductName: Boolean = true,
    val printPrice: Boolean = true,
    val printBarcodeRaster: Boolean = true,
    val printSerialNumber: Boolean = true,
    val serialNumber: String? = null
)

object BluetoothPrinterHelper {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS Commands
    private val INIT_PRINTER = byteArrayOf(0x1B, 0x40)
    private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val DOUBLE_HEIGHT_ON = byteArrayOf(0x1B, 0x21, 0x10)
    private val DOUBLE_BOTH_ON = byteArrayOf(0x1B, 0x21, 0x30)
    private val DOUBLE_OFF = byteArrayOf(0x1B, 0x21, 0x00)
    private val FONT_SMALL = byteArrayOf(0x1B, 0x21, 0x01)
    private val FONT_NORMAL = byteArrayOf(0x1B, 0x21, 0x00)
    private val FEED_PAPER = byteArrayOf(0x1B, 0x64, 0x03)
    private val CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x41, 0x00)

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(context: Context): List<BluetoothPrinterDevice> {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter() ?: return emptyList()

        if (!adapter.isEnabled) return emptyList()

        return try {
            adapter.bondedDevices.map { device: BluetoothDevice ->
                BluetoothPrinterDevice(
                    name = device.name ?: "Imprimante Inconnue",
                    address = device.address
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun testPrinter(
        context: Context,
        deviceAddress: String,
        shopName: String = "SYPOS MOBILE"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            ?: return@withContext Result.failure(Exception("Bluetooth non disponible"))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(Exception("Veuillez activer le Bluetooth"))
        }

        var socket: BluetoothSocket? = null
        var out: OutputStream? = null

        try {
            socket = connectSocket(adapter, deviceAddress)
            out = socket.outputStream

            out.write(INIT_PRINTER)
            out.write(ALIGN_CENTER)
            out.write(BOLD_ON)
            out.write(DOUBLE_HEIGHT_ON)
            out.write(cleanText("$shopName\n"))
            out.write(DOUBLE_OFF)
            out.write(BOLD_OFF)
            out.write(cleanText("--------------------------------\n"))
            out.write(cleanText("TEST D'IMPRESSION REUSSI !\n"))
            out.write(cleanText("SYPOS POS Mobile - Version Pro\n"))
            out.write(cleanText("Date : ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())}\n"))
            out.write(cleanText("--------------------------------\n"))
            out.write(FEED_PAPER)
            out.write(CUT_PAPER)
            out.flush()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                out?.close()
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Generates a monochrome bitmap raster byte array for genuine scannable Code 128 barcodes.
     * Uses ESC/POS standard raster command `GS v 0`.
     */
    fun generateBarcodeRaster(data: String, width: Int = 384, height: Int = 110): ByteArray {
        return try {
            val writer = MultiFormatWriter()
            val format = if (data.all { it.isDigit() } && data.length in 8..14) BarcodeFormat.CODE_128 else BarcodeFormat.CODE_128
            val bitMatrix: BitMatrix = writer.encode(data, format, width, height)

            val bmWidth = bitMatrix.width
            val bmHeight = bitMatrix.height
            val bytesPerLine = (bmWidth + 7) / 8

            val outputStream = ByteArrayOutputStream()
            // GS v 0 m xL xH yL yH
            outputStream.write(0x1D)
            outputStream.write(0x76)
            outputStream.write(0x30)
            outputStream.write(0x00) // Mode normal
            outputStream.write(bytesPerLine and 0xFF)
            outputStream.write((bytesPerLine shr 8) and 0xFF)
            outputStream.write(bmHeight and 0xFF)
            outputStream.write((bmHeight shr 8) and 0xFF)

            for (y in 0 until bmHeight) {
                var currentByte = 0
                var bitIndex = 0
                for (x in 0 until bmWidth) {
                    if (bitMatrix.get(x, y)) {
                        currentByte = currentByte or (1 shl (7 - bitIndex))
                    }
                    bitIndex++
                    if (bitIndex == 8) {
                        outputStream.write(currentByte)
                        currentByte = 0
                        bitIndex = 0
                    }
                }
                if (bitIndex > 0) {
                    outputStream.write(currentByte)
                }
            }

            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e("PrinterHelper", "Error generating raster barcode", e)
            ByteArray(0)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun printTicket(
        context: Context,
        deviceAddress: String,
        ticket: TicketEntity,
        items: List<TicketItemEntity>,
        customerName: String?,
        settings: ShopSettings
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            ?: return@withContext Result.failure(Exception("Bluetooth non disponible sur cet appareil"))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(Exception("Veuillez activer le Bluetooth sur votre téléphone"))
        }

        var socket: BluetoothSocket? = null
        var out: OutputStream? = null

        try {
            socket = connectSocket(adapter, deviceAddress)
            out = socket.outputStream

            out.write(INIT_PRINTER)

            // Header
            out.write(ALIGN_CENTER)
            out.write(BOLD_ON)
            out.write(DOUBLE_HEIGHT_ON)
            out.write(cleanText("${settings.shopName.ifBlank { "SYPOS COMMERCE" }}\n"))
            out.write(DOUBLE_OFF)
            out.write(BOLD_OFF)

            if (settings.shopAddress.isNotBlank()) {
                out.write(cleanText("${settings.shopAddress}\n"))
            }
            if (settings.shopPhone.isNotBlank()) {
                out.write(cleanText("Tel: ${settings.shopPhone}\n"))
            }

            out.write(cleanText("--------------------------------\n"))

            // Ticket metadata
            out.write(ALIGN_LEFT)
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(ticket.date))
            out.write(cleanText("Ticket N: ${ticket.ticketNumber}\n"))
            out.write(cleanText("Date    : $dateStr\n"))

            val seller = ticket.sellerName ?: settings.sellerName
            if (seller.isNotBlank()) {
                out.write(cleanText("Vendeur : $seller\n"))
            }

            if (customerName != null) {
                out.write(cleanText("Client  : $customerName\n"))
            }

            if (settings.businessMode == BusinessMode.RESTAURANT) {
                val typeLabel = when (ticket.orderType) {
                    OrderType.TAKEAWAY -> "A EMPORTER"
                    OrderType.DINE_IN -> "SUR PLACE"
                    OrderType.DELIVERY -> "LIVRAISON"
                }
                out.write(BOLD_ON)
                out.write(cleanText("Type    : $typeLabel\n"))
                out.write(BOLD_OFF)
            }

            out.write(cleanText("--------------------------------\n"))
            out.write(cleanText(formatItemHeader(32) + "\n"))
            out.write(cleanText("--------------------------------\n"))

            // Items
            for (item in items) {
                printMultiLineItem(out, item.productName, item.quantity, item.unitPrice, item.total, 32)
            }

            out.write(cleanText("--------------------------------\n"))

            // Totals
            if (ticket.discount > 0) {
                out.write(cleanText(formatTwoColumns("Sous-Total", formatMoney(ticket.subTotal), 32) + "\n"))
                out.write(cleanText(formatTwoColumns("Remise", "-${formatMoney(ticket.discount)}", 32) + "\n"))
            }

            if (settings.taxEnabled && ticket.taxAmount > 0) {
                val net = ticket.totalAmount - ticket.taxAmount
                out.write(cleanText(formatTwoColumns("Total HT", formatMoney(net), 32) + "\n"))
                out.write(cleanText(formatTwoColumns("TVA (${settings.taxRatePercent.toInt()}%)", formatMoney(ticket.taxAmount), 32) + "\n"))
            }

            out.write(BOLD_ON)
            out.write(DOUBLE_HEIGHT_ON)
            out.write(cleanText(formatTwoColumns("TOTAL TTC", formatMoney(ticket.totalAmount), 32) + "\n"))
            out.write(DOUBLE_OFF)
            out.write(BOLD_OFF)

            // Payment Details
            if (ticket.paymentMethod != null) {
                val methodLabel = when (ticket.paymentMethod) {
                    com.sypos.mobile.data.local.entity.PaymentMethod.CASH -> "Especes (Cash)"
                    com.sypos.mobile.data.local.entity.PaymentMethod.WAVE -> "Wave"
                    com.sypos.mobile.data.local.entity.PaymentMethod.ORANGE_MONEY -> "Orange Money"
                    com.sypos.mobile.data.local.entity.PaymentMethod.MTN -> "MTN Mobile Money"
                    com.sypos.mobile.data.local.entity.PaymentMethod.MOOV -> "Moov Money"
                    com.sypos.mobile.data.local.entity.PaymentMethod.CARD -> "Carte Bancaire"
                    com.sypos.mobile.data.local.entity.PaymentMethod.CREDIT -> "Vente a Credit"
                }
                out.write(cleanText(formatTwoColumns("Mode Paiement", methodLabel, 32) + "\n"))

                if (ticket.paymentMethod == com.sypos.mobile.data.local.entity.PaymentMethod.CASH && ticket.amountPaid > 0) {
                    out.write(cleanText(formatTwoColumns("Montant Recu", formatMoney(ticket.amountPaid), 32) + "\n"))
                    if (ticket.changeReturned > 0) {
                        out.write(cleanText(formatTwoColumns("Monnaie Rendue", formatMoney(ticket.changeReturned), 32) + "\n"))
                    }
                }
            }

            out.write(cleanText("--------------------------------\n"))

            // Footer
            out.write(ALIGN_CENTER)
            if (settings.receiptFooter.isNotBlank()) {
                out.write(cleanText("${settings.receiptFooter}\n"))
            }

            // Publisher Signature if enabled: Compact single-line small font
            if (settings.showPublisherSignature) {
                out.write(FONT_SMALL)
                out.write(cleanText("Solution: SYPOS MOBILE 0758245530\n"))
                out.write(FONT_NORMAL)
            }

            out.write(FEED_PAPER)
            out.write(CUT_PAPER)
            out.flush()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PrinterHelper", "Error printing ticket", e)
            Result.failure(e)
        } finally {
            try {
                out?.close()
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Prints an intelligent standalone shelf price label or barcode label.
     */
    @SuppressLint("MissingPermission")
    suspend fun printBarcodeLabel(
        context: Context,
        deviceAddress: String,
        productName: String,
        price: Double,
        barcode: String?,
        settings: ShopSettings,
        options: LabelPrintOptions = LabelPrintOptions()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            ?: return@withContext Result.failure(Exception("Bluetooth non disponible"))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(Exception("Veuillez activer le Bluetooth sur votre téléphone"))
        }

        var socket: BluetoothSocket? = null
        var out: OutputStream? = null

        try {
            socket = connectSocket(adapter, deviceAddress)
            out = socket.outputStream

            out.write(INIT_PRINTER)
            out.write(ALIGN_CENTER)

            // 1. Shop Name Header if requested
            if (options.printShopHeader && settings.shopName.isNotBlank()) {
                out.write(BOLD_ON)
                out.write(cleanText("${settings.shopName.uppercase(Locale.getDefault())}\n"))
                out.write(BOLD_OFF)
                out.write(cleanText("--------------------------------\n"))
            }

            // 2. Product Name if requested
            if (options.printProductName && productName.isNotBlank()) {
                out.write(BOLD_ON)
                out.write(DOUBLE_HEIGHT_ON)
                out.write(cleanText("$productName\n"))
                out.write(DOUBLE_OFF)
                out.write(BOLD_OFF)
            }

            // 3. Price if requested
            if (options.printPrice && price > 0) {
                out.write(BOLD_ON)
                out.write(DOUBLE_BOTH_ON)
                out.write(cleanText("${formatMoney(price)}\n"))
                out.write(DOUBLE_OFF)
                out.write(BOLD_OFF)
            }

            // 4. Scannable Barcode Bitmap if requested
            val codeToPrint = barcode?.trim()?.takeIf { it.isNotBlank() }
            if (options.printBarcodeRaster && codeToPrint != null) {
                if (options.printProductName || options.printPrice) {
                    out.write(cleanText("--------------------------------\n"))
                }
                val barcodeRaster = generateBarcodeRaster(codeToPrint, width = 384, height = 110)
                if (barcodeRaster.isNotEmpty()) {
                    out.write(barcodeRaster)
                }
            }

            // 5. Serial Number / Code Text if requested
            val serialText = options.serialNumber?.trim()?.takeIf { it.isNotBlank() } ?: codeToPrint
            if (options.printSerialNumber && serialText != null) {
                out.write(BOLD_ON)
                out.write(cleanText("\n* $serialText *\n"))
                out.write(BOLD_OFF)
            }

            out.write(FEED_PAPER)
            out.write(CUT_PAPER)
            out.flush()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                out?.close()
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun printZReport(
        context: Context,
        deviceAddress: String,
        zData: ZReportData,
        settings: ShopSettings
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            ?: return@withContext Result.failure(Exception("Bluetooth non disponible"))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(Exception("Veuillez activer le Bluetooth sur votre téléphone"))
        }

        var socket: BluetoothSocket? = null
        var out: OutputStream? = null

        try {
            socket = connectSocket(adapter, deviceAddress)
            out = socket.outputStream

            out.write(INIT_PRINTER)
            out.write(ALIGN_CENTER)
            out.write(BOLD_ON)
            out.write(DOUBLE_HEIGHT_ON)
            out.write(cleanText("RAPPORT Z DE CAISSE\n"))
            out.write(DOUBLE_OFF)
            out.write(BOLD_OFF)

            out.write(cleanText("${settings.shopName.ifBlank { "SYPOS COMMERCE" }}\n"))
            out.write(cleanText("Date de cloture: ${zData.dateText}\n"))
            if (settings.sellerName.isNotBlank()) {
                out.write(cleanText("Responsable: ${settings.sellerName}\n"))
            }
            out.write(cleanText("================================\n"))

            out.write(ALIGN_LEFT)
            out.write(BOLD_ON)
            out.write(cleanText(formatTwoColumns("TOTAL VENTES", formatMoney(zData.totalSales), 32) + "\n"))
            out.write(BOLD_OFF)
            out.write(cleanText(formatTwoColumns("Nombre de Tickets", "${zData.ticketsCount}", 32) + "\n"))

            if (zData.totalExpenses > 0) {
                out.write(cleanText(formatTwoColumns("Total Depenses/Sorties", "-${formatMoney(zData.totalExpenses)}", 32) + "\n"))
            }

            out.write(cleanText("--------------------------------\n"))
            out.write(cleanText("Ventilation Encaissements:\n"))

            if (zData.cashSales > 0) out.write(cleanText(formatTwoColumns("  - Especes (Cash)", formatMoney(zData.cashSales), 32) + "\n"))
            if (zData.waveSales > 0) out.write(cleanText(formatTwoColumns("  - Wave", formatMoney(zData.waveSales), 32) + "\n"))
            if (zData.orangeMoneySales > 0) out.write(cleanText(formatTwoColumns("  - Orange Money", formatMoney(zData.orangeMoneySales), 32) + "\n"))
            if (zData.mtnSales > 0) out.write(cleanText(formatTwoColumns("  - MTN Money", formatMoney(zData.mtnSales), 32) + "\n"))
            if (zData.moovSales > 0) out.write(cleanText(formatTwoColumns("  - Moov Money", formatMoney(zData.moovSales), 32) + "\n"))
            if (zData.cardSales > 0) out.write(cleanText(formatTwoColumns("  - Carte Bancaire", formatMoney(zData.cardSales), 32) + "\n"))
            if (zData.creditSales > 0) out.write(cleanText(formatTwoColumns("  - Ventes a Credit", formatMoney(zData.creditSales), 32) + "\n"))

            out.write(cleanText("================================\n"))
            out.write(BOLD_ON)
            out.write(DOUBLE_HEIGHT_ON)
            out.write(cleanText(formatTwoColumns("CASH NET EN CAISSE", formatMoney(zData.netCashInDrawer), 32) + "\n"))
            out.write(DOUBLE_OFF)
            out.write(BOLD_OFF)
            out.write(cleanText("================================\n"))

            out.write(ALIGN_CENTER)
            out.write(cleanText("Signature Responsable:\n\n\n"))
            out.write(cleanText("................................\n"))

            out.write(FEED_PAPER)
            out.write(CUT_PAPER)
            out.flush()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                out?.close()
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun printDeliveryNote(
        context: Context,
        deviceAddress: String,
        delivery: DeliveryNoteData,
        settings: ShopSettings
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            ?: return@withContext Result.failure(Exception("Bluetooth non disponible"))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(Exception("Veuillez activer le Bluetooth sur votre téléphone"))
        }

        var socket: BluetoothSocket? = null
        var out: OutputStream? = null

        try {
            socket = connectSocket(adapter, deviceAddress)
            out = socket.outputStream

            out.write(INIT_PRINTER)
            out.write(ALIGN_CENTER)
            out.write(BOLD_ON)
            out.write(DOUBLE_HEIGHT_ON)
            out.write(cleanText("BORDEREAU DE LIVRAISON\n"))
            out.write(DOUBLE_OFF)
            out.write(BOLD_OFF)

            out.write(cleanText("${settings.shopName.ifBlank { "EXPEDITEUR" }}\n"))
            if (settings.shopPhone.isNotBlank()) out.write(cleanText("Tel: ${settings.shopPhone}\n"))
            out.write(cleanText("================================\n"))

            out.write(ALIGN_LEFT)
            out.write(BOLD_ON)
            out.write(cleanText("DESTINATAIRE :\n"))
            out.write(BOLD_OFF)
            out.write(cleanText("Nom : ${delivery.recipientName}\n"))
            out.write(cleanText("Tel : ${delivery.recipientPhone}\n"))
            out.write(cleanText("Lieu: ${delivery.deliveryAddress}\n"))

            if (!delivery.note.isNullOrBlank()) {
                out.write(cleanText("Note: ${delivery.note}\n"))
            }

            out.write(cleanText("--------------------------------\n"))
            out.write(BOLD_ON)
            out.write(cleanText("COLIS / CONTENU :\n"))
            out.write(BOLD_OFF)
            out.write(cleanText("${delivery.itemsSummary}\n"))
            out.write(cleanText("--------------------------------\n"))

            out.write(BOLD_ON)
            val totalCollect = delivery.amountToCollect + delivery.deliveryFee
            out.write(cleanText(formatTwoColumns("Prix Articles", formatMoney(delivery.amountToCollect), 32) + "\n"))
            if (delivery.deliveryFee > 0) {
                out.write(cleanText(formatTwoColumns("Frais de Livraison", formatMoney(delivery.deliveryFee), 32) + "\n"))
            }
            out.write(cleanText("--------------------------------\n"))
            out.write(DOUBLE_HEIGHT_ON)
            out.write(cleanText(formatTwoColumns("A ENCAISSER", formatMoney(totalCollect), 32) + "\n"))
            out.write(DOUBLE_OFF)
            out.write(BOLD_OFF)

            out.write(cleanText("================================\n"))
            out.write(ALIGN_CENTER)
            out.write(cleanText("Signature Client a la reception:\n\n\n"))
            out.write(cleanText("................................\n"))

            out.write(FEED_PAPER)
            out.write(CUT_PAPER)
            out.flush()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                out?.close()
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectSocket(adapter: BluetoothAdapter, deviceAddress: String): BluetoothSocket {
        val device = adapter.getRemoteDevice(deviceAddress)
        adapter.cancelDiscovery()

        return try {
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            socket
        } catch (e: Exception) {
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val fallbackSocket = method.invoke(device, 1) as BluetoothSocket
            fallbackSocket.connect()
            fallbackSocket
        }
    }

    private fun printMultiLineItem(
        out: OutputStream,
        name: String,
        qty: Int,
        unitPrice: Double,
        total: Double,
        width: Int
    ) {
        val rightCol = formatMoney(total)
        val leftPrefix = "${qty}x "
        val unitStr = "@${formatMoney(unitPrice)}"

        val maxNameWidth = width - rightCol.length - 1
        val cleanName = name.replace("\n", " ").trim()

        if (cleanName.length <= maxNameWidth - leftPrefix.length) {
            val leftSide = "$leftPrefix$cleanName"
            val spaces = " ".repeat((width - leftSide.length - rightCol.length).coerceAtLeast(1))
            out.write(cleanText("$leftSide$spaces$rightCol\n"))
        } else {
            out.write(cleanText("$leftPrefix$cleanName\n"))
            val detailLeft = "  $unitStr"
            val spaces = " ".repeat((width - detailLeft.length - rightCol.length).coerceAtLeast(1))
            out.write(cleanText("$detailLeft$spaces$rightCol\n"))
        }
    }

    /**
     * Cleans text to strictly standard ASCII 32..126 without any non-breaking spaces
     * that cause '?' question marks on thermal printers.
     */
    private fun cleanText(text: String): ByteArray {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        val noAccents = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        val sanitized = noAccents
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u2007', ' ')
            .replace('\u2009', ' ')
            .replace("’", "'")
            .replace("‘", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("–", "-")
            .replace("—", "-")
            .replace("…", "...")
            .replace("•", "-")

        val asciiBytes = ByteArray(sanitized.length)
        for (i in sanitized.indices) {
            val c = sanitized[i]
            asciiBytes[i] = if (c.code in 32..126 || c == '\n' || c == '\r' || c == '\t') c.code.toByte() else ' '.code.toByte()
        }
        return asciiBytes
    }

    /**
     * Formats monetary amounts with standard ASCII spaces, preventing '?' question marks.
     */
    private fun formatMoney(amount: Double): String {
        val numStr = String.format(Locale.US, "%,.0f", amount).replace(',', ' ')
        return "$numStr CFA"
    }

    private fun formatTwoColumns(left: String, right: String, totalWidth: Int): String {
        val spacesCount = totalWidth - left.length - right.length
        return if (spacesCount > 0) {
            left + " ".repeat(spacesCount) + right
        } else {
            "$left $right"
        }
    }

    private fun formatItemHeader(width: Int): String {
        val left = "QTE / ARTICLE"
        val right = "MONTANT"
        val spacesCount = (width - left.length - right.length).coerceAtLeast(1)
        return left + " ".repeat(spacesCount) + right
    }
}
