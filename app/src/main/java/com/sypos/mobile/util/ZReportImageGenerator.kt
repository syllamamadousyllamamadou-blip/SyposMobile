package com.sypos.mobile.util

import com.sypos.mobile.util.CurrencyUtils.formatAmount
import android.content.Context
import android.graphics.*
import android.text.TextPaint
import com.sypos.mobile.data.local.ShopSettings

object ZReportImageGenerator {

    fun generateReportBitmap(
        zData: ZReportData,
        settings: ShopSettings = ShopSettings()
    ): Bitmap {
        val width = 640
        val padding = 36f

        val paintHeader = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val paintSubHeader = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 20f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
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
        val paintBoldLeft = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val paintBoldRight = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 22f
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
        val paintDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        }

        val rightX = width - padding

        var totalHeight = 150f
        totalHeight += 240f // details
        val benefice = zData.totalSales - zData.totalPurchaseCost - zData.totalExpenses
        totalHeight += 120f
        
        var paymentMethodsCount = 0
        if (zData.cashSales > 0) paymentMethodsCount++
        if (zData.waveSales > 0) paymentMethodsCount++
        if (zData.orangeMoneySales > 0) paymentMethodsCount++
        if (zData.mtnSales > 0) paymentMethodsCount++
        if (zData.moovSales > 0) paymentMethodsCount++
        if (zData.cardSales > 0) paymentMethodsCount++
        if (zData.creditSales > 0) paymentMethodsCount++

        totalHeight += (paymentMethodsCount * 36f) + 100f
        totalHeight += 200f // Footer

        val bitmap = Bitmap.createBitmap(width, totalHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = 50f
        val centerX = width / 2f

        canvas.drawText("RAPPORT Z DE CAISSE", centerX, y, paintHeader)
        y += 40f
        canvas.drawText(settings.shopName.ifBlank { "KAYO COMMERCE" }, centerX, y, paintSubHeader)
        y += 30f
        canvas.drawText("Date: ", centerX, y, paintSubHeader)
        y += 30f
        if (settings.sellerName.isNotBlank()) {
            canvas.drawText("Responsable: ", centerX, y, paintSubHeader)
            y += 30f
        }

        y += 10f
        canvas.drawLine(padding, y, rightX, y, paintDivider)
        y += 40f

        canvas.drawText("TOTAL VENTES :", padding, y, paintTotalLabel)
        canvas.drawText(zData.totalSales.formatAmount(settings.currency), rightX, y, paintTotalValue)
        y += 40f
        
        canvas.drawText("Nombre de Tickets :", padding, y, paintNormalLeft)
        canvas.drawText("", rightX, y, paintNormalRight)
        y += 36f

        if (zData.totalExpenses > 0) {
            canvas.drawText("Total Dépenses :", padding, y, paintNormalLeft)
            canvas.drawText("-", rightX, y, paintNormalRight)
            y += 36f
        }

        y += 10f
        canvas.drawLine(padding, y, rightX, y, paintDivider)
        y += 40f

        canvas.drawText("BÉNÉFICE NET :", padding, y, paintBoldLeft)
        canvas.drawText(benefice.formatAmount(settings.currency), rightX, y, paintBoldRight)
        y += 40f

        y += 10f
        canvas.drawLine(padding, y, rightX, y, paintDivider)
        y += 40f

        canvas.drawText("Ventilation Encaissements :", padding, y, paintBoldLeft)
        y += 36f

        if (zData.cashSales > 0) {
            canvas.drawText(" - Espèces (Cash)", padding, y, paintNormalLeft)
            canvas.drawText(zData.cashSales.formatAmount(settings.currency), rightX, y, paintNormalRight)
            y += 36f
        }
        if (zData.waveSales > 0) {
            canvas.drawText(" - Wave", padding, y, paintNormalLeft)
            canvas.drawText(zData.waveSales.formatAmount(settings.currency), rightX, y, paintNormalRight)
            y += 36f
        }
        if (zData.orangeMoneySales > 0) {
            canvas.drawText(" - Orange Money", padding, y, paintNormalLeft)
            canvas.drawText(zData.orangeMoneySales.formatAmount(settings.currency), rightX, y, paintNormalRight)
            y += 36f
        }
        if (zData.mtnSales > 0) {
            canvas.drawText(" - MTN Money", padding, y, paintNormalLeft)
            canvas.drawText(zData.mtnSales.formatAmount(settings.currency), rightX, y, paintNormalRight)
            y += 36f
        }
        if (zData.moovSales > 0) {
            canvas.drawText(" - Moov Money", padding, y, paintNormalLeft)
            canvas.drawText(zData.moovSales.formatAmount(settings.currency), rightX, y, paintNormalRight)
            y += 36f
        }
        if (zData.cardSales > 0) {
            canvas.drawText(" - Carte Bancaire", padding, y, paintNormalLeft)
            canvas.drawText(zData.cardSales.formatAmount(settings.currency), rightX, y, paintNormalRight)
            y += 36f
        }
        if (zData.creditSales > 0) {
            canvas.drawText(" - Ventes à Crédit", padding, y, paintNormalLeft)
            canvas.drawText(zData.creditSales.formatAmount(settings.currency), rightX, y, paintNormalRight)
            y += 36f
        }

        y += 10f
        canvas.drawLine(padding, y, rightX, y, paintDivider)
        y += 40f

        canvas.drawText("CASH NET EN CAISSE :", padding, y, paintTotalLabel)
        canvas.drawText(zData.netCashInDrawer.formatAmount(settings.currency), rightX, y, paintTotalValue)
        y += 50f
        
        y += 10f
        canvas.drawLine(padding, y, rightX, y, paintDivider)
        y += 30f

        canvas.drawText("Généré par KAYO", centerX, y, paintSubHeader)

        return bitmap
    }
}
