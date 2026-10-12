package com.sypos.mobile.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.sypos.mobile.data.local.entity.ProductEntity
import com.sypos.mobile.data.local.entity.TicketEntity
import com.sypos.mobile.data.local.entity.CustomerEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object ExportHelper {

    fun exportProductsToCsv(context: Context, products: List<ProductEntity>) {
        val fileName = "catalogue_produits_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.csv"
        val file = File(context.cacheDir, fileName)

        file.bufferedWriter(Charsets.UTF_8).use { out ->
            // BOM for UTF-8 Excel compatibility
            out.write("\uFEFF")
            out.write("ID;Nom du Produit;Prix de Vente;Coût d'Achat;Marge;Stock Actuel;Stock Alerte;Code-barres\n")

            products.forEach { p ->
                val margin = p.salePrice - p.costPrice
                val line = "${p.id};${escapeCsv(p.name)};${p.salePrice};${p.costPrice};$margin;${p.stockQuantity};${p.alertStock};${p.barcode ?: ""}\n"
                out.write(line)
            }
        }

        shareCsvFile(context, file, "Exporter le Catalogue Produits")
    }

    fun exportTicketsToCsv(context: Context, tickets: List<TicketEntity>) {
        val fileName = "journal_ventes_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.csv"
        val file = File(context.cacheDir, fileName)

        file.bufferedWriter(Charsets.UTF_8).use { out ->
            out.write("\uFEFF")
            out.write("Numero Ticket;Date;Statut;Type Commande;Total;Mode Paiement;Montant Paye;Monnaie Rendue\n")

            tickets.forEach { t ->
                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(t.date))
                val line = "${t.ticketNumber};$dateStr;${t.status.name};${t.orderType.name};${t.totalAmount};${t.paymentMethod?.name ?: ""};${t.amountPaid};${t.changeReturned}\n"
                out.write(line)
            }
        }

        shareCsvFile(context, file, "Exporter le Journal des Ventes")
    }

    fun exportCustomersToCsv(context: Context, customers: List<CustomerEntity>) {
        val fileName = "base_clients_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.csv"
        val file = File(context.cacheDir, fileName)

        file.bufferedWriter(Charsets.UTF_8).use { out ->
            out.write("\uFEFF")
            out.write("ID;Nom;Telephone;Email;Total Depense;Nombre de Visites;Derniere Visite;Notes\n")

            customers.forEach { c ->
                val dateStr = if (c.lastPurchaseDate != null && c.lastPurchaseDate > 0) {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(c.lastPurchaseDate))
                } else "Aucune"
                
                val line = "${c.id};${escapeCsv(c.name)};${escapeCsv(c.phone ?: "")};${escapeCsv(c.email ?: "")};${c.totalSpent};${c.visitCount};$dateStr;${escapeCsv(c.notes ?: "")}\n"
                out.write(line)
            }
        }

        shareCsvFile(context, file, "Exporter la Base Clients")
    }

    private fun shareCsvFile(context: Context, file: File, title: String) {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            android.net.Uri.fromFile(file)
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "text/csv"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(sendIntent, title)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun escapeCsv(value: String): String {
        return "\"${value.replace("\"", "\"\"")}\""
    }
}
