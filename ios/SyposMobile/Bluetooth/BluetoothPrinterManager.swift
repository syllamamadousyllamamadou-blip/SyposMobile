import Foundation
import CoreBluetooth
import UIKit

public struct BluetoothPrinterDevice: Identifiable, Equatable {
    public var id: UUID
    public var name: String
    public var peripheral: CBPeripheral

    public init(id: UUID = UUID(), name: String, peripheral: CBPeripheral) {
        self.id = id
        self.name = name
        self.peripheral = peripheral
    }
}

public struct LabelPrintOptions {
    public var printShopHeader: Bool
    public var printProductName: Bool
    public var printPrice: Bool
    public var printBarcodeRaster: Bool
    public var printSerialNumber: Bool
    public var serialNumber: String?

    public init(
        printShopHeader: Bool = false,
        printProductName: Bool = true,
        printPrice: Bool = true,
        printBarcodeRaster: Bool = true,
        printSerialNumber: Bool = true,
        serialNumber: String? = nil
    ) {
        self.printShopHeader = printShopHeader
        self.printProductName = printProductName
        self.printPrice = printPrice
        self.printBarcodeRaster = printBarcodeRaster
        self.printSerialNumber = printSerialNumber
        self.serialNumber = serialNumber
    }
}

public struct DeliveryNoteData {
    public var recipientName: String
    public var recipientPhone: String
    public var deliveryAddress: String
    public var itemsSummary: String
    public var amountToCollect: Double
    public var deliveryFee: Double
    public var note: String?

    public init(
        recipientName: String,
        recipientPhone: String,
        deliveryAddress: String,
        itemsSummary: String,
        amountToCollect: Double,
        deliveryFee: Double = 0.0,
        note: String? = nil
    ) {
        self.recipientName = recipientName
        self.recipientPhone = recipientPhone
        self.deliveryAddress = deliveryAddress
        self.itemsSummary = itemsSummary
        self.amountToCollect = amountToCollect
        self.deliveryFee = deliveryFee
        self.note = note
    }
}

