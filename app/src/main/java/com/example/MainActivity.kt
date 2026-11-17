package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.backup.DriveAuthOutcome
import com.example.data.backup.DriveBackupPreferences
import com.example.data.backup.GoogleDriveAuthHelper
import com.example.data.firebase.configureAppCheck
import com.example.data.network.NetworkMonitor
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.LanguagePreferences
import com.example.data.preferences.bi
import com.example.data.preferences.formatShortDate
import com.example.data.repository.KhataAiRepository
import com.example.data.repository.LedgerRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.AddCustomerDialog
import com.example.ui.components.ExportMonthlyReportDialog
import com.example.ui.components.LanguageSwitcherPill
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.CustomerLedgerScreen
import com.example.ui.screens.DailySaleScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExpenseLoanScreen
import com.example.ui.screens.KhataScannerScreen
import com.example.ui.screens.ProfileAnalyticsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CrimsonDue
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ForestHeroEnd
import com.example.ui.theme.ForestHeroStart
import com.example.ui.theme.HarvestAmber
import com.example.ui.theme.ShouravColors
import com.example.ui.theme.ShouravTradersTheme
import com.example.ui.viewmodel.BusinessUiState
import com.example.ui.viewmodel.BusinessViewModel
import com.example.ui.viewmodel.KhataScannerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_KHATA = "khata"
const val ROUTE_CUSTOMER_DETAIL = "customer_detail"
const val ROUTE_DAILY_SALE = "daily_sale"
const val ROUTE_EXPENSES = "expenses"
const val ROUTE_SCAN_KHATA = "scan_khata"
const val ROUTE_PROFILE_ANALYTICS = "profile_analytics"
const val ROUTE_SETTINGS = "settings"

private data class NavDestination(
    val route: String,
    val enLabel: String,
    val bnLabel: String,
    val icon: ImageVector,
    val testTag: String,
)

