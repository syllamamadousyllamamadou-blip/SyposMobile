package com.sypos.mobile.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sypos.mobile.data.local.ShopSettings
import com.sypos.mobile.data.local.entity.TicketEntity
import com.sypos.mobile.data.local.entity.TicketStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfExportHelper {

    suspend fun generateSalesPdf(
        context: Context,
        tickets: List<TicketEntity>,
        settings: ShopSettings,
        periodTitle: String = "Historique Complet"
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 standard width (pt)
            val pageHeight = 842 // A4 standard height (pt)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val grayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 9f }

            val totalSales = tickets.filter { it.status == TicketStatus.PAID }.sumOf { it.totalAmount }
            val paidCount = tickets.count { it.status == TicketStatus.PAID }

            val itemsPerPage = 22
            val totalPages = (tickets.size / itemsPerPage) + 1

            var currentTicketIndex = 0

            for (pageNumber in 1..totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                var y = 40f

                // Header on Page 1
                if (pageNumber == 1) {
                    // Header background band
                    paint.color = Color.rgb(240, 244, 248)
                    canvas.drawRoundRect(20f, 20f, (pageWidth - 20).toFloat(), 95f, 10f, 10f, paint)

                    // Shop Name & Title
                    boldPaint.color = Color.rgb(26, 86, 219)
                    boldPaint.textSize = 16f
                    canvas.drawText(settings.shopName.uppercase(Locale.getDefault()), 35f, y + 10f, boldPaint)

                    paint.color = Color.DKGRAY
                    paint.textSize = 9f
                    val subTitle = if (settings.shopAddress.isNotBlank()) "${settings.shopAddress} • Tel: ${settings.shopPhone}" else "Rapport d'activité"
                    canvas.drawText(subTitle, 35f, y + 25f, paint)

                    boldPaint.color = Color.rgb(30, 41, 59)
                    boldPaint.textSize = 13f
                    canvas.drawText("RAPPORT DES VENTES - $periodTitle", 35f, y + 45f, boldPaint)

                    val dateGenStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
                    grayPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("Généré le: $dateGenStr", (pageWidth - 35).toFloat(), y + 15f, grayPaint)
                    canvas.drawText("Page $pageNumber / $totalPages", (pageWidth - 35).toFloat(), y + 30f, grayPaint)
                    grayPaint.textAlign = Paint.Align.LEFT

                    y = 115f

                    // KPI Summary Box
                    paint.color = Color.rgb(238, 242, 255)
                    canvas.drawRoundRect(20f, y, (pageWidth - 20).toFloat(), y + 45f, 8f, 8f, paint)

                    boldPaint.color = Color.rgb(30, 64, 175)
                    boldPaint.textSize = 10f
                    canvas.drawText("CHIFFRE D'AFFAIRES TOTAL", 35f, y + 18f, boldPaint)
                    boldPaint.textSize = 14f
                    canvas.drawText(String.format(Locale.FRANCE, "%,.0f CFA", totalSales), 35f, y + 35f, boldPaint)

                    boldPaint.textSize = 10f
                    canvas.drawText("NOMBRE DE TICKETS PAYÉS", 260f, y + 18f, boldPaint)
                    boldPaint.textSize = 14f
                    canvas.drawText("$paidCount ventes", 260f, y + 35f, boldPaint)

                    boldPaint.textSize = 10f
                    canvas.drawText("TOTAL TICKETS GÉNÉRÉS", 430f, y + 18f, boldPaint)
                    boldPaint.textSize = 14f
                    canvas.drawText("${tickets.size} tickets", 430f, y + 35f, boldPaint)

                    y += 65f
                } else {
                    boldPaint.color = Color.rgb(30, 41, 59)
                    boldPaint.textSize = 11f
                    canvas.drawText("${settings.shopName} - Rapport des Ventes (Suite)", 25f, y, boldPaint)
                    grayPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("Page $pageNumber / $totalPages", (pageWidth - 25).toFloat(), y, grayPaint)
                    grayPaint.textAlign = Paint.Align.LEFT
                    y += 20f
                }

                // Table Header
                paint.color = Color.rgb(226, 232, 240)
                canvas.drawRect(20f, y, (pageWidth - 20).toFloat(), y + 22f, paint)

                boldPaint.color = Color.rgb(15, 23, 42)
                boldPaint.textSize = 9f
                canvas.drawText("N° TICKET", 30f, y + 15f, boldPaint)
                canvas.drawText("DATE & HEURE", 120f, y + 15f, boldPaint)
                canvas.drawText("RÈGLEMENT", 240f, y + 15f, boldPaint)
                canvas.drawText("STATUT", 340f, y + 15f, boldPaint)
                boldPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("MONTANT CFA", (pageWidth - 30).toFloat(), y + 15f, boldPaint)
                boldPaint.textAlign = Paint.Align.LEFT

                y += 26f

                // Table Rows
                val startIndex = currentTicketIndex
                val endIndex = minOf(startIndex + itemsPerPage, tickets.size)

                for (i in startIndex until endIndex) {
                    val ticket = tickets[i]
                    val dateStr = SimpleDateFormat("dd/MM/yy HH:mm", Locale.FRANCE).format(Date(ticket.date))

                    // Zebra striping
                    if ((i - startIndex) % 2 == 1) {
                        paint.color = Color.rgb(248, 250, 252)
                        canvas.drawRect(20f, y - 4f, (pageWidth - 20).toFloat(), y + 18f, paint)
                    }

                    paint.color = Color.rgb(30, 41, 59)
                    paint.textSize = 9f
                    canvas.drawText(ticket.ticketNumber, 30f, y + 10f, paint)
                    canvas.drawText(dateStr, 120f, y + 10f, paint)
                    canvas.drawText(ticket.paymentMethod?.name ?: "ESPECES", 240f, y + 10f, paint)

                    // Status badge color
                    val (statusText, statusColor) = when (ticket.status) {
                        TicketStatus.PAID -> Pair("PAYÉ", Color.rgb(22, 101, 52))
                        TicketStatus.CREDIT -> Pair("CRÉDIT", Color.rgb(194, 65, 12))
                        TicketStatus.ON_HOLD -> Pair("EN ATTENTE", Color.rgb(30, 64, 175))
                        TicketStatus.CANCELLED -> Pair("ANNULÉ", Color.rgb(153, 27, 27))
                    }
                    boldPaint.color = statusColor
                    boldPaint.textSize = 8.5f
                    canvas.drawText(statusText, 340f, y + 10f, boldPaint)

                    // Amount
                    boldPaint.color = Color.rgb(15, 23, 42)
                    boldPaint.textSize = 9.5f
                    boldPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(String.format(Locale.FRANCE, "%,.0f", ticket.totalAmount), (pageWidth - 30).toFloat(), y + 10f, boldPaint)
                    boldPaint.textAlign = Paint.Align.LEFT

                    y += 22f
                }

                currentTicketIndex = endIndex

                // Footer on bottom of page
                paint.color = Color.rgb(148, 163, 184)
                canvas.drawLine(20f, (pageHeight - 35).toFloat(), (pageWidth - 20).toFloat(), (pageHeight - 35).toFloat(), paint)
                grayPaint.textSize = 8f
                val footerSig = if (settings.showPublisherSignature) "${settings.publisherSignatureText} • " else ""
                canvas.drawText("${footerSig}Édité par ${settings.sellerName} - SYPOS Mobile POS", 25f, (pageHeight - 20).toFloat(), grayPaint)

                pdfDocument.finishPage(page)

                if (currentTicketIndex >= tickets.size) break
            }

            // Save PDF to cache/files dir
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(exportsDir, "Ventes_SYPOS_$timeStamp.pdf")

            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openOrSharePdf(context: Context, pdfFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Rapport des Ventes SYPOS")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Ouvrir ou Partager le Rapport PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur lors de l'ouverture du PDF : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
