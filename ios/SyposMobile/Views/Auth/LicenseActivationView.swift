import SwiftUI

public struct LicenseActivationView: View {
    @ObservedObject var dataStore: DataStore
    @State private var licenseKeyInput: String = ""
    @State private var errorMessage: String? = nil

    public var body: some View {
        ZStack {
            Color(.systemGroupedBackground).ignoresSafeArea()

            VStack(spacing: 20) {
                Spacer()

                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 70))
                    .foregroundColor(.blue)

                Text("Installation SYPOS Mobile")
                    .font(.title)
                    .bold()

                Text("Initialisation et activation de votre système de caisse professionnel pour iPhone")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)

                VStack(alignment: .leading, spacing: 14) {
                    Text("Options d'activation rapide :")
                        .font(.caption)
                        .bold()
                        .foregroundColor(.secondary)

                    HStack(spacing: 8) {
                        Button("Essai 30j") { licenseKeyInput = "SYPOS-TRIAL-30D" }
                            .buttonStyle(.bordered)
                        Button("1 An") { licenseKeyInput = "SYPOS-1AN-2026" }
                            .buttonStyle(.bordered)
                        Button("Illimité") { licenseKeyInput = "SYPOS-PRO-VIP-2026" }
                            .buttonStyle(.borderedProminent)
                    }

                    TextField("Saisissez votre clé de licence...", text: $licenseKeyInput)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .autocapitalization(.allCharacters)
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
                        .frame(height: 52)
                        .background(Color.blue)
                        .cornerRadius(12)
                }
                .padding(.horizontal, 20)

                Spacer()
            }
        }
    }

    private func activate() {
        let status = LicenseManager.validateKey(licenseKeyInput)
        if status.isValid {
            dataStore.settings.isLicensed = true
            dataStore.settings.licenseKey = licenseKeyInput
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
