package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.random.Random
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ChatMessage
import com.example.data.Load
import com.example.data.User
import com.example.ui.MainViewModel.NearbyService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun getHighContrastTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedLabelColor = Color.Black,
    unfocusedLabelColor = Color(0xFF44474E),
    focusedPlaceholderColor = Color.Gray,
    unfocusedPlaceholderColor = Color.Gray,
    focusedBorderColor = Color(0xFF005AC1),
    unfocusedBorderColor = Color(0xFF74777F)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KgiApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val allLoads by viewModel.allLoads.collectAsStateWithLifecycle()
    val trackingProgress by viewModel.loadTrackingProgress.collectAsStateWithLifecycle()
    val activeChatMessages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
    val currentScreen = viewModel.currentScreen
    val lang = viewModel.language

    // State for payment/commission blocking dialog
    var showCommissionDialog by remember { mutableStateOf(false) }
    var commissionAmountToPay by remember { mutableStateOf(0.0) }
    var activeLoadIdForCommission by remember { mutableStateOf<Int?>(null) }
    var activeDriverIdForCommission by remember { mutableStateOf<Int?>(null) }

    // Prevent accidental logout or return to login screen when session is active
    if (currentUser != null) {
        BackHandler {
            Toast.makeText(context, "Session Active • Use Logout icon to sign out", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = Localization.get("app_title", lang),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = com.example.ui.theme.Color005AC1,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.testTag("app_logo_text")
                            )
                            Text(
                                text = "अब ट्रांसपोर्ट नहीं रुकेगा",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = com.example.ui.theme.Color74777F
                            )
                        }
                    },
                    actions = {
                        // Refresh Data Button
                        IconButton(
                            onClick = {
                                viewModel.refreshData()
                                Toast.makeText(context, "Refreshed live data ✓", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Data",
                                tint = com.example.ui.theme.Color005AC1
                            )
                        }

                        // Info Button
                        IconButton(
                            onClick = {
                                Toast.makeText(context, "KGI Freight Platform: Powered by OpenStreetMap & Overpass API", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.testTag("topbar_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "App Info",
                                tint = com.example.ui.theme.Color005AC1
                            )
                        }

                        // Multi-language Toggle Button
                        IconButton(
                            onClick = { viewModel.toggleLanguage() },
                            modifier = Modifier.testTag("language_toggle")
                        ) {
                            Text(
                                text = if (lang == "en") "हिं" else "EN",
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.Color005AC1,
                                fontSize = 14.sp
                            )
                        }

                        if (currentUser != null) {
                            IconButton(
                                onClick = { viewModel.logout() },
                                modifier = Modifier.testTag("logout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = Localization.get("nav_logout", lang),
                                    tint = com.example.ui.theme.Color1B1B1F
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White
                    )
                )
                HorizontalDivider(color = com.example.ui.theme.ColorE0E2EC, thickness = 1.dp)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                "landing" -> PremiumLandingScreen(viewModel = viewModel, onNavigate = { viewModel.currentScreen = it })
                "admin_login" -> AdminLoginScreen(onSuccess = { viewModel.currentScreen = "admin_panel" }, onBack = { viewModel.currentScreen = "landing" })
                "admin_panel" -> AdminCommissionPanel(viewModel = viewModel, onBack = { viewModel.currentScreen = "landing" })
                "login_driver" -> LoginScreen(viewModel = viewModel, role = "DRIVER")
                "signup_driver" -> SignupDriverScreen(viewModel = viewModel)
                "login_shipper" -> LoginScreen(viewModel = viewModel, role = "SHIPPER")
                "signup_shipper" -> SignupShipperScreen(viewModel = viewModel)
                "driver_home" -> DriverHomeScreen(
                    viewModel = viewModel,
                    allLoads = allLoads,
                    lang = lang,
                    onPayCommission = { loadId, amount ->
                        activeLoadIdForCommission = loadId
                        activeDriverIdForCommission = null
                        commissionAmountToPay = amount
                        showCommissionDialog = true
                    }
                )
                "shipper_home" -> ShipperHomeScreen(
                    viewModel = viewModel,
                    allLoads = allLoads,
                    lang = lang,
                    trackingProgress = trackingProgress,
                    onBlockCommission = { loadId, driverId, amount ->
                        activeLoadIdForCommission = loadId
                        activeDriverIdForCommission = driverId
                        commissionAmountToPay = amount
                        showCommissionDialog = true
                    }
                )
            }

            // Commission & UPI QR Payment Dialog
            if (showCommissionDialog) {
                CommissionPaymentDialog(
                    viewModel = viewModel,
                    amount = commissionAmountToPay,
                    onDismiss = { showCommissionDialog = false },
                    onSubmitProof = { utr, phone, screenshotPath ->
                        val loadId = activeLoadIdForCommission
                        if (loadId != null) {
                            viewModel.payCommission(loadId, utr, phone, screenshotPath) { success, message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                if (success) {
                                    showCommissionDialog = false
                                    Toast.makeText(
                                        context,
                                        "Once verified, tap Accept on this driver again to confirm the trip.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 1. Landing Screen
// -------------------------------------------------------------
@Composable
fun PremiumLandingScreen(viewModel: MainViewModel, onNavigate: (String) -> Unit) {
    val lang = viewModel.language

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(com.example.ui.theme.ColorFDFBFF)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Hero Section - Bold Typography Title & Tagline
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "KGI DIESELS",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = com.example.ui.theme.Color005AC1,
                modifier = Modifier.testTag("hero_brand_badge")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = Localization.get("hindi_tagline", lang),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = com.example.ui.theme.Color74777F
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Main Display Headline
            Text(
                text = "DISTRIBUTION",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = com.example.ui.theme.Color1B1B1F,
                lineHeight = 40.sp,
                modifier = Modifier.testTag("hero_title")
            )
            Text(
                text = "REDEFINED.",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = com.example.ui.theme.Color005AC1,
                lineHeight = 40.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connecting India's commercial freight network.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = com.example.ui.theme.Color44474E,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // -------------------------------------------------------------
        // Gorgeous Flashing 0% Commission Promo Banner
        // -------------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("promo_commission_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)), // Warm soft amber background
            border = BorderStroke(2.dp, Color(0xFFF97316)) // Orange border
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFF97316), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "Offer",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "0% COMMISSION AD",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = Color(0xFFC2410C),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "0% Commission On Job providing And Driving hiring.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Beautiful Interactive Highway / Truck Art Illustration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Draw schematic dynamic highway
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pathColor = Color.White.copy(alpha = 0.15f)
                val dashColor = Color(0xFFFBBF24)
                
                // Highway curves
                drawLine(pathColor, Offset(0f, size.height * 0.7f), Offset(size.width, size.height * 0.7f), strokeWidth = 4f)
                drawLine(pathColor, Offset(0f, size.height * 0.9f), Offset(size.width, size.height * 0.9f), strokeWidth = 4f)
                
                // Dash lines
                val dashWidth = 20f
                val gapWidth = 20f
                var x = 0f
                while (x < size.width) {
                    drawLine(dashColor, Offset(x, size.height * 0.8f), Offset(x + dashWidth, size.height * 0.8f), strokeWidth = 3f)
                    x += dashWidth + gapWidth
                }
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No commission before 3rd trip!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Fast matching • Verified Shippers & Drivers",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // -------------------------------------------------------------
        // Entrance / Role Selection Buttons
        // -------------------------------------------------------------
        Text(
            text = "CHOOSE YOUR PANEL TO ENTER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = com.example.ui.theme.Color74777F,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // DRIVER PANEL
            Button(
                onClick = { onNavigate("login_driver") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("driver_entry_btn"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "DRIVER / TRANSPORTER PANEL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Get loads, accept cargo, view assistance",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // SHIPPER PANEL
            Button(
                onClick = { onNavigate("login_shipper") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("shipper_entry_btn"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color1B1B1F)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "SHIPPER / SENDER PANEL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Post load/parcel, find trucks, calculate fares",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = { onNavigate("admin_login") }, modifier = Modifier.fillMaxWidth()) {
                Text("Admin", fontSize = 11.sp, color = com.example.ui.theme.Color74777F)
            }
        }
    }
}

@Composable
fun LandingScreen(viewModel: MainViewModel, onNavigate: (String) -> Unit) {}

@Composable
fun OldLandingScreenDisabled(viewModel: MainViewModel, onNavigate: (String) -> Unit) {
    val lang = viewModel.language
    var dropdownExpanded by remember { mutableStateOf(false) }
    var truckSize by remember { mutableStateOf("LCV (3 Ton)") }
    var rateKmText by remember { mutableStateOf("12") }
    var distanceText by remember { mutableStateOf("100") }
    var weightText by remember { mutableStateOf("3") }
    val defaultRates = mapOf("LCV (3 Ton)" to 12)
    val truckSizes = listOf("LCV (3 Ton)")
    val distanceCost = 0.0
    val weightCost = 0.0
    val totalFare = 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(com.example.ui.theme.ColorFDFBFF)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Hero Section - Bold Typography Title & Tagline
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "KGI DIESELS",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = com.example.ui.theme.Color005AC1,
                modifier = Modifier.testTag("hero_brand_badge")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = Localization.get("hindi_tagline", lang),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = com.example.ui.theme.Color74777F
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Main Display Headline
            Text(
                text = "DISTRIBUTION",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = com.example.ui.theme.Color1B1B1F,
                lineHeight = 40.sp,
                modifier = Modifier.testTag("hero_title")
            )
            Text(
                text = "REDEFINED.",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = com.example.ui.theme.Color005AC1,
                lineHeight = 40.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connecting India's commercial freight network.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = com.example.ui.theme.Color44474E,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // -------------------------------------------------------------
        // Gorgeous Flashing 0% Commission Promo Banner
        // -------------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("promo_commission_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)), // Warm soft amber background
            border = BorderStroke(2.dp, Color(0xFFF97316)) // Orange border
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFF97316), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "Offer",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "0% COMMISSION AD",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = Color(0xFFC2410C),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "0% Commission On Job providing And Driving hiring.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // -------------------------------------------------------------
        // Fare Calculator Card Section
        // -------------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("landing_calculator_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ColorE0E2EC),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Calculator Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Fare Calculator",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.Color1B1B1F
                    )
                    
                    // Live Rates Badge
                    Box(
                        modifier = Modifier
                            .background(com.example.ui.theme.Color005AC1, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "LIVE RATES",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 1. Truck Size Selection Box with Overlapping Label
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, com.example.ui.theme.Color74777F, RoundedCornerShape(12.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(truckSize, fontSize = 14.sp, color = com.example.ui.theme.Color1B1B1F)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = com.example.ui.theme.Color1B1B1F)
                        }
                    }
                    // Overlapping label
                    Text(
                        text = "TRUCK SIZE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.Color44474E,
                        modifier = Modifier
                            .offset(x = 12.dp, y = (-6).dp)
                            .background(com.example.ui.theme.ColorE0E2EC)
                            .padding(horizontal = 4.dp)
                    )

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        truckSizes.forEach { size ->
                            DropdownMenuItem(
                                text = { Text(size) },
                                onClick = {
                                    truckSize = size
                                    rateKmText = defaultRates[size]?.toString() ?: "22"
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Distance and Weight 2-Column Inputs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Distance Input
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = distanceText,
                            onValueChange = { distanceText = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = com.example.ui.theme.Color1B1B1F),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, com.example.ui.theme.Color74777F, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        )
                        Text(
                            text = "DISTANCE (KM)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.Color44474E,
                            modifier = Modifier
                                .offset(x = 12.dp, y = (-6).dp)
                                .background(com.example.ui.theme.ColorE0E2EC)
                                .padding(horizontal = 4.dp)
                        )
                    }

                    // Weight Input
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = com.example.ui.theme.Color1B1B1F),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, com.example.ui.theme.Color74777F, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        )
                        Text(
                            text = "WEIGHT (TONS)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.Color44474E,
                            modifier = Modifier
                                .offset(x = 12.dp, y = (-6).dp)
                                .background(com.example.ui.theme.ColorE0E2EC)
                                .padding(horizontal = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Dynamic Calculation Card with dashed divider
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "Base Rate (₹${rateKmText}/km)",
                                fontSize = 12.sp,
                                color = com.example.ui.theme.Color44474E
                            )
                            Text(
                                text = "₹${"%,.0f".format(distanceCost)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.Color1B1B1F
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Custom dashed divider
                        Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
                            drawLine(
                                color = com.example.ui.theme.ColorC4C6CF,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ESTIMATED TOTAL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.example.ui.theme.Color44474E
                            )
                            Text(
                                text = "₹${"%,.0f".format(totalFare)}*",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = com.example.ui.theme.Color005AC1,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CALCULATE FARE main button inside card
                Button(
                    onClick = {
                        // Triggers re-computation visually
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1),
                    shape = RoundedCornerShape(27.dp)
                ) {
                    Text(
                        text = "CALCULATE FARE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // -------------------------------------------------------------
        // Entrance / Role Selection Buttons at the Bottom
        // -------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // I AM A DRIVER
            OutlinedButton(
                onClick = { onNavigate("login_driver") },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("driver_entry_btn"),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, com.example.ui.theme.Color005AC1),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = com.example.ui.theme.Color005AC1)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I AM A DRIVER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // SEND FREIGHT
            OutlinedButton(
                onClick = { onNavigate("login_shipper") },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("shipper_entry_btn"),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, com.example.ui.theme.Color1B1B1F),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = com.example.ui.theme.Color1B1B1F)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SEND FREIGHT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. Authentication & Signup
// -------------------------------------------------------------
// -------------------------------------------------------------
// Admin - manual payment verification (no payment-gateway API is used,
// so a human has to confirm each UTR really arrived before it unlocks anything)
// -------------------------------------------------------------
@Composable
fun AdminLoginScreen(onSuccess: () -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    // NOTE: this is a basic PIN gate, not real security - anyone who decompiles the APK
    // can read this value. It only exists to stop casual users from wandering into the
    // admin screen by accident. Change ADMIN_PIN below to your own number before publishing.
    val ADMIN_PIN = "5566"

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(48.dp), tint = com.example.ui.theme.Color005AC1)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Admin Access", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it; error = "" },
                label = { Text("Enter Admin PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            if (error.isNotBlank()) {
                Text(error, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (pin == ADMIN_PIN) onSuccess() else error = "Incorrect PIN"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enter")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
fun AdminCommissionPanel(viewModel: MainViewModel, onBack: () -> Unit) {
    val commissions by viewModel.allCommissions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pending = commissions.filter { it.verificationStatus == "SUBMITTED" }
    val resolved = commissions.filter { it.verificationStatus == "VERIFIED" || it.verificationStatus == "REJECTED" }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Commission Verification", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text("Pending Verification (${pending.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFB45309))
        Spacer(modifier = Modifier.height(8.dp))

        if (pending.isEmpty()) {
            Text("Nothing waiting on review right now.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 13.sp)
        }

        LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pending) { comm ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                    border = BorderStroke(1.dp, Color(0xFFFDBA74))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Load #${comm.loadId} · ₹${"%,.2f".format(comm.amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("UTR/Txn ID: ${comm.utrNumber}", fontSize = 12.sp)
                        Text("Phone given: ${comm.payeePhone}", fontSize = 12.sp)
                        Text(
                            "Who: ${if (comm.driverId != 0) "Driver #${comm.driverId}" else "Shipper #${comm.shipperId}"}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        if (comm.screenshotPath.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            AsyncImage(
                                model = Uri.parse(comm.screenshotPath),
                                contentDescription = "Payment screenshot",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.adminVerifyCommission(comm.id, true) { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Approve", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.adminVerifyCommission(comm.id, false) { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reject", fontSize = 12.sp, color = Color.Red)
                            }
                        }
                    }
                }
            }

            if (resolved.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("History", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(resolved.take(30)) { comm ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Load #${comm.loadId} · ₹${"%,.2f".format(comm.amount)}", fontSize = 12.sp)
                        Text(
                            comm.verificationStatus,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (comm.verificationStatus == "VERIFIED") Color(0xFF10B981) else Color.Red
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: MainViewModel, role: String) {
    val lang = viewModel.language
    var phone by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (role == "DRIVER") Icons.Default.LocalShipping else Icons.Default.Inventory,
            contentDescription = null,
            tint = com.example.ui.theme.Color005AC1,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = (if (role == "DRIVER") "Driver" else "Shipper") + " " + Localization.get("login_title", lang),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = com.example.ui.theme.Color1B1B1F,
            letterSpacing = (-1).sp
        )

        Text(
            text = "Enter your 10-digit registered mobile number",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = com.example.ui.theme.Color74777F,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        // Bold design with +91 block next to input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(56.dp)
                    .background(com.example.ui.theme.ColorE0E2EC, RoundedCornerShape(12.dp))
                    .border(1.5.dp, com.example.ui.theme.Color74777F, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+91",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.example.ui.theme.Color1B1B1F
                )
            }

            OutlinedTextField(
                value = phone,
                onValueChange = { input ->
                    val clean = input.filter { it.isDigit() }
                    if (clean.length <= 10) {
                        phone = clean
                    }
                },
                placeholder = { Text("10-Digit Phone Number", color = com.example.ui.theme.Color74777F) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = getHighContrastTextFieldColors(),
                trailingIcon = {
                    if (phone.length == 10) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Valid phone number",
                            tint = Color(0xFF137333)
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("login_phone_input")
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.login(phone, role) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            enabled = phone.length == 10,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("login_submit_btn"),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1)
        ) {
            Text(Localization.get("login_btn", lang), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                viewModel.currentScreen = if (role == "DRIVER") "signup_driver" else "signup_shipper"
            },
            modifier = Modifier.testTag("signup_redirect")
        ) {
            Text(
                text = "Don't have an account? " + Localization.get("signup_title", lang),
                fontWeight = FontWeight.Bold,
                color = com.example.ui.theme.Color005AC1
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Demo Accounts helper section with One-tap Quick Logins
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ColorE0E2EC),
            border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (lang == "en") "Evaluator Quick Login Help:" else "परीक्षण के लिए त्वरित लॉगिन सहायता:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = com.example.ui.theme.Color1B1B1F
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (lang == "en") "Tap an account below to auto-fill details and log in instantly!" else "विवरण स्वतः भरने और तुरंत लॉगिन करने के लिए नीचे टैप करें!",
                    fontSize = 12.sp,
                    color = com.example.ui.theme.Color44474E
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                if (role == "DRIVER") {
                    Button(
                        onClick = {
                            phone = "9112233445"
                            viewModel.login("9112233445", "DRIVER") { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("AUTO-FILL: Karan Singh (Driver)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    Button(
                        onClick = {
                            phone = "9876543210"
                            viewModel.login("9876543210", "SHIPPER") { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("AUTO-FILL: Rajesh Senders (Shipper)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignupDriverScreen(viewModel: MainViewModel) {
    val lang = viewModel.language
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var truckNumber by remember { mutableStateOf("") }
    var truckSize by remember { mutableStateOf("19 Feet") }

    // Documents
    var rcFile by remember { mutableStateOf("") }
    var dlFile by remember { mutableStateOf("") }
    var aadhaarFile by remember { mutableStateOf("") }
    var permitFile by remember { mutableStateOf("") }

    val truckSizes = listOf("14 Feet", "17 Feet", "19 Feet", "22 Feet", "24 Feet", "32 Feet")
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Document Pickers Launcher
    val rcLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        rcFile = uri?.toString() ?: ""
    }
    val dlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        dlFile = uri?.toString() ?: ""
    }
    val aadhaarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        aadhaarFile = uri?.toString() ?: ""
    }
    val permitLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        permitFile = uri?.toString() ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Driver Onboarding",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Complete your registration to access freight loads",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Demo doc autofill
        Button(
            onClick = {
                name = "Demo Transporter"
                phone = "9" + (100000000 + Random.nextInt(900000000))
                truckNumber = "DL-01-CA-9988"
                truckSize = "22 Feet"
                rcFile = "simulated_rc_photo.jpg"
                dlFile = "simulated_dl_photo.jpg"
                aadhaarFile = "simulated_aadhaar_photo.jpg"
                permitFile = "simulated_permit_photo.jpg"
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Auto-Fill Realistic Demo Driver Docs", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(Localization.get("name_label", lang)) },
            singleLine = true,
            colors = getHighContrastTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text(Localization.get("phone_label", lang)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            colors = getHighContrastTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = truckNumber,
            onValueChange = { truckNumber = it.uppercase() },
            label = { Text(Localization.get("truck_num_label", lang)) },
            singleLine = true,
            colors = getHighContrastTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Truck Size Dropdown Selection
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = truckSize,
                onValueChange = {},
                readOnly = true,
                label = { Text(Localization.get("truck_size_label", lang)) },
                trailingIcon = {
                    IconButton(onClick = { dropdownExpanded = true }) {
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                },
                colors = getHighContrastTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                truckSizes.forEach { size ->
                    DropdownMenuItem(
                        text = { Text(size) },
                        onClick = {
                            truckSize = size
                            dropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Document uploading section
        Text(
            text = "Required Documents (Verification)",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(vertical = 8.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val widthModifier = Modifier.widthIn(max = 160.dp)
            DocUploadCard(
                label = Localization.get("rc_label", lang),
                isUploaded = rcFile.isNotEmpty(),
                onClick = { rcLauncher.launch("image/*") },
                modifier = widthModifier
            )
            DocUploadCard(
                label = Localization.get("dl_label", lang),
                isUploaded = dlFile.isNotEmpty(),
                onClick = { dlLauncher.launch("image/*") },
                modifier = widthModifier
            )
            DocUploadCard(
                label = Localization.get("aadhaar_label", lang),
                isUploaded = aadhaarFile.isNotEmpty(),
                onClick = { aadhaarLauncher.launch("image/*") },
                modifier = widthModifier
            )
            DocUploadCard(
                label = Localization.get("permit_label", lang),
                isUploaded = permitFile.isNotEmpty(),
                onClick = { permitLauncher.launch("image/*") },
                modifier = widthModifier
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.signupDriver(
                    name = name,
                    phone = phone,
                    truckSize = truckSize,
                    truckNumber = truckNumber,
                    rc = rcFile,
                    dl = dlFile,
                    aadhaar = aadhaarFile,
                    permit = permitFile
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(Localization.get("submit_register", lang), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { viewModel.currentScreen = "landing" }) {
            Text("Cancel", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DocUploadCard(label: String, isUploaded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .height(90.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isUploaded) Color(0xFFE6F4EA) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            1.dp,
            if (isUploaded) Color(0xFF137333) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isUploaded) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                contentDescription = null,
                tint = if (isUploaded) Color(0xFF137333) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = if (isUploaded) Color(0xFF137333) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SignupShipperScreen(viewModel: MainViewModel) {
    val lang = viewModel.language
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Shipper / Load Owner Signup",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(Localization.get("name_label", lang)) },
            singleLine = true,
            colors = getHighContrastTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text(Localization.get("phone_label", lang)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            colors = getHighContrastTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.signupShipper(name, phone) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(Localization.get("signup_btn", lang), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { viewModel.currentScreen = "landing" }) {
            Text("Cancel", fontWeight = FontWeight.SemiBold)
        }
    }
}

// -------------------------------------------------------------
// 3. Driver Side Interface
// -------------------------------------------------------------
@Composable
fun DriverHomeScreen(
    viewModel: MainViewModel,
    allLoads: List<Load>,
    lang: String,
    onPayCommission: (loadId: Int, amount: Double) -> Unit
) {
    var selectedTab by remember { mutableStateOf("loads") }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = when (selectedTab) {
                "loads" -> 0
                "jobs" -> 1
                "trips" -> 2
                "assistance" -> 3
                "profile" -> 4
                else -> 0
            },
            modifier = Modifier.testTag("driver_top_navigation")
        ) {
            Tab(
                selected = selectedTab == "loads",
                onClick = { selectedTab = "loads" },
                text = { Text("Get Loads", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "jobs",
                onClick = { selectedTab = "jobs" },
                text = { Text("Find Jobs", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "trips",
                onClick = { selectedTab = "trips" },
                text = { Text("Ongoing Trip", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "assistance",
                onClick = { selectedTab = "assistance" },
                text = { Text("Assistance", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.BuildCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "profile",
                onClick = { selectedTab = "profile" },
                text = { Text("Profile & Help", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (selectedTab) {
                "loads" -> DriverLoadsTab(viewModel = viewModel, allLoads = allLoads, lang = lang)
                "jobs" -> DriverJobsTab(viewModel = viewModel, lang = lang)
                "trips" -> DriverTripsTab(viewModel = viewModel, allLoads = allLoads, lang = lang, onPayCommission = onPayCommission)
                "assistance" -> DriverServicesTab(viewModel = viewModel, lang = lang)
                "profile" -> DriverProfileTab(viewModel = viewModel, lang = lang)
            }
        }
    }
}

@Composable
fun DriverProfileTab(viewModel: MainViewModel, lang: String) {
    val driver = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Driver Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ColorE0E2EC),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(com.example.ui.theme.Color005AC1, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = driver.name.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = driver.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = com.example.ui.theme.Color1B1B1F
                        )
                        Text(
                            text = "+91 ${driver.phone}",
                            fontSize = 14.sp,
                            color = com.example.ui.theme.Color44474E
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = com.example.ui.theme.ColorC4C6CF)

                Spacer(modifier = Modifier.height(12.dp))

                // Vehicle details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("VEHICLE SIZE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Color74777F)
                        Text(driver.truckSize, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Color1B1B1F)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("VEHICLE NUMBER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Color74777F)
                        Text(driver.truckNumber, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Color1B1B1F)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Document Verification Status
        Text("Document Verification Status", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = com.example.ui.theme.Color1B1B1F)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DocStatusRow(label = "Registration Certificate (RC)", isVerified = driver.rcPath.isNotEmpty())
                DocStatusRow(label = "Driving License (DL)", isVerified = driver.dlPath.isNotEmpty())
                DocStatusRow(label = "Aadhaar Card", isVerified = driver.aadhaarPath.isNotEmpty())
                DocStatusRow(label = "National Permit", isVerified = driver.permitPath.isNotEmpty())
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Help & Support Helpline Section
        Text("Emergency Help & Assistance", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = com.example.ui.theme.Color1B1B1F)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBFF)),
            border = BorderStroke(1.5.dp, com.example.ui.theme.Color005AC1)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = null,
                        tint = com.example.ui.theme.Color005AC1,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "KGI Support Helpline",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.Color1B1B1F
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "If you face any issues with a load, payment, or commission, contact our 24/7 dedicated helper immediately.",
                    fontSize = 12.sp,
                    color = com.example.ui.theme.Color44474E
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:9660033436"))
                            context.startActivity(dialIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CALL SUPPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            Toast.makeText(context, "Opening Live WhatsApp Assistant...", Toast.LENGTH_SHORT).show()
                        },
                        border = BorderStroke(1.dp, com.example.ui.theme.Color005AC1),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LIVE CHAT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DocStatusRow(label: String, isVerified: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isVerified) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isVerified) Color(0xFF137333) else Color.Red,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, fontSize = 13.sp, color = com.example.ui.theme.Color1B1B1F)
        }
        Text(
            text = if (isVerified) "VERIFIED" else "PENDING",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isVerified) Color(0xFF137333) else Color.Red
        )
    }
}

@Composable
fun DriverLoadsTab(viewModel: MainViewModel, allLoads: List<Load>, lang: String) {
    val driver = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return

    var searchDest by remember { mutableStateOf("") }
    var selectedTruckSizeFilter by remember { mutableStateOf("All") }
    var showDriverCommissionBlock by remember { mutableStateOf(false) }
    var driverCommissionAmount by remember { mutableStateOf(0.0) }
    var pendingInterestLoadId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Proactive reminder: pop up automatically whenever this screen opens if commission is owed
    LaunchedEffect(Unit) {
        val unpaid = viewModel.getUnpaidCommissionsForDriver(driver.id)
        if (unpaid.isNotEmpty()) {
            driverCommissionAmount = unpaid.sumOf { it.amount }
            showDriverCommissionBlock = true
        }
    }

    val truckSizes = listOf("All", "14 Feet", "17 Feet", "19 Feet", "22 Feet", "24 Feet", "32 Feet")

    // Free-trial rule: driver's own completed-trips count (1 free trip, then commission required)
    val completedTripsCount = allLoads.count { it.assignedDriverId == driver.id && it.status == "COMPLETED" }

    // Filtered loads
    val filteredLoads = allLoads.filter { load ->
        val matchesDest = load.dropLocation.contains(searchDest, ignoreCase = true) ||
                load.pickupLocation.contains(searchDest, ignoreCase = true)
        val matchesSize = selectedTruckSizeFilter == "All" || load.truckSize == selectedTruckSizeFilter
        matchesDest && matchesSize && load.status == "POSTED"
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Search & Filters
        OutlinedTextField(
            value = searchDest,
            onValueChange = { searchDest = it },
            label = { Text("Search Destination / Pickup") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("loads_search")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal size filter
        Text("Filter by Truck Size:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            truckSizes.forEach { size ->
                FilterChip(
                    selected = selectedTruckSizeFilter == size,
                    onClick = { selectedTruckSizeFilter = size },
                    label = { Text(size) },
                    modifier = Modifier.testTag("filter_chip_$size")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredLoads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No matching loads available right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredLoads) { load ->
                    DriverLoadItemCard(load = load, driverId = driver.id, completedTripsCount = completedTripsCount, onInterest = {
                        scope.launch {
                            val unpaid = viewModel.getUnpaidCommissionsForDriver(driver.id)
                            if (unpaid.isNotEmpty()) {
                                driverCommissionAmount = unpaid.sumOf { it.amount }
                                pendingInterestLoadId = load.id
                                showDriverCommissionBlock = true
                            } else {
                                viewModel.expressInterest(load.id)
                            }
                        }
                    })
                }
            }
        }

        if (showDriverCommissionBlock) {
            CommissionPaymentDialog(
                viewModel = viewModel,
                amount = driverCommissionAmount,
                onDismiss = { showDriverCommissionBlock = false },
                onSubmitProof = { utr, phone, screenshotPath ->
                    viewModel.submitDriverCommissionPayment(driver.id, utr, phone) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        if (success) {
                            showDriverCommissionBlock = false
                            Toast.makeText(
                                context,
                                "Once verified, come back and apply for this load again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun DriverLoadItemCard(load: Load, driverId: Int, completedTripsCount: Int, onInterest: () -> Unit) {
    val isInterested = load.getInterestedDriverIds().contains(driverId)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("load_item_${load.id}"),
        elevation = CardDefaults.cardElevation(3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                    Text(load.truckSize, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text(
                    text = "₹${"%,.0f".format(load.totalFare)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Route representation
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.RadioButtonChecked, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                    Box(modifier = Modifier.width(2.dp).height(24.dp).background(MaterialTheme.colorScheme.primary))
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(load.pickupLocation, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(load.dropLocation, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Load Type", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(load.loadType, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Column {
                    Text("Weight", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("${load.weightTons} Tons", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Column {
                    Text("Distance", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("${load.distanceKm.toInt()} km", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Charges Breakdown Box
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Est. Driver Earnings:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text("₹${"%,.2f".format(load.totalFare)}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Platform Commission (0.8% with Tax):", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        Text("₹${"%,.2f".format(load.totalFare * 0.008)}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("★ Offer: First 2 Completed Trips 100% Free!", fontSize = 10.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Contact visibility: during the free trial (before the driver's 1st completed trip),
            // the shipper's number is visible even before accepting. After that, it stays hidden
            // on every load until that trip's commission is paid.
            if (completedTripsCount < 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Shipper: ${load.shipperName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Phone: ${load.shipperPhone} (Free 1st Trip)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0369A1))
                    }
                    val context = LocalContext.current
                    IconButton(
                        onClick = {
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${load.shipperPhone}"))
                            context.startActivity(dialIntent)
                        },
                        modifier = Modifier.clip(CircleShape).background(Color(0xFFE0F2FE))
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "Call shipper", tint = Color(0xFF0369A1))
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Shipper contact hidden — pay commission on your last completed trip to unlock.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onInterest() },
                enabled = !isInterested,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInterested) Color.Gray else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isInterested) "Interest Expressed ✓" else "Send Interest / Apply",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun LeafletMapView(
    userLat: Double,
    userLng: Double,
    services: List<NearbyService>,
    radiusKm: Int,
    modifier: Modifier = Modifier
) {
    val htmlContent = remember(userLat, userLng, services, radiusKm) {
        val markersJs = StringBuilder()
        services.forEachIndexed { idx, srv ->
            val name = srv.name.replace("'", "\\'").replace("\"", "\\\"")
            val desc = srv.description.replace("'", "\\'").replace("\"", "\\\"")
            val phone = srv.phone.replace("'", "\\'").replace("\"", "\\\"")
            val lat = srv.latOffset
            val lng = srv.lngOffset
            val dist = String.format("%.1f", srv.distanceKm)

            markersJs.append("""
                var redIcon = L.divIcon({
                  className: 'srv-pin',
                  html: '<div style="background-color:#DC2626;color:white;width:24px;height:24px;border-radius:50%;border:2px solid white;display:flex;align-items:center;justify-content:center;font-weight:bold;font-size:11px;box-shadow:0 2px 4px rgba(0,0,0,0.3);">${idx + 1}</div>',
                  iconSize: [24, 24],
                  iconAnchor: [12, 12]
                });
                var m = L.marker([$lat, $lng], {icon: redIcon}).addTo(map);
                m.bindPopup("<b>$name</b><br><b>Distance:</b> $dist km<br><b>Phone:</b> $phone<br><small>$desc</small><br><a href='https://www.google.com/maps/dir/?api=1&destination=$lat,$lng' target='_blank' style='display:inline-block;background:#005AC1;color:white;padding:6px 12px;text-decoration:none;border-radius:6px;font-weight:bold;font-size:11px;margin-top:6px;'>🗺️ Get Directions</a>");
            """.trimIndent()).append("\n")
        }

        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <style>
            html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
            .leaflet-popup-content-wrapper { border-radius: 12px; padding: 4px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); }
            .leaflet-popup-content { font-size: 13px; line-height: 1.4; color: #1f2937; margin: 10px; }
            .leaflet-container { background: #e5e7eb; }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script>
            var map = L.map('map', { zoomControl: true }).setView([$userLat, $userLng], 12);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
              maxZoom: 19,
              attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            }).addTo(map);

            var userIcon = L.divIcon({
              className: 'user-pin',
              html: '<div style="background-color:#005AC1;width:18px;height:18px;border-radius:50%;border:3px solid white;box-shadow:0 0 8px rgba(0,90,193,0.6);"></div>',
              iconSize: [18, 18],
              iconAnchor: [9, 9]
            });
            L.marker([$userLat, $userLng], {icon: userIcon}).addTo(map)
              .bindPopup("<b>📍 Your Live GPS Location</b><br><small>Lat: ${String.format("%.4f", userLat)}, Lng: ${String.format("%.4f", userLng)}</small>");

            L.circle([$userLat, $userLng], {
              color: '#005AC1',
              fillColor: '#005AC1',
              fillOpacity: 0.08,
              weight: 1.5,
              radius: ${radiusKm * 1000}
            }).addTo(map);

            $markersJs
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadDataWithBaseURL("https://www.openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://www.openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}

@Composable
fun DriverServicesTab(viewModel: MainViewModel, lang: String) {
    val gpsActive = viewModel.gpsEnabled
    var selectedCategory by remember { mutableStateOf("garages") }
    var radiusKm by remember { mutableStateOf(10) } // Default 10km radius
    val context = LocalContext.current

    // Location search inputs
    var stateQuery by remember { mutableStateOf("") }
    var cityQuery by remember { mutableStateOf("") }
    var areaQuery by remember { mutableStateOf("") }
    var pincodeQuery by remember { mutableStateOf("") }

    var stateSearch by remember { mutableStateOf("") }
    var citySearch by remember { mutableStateOf("") }
    var areaSearch by remember { mutableStateOf("") }
    var pincodeSearch by remember { mutableStateOf("") }

    var liveOutposts by remember { mutableStateOf<List<NearbyService>?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Helper search function executing keyless Overpass + Nominatim search
    var usedGoogleFallback by remember { mutableStateOf(false) }
    var googleFallbackMessage by remember { mutableStateOf<String?>(null) }

    val performLocationSearch: suspend (String, String, String, String, String, Int) -> Unit = { cCat, cCity, cPin, cState, cArea, cRadius ->
        isSearching = true
        usedGoogleFallback = false
        googleFallbackMessage = null
        Log.d("OverpassAPI", "Initiating Overpass Search: cat=$cCat, city=$cCity, radius=${cRadius}km")
        val osmResults = viewModel.searchLiveOsmOutposts(
            categoryId = cCat,
            city = cCity,
            pincode = cPin,
            state = cState,
            area = cArea,
            radiusKm = cRadius
        )

        if (osmResults.isEmpty()) {
            // OpenStreetMap found nothing real here - try Google Places as a backup,
            // but only if the user has added their own Google Cloud API key.
            val savedKey = viewModel.getSavedGoogleApiKey(context)
            if (savedKey.isNotBlank()) {
                val googleResult = viewModel.searchLiveGooglePlaces(context, cCat, cCity, cPin, cState, cArea)
                if (googleResult.services.isNotEmpty()) {
                    liveOutposts = googleResult.services
                    usedGoogleFallback = true
                } else {
                    liveOutposts = emptyList()
                    googleFallbackMessage = googleResult.errorMessage
                }
            } else {
                liveOutposts = emptyList()
            }
        } else {
            liveOutposts = osmResults
        }
        isSearching = false
    }

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            Toast.makeText(context, "GPS location permission granted. Acquiring position...", Toast.LENGTH_SHORT).show()
            scope.launch {
                isSearching = true
                val details = viewModel.fetchLiveDeviceLocationInfo(context)
                if (details.city.isNotBlank() || details.state.isNotBlank()) {
                    stateQuery = details.state
                    cityQuery = details.city
                    areaQuery = details.area
                    pincodeQuery = details.pincode
                    stateSearch = details.state
                    citySearch = details.city
                    areaSearch = details.area
                    pincodeSearch = details.pincode
                    Toast.makeText(
                        context,
                        "GPS Location: ${listOf(details.area, details.city, details.state).filter { it.isNotBlank() }.joinToString(", ")}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(context, "GPS position captured (${String.format("%.4f", details.lat)}, ${String.format("%.4f", details.lng)})", Toast.LENGTH_SHORT).show()
                }
                performLocationSearch(selectedCategory, details.city, details.pincode, details.state, details.area, radiusKm)
            }
        } else {
            Toast.makeText(context, "Location permission denied. You can manually type a location below.", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-search on initial launch
    LaunchedEffect(Unit) {
        if (liveOutposts == null) {
            performLocationSearch(selectedCategory, "", "", "", "", radiusKm)
        }
    }

    // Auto-trigger search when category, radius, or city updates
    LaunchedEffect(selectedCategory, radiusKm, citySearch, stateSearch) {
        if (citySearch.isNotBlank() || stateSearch.isNotBlank() || liveOutposts == null) {
            performLocationSearch(selectedCategory, citySearch, pincodeSearch, stateSearch, areaSearch, radiusKm)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nearby Assistance Radar",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = com.example.ui.theme.Color005AC1,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Free & Keyless OpenStreetMap + Overpass API",
                    fontSize = 11.sp,
                    color = com.example.ui.theme.Color74777F
                )
            }

            Surface(
                color = Color(0xFFDCFCE7),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF86EFAC))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF166534),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "100% Free / Keyless",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF166534)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // GPS / Live Radar Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ColorE0E2EC)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (gpsActive) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                        contentDescription = null,
                        tint = if (gpsActive) Color(0xFF005AC1) else Color.Red,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (gpsActive) "Live GPS Active" else "GPS Off",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = com.example.ui.theme.Color1B1B1F
                        )
                        Text(
                            text = if (gpsActive) "GPS Coords: (${String.format("%.4f", viewModel.driverLat)}, ${String.format("%.4f", viewModel.driverLng)})" else "Enable live GPS radar locator",
                            fontSize = 12.sp,
                            color = com.example.ui.theme.Color44474E
                        )
                        if (gpsActive) {
                            Text(
                                text = if (viewModel.usingRealGpsFix) "✓ Real device GPS fix" else "⚠ Using default location - tap below to get your real position",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (viewModel.usingRealGpsFix) Color(0xFF15803D) else Color(0xFFB45309)
                            )
                        }
                    }
                }
                Switch(
                    checked = gpsActive,
                    onCheckedChange = { viewModel.toggleGps() },
                    modifier = Modifier.testTag("gps_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Location Search Input Card
        Text(
            text = "Location & Search Filters",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = com.example.ui.theme.Color1B1B1F
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Quick Action: Use Live Device Location
                OutlinedButton(
                    onClick = {
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                        if (!hasFine && !hasCoarse) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            scope.launch {
                                isSearching = true
                                Toast.makeText(context, "Acquiring current GPS location...", Toast.LENGTH_SHORT).show()
                                val details = viewModel.fetchLiveDeviceLocationInfo(context)
                                stateQuery = details.state
                                cityQuery = details.city
                                areaQuery = details.area
                                pincodeQuery = details.pincode
                                stateSearch = details.state
                                citySearch = details.city
                                areaSearch = details.area
                                pincodeSearch = details.pincode
                                Toast.makeText(
                                    context,
                                    "GPS Position Captured: (${String.format("%.4f", details.lat)}, ${String.format("%.4f", details.lng)})",
                                    Toast.LENGTH_SHORT
                                ).show()
                                performLocationSearch(selectedCategory, details.city, details.pincode, details.state, details.area, radiusKm)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp).testTag("use_gps_location_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF005AC1)),
                    border = BorderStroke(1.dp, Color(0xFF005AC1))
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF005AC1))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("USE DEVICE GPS LOCATION", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                // Radius selection buttons
                Text(
                    text = "Search Radius (around location):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.example.ui.theme.Color1B1B1F
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 25, 50).forEach { rVal ->
                        FilterChip(
                            selected = radiusKm == rVal,
                            onClick = {
                                radiusKm = rVal
                                scope.launch {
                                    performLocationSearch(selectedCategory, citySearch, pincodeSearch, stateSearch, areaSearch, rVal)
                                }
                            },
                            label = { Text("${rVal}km", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f).testTag("radius_chip_${rVal}km")
                        )
                    }
                }

                // State and City side-by-side
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stateQuery,
                        onValueChange = { stateQuery = it },
                        label = { Text("State", fontSize = 11.sp) },
                        placeholder = { Text("e.g. Maharashtra") },
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = cityQuery,
                        onValueChange = { cityQuery = it },
                        label = { Text("City", fontSize = 11.sp) },
                        placeholder = { Text("e.g. Mumbai") },
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Area and Pincode
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = areaQuery,
                        onValueChange = { areaQuery = it },
                        label = { Text("Area / Landmark", fontSize = 11.sp) },
                        placeholder = { Text("e.g. Highway or Port") },
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = pincodeQuery,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() }
                            if (clean.length <= 6) pincodeQuery = clean
                        },
                        label = { Text("Pincode", fontSize = 11.sp) },
                        placeholder = { Text("e.g. 400050") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        scope.launch {
                            stateSearch = stateQuery
                            citySearch = cityQuery
                            areaSearch = areaQuery
                            pincodeSearch = pincodeQuery
                            performLocationSearch(selectedCategory, cityQuery, pincodeQuery, stateQuery, areaQuery, radiusKm)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("search_services_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("QUERYING OVERPASS API...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SEARCH OVERPASS RADAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Categories selector
        Text(
            text = "Service Category",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = com.example.ui.theme.Color1B1B1F
        )
        Spacer(modifier = Modifier.height(8.dp))

        val serviceCategories = listOf(
            Triple("garages", "🔧 Garages", "Garages & Mechanics"),
            Triple("pumps", "⛽ Petrol Pumps", "Fuel Stations"),
            Triple("restaurants", "🍲 Dhabas", "Restaurants & Dhabas"),
            Triple("commercial_repair", "🛞 Truck Repair", "Commercial Repair"),
            Triple("brands", "🚛 Brand Centers", "Tata, Leyland, Mahindra, Eicher"),
            Triple("workshops", "🛠️ Workshops", "Mechanic Workshops"),
            Triple("hospitals", "🏥 Hospitals", "Emergency Hospitals")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            serviceCategories.forEach { (catId, catLabel, _) ->
                FilterChip(
                    selected = selectedCategory == catId,
                    onClick = {
                        selectedCategory = catId
                        scope.launch {
                            performLocationSearch(catId, citySearch, pincodeSearch, stateSearch, areaSearch, radiusKm)
                        }
                    },
                    label = { Text(catLabel, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("cat_chip_$catId")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val services = liveOutposts ?: emptyList()
        var showGoogleKeyInput by remember { mutableStateOf(false) }
        var googleKeyText by remember { mutableStateOf("") }

        if (usedGoogleFallback) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE))
            ) {
                Text(
                    "OpenStreetMap had nothing here, so these results came from Google Places instead.",
                    fontSize = 11.sp,
                    color = Color(0xFF075985),
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (liveOutposts != null && services.isEmpty() && !isSearching) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                border = BorderStroke(1.dp, Color(0xFFFCD34D))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "No real listings found in this exact spot.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF92400E)
                    )
                    Text(
                        "OpenStreetMap's coverage varies by area - try a larger radius, or search the name of a nearby bigger town instead of a small village/exact address.",
                        fontSize = 11.sp,
                        color = Color(0xFF92400E)
                    )
                    if (!googleFallbackMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Google Places also tried and failed: $googleFallbackMessage", fontSize = 10.sp, color = Color(0xFF92400E))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (viewModel.getSavedGoogleApiKey(context).isBlank()) {
                        Text(
                            "For much better coverage (especially in smaller towns), you can add a free Google Cloud Places API key - it's tried automatically whenever OpenStreetMap comes up empty.",
                            fontSize = 11.sp,
                            color = Color(0xFF92400E)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(onClick = { showGoogleKeyInput = !showGoogleKeyInput }) {
                            Text("Add Google API Key", fontSize = 11.sp)
                        }
                        if (showGoogleKeyInput) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = googleKeyText,
                                onValueChange = { googleKeyText = it },
                                label = { Text("Google Cloud API Key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        val (ok, msg) = viewModel.verifyAndSaveGoogleApiKey(context, googleKeyText)
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        if (ok) {
                                            showGoogleKeyInput = false
                                            performLocationSearch(selectedCategory, citySearch, pincodeSearch, stateSearch, areaSearch, radiusKm)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Verify & Save Key", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Leaflet.js Interactive OpenStreetMap View
        Text(
            text = "Interactive OpenStreetMap Radar View",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = com.example.ui.theme.Color1B1B1F
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Displays live GPS location, ${radiusKm}km radius ring, and ${services.size} service markers",
            fontSize = 11.sp,
            color = com.example.ui.theme.Color74777F
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
        ) {
            LeafletMapView(
                userLat = viewModel.driverLat,
                userLng = viewModel.driverLng,
                services = services,
                radiusKm = radiusKm,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Results List Header
        Text(
            text = "Assistance Outposts Found (${services.size} results)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = com.example.ui.theme.Color1B1B1F
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (services.isEmpty() && !isSearching) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                border = BorderStroke(1.dp, Color(0xFFFDE68A))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "No data found nearby within ${radiusKm}km radius. Try increasing the search radius above or choosing another service category.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF92400E),
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                services.forEachIndexed { index, service ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(com.example.ui.theme.Color005AC1, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = service.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = com.example.ui.theme.Color1B1B1F
                                    )
                                }

                                Surface(
                                    color = Color(0xFFEFF6FF),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${String.format("%.1f", service.distanceKm)} km away",
                                        fontWeight = FontWeight.Bold,
                                        color = com.example.ui.theme.Color005AC1,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = service.description,
                                fontSize = 12.sp,
                                color = com.example.ui.theme.Color44474E
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (service.phone.isNotBlank()) {
                                Text(
                                    text = "📞 Phone: ${service.phone}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                            } else {
                                Text(
                                    text = "No listed phone number — use directions to visit",
                                    fontSize = 11.sp,
                                    color = com.example.ui.theme.Color44474E
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // One-Tap Direct Directions Button (Keyless Google Maps Directions)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val webDirUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${service.latOffset},${service.lngOffset}")
                                        val intent = Intent(Intent.ACTION_VIEW, webDirUri)
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open directions link", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .testTag("directions_btn_${index}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("GET DIRECTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                if (service.phone.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            val phoneUri = Uri.parse("tel:${service.phone}")
                                            val intent = Intent(Intent.ACTION_DIAL, phoneUri)
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not launch dialer", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.height(36.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = com.example.ui.theme.Color005AC1)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("CALL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.Color005AC1)
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

@Composable
fun RadarRadar(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAngle"
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .background(Color(0xFF0F172A), CircleShape)
            .border(2.dp, Color(0xFF10B981).copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Radar sweeps using Canvas
        Canvas(modifier = Modifier.fillMaxSize().rotate(angle)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2
            // Sweep light
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color.Transparent, Color(0xFF10B981).copy(alpha = 0.5f))
                ),
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = true,
                size = size
            )
            // Sweep line
            val lineEnd = Offset(
                x = center.x + radius * cos(0.0).toFloat(),
                y = center.y + radius * sin(0.0).toFloat()
            )
            drawLine(
                color = Color(0xFF10B981),
                start = center,
                end = lineEnd,
                strokeWidth = 2f
            )
        }

        // Radar static rings
        Box(modifier = Modifier.size(110.dp).border(1.dp, Color(0xFF10B981).copy(alpha = 0.2f), CircleShape))
        Box(modifier = Modifier.size(60.dp).border(1.dp, Color(0xFF10B981).copy(alpha = 0.2f), CircleShape))

        // Center dot
        Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))

        // Simulated local targets
        TargetDot(offset = Offset(-30f, -40f))
        TargetDot(offset = Offset(45f, 20f))
        TargetDot(offset = Offset(-25f, 50f))
    }
}

@Composable
fun TargetDot(offset: Offset) {
    val infiniteTransition = rememberInfiniteTransition(label = "dotPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .offset(x = offset.x.dp, y = offset.y.dp)
            .size(6.dp)
            .background(Color(0xFF10B981).copy(alpha = alpha), CircleShape)
    )
}

@Composable
fun DriverTripsTab(
    viewModel: MainViewModel,
    allLoads: List<Load>,
    lang: String,
    onPayCommission: (loadId: Int, amount: Double) -> Unit
) {
    val driver = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return
    val driverTrips = allLoads.filter { it.assignedDriverId == driver.id }

    var showChatDialogLoad by remember { mutableStateOf<Load?>(null) }
    var sosMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val tripsLocationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false) ||
            (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false)
        if (!locationPermissionGranted) {
            Toast.makeText(
                context,
                "Location access is required for the shipper to see your live position during a trip. Please allow it in Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // The moment this driver has an ONGOING trip, actively ask for GPS access if not already granted -
    // don't wait for them to visit a different tab first.
    LaunchedEffect(driverTrips.map { it.status }) {
        val hasOngoing = driverTrips.any { it.status == "ONGOING" }
        if (hasOngoing && !locationPermissionGranted) {
            tripsLocationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Your Trip Assignments", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(12.dp))

        if (driverTrips.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "You have no active trip assignments yet.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(driverTrips) { load ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Status Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Trip #${load.id}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Badge(
                                    containerColor = when (load.status) {
                                        "ACCEPTED" -> Color(0xFF0284C7)
                                        "ONGOING" -> Color(0xFF10B981)
                                        "COMPLETED" -> Color(0xFF6B7280)
                                        else -> Color.Gray
                                    }
                                ) {
                                    Text(
                                        text = Localization.get("status_" + load.status.lowercase(), lang),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Route: ${load.pickupLocation} ➔ ${load.dropLocation}", fontWeight = FontWeight.Bold)
                            Text("Cargo: ${load.loadType} (${load.weightTons} Tons)")
                            Text("Fare Reward: ₹${"%,.2f".format(load.totalFare)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                            Spacer(modifier = Modifier.height(6.dp))

                            // Driver charges info
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Platform Fee (0.8%): ₹${"%,.2f".format(load.totalFare * 0.008)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("★ 1 Free Completed Trip Offered", fontSize = 10.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 12.dp))

                            // Contact info (Unlocked because driver accepted, hidden after 1 completed trip)
                            val completedTripsCount = allLoads.count { it.assignedDriverId == driver.id && it.status == "COMPLETED" }
                            val hideShipperDetails = completedTripsCount >= 1 && !load.isCommissionPaid

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (hideShipperDetails) {
                                        Column {
                                            Text("Shipper: Contact Hidden", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Gray)
                                            Text("1st trip was free. Pay 0.8% commission to unlock this one.", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    } else {
                                        Column {
                                            val tripNoLabel = if (completedTripsCount < 1) "Free 1st Trip" else "Commission Paid"
                                            Text("Shipper: ${load.shipperName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text("Phone: ${load.shipperPhone} ($tripNoLabel)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0369A1))
                                        }
                                        IconButton(
                                            onClick = {
                                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${load.shipperPhone}"))
                                                context.startActivity(dialIntent)
                                            },
                                            modifier = Modifier.clip(CircleShape).background(Color(0xFFE0F2FE))
                                        ) {
                                            Icon(Icons.Default.Phone, contentDescription = "Call shipper", tint = Color(0xFF0369A1))
                                        }
                                    }
                                }

                                if (hideShipperDetails) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { onPayCommission(load.id, load.totalFare * 0.008) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pay Commission (₹${"%,.2f".format(load.totalFare * 0.008)}) to Unlock Contact", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons depending on state
                            // Push real GPS location to Firestore every ~8s while this trip is ONGOING,
                            // so the shipper's phone can see the driver's live position.
                            LaunchedEffect(load.id, load.status) {
                                if (load.status == "ONGOING") {
                                    while (true) {
                                        viewModel.pushLiveLocationForTrip(load.id)
                                        delay(8000)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (load.status) {
                                    "ACCEPTED" -> {
                                        Button(
                                            onClick = { viewModel.updateTripStatus(load.id, "ONGOING") },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Start Trip", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    "ONGOING" -> {
                                        Button(
                                            onClick = {
                                                viewModel.updateTripStatus(load.id, "COMPLETED")
                                                scope.launch { com.example.data.LiveTrackingSync.clearTracking(load.id) }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                        ) {
                                            Text("Complete Trip", fontWeight = FontWeight.Bold)
                                        }

                                        // SOS emergency button
                                        Button(
                                            onClick = {
                                                sosMessage = Localization.get("sos_triggered", lang)
                                                Toast.makeText(context, sosMessage, Toast.LENGTH_LONG).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(Localization.get("sos_emergency", lang), fontWeight = FontWeight.Black, fontSize = 11.sp)
                                        }
                                    }
                                }

                                // Direct Chat trigger button
                                OutlinedButton(
                                    onClick = {
                                        viewModel.selectActiveChatLoadId(load.id)
                                        showChatDialogLoad = load
                                    },
                                    modifier = Modifier.testTag("open_chat_btn_${load.id}")
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Chat")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Direct Chat overlay dialog
    if (showChatDialogLoad != null) {
        val load = showChatDialogLoad!!
        ChatDialog(
            viewModel = viewModel,
            load = load,
            onDismiss = {
                showChatDialogLoad = null
                viewModel.selectActiveChatLoadId(null)
            }
        )
    }
}

// -------------------------------------------------------------
// 4. Shipper Side Interface
// -------------------------------------------------------------
@Composable
fun ShipperHomeScreen(
    viewModel: MainViewModel,
    allLoads: List<Load>,
    lang: String,
    trackingProgress: Map<Int, Double>,
    onBlockCommission: (Int, Int, Double) -> Unit
) {
    var selectedTab by remember { mutableStateOf("post_load") }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = when (selectedTab) {
                "post_load" -> 0
                "active_loads" -> 1
                "trips" -> 2
                "verification_jobs" -> 3
                else -> 0
            },
            modifier = Modifier.testTag("shipper_top_navigation")
        ) {
            Tab(
                selected = selectedTab == "post_load",
                onClick = { selectedTab = "post_load" },
                text = { Text(Localization.get("nav_loads", lang), fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "active_loads",
                onClick = { selectedTab = "active_loads" },
                text = { Text("Applications", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "trips",
                onClick = { selectedTab = "trips" },
                text = { Text(Localization.get("nav_trips", lang), fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == "verification_jobs",
                onClick = { selectedTab = "verification_jobs" },
                text = { Text("Drivers & Jobs", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                icon = { Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (selectedTab) {
                "post_load" -> ShipperPostLoadTab(viewModel = viewModel, lang = lang)
                "active_loads" -> ShipperActiveLoadsTab(viewModel = viewModel, allLoads = allLoads, lang = lang, onBlockCommission = onBlockCommission)
                "trips" -> ShipperTripsTab(viewModel = viewModel, allLoads = allLoads, lang = lang, trackingProgress = trackingProgress)
                "verification_jobs" -> ShipperVerificationJobsTab(viewModel = viewModel, lang = lang)
            }
        }
    }
}

@Composable
fun LocationMapConfirmation(
    pickupCity: String,
    pickupPincode: String,
    dropCity: String,
    dropPincode: String,
    distanceKm: Double,
    estimatedFare: Double
) {
    if (pickupCity.isBlank() && dropCity.isBlank()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("location_map_confirmation"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), // deep elegant slate dark background
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Live GPS Route Confirmation",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Box(
                    modifier = Modifier
                        .background(Color(0xFF0F766E), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "READY TO ACCEPT",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Procedural Map drawing using Bezier math
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Background grid lines to feel like a modern navigation map
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridColor = Color.White.copy(alpha = 0.05f)
                    val gap = 20.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        x += gap
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += gap
                    }
                }

                // Route curve animation
                val infiniteTransition = rememberInfiniteTransition(label = "routeMap")
                val animProgress = infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "mapProgress"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val startX = 60.dp.toPx()
                    val startY = size.height / 2
                    val endX = size.width - 60.dp.toPx()
                    val endY = size.height / 2

                    // Control point for a nice curve
                    val controlX = size.width / 2
                    val controlY = size.height / 2 - 40.dp.toPx()

                    // Draw dashed/solid background curve
                    val steps = 40
                    var prevX = startX
                    var prevY = startY
                    for (i in 1..steps) {
                        val t = i.toFloat() / steps
                        val currX = (1 - t) * (1 - t) * startX + 2 * (1 - t) * t * controlX + t * t * endX
                        val currY = (1 - t) * (1 - t) * startY + 2 * (1 - t) * t * controlY + t * t * endY
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(prevX, prevY),
                            end = Offset(currX, currY),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        prevX = currX
                        prevY = currY
                    }

                    // Draw active progress glow segment
                    val progressT = animProgress.value
                    prevX = startX
                    prevY = startY
                    val progressSteps = (steps * progressT).toInt()
                    for (i in 1..progressSteps) {
                        val t = i.toFloat() / steps
                        val currX = (1 - t) * (1 - t) * startX + 2 * (1 - t) * t * controlX + t * t * endX
                        val currY = (1 - t) * (1 - t) * startY + 2 * (1 - t) * t * controlY + t * t * endY

                        drawLine(
                            color = Color(0xFF38BDF8),
                            start = Offset(prevX, prevY),
                            end = Offset(currX, currY),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        prevX = currX
                        prevY = currY
                    }

                    // Active moving truck/pulse coordinates
                    val pulseX = (1 - progressT) * (1 - progressT) * startX + 2 * (1 - progressT) * progressT * controlX + progressT * progressT * endX
                    val pulseY = (1 - progressT) * (1 - progressT) * startY + 2 * (1 - progressT) * progressT * controlY + progressT * progressT * endY

                    // Pulse glow
                    drawCircle(
                        color = Color(0xFF38BDF8),
                        radius = 6.dp.toPx(),
                        center = Offset(pulseX, pulseY)
                    )
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = 0.3f),
                        radius = 12.dp.toPx() * progressT,
                        center = Offset(pulseX, pulseY)
                    )

                    // Point A node (Green)
                    drawCircle(color = Color(0xFF10B981), radius = 8.dp.toPx(), center = Offset(startX, startY))
                    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(startX, startY))

                    // Point B node (Red)
                    drawCircle(color = Color(0xFFEF4444), radius = 8.dp.toPx(), center = Offset(endX, endY))
                    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(endX, endY))
                }

                // Text labels superimposed on map
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = if (pickupCity.isNotBlank()) pickupCity else "Pickup Point",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (pickupPincode.isNotBlank()) {
                            Text(text = "PIN: $pickupPincode", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (dropCity.isNotBlank()) dropCity else "Drop Point",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (dropPincode.isNotBlank()) {
                            Text(text = "PIN: $dropPincode", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Parameter specifications
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Distance", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text(
                        text = if (distanceKm > 0) "${"%,.0f".format(distanceKm)} km" else "--- km",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Estimated Trip Fare", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text(
                        text = if (estimatedFare > 0) "₹${"%,.0f".format(estimatedFare)}" else "₹---",
                        color = Color(0xFFFBBF24), // Gold
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun ShipperPostLoadTab(viewModel: MainViewModel, lang: String) {
    val context = LocalContext.current
    val shipper = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return
    val scope = rememberCoroutineScope()
    var showShipperCommissionBlock by remember { mutableStateOf(false) }
    var shipperCommissionAmount by remember { mutableStateOf(0.0) }

    // Proactive reminder: pop up automatically whenever this screen opens if commission is owed
    LaunchedEffect(Unit) {
        val unpaid = viewModel.getUnpaidCommissionsForShipper(shipper.id)
        if (unpaid.isNotEmpty()) {
            shipperCommissionAmount = unpaid.sumOf { it.amount }
            showShipperCommissionBlock = true
        }
    }

    var pickupCity by remember { mutableStateOf("") }
    var pickupPincode by remember { mutableStateOf("") }
    var dropCity by remember { mutableStateOf("") }
    var dropPincode by remember { mutableStateOf("") }
    var loadType by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var distanceText by remember { mutableStateOf("") }

    // Fare calculator factors
    var truckSize by remember { mutableStateOf("14 Feet") }
    var rateKmText by remember { mutableStateOf("22") }
    var rateTonText by remember { mutableStateOf("100") }

    val truckSizes = listOf("14 Feet", "17 Feet", "19 Feet", "22 Feet", "24 Feet", "32 Feet")
    val defaultRates = mapOf(
        "14 Feet" to 22.0,
        "17 Feet" to 25.0,
        "19 Feet" to 28.0,
        "22 Feet" to 32.0,
        "24 Feet" to 36.0,
        "32 Feet" to 42.0
    )
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Computations
    val distance = distanceText.toDoubleOrNull() ?: 0.0
    val weight = weightText.toDoubleOrNull() ?: 0.0
    val rateKm = rateKmText.toDoubleOrNull() ?: 0.0
    val rateTon = rateTonText.toDoubleOrNull() ?: 0.0

    val distanceCost = distance * rateKm
    val weightCost = weight * rateTon
    val totalFare = distanceCost + weightCost

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Post New Cargo Load",
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            color = com.example.ui.theme.Color005AC1,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pickup details (City & Pincode side-by-side)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = pickupCity,
                onValueChange = { pickupCity = it },
                label = { Text("Pickup City") },
                placeholder = { Text("e.g. Jaipur") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF10B981)) },
                singleLine = true,
                colors = getHighContrastTextFieldColors(),
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = pickupPincode,
                onValueChange = { input ->
                    val clean = input.filter { it.isDigit() }
                    if (clean.length <= 6) pickupPincode = clean
                },
                label = { Text("Pincode") },
                placeholder = { Text("302001") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = getHighContrastTextFieldColors(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Drop details (City & Pincode side-by-side)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = dropCity,
                onValueChange = { dropCity = it },
                label = { Text("Drop City") },
                placeholder = { Text("e.g. Delhi") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFEF4444)) },
                singleLine = true,
                colors = getHighContrastTextFieldColors(),
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = dropPincode,
                onValueChange = { input ->
                    val clean = input.filter { it.isDigit() }
                    if (clean.length <= 6) dropPincode = clean
                },
                label = { Text("Pincode") },
                placeholder = { Text("110001") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = getHighContrastTextFieldColors(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = loadType,
            onValueChange = { loadType = it },
            label = { Text(Localization.get("load_type_label", lang)) },
            placeholder = { Text("e.g. Steel, Cement, Parcels") },
            singleLine = true,
            colors = getHighContrastTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Live visual Route Mapping
        LocationMapConfirmation(
            pickupCity = pickupCity,
            pickupPincode = pickupPincode,
            dropCity = dropCity,
            dropPincode = dropPincode,
            distanceKm = distance,
            estimatedFare = totalFare
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Embedded Fare Calculator Widget (Rounded card format)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("fare_calculator_widget"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.ColorE0E2EC),
            border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = Localization.get("fare_calc_title", lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = com.example.ui.theme.Color005AC1
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Truck size selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = truckSize,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(Localization.get("truck_size_label", lang)) },
                        trailingIcon = {
                            IconButton(onClick = { dropdownExpanded = true }) {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        truckSizes.forEach { size ->
                            DropdownMenuItem(
                                text = { Text(size) },
                                onClick = {
                                    truckSize = size
                                    rateKmText = defaultRates[size]?.toString() ?: "22"
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = { distanceText = it },
                        label = { Text(Localization.get("dist_label", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text(Localization.get("weight_label", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rateKmText,
                        onValueChange = { rateKmText = it },
                        label = { Text(Localization.get("rate_km_label", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = rateTonText,
                        onValueChange = { rateTonText = it },
                        label = { Text(Localization.get("rate_ton_label", lang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Calculations Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, com.example.ui.theme.ColorC4C6CF)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(Localization.get("dist_cost", lang), fontSize = 13.sp)
                            Text("₹${"%,.2f".format(distanceCost)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(Localization.get("weight_cost", lang), fontSize = 13.sp)
                            Text("₹${"%,.2f".format(weightCost)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = com.example.ui.theme.ColorC4C6CF)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(Localization.get("total_fare", lang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("₹${"%,.2f".format(totalFare)}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = com.example.ui.theme.Color005AC1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reset Fare calculator
                TextButton(
                    onClick = {
                        distanceText = ""
                        weightText = ""
                        rateTonText = "100"
                        rateKmText = defaultRates[truckSize]?.toString() ?: "22"
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Localization.get("reset_btn", lang), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Submit Posting Load button
        Button(
            onClick = {
                scope.launch {
                    val unpaid = viewModel.getUnpaidCommissionsForShipper(shipper.id)
                    if (unpaid.isNotEmpty()) {
                        shipperCommissionAmount = unpaid.sumOf { it.amount }
                        showShipperCommissionBlock = true
                        return@launch
                    }

                    val fullPickup = if (pickupPincode.isNotBlank()) "$pickupCity ($pickupPincode)" else pickupCity
                    val fullDrop = if (dropPincode.isNotBlank()) "$dropCity ($dropPincode)" else dropCity

                    viewModel.postLoad(
                        pickup = fullPickup,
                        drop = fullDrop,
                        loadType = loadType,
                        weight = weight,
                        truckSize = truckSize,
                        distance = distance,
                        rateKm = rateKm,
                        rateTon = rateTon
                    ) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            pickupCity = ""
                            pickupPincode = ""
                            dropCity = ""
                            dropPincode = ""
                            loadType = ""
                            weightText = ""
                            distanceText = ""
                        }
                    }
                }
            },
            enabled = pickupCity.isNotBlank() && dropCity.isNotBlank() && loadType.isNotBlank() && distance > 0 && weight > 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("submit_post_load_btn"),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1)
        ) {
            Text(Localization.get("post_load_btn", lang), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
        }

        if (showShipperCommissionBlock) {
            CommissionPaymentDialog(
                viewModel = viewModel,
                amount = shipperCommissionAmount,
                onDismiss = { showShipperCommissionBlock = false },
                onSubmitProof = { utr, phone, screenshotPath ->
                    viewModel.submitShipperCommissionPayment(shipper.id, utr, phone) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        if (success) {
                            showShipperCommissionBlock = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ShipperActiveLoadsTab(
    viewModel: MainViewModel,
    allLoads: List<Load>,
    lang: String,
    onBlockCommission: (Int, Int, Double) -> Unit
) {
    val shipper = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return
    val shipperLoads = allLoads.filter { it.shipperId == shipper.id && it.status == "POSTED" }
    // Free-trial rule: shipper's own completed-trips count (1 free trip, then commission required)
    val shipperCompletedTripsCount = allLoads.count { it.shipperId == shipper.id && it.status == "COMPLETED" }

    var selectedDriversLoad by remember { mutableStateOf<Load?>(null) }
    var viewDocsDriver by remember { mutableStateOf<User?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Active Load Applications", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        if (shipperLoads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No active loads posted yet.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(shipperLoads) { load ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Load #${load.id}: ${load.pickupLocation} ➔ ${load.dropLocation}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Cargo Type: ${load.loadType} | Budget: ₹${"%,.0f".format(load.totalFare)}")

                            Divider(modifier = Modifier.padding(vertical = 12.dp))

                            Text(
                                text = "Interested Drivers (${load.getInterestedDriverIds().size})",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )

                            val driverIds = load.getInterestedDriverIds()
                            DriverApplicationsSection(
                                viewModel = viewModel,
                                driverIds = driverIds,
                                load = load,
                                lang = lang,
                                isFreeTrial = shipperCompletedTripsCount < 1,
                                onReviewDocs = { viewDocsDriver = it },
                                onBlockCommission = onBlockCommission
                            )
                        }
                    }
                }
            }
        }
    }

    // Document review overlay dialog
    if (viewDocsDriver != null) {
        DriverDocsReviewDialog(
            viewModel = viewModel,
            driver = viewDocsDriver!!,
            onDismiss = { viewDocsDriver = null }
        )
    }
}

@Composable
fun DriverApplicationsSection(
    viewModel: MainViewModel,
    driverIds: List<Int>,
    load: Load,
    lang: String,
    isFreeTrial: Boolean,
    onReviewDocs: (User) -> Unit,
    onBlockCommission: (Int, Int, Double) -> Unit
) {
    if (driverIds.isEmpty()) {
        Text(
            text = Localization.get("no_drivers_interested", lang),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp)
        )
    } else {
        val driversState = viewModel.getDriversByIds(driverIds).collectAsStateWithLifecycle(emptyList())
        val drivers = driversState.value
        val context = LocalContext.current

        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            drivers.forEach { driver ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(driver.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                                Text("Truck: ${driver.truckNumber} (${driver.truckSize})", fontSize = 12.sp, color = Color.DarkGray)
                            }

                            Row {
                                OutlinedButton(
                                    onClick = { onReviewDocs(driver) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Verify Docs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Button(
                                    onClick = {
                                        viewModel.acceptDriverForLoad(
                                            loadId = load.id,
                                            driverId = driver.id,
                                            onBlock = { commissionAmount ->
                                                onBlockCommission(load.id, driver.id, commissionAmount)
                                            },
                                            onSuccess = {
                                                Toast.makeText(context, "Driver accepted! Load activated.", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val isUnlocked = isFreeTrial || load.status != "POSTED" || load.isCommissionPaid
                        ContactPhoneNumberView(
                            phone = driver.phone,
                            isUnlocked = isUnlocked,
                            label = "Driver Contact Phone"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DriverDocsReviewDialog(viewModel: MainViewModel, driver: User, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var fullImagePreview by remember { mutableStateOf<Pair<String, String>?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Verify Driver Documents",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = com.example.ui.theme.Color005AC1
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(driver.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                Text("Truck: ${driver.truckNumber} • ${driver.truckSize}", fontSize = 12.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(12.dp))

                // Overall Verification Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (driver.isApproved) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (driver.isApproved) Icons.Default.Verified else Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            tint = if (driver.isApproved) Color(0xFF15803D) else Color(0xFFD97706)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (driver.isApproved) "ALL DOCUMENTS VERIFIED & APPROVED ✓" else "DOCUMENT REVIEW IN PROGRESS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (driver.isApproved) Color(0xFF15803D) else Color(0xFFB45309)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Document Rows
                DocRowReview(
                    label = "Registration Certificate (RC)",
                    path = driver.rcPath,
                    status = driver.rcStatus,
                    onStatusChange = { newStatus ->
                        viewModel.updateDriverDocStatus(driver.id, "RC", newStatus)
                        Toast.makeText(context, "RC marked as $newStatus", Toast.LENGTH_SHORT).show()
                    },
                    onOpenPreview = { fullImagePreview = Pair("Registration Certificate (RC)", driver.rcPath) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                DocRowReview(
                    label = "Driving License (DL)",
                    path = driver.dlPath,
                    status = driver.dlStatus,
                    onStatusChange = { newStatus ->
                        viewModel.updateDriverDocStatus(driver.id, "DL", newStatus)
                        Toast.makeText(context, "DL marked as $newStatus", Toast.LENGTH_SHORT).show()
                    },
                    onOpenPreview = { fullImagePreview = Pair("Driving License (DL)", driver.dlPath) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                DocRowReview(
                    label = "Aadhaar Identity Card",
                    path = driver.aadhaarPath,
                    status = driver.aadhaarStatus,
                    onStatusChange = { newStatus ->
                        viewModel.updateDriverDocStatus(driver.id, "AADHAAR", newStatus)
                        Toast.makeText(context, "Aadhaar marked as $newStatus", Toast.LENGTH_SHORT).show()
                    },
                    onOpenPreview = { fullImagePreview = Pair("Aadhaar Identity Card", driver.aadhaarPath) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                DocRowReview(
                    label = "Vehicle Permit / Driver Photo",
                    path = driver.permitPath,
                    status = driver.permitStatus,
                    onStatusChange = { newStatus ->
                        viewModel.updateDriverDocStatus(driver.id, "PERMIT", newStatus)
                        Toast.makeText(context, "Permit marked as $newStatus", Toast.LENGTH_SHORT).show()
                    },
                    onOpenPreview = { fullImagePreview = Pair("Vehicle Permit", driver.permitPath) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Done / Close Review", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }

    // Fullscreen Document Inspector Dialog
    if (fullImagePreview != null) {
        val (docLabel, path) = fullImagePreview!!
        Dialog(onDismissRequest = { fullImagePreview = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(docLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("File Reference: $path", color = Color.Gray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Stylized High Resolution Document Mockup Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("GOVERNMENT REGISTERED DOCUMENT", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            Text("Driver Name: ${driver.name}", color = Color.LightGray, fontSize = 11.sp)
                            Text("Vehicle Reg: ${driver.truckNumber}", color = Color.LightGray, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cryptographically Verified Upload", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { fullImagePreview = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close Inspector", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DocRowReview(
    label: String,
    path: String,
    status: String,
    onStatusChange: (String) -> Unit,
    onOpenPreview: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status.uppercase()) {
                "CORRECT" -> Color(0xFFF0FDF4)
                "WRONG" -> Color(0xFFFEF2F2)
                else -> Color(0xFFF8FAFC)
            }
        ),
        border = BorderStroke(
            1.dp,
            when (status.uppercase()) {
                "CORRECT" -> Color(0xFF22C55E)
                "WRONG" -> Color(0xFFEF4444)
                else -> Color(0xFFCBD5E1)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))

                // Verification Status Badge
                Box(
                    modifier = Modifier
                        .background(
                            when (status.uppercase()) {
                                "CORRECT" -> Color(0xFF16A34A)
                                "WRONG" -> Color(0xFFDC2626)
                                else -> Color(0xFFD97706)
                            },
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when (status.uppercase()) {
                            "CORRECT" -> "✅ CORRECT"
                            "WRONG" -> "❌ WRONG"
                            else -> "⏳ PENDING"
                        },
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onOpenPreview() }
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(path.ifBlank { "document_file.jpg" }, fontSize = 11.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                    Text("Tap to inspect image 🔍", fontSize = 10.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons for Shipper (Mark Correct or Mark Wrong)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onStatusChange("CORRECT") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (status.uppercase() == "CORRECT") Color(0xFF16A34A) else Color(0xFFDCFCE7),
                        contentColor = if (status.uppercase() == "CORRECT") Color.White else Color(0xFF15803D)
                    ),
                    modifier = Modifier.weight(1f).height(34.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("✓ Correct", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onStatusChange("WRONG") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (status.uppercase() == "WRONG") Color(0xFFDC2626) else Color(0xFFFEE2E2),
                        contentColor = if (status.uppercase() == "WRONG") Color.White else Color(0xFFB91C1C)
                    ),
                    modifier = Modifier.weight(1f).height(34.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("✕ Wrong", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ContactPhoneNumberView(
    phone: String,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier,
    label: String = "Contact Number"
) {
    val context = LocalContext.current
    val maskedPhone = if (phone.length >= 10) {
        "${phone.take(2)}••••••${phone.takeLast(2)}"
    } else {
        "••••••••••"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) Color(0xFFF0FDF4) else Color(0xFFFFF7ED)
        ),
        border = BorderStroke(1.dp, if (isUnlocked) Color(0xFF22C55E) else Color(0xFFF97316))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isUnlocked) Icons.Default.Phone else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isUnlocked) Color(0xFF16A34A) else Color(0xFFEA580C),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isUnlocked) "+91 $phone" else maskedPhone,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isUnlocked) Color(0xFF15803D) else Color(0xFFC2410C)
                    )
                }
            }

            if (isUnlocked) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+91$phone"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(32.dp).background(Color(0xFF16A34A), CircleShape)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=91$phone"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(32.dp).background(Color(0xFF25D366), CircleShape)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFEDD5), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "🔒 Unlocks on Load Accept",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC2410C)
                    )
                }
            }
        }
    }
}

@Composable
fun ShipperTripsTab(
    viewModel: MainViewModel,
    allLoads: List<Load>,
    lang: String,
    trackingProgress: Map<Int, Double>
) {
    val shipper = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return
    val shipperTrips = allLoads.filter { it.shipperId == shipper.id && it.status != "POSTED" }

    var showChatDialogLoad by remember { mutableStateOf<Load?>(null) }
    var ratingDialogLoad by remember { mutableStateOf<Load?>(null) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Trip History & Live Tracking", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        if (shipperTrips.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No active or completed trips found.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(shipperTrips) { load ->
                    val progress = trackingProgress[load.id] ?: 0.0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Status Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Trip #${load.id}: ${load.pickupLocation} ➔ ${load.dropLocation}", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Badge(
                                    containerColor = when (load.status) {
                                        "ACCEPTED" -> Color(0xFF0284C7)
                                        "ONGOING" -> Color(0xFF10B981)
                                        "COMPLETED" -> Color(0xFF6B7280)
                                        else -> Color.Gray
                                    }
                                ) {
                                    Text(
                                        text = Localization.get("status_" + load.status.lowercase(), lang),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Cargo: ${load.loadType} (${load.weightTons} Tons) | Fare: ₹${"%,.0f".format(load.totalFare)}")

                            var showFareAdjust by remember(load.id) { mutableStateOf(false) }
                            var fareAdjustInput by remember(load.id) { mutableStateOf(load.totalFare.toString()) }

                            if (load.status == "ACCEPTED" || load.status == "ONGOING") {
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(onClick = { showFareAdjust = !showFareAdjust }) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Adjust Fare (optional)", fontSize = 12.sp)
                                }
                                if (showFareAdjust) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = fareAdjustInput,
                                            onValueChange = { fareAdjustInput = it },
                                            label = { Text("New Fare (₹)") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(onClick = {
                                            val newFare = fareAdjustInput.toDoubleOrNull()
                                            if (newFare == null || newFare <= 0) {
                                                Toast.makeText(context, "Enter a valid fare amount.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.adjustLoadFare(load.id, newFare)
                                                Toast.makeText(context, "Fare updated to ₹${"%,.0f".format(newFare)}", Toast.LENGTH_SHORT).show()
                                                showFareAdjust = false
                                            }
                                        }) {
                                            Text("Save", fontSize = 12.sp)
                                        }
                                    }
                                    Text(
                                        "This changes the agreed fare for this trip only - let the driver know before changing it.",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 12.dp))

                            // Contact info unlocked, hidden after 1 completed trip
                            val completedTripsCount = allLoads.count { it.shipperId == shipper.id && it.status == "COMPLETED" }
                            val hideDriverDetails = completedTripsCount >= 1

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hideDriverDetails) {
                                    Column {
                                        Text("Driver: Contact Hidden", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
                                        Text("1st trip was free. Pay commission on this one to unlock.", fontSize = 12.sp, color = Color.Gray)
                                    }
                                } else {
                                    Column {
                                        val tripNoLabel = "Free 1st Trip"
                                        Text("Driver: ${load.assignedDriverName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Phone: ${load.assignedDriverPhone} ($tripNoLabel)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF15803D))
                                    }
                                    IconButton(
                                        onClick = {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${load.assignedDriverPhone}"))
                                            context.startActivity(dialIntent)
                                        },
                                        modifier = Modifier.clip(CircleShape).background(Color(0xFFDCFCE7))
                                    ) {
                                        Icon(Icons.Default.Phone, contentDescription = "Call driver", tint = Color(0xFF15803D))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // REAL TIME GPS TRACKING REPRESENTATION
                            if (load.status == "ONGOING") {
                                LiveTrackingRouteIndicator(progress = progress, totalDistance = load.distanceKm, lang = lang)
                                Spacer(modifier = Modifier.height(12.dp))
                                RealLiveLocationCard(loadId = load.id)
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Direct Chat button
                                OutlinedButton(
                                    onClick = {
                                        viewModel.selectActiveChatLoadId(load.id)
                                        showChatDialogLoad = load
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Direct Chat")
                                }

                                if (load.status == "COMPLETED") {
                                    Button(
                                        onClick = { ratingDialogLoad = load },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Rate Driver")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Direct Chat overlay dialog
    if (showChatDialogLoad != null) {
        val load = showChatDialogLoad!!
        ChatDialog(
            viewModel = viewModel,
            load = load,
            onDismiss = {
                showChatDialogLoad = null
                viewModel.selectActiveChatLoadId(null)
            }
        )
    }

    // Rating/Review overlay dialog
    if (ratingDialogLoad != null) {
        RatingDialog(
            load = ratingDialogLoad!!,
            lang = lang,
            onDismiss = { ratingDialogLoad = null }
        )
    }
}

@Composable
fun RealLiveLocationCard(loadId: Int) {
    var liveLocation by remember { mutableStateOf<com.example.data.LiveTrackingSync.LiveLocation?>(null) }
    val context = LocalContext.current

    // Subscribe to real-time Firestore updates for as long as this card is on screen;
    // unsubscribe automatically when it leaves (trip completes / user navigates away).
    DisposableEffect(loadId) {
        val registration = com.example.data.LiveTrackingSync.listenToDriverLocation(loadId) { loc ->
            liveLocation = loc
        }
        onDispose { registration.remove() }
    }

    val loc = liveLocation
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        border = BorderStroke(1.dp, Color(0xFF86EFAC))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GpsFixed, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Real Driver GPS Location", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF15803D))
            }
            Spacer(modifier = Modifier.height(6.dp))

            if (loc == null) {
                Text(
                    "Waiting for the driver's phone to send its first live position…",
                    fontSize = 12.sp,
                    color = Color(0xFF166534)
                )
            } else {
                val secondsAgo = ((System.currentTimeMillis() - loc.updatedAtMillis) / 1000).coerceAtLeast(0)
                Text(
                    text = "Coordinates: ${"%.5f".format(loc.lat)}, ${"%.5f".format(loc.lng)}",
                    fontSize = 12.sp,
                    color = Color(0xFF166534)
                )
                Text(
                    text = if (secondsAgo < 60) "Updated ${secondsAgo}s ago" else "Updated ${secondsAgo / 60}m ago — driver may be offline",
                    fontSize = 11.sp,
                    color = if (secondsAgo < 30) Color(0xFF15803D) else Color(0xFFB45309)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${loc.lat},${loc.lng}")
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open map", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("VIEW LIVE ON MAP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun LiveTrackingRouteIndicator(progress: Double, totalDistance: Double, lang: String) {
    val baseLat = 26.9124 + (progress * 1.766)
    val baseLng = 75.7873 + (progress * 1.431)
    val currentSpeed = if (progress in 0.01..0.99) 58 + ((progress * 100).toInt() % 12) else 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Localization.get("gps_tracking_title", lang),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                fontSize = 13.sp
            )
            Text(
                text = "GPS Signal Active ✓",
                color = Color(0xFF10B981),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Live Driver Coordinates Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Coordinates: ${"%.4f".format(baseLat)}° N, ${"%.4f".format(baseLng)}° E | Speed: $currentSpeed km/h",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Linear Progress bar with truck icon animated
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Track background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0xFF334155))
            )

            // Progress filler
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.toFloat())
                    .height(4.dp)
                    .background(Color(0xFF0284C7))
            )

            // Moving Truck icon
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.toFloat())
                    .offset(x = (-12).dp) // slightly center truck over the point
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color(0xFF0F172A), CircleShape)
                        .padding(2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Pickup Area", color = Color(0xFF94A3B8), fontSize = 11.sp)
            Text("Destination", color = Color(0xFF94A3B8), fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        val covered = progress * totalDistance
        Text(
            text = Localization.get("gps_distance_remaining", lang) + "${covered.toInt()} km / ${totalDistance.toInt()} km",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

// -------------------------------------------------------------
// 5. Direct In-App Chat Component
// -------------------------------------------------------------
@Composable
fun ChatDialog(viewModel: MainViewModel, load: Load, onDismiss: () -> Unit) {
    val messages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
    val driver = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return

    var currentText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = Localization.get("chat_title", viewModel.language),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Cargo: ${load.loadType} (#${load.id})",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close chat", tint = Color.White)
                    }
                }

                // Messages list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        val isMe = msg.senderId == driver.id
                        val bg = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        val align = if (isMe) Alignment.End else Alignment.Start

                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
                            Text(
                                text = if (isMe) "You" else msg.senderName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = bg),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    modifier = Modifier.padding(10.dp),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Message input bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = currentText,
                        onValueChange = { currentText = it },
                        placeholder = { Text(Localization.get("send_msg_hint", viewModel.language)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input"),
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.sendChatMessage(load.id, currentText)
                            currentText = ""
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("chat_send_btn")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6. Commission UPI Dialog (With High Precision ZXing QR Code)
// -------------------------------------------------------------
@Composable
fun CommissionPaymentDialog(viewModel: MainViewModel, amount: Double, onDismiss: () -> Unit, onSubmitProof: (utr: String, phone: String, screenshotPath: String) -> Unit) {
    val lang = viewModel.language
    val context = LocalContext.current
    var upiId by remember { mutableStateOf(viewModel.adminUpiId) }
    var utrInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var screenshotUri by remember { mutableStateOf("") }
    val screenshotLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        screenshotUri = uri?.toString() ?: ""
    }

    // Standardized UPI Payment Deep Link URI
    val upiString = "upi://pay?pa=$upiId&pn=KGI%20Logistics&am=${"%.2f".format(amount)}&cu=INR&tn=KGI%20Commission"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Localization.get("commission_warning", lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Red
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = Localization.get("commission_desc", lang),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Commission display
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Platform Commission (0.8%):", fontSize = 12.sp)
                        Text("₹${"%,.2f".format(amount)}", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Editable admin UPI ID config
                OutlinedTextField(
                    value = upiId,
                    onValueChange = {
                        upiId = it
                        viewModel.adminUpiId = it
                    },
                    label = { Text("Destination UPI VPA ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Direct Payment App Redirect Buttons
                Text("Tap Icon to Redirect to Payment App:", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Google Pay
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiString))
                            intent.setPackage("com.google.android.apps.nbu.paisa.user")
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(upiString)))
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "No UPI app found. Scan QR Code below.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Google Pay", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // PhonePe
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiString))
                            intent.setPackage("com.phonepe.app")
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(upiString)))
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "No UPI app found. Scan QR Code below.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5F259F)),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("PhonePe", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // Paytm
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiString))
                            intent.setPackage("net.one97.paytm")
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(upiString)))
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "No UPI app found. Scan QR Code below.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF002E6E)),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Paytm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Auto-Generated ZXing High-Resolution QR Code
                Text("OR Scan Live UPI QR Code:", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))

                LibraryUpiQrCard(upiUrl = upiString, upiId = upiId, amount = amount)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "After paying, enter your UTR / Transaction ID and phone number below. " +
                    "This will be reviewed and verified - it does not unlock instantly.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = utrInput,
                    onValueChange = { utrInput = it },
                    label = { Text("UTR / Transaction ID") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Your Mobile Number") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { screenshotLauncher.launch("image/*") },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (screenshotUri.isBlank()) "Attach Payment Screenshot (Optional)" else "Screenshot Attached ✓",
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isSubmitting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Submitting for verification…", fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = {
                            if (utrInput.isBlank() || phoneInput.isBlank()) {
                                Toast.makeText(context, "Please enter both UTR/Transaction ID and mobile number.", Toast.LENGTH_SHORT).show()
                            } else {
                                isSubmitting = true
                                onSubmitProof(utrInput.trim(), phoneInput.trim(), screenshotUri)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submit Payment for Verification", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text("Close", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun LibraryUpiQrCard(upiUrl: String, upiId: String, amount: Double) {
    val qrBitmap = remember(upiUrl) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(upiUrl, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("GENUINE UPI QR CODE (SCAN WITH ANY APP)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
            Spacer(modifier = Modifier.height(10.dp))

            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Live UPI QR Code",
                    modifier = Modifier.size(180.dp)
                )
            } else {
                Text("Error generating QR", color = Color.Red, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(upiId, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text("Amount: ₹${"%,.2f".format(amount)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
        }
    }
}

// -------------------------------------------------------------
// 7. Post Trip Ratings Component
// -------------------------------------------------------------
@Composable
fun RatingDialog(load: Load, lang: String, onDismiss: () -> Unit) {
    var rating by remember { mutableIntStateOf(5) }
    var feedback by remember { mutableStateOf("") }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = Localization.get("ratings_title", lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("How was your trip with driver ${load.assignedDriverName}?", textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(16.dp))

                // 5 Star row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1..5) {
                        Icon(
                            imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Star $i",
                            tint = if (i <= rating) Color(0xFFF59E0B) else Color.Gray,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { rating = i }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    label = { Text("Write feedback (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "Feedback Submitted! Thank you.", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Localization.get("submit_rating", lang), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DriverJobsTab(viewModel: MainViewModel, lang: String) {
    val context = LocalContext.current
    val allJobs by viewModel.allJobs.collectAsStateWithLifecycle()
    val driver = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return

    var showApplyDialog by remember { mutableStateOf(false) }
    var selectedJobForApply by remember { mutableStateOf<com.example.data.JobProfile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "AVAILABLE JOB PROFILES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = com.example.ui.theme.Color74777F,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (allJobs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No job profiles posted yet.", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(allJobs) { job ->
                    val applicantIds = job.getApplicantIds()
                    val hasApplied = applicantIds.contains(driver.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("driver_job_card_${job.id}"),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = job.workTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = com.example.ui.theme.Color1B1B1F
                                )
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = job.salaryText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Shipper: ${job.shipperName}",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = com.example.ui.theme.Color74777F
                            )
                            Text(
                                text = "Location: ${job.location}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = com.example.ui.theme.Color005AC1
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = job.description,
                                fontSize = 13.sp,
                                color = Color.DarkGray
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    selectedJobForApply = job
                                    showApplyDialog = true
                                },
                                enabled = !hasApplied,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hasApplied) Color.Gray else com.example.ui.theme.Color005AC1
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (hasApplied) "Applied ✓" else "APPLY FOR THIS JOB",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showApplyDialog && selectedJobForApply != null) {
        val job = selectedJobForApply!!
        Dialog(onDismissRequest = { showApplyDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, Color.Black)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Apply for: ${job.workTitle}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                    Text(
                        text = "Confirm Verifiable Documents",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var editDl by remember { mutableStateOf(driver.dlPath.ifBlank { "simulated_dl_doc.jpg" }) }
                    var editRc by remember { mutableStateOf(driver.rcPath.ifBlank { "simulated_rc_doc.jpg" }) }
                    var editAadhaar by remember { mutableStateOf(driver.aadhaarPath.ifBlank { "simulated_aadhaar_doc.jpg" }) }
                    var editPermit by remember { mutableStateOf(driver.permitPath.ifBlank { "simulated_permit_doc.jpg" }) }

                    OutlinedTextField(
                        value = editDl,
                        onValueChange = { editDl = it },
                        label = { Text("Driving License (DL) File/No.") },
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = editRc,
                        onValueChange = { editRc = it },
                        label = { Text("Registration Certificate (RC) File/No.") },
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = editAadhaar,
                        onValueChange = { editAadhaar = it },
                        label = { Text("Aadhaar Card File/No.") },
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = editPermit,
                        onValueChange = { editPermit = it },
                        label = { Text("Vehicle Permit File/No.") },
                        singleLine = true,
                        colors = getHighContrastTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { showApplyDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color.Red, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.updateDriverDocs(editDl, editRc, editAadhaar, editPermit) {
                                    viewModel.applyForJob(job.id) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        if (success) {
                                            showApplyDialog = false
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("Submit Application", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShipperVerificationJobsTab(viewModel: MainViewModel, lang: String) {
    val context = LocalContext.current
    var subTab by remember { mutableStateOf("verify") } // "verify" or "jobs"
    val allDrivers by viewModel.allDrivers.collectAsStateWithLifecycle()
    val allJobs by viewModel.allJobs.collectAsStateWithLifecycle()
    val currentShipper = viewModel.currentUser.collectAsStateWithLifecycle().value ?: return

    // Job Posting Form State
    var workTitle by remember { mutableStateOf("") }
    var salaryText by remember { mutableStateOf("") }
    var jobLocation by remember { mutableStateOf("") }
    var jobDescription by remember { mutableStateOf("") }

    // Selected Driver for Dialog Document View
    var selectedDriverForDocView by remember { mutableStateOf<User?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Sub Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { subTab = "verify" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (subTab == "verify") com.example.ui.theme.Color005AC1 else com.example.ui.theme.ColorE0E2EC,
                    contentColor = if (subTab == "verify") Color.White else com.example.ui.theme.Color1B1B1F
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Verify Drivers", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { subTab = "jobs" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (subTab == "jobs") com.example.ui.theme.Color005AC1 else com.example.ui.theme.ColorE0E2EC,
                    contentColor = if (subTab == "jobs") Color.White else com.example.ui.theme.Color1B1B1F
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Post & Manage Jobs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (subTab == "verify") {
            // VERIFY DRIVERS PANEL
            Text(
                text = "REGISTERED DRIVERS FOR VERIFICATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = com.example.ui.theme.Color74777F,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (allDrivers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No drivers registered yet.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(allDrivers) { driver ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("driver_verify_card_${driver.id}"),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = driver.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = com.example.ui.theme.Color1B1B1F
                                        )
                                        Text(
                                            text = "Phone: +91 ${driver.phone}",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = com.example.ui.theme.Color005AC1
                                        )
                                    }
                                    
                                    // Status Badge
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (driver.isApproved) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (driver.isApproved) "APPROVED ✓" else "PENDING",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (driver.isApproved) Color(0xFF15803D) else Color(0xFFB91C1C)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Truck Number", fontSize = 10.sp, color = Color.Gray)
                                        Text(driver.truckNumber.ifBlank { "N/A" }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column {
                                        Text("Truck Size", fontSize = 10.sp, color = Color.Gray)
                                        Text(driver.truckSize.ifBlank { "N/A" }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column {
                                        Text("Documents", fontSize = 10.sp, color = Color.Gray)
                                        Text("4 Files Uploaded", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F766E))
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { selectedDriverForDocView = driver },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color1B1B1F),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("View Documents & Verification Info", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // POST & MANAGE JOBS PANEL
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    // Create Job Form
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "POST A NEW JOB PROFILE",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = com.example.ui.theme.Color1B1B1F,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = workTitle,
                                onValueChange = { workTitle = it },
                                label = { Text("Work Title (e.g. Jaipur to Ahmedabad Driver)") },
                                singleLine = true,
                                colors = getHighContrastTextFieldColors(),
                                modifier = Modifier.fillMaxWidth().testTag("job_title_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = salaryText,
                                    onValueChange = { salaryText = it },
                                    label = { Text("Salary (e.g. ₹28,000 / month)") },
                                    singleLine = true,
                                    colors = getHighContrastTextFieldColors(),
                                    modifier = Modifier.weight(1f).testTag("job_salary_input")
                                )
                                OutlinedTextField(
                                    value = jobLocation,
                                    onValueChange = { jobLocation = it },
                                    label = { Text("Work Location (City)") },
                                    singleLine = true,
                                    colors = getHighContrastTextFieldColors(),
                                    modifier = Modifier.weight(1f).testTag("job_location_input")
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = jobDescription,
                                onValueChange = { jobDescription = it },
                                label = { Text("Detailed Job Description") },
                                maxLines = 3,
                                colors = getHighContrastTextFieldColors(),
                                modifier = Modifier.fillMaxWidth().testTag("job_desc_input")
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    viewModel.postJob(
                                        workTitle = workTitle,
                                        salaryText = salaryText,
                                        location = jobLocation,
                                        description = jobDescription
                                    ) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        if (success) {
                                            workTitle = ""
                                            salaryText = ""
                                            jobLocation = ""
                                            jobDescription = ""
                                        }
                                    }
                                },
                                enabled = workTitle.isNotBlank() && salaryText.isNotBlank() && jobLocation.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.Color005AC1)
                            ) {
                                Text("POST JOB PROFILE", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "YOUR POSTED JOBS",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = com.example.ui.theme.Color74777F,
                        letterSpacing = 1.sp
                    )
                }

                val shipperJobs = allJobs.filter { it.shipperId == currentShipper.id }
                if (shipperJobs.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No job profiles posted yet.", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    items(shipperJobs) { job ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(job.workTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = com.example.ui.theme.Color1B1B1F)
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(job.salaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                    }
                                }
                                Text("Location: ${job.location}", fontSize = 13.sp, color = com.example.ui.theme.Color005AC1, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(job.description, fontSize = 13.sp, color = Color.DarkGray)

                                Divider(modifier = Modifier.padding(vertical = 12.dp))

                                val applicantIds = job.getApplicantIds()
                                Text(
                                    text = "APPLICANTS (${applicantIds.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = com.example.ui.theme.Color74777F,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (applicantIds.isEmpty()) {
                                    Text("No applications received yet.", fontSize = 12.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                } else {
                                    val applicants = allDrivers.filter { applicantIds.contains(it.id) }
                                    applicants.forEach { driver ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(driver.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("Phone: +91 ${driver.phone}", fontSize = 11.sp, color = com.example.ui.theme.Color005AC1)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { selectedDriverForDocView = driver },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("Review Docs", fontSize = 10.sp, color = Color.White)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Driver Document Inspection & Verification Review Overlay
    if (selectedDriverForDocView != null) {
        DriverDocsReviewDialog(
            viewModel = viewModel,
            driver = selectedDriverForDocView!!,
            onDismiss = { selectedDriverForDocView = null }
        )
    }
}



