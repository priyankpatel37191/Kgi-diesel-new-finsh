package com.example.ui

import android.app.Application
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.KgiApplication
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import kotlin.math.roundToInt
import kotlin.random.Random
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainViewModel(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    // Language state: "en" for English, "hi" for Hindi
    var language by mutableStateOf("en")
        private set

    fun toggleLanguage() {
        language = if (language == "en") "hi" else "en"
    }

    // Current logged in user ID
    private val _currentUserId = MutableStateFlow<Int?>(null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentUser: StateFlow<User?> = _currentUserId
        .flatMapLatest { id ->
            if (id != null) repository.getUser(id)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Flag to check if current driver location tracking is enabled
    var gpsEnabled by mutableStateOf(true)
        private set

    // Simulated coordinates of driver (Jaipur area) - only used as a last-resort default
    // if GPS and network location are both unavailable. Real location overwrites this
    // as soon as it's acquired via toggleGps() or fetchLiveDeviceLocationInfo().
    var driverLat by mutableStateOf(26.9124)
        private set
    var driverLng by mutableStateOf(75.7873)
        private set

    // Tracks whether the last location acquired was a real device fix or the hardcoded default
    var usingRealGpsFix by mutableStateOf(false)
        private set

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(getApplication<Application>())
    }

    fun toggleGps() {
        gpsEnabled = !gpsEnabled
        if (gpsEnabled) {
            // BUG FIX: this previously just jittered a fixed Jaipur coordinate instead of
            // reading the real device location. Now it requests an actual GPS/network fix.
            viewModelScope.launch {
                val context = getApplication<Application>().applicationContext
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) {
                    val fix = requestFreshLocationFix()
                    if (fix != null) {
                        driverLat = fix.latitude
                        driverLng = fix.longitude
                        usingRealGpsFix = true
                        Log.d("GPSLocation", "toggleGps acquired REAL fix: (${fix.latitude}, ${fix.longitude})")
                    } else {
                        usingRealGpsFix = false
                        Log.w("GPSLocation", "toggleGps could not acquire a real fix - keeping last known coordinates")
                    }
                } else {
                    usingRealGpsFix = false
                    Log.w("GPSLocation", "toggleGps: location permission not granted")
                }
            }
        }
    }

    /**
     * Requests a single fresh, high-accuracy location fix from FusedLocationProviderClient.
     * Returns null (instead of silently falling back to a fake coordinate) if it times out,
     * GPS/network location is off, or permission is missing - callers must handle null.
     */
    @Suppress("MissingPermission")
    suspend fun requestFreshLocationFix(): Location? {
        val context = getApplication<Application>().applicationContext
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        return try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNullCompat(12000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val cts = CancellationTokenSource()
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            cts.token
                        ).addOnSuccessListener { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }.addOnFailureListener { e ->
                            Log.e("GPSLocation", "getCurrentLocation failed: ${e.message}")
                            if (cont.isActive) cont.resume(null)
                        }
                        cont.invokeOnCancellation { cts.cancel() }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GPSLocation", "requestFreshLocationFix exception: ${e.message}")
            null
        }
    }

    private suspend fun <T> withTimeoutOrNullCompat(timeoutMs: Long, block: suspend () -> T): T? {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) { block() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w("GPSLocation", "Location fix timed out after ${timeoutMs}ms")
            null
        }
    }

    /**
     * Called repeatedly (e.g. every ~8 seconds) from the DRIVER's phone while a trip is
     * ONGOING. Gets a real fresh GPS fix and pushes it to Firestore so the shipper's phone
     * can see it live. Safe to call even if permission/GPS momentarily unavailable - it just
     * skips that cycle rather than crashing.
     */
    suspend fun pushLiveLocationForTrip(loadId: Int) {
        val fix = requestFreshLocationFix() ?: return
        driverLat = fix.latitude
        driverLng = fix.longitude
        usingRealGpsFix = true
        LiveTrackingSync.pushDriverLocation(loadId, fix.latitude, fix.longitude)
    }

    // Navigation state (for simple app state navigation if desired, or backup)
    var currentScreen by mutableStateOf("landing")

    // Active Load lists
    val allLoads: StateFlow<List<Load>> = repository.getAllLoads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All registered drivers (for Shippers to review and approve/reject)
    val allDrivers: StateFlow<List<User>> = repository.getAllDrivers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live chat messages for the currently selected load
    private val _activeLoadIdForChat = MutableStateFlow<Int?>(null)
    val activeChatMessages: StateFlow<List<ChatMessage>> = _activeLoadIdForChat
        .flatMapLatest { loadId ->
            if (loadId != null) repository.getChatMessagesForLoad(loadId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real-time tracking animation progress map [LoadId -> Progress 0.0 to 1.0]
    private val _loadTrackingProgress = MutableStateFlow<Map<Int, Double>>(emptyMap())
    val loadTrackingProgress: StateFlow<Map<Int, Double>> = _loadTrackingProgress.asStateFlow()

    // Tracking tracking jobs to cancel if needed
    private val activeTrackingJobs = mutableMapOf<Int, Job>()

    // Configurable commission rate (0.5% - 1.0%), default is 0.8%
    var commissionRate by mutableStateOf(0.008)
    
    // User configured UPI ID for receiving/sending
    var adminUpiId by mutableStateOf("9660033436@pthdfc")

    // Google Cloud API Key State
    var googleApiKey by mutableStateOf("")
        private set

    var googleApiKeyVerificationStatus by mutableStateOf<String?>(null)

    var isVerifyingGoogleKey by mutableStateOf(false)

    fun loadSavedGoogleApiKey(context: Context) {
        val prefs = context.getSharedPreferences("kgi_secure_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("google_cloud_api_key", "")?.trim() ?: ""
        if (savedKey.isNotBlank()) {
            googleApiKey = savedKey
        } else {
            val buildConfigKey = try {
                val key = com.example.BuildConfig.GOOGLE_CLOUD_API_KEY
                if (key != "MY_GOOGLE_CLOUD_API_KEY" && key.isNotBlank()) key else ""
            } catch (e: Exception) {
                ""
            }
            googleApiKey = buildConfigKey
        }
    }

    fun getSavedGoogleApiKey(context: Context): String {
        if (googleApiKey.isBlank() || googleApiKey == "MY_GOOGLE_CLOUD_API_KEY") {
            loadSavedGoogleApiKey(context)
        }
        return if (googleApiKey == "MY_GOOGLE_CLOUD_API_KEY") "" else googleApiKey
    }

    fun saveGoogleApiKey(context: Context, key: String) {
        val cleanKey = key.trim()
        googleApiKey = cleanKey
        val prefs = context.getSharedPreferences("kgi_secure_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("google_cloud_api_key", cleanKey).apply()
    }

    fun maskApiKey(key: String): String {
        return if (key.length > 8) "${key.take(6)}****${key.takeLast(2)}" else "****"
    }

    private fun saveSessionUserId(userId: Int?) {
        val prefs = getApplication<Application>().getSharedPreferences("kgi_secure_prefs", Context.MODE_PRIVATE)
        if (userId != null) {
            prefs.edit().putInt("active_user_id", userId).apply()
        } else {
            prefs.edit().remove("active_user_id").apply()
        }
    }

    private fun restoreSession() {
        val prefs = getApplication<Application>().getSharedPreferences("kgi_secure_prefs", Context.MODE_PRIVATE)
        val savedUserId = prefs.getInt("active_user_id", -1)
        if (savedUserId != -1) {
            viewModelScope.launch {
                val user = repository.getUserSync(savedUserId)
                if (user != null) {
                    _currentUserId.value = user.id
                    currentScreen = if (user.role == "DRIVER") "driver_home" else "shipper_home"
                }
            }
        }
    }

    init {
        loadSavedGoogleApiKey(application.applicationContext)
        restoreSession()
        // Create demo data if empty
        viewModelScope.launch {
            delay(500) // wait for database to be fully up
            repository.getAllLoads().first().let { currentLoads ->
                if (currentLoads.isEmpty()) {
                    createDemoData()
                }
            }
        }

        // Listen for load changes from every device, live, and merge them into this
        // device's local database - this is what makes a load posted on one phone
        // actually show up (and stay updated) on another.
        com.example.data.LoadsDirectorySync.listenToAllLoads { _, remoteLoad ->
            viewModelScope.launch {
                repository.upsertLoadFromCloud(remoteLoad)
            }
        }

        // Same for commission payments - this is what lets you approve a payment from
        // ANY phone with this app installed, not just the device it was submitted from.
        com.example.data.CommissionDirectorySync.listenToAllCommissions { remoteCommission ->
            viewModelScope.launch {
                repository.upsertCommissionFromCloud(remoteCommission)
            }
        }

        // Same for job postings - a job posted on one phone now shows up for drivers everywhere.
        com.example.data.JobsDirectorySync.listenToAllJobs { remoteJob ->
            viewModelScope.launch {
                repository.upsertJobFromCloud(remoteJob)
            }
        }
    }

    private suspend fun createDemoData() {
        // Create a couple of mock shippers and drivers
        val demoShipper = User(
            id = stableIdFromPhone("9876543210"),
            name = "Rajesh Senders",
            phone = "9876543210",
            role = "SHIPPER",
            isApproved = true
        )
        val shipperId = repository.insertUser(demoShipper).toInt()

        val demoDriver = User(
            id = stableIdFromPhone("9112233445"),
            name = "Karan Singh",
            phone = "9112233445",
            role = "DRIVER",
            truckSize = "19 Feet",
            truckNumber = "RJ-14-GB-8822",
            rcPath = "demo_rc.png",
            dlPath = "demo_dl.png",
            aadhaarPath = "demo_aadhaar.png",
            permitPath = "demo_permit.png",
            isApproved = true,
            dlStatus = "CORRECT",
            rcStatus = "CORRECT",
            aadhaarStatus = "CORRECT",
            permitStatus = "CORRECT"
        )
        val driverId = repository.insertUser(demoDriver).toInt()

        // Create sample loads
        val load1 = Load(
            shipperId = shipperId,
            shipperName = "Rajesh Senders",
            shipperPhone = "9876543210",
            pickupLocation = "Jaipur Industrial Area",
            dropLocation = "Delhi Okhla Phase 3",
            loadType = "Auto Parts",
            weightTons = 6.5,
            truckSize = "19 Feet",
            distanceKm = 270.0,
            ratePerKm = 28.0,
            ratePerTon = 100.0,
            totalFare = 270.0 * 28.0 + 6.5 * 100.0, // 7560 + 650 = 8210
            status = "POSTED",
            interestedDriverIdsString = ""
        )
        repository.insertLoad(load1)

        val load2 = Load(
            shipperId = shipperId,
            shipperName = "Rajesh Senders",
            shipperPhone = "9876543210",
            pickupLocation = "Jaipur VKI",
            dropLocation = "Mumbai Kalamboli",
            loadType = "Steel Sheets",
            weightTons = 12.0,
            truckSize = "32 Feet",
            distanceKm = 1150.0,
            ratePerKm = 42.0,
            ratePerTon = 150.0,
            totalFare = 1150.0 * 42.0 + 12.0 * 150.0, // 48300 + 1800 = 50100
            status = "POSTED",
            interestedDriverIdsString = "$driverId" // driver interested
        )
        repository.insertLoad(load2)
    }

    // Phone Normalization Helper
    fun normalizePhone(phone: String): String {
        val digitsOnly = phone.filter { it.isDigit() }
        return if (digitsOnly.length >= 10) {
            digitsOnly.takeLast(10)
        } else {
            digitsOnly
        }
    }

    // Every device must agree on the same numeric ID for the same person, otherwise
    // "assigned driver #5" means a different person on every phone. Since phone numbers
    // are already unique and identical everywhere, we derive the ID from the phone number
    // itself (last 9 digits, safely inside Int range) instead of letting each device count
    // up independently from 1. This makes every cross-device data reference (assigned
    // driver, shipper, chat sender, etc.) correct without needing a lookup or coordination.
    fun stableIdFromPhone(normalizedPhone: String): Int {
        val last9 = normalizedPhone.takeLast(9)
        return last9.toIntOrNull() ?: kotlin.math.abs(normalizedPhone.hashCode())
    }

    // Authentication
    fun login(phone: String, role: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val normalized = normalizePhone(phone)
            if (normalized.length != 10) {
                onResult(false, "Invalid phone number. Must be exactly 10 digits.")
                return@launch
            }
            var existing = repository.getUserByPhoneSync(normalized)

            // Not on this device yet - check the shared cloud directory before giving up.
            // This is what makes an account created on one phone loginable from another.
            if (existing == null) {
                val cloudUser = com.example.data.UserDirectorySync.fetchUserByPhone(normalized)
                if (cloudUser != null) {
                    val newLocalId = repository.insertUser(cloudUser).toInt()
                    existing = cloudUser.copy(id = newLocalId)
                }
            }

            if (existing != null) {
                if (existing.role != role) {
                    onResult(false, "User already registered as ${existing.role}.")
                } else {
                    _currentUserId.value = existing.id
                    saveSessionUserId(existing.id)
                    currentScreen = if (existing.role == "DRIVER") "driver_home" else "shipper_home"
                    onResult(true, "Welcome back, ${existing.name}!")
                }
            } else {
                onResult(false, "User not found. Please sign up first.")
            }
        }
    }

    fun signupDriver(
        name: String,
        phone: String,
        truckSize: String,
        truckNumber: String,
        rc: String,
        dl: String,
        aadhaar: String,
        permit: String,
        insurance: String = "",
        puc: String = "",
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            if (name.isBlank() || phone.isBlank() || truckNumber.isBlank()) {
                onResult(false, "Please fill in all mandatory fields.")
                return@launch
            }
            val normalized = normalizePhone(phone)
            if (normalized.length != 10) {
                onResult(false, "Invalid phone number. Must be exactly 10 digits.")
                return@launch
            }
            val existing = repository.getUserByPhoneSync(normalized)
            if (existing != null) {
                onResult(false, "Phone number already registered as ${existing.role}.")
                return@launch
            }

            // Check the shared cloud directory too - this number might already have an
            // account from a different device, just not synced to this one yet.
            val cloudExisting = com.example.data.UserDirectorySync.fetchUserByPhone(normalized)
            if (cloudExisting != null) {
                val newLocalId = repository.insertUser(cloudExisting).toInt()
                _currentUserId.value = newLocalId
                saveSessionUserId(newLocalId)
                currentScreen = if (cloudExisting.role == "DRIVER") "driver_home" else "shipper_home"
                onResult(true, "Welcome back, ${cloudExisting.name}! Found your existing account.")
                return@launch
            }

            val newUser = User(
                id = stableIdFromPhone(normalized),
                role = "DRIVER",
                name = name,
                phone = normalized,
                truckSize = truckSize,
                truckNumber = truckNumber,
                rcPath = rc.ifBlank { "simulated_rc.jpg" },
                dlPath = dl.ifBlank { "simulated_dl.jpg" },
                aadhaarPath = aadhaar.ifBlank { "simulated_aadhaar.jpg" },
                permitPath = permit.ifBlank { "simulated_permit.jpg" },
                insurancePath = insurance.ifBlank { "simulated_insurance.jpg" },
                pucPath = puc.ifBlank { "simulated_puc.jpg" },
                isApproved = false,
                dlStatus = "PENDING",
                rcStatus = "PENDING",
                aadhaarStatus = "PENDING",
                permitStatus = "PENDING"
            )

            val newId = repository.insertUser(newUser).toInt()
            com.example.data.UserDirectorySync.pushUser(newUser.copy(id = newId))
            _currentUserId.value = newId
            saveSessionUserId(newId)
            currentScreen = "driver_home"
            onResult(true, "Driver profile created successfully!")
        }
    }

    fun signupShipper(
        name: String,
        phone: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            if (name.isBlank() || phone.isBlank()) {
                onResult(false, "Please fill in all mandatory fields.")
                return@launch
            }
            val normalized = normalizePhone(phone)
            if (normalized.length != 10) {
                onResult(false, "Invalid phone number. Must be exactly 10 digits.")
                return@launch
            }
            val existing = repository.getUserByPhoneSync(normalized)
            if (existing != null) {
                onResult(false, "Phone number already registered as ${existing.role}.")
                return@launch
            }

            val cloudExisting = com.example.data.UserDirectorySync.fetchUserByPhone(normalized)
            if (cloudExisting != null) {
                val newLocalId = repository.insertUser(cloudExisting).toInt()
                _currentUserId.value = newLocalId
                saveSessionUserId(newLocalId)
                currentScreen = if (cloudExisting.role == "DRIVER") "driver_home" else "shipper_home"
                onResult(true, "Welcome back, ${cloudExisting.name}! Found your existing account.")
                return@launch
            }

            val newUser = User(
                id = stableIdFromPhone(normalized),
                role = "SHIPPER",
                name = name,
                phone = normalized,
                isApproved = true
            )

            val newId = repository.insertUser(newUser).toInt()
            com.example.data.UserDirectorySync.pushUser(newUser.copy(id = newId))
            _currentUserId.value = newId
            saveSessionUserId(newId)
            currentScreen = "shipper_home"
            onResult(true, "Shipper account created successfully!")
        }
    }

    fun logout() {
        _currentUserId.value = null
        saveSessionUserId(null)
        currentScreen = "landing"
    }

    fun refreshData() {
        val uid = _currentUserId.value
        if (uid != null) {
            viewModelScope.launch {
                val user = repository.getUserSync(uid)
                if (user != null) {
                    _currentUserId.value = user.id
                }
            }
        }
    }

    fun updateDriverDocStatus(driverId: Int, docType: String, status: String, reason: String = "") {
        viewModelScope.launch {
            val driver = repository.getUserSync(driverId) ?: return@launch
            val updated = when (docType.uppercase()) {
                "DL" -> driver.copy(dlStatus = status)
                "RC" -> driver.copy(rcStatus = status)
                "AADHAAR" -> driver.copy(aadhaarStatus = status)
                "PERMIT", "PHOTO" -> driver.copy(permitStatus = status)
                else -> driver
            }
            val allCorrect = updated.dlStatus == "CORRECT" && updated.rcStatus == "CORRECT" && updated.aadhaarStatus == "CORRECT" && updated.permitStatus == "CORRECT"
            val finalUser = updated.copy(
                isApproved = allCorrect,
                rejectionReason = if (status == "WRONG") reason else updated.rejectionReason
            )
            repository.updateUser(finalUser)
        }
    }

    // Driver: Express interest in a load
    fun expressInterest(loadId: Int) {
        val driver = currentUser.value ?: return
        if (driver.role != "DRIVER") return

        viewModelScope.launch {
            val load = repository.getLoadByIdSync(loadId) ?: return@launch
            val updated = load.withInterestFromDriver(driver.id)
            repository.updateLoad(updated)
        }
    }

    // Shipper: Post load
    fun postLoad(
        pickup: String,
        drop: String,
        loadType: String,
        weight: Double,
        truckSize: String,
        distance: Double,
        rateKm: Double,
        rateTon: Double,
        pickupLat: Double = 0.0,
        pickupLng: Double = 0.0,
        dropLat: Double = 0.0,
        dropLng: Double = 0.0,
        onResult: (Boolean, String) -> Unit
    ) {
        val shipper = currentUser.value ?: return
        if (shipper.role != "SHIPPER") return

        viewModelScope.launch {
            if (pickup.isBlank() || drop.isBlank() || loadType.isBlank() || weight <= 0 || distance <= 0) {
                onResult(false, "Please fill all fields with valid inputs.")
                return@launch
            }

            val totalFare = (distance * rateKm) + (weight * rateTon)
            val newLoad = Load(
                cloudId = java.util.UUID.randomUUID().toString(),
                shipperId = shipper.id,
                shipperName = shipper.name,
                shipperPhone = shipper.phone,
                pickupLocation = pickup,
                dropLocation = drop,
                pickupLat = pickupLat,
                pickupLng = pickupLng,
                dropLat = dropLat,
                dropLng = dropLng,
                loadType = loadType,
                weightTons = weight,
                truckSize = truckSize,
                distanceKm = distance,
                ratePerKm = rateKm,
                ratePerTon = rateTon,
                totalFare = totalFare,
                status = "POSTED"
            )

            repository.insertLoad(newLoad)
            onResult(true, "Load posted successfully!")
        }
    }

    // Check if commission is required/paid before proceeding
    // Rules:
    // First trip between a given driver-shipper pair: commission-free
    // Second trip onward: commission is mandatory before proceeding.
    // If unpaid, blocks progress.
    suspend fun isCommissionRequiredAndUnpaid(driverId: Int, shipperId: Int, loadId: Int): Boolean {
        // Query completed loads count between this driver and shipper
        val completedCount = repository.getCompletedLoadsCountBetween(driverId, shipperId)
        
        // If this is the very first completed trip, count is 0, so free!
        // Wait, if completedCount >= 1, it means they already completed 1 trip together.
        // So the second trip onwards requires commission.
        if (completedCount == 0) return false

        // Check if there is an unpaid commission record for this specific load
        val commission = repository.getCommissionForLoadSync(loadId)
        if (commission == null) {
            // Create a pending commission record for this load
            val load = repository.getLoadByIdSync(loadId) ?: return false
            val commissionAmount = load.totalFare * commissionRate
            val newCommission = CommissionPayment(
                driverId = driverId,
                shipperId = shipperId,
                loadId = loadId,
                amount = commissionAmount,
                isPaid = false,
                upiIdUsed = adminUpiId
            )
            repository.insertCommission(newCommission)
            return true
        }
        return !commission.isPaid
    }

    // Pay commission - now requires proof (UTR + phone) and goes to SUBMITTED, NOT instantly paid.
    // An admin must verify it (see adminVerifyCommission) before it actually unlocks anything -
    // a UPI app saying "success" on the user's screen is not proof the money actually arrived.
    fun payCommission(loadId: Int, utr: String, phone: String, screenshotPath: String = "", onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (utr.trim().isBlank() || phone.trim().isBlank()) {
                onResult(false, "Please enter both UTR/Transaction ID and mobile number.")
                return@launch
            }
            val commission = repository.getCommissionForLoadSync(loadId)
            if (commission != null) {
                val updated = commission.copy(
                    utrNumber = utr.trim(),
                    payeePhone = phone.trim(),
                    verificationStatus = "SUBMITTED",
                    screenshotPath = screenshotPath
                )
                repository.updateCommission(updated)
                onResult(true, "Submitted! We'll verify your payment shortly.")
            } else {
                onResult(false, "No commission record found for this trip.")
            }
        }
    }

    // Shipper: Accept driver
    fun acceptDriverForLoad(loadId: Int, driverId: Int, onBlock: (Double) -> Unit, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val load = repository.getLoadByIdSync(loadId) ?: return@launch

            // The driver who applied may have never personally logged into THIS device before -
            // if so, their account won't be in this device's local database at all. Fall back
            // to the shared cloud directory (and cache it locally) instead of silently failing.
            var driver = repository.getUserSync(driverId)
            if (driver == null) {
                val cloudDriver = com.example.data.UserDirectorySync.fetchUserById(driverId)
                if (cloudDriver != null) {
                    repository.insertUser(cloudDriver)
                    driver = cloudDriver
                }
            }
            if (driver == null) {
                onBlock(0.0) // couldn't find the driver anywhere - surfaced via onBlock so the UI can show something instead of doing nothing
                return@launch
            }

            // Check if second trip onward commission is unpaid!
            val unpaid = isCommissionRequiredAndUnpaid(driverId, load.shipperId, loadId)
            if (unpaid) {
                val commission = repository.getCommissionForLoadSync(loadId)
                val amount = commission?.amount ?: (load.totalFare * commissionRate)
                onBlock(amount)
                return@launch
            }

            // Accept driver
            val updated = load.copy(
                assignedDriverId = driverId,
                assignedDriverName = driver.name,
                assignedDriverPhone = driver.phone,
                status = "ACCEPTED"
            )
            repository.updateLoad(updated)
            onSuccess()

            // Automatically send initial greeting in chat
            repository.insertChatMessage(
                ChatMessage(
                    loadId = loadId,
                    senderId = load.shipperId,
                    senderName = load.shipperName,
                    text = "Hello ${driver.name}, I have accepted your interest for this load. Let's arrange pickup!"
                )
            )
        }
    }

    // Driver: Update trip status (e.g. Start trip, Complete trip)
    // Optional manual fare correction by the shipper, e.g. for real-world adjustments
    // (extra weight found, route change, etc). Only affects this specific trip.
    fun adjustLoadFare(loadId: Int, newFare: Double) {
        viewModelScope.launch {
            val load = repository.getLoadByIdSync(loadId) ?: return@launch
            repository.updateLoad(load.copy(totalFare = newFare))
        }
    }

    fun updateTripStatus(loadId: Int, newStatus: String) {
        viewModelScope.launch {
            val load = repository.getLoadByIdSync(loadId) ?: return@launch
            val updated = load.copy(status = newStatus)
            repository.updateLoad(updated)

            if (newStatus == "ONGOING") {
                startTrackingAnimation(loadId)
            } else if (newStatus == "COMPLETED") {
                stopTrackingAnimation(loadId)

                // When load is completed, generate commissions if shipper/driver completed count >= 1
                val shipperId = load.shipperId
                val shipperCompletedCount = repository.getCompletedLoadsCountForShipper(shipperId)
                if (shipperCompletedCount >= 1) {
                    val commissionAmount = load.totalFare * commissionRate
                    val newCommission = CommissionPayment(
                        driverId = 0,
                        shipperId = shipperId,
                        loadId = loadId,
                        amount = commissionAmount,
                        isPaid = false,
                        upiIdUsed = adminUpiId
                    )
                    repository.insertCommission(newCommission)
                }

                val driverId = load.assignedDriverId
                if (driverId != null) {
                    val driverCompletedCount = repository.getCompletedLoadsCountForDriver(driverId)
                    if (driverCompletedCount >= 1) {
                        val commissionAmount = load.totalFare * commissionRate
                        val newCommission = CommissionPayment(
                            driverId = driverId,
                            shipperId = 0,
                            loadId = loadId,
                            amount = commissionAmount,
                            isPaid = false,
                            upiIdUsed = adminUpiId
                        )
                        repository.insertCommission(newCommission)
                    }
                }
            }
        }
    }

    // Job Posting and Application Lists & Actions
    val allJobs: StateFlow<List<JobProfile>> = repository.getAllJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun postJob(
        workTitle: String,
        salaryText: String,
        location: String,
        description: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val shipper = currentUser.value ?: return
        viewModelScope.launch {
            if (workTitle.isBlank() || salaryText.isBlank() || location.isBlank() || description.isBlank()) {
                onResult(false, "Please fill in all fields with valid information.")
                return@launch
            }
            val job = JobProfile(
                shipperId = shipper.id,
                shipperName = shipper.name,
                shipperPhone = shipper.phone,
                workTitle = workTitle,
                salaryText = salaryText,
                location = location,
                description = description
            )
            repository.insertJob(job)
            onResult(true, "Job profile posted successfully!")
        }
    }

    fun updateDriverDocs(dl: String, rc: String, aadhaar: String, permit: String, onComplete: () -> Unit) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val updated = user.copy(
                dlPath = dl,
                rcPath = rc,
                aadhaarPath = aadhaar,
                permitPath = permit
            )
            repository.updateUser(updated)
            onComplete()
        }
    }

    fun applyForJob(jobId: Int, onResult: (Boolean, String) -> Unit) {
        val driverSession = currentUser.value ?: return
        viewModelScope.launch {
            val driver = repository.getUserSync(driverSession.id) ?: return@launch
            val job = repository.getJobByIdSync(jobId) ?: return@launch
            // Verify driver has documents uploaded (Aadhaar, DL, and Photo / PermitPath)
            if (driver.aadhaarPath.isBlank() || driver.dlPath.isBlank() || driver.permitPath.isBlank()) {
                onResult(false, "Please complete your profile with Aadhaar, DL, and Photo before applying.")
                return@launch
            }
            val updated = job.withApplicant(driver.id)
            repository.updateJob(updated)
            onResult(true, "Applied successfully! Shipper can now review your documents.")
        }
    }

    suspend fun getUnpaidCommissionsForShipper(shipperId: Int): List<CommissionPayment> =
        repository.getUnpaidCommissionsForShipper(shipperId)

    suspend fun getUnpaidCommissionsForDriver(driverId: Int): List<CommissionPayment> =
        repository.getUnpaidCommissionsForDriver(driverId)

    suspend fun isShipperBlockedFromPosting(shipperId: Int): Boolean {
        val completedCount = repository.getCompletedLoadsCountForShipper(shipperId)
        if (completedCount < 1) return false
        val unpaid = repository.getUnpaidCommissionsForShipper(shipperId)
        return unpaid.isNotEmpty()
    }

    suspend fun isDriverBlockedFromApplying(driverId: Int): Boolean {
        val completedCount = repository.getCompletedLoadsCountForDriver(driverId)
        if (completedCount < 1) return false
        val unpaid = repository.getUnpaidCommissionsForDriver(driverId)
        return unpaid.isNotEmpty()
    }

    fun submitShipperCommissionPayment(shipperId: Int, utr: String, phone: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (utr.trim().isBlank() || phone.trim().isBlank()) {
                onResult(false, "Please enter both UTR/Transaction ID and mobile number.")
                return@launch
            }
            val unpaid = repository.getUnpaidCommissionsForShipper(shipperId)
            if (unpaid.isEmpty()) {
                onResult(true, "No pending commissions to pay!")
                return@launch
            }
            unpaid.forEach { comm ->
                val updated = comm.copy(utrNumber = utr.trim(), payeePhone = phone.trim(), verificationStatus = "SUBMITTED")
                repository.updateCommission(updated)
            }
            onResult(true, "Payment submitted for verification. Access unlocks once it's confirmed - usually quick.")
        }
    }

    fun submitDriverCommissionPayment(driverId: Int, utr: String, phone: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (utr.trim().isBlank() || phone.trim().isBlank()) {
                onResult(false, "Please enter both UTR/Transaction ID and mobile number.")
                return@launch
            }
            val unpaid = repository.getUnpaidCommissionsForDriver(driverId)
            if (unpaid.isEmpty()) {
                onResult(true, "No pending commissions to pay!")
                return@launch
            }
            unpaid.forEach { comm ->
                val updated = comm.copy(utrNumber = utr.trim(), payeePhone = phone.trim(), verificationStatus = "SUBMITTED")
                repository.updateCommission(updated)
            }
            onResult(true, "Payment submitted for verification. Your application/details unlock once it's confirmed.")
        }
    }

    // ---- Admin verification (manual approval since there's no paid payment-gateway API) ----
    val allCommissions: StateFlow<List<CommissionPayment>> = repository.getAllCommissions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun adminVerifyCommission(commissionId: Int, approve: Boolean, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val commission = repository.getCommissionByIdSync(commissionId)
            if (commission == null) {
                onResult(false, "Commission record not found.")
                return@launch
            }
            if (approve) {
                val updated = commission.copy(isPaid = true, verificationStatus = "VERIFIED")
                repository.updateCommission(updated)
                // Also unlock the specific load's contact info (this flag is checked in the UI)
                val load = repository.getLoadByIdSync(commission.loadId)
                if (load != null) {
                    repository.updateLoad(load.copy(isCommissionPaid = true))
                }
                onResult(true, "Payment verified. Access unlocked for this trip.")
            } else {
                val updated = commission.copy(verificationStatus = "REJECTED", utrNumber = "", payeePhone = "")
                repository.updateCommission(updated)
                onResult(true, "Payment rejected - user will need to resubmit proof.")
            }
        }
    }

    fun approveDriver(driverId: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val user = repository.getUserSync(driverId)
            if (user != null) {
                val updated = user.copy(isApproved = true)
                repository.updateUser(updated)
                onResult(true, "Driver successfully approved!")
            } else {
                onResult(false, "Driver not found.")
            }
        }
    }

    fun rejectDriver(driverId: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val user = repository.getUserSync(driverId)
            if (user != null) {
                val updated = user.copy(isApproved = false)
                repository.updateUser(updated)
                onResult(true, "Driver application rejected.")
            } else {
                onResult(false, "Driver not found.")
            }
        }
    }

    // Real-time progress tracker animation
    private fun startTrackingAnimation(loadId: Int) {
        activeTrackingJobs[loadId]?.cancel()
        val job = viewModelScope.launch {
            var progress = 0.0
            while (progress <= 1.0) {
                val currentMap = _loadTrackingProgress.value.toMutableMap()
                currentMap[loadId] = progress
                _loadTrackingProgress.value = currentMap
                progress += 0.05
                delay(2000) // update every 2 seconds
            }
            // Trip finished moving!
            val currentMap = _loadTrackingProgress.value.toMutableMap()
            currentMap[loadId] = 1.0
            _loadTrackingProgress.value = currentMap
        }
        activeTrackingJobs[loadId] = job
    }

    private fun stopTrackingAnimation(loadId: Int) {
        activeTrackingJobs[loadId]?.cancel()
        activeTrackingJobs.remove(loadId)
        val currentMap = _loadTrackingProgress.value.toMutableMap()
        currentMap.remove(loadId)
        _loadTrackingProgress.value = currentMap
    }

    // Active Chat Selection
    fun selectActiveChatLoadId(loadId: Int?) {
        _activeLoadIdForChat.value = loadId
    }

    // In-app Chat: Send message
    fun sendChatMessage(loadId: Int, text: String) {
        val user = currentUser.value ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            val msg = ChatMessage(
                loadId = loadId,
                senderId = user.id,
                senderName = user.name,
                text = text.trim()
            )
            repository.insertChatMessage(msg)
        }
    }

    fun getDriversByIds(ids: List<Int>): Flow<List<User>> = repository.getDriversByIds(ids)

    // Nearby Service categories
    val serviceCategories = listOf(
        ServiceCategory("garages", "Service Center", "build"),
        ServiceCategory("pumps", "Petrol Pumps", "local_gas_station"),
        ServiceCategory("restaurants", "Hotel Dhabas & Restaurants", "restaurant"),
        ServiceCategory("commercial_repair", "Commercial Vehicle Repair", "settings_suggest"),
        ServiceCategory("workshops", "Workshops", "precision_manufacturing"),
        ServiceCategory("hospitals", "Hospitals & Trauma", "local_hospital")
    )

    data class ServiceCategory(val id: String, val name: String, val iconName: String)
    data class NearbyService(
        val name: String,
        val distanceKm: Double,
        val phone: String,
        val description: String,
        val latOffset: Double,
        val lngOffset: Double
    )

    // Query nearby services based on category and live coordinates/search criteria
    // BUG FIX: this previously returned latOffset/lngOffset as tiny raw numbers like 0.005,
    // -0.003 which the map then used DIRECTLY as absolute latitude/longitude - placing every
    // fallback pin near (0,0), off the coast of West Africa, nowhere near the driver. Now it
    // adds those offsets to the real center coordinates so fallback pins land near the driver.
    fun getNearbyServices(
        categoryId: String,
        city: String = "",
        pincode: String = "",
        state: String = "",
        area: String = "",
        centerLat: Double = driverLat,
        centerLng: Double = driverLng
    ): List<NearbyService> {
        val displayCity = city.ifBlank { "Current Area" }.trim()
        val displayPincode = pincode.ifBlank { "" }.trim()
        val displayState = state.ifBlank { "" }.trim()
        val displayArea = area.ifBlank { "GPS Location" }.trim()

        val locString = listOf(displayArea, displayCity, displayState, displayPincode).filter { it.isNotBlank() }.joinToString(", ")
        val baseInfo = ", Location: $locString"

        val rawServices = when (categoryId) {
            "garages" -> listOf(
                NearbyService("Shree Balaji Truck Service Center", 1.2, "9660033436", "Puncture repair, tire changing, wheel alignment & air check", 0.005, -0.003),
                NearbyService("Krishna Multi-brand Commercial Hub", 2.1, "9112233445", "Authorized spares of Tata, Leyland & Eicher commercial trucks", -0.008, 0.005),
                NearbyService("National Diesel Fuel Injector Point", 2.8, "9876543210", "FIP calibrators, nozzle settings & diesel leak experts", 0.012, -0.009),
                NearbyService("Highway Mech Care & Breakdown Point", 0.4, "9660033436", "24/7 Breakdown mechanical support, suspension repairs", -0.015, 0.011),
                NearbyService("Heavy Mech Point", 1.8, "9829055443", "Engine repair, clutch overhaul & gearbox setting", 0.018, -0.014),
                NearbyService("Apex Authorized Commercial Service", 4.7, "9112233445", "Authorized Leyland mechanics, engine diagnostics", -0.022, 0.016),
                NearbyService("Royal Truck Hub & Brake Service", 5.5, "9876543210", "Air brake setting, booster replacement & drum cutting", 0.025, -0.021),
                NearbyService("Express Wheel Alignment Commercials", 0.9, "9112233445", "Laser computer alignment for 10-to-22 wheeler multi-axle trailers", -0.029, 0.024),
                NearbyService("Leaf Spring Repair (Kamaan Work)", 6.9, "9660033436", "Kamaan addition, heavy leaf spring tempering & bushes", 0.032, -0.028),
                NearbyService("Radhe Radiator & Cabin Welder Shop", 3.1, "9829055443", "Copper & aluminum radiator soldering, heavy welding, cabin denting", -0.036, 0.031),
                NearbyService("Golden Jubilee Truck Spares & Oils", 8.4, "01412555666", "Chassis lubricants, engine oils, air pressure pipelines", 0.041, -0.035),
                NearbyService("Bhawani Heavy Duty Differential Garage", 5.2, "9112233445", "Heavy truck differential repairs, hub greasing & crown wheel", -0.045, 0.039),
                NearbyService("Small Commercial Support Garage", 1.5, "9660033436", "Expert in Tata Ace, Mahindra Bolero Pickup, Dost repairs", 0.050, -0.044),
                NearbyService("Bajrang Commercial Gear Overhaul Shop", 7.0, "9876543210", "Gearbox bearing replacement, clutch finger adjusting", -0.054, 0.048),
                NearbyService("Hindustan 50-Ton Towing & Recovery Crane", 6.3, "9112233445", "Heavy hydraulic cranes for truck retrieval & highway towing", 0.061, -0.055)
            )
            "pumps" -> listOf(
                NearbyService("Indian Oil Highway Swagat Plaza", 0.8, "18002333555", "AdBlue dispenser, automated commercial vehicle wash yard, Swagat kitchen", -0.002, 0.004),
                NearbyService("Bharat Petroleum COCO Plaza", 1.9, "02222713000", "High speed diesel dispensers, truck washing bay, large dormitory", 0.008, -0.007),
                NearbyService("HP Fuel Care & Truck Oasis", 2.6, "1800116030", "Digital payments, nitrogen air, engine coolant and backup power", -0.012, 0.010),
                NearbyService("Reliance Jio-bp Transit Station", 0.5, "9876543210", "Premium diesel, clean driver restrooms, driver canteen & charging points", 0.016, -0.014),
                NearbyService("Nayara Energy Safe Truck Stop", 4.3, "9660033436", "Emergency tyre inflator, 24/7 highway store, pure fuel certified", -0.020, 0.018),
                NearbyService("Shell V-Power Commercial Pump", 5.1, "9112233445", "Premium quality Shell fuels, driver cafe, FASTag recharge center", 0.024, -0.021),
                NearbyService("Bypass HP Fuel Station", 2.0, "1800116030", "Large parking capacity for 50+ trucks, 24-hour service", -0.028, 0.025),
                NearbyService("HP CL-48 Highway Fuel Plaza", 6.8, "01412555666", "Advanced diesel filter quality check, overnight rest hub", 0.032, -0.029),
                NearbyService("BPCL Smart Line Highway Outlet", 3.5, "9829055443", "Dormitory bed, warm baths, automated token system for fuel", -0.035, 0.032),
                NearbyService("Essar Transit Truck Stop", 8.3, "9112233445", "Spacious parking lanes, free drinking water tank, lubricants store", 0.039, -0.036),
                NearbyService("IOCL Swagat Safe Parking Stop", 9.1, "18002333555", "Security guards, CCTV protected parking for cargos, drivers lounge", -0.043, 0.039),
                NearbyService("HP Auto Care Hub - Transport Nagar", 1.0, "9660033436", "FASTag help desk, oil filters, tire pressure monitors", 0.047, -0.043),
                NearbyService("BPCL Highway Hub near Toll Plaza", 10.9, "02222713000", "Emergency breakdown helper, toll info center, clean toilets", -0.051, 0.047),
                NearbyService("Indian Oil Commercial Fleet Center", 11.8, "18002333555", "Discounted fleet diesel cards accepted, rapid nozzle dispensers", 0.055, -0.051),
                NearbyService("HP Petrol Pump Bypass Corner", 12.7, "1800116030", "Mobile oil top-up, gear lubricants, windshield wipers store", -0.059, 0.055)
            )
            "restaurants" -> listOf(
                NearbyService("Dhaba Shri Balaji Veg Express", 1.5, "9414012345", "Traditional cot seating, delicious local meals, unlimited butter milk", 0.006, 0.007),
                NearbyService("Sardarji Da Famous Highway Dhaba", 2.4, "9829055443", "Spiced Amritsari Dal Makhani, hot tandoori rotis, overnight driver beds", -0.010, -0.009),
                NearbyService("Milestone Family Dhaba & Cafe", 0.6, "01412555666", "Air-conditioned dining section, hot tea stall, specialized driver meals", 0.014, 0.013),
                NearbyService("Royal Rajputana Desi Ghee Dhaba", 4.1, "9660033436", "Pure ghee Gatta Masala, Kadi khichdi, hot bajre ki roti", -0.018, -0.016),
                NearbyService("Punjab Express Highway Dhaba & Dorms", 5.0, "9112233445", "Special lassi, paneer bhurji, clay-oven tandoor, huge truck parking", 0.022, 0.021),
                NearbyService("Bypass Highway Food Plaza", 1.9, "9876543210", "Clean washrooms, continuous mineral water supply, breakfast paranthas", -0.026, -0.024),
                NearbyService("Ganesh Pure Veg Fast Food Dhaba", 6.7, "9660033436", "Sev bhaji, paneer butter masala, quick dispatch for drivers", 0.030, 0.029),
                NearbyService("NH-48 highway Tea & Parantha Point", 0.3, "9829055443", "Stuffed Aloo-Pyaj Paranthas, special ginger cardamom tea 24/7", -0.034, -0.032),
                NearbyService("Sher-E-Punjab Food Point & Sleep Rooms", 8.4, "9112233445", "Sarson ka Saag, Makki di Roti, clean sleeping cots, separate bath area", 0.038, 0.037),
                NearbyService("Hotel Highway King Premium Dhaba", 9.3, "01412555666", "Multi-cuisine, premium western toilets, South Indian thalis", -0.042, -0.040),
                NearbyService("Apna Marwadi Chhaas & Rabdi Plaza", 3.1, "9660033436", "Chilled clay-pot butter milk, local sweet Rabdi, spicy sev-tomato", 0.046, 0.045),
                NearbyService("Guru Nanak Pure Veg Punjabi Food", 11.0, "9112233445", "Mix veg, yellow dal fry, butter tandoori roti, continuous service", -0.050, -0.048),
                NearbyService("Shiv Shakti Low Cost Driver Bhojnalaya", 0.7, "9876543210", "Highly economic and nutritious full meals for truck staff", 0.054, 0.053),
                NearbyService("Bharat Highway Family Dhaba & Garden", 12.6, "9112233445", "Spacious open-air lawn dining, spiced Shahi Paneer, naans", -0.058, -0.056),
                NearbyService("The Trucker's Welcome 24/7 Tea Corner", 13.5, "9660033436", "Strong highway tea, snacks, mobile recharge and maps help desk", 0.062, 0.061)
            )
            "commercial_repair" -> listOf(
                NearbyService("Heavy Truck Chassis Straightening", 1.8, "9876543210", "Specialized laser frame alignment, heavy duty welding, axle balancing", 0.007, -0.006),
                NearbyService("National Commercial Spares & Hydraulics", 2.6, "9112233445", "Original engine mounts, tipper hydraulic pump repairs, oil seals", -0.011, 0.009),
                NearbyService("Jai Shree Ram Heavy Engine overhaul", 0.5, "9660033436", "Complete commercial engine rebuilding, ring & liner replacement", 0.015, -0.013),
                NearbyService("Speed King Air Brake System Mechanics", 4.3, "9829055443", "Air brake valve setting, booster maintenance, leakage check", -0.019, 0.016),
                NearbyService("Commercial Gearbox Repair Shop", 5.2, "9112233445", "Heavy truck transmission overhauls, clutch pressure plate replacement", 0.023, -0.020),
                NearbyService("Leaf Spring (Kamani) Fitting Point", 1.0, "9660033436", "Extra kamaan leaf insertion, heavy center bolt changing", -0.027, 0.023),
                NearbyService("Vishwakarma Tipper & Dumper Hydraulic Experts", 6.9, "9876543210", "Dumper hydraulic cylinder rebuilding, high pressure hose crimping", 0.031, -0.027),
                NearbyService("Highway Alternator & Starter Repair Point", 3.7, "9112233445", "Heavy commercial vehicle dynamic dynamo wiring, new batteries", -0.035, 0.031),
                NearbyService("Radiator Core & Condenser Welders", 8.5, "9829055443", "Radiator copper soldering, cooling fan repair, high pressure wash", 0.039, -0.035),
                NearbyService("Tractor & Heavy Truck Power Steering Works", 2.4, "9660033436", "Steering box overhaul, power steering pump sealing, tie rod change", -0.043, 0.039),
                NearbyService("Pioneer Diesel FIP Calibration & Tuning Lab", 10.2, "01412555666", "Bosch fuel pump calibrators, injector cleaning, smoke control", 0.047, -0.043),
                NearbyService("Tirupati Commercial Body & Cabin Fabricators", 11.1, "9112233445", "Chassis modification, driver cabin welding, heavy metal side sheet", -0.051, 0.047),
                NearbyService("Royal Propeller Shaft & Cross-Bearing Center", 11.9, "9876543210", "Propeller shaft alignment, universal cross joint fitting", 0.055, -0.051),
                NearbyService("Hindustan Air Brake Pipeline Sealing Point", 1.2, "9112233445", "Metal and plastic air line piping, booster valve setting", -0.059, 0.055),
                NearbyService("Ambika Heavy Commercial Turbo Care", 13.6, "9660033436", "Turbocharger turbine overhauling, wastegate settings, intercooler", 0.063, -0.059)
            )
            "workshops" -> listOf(
                NearbyService("Auto Cabin & Body Workshop", 2.2, "9660033436", "Full cabin rebuilding, sheet welding, spray painting, structural repair", 0.004, -0.005),
                NearbyService("Royal Heavy Electrical & Rewinding Workshop", 3.0, "9112233445", "Dynamo rewinding, vehicle wiring harness repairs, heavy battery charging", -0.008, 0.007),
                NearbyService("Vijay Lathe & Crankshaft Surfacing Workshop", 1.9, "9876543210", "Engine head surfacing, cylinder boring, custom thread lathe cutting", 0.012, -0.010),
                NearbyService("Shree Radhe Gas & Arc Welding Workshop", 4.7, "9829055443", "Heavy metal chassis gas cutting, trailer dumper structural welding", -0.016, 0.013),
                NearbyService("Balaji Pneumatic Tools & Compressor Workshop", 0.6, "9660033436", "Air compressors, pneumatic gun repairs, fast tyre replacement", 0.020, -0.017),
                NearbyService("Sai Ram Fuel Pump Calibration Workshop", 6.4, "9112233445", "High-precision electronic common-rail CRDI system testing", -0.024, 0.020),
                NearbyService("Cabin Seat Cushion & Glass Workshop", 2.3, "9876543210", "Driver comfort seat modification, windshield glass sealing", 0.028, -0.024),
                NearbyService("National Highway Art & Spray Paint Workshop", 8.1, "9112233445", "Traditional Indian truck art decoration, warning letters, reflective tape", -0.032, 0.027),
                NearbyService("Supreme Tyre Retreading & Vulcanizing Hub", 3.0, "9660033436", "Cold tyre retreading, advanced radial tyre hot patch vulcanizing", 0.036, -0.031),
                NearbyService("Hydraulic Pipe Crimping & Hose Workshop", 9.8, "9829055443", "Heavy hydraulic hose pipe making, high-pressure coupling", -0.040, 0.034),
                NearbyService("Jai Durga Spring Steel Heat Treatment Point", 10.7, "01412555666", "Leaf spring re-tempering, hardening, heavy industrial metal smithy", 0.044, -0.038),
                NearbyService("Bhawani Clutch Leather Riveting & Drum Workshop", 1.5, "9112233445", "Brake drum turning, clutch leather facing, heavy riveters", -0.048, 0.041),
                NearbyService("Bharat Radiator Core & Copper Welding Point", 12.4, "9876543210", "Radiator high pressure washing, block checking, core replacement", 0.052, -0.045),
                NearbyService("Durga Trailer Trailer Chassis Fabricators", 13.2, "9112233445", "Custom commercial trailer design, heavy-duty axle mounting", -0.056, 0.048),
                NearbyService("Everest Auto Dynamo carbon brush Workshop", 4.1, "9660033436", "Self starter carbon replacement, heavy truck fuse boxes wiring", 0.060, -0.052)
            )
            "hospitals" -> listOf(
                NearbyService("Highway Emergency Trauma Care Centre", 3.2, "108", "24/7 Intensive care unit, fracture surgeons, cardiac ambulance service", -0.010, 0.005),
                NearbyService("City Lifeline Multi-specialty Hospital", 1.1, "01412334455", "Emergency ward, operation theater, advanced diagnostics, blood bank", 0.015, -0.009),
                NearbyService("NH-48 Community Health & Dressing Center", 4.9, "108", "Primary treatment, saline drips, minor stitching, dehydration help", -0.019, 0.012),
                NearbyService("Apex Trauma & Orthopedic Fracture Hospital", 0.8, "9876543210", "Specialized bone setters, pain relief injections, x-ray unit", 0.023, -0.015),
                NearbyService("Bypass General Clinic & Oxygen Center", 2.6, "9112233445", "24-hour on-call doctors, critical oxygen cylinder support", -0.027, 0.018),
                NearbyService("National Highway Red Cross First-Aid Clinic", 7.5, "108", "Immediate dressings, antiseptic washing, primary painkillers", 0.031, -0.021),
                NearbyService("Life Guard Ambulance & Support near Toll Plaza", 8.3, "108", "Rapid transit ambulance service, emergency paramedical staff", -0.035, 0.024),
                NearbyService("Shree Ram General Hospital Emergency Wing", 4.2, "01412555666", "Critical care physicians, emergency medical supply, diagnostic lab", 0.039, -0.027),
                NearbyService("First-Aid Care Station", 10.0, "108", "Primary wound care, rehydration saline center, ambulance dispatch", -0.043, 0.030),
                NearbyService("Escorts Emergency Cardiac Ward", 10.9, "01412555666", "Tertiary level medical care, ventilator and advanced life support", 0.047, -0.033),
                NearbyService("Sanjeevani Specialty Burn & Injury Clinic", 3.7, "9829055443", "Emergency burn dressing, deep cleanups, continuous care", -0.051, 0.036),
                NearbyService("Golden Trauma & Medical Center", 12.6, "9112233445", "Emergency operations, 24/7 medical store, continuous ICU", 0.055, -0.039),
                NearbyService("Arogya Medical Clinic & Highway Pharmacy", 5.4, "9660033436", "First aid kits, blood pressure/sugar testing, OTC pain killers", -0.059, 0.042),
                NearbyService("Metro Mass Emergency Super-Specialty Unit", 14.3, "01412334455", "Full scale intensive care beds, immediate fracture surgeons", 0.063, -0.045),
                NearbyService("Highway Care First-Responder Emergency Post", 0.2, "108", "Free emergency medicines, first-aid box, instant helper dispatch", -0.067, 0.048)
            )
            // BUG FIX: these two categories previously had NO fallback data at all (fell through
            // to `else -> emptyList()`), so if the live Overpass query also returned nothing for
            // them, the driver saw a completely empty screen for the brand-service-centre search -
            // the exact category most requested. Added dedicated fallback lists.
            "brands" -> listOf(
                NearbyService("Tata Motors Commercial Vehicle Service Centre", 2.4, "18002097979", "Authorised Tata Motors truck & bus service, genuine spares, warranty work", 0.010, -0.008),
                NearbyService("Ashok Leyland Authorised Workshop", 3.6, "18004253969", "Authorised Ashok Leyland service, engine diagnostics, genuine parts", -0.014, 0.011),
                NearbyService("Mahindra Truck & Bus Service Centre", 1.8, "18002665006", "Authorised Mahindra commercial vehicle service and spares", 0.017, -0.013),
                NearbyService("Eicher Trucks & Buses Authorised Dealer", 4.2, "18001025858", "Authorised Eicher service, VE Commercial Vehicles genuine parts", -0.021, 0.016),
                NearbyService("SML Isuzu Authorised Service Centre", 5.5, "1800116211", "Authorised SML Isuzu truck & bus service and spares", 0.025, -0.019),
                NearbyService("BharatBenz Authorised Workshop", 6.8, "1800419001", "Authorised Daimler India BharatBenz commercial vehicle service", -0.029, 0.022)
            )
            "commercial_repair" -> listOf(
                NearbyService("Highway Commercial Tyre & Puncture Repair", 0.9, "9660033436", "Truck tyre puncture repair, retreading, wheel balancing", 0.006, -0.005),
                NearbyService("National Truck Battery & Electrical Point", 2.0, "9112233445", "Heavy vehicle battery replacement, alternator and wiring repair", -0.010, 0.008),
                NearbyService("Highway Crane & Breakdown Recovery Service", 5.1, "9876543210", "24/7 crane recovery, roadside breakdown assistance for trucks", 0.018, -0.014),
                NearbyService("Commercial Vehicle Body & Chassis Repair", 3.3, "9829055443", "Truck body repair, chassis straightening, welding", -0.024, 0.019),
                NearbyService("Truck Spare Parts & Accessories Shop", 1.4, "9660033436", "Genuine and aftermarket commercial vehicle spare parts", 0.031, -0.026),
                NearbyService("Heavy Vehicle Suspension & Brake Repair", 4.6, "9112233445", "Leaf spring, air brake, suspension repair for trucks", -0.037, 0.030)
            )
            else -> emptyList()
        }

        return rawServices.map { service ->
            val modifiedDescription = service.description + baseInfo
            // Convert stored small offsets into REAL absolute coordinates around the driver's
            // actual current/searched position, instead of leaving them as raw offsets that
            // the map was previously plotting as literal (lat, lng) near (0,0).
            service.copy(
                description = modifiedDescription,
                latOffset = centerLat + service.latOffset,
                lngOffset = centerLng + service.lngOffset
            )
        }.sortedBy { it.distanceKm }
    }

    private fun calculateHaversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val dist = r * c
        return (dist * 10).roundToInt() / 10.0
    }

    // Public wrapper so the UI can show "remaining distance to pickup/drop" live
    fun distanceBetweenKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        calculateHaversineDistanceKm(lat1, lon1, lat2, lon2)

    data class DeviceLocationDetails(
        val lat: Double,
        val lng: Double,
        val city: String,
        val state: String,
        val area: String,
        val pincode: String
    )

    suspend fun fetchLiveDeviceLocationInfo(context: Context): DeviceLocationDetails = withContext(Dispatchers.IO) {
        var acquiredLat = driverLat
        var acquiredLng = driverLng

        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (locationManager != null) {
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) {
                    val gpsLoc = try { if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) else null } catch (e: Exception) { null }
                    val netLoc = try { if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) else null } catch (e: Exception) { null }
                    val passLoc = try { locationManager.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER) } catch (e: Exception) { null }

                    var bestLoc = listOfNotNull(gpsLoc, netLoc, passLoc).maxByOrNull { it.time }

                    // BUG FIX: getLastKnownLocation only returns a CACHED fix from some other
                    // app/session and is frequently null on first run or on emulators - which
                    // previously caused this function to silently fall back to the hardcoded
                    // Jaipur coordinates without telling the driver. Now we actively request a
                    // fresh fix instead of giving up.
                    if (bestLoc == null) {
                        Log.w("GPSLocation", "No cached location available - requesting a fresh fix")
                        bestLoc = requestFreshLocationFix()
                    }

                    if (bestLoc != null) {
                        acquiredLat = bestLoc.latitude
                        acquiredLng = bestLoc.longitude
                        withContext(Dispatchers.Main) {
                            driverLat = acquiredLat
                            driverLng = acquiredLng
                            usingRealGpsFix = true
                        }
                    } else {
                        Log.e("GPSLocation", "Could not acquire any real location fix - falling back to default coordinates ($acquiredLat, $acquiredLng). Results below will NOT reflect the driver's real position.")
                        withContext(Dispatchers.Main) { usingRealGpsFix = false }
                    }
                } else {
                    Log.w("GPSLocation", "Location permission not granted - falling back to default coordinates")
                    withContext(Dispatchers.Main) { usingRealGpsFix = false }
                }
            }
        } catch (e: Exception) {
            Log.e("GPSLocation", "fetchLiveDeviceLocationInfo location acquisition failed: ${e.message}")
        }

        var resCity = ""
        var resState = ""
        var resArea = ""
        var resPincode = ""

        try {
            val reverseUrl = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$acquiredLat&lon=$acquiredLng&addressdetails=1"
            val conn = java.net.URL(reverseUrl).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "KGI-Logistics-MapEngine/1.0 (admin@kgilogistics.com)")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = org.json.JSONObject(jsonStr)
                val address = root.optJSONObject("address")
                if (address != null) {
                    resCity = address.optString("city")
                        .ifBlank { address.optString("town") }
                        .ifBlank { address.optString("village") }
                        .ifBlank { address.optString("municipality") }
                        .ifBlank { address.optString("county") }
                        .ifBlank { address.optString("state_district") }
                    resState = address.optString("state")
                    resArea = address.optString("suburb")
                        .ifBlank { address.optString("neighbourhood") }
                        .ifBlank { address.optString("road") }
                        .ifBlank { address.optString("residential") }
                    resPincode = address.optString("postcode")
                }
            }
        } catch (e: Exception) {
            // Reverse geocode failed
        }

        return@withContext DeviceLocationDetails(
            lat = acquiredLat,
            lng = acquiredLng,
            city = resCity,
            state = resState,
            area = resArea,
            pincode = resPincode
        )
    }

    // Perform live OpenStreetMap & Overpass API search for real assistance outposts (Keyless, Free)
    suspend fun searchLiveOsmOutposts(
        categoryId: String,
        city: String,
        pincode: String,
        state: String,
        area: String,
        radiusKm: Int = 10
    ): List<NearbyService> = withContext(Dispatchers.IO) {
        val displayCity = city.trim()
        val displayArea = area.trim()
        val displayState = state.trim()

        val currentLat = driverLat
        val currentLng = driverLng

        var centerLat = currentLat
        var centerLng = currentLng

        // Reverse geocoding / forward search via Nominatim with required User-Agent
        if (displayCity.isNotBlank() && !displayCity.contains("Current Location", ignoreCase = true) && !displayCity.contains("GPS", ignoreCase = true)) {
            try {
                val queryStr = listOf(displayCity, displayState).filter { it.isNotBlank() }.joinToString(", ")
                val geocodeQ = Uri.encode(queryStr)
                val geocodeUrl = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=$geocodeQ"
                val conn = java.net.URL(geocodeUrl).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "KGI-Logistics-TruckAssistance/1.0 (support@kgilogistics.com)")
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = org.json.JSONArray(jsonStr)
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        centerLat = obj.optDouble("lat", currentLat)
                        centerLng = obj.optDouble("lon", currentLng)
                    }
                }
            } catch (e: Exception) {
                Log.e("OverpassAPI", "Nominatim search error: ${e.message}")
            }
        }

        val radiusMeters = radiusKm * 1000

        Log.d("OverpassAPI", "Starting search at GPS ($centerLat, $centerLng) with radius ${radiusMeters}m for category '$categoryId'")

        val results = mutableListOf<NearbyService>()

        // Build OSM Tag Query according to specs
        val overpassFilter = when (categoryId) {
            "pumps" -> """node["amenity"="fuel"](around:$radiusMeters, $centerLat, $centerLng); way["amenity"="fuel"](around:$radiusMeters, $centerLat, $centerLng);"""
            "restaurants" -> """node["amenity"~"restaurant|fast_food"](around:$radiusMeters, $centerLat, $centerLng); way["amenity"~"restaurant|fast_food"](around:$radiusMeters, $centerLat, $centerLng);"""
            "garages" -> """node["shop"="car_repair"](around:$radiusMeters, $centerLat, $centerLng); way["shop"="car_repair"](around:$radiusMeters, $centerLat, $centerLng);"""
            // BUG FIX: was identical to "garages" (only shop=car_repair). Broadened to also
            // catch tyre/truck-specific tagging so it returns different, more relevant results.
            "commercial_repair" -> """node["shop"~"car_repair|tyres"](around:$radiusMeters, $centerLat, $centerLng); node["craft"="car_repair"](around:$radiusMeters, $centerLat, $centerLng); way["shop"~"car_repair|tyres"](around:$radiusMeters, $centerLat, $centerLng);"""
            // BUG FIX: previous regex only matched shop=car_repair|car with an exact brand name -
            // OSM rarely tags things this narrowly, so this category was very likely returning
            // zero real results. Broadened tag matching and added SML Isuzu, BharatBenz, Volvo,
            // Force Motors and Scania since OSM has no dedicated "truck brand dealer" tag.
            "brands" -> """node["name"~"Tata Motors|Ashok Leyland|Mahindra|Eicher|SML Isuzu|BharatBenz|Force Motors|Volvo Truck|Scania",i](around:$radiusMeters, $centerLat, $centerLng); way["name"~"Tata Motors|Ashok Leyland|Mahindra|Eicher|SML Isuzu|BharatBenz|Force Motors|Volvo Truck|Scania",i](around:$radiusMeters, $centerLat, $centerLng);"""
            "workshops" -> """node["craft"~"mechanic|welder"](around:$radiusMeters, $centerLat, $centerLng); node["shop"="car_repair"](around:$radiusMeters, $centerLat, $centerLng);"""
            "hospitals" -> """node["amenity"~"hospital|clinic"](around:$radiusMeters, $centerLat, $centerLng); way["amenity"~"hospital|clinic"](around:$radiusMeters, $centerLat, $centerLng);"""
            else -> """node["amenity"](around:$radiusMeters, $centerLat, $centerLng);"""
        }

        val overpassQL = "[out:json][timeout:15];($overpassFilter);out center 30;"
        Log.d("OverpassAPI", "Overpass Query Text: $overpassQL")

        // Helper function to query an Overpass endpoint
        val queryOverpassEndpoint: (String) -> String? = { endpointUrl ->
            try {
                val encodedQL = Uri.encode(overpassQL)
                val fullUrl = "$endpointUrl?data=$encodedQL"
                val conn = java.net.URL(fullUrl).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "KGI-Logistics-TruckAssistance/1.0 (support@kgilogistics.com)")
                conn.connectTimeout = 5000
                conn.readTimeout = 7000
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    Log.d("OverpassAPI", "Endpoint $endpointUrl SUCCESS. Raw JSON length: ${body.length}")
                    body
                } else {
                    Log.w("OverpassAPI", "Endpoint $endpointUrl returned status code: $code")
                    null
                }
            } catch (e: Exception) {
                Log.e("OverpassAPI", "Endpoint $endpointUrl exception: ${e.message}")
                null
            }
        }

        // 1. Try Primary Endpoint: https://overpass-api.de/api/interpreter
        var jsonStr = queryOverpassEndpoint("https://overpass-api.de/api/interpreter")

        // 2. Retry against Backup Mirror if primary failed or returned null: https://overpass.kumi.systems/api/interpreter
        if (jsonStr.isNullOrBlank()) {
            Log.d("OverpassAPI", "Primary Overpass API failed or timed out. Retrying against backup mirror...")
            jsonStr = queryOverpassEndpoint("https://overpass.kumi.systems/api/interpreter")
        }

        if (!jsonStr.isNullOrBlank()) {
            try {
                val rootObj = org.json.JSONObject(jsonStr)
                val elements = rootObj.optJSONArray("elements") ?: org.json.JSONArray()
                Log.d("OverpassAPI", "Parsed Overpass elements count: ${elements.length()}")

                for (i in 0 until elements.length()) {
                    val item = elements.getJSONObject(i)
                    val centerObj = item.optJSONObject("center")
                    val itemLat = item.optDouble("lat", centerObj?.optDouble("lat") ?: centerLat)
                    val itemLon = item.optDouble("lon", centerObj?.optDouble("lon") ?: centerLng)
                    val tags = item.optJSONObject("tags")

                    var rawName = tags?.optString("name")
                        ?.ifBlank { tags.optString("brand") }
                        ?.ifBlank { tags.optString("operator") }
                        ?.ifBlank { tags.optString("amenity") }
                        ?.ifBlank { tags.optString("shop") }
                        ?.ifBlank { tags.optString("craft") }
                        ?: ""

                    if (rawName.isBlank()) {
                        rawName = when (categoryId) {
                            "pumps" -> "Fuel Station & Petrol Pump"
                            "hospitals" -> "Emergency Hospital & Medical Care"
                            "restaurants" -> "Highway Dhaba & Restaurant"
                            "garages" -> "Auto & Truck Garage"
                            "commercial_repair" -> "Commercial Vehicle Repair Shop"
                            "brands" -> "Authorized Truck Service Center"
                            "workshops" -> "Mechanical Workshop"
                            else -> "Truck Assistance Outpost"
                        }
                    }

                    val street = tags?.optString("addr:street") ?: ""
                    val suburb = tags?.optString("addr:suburb") ?: tags?.optString("addr:neighbourhood") ?: ""
                    val town = tags?.optString("addr:city") ?: displayCity
                    val phoneStr = tags?.optString("phone")
                        ?.ifBlank { tags.optString("contact:phone") }
                        ?: ""

                    val addrParts = listOf(street, suburb, town).filter { it.isNotBlank() }
                    val fullAddr = if (addrParts.isNotEmpty()) addrParts.joinToString(", ") else if (displayCity.isNotBlank()) "$displayCity Area" else "Highway Zone"

                    val realDist = calculateHaversineDistanceKm(currentLat, currentLng, itemLat, itemLon)

                    if (results.none { it.name.equals(rawName.trim(), ignoreCase = true) }) {
                        results.add(
                            NearbyService(
                                name = rawName.trim(),
                                distanceKm = realDist,
                                phone = phoneStr,
                                description = "Address: $fullAddr (OSM Node #${item.optLong("id", i.toLong())})",
                                latOffset = itemLat,
                                lngOffset = itemLon
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("OverpassAPI", "Error parsing Overpass JSON response: ${e.message}")
            }
        }

        if (results.isEmpty()) {
            Log.d("OverpassAPI", "Overpass returned 0 real results for this area/category. Returning empty (no fake data).")
            return@withContext emptyList()
        } else {
            return@withContext results.sortedBy { it.distanceKm }
        }
    }

    data class GooglePlacesResult(
        val services: List<NearbyService>,
        val errorMessage: String? = null,
        val apiStatus: String = "OK",
        val isKeyMissing: Boolean = false,
        val totalParsed: Int = 0
    )

    suspend fun verifyAndSaveGoogleApiKey(context: Context, keyToTest: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanKey = keyToTest.trim()
        if (cleanKey.isBlank()) {
            val err = "API key cannot be empty."
            withContext(Dispatchers.Main) {
                googleApiKeyVerificationStatus = err
            }
            return@withContext Pair(false, err)
        }

        withContext(Dispatchers.Main) {
            isVerifyingGoogleKey = true
            googleApiKeyVerificationStatus = null
        }

        val maskedKey = maskApiKey(cleanKey)
        val testUrlStr = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=26.9124,75.7873&radius=1000&type=gas_station&key=$cleanKey"
        val logMaskedUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=26.9124,75.7873&radius=1000&type=gas_station&key=$maskedKey"

        Log.d("GooglePlacesAPI", "Testing API Key with Request URL: $logMaskedUrl")

        try {
            val url = java.net.URL(testUrlStr)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "KGI-Logistics-GoogleMapEngine/1.0")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            val statusCode = conn.responseCode
            Log.d("GooglePlacesAPI", "Test Connection HTTP Status Code: $statusCode")

            val responseText = if (statusCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (statusCode != 200) {
                val failResult = "Invalid Key ❌ [HTTP $statusCode]: ${conn.responseMessage} ($responseText)"
                Log.e("GooglePlacesAPI", "Verification failed: $failResult")
                withContext(Dispatchers.Main) {
                    isVerifyingGoogleKey = false
                    googleApiKeyVerificationStatus = failResult
                }
                return@withContext Pair(false, failResult)
            }

            val json = org.json.JSONObject(responseText)
            val status = json.optString("status")
            val errorMessage = json.optString("error_message")
            val resultsCount = json.optJSONArray("results")?.length() ?: 0

            Log.d("GooglePlacesAPI", "Test Response Status: $status, Results count: $resultsCount, Error: $errorMessage")

            if (status == "OK" || status == "ZERO_RESULTS") {
                val successMsg = "API Key Verified ✅ (Status: $status)"
                saveGoogleApiKey(context, cleanKey)
                withContext(Dispatchers.Main) {
                    isVerifyingGoogleKey = false
                    googleApiKeyVerificationStatus = successMsg
                }
                return@withContext Pair(true, successMsg)
            } else {
                val failResult = "Invalid Key ❌ [$status]: ${errorMessage.ifBlank { "Google Places API rejected key" }}"
                Log.e("GooglePlacesAPI", "Verification status failed: $failResult")
                withContext(Dispatchers.Main) {
                    isVerifyingGoogleKey = false
                    googleApiKeyVerificationStatus = failResult
                }
                return@withContext Pair(false, failResult)
            }
        } catch (e: Exception) {
            val errText = "Invalid Key ❌ Network Error: ${e.localizedMessage ?: "Connection error"}"
            Log.e("GooglePlacesAPI", "Exception testing key: ${e.message}", e)
            withContext(Dispatchers.Main) {
                isVerifyingGoogleKey = false
                googleApiKeyVerificationStatus = errText
            }
            return@withContext Pair(false, errText)
        }
    }

    suspend fun searchLiveGooglePlaces(
        context: Context,
        categoryId: String,
        city: String,
        pincode: String,
        state: String,
        area: String
    ): GooglePlacesResult = withContext(Dispatchers.IO) {
        val storedKey = getSavedGoogleApiKey(context)
        if (storedKey.isBlank()) {
            Log.w("GooglePlacesAPI", "Search aborted: Google Cloud API key is missing in secure prefs")
            return@withContext GooglePlacesResult(
                services = emptyList(),
                errorMessage = "Please add your Google Cloud API key in Settings to use Google Places live search.",
                isKeyMissing = true
            )
        }

        val maskedKey = maskApiKey(storedKey)
        val displayCity = city.trim()
        val displayState = state.trim()
        val currentLat = driverLat
        val currentLng = driverLng

        var centerLat = currentLat
        var centerLng = currentLng

        // 1. Geocoding API if city specified
        if (displayCity.isNotBlank() && !displayCity.contains("Current Location", ignoreCase = true) && !displayCity.contains("GPS", ignoreCase = true)) {
            try {
                val queryStr = listOf(displayCity, displayState, "India").filter { it.isNotBlank() }.joinToString(", ")
                val geoAddr = Uri.encode(queryStr)
                val geocodeUrl = "https://maps.googleapis.com/maps/api/geocode/json?address=$geoAddr&key=$storedKey"
                val maskedGeocodeUrl = "https://maps.googleapis.com/maps/api/geocode/json?address=$geoAddr&key=$maskedKey"

                Log.d("GooglePlacesAPI", "Geocoding Request URL: $maskedGeocodeUrl")

                val geoConn = java.net.URL(geocodeUrl).openConnection() as java.net.HttpURLConnection
                geoConn.requestMethod = "GET"
                geoConn.setRequestProperty("User-Agent", "KGI-Logistics-GoogleMapEngine/1.0")
                geoConn.connectTimeout = 4000
                geoConn.readTimeout = 4000

                val geoCode = geoConn.responseCode
                Log.d("GooglePlacesAPI", "Geocoding HTTP Status Code: $geoCode")

                if (geoCode == 200) {
                    val jsonStr = geoConn.inputStream.bufferedReader().use { it.readText() }
                    val root = org.json.JSONObject(jsonStr)
                    val status = root.optString("status")
                    val resultsArr = root.optJSONArray("results")
                    Log.d("GooglePlacesAPI", "Geocoding API Status: $status, Results count: ${resultsArr?.length() ?: 0}")

                    if (resultsArr != null && resultsArr.length() > 0) {
                        val locationObj = resultsArr.getJSONObject(0).optJSONObject("geometry")?.optJSONObject("location")
                        if (locationObj != null) {
                            centerLat = locationObj.optDouble("lat", currentLat)
                            centerLng = locationObj.optDouble("lng", currentLng)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GooglePlacesAPI", "Geocoding Exception: ${e.message}")
            }
        }

        val placeType = when (categoryId) {
            "pumps" -> "gas_station"
            "restaurants" -> "restaurant"
            "garages" -> "car_repair"
            "commercial_repair" -> "car_repair"
            "workshops" -> "car_repair"
            "hospitals" -> "hospital"
            else -> "gas_station"
        }

        val results = mutableListOf<NearbyService>()
        var mainApiStatus = "OK"

        // 2. Places API Nearby Search
        try {
            val nearbyUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=$centerLat,$centerLng&radius=25000&type=$placeType&key=$storedKey"
            val maskedNearbyUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=$centerLat,$centerLng&radius=25000&type=$placeType&key=$maskedKey"

            Log.d("GooglePlacesAPI", "Places Nearby Search Request URL: $maskedNearbyUrl")

            val conn = java.net.URL(nearbyUrl).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "KGI-Logistics-GoogleMapEngine/1.0")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val statusCode = conn.responseCode
            Log.d("GooglePlacesAPI", "Places Nearby Search HTTP Status Code: $statusCode")

            val responseText = if (statusCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (statusCode != 200) {
                val err = "Google Places API HTTP $statusCode: ${conn.responseMessage}. $responseText"
                Log.e("GooglePlacesAPI", "Nearby Search failed: $err")
                return@withContext GooglePlacesResult(
                    services = emptyList(),
                    errorMessage = err,
                    apiStatus = "HTTP_$statusCode"
                )
            }

            val json = org.json.JSONObject(responseText)
            mainApiStatus = json.optString("status")
            val errorMsg = json.optString("error_message")

            val resultsArr = json.optJSONArray("results") ?: org.json.JSONArray()
            Log.d("GooglePlacesAPI", "Places Nearby Search API Status: $mainApiStatus, Results parsed: ${resultsArr.length()}")

            if (mainApiStatus != "OK" && mainApiStatus != "ZERO_RESULTS") {
                val err = "Google Places API Error [$mainApiStatus]: ${errorMsg.ifBlank { "API call was rejected by Google Cloud" }}"
                Log.e("GooglePlacesAPI", err)
                return@withContext GooglePlacesResult(
                    services = emptyList(),
                    errorMessage = err,
                    apiStatus = mainApiStatus
                )
            }

            for (i in 0 until resultsArr.length()) {
                val item = resultsArr.getJSONObject(i)
                val name = item.optString("name", "Nearby Center")
                val geom = item.optJSONObject("geometry")?.optJSONObject("location")
                val itemLat = geom?.optDouble("lat", centerLat) ?: centerLat
                val itemLng = geom?.optDouble("lng", centerLng) ?: centerLng

                val vicinity = item.optString("vicinity")
                    .ifBlank { item.optString("formatted_address") }
                    .ifBlank { "Location details" }

                val rating = item.optDouble("rating", 0.0)
                val userRatingsTotal = item.optInt("user_ratings_total", 0)
                val openNowObj = item.optJSONObject("opening_hours")
                val openNow = if (openNowObj != null && openNowObj.has("open_now")) openNowObj.getBoolean("open_now") else null

                val dist = calculateHaversineDistanceKm(currentLat, currentLng, itemLat, itemLng)

                val ratingStr = if (rating > 0) "⭐ ${String.format("%.1f", rating)} ($userRatingsTotal)" else ""
                val statusStr = if (openNow == true) "🟢 Open Now" else if (openNow == false) "🔴 Closed" else ""

                val descParts = listOf("Address: $vicinity", ratingStr, statusStr).filter { it.isNotBlank() }
                val fullDesc = descParts.joinToString(" | ")

                if (results.none { it.name.equals(name.trim(), ignoreCase = true) }) {
                    results.add(
                        NearbyService(
                            name = name.trim(),
                            distanceKm = dist,
                            phone = "Call via Google Maps",
                            description = fullDesc,
                            latOffset = itemLat,
                            lngOffset = itemLng
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GooglePlacesAPI", "Exception during Nearby Search: ${e.message}", e)
        }

        // 3. Brand-specific Text Search queries for truck drivers
        if (categoryId == "garages" || categoryId == "commercial_repair" || categoryId == "workshops") {
            val brands = listOf("Tata Motors commercial service center", "Ashok Leyland service center", "Mahindra truck service center", "Eicher commercial workshop")
            for (brandQuery in brands) {
                try {
                    val encodedQuery = Uri.encode(brandQuery)
                    val textUrl = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=$encodedQuery&location=$centerLat,$centerLng&radius=25000&key=$storedKey"
                    val maskedTextUrl = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=$encodedQuery&location=$centerLat,$centerLng&radius=25000&key=$maskedKey"

                    Log.d("GooglePlacesAPI", "Brand Text Search Request URL: $maskedTextUrl")

                    val conn = java.net.URL(textUrl).openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "KGI-Logistics-GoogleMapEngine/1.0")
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000

                    val statusCode = conn.responseCode
                    Log.d("GooglePlacesAPI", "Brand Text Search HTTP Status Code: $statusCode")

                    if (statusCode == 200) {
                        val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = org.json.JSONObject(jsonStr)
                        val status = json.optString("status")
                        val resultsArr = json.optJSONArray("results") ?: org.json.JSONArray()

                        Log.d("GooglePlacesAPI", "Brand Text Search Status: $status, Results parsed: ${resultsArr.length()}")

                        for (i in 0 until minOf(resultsArr.length(), 3)) {
                            val item = resultsArr.getJSONObject(i)
                            val name = item.optString("name")
                            val geom = item.optJSONObject("geometry")?.optJSONObject("location")
                            val itemLat = geom?.optDouble("lat", centerLat) ?: centerLat
                            val itemLng = geom?.optDouble("lng", centerLng) ?: centerLng

                            val address = item.optString("formatted_address").ifBlank { item.optString("vicinity") }
                            val rating = item.optDouble("rating", 0.0)
                            val dist = calculateHaversineDistanceKm(currentLat, currentLng, itemLat, itemLng)
                            val ratingStr = if (rating > 0) "⭐ ${String.format("%.1f", rating)}" else ""

                            val fullDesc = listOf("Address: $address", ratingStr, "Authorized Commercial Service").filter { it.isNotBlank() }.joinToString(" | ")

                            if (name.isNotBlank() && results.none { it.name.equals(name.trim(), ignoreCase = true) }) {
                                results.add(
                                    NearbyService(
                                        name = name.trim(),
                                        distanceKm = dist,
                                        phone = "Authorized Helpline",
                                        description = fullDesc,
                                        latOffset = itemLat,
                                        lngOffset = itemLng
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GooglePlacesAPI", "Text Search exception for $brandQuery: ${e.message}")
                }
            }
        }

        val finalSorted = results.sortedBy { it.distanceKm }
        Log.d("GooglePlacesAPI", "Total Google Places results returned & rendered: ${finalSorted.size}")

        return@withContext GooglePlacesResult(
            services = finalSorted,
            errorMessage = if (finalSorted.isEmpty() && mainApiStatus != "OK") "Google Places API Error [$mainApiStatus]" else null,
            apiStatus = mainApiStatus,
            totalParsed = finalSorted.size
        )
    }
}

class MainViewModelFactory(
    private val application: Application,
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
