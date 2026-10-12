package com.sypos.mobile.ui.navigation

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.sypos.mobile.ui.auth.AuthScreen
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
import com.google.firebase.auth.FirebaseAuth
import com.sypos.mobile.data.remote.CloudAuthManager
import kotlinx.coroutines.launch

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Pos : BottomNavItem("pos", "Caisse", Icons.Filled.PointOfSale, Icons.Outlined.PointOfSale)
    object Products : BottomNavItem("products", "Articles", Icons.Filled.Inventory2, Icons.Outlined.Inventory2)
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isCheckingAuth by remember { mutableStateOf(true) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthenticated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            isCheckingAuth = false
            isAuthenticated = false
        } else {
            val status = CloudAuthManager().verifyAndRegisterDevice(context, null, null)
            if (status.isValid) {
                isAuthenticated = true
                if (status.shopId.isNotBlank()) {
                    settingsViewModel.setCurrentShopId(status.shopId)
                    settingsViewModel.startCloudSync(status.shopId)
                }
                
                // Real-time listener for blocking and expiry
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(user.uid)
                    .addSnapshotListener { snapshot, error -> 
                        if (error == null && snapshot != null) {
                            val plan = snapshot.getString("plan")
                            val expiry = snapshot.getLong("expiry") ?: 0L
                            
                            if (plan == "BLOCKED") {
                                isAuthenticated = false
                                authError = "Accès révoqué ou bloqué par l'administrateur. Veuillez contacter KAYO au +2250758245530."
                                FirebaseAuth.getInstance().signOut()
                            } else if (plan != "LIFETIME" && expiry > 0 && System.currentTimeMillis() > expiry) {
                                isAuthenticated = false
                                authError = "Votre abonnement (ou période d'essai) a expiré. Veuillez contacter le développeur (KAYO) au +2250758245530 pour le renouveler."
                                FirebaseAuth.getInstance().signOut()
                            }
                        }
                    }
            } else {
                authError = status.message
            }
            isCheckingAuth = false
        }
    }

    if (isCheckingAuth) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!isAuthenticated) {
        AuthScreen(onAuthSuccess = { shopName, phone ->
            isCheckingAuth = true
            coroutineScope.launch {
                val status = CloudAuthManager().verifyAndRegisterDevice(context, shopName, phone)
                if (status.isValid) {
                    isAuthenticated = true
                    authError = null
                    if (status.shopId.isNotBlank()) {
                        settingsViewModel.setCurrentShopId(status.shopId)
                        settingsViewModel.startCloudSync(status.shopId)
                    }
                } else {
                    authError = status.message
                }
                isCheckingAuth = false
            }
        })
        return
    }

    if (authError != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Abonnement / Accès KAYO", fontWeight = FontWeight.Bold) },
            text = { Text(authError!!) },
            confirmButton = {
                Button(onClick = { 
                    FirebaseAuth.getInstance().signOut()
                    isAuthenticated = false
                    authError = null
                }) {
                    Text("Se déconnecter")
                }
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

    val bottomNavItems = mutableListOf(
        BottomNavItem.Pos,
        BottomNavItem.Products,
        BottomNavItem.History,
        BottomNavItem.Tools,
        BottomNavItem.Customers,
        BottomNavItem.Reports
    ).apply {
        if (settings.currentUserRole == com.sypos.mobile.data.local.UserRole.CASHIER) {
            remove(BottomNavItem.Products) // Le vendeur ne doit pas avoir accès au catalogue
        }
    }

    val isBottomBarVisible = currentRoute in bottomNavItems.map { it.route }

    fun navigateSafely(targetRoute: String) {
        if (targetRoute == "settings") {
            if (settings.pinLockEnabled && settings.currentUserRole == com.sypos.mobile.data.local.UserRole.CASHIER) {
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
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateSafely(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    fontSize = 11.sp
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
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Pos.route) { entry ->
                val scannedBarcode = entry.savedStateHandle.get<String>("scanned_barcode")
                PosScreen(
                    scannedBarcode = scannedBarcode,
                    onBarcodeProcessed = { entry.savedStateHandle.remove<String>("scanned_barcode") },
                    onNavigateToScanner = { navController.navigate("barcode_scanner") },
                    onNavigateToSettings = { navigateSafely("settings") },
                    onNavigateToAddProductWithBarcode = { barcode -> 
                        navController.navigate("add_edit_product")
                    },
                    onLockApp = { isAppUnlocked = false }
                ) 
            }
            composable(BottomNavItem.Products.route) { ProductListScreen(
                onAddProductClick = { navController.navigate("add_edit_product") },
                onEditProductClick = { productId -> navController.navigate("add_edit_product?productId=$productId") },
                onNavigateToScanner = { navController.navigate("barcode_scanner") }
            ) }
            composable(
                route = "add_edit_product?productId={productId}",
                arguments = listOf(navArgument("productId") { type = NavType.StringType; nullable = true })
            ) { backStackEntry ->
                val productId = backStackEntry.arguments?.getString("productId")
                val scannedBarcode = backStackEntry.savedStateHandle.get<String>("scanned_barcode")
                AddEditProductScreen(
                    productId = productId,
                    scannedBarcode = scannedBarcode,
                    onNavigateBack = { 
                        backStackEntry.savedStateHandle.remove<String>("scanned_barcode")
                        navController.navigateUp() 
                    },
                    onNavigateToScanner = { navController.navigate("barcode_scanner") }
                )
            }
            composable(BottomNavItem.History.route) { HistoryScreen() }
            composable(BottomNavItem.Reports.route) { ReportScreen() }
            composable(BottomNavItem.Tools.route) { ToolsScreen(
                onNavigateToScanner = { navController.navigate("barcode_scanner") }
            ) }
            composable(BottomNavItem.Customers.route) { CustomerListScreen() }
            composable("settings") { 
                SettingsScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onLogout = { isAuthenticated = false }
                ) 
            }
            composable("barcode_scanner") {
                BarcodeScannerScreen(
                    onBarcodeScanned = { code -> 
                        navController.previousBackStackEntry?.savedStateHandle?.set("scanned_barcode", code)
                        navController.navigateUp()
                    },
                    onNavigateBack = { navController.navigateUp() }
                )
            }
        }

        if (showAdminPinDialog) {
            PinAuthDialog(
                correctPin = settings.adminPin,
                onDismiss = {
                    showAdminPinDialog = false
                    pendingAdminRoute = null
                },
                onSuccess = {
                    showAdminPinDialog = false
                    pendingAdminRoute?.let {
                        navController.navigate(it)
                        pendingAdminRoute = null
                    }
                }
            )
        }
    }
}

