import SwiftUI

public struct PinAuthSheetView: View {
    public var title: String
    public var correctPin: String
    public var onSuccess: () -> Void
    @Environment(\.presentationMode) var presentationMode

    @State private var enteredPin: String = ""
    @State private var errorMessage: String? = nil

    public init(title: String = "Autorisation Requise", correctPin: String, onSuccess: @escaping () -> Void) {
        self.title = title
        self.correctPin = correctPin
        self.onSuccess = onSuccess
    }

    public var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                VStack(spacing: 8) {
                    Image(systemName: "lock.shield.fill")
                        .font(.system(size: 50))
                        .foregroundColor(.blue)

                    Text(title)
                        .font(.headline)
                        .multilineTextAlignment(.center)

                    Text("Entrez le code PIN Admin pour valider cette action")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding(.top, 20)

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

                // Numeric Keypad
                VStack(spacing: 12) {
                    ForEach([[1, 2, 3], [4, 5, 6], [7, 8, 9]], id: \.self) { row in
                        HStack(spacing: 24) {
                            ForEach(row, id: \.self) { num in
                                Button(action: { numberTapped("\(num)") }) {
                                    Text("\(num)")
                                        .font(.title)
                                        .bold()
                                        .frame(width: 72, height: 72)
                                        .background(Color(.systemGray6))
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
                                .background(Color(.systemGray6))
                                .clipShape(Circle())
                        }

                        Button(action: { numberTapped("0") }) {
                            Text("0")
                                .font(.title)
                                .bold()
                                .frame(width: 72, height: 72)
                                .background(Color(.systemGray6))
                                .clipShape(Circle())
                        }

                        Button(action: { deleteTapped() }) {
                            Image(systemName: "delete.left.fill")
                                .font(.title2)
                                .foregroundColor(.secondary)
                                .frame(width: 72, height: 72)
                                .background(Color(.systemGray6))
                                .clipShape(Circle())
                        }
                    }
                }

                Spacer()
            }
            .padding()
            .navigationBarItems(leading: Button("Annuler") {
                presentationMode.wrappedValue.dismiss()
            })
        }
    }

    private func numberTapped(_ num: String) {
        if enteredPin.count < 6 {
            enteredPin.append(num)
            errorMessage = nil
            if enteredPin == correctPin {
                let generator = UINotificationFeedbackGenerator()
                generator.notificationOccurred(.success)
                presentationMode.wrappedValue.dismiss()
                onSuccess()
            } else if enteredPin.count == correctPin.count {
                let generator = UINotificationFeedbackGenerator()
                generator.notificationOccurred(.error)
                errorMessage = "Code PIN incorrect !"
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    enteredPin = ""
                }
            }
        }
    }

    private func deleteTapped() {
        if !enteredPin.isEmpty {
            enteredPin.removeLast()
        }
    }
}
