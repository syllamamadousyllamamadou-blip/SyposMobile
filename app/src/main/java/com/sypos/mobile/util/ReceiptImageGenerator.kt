package com.sypos.mobile.util

import com.sypos.mobile.util.CurrencyUtils.formatAmount
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import androidx.core.content.FileProvider
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.entity.PaymentMethod
import com.sypos.mobile.data.local.entity.TicketEntity
import com.sypos.mobile.data.local.entity.TicketItemEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptImageGenerator {

    fun generateReceiptBitmap(
        ticket: TicketEntity,
        items: List<TicketItemEntity>,
        customerName: String?,
        settings: ShopSettings = ShopSettings()
    ): Bitmap {
        val width = 640
        val padding = 36f

        val paintHeader = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val paintSubHeader = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 20f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }
        val paintBoldLeft = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val paintNormalLeft = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 20f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }
        val paintNormalRight = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 20f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }
        val paintBoldRight = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val paintTotalLabel = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val paintTotalValue = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0D9488")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val paintFooter = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 18f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }
        val paintDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        }

        fun drawMultiline(canvas: Canvas, text: String, x: Float, y: Float, paint: TextPaint, maxWidth: Int, align: Layout.Alignment): Float {
            val sl = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
                .setAlignment(align)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .build()
            canvas.save()
            if (align == Layout.Alignment.ALIGN_CENTER) {
                canvas.translate(x - maxWidth / 2f, y)
            } else if (align == Layout.Alignment.ALIGN_OPPOSITE) {
                canvas.translate(x - maxWidth, y)
            } else {
                canvas.translate(x, y)
            }
            sl.draw(canvas)
            canvas.restore()
            return sl.height.toFloat()
        }

        val maxTempHeight = 4000
        val tempBitmap = Bitmap.createBitmap(width, maxTempHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tempBitmap)
        canvas.drawColor(Color.WHITE)

        var y = 50f
        val centerX = width / 2f
        val rightX = width - padding
        val textWidth = (width - 2 * padding).toInt()

        if (settings.shopLogoUri != null) {
            try {
                val logoFile = java.io.File(settings.shopLogoUri)
                if (logoFile.exists()) {
                    val logoBitmap = android.graphics.BitmapFactory.decodeFile(logoFile.absolutePath)
                    val scaledLogo = android.graphics.Bitmap.createScaledBitmap(logoBitmap, 120, 120, true)
                    val logoX = centerX - (120f / 2f)
                    canvas.drawBitmap(scaledLogo, logoX, y, null)
                    y += 130f
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        y += drawMultiline(canvas, settings.shopName.uppercase(Locale.getDefault()), centerX, y, paintHeader, textWidth, Layout.Alignment.ALIGN_CENTER)
        y += 20f

        if (settings.shopAddress.isNotBlank()) {
            y += drawMultiline(canvas, settings.shopAddress, centerX, y, paintSubHeader, textWidth, Layout.Alignment.ALIGN_CENTER)
            y += 16f
        }
        if (settings.shopPhone.isNotBlank()) {
            y += drawMultiline(canvas, "Tél : ${settings.shopPhone}", centerX, y, paintSubHeader, textWidth, Layout.Alignment.ALIGN_CENTER)
            y += 16f
        }

        y += 10f
        canvas.drawLine(padding, y, rightX, y, paintDivider)
        y += 30f

        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(ticket.date))
        canvas.drawText("Ticket N° : ${ticket.ticketNumber}", padding, y + 20f, paintBoldLeft)
        canvas.drawText(dateStr, rightX, y + 20f, paintNormalRight)
        y += 38f

        if (settings.sellerName.isNotBlank()) {
            canvas.drawText("Vendeur : ${settings.sellerName}", padding, y + 20f, paintNormalLeft)
            y += 36f
        }

        if (!customerName.isNullOrBlank()) {
            canvas.drawText("Client : $customerName", padding, y + 20f, paintBoldLeft)
            y += 38f
        }

        y += 6f
        canvas.drawLine(padding, y, rightX, y, paintDivider)
        y += 30f

        items.forEach { item ->
            val qty = "${item.quantity}x "
            val totalStr = item.total.formatAmount(settings.currency)
            
            val totalWidth = paintNormalRight.measureText(totalStr)
            val leftWidth = (width - padding * 2 - totalWidth - 20f).toInt()
            
            val h = drawMultiline(canvas, qty + item.productName, padding, y, paintNormalLeft, leftWidth, Layout.Alignment.ALIGN_NORMAL)
            canvas.drawText(totalStr, rightX, y + paintNormalRight.textSize, paintNormalRight)
            y += h + 16f
        }

        y += 6f
        canvas.drawLine(padding, y, rightX, y, paintDivider)
        y += 36f

        if (ticket.discount > 0) {
            canvas.drawText("Remise accordée :", padding, y + 20f, paintNormalLeft)
            canvas.drawText("-${ticket.discount.formatAmount(settings.currency)}", rightX, y + 20f, paintNormalRight)
            y += 38f
        }

        if (ticket.taxAmount > 0) {
            canvas.drawText("TVA (${settings.taxRatePercent.toInt()}%) :", padding, y + 20f, paintNormalLeft)
            canvas.drawText(ticket.taxAmount.formatAmount(settings.currency), rightX, y + 20f, paintNormalRight)
            y += 38f
        }

        canvas.drawText("TOTAL NET :", padding, y + 20f, paintTotalLabel)
        canvas.drawText(ticket.totalAmount.formatAmount(settings.currency), rightX, y + 20f, paintTotalValue)
        y += 46f

        if (ticket.paymentMethod == PaymentMethod.SPLIT) {
            canvas.drawText("Paiement Fractionné :", padding, y + 20f, paintNormalLeft)
            y += 38f
            if (ticket.splitCashAmount != null && ticket.splitCashAmount > 0) {
                canvas.drawText(" - Espèces", padding, y + 20f, paintNormalLeft)
                canvas.drawText(ticket.splitCashAmount.formatAmount(settings.currency), rightX, y + 20f, paintNormalRight)
                y += 36f
            }
            if (ticket.splitOtherAmount != null && ticket.splitOtherAmount > 0 && ticket.splitOtherMethod != null) {
                val otherMethodName = when (ticket.splitOtherMethod) {
                    PaymentMethod.WAVE -> "Wave"
                    PaymentMethod.ORANGE_MONEY -> "Orange Money"
                    PaymentMethod.MTN -> "MTN MoMo"
                    PaymentMethod.MOOV -> "Moov Money"
                    PaymentMethod.CARD -> "Carte"
                    else -> "Autre"
                }
                canvas.drawText(" - $otherMethodName", padding, y + 20f, paintNormalLeft)
                canvas.drawText(ticket.splitOtherAmount.formatAmount(settings.currency), rightX, y + 20f, paintNormalRight)
                y += 36f
            }
            if (ticket.changeReturned > 0) {
                canvas.drawText("Monnaie rendue :", padding, y + 20f, paintNormalLeft)
                canvas.drawText(ticket.changeReturned.formatAmount(settings.currency), rightX, y + 20f, paintNormalRight)
                y += 36f
            }
        } else if (ticket.paymentMethod == PaymentMethod.CREDIT) {
            canvas.drawText("Règlement :", padding, y + 20f, paintNormalLeft)
            canvas.drawText("À Crédit", rightX, y + 20f, paintBoldRight)
            y += 38f
            if (ticket.amountPaid > 0) {
                canvas.drawText("Acompte payé :", padding, y + 20f, paintNormalLeft)
                canvas.drawText(ticket.amountPaid.formatAmount(settings.currency), rightX, y + 20f, paintNormalRight)
                y += 36f
            }
            val restToPay = ticket.totalAmount - ticket.amountPaid
            if (restToPay > 0) {
                canvas.drawText("Reste à payer :", padding, y + 20f, paintNormalLeft)
                canvas.drawText(restToPay.formatAmount(settings.currency), rightX, y + 20f, paintNormalRight)
                y += 36f
            }
        } else {
            val methodStr = when (ticket.paymentMethod) {
                PaymentMethod.CASH -> "Espèces (Cash)"
                PaymentMethod.WAVE -> "Wave Mobile Money"
                PaymentMethod.ORANGE_MONEY -> "Orange Money"
                PaymentMethod.MTN -> "MTN MoMo"
                PaymentMethod.MOOV -> "Moov Money"
                PaymentMethod.CARD -> "Carte Bancaire"
                else -> "Inconnu"
            }
            canvas.drawText("Règlement :", padding, y + 20f, paintNormalLeft)
            canvas.drawText(methodStr, rightX, y + 20f, paintBoldRight)
            y += 38f

            if (ticket.amountPaid > 0 && ticket.paymentMethod == PaymentMethod.CASH) {
                canvas.drawText("Montant reçu :", padding, y + 20f, paintNormalLeft)
                canvas.drawText(ticket.amountPaid.formatAmount(settings.currency), rightX, y + 20f, paintNormalRight)
                y += 36f
            }

            if (ticket.changeReturned > 0) {
                canvas.drawText("Monnaie rendue :", padding, y + 20f, paintNormalLeft)
                canvas.drawText(ticket.changeReturned.formatAmount(settings.currency), rightX, y + 20f, paintNormalRight)
                y += 36f
            }
        }

        y += 10f
        canvas.drawLine(padding, y, rightX, y, paintDivider)
        y += 30f

        if (settings.receiptFooter.isNotBlank()) {
            y += drawMultiline(canvas, settings.receiptFooter, centerX, y, paintFooter, textWidth, Layout.Alignment.ALIGN_CENTER)
            y += 24f
        }

        if (settings.showPublisherSignature) {
            y += drawMultiline(canvas, settings.publisherSignatureText, centerX, y, paintFooter, textWidth, Layout.Alignment.ALIGN_CENTER)
            y += 20f
        }

        y += 40f

        val finalBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, width, y.toInt())
        tempBitmap.recycle()
        return finalBitmap
    }

    fun saveReceiptToPngFile(context: Context, bitmap: Bitmap, ticketNumber: String): File {
        val dir = File(context.cacheDir, "receipts")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "Ticket_${ticketNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    fun shareReceiptPng(
        context: Context,
        ticket: TicketEntity,
        items: List<TicketItemEntity>,
        customerName: String?,
        settings: ShopSettings = ShopSettings()
    ) {
        try {
            val bitmap = generateReceiptBitmap(ticket, items, customerName, settings)
            val file = saveReceiptToPngFile(context, bitmap, ticket.ticketNumber)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Bonjour, voici votre reçu de caisse ${ticket.ticketNumber} chez ${settings.shopName}. Merci pour votre confiance !")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Partager le ticket WhatsApp"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


