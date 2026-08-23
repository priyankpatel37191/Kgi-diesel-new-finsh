package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.example.data.LiveTrackingSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps sharing a driver's live location while the app is closed/backgrounded (not force-quit
 * or uninstalled - see the honest limits explained where this is started).
 *
 * IMPORTANT REALITY CHECK, not a bug:
 *  - If the user force-stops the app from Android's app-info screen, or uninstalls it, this
 *    service is destroyed along with everything else - no app can survive either of those.
 *  - Some phone brands (Xiaomi/MIUI, Oppo, Vivo, some Samsung battery savers) aggressively
 *    kill background services regardless of what any app does, to save battery. This is a
 *    well-known Android ecosystem limitation, not something fixable purely in this app's code -
 *    the user may need to disable battery optimization for this app in their phone's settings
 *    for genuinely reliable long-term background tracking.
 */
class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var driverId: Int = -1

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location: Location = result.lastLocation ?: return
            if (driverId != -1) {
                serviceScope.launch {
                    LiveTrackingSync.pushContinuousDriverLocation(driverId, location.latitude, location.longitude)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        driverId = intent?.getIntExtra(EXTRA_DRIVER_ID, -1) ?: -1
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        // START_STICKY: ask Android to restart this service if it gets killed to free memory
        // (best-effort only - see the class-level note above for what this can't override).
        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val locationRequest = LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            30000L // every 30 seconds - a reasonable balance between "live" and battery drain
        ).build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KGI Diesels - Location Sharing Active")
            .setContentText("Your live location is being shared so shippers can find/track you.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Location Sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when your location is being shared with shippers"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "kgi_location_tracking_channel"
        private const val NOTIFICATION_ID = 4471
        const val EXTRA_DRIVER_ID = "extra_driver_id"

        fun start(context: android.content.Context, driverId: Int) {
            val intent = Intent(context, LocationTrackingService::class.java)
                .putExtra(EXTRA_DRIVER_ID, driverId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}
