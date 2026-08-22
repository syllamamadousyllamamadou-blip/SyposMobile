import SwiftUI

public struct ContentView: View {
    @StateObject private var dataStore = DataStore.shared
    @State private var isAppUnlocked: Bool = false
    @State private var showAdminPinDialog: Bool = false
    @State private var selectedTab: Int = 0

    public var body: some View {
        Group {
            // 1. Initial License Activation check
            if !dataStore.settings.isLicensed || LicenseManager.isLicenseExpired(expiryTimestamp: dataStore.settings.licenseExpiryDate) {
                LicenseActivationView(dataStore: dataStore)
            }
            // 2. PIN Lock check
            else if dataStore.settings.pinLockEnabled && !isAppUnlocked {
                PinLockView(dataStore: dataStore) { role in
                    dataStore.currentUserRole = role
                    isAppUnlocked = true
                }
            }
            // 3. Main Application TabView
            else {
                TabView(selection: $selectedTab) {
                    PosView(dataStore: dataStore)
                        .tabItem {
                            Label("Caisse", systemImage: "cart.fill")
                        }
                        .tag(0)

                    ProductListView(dataStore: dataStore)
                        .tabItem {
                            Label("Catalogue", systemImage: "cube.box.fill")
                        }
                        .tag(1)

                    HistoryView(dataStore: dataStore)
                        .tabItem {
                            Label("Historique", systemImage: "clock.arrow.circlepath")
                        }
                        .tag(2)

                    ReportView(dataStore: dataStore)
                        .tabItem {
                            Label("Bilan", systemImage: "chart.bar.xaxis")
                        }
                        .tag(3)

                    CustomerListView(dataStore: dataStore)
                        .tabItem {
                            Label("Clients", systemImage: "person.2.fill")
                        }
                        .tag(4)

                    ToolsView(dataStore: dataStore)
                        .tabItem {
                            Label("Outils", systemImage: "wrench.and.screwdriver.fill")
                        }
                        .tag(5)

                    SettingsView(dataStore: dataStore)
                        .tabItem {
                            Label("Paramètres", systemImage: "gearshape.fill")
                        }
                        .tag(6)
                }
                .accentColor(.blue)
            }
        }
        .onAppear {
            isAppUnlocked = !dataStore.settings.pinLockEnabled
        }
    }
}
