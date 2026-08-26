import SwiftUI

public struct LicenseActivationView: View {
    @ObservedObject var dataStore: DataStore
    @State private var licenseKeyInput: String = ""
    @State private var errorMessage: String? = nil
    @State private var showCopiedAlert: Bool = false

    private var deviceId: String {
        LicenseManager.getDeviceId()
    }

    public var body: some View {
        ZStack {
            Color(.systemGroupedBackground).ignoresSafeArea()

            ScrollView {
                VStack(spacing: 20) {
                    Spacer().frame(height: 20)

                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 64))
                        .foregroundColor(.blue)

                    Text("Activation SYPOS Mobile")
                        .font(.title2)
                        .bold()

                    Text("Protection Cryptographique Sécurisée de Caisse")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)

                    // Device ID Box
                    VStack(spacing: 12) {
                        HStack {
                            Text("📱 ID UNIQUE DE CET APPAREIL")
                                .font(.caption2)
                                .bold()
                                .foregroundColor(.blue)
                            Spacer()
                        }

                        Text(deviceId)
                            .font(.system(.body, design: .monospaced))
                            .bold()
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(Color(.systemGray6))
                            .cornerRadius(8)

                        HStack(spacing: 12) {
                            Button(action: {
                                UIPasteboard.general.string = deviceId
                                showCopiedAlert = true
                            }) {
                                Label("Copier l'ID", systemImage: "doc.on.doc")
                                    .font(.subheadline)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                            }
                            .buttonStyle(.bordered)

                            Button(action: {
                                let msg = "Bonjour SYPOS, voici mon ID Appareil pour activer ma licence :\n\n📱 *ID :* \(deviceId)\n🏪 *Boutique :* \(dataStore.settings.shopName)"
                                if let encoded = msg.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                                   let url = URL(string: "https://api.whatsapp.com/send?text=\(encoded)") {
                                    UIApplication.shared.open(url)
                                }
                            }) {
                                Label("WhatsApp", systemImage: "message.fill")
                                    .font(.subheadline)
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                                    .background(Color.green)
                                    .cornerRadius(8)
                            }
                        }
                    }
                    .padding()
                    .background(Color(.systemBackground))
                    .cornerRadius(16)
                    .padding(.horizontal, 20)

                    // License Input Box
                    VStack(alignment: .leading, spacing: 14) {
                        Text("Saisie de la Clé Officielle :")
                            .font(.caption)
                            .bold()
                            .foregroundColor(.secondary)

                        TextField("Collez votre clé SYP1...", text: $licenseKeyInput)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .autocapitalization(.none)
                            .disableAutocorrection(true)

                        if let error = errorMessage {
                            Text(error)
                                .font(.caption)
                                .foregroundColor(.red)
                                .bold()
                        }
                    }
                    .padding()
                    .background(Color(.systemBackground))
                    .cornerRadius(16)
                    .padding(.horizontal, 20)

                    Button(action: activate) {
                        Text("Activer & Démarrer")
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(Color.blue)
                            .cornerRadius(12)
                    }
                    .padding(.horizontal, 20)

                    Spacer().frame(height: 30)
                }
            }
        }
        .alert(isPresented: $showCopiedAlert) {
            Alert(title: Text("Copié !"), message: Text("ID Appareil copié dans le presse-papier."), dismissButton: .default(Text("OK")))
        }
    }

    private func activate() {
        let status = LicenseManager.validateKey(licenseKeyInput)
        if status.isValid {
            dataStore.settings.isLicensed = true
            dataStore.settings.licenseKey = licenseKeyInput.trimmingCharacters(in: .whitespacesAndNewlines)
            dataStore.settings.licenseType = status.licenseType
            dataStore.settings.licenseExpiryDate = status.expiryDate
            dataStore.saveAll()
        } else {
            errorMessage = status.message
        }
    }
}

public struct PinLockView: View {
    @ObservedObject var dataStore: DataStore
    public var onUnlocked: (UserRole) -> Void

    @State private var enteredPin: String = ""
    @State private var errorMessage: String? = nil

    public var body: some View {
        ZStack {
            Color(.systemGroupedBackground).ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer()

                VStack(spacing: 8) {
                    Image(systemName: "lock.circle.fill")
                        .font(.system(size: 60))
                        .foregroundColor(.blue)

                    Text(dataStore.settings.shopName.isEmpty ? "SYPOS MOBILE" : dataStore.settings.shopName)
                        .font(.title2)
                        .bold()

                    Text("Entrez votre code PIN Caissier ou Admin")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                // PIN Dots
                HStack(spacing: 16) {
                    ForEach(0..<4) { index in
                        Circle()
                            .fill(index < enteredPin.count ? Color.blue : Color.gray.opacity(0.3))
                            .frame(width: 18, height: 18)
                    }
                }

                if let error = errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                        .bold()
                }

                // Keypad
                VStack(spacing: 12) {
                    ForEach([[1, 2, 3], [4, 5, 6], [7, 8, 9]], id: \.self) { row in
                        HStack(spacing: 24) {
                            ForEach(row, id: \.self) { num in
                                Button(action: { numberTapped("\(num)") }) {
                                    Text("\(num)")
                                        .font(.title)
                                        .bold()
                                        .frame(width: 72, height: 72)
                                        .background(Color(.systemBackground))
                                        .clipShape(Circle())
                                }
                            }
                        }
                    }

                    HStack(spacing: 24) {
                        Button(action: { enteredPin = "" }) {
                            Text("C")
                                .font(.title2)
                                .bold()
                                .foregroundColor(.red)
                                .frame(width: 72, height: 72)
                                .background(Color(.systemBackground))
                                .clipShape(Circle())
                        }

                        Button(action: { numberTapped("0") }) {
                            Text("0")
                                .font(.title)
                                .bold()
                                .frame(width: 72, height: 72)
                                .background(Color(.systemBackground))
                                .clipShape(Circle())
                        }

                        Button(action: { if !enteredPin.isEmpty { enteredPin.removeLast() } }) {
                            Image(systemName: "delete.left.fill")
                                .font(.title2)
                                .foregroundColor(.secondary)
                                .frame(width: 72, height: 72)
                                .background(Color(.systemBackground))
                                .clipShape(Circle())
                        }
                    }
                }

                Spacer()
            }
            .padding()
        }
    }

    private func numberTapped(_ num: String) {
        if enteredPin.count < 6 {
            enteredPin.append(num)
            errorMessage = nil

            if enteredPin == dataStore.settings.adminPin {
                onUnlocked(.admin)
            } else if enteredPin == dataStore.settings.cashierPin {
                onUnlocked(.cashier)
            } else if enteredPin.count >= max(dataStore.settings.adminPin.count, dataStore.settings.cashierPin.count) {
                errorMessage = "Code PIN incorrect"
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    enteredPin = ""
                }
            }
        }
    }
}
