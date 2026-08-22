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

public class BluetoothPrinterManager: NSObject, ObservableObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    public static let shared = BluetoothPrinterManager()

    @Published public var discoveredPrinters: [BluetoothPrinterDevice] = []
    @Published public var connectedPrinter: CBPeripheral?
    @Published public var isScanning = false
    @Published public var isConnected = false

    private var centralManager: CBCentralManager!
    private var targetCharacteristic: CBCharacteristic?

    // Common ESC/POS BLE Service UUIDs
    private let serviceUUIDs: [CBUUID] = [
        CBUUID(string: "E7810A71-73AE-499D-8C15-FAA9AEF0C3F2"),
        CBUUID(string: "49535343-FE7D-4AE5-8FA9-9FAFD205E455"),
        CBUUID(string: "18F0"),
        CBUUID(string: "FF00")
    ]

    public override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
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
        connectedPrinter = device.peripheral
        device.peripheral.delegate = self
        centralManager.connect(device.peripheral, options: nil)
    }

    public func disconnect() {
        if let peripheral = connectedPrinter {
            centralManager.cancelPeripheralConnection(peripheral)
        }
    }

    // MARK: - CBCentralManagerDelegate

    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            startScanning()
        }
    }

    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        let name = peripheral.name ?? advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? "Imprimante BLE"
        if !discoveredPrinters.contains(where: { $0.peripheral.identifier == peripheral.identifier }) {
            let device = BluetoothPrinterDevice(name: name, peripheral: peripheral)
            discoveredPrinters.append(device)
        }
    }

    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        isConnected = true
        peripheral.discoverServices(nil)
    }

    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        isConnected = false
        targetCharacteristic = nil
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
                break
            }
        }
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

        // Publisher Signature in small compact font
        if settings.showPublisherSignature {
            data.append(contentsOf: [0x1B, 0x21, 0x01]) // Font B small
            data.append(cleanData("Solution: SYPOS MOBILE 0758245530\n"))
            data.append(contentsOf: [0x1B, 0x21, 0x00]) // Normal
        }

        data.append(contentsOf: [0x1B, 0x64, 0x03]) // Feed paper
        data.append(contentsOf: [0x1D, 0x56, 0x41, 0x00]) // Cut paper

        sendData(data, completion: completion)
    }

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
            completion(.failure(NSError(domain: "SYPOS", code: -1, userInfo: [NSLocalizedDescriptionKey: "Aucune imprimante Bluetooth connectée"])))
            return
        }

        // Send in chunks of 100 bytes for BLE reliability
        let chunkSize = 100
        var offset = 0
        while offset < data.count {
            let chunk = data.subdata(in: offset..<min(offset + chunkSize, data.count))
            peripheral.writeValue(chunk, for: characteristic, type: .withoutResponse)
            offset += chunkSize
            Thread.sleep(forTimeInterval: 0.01)
        }

        completion(.success(()))
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
        // Generates monochrome GS v 0 raster bitmap
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

        // Create bitmap context
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
