package com.example.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Real cross-device GPS sync for an active trip, using Firebase Firestore.
 *
 * Why this is needed: without a backend, each phone only has its own local copy of the
 * app's data (Room database) - a driver's phone has no way to tell a shipper's phone
 * anything in real time. Firestore is the shared, real-time layer that makes that possible.
 *
 * How it's used:
 *  - The driver's phone calls pushDriverLocation(...) every few seconds while a trip is ONGOING.
 *  - The shipper's phone calls listenToDriverLocation(...) to get live updates the moment
 *    the driver's phone writes a new position - no polling needed, Firestore pushes it instantly.
 *
 * IMPORTANT (one-time setup you still need to do in the Firebase console):
 *  Firestore's default "test mode" security rules only allow open read/write for 30 days
 *  after the database was created. After that, reads/writes will silently start failing
 *  with a permission error. Before that window closes, go to
 *  Firebase console -> Firestore Database -> Rules, and set rules that allow signed-in
 *  users (or, at minimum, any request) to read/write the "live_tracking" collection -
 *  otherwise live tracking will stop working with no visible crash, just no updates.
 */
object LiveTrackingSync {

    private val db by lazy { FirebaseFirestore.getInstance() }

    data class LiveLocation(
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val updatedAtMillis: Long = 0L
    )

    /** Called from the DRIVER's phone while a trip is ONGOING. Best-effort: never throws. */
    suspend fun pushDriverLocation(loadId: Int, lat: Double, lng: Double) {
        try {
            db.collection("live_tracking")
                .document(loadId.toString())
                .set(
                    mapOf(
                        "lat" to lat,
                        "lng" to lng,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
        } catch (e: Exception) {
            // Tracking sync is best-effort - a failed location push should never crash
            // or block the trip flow (e.g. no internet for a moment, or rules not yet set).
        }
    }

    /**
     * Called from the SHIPPER's phone. Returns a ListenerRegistration - the caller MUST
     * call .remove() on it (e.g. in a Compose DisposableEffect) when the screen closes,
     * otherwise it keeps listening (and using data) in the background.
     */
    fun listenToDriverLocation(loadId: Int, onUpdate: (LiveLocation) -> Unit): ListenerRegistration {
        return db.collection("live_tracking")
            .document(loadId.toString())
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val lat = snapshot.getDouble("lat")
                    val lng = snapshot.getDouble("lng")
                    val updatedAt = snapshot.getLong("updatedAt") ?: 0L
                    if (lat != null && lng != null) {
                        onUpdate(LiveLocation(lat, lng, updatedAt))
                    }
                }
            }
    }

    /** Call when a trip completes, so the tracking document doesn't linger forever. */
    suspend fun clearTracking(loadId: Int) {
        try {
            db.collection("live_tracking").document(loadId.toString()).delete().await()
        } catch (e: Exception) {
            // Best-effort cleanup only.
        }
    }
}
