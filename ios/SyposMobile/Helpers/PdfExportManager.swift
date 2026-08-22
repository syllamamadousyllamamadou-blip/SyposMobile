import Foundation
import UIKit
import PDFKit

public class PdfExportManager {
    public static func exportSalesPdf(tickets: [Ticket], settings: ShopSettings) -> URL? {
        let pdfMetaData = [
            kCGPDFContextCreator: "SYPOS Mobile iOS",
            kCGPDFContextAuthor: settings.shopName
        ]
        let format = UIGraphicsPDFRendererFormat()
        format.documentInfo = pdfMetaData as [String: Any]

        let pageWidth = 8.5 * 72.0
        let pageHeight = 11.0 * 72.0
        let pageRect = CGRect(x: 0, y: 0, width: pageWidth, height: pageHeight)

        let renderer = UIGraphicsPDFRenderer(bounds: pageRect, format: format)

        let data = renderer.pdfData { context in
            context.beginPage()

            let titleFont = UIFont.boldSystemFont(ofSize: 22)
            let headerFont = UIFont.boldSystemFont(ofSize: 14)
            let bodyFont = UIFont.systemFont(ofSize: 11)

            var yOffset: CGFloat = 36

            // Header
            let shopTitle = settings.shopName.isEmpty ? "SYPOS MOBILE" : settings.shopName
            shopTitle.draw(at: CGPoint(x: 36, y: yOffset), withAttributes: [.font: titleFont])
            yOffset += 28

            let subtitle = "Rapport d'Historique des Ventes • Généré le \(DateFormatter.localizedString(from: Date(), dateStyle: .medium, timeStyle: .short))"
            subtitle.draw(at: CGPoint(x: 36, y: yOffset), withAttributes: [.font: bodyFont, .foregroundColor: UIColor.darkGray])
            yOffset += 30

            // Table Header
            let headerRect = CGRect(x: 36, y: yOffset, width: pageWidth - 72, height: 24)
            UIColor.systemGray6.setFill()
            UIRectFill(headerRect)

            "N° Ticket".draw(at: CGPoint(x: 42, y: yOffset + 4), withAttributes: [.font: headerFont])
            "Date".draw(at: CGPoint(x: 150, y: yOffset + 4), withAttributes: [.font: headerFont])
            "Paiement".draw(at: CGPoint(x: 270, y: yOffset + 4), withAttributes: [.font: headerFont])
            "Statut".draw(at: CGPoint(x: 390, y: yOffset + 4), withAttributes: [.font: headerFont])
            "Montant".draw(at: CGPoint(x: 490, y: yOffset + 4), withAttributes: [.font: headerFont])
            yOffset += 28

            let dateFormatter = DateFormatter()
            dateFormatter.dateFormat = "dd/MM/yy HH:mm"

            var totalSales: Double = 0

            for ticket in tickets {
                if yOffset > pageHeight - 50 {
                    context.beginPage()
                    yOffset = 36
                }

                if ticket.status == .paid || ticket.status == .credit {
                    totalSales += ticket.totalAmount
                }

                ticket.ticketNumber.draw(at: CGPoint(x: 42, y: yOffset), withAttributes: [.font: bodyFont])
                dateFormatter.string(from: ticket.date).draw(at: CGPoint(x: 150, y: yOffset), withAttributes: [.font: bodyFont])
                (ticket.paymentMethod?.displayName ?? "-").draw(at: CGPoint(x: 270, y: yOffset), withAttributes: [.font: bodyFont])
                ticket.status.displayName.draw(at: CGPoint(x: 390, y: yOffset), withAttributes: [.font: bodyFont])

                let amtStr = "\(Int(ticket.totalAmount)) CFA"
                amtStr.draw(at: CGPoint(x: 490, y: yOffset), withAttributes: [.font: bodyFont])

                yOffset += 18
            }

            yOffset += 15
            let totalLine = "TOTAL VENTES VALIDEES : \(Int(totalSales)) CFA (\(tickets.count) tickets)"
            totalLine.draw(at: CGPoint(x: 36, y: yOffset), withAttributes: [.font: headerFont, .foregroundColor: UIColor.systemGreen])
        }

        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("Rapport_Ventes_SYPOS_\(Int(Date().timeIntervalSince1970)).pdf")
        do {
            try data.write(to: tempURL)
            return tempURL
        } catch {
            return nil
        }
    }

    public static func shareFile(url: URL) {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = windowScene.windows.first?.rootViewController else { return }

        let activityVC = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        rootVC.present(activityVC, animated: true)
    }
}