public class BluetoothPrinterManager: NSObject, ObservableObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    public static let shared = BluetoothPrinterManager()

    @Published public var discoveredPrinters: [BluetoothPrinterDevice] = []
    @Published public var connectedPrinter: CBPeripheral?
    @Published public var connectedPrinterName: String?
    @Published public var isScanning = false
    @Published public var isConnected = false

    private var centralManager: CBCentralManager!
    private var targetCharacteristic: CBCharacteristic?
    private var pendingPrintData: Data?
    private var pendingPrintCompletion: ((Result<Void, Error>) -> Void)?

    public override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil, options: [CBCentralManagerOptionShowPowerAlertKey: true])
    }

    public func startScanning() {
        guard centralManager.state == .poweredOn else { return }
        discoveredPrinters.removeAll()
        isScanning = true
        centralManager.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])

        DispatchQueue.main.asyncAfter(deadline: .now() + 10) { [weak self] in
            self?.stopScanning()
        }
    }

    public func stopScanning() {
        centralManager.stopScan()
        isScanning = false
    }

    public func connect(to device: BluetoothPrinterDevice) {
        connect(peripheral: device.peripheral, name: device.name)
    }

    public func connect(peripheral: CBPeripheral, name: String? = nil) {
        connectedPrinter = peripheral
        connectedPrinterName = name ?? peripheral.name ?? "Imprimante Thermique"
        peripheral.delegate = self
        centralManager.connect(peripheral, options: [
            CBConnectPeripheralOptionNotifyOnConnectionKey: true,
            CBConnectPeripheralOptionNotifyOnDisconnectionKey: true
        ])
    }

    public func autoConnectSavedPrinter(savedUUIDString: String?) {
        guard centralManager.state == .poweredOn, let uuidString = savedUUIDString, let uuid = UUID(uuidString: uuidString) else { return }
        if isConnected && connectedPrinter?.identifier == uuid { return }

        let known = centralManager.retrievePeripherals(withIdentifiers: [uuid])
        if let target = known.first {
            connect(peripheral: target, name: target.name)
        } else {
            startScanning()
        }
    }

    public func disconnect() {
        if let peripheral = connectedPrinter {
            centralManager.cancelPeripheralConnection(peripheral)
        }
        connectedPrinter = nil
        connectedPrinterName = nil
        isConnected = false
        targetCharacteristic = nil
    }

    // MARK: - CBCentralManagerDelegate

    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            if let saved = DataStore.shared.settings.bluetoothPrinterUUID {
                autoConnectSavedPrinter(savedUUIDString: saved)
            } else {
                startScanning()
            }
        } else {
            isConnected = false
            connectedPrinter = nil
            targetCharacteristic = nil
        }
    }

    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        let name = peripheral.name ?? advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? "Imprimante BLE"
        if !discoveredPrinters.contains(where: { $0.peripheral.identifier == peripheral.identifier }) {
            let device = BluetoothPrinterDevice(id: peripheral.identifier, name: name, peripheral: peripheral)
            discoveredPrinters.append(device)
        }

        if let savedUUID = DataStore.shared.settings.bluetoothPrinterUUID, peripheral.identifier.uuidString == savedUUID, !isConnected {
            connect(peripheral: peripheral, name: name)
        }
    }

    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        DispatchQueue.main.async {
            self.isConnected = true
        }
        peripheral.discoverServices(nil)
    }

    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        DispatchQueue.main.async {
            self.isConnected = false
            self.targetCharacteristic = nil
        }

        // Auto-reconnect if it was our saved printer
        if let savedUUID = DataStore.shared.settings.bluetoothPrinterUUID, peripheral.identifier.uuidString == savedUUID {
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
                self?.autoConnectSavedPrinter(savedUUIDString: savedUUID)
            }
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let services = peripheral.services else { return }
        for service in services {
            peripheral.discoverCharacteristics(nil, for: service)
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let characteristics = service.characteristics else { return }
        for characteristic in characteristics {
            if characteristic.properties.contains(.write) || characteristic.properties.contains(.writeWithoutResponse) {
                targetCharacteristic = characteristic
                if let data = pendingPrintData, let completion = pendingPrintCompletion {
                    pendingPrintData = nil
                    pendingPrintCompletion = nil
                    sendData(data, completion: completion)
                }
                break
            }
        }
    }

    // MARK: - Test Printer

    public func testPrinter(settings: ShopSettings, completion: @escaping (Result<Void, Error>) -> Void) {
        var data = Data()
        data.append(contentsOf: [0x1B, 0x40]) // ESC @
        data.append(contentsOf: [0x1B, 0x61, 0x01]) // Center
        data.append(contentsOf: [0x1B, 0x45, 0x01]) // Bold
        data.append(contentsOf: [0x1B, 0x21, 0x10]) // Double height
        data.append(cleanData("\(settings.shopName.isEmpty ? "SYPOS COMMERCE" : settings.shopName)\n"))
        data.append(contentsOf: [0x1B, 0x21, 0x00])
        data.append(contentsOf: [0x1B, 0x45, 0x00])
        data.append(cleanData("--------------------------------\n"))
        data.append(contentsOf: [0x1B, 0x45, 0x01])
        data.append(cleanData("TEST D'IMPRESSION REUSSI !\n"))
        data.append(contentsOf: [0x1B, 0x45, 0x00])
        data.append(cleanData("SYPOS Mobile iOS — Version Pro\n"))
        let formatter = DateFormatter()
        formatter.dateFormat = "dd/MM/yyyy HH:mm:ss"
        data.append(cleanData("Date : \(formatter.string(from: Date()))\n"))
        data.append(cleanData("--------------------------------\n"))
        data.append(cleanData("Imprimante connectee et prete.\n"))
        data.append(contentsOf: [0x1B, 0x64, 0x03])
        data.append(contentsOf: [0x1D, 0x56, 0x41, 0x00])

        sendData(data, completion: completion)
    }

    // MARK: - Printing ESC/POS Methods

    public func printTicket(ticket: Ticket, customerName: String?, settings: ShopSettings, completion: @escaping (Result<Void, Error>) -> Void) {
        var data = Data()

        // Init
        data.append(contentsOf: [0x1B, 0x40]) // ESC @

        // Header Centered
        data.append(contentsOf: [0x1B, 0x61, 0x01]) // Align Center
        data.append(contentsOf: [0x1B, 0x45, 0x01]) // Bold ON
        data.append(contentsOf: [0x1B, 0x21, 0x10]) // Double height
        data.append(cleanData("\(settings.shopName.isEmpty ? "SYPOS COMMERCE" : settings.shopName)\n"))
        data.append(contentsOf: [0x1B, 0x21, 0x00]) // Normal size
        data.append(contentsOf: [0x1B, 0x45, 0x00]) // Bold OFF

        if !settings.shopAddress.isEmpty {
            data.append(cleanData("\(settings.shopAddress)\n"))
        }
        if !settings.shopPhone.isEmpty {
            data.append(cleanData("Tel: \(settings.shopPhone)\n"))
        }

        data.append(cleanData("--------------------------------\n"))

        // Ticket Details Left Aligned
        data.append(contentsOf: [0x1B, 0x61, 0x00]) // Align Left
        let formatter = DateFormatter()
        formatter.dateFormat = "dd/MM/yyyy HH:mm"
        data.append(cleanData("Ticket N: \(ticket.ticketNumber)\n"))
        data.append(cleanData("Date    : \(formatter.string(from: ticket.date))\n"))

        let seller = ticket.sellerName ?? settings.sellerName
        if !seller.isEmpty {
            data.append(cleanData("Vendeur : \(seller)\n"))
        }
        if let customer = customerName {
            data.append(cleanData("Client  : \(customer)\n"))
        }

        if settings.businessMode == .restaurant {
            data.append(cleanData("Type    : \(ticket.orderType.displayName.uppercased())\n"))
        }

        data.append(cleanData("--------------------------------\n"))
        data.append(cleanData(formatTwoColumns("QTE / ARTICLE", "MONTANT", width: 32) + "\n"))
        data.append(cleanData("--------------------------------\n"))

        // Items
        for item in ticket.items {
            let left = "\(item.quantity)x \(item.productName)"
            let right = formatMoney(item.total)
            if left.count + right.count + 1 <= 32 {
                data.append(cleanData(formatTwoColumns(left, right, width: 32) + "\n"))
            } else {
                data.append(cleanData("\(left)\n"))
                let unit = "  @\(formatMoney(item.unitPrice))"
                data.append(cleanData(formatTwoColumns(unit, right, width: 32) + "\n"))
            }
        }

        data.append(cleanData("--------------------------------\n"))

        if ticket.discount > 0 {
            data.append(cleanData(formatTwoColumns("Sous-Total", formatMoney(ticket.subTotal), width: 32) + "\n"))
            data.append(cleanData(formatTwoColumns("Remise", "-\(formatMoney(ticket.discount))", width: 32) + "\n"))
        }

        if settings.taxEnabled && ticket.taxAmount > 0 {
            let net = ticket.totalAmount - ticket.taxAmount
            data.append(cleanData(formatTwoColumns("Total HT", formatMoney(net), width: 32) + "\n"))
            data.append(cleanData(formatTwoColumns("TVA (\(Int(settings.taxRatePercent))%)", formatMoney(ticket.taxAmount), width: 32) + "\n"))
        }

        data.append(contentsOf: [0x1B, 0x45, 0x01]) // Bold ON
        data.append(contentsOf: [0x1B, 0x21, 0x10]) // Double height
        data.append(cleanData(formatTwoColumns("TOTAL TTC", formatMoney(ticket.totalAmount), width: 32) + "\n"))
        data.append(contentsOf: [0x1B, 0x21, 0x00]) // Normal size
        data.append(contentsOf: [0x1B, 0x45, 0x00]) // Bold OFF

        if let method = ticket.paymentMethod {
            data.append(cleanData(formatTwoColumns("Paiement", method.displayName, width: 32) + "\n"))
            if method == .cash && ticket.amountPaid > 0 {
                data.append(cleanData(formatTwoColumns("Montant Recu", formatMoney(ticket.amountPaid), width: 32) + "\n"))
                if ticket.changeReturned > 0 {
                    data.append(cleanData(formatTwoColumns("Monnaie Rendue", formatMoney(ticket.changeReturned), width: 32) + "\n"))
                }
            }
        }

        data.append(cleanData("--------------------------------\n"))

        // Footer Centered
        data.append(contentsOf: [0x1B, 0x61, 0x01]) // Align Center
        if !settings.receiptFooter.isEmpty {
            data.append(cleanData("\(settings.receiptFooter)\n"))
        }

        // Publisher Signature
        if settings.showPublisherSignature {
            data.append(contentsOf: [0x1B, 0x21, 0x01]) // Font B small
            data.append(cleanData("\(settings.publisherSignatureText)\n"))
            data.append(contentsOf: [0x1B, 0x21, 0x00]) // Normal
        }

        data.append(contentsOf: [0x1B, 0x64, 0x03]) // Feed paper
        data.append(contentsOf: [0x1D, 0x56, 0x41, 0x00]) // Cut paper

        sendData(data, completion: completion)
    }

    // MARK: - Print Z-Report (Clôture de Caisse)

    public func printZReport(zData: ZReportSummary, settings: ShopSettings, completion: @escaping (Result<Void, Error>) -> Void) {
        var data = Data()

        data.append(contentsOf: [0x1B, 0x40]) // ESC @
        data.append(contentsOf: [0x1B, 0x61, 0x01]) // Center
        data.append(contentsOf: [0x1B, 0x45, 0x01]) // Bold
        data.append(contentsOf: [0x1B, 0x21, 0x10]) // Double height
        data.append(cleanData("RAPPORT Z DE CAISSE\n"))
        data.append(contentsOf: [0x1B, 0x21, 0x00])
        data.append(cleanData("\(settings.shopName.isEmpty ? "SYPOS COMMERCE" : settings.shopName)\n"))
        data.append(contentsOf: [0x1B, 0x45, 0x00])

        if !settings.shopPhone.isEmpty {
            data.append(cleanData("Tel: \(settings.shopPhone)\n"))
        }
        data.append(cleanData("Periode : \(zData.dateText)\n"))
        data.append(cleanData("--------------------------------\n"))

        data.append(contentsOf: [0x1B, 0x61, 0x00]) // Align Left
        data.append(contentsOf: [0x1B, 0x45, 0x01])
        data.append(cleanData(formatTwoColumns("TOTAL VENTES", formatMoney(zData.totalSales), width: 32) + "\n"))
        data.append(contentsOf: [0x1B, 0x45, 0x00])
        data.append(cleanData(formatTwoColumns("Nombre de tickets", "\(zData.ticketsCount)", width: 32) + "\n"))
        data.append(cleanData(formatTwoColumns("Total Depenses", "-\(formatMoney(zData.totalExpenses))", width: 32) + "\n"))

        data.append(cleanData("--------------------------------\n"))
        data.append(contentsOf: [0x1B, 0x45, 0x01])
        data.append(cleanData("VENTILATION DES ENCAISSEMENTS\n"))
        data.append(contentsOf: [0x1B, 0x45, 0x00])
        data.append(cleanData("--------------------------------\n"))

        data.append(cleanData(formatTwoColumns("Especes (Cash)", formatMoney(zData.cashSales), width: 32) + "\n"))
        data.append(cleanData(formatTwoColumns("Wave Money", formatMoney(zData.waveSales), width: 32) + "\n"))
        data.append(cleanData(formatTwoColumns("Orange Money", formatMoney(zData.orangeMoneySales), width: 32) + "\n"))
        data.append(cleanData(formatTwoColumns("MTN MoMo", formatMoney(zData.mtnSales), width: 32) + "\n"))
        data.append(cleanData(formatTwoColumns("Moov Money", formatMoney(zData.moovSales), width: 32) + "\n"))
        data.append(cleanData(formatTwoColumns("Carte Bancaire", formatMoney(zData.cardSales), width: 32) + "\n"))
        data.append(cleanData(formatTwoColumns("Ventes a Credit", formatMoney(zData.creditSales), width: 32) + "\n"))

        data.append(cleanData("================================\n"))
        data.append(contentsOf: [0x1B, 0x45, 0x01])
        data.append(contentsOf: [0x1B, 0x21, 0x10])
        data.append(cleanData(formatTwoColumns("CASH NET CAISSE", formatMoney(zData.netCashInDrawer), width: 32) + "\n"))
        data.append(contentsOf: [0x1B, 0x21, 0x00])
        data.append(contentsOf: [0x1B, 0x45, 0x00])
        data.append(cleanData("================================\n"))

        data.append(contentsOf: [0x1B, 0x61, 0x01]) // Center
        let nowFormatter = DateFormatter()
        nowFormatter.dateFormat = "dd/MM/yyyy HH:mm"
        data.append(cleanData("Edite le : \(nowFormatter.string(from: Date()))\n"))
        if settings.showPublisherSignature {
            data.append(contentsOf: [0x1B, 0x21, 0x01])
            data.append(cleanData("\(settings.publisherSignatureText)\n"))
            data.append(contentsOf: [0x1B, 0x21, 0x00])
        }

        data.append(contentsOf: [0x1B, 0x64, 0x03])
        data.append(contentsOf: [0x1D, 0x56, 0x41, 0x00])

        sendData(data, completion: completion)
    }

    // MARK: - Print Delivery Note (Bordereau de Livraison)

    public func printDeliveryNote(data note: DeliveryNoteData, settings: ShopSettings, completion: @escaping (Result<Void, Error>) -> Void) {
        var printData = Data()

        printData.append(contentsOf: [0x1B, 0x40]) // ESC @
        printData.append(contentsOf: [0x1B, 0x61, 0x01]) // Center
        printData.append(contentsOf: [0x1B, 0x45, 0x01])
        printData.append(contentsOf: [0x1B, 0x21, 0x10])
        printData.append(cleanData("BON DE LIVRAISON\n"))
        printData.append(contentsOf: [0x1B, 0x21, 0x00])
        printData.append(cleanData("\(settings.shopName.isEmpty ? "SYPOS COMMERCE" : settings.shopName)\n"))
        printData.append(contentsOf: [0x1B, 0x45, 0x00])

        if !settings.shopPhone.isEmpty {
            printData.append(cleanData("Tel Boutique: \(settings.shopPhone)\n"))
        }

        printData.append(cleanData("--------------------------------\n"))
        printData.append(contentsOf: [0x1B, 0x61, 0x00]) // Left
        let formatter = DateFormatter()
        formatter.dateFormat = "dd/MM/yyyy HH:mm"
        printData.append(cleanData("Date        : \(formatter.string(from: Date()))\n"))
        printData.append(cleanData("Destinataire: \(note.recipientName)\n"))
        printData.append(cleanData("Contact     : \(note.recipientPhone)\n"))
        printData.append(cleanData("Adresse     : \(note.deliveryAddress)\n"))

        printData.append(cleanData("--------------------------------\n"))
        printData.append(contentsOf: [0x1B, 0x45, 0x01])
        printData.append(cleanData("CONTENU DU COLIS :\n"))
        printData.append(contentsOf: [0x1B, 0x45, 0x00])
        printData.append(cleanData("\(note.itemsSummary)\n"))

        if let extraNote = note.note, !extraNote.isEmpty {
            printData.append(cleanData("Note: \(extraNote)\n"))
        }

        printData.append(cleanData("--------------------------------\n"))
        printData.append(cleanData(formatTwoColumns("Frais Livraison", formatMoney(note.deliveryFee), width: 32) + "\n"))
        printData.append(contentsOf: [0x1B, 0x45, 0x01])
        printData.append(contentsOf: [0x1B, 0x21, 0x10])
        printData.append(cleanData(formatTwoColumns("NET A ENCAISSER", formatMoney(note.amountToCollect + note.deliveryFee), width: 32) + "\n"))
        printData.append(contentsOf: [0x1B, 0x21, 0x00])
        printData.append(contentsOf: [0x1B, 0x45, 0x00])

        printData.append(cleanData("--------------------------------\n"))
        printData.append(contentsOf: [0x1B, 0x61, 0x01]) // Center
        printData.append(cleanData("Signature du Client :\n\n\n"))
        printData.append(cleanData("...............................\n"))

        if settings.showPublisherSignature {
            printData.append(contentsOf: [0x1B, 0x21, 0x01])
            printData.append(cleanData("\(settings.publisherSignatureText)\n"))
            data.append(contentsOf: [0x1B, 0x21, 0x00])
        }

        printData.append(contentsOf: [0x1B, 0x64, 0x03])
        printData.append(contentsOf: [0x1D, 0x56, 0x41, 0x00])

        sendData(printData, completion: completion)
    }

    // MARK: - Print Barcode Label

    public func printBarcodeLabel(productName: String, price: Double, barcode: String?, settings: ShopSettings, options: LabelPrintOptions, completion: @escaping (Result<Void, Error>) -> Void) {
        var data = Data()

        data.append(contentsOf: [0x1B, 0x40]) // ESC @
        data.append(contentsOf: [0x1B, 0x61, 0x01]) // Center

        if options.printShopHeader && !settings.shopName.isEmpty {
            data.append(contentsOf: [0x1B, 0x45, 0x01])
            data.append(cleanData("\(settings.shopName.uppercased())\n"))
            data.append(contentsOf: [0x1B, 0x45, 0x00])
            data.append(cleanData("--------------------------------\n"))
        }

        if options.printProductName && !productName.isEmpty {
            data.append(contentsOf: [0x1B, 0x45, 0x01])
            data.append(contentsOf: [0x1B, 0x21, 0x10])
            data.append(cleanData("\(productName)\n"))
            data.append(contentsOf: [0x1B, 0x21, 0x00])
            data.append(contentsOf: [0x1B, 0x45, 0x00])
        }

        if options.printPrice && price > 0 {
            data.append(contentsOf: [0x1B, 0x45, 0x01])
            data.append(contentsOf: [0x1B, 0x21, 0x30])
            data.append(cleanData("\(formatMoney(price))\n"))
            data.append(contentsOf: [0x1B, 0x21, 0x00])
            data.append(contentsOf: [0x1B, 0x45, 0x00])
        }

        let code = barcode?.trimmingCharacters(in: .whitespacesAndNewlines)
        if options.printBarcodeRaster, let code = code, !code.isEmpty {
            if options.printProductName || options.printPrice {
                data.append(cleanData("--------------------------------\n"))
            }
            let raster = generateBarcodeRaster(data: code, width: 384, height: 110)
            data.append(raster)
        }

        let serial = options.serialNumber?.trimmingCharacters(in: .whitespacesAndNewlines) ?? code
        if options.printSerialNumber, let serial = serial, !serial.isEmpty {
            data.append(contentsOf: [0x1B, 0x45, 0x01])
            data.append(cleanData("\n* \(serial) *\n"))
            data.append(contentsOf: [0x1B, 0x45, 0x00])
        }

        data.append(contentsOf: [0x1B, 0x64, 0x03])
        data.append(contentsOf: [0x1D, 0x56, 0x41, 0x00])

        sendData(data, completion: completion)
    }

    private func sendData(_ data: Data, completion: @escaping (Result<Void, Error>) -> Void) {
        guard let peripheral = connectedPrinter, let characteristic = targetCharacteristic else {
            // Attempt auto-reconnect if saved
            if let saved = DataStore.shared.settings.bluetoothPrinterUUID {
                pendingPrintData = data
                pendingPrintCompletion = completion
                autoConnectSavedPrinter(savedUUIDString: saved)
                // Timeout if reconnect fails
                DispatchQueue.main.asyncAfter(deadline: .now() + 4.0) { [weak self] in
                    if self?.pendingPrintData != nil {
                        self?.pendingPrintData = nil
                        self?.pendingPrintCompletion = nil
                        completion(.failure(NSError(domain: "SYPOS", code: -1, userInfo: [NSLocalizedDescriptionKey: "Impossible de joindre l'imprimante Bluetooth. Veuillez vérifier qu'elle est allumée."])))
                    }
                }
                return
            }

            completion(.failure(NSError(domain: "SYPOS", code: -1, userInfo: [NSLocalizedDescriptionKey: "Aucune imprimante Bluetooth connectée. Rendez-vous dans Paramètres pour connecter votre imprimante."])))
            return
        }

        // Send in chunks of 100 bytes for BLE reliability
        DispatchQueue.global(qos: .userInitiated).async {
            let chunkSize = 100
            var offset = 0
            while offset < data.count {
                let chunk = data.subdata(in: offset..<min(offset + chunkSize, data.count))
                peripheral.writeValue(chunk, for: characteristic, type: .withoutResponse)
                offset += chunkSize
                Thread.sleep(forTimeInterval: 0.015)
            }
            DispatchQueue.main.async {
                completion(.success(()))
            }
        }
    }

    // MARK: - Helper Text Formatter (Zero question marks on receipts)

    private func cleanData(_ text: String) -> Data {
        let noAccents = text.folding(options: .diacriticInsensitive, locale: .current)
        let sanitized = noAccents
            .replacingOccurrences(of: "\u{00A0}", with: " ")
            .replacingOccurrences(of: "\u{202F}", with: " ")
            .replacingOccurrences(of: "\u{2007}", with: " ")
            .replacingOccurrences(of: "\u{2009}", with: " ")
            .replacingOccurrences(of: "’", with: "'")
            .replacingOccurrences(of: "‘", with: "'")
            .replacingOccurrences(of: "“", with: "\"")
            .replacingOccurrences(of: "”", with: "\"")
            .replacingOccurrences(of: "–", with: "-")
            .replacingOccurrences(of: "—", with: "-")
            .replacingOccurrences(of: "…", with: "...")
            .replacingOccurrences(of: "•", with: "-")

        var asciiBytes = [UInt8]()
        for char in sanitized.utf8 {
            if char >= 32 && char <= 126 || char == 10 || char == 13 || char == 9 {
                asciiBytes.append(char)
            } else {
                asciiBytes.append(32) // space
            }
        }
        return Data(asciiBytes)
    }

    private func formatMoney(_ amount: Double) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = " "
        formatter.maximumFractionDigits = 0
        let str = formatter.string(from: NSNumber(value: amount)) ?? "\(Int(amount))"
        return "\(str) CFA"
    }

    private func formatTwoColumns(_ left: String, _ right: String, width: Int) -> String {
        let spaces = max(1, width - left.count - right.count)
        return left + String(repeating: " ", count: spaces) + right
    }

    private func generateBarcodeRaster(data: String, width: Int, height: Int) -> Data {
        guard let filter = CIFilter(name: "CICode128BarcodeGenerator") else { return Data() }
        filter.setValue(data.data(using: .ascii), forKey: "inputMessage")
        guard let ciImage = filter.outputImage else { return Data() }

        let context = CIContext()
        guard let cgImage = context.createCGImage(ciImage, from: ciImage.extent) else { return Data() }

        let bytesPerLine = (width + 7) / 8
        var rasterData = Data()
        rasterData.append(contentsOf: [0x1D, 0x76, 0x30, 0x00])
        rasterData.append(UInt8(bytesPerLine & 0xFF))
        rasterData.append(UInt8((bytesPerLine >> 8) & 0xFF))
        rasterData.append(UInt8(height & 0xFF))
        rasterData.append(UInt8((height >> 8) & 0xFF))

        let colorSpace = CGColorSpaceCreateDeviceGray()
        var rawData = [UInt8](repeating: 255, count: width * height)
        let bitmapContext = CGContext(data: &rawData, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width, space: colorSpace, bitmapInfo: CGImageAlphaInfo.none.rawValue)
        bitmapContext?.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        for y in 0..<height {
            var currentByte: UInt8 = 0
            var bitIndex = 0
            for x in 0..<width {
                let pixel = rawData[y * width + x]
                if pixel < 128 { // Black pixel
                    currentByte |= (1 << (7 - bitIndex))
                }
                bitIndex += 1
                if bitIndex == 8 {
                    rasterData.append(currentByte)
                    currentByte = 0
                    bitIndex = 0
                }
            }
            if bitIndex > 0 {
                rasterData.append(currentByte)
            }
        }

        return rasterData
    }
}
