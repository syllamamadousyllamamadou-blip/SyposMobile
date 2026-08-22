package com.sypos.mobile.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sypos.mobile.data.local.UserRole
import com.sypos.mobile.ui.auth.PinAuthDialog
import com.sypos.mobile.ui.auth.PinLockScreen
import com.sypos.mobile.ui.customer.CustomerListScreen
import com.sypos.mobile.ui.history.HistoryScreen
import com.sypos.mobile.ui.pos.PosScreen
import com.sypos.mobile.ui.product.AddEditProductScreen
import com.sypos.mobile.ui.product.BarcodeScannerScreen
import com.sypos.mobile.ui.product.ProductListScreen
import com.sypos.mobile.ui.report.ReportScreen
import com.sypos.mobile.ui.settings.SettingsScreen
import com.sypos.mobile.ui.settings.SettingsViewModel
import com.sypos.mobile.ui.tools.ToolsScreen

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Pos : BottomNavItem("pos", "Caisse", Icons.Filled.PointOfSale, Icons.Outlined.PointOfSale)
    object Products : BottomNavItem("products", "Catalogue", Icons.Filled.Inventory2, Icons.Outlined.Inventory2)
    object History : BottomNavItem("history", "Ventes", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong)
    object Tools : BottomNavItem("tools", "Outils", Icons.Filled.Build, Icons.Outlined.Build)
    object Customers : BottomNavItem("customers", "Clients", Icons.Filled.People, Icons.Outlined.PeopleOutline)
    object Reports : BottomNavItem("reports", "Bilan", Icons.Filled.Assessment, Icons.Outlined.Assessment)
}

@Composable
fun SyposNavigation(
    navController: NavHostController = rememberNavController(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by settingsViewModel.settings.collectAsState()
    var isAppUnlocked by remember(settings.pinLockEnabled) { mutableStateOf(!settings.pinLockEnabled) }
    var pendingAdminRoute by remember { mutableStateOf<String?>(null) }
    var showAdminPinDialog by remember { mutableStateOf(false) }

    val needsLicenseActivation = !settings.isLicensed || com.sypos.mobile.util.LicenseManager.isLicenseExpired(settings.licenseExpiryDate)

    // If application needs initial license activation
    if (needsLicenseActivation) {
        com.sypos.mobile.ui.auth.LicenseDialog(
            settings = settings,
            isInitialSetup = true,
            onSaveLicense = { key, type, expiry ->
                settingsViewModel.saveLicense(key, type, expiry)
            }
        )
        return
    }

    // If PIN Lock is enabled and app is locked, display Lock Screen
    if (settings.pinLockEnabled && !isAppUnlocked) {
        PinLockScreen(
            settings = settings,
            onUnlock = { role ->
                settingsViewModel.setUserRole(if (role == com.sypos.mobile.ui.auth.UserRole.ADMIN) UserRole.ADMIN else UserRole.CASHIER)
                isAppUnlocked = true
            }
        )
        return
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        BottomNavItem.Pos,
        BottomNavItem.Products,
        BottomNavItem.History,
        BottomNavItem.Tools,
        BottomNavItem.Customers,
        BottomNavItem.Reports
    )

    val isBottomBarVisible = currentRoute in bottomNavItems.map { it.route }

    fun navigateSafely(targetRoute: String) {
        if (targetRoute == "reports" || targetRoute == "settings") {
            if (settings.pinLockEnabled && settings.currentUserRole == UserRole.CASHIER) {
                pendingAdminRoute = targetRoute
                showAdminPinDialog = true
                return
            }
        }

        if (currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (isBottomBarVisible) {
                NavigationBar(
                    tonalElevation = NavigationBarDefaults.Elevation
                ) {
                    bottomNavItems.forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { navigateSafely(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Pos.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab 1: Caisse POS
            composable(BottomNavItem.Pos.route) { backStackEntry ->
                val scannedBarcode by backStackEntry.savedStateHandle.getStateFlow<String?>("scanned_barcode", null).collectAsState()
                PosScreen(
                    onNavigateToScanner = { navController.navigate("scanner?target=pos") },
                    onNavigateToSettings = { navigateSafely("settings") },
                    onNavigateToAddProductWithBarcode = { unknownBarcode ->
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.set("scanned_barcode", unknownBarcode)
                        navController.navigate("addProduct")
                    },
                    onLockApp = { isAppUnlocked = false },
                    scannedBarcode = scannedBarcode,
                    onBarcodeProcessed = {
                        backStackEntry.savedStateHandle.remove<String>("scanned_barcode")
                    }
                )
            }

            // Tab 2: Catalogue & Stock
            composable(BottomNavItem.Products.route) { backStackEntry ->
                val scannedBarcode by backStackEntry.savedStateHandle.getStateFlow<String?>("scanned_barcode", null).collectAsState()
                ProductListScreen(
                    onAddProductClick = { navController.navigate("addProduct") },
                    onEditProductClick = { productId -> navController.navigate("editProduct/$productId") },
                    onNavigateToScanner = { navController.navigate("scanner?target=products") },
                    scannedBarcode = scannedBarcode
                )
            }

            // Tab 3: Historique des Ventes
            composable(BottomNavItem.History.route) {
                HistoryScreen(
                    onNavigateToScanner = { navController.navigate("scanner?target=history") }
                )
            }

            // Tab 4: Outils & Étiquettes
            composable(BottomNavItem.Tools.route) {
                ToolsScreen(
                    onNavigateToScanner = { navController.navigate("scanner?target=tools") }
                )
            }

            // Tab 5: Clients & Dettes
            composable(BottomNavItem.Customers.route) {
                CustomerListScreen()
            }

            // Tab 6: Bilan & Caisse
            composable(BottomNavItem.Reports.route) {
                ReportScreen()
            }

            // Settings
            composable("settings") {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Add Product
            composable("addProduct") { backStackEntry ->
                val scannedBarcode by backStackEntry.savedStateHandle.getStateFlow<String?>("scanned_barcode", null).collectAsState()
                AddEditProductScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToScanner = { navController.navigate("scanner?target=addProduct") },
                    scannedBarcode = scannedBarcode
                )
            }

            // Edit Product
            composable(
                route = "editProduct/{productId}",
                arguments = listOf(navArgument("productId") { type = NavType.StringType })
            ) { backStackEntry ->
                val productId = backStackEntry.arguments?.getString("productId")
                val scannedBarcode by backStackEntry.savedStateHandle.getStateFlow<String?>("scanned_barcode", null).collectAsState()
                AddEditProductScreen(
                    productId = productId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToScanner = { navController.navigate("scanner?target=editProduct") },
                    scannedBarcode = scannedBarcode
                )
            }

            // Barcode Scanner with target routing
            composable(
                route = "scanner?target={target}",
                arguments = listOf(navArgument("target") {
                    type = NavType.StringType
                    defaultValue = "pos"
                })
            ) {
                BarcodeScannerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onBarcodeScanned = { barcode ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("scanned_barcode", barcode)
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    if (showAdminPinDialog && pendingAdminRoute != null) {
        PinAuthDialog(
            title = "Code PIN Administrateur Requis",
            correctPin = settings.adminPin,
            onDismiss = {
                showAdminPinDialog = false
                pendingAdminRoute = null
            },
            onSuccess = {
                val route = pendingAdminRoute!!
                showAdminPinDialog = false
                pendingAdminRoute = null
                settingsViewModel.setUserRole(UserRole.ADMIN)
                navController.navigate(route)
            }
        )
    }
}