private val mainDestinations = listOf(
    NavDestination(ROUTE_DASHBOARD, "Home", "হোম", Icons.Filled.Home, "nav_dashboard"),
    NavDestination(ROUTE_KHATA, "Customers", "কাস্টমার", Icons.Filled.Groups, "nav_khata"),
    NavDestination(ROUTE_PROFILE_ANALYTICS, "Analytics", "বিশ্লেষণ", Icons.Filled.Analytics, "nav_analytics"),
    NavDestination(ROUTE_EXPENSES, "Expenses", "খরচ", Icons.AutoMirrored.Filled.ReceiptLong, "nav_expenses"),
    NavDestination(ROUTE_SETTINGS, "Settings", "সেটিংস", Icons.Filled.Settings, "nav_settings"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before any UI/ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            ShouravTradersTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val uid = user.uid
        key(uid) {
            ShouravTradersShell(
                currentUserId = uid,
                userEmail = user.email.orEmpty(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShouravTradersShell(
    currentUserId: String,
    userEmail: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val businessViewModel: BusinessViewModel = viewModel(
        key = "biz_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                BusinessViewModel(
                    repository = LedgerRepository(app),
                    languagePreferences = LanguagePreferences(app),
                    currentUserId = currentUserId,
                    isOnlineFlow = NetworkMonitor.observeIsOnline(app),
                    driveBackupPreferences = DriveBackupPreferences(app),
                )
            }
        },
    )

    val scannerViewModel: KhataScannerViewModel = viewModel(
        key = "scan_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                KhataScannerViewModel(
                    aiRepository = KhataAiRepository(),
                    ledgerRepository = LedgerRepository(app),
                    currentUserId = currentUserId,
                )
            }
        },
    )

    val uiState by businessViewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by businessViewModel.isOnline.collectAsStateWithLifecycle()
    val languageMode by businessViewModel.languageMode.collectAsStateWithLifecycle()
    val isBusy by businessViewModel.isBusy.collectAsStateWithLifecycle()
    val feedback by businessViewModel.feedbackMessage.collectAsStateWithLifecycle()
    val driveBackupState by businessViewModel.driveBackupState.collectAsStateWithLifecycle()
    val scanState by scannerViewModel.scanState.collectAsStateWithLifecycle()

    var pendingDriveAction by remember { mutableStateOf<PendingDriveAction?>(null) }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        val action = pendingDriveAction
        pendingDriveAction = null
        val tokenResult = GoogleDriveAuthHelper.extractTokenFromActivityResult(context, activityResult.data)
        tokenResult.fold(
            onSuccess = { token ->
                when (action) {
                    PendingDriveAction.BACKUP -> businessViewModel.backupToGoogleDrive(token, userEmail)
                    PendingDriveAction.RESTORE -> businessViewModel.prepareRestoreFromGoogleDrive(token)
                    null -> businessViewModel.setDriveAuthorizing(false)
                }
            },
            onFailure = { err ->
                businessViewModel.reportDriveError(
                    err.localizedMessage ?: "Google Drive authorization was cancelled."
                )
            },
        )
    }

    fun triggerDriveAction(action: PendingDriveAction) {
        if (!isOnline) {
            businessViewModel.reportDriveError(
                bi(
                    "Internet connection is required to sync with Google Drive.",
                    "গুগল ড্রাইভে ব্যাকআপ বা রিস্টোরের জন্য ইন্টারনেট সংযোগ প্রয়োজন।",
                    languageMode,
                )
            )
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            businessViewModel.reportDriveError("Unable to start Google Drive authorization.")
            return
        }
        pendingDriveAction = action
        businessViewModel.setDriveAuthorizing(true)
        scope.launch {
            when (val outcome = GoogleDriveAuthHelper.authorizeDriveAccess(activity)) {
                is DriveAuthOutcome.Authorized -> {
                    pendingDriveAction = null
                    when (action) {
                        PendingDriveAction.BACKUP ->
                            businessViewModel.backupToGoogleDrive(outcome.accessToken, userEmail)
                        PendingDriveAction.RESTORE ->
                            businessViewModel.prepareRestoreFromGoogleDrive(outcome.accessToken)
                    }
                }
                is DriveAuthOutcome.NeedsConsent -> {
                    driveConsentLauncher.launch(
                        IntentSenderRequest.Builder(outcome.intentSender).build()
                    )
                }
                is DriveAuthOutcome.Failed -> {
                    pendingDriveAction = null
                    businessViewModel.reportDriveError(outcome.message)
                }
            }
        }
    }

    val hasPendingWrites = (uiState as? BusinessUiState.Ready)?.hasPendingWrites == true

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback!!)
            businessViewModel.clearFeedback()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DASHBOARD

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showExportPdfDialog by remember { mutableStateOf(false) }
    var preselectedSaleCustomerId by remember { mutableStateOf<String?>(null) }
    var openCustomerLendenDirectly by remember { mutableStateOf(false) }

    if (showAddCustomerDialog) {
        AddCustomerDialog(
            mode = languageMode,
            onDismiss = { showAddCustomerDialog = false },
            onConfirm = { name, phone, shop, opening ->
                showAddCustomerDialog = false
                businessViewModel.addCustomer(name, phone, shop, opening)
            },
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text(bi("Reset / Clear All Data?", "সব ডাটা মুছে ফেলবেন?", languageMode)) },
            text = {
                Text(
                    bi(
                        "This will delete all customers, rice sales, expenses, and bank loans from your cloud ledger so you can start fresh.",
                        "এটি আপনার ক্লাউড খাতা থেকে সব ক্রেতা, বিক্রি, খরচ ও লোন মুছে ফেলবে যাতে আপনি নতুন করে শুরু করতে পারেন।",
                        languageMode,
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        businessViewModel.clearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDue),
                ) {
                    Text(bi("Clear All", "মুছে ফেলুন", languageMode))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(bi("Cancel", "বাতিল", languageMode))
                }
            },
        )
    }

    if (showExportPdfDialog) {
        val readyState = uiState as? BusinessUiState.Ready
        if (readyState != null) {
            ExportMonthlyReportDialog(
                customers = readyState.customers,
                transactions = readyState.transactions,
                expenses = readyState.expenses,
                loans = readyState.loans,
                mode = languageMode,
                onDismiss = { showExportPdfDialog = false },
                onExportFeedback = { message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                },
            )
        } else {
            showExportPdfDialog = false
        }
    }

    val pendingRestore = driveBackupState.pendingRestore
    if (pendingRestore != null) {
        val backupPayload = pendingRestore.payload
        val backupDateText = if (backupPayload.exportedAtMillis > 0L) {
            formatShortDate(backupPayload.exportedAtMillis)
        } else {
            pendingRestore.fileMetadata.modifiedTimeIso.take(10).ifBlank { "Recent" }
        }
        AlertDialog(
            onDismissRequest = businessViewModel::dismissPendingRestore,
            title = {
                Text(bi("Restore Google Drive Backup?", "গুগল ড্রাইভ ব্যাকআপ রিস্টোর করবেন?", languageMode))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = bi(
                            "Found ${pendingRestore.fileMetadata.fileName} (exported $backupDateText).",
                            "ফাইল পাওয়া গেছে: ${pendingRestore.fileMetadata.fileName} ($backupDateText)।",
                            languageMode,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${backupPayload.customers.size} ${bi("customers", "ক্রেতা", languageMode)} · " +
                            "${backupPayload.transactions.size} ${bi("transactions", "লেনদেন", languageMode)} · " +
                            "${backupPayload.expenses.size} ${bi("expenses", "খরচ", languageMode)} · " +
                            "${backupPayload.loans.size} ${bi("loans", "লোন", languageMode)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ShouravColors.emeraldInk,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = bi(
                            "Choose Replace to cleanly restore this snapshot, or Merge to keep current entries and add/update backed up ones.",
                            "বর্তমান হিসাব বদলে পুরো ব্যাকআপ আনতে Replace দিন, অথবা পুরনোর সাথে মিলাতে Merge দিন।",
                            languageMode,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { businessViewModel.confirmRestoreFromDrive(replaceExisting = false) },
                        modifier = Modifier.testTag("drive_restore_merge_btn"),
                    ) {
                        Text(bi("Merge", "মার্জ", languageMode))
                    }
                    Button(
                        onClick = { businessViewModel.confirmRestoreFromDrive(replaceExisting = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.testTag("drive_restore_replace_btn"),
                    ) {
                        Text(bi("Replace & Restore", "রিস্টোর করুন", languageMode))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = businessViewModel::dismissPendingRestore,
                    modifier = Modifier.testTag("drive_restore_cancel_btn"),
                ) {
                    Text(bi("Cancel", "বাতিল", languageMode))
                }
            },
        )
    }

    fun navigateTopTab(route: String) {
        if (route == ROUTE_DAILY_SALE && currentRoute != ROUTE_CUSTOMER_DETAIL) {
            preselectedSaleCustomerId = null
        }
        if (route != ROUTE_KHATA) {
            openCustomerLendenDirectly = false
        }
        navController.navigate(route) {
            popUpTo(ROUTE_DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(ForestHeroStart, ForestHeroEnd)
                                        )
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "Shourav Traders",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (userEmail.isNotBlank()) {
                                    Text(
                                        text = userEmail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        LanguageSwitcherPill(
                            currentMode = languageMode,
                            onSelectMode = businessViewModel::setLanguageMode,
                        )
                        IconButton(
                            onClick = {
                                if (currentRoute != ROUTE_PROFILE_ANALYTICS) {
                                    navController.navigate(ROUTE_PROFILE_ANALYTICS) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            modifier = Modifier.testTag("profile_analytics_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = bi("Profile & 7D/30D Analytics", "প্রোফাইল ও ৭/৩০ দিনের হিসাব", languageMode),
                                tint = if (currentRoute == ROUTE_PROFILE_ANALYTICS) {
                                    ShouravColors.emeraldIconTint
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.testTag("top_menu_button"),
                            ) {
                                Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Menu")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(bi("Export Monthly PDF", "মাসিক PDF রিপোর্ট", languageMode)) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Filled.PictureAsPdf, contentDescription = null, tint = EmeraldPrimary)
                                    },
                                    enabled = uiState is BusinessUiState.Ready,
                                    onClick = {
                                        showOverflowMenu = false
                                        showExportPdfDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(bi("Load Sample Data", "নমুনা ডাটা লোড", languageMode)) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null, tint = EmeraldPrimary)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        businessViewModel.loadSampleData()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(bi("Reset / Clear Data", "ডাটা রিসেট / মুছুন", languageMode)) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = null, tint = CrimsonDue)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showResetConfirmDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(bi("Sign Out", "লগ আউট", languageMode)) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        signOutUser(credentialManager, scope)
                                    },
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (!useRail) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                        mainDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route ||
                                (dest.route == ROUTE_KHATA && currentRoute.startsWith(ROUTE_CUSTOMER_DETAIL))
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopTab(dest.route) },
                                icon = {
                                    Icon(imageVector = dest.icon, contentDescription = dest.enLabel)
                                },
                                label = {
                                    val label = when (languageMode) {
                                        LanguageMode.BENGALI -> dest.bnLabel
                                        LanguageMode.ENGLISH -> dest.enLabel
                                        LanguageMode.BILINGUAL -> dest.enLabel
                                    }
                                    Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ShouravColors.emeraldInk,
                                    selectedTextColor = ShouravColors.emeraldInk,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        mainDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route ||
                                (dest.route == ROUTE_KHATA && currentRoute.startsWith(ROUTE_CUSTOMER_DETAIL))
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopTab(dest.route) },
                                icon = { Icon(imageVector = dest.icon, contentDescription = dest.enLabel) },
                                label = {
                                    val label = if (languageMode == LanguageMode.BENGALI) dest.bnLabel else dest.enLabel
                                    Text(label, maxLines = 1)
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ShouravColors.emeraldInk,
                                    selectedTextColor = ShouravColors.emeraldInk,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedVisibility(visible = !isOnline || hasPendingWrites) {
                        val bannerBg = if (!isOnline) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                        val bannerBorder = if (!isOnline) {
                            ShouravColors.amberBorder
                        } else {
                            ShouravColors.emeraldBorder
                        }
                        val bannerTint = if (!isOnline) HarvestAmber else ShouravColors.emeraldInk
                        val bannerIcon = if (!isOnline) Icons.Filled.CloudOff else Icons.Filled.CloudSync
                        val bannerText = when {
                            !isOnline && hasPendingWrites -> bi(
                                "Offline · Local edits saved, waiting to sync",
                                "অফলাইন · নতুন হিসাব লোকাল সেভ আছে, সিঙ্ক বাকি",
                                languageMode,
                            )
                            !isOnline -> bi(
                                "Offline Mode · Viewing & saving locally",
                                "অফলাইন মোড · লোকাল ডাটা চালু আছে",
                                languageMode,
                            )
                            else -> bi(
                                "Back online · Syncing local changes to cloud...",
                                "অনলাইন · লোকাল হিসাব ক্লাউডে সিঙ্ক হচ্ছে...",
                                languageMode,
                            )
                        }
                        Surface(
                            color = bannerBg,
                            border = BorderStroke(1.dp, bannerBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("offline_sync_banner"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = bannerIcon,
                                    contentDescription = null,
                                    tint = bannerTint,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = bannerText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = bannerTint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (val state = uiState) {
                    is BusinessUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = EmeraldPrimary)
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(
                                    text = bi("Syncing Shourav Traders Ledger...", "খাতার হিসাব সিঙ্ক হচ্ছে...", languageMode),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    is BusinessUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = bi("Unable to load cloud ledger", "ক্লাউড খাতা লোড করা যায়নি", languageMode),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CrimsonDue,
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    is BusinessUiState.Ready -> {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_DASHBOARD,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            composable(ROUTE_DASHBOARD) {
                                DashboardScreen(
                                    state = state,
                                    mode = languageMode,
                                    isBusy = isBusy,
                                    onQuickNewSale = {
                                        preselectedSaleCustomerId = null
                                        navigateTopTab(ROUTE_DAILY_SALE)
                                    },
                                    onQuickAddCustomer = { showAddCustomerDialog = true },
                                    onQuickAddExpense = { navigateTopTab(ROUTE_EXPENSES) },
                                    onQuickScanKhata = { navigateTopTab(ROUTE_SCAN_KHATA) },
                                    onLoadSampleData = businessViewModel::loadSampleData,
                                    onOpenDueCustomers = {
                                        openCustomerLendenDirectly = false
                                        navigateTopTab(ROUTE_KHATA)
                                    },
                                    onOpenCustomerTransactions = {
                                        openCustomerLendenDirectly = true
                                        navigateTopTab(ROUTE_KHATA)
                                    },
                                    onOpenExpenses = { navigateTopTab(ROUTE_EXPENSES) },
                                    onExportMonthlyPdf = { showExportPdfDialog = true },
                                    onOpenProfileAnalytics = { navigateTopTab(ROUTE_PROFILE_ANALYTICS) },
                                    onSlipFeedback = { message ->
                                        scope.launch { snackbarHostState.showSnackbar(message) }
                                    },
                                )
                            }

                            composable(ROUTE_KHATA) {
                                CustomerLedgerScreen(
                                    state = state,
                                    mode = languageMode,
                                    onOpenCustomer = { customerId ->
                                        navController.navigate("$ROUTE_CUSTOMER_DETAIL/$customerId")
                                    },
                                    onAddCustomerClick = { showAddCustomerDialog = true },
                                    initialShowTransactions = openCustomerLendenDirectly,
                                    onConsumeInitialTab = { openCustomerLendenDirectly = false },
                                    onSlipFeedback = { message ->
                                        scope.launch { snackbarHostState.showSnackbar(message) }
                                    },
                                )
                            }

                            composable(
                                route = "$ROUTE_CUSTOMER_DETAIL/{customerId}",
                                arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
                            ) { entry ->
                                val customerId = entry.arguments?.getString("customerId").orEmpty()
                                val summary = state.customerSummaries.firstOrNull { it.customer.id == customerId }
                                CustomerDetailScreen(
                                    summary = summary,
                                    transactions = state.transactions,
                                    mode = languageMode,
                                    onBack = { navController.popBackStack() },
                                    onRecordPayment = { amount, notes ->
                                        summary?.let {
                                            businessViewModel.recordPayment(
                                                customerId = it.customer.id,
                                                customerName = it.customer.name,
                                                amount = amount,
                                                notes = notes,
                                            )
                                        }
                                    },
                                    onNewSaleForCustomer = { cid ->
                                        preselectedSaleCustomerId = cid
                                        navController.navigate(ROUTE_DAILY_SALE) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onSlipFeedback = { message ->
                                        scope.launch { snackbarHostState.showSnackbar(message) }
                                    },
                                )
                            }

                            composable(ROUTE_DAILY_SALE) {
                                DailySaleScreen(
                                    customers = state.customers,
                                    preselectedCustomerId = preselectedSaleCustomerId,
                                    mode = languageMode,
                                    isBusy = isBusy,
                                    onSaveSale = { customerId, customerName, variety, unit, qty, cost, sell, paid, notes ->
                                        businessViewModel.recordSale(
                                            customerId = customerId,
                                            customerName = customerName,
                                            riceVariety = variety,
                                            unit = unit,
                                            quantity = qty,
                                            costPricePerUnit = cost,
                                            sellingPricePerUnit = sell,
                                            paidAmount = paid,
                                            notes = notes,
                                            onSuccess = {
                                                preselectedSaleCustomerId = null
                                                if (customerId.isNotBlank()) {
                                                    navController.navigate("$ROUTE_CUSTOMER_DETAIL/$customerId") {
                                                        popUpTo(ROUTE_DAILY_SALE) { inclusive = true }
                                                        launchSingleTop = true
                                                    }
                                                } else {
                                                    navigateTopTab(ROUTE_DASHBOARD)
                                                }
                                            },
                                        )
                                    },
                                    onAddCustomerClick = { showAddCustomerDialog = true },
                                    customerDueMap = remember(state.customerSummaries) {
                                        state.customerSummaries.associate { it.customer.id to it.currentDue }
                                    },
                                )
                            }

                            composable(ROUTE_EXPENSES) {
                                ExpenseLoanScreen(
                                    state = state,
                                    mode = languageMode,
                                    isBusy = isBusy,
                                    onAddExpense = { freq, cat, amt, notes ->
                                        businessViewModel.addExpense(freq, cat, amt, notes)
                                    },
                                    onDeleteExpense = businessViewModel::deleteExpense,
                                    onAddLoan = { lender, principal, rate, notes ->
                                        businessViewModel.addLoan(lender, principal, rate, notes)
                                    },
                                    onRecordInstallment = { loan, paid ->
                                        businessViewModel.recordLoanInstallment(loan, paid)
                                    },
                                    onDeleteLoan = businessViewModel::deleteLoan,
                                )
                            }

                            composable(ROUTE_SCAN_KHATA) {
                                KhataScannerScreen(
                                    scanState = scanState,
                                    existingCustomers = state.customers,
                                    mode = languageMode,
                                    isOnline = isOnline,
                                    onAnalyzeImage = scannerViewModel::analyzeKhataImage,
                                    onStartManualTable = scannerViewModel::startManualTable,
                                    onUpdateRow = scannerViewModel::updateRow,
                                    onAddRow = scannerViewModel::addRow,
                                    onRemoveRow = scannerViewModel::removeRow,
                                    onImportAll = scannerViewModel::importAll,
                                    onReset = scannerViewModel::reset,
                                    onGoToKhata = { navigateTopTab(ROUTE_KHATA) },
                                )
                            }

                            composable(ROUTE_PROFILE_ANALYTICS) {
                                ProfileAnalyticsScreen(
                                    userEmail = userEmail,
                                    transactions = state.transactions,
                                    expenses = state.expenses,
                                    loans = state.loans,
                                    mode = languageMode,
                                    onBack = { navController.popBackStack() },
                                    onExportMonthlyPdf = { showExportPdfDialog = true },
                                    onSignOut = { signOutUser(credentialManager, scope) },
                                    customerSummaries = state.customerSummaries,
                                    showBackButton = false,
                                )
                            }

                            composable(ROUTE_SETTINGS) {
                                SettingsScreen(
                                    userEmail = userEmail,
                                    languageMode = languageMode,
                                    isOnline = isOnline,
                                    hasPendingWrites = hasPendingWrites,
                                    customerCount = state.customers.size,
                                    onSelectLanguageMode = businessViewModel::setLanguageMode,
                                    onOpenAnalytics = { navigateTopTab(ROUTE_PROFILE_ANALYTICS) },
                                    onOpenScanner = {
                                        navController.navigate(ROUTE_SCAN_KHATA) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onExportMonthlyPdf = { showExportPdfDialog = true },
                                    onLoadSampleData = businessViewModel::loadSampleData,
                                    onRequestResetData = { showResetConfirmDialog = true },
                                    onSignOut = { signOutUser(credentialManager, scope) },
                                    driveBackupState = driveBackupState,
                                    onBackupToGoogleDrive = { triggerDriveAction(PendingDriveAction.BACKUP) },
                                    onRestoreFromGoogleDrive = { triggerDriveAction(PendingDriveAction.RESTORE) },
                                )
                            }
                        }
                    }
                }
                    }
                }
            }
        }
    }
}

private enum class PendingDriveAction {
    BACKUP,
    RESTORE,
}
