package com.example.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Cross-device Loads sync via Firestore.
 *
 * This is what lets a shipper's posted load actually appear on a different phone
 * (a driver's device), and lets status changes (accepted/ongoing/completed) travel
 * back the other way - which local-only Room storage could never do on its own.
 *
 * Matching key: each Load has a `cloudId` (a UUID) that stays the same everywhere,
 * even though the local Room `id` number can differ per phone. Always match/update
 * by cloudId, never by the local id, when merging data from the cloud.
 */
object LoadsDirectorySync {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private const val COLLECTION = "loads_directory"

    suspend fun pushLoad(load: Load) {
        if (load.cloudId.isBlank()) return // safety: don't push a load with no stable key yet
        try {
            val data = mapOf(
                "cloudId" to load.cloudId,
                "shipperId" to load.shipperId,
                "shipperName" to load.shipperName,
                "shipperPhone" to load.shipperPhone,
                "pickupLocation" to load.pickupLocation,
                "dropLocation" to load.dropLocation,
                "loadType" to load.loadType,
                "weightTons" to load.weightTons,
                "truckSize" to load.truckSize,
                "distanceKm" to load.distanceKm,
                "ratePerKm" to load.ratePerKm,
                "ratePerTon" to load.ratePerTon,
                "totalFare" to load.totalFare,
                "status" to load.status,
                "interestedDriverIdsString" to load.interestedDriverIdsString,
                "assignedDriverId" to load.assignedDriverId,
                "assignedDriverName" to load.assignedDriverName,
                "assignedDriverPhone" to load.assignedDriverPhone,
                "isCommissionPaid" to load.isCommissionPaid,
                "createdAt" to load.createdAt
            )
            db.collection(COLLECTION).document(load.cloudId).set(data).await()
        } catch (e: Exception) {
            // Best-effort: a failed cloud push should never crash or block using the app locally.
        }
    }

    private fun mapToLoad(cloudId: String, d: Map<String, Any?>): Load? {
        return try {
            Load(
                id = 0, // caller decides the correct local id (existing row's id, or let Room assign a new one)
                cloudId = cloudId,
                shipperId = (d["shipperId"] as? Long)?.toInt() ?: return null,
                shipperName = d["shipperName"] as? String ?: "",
                shipperPhone = d["shipperPhone"] as? String ?: "",
                pickupLocation = d["pickupLocation"] as? String ?: "",
                dropLocation = d["dropLocation"] as? String ?: "",
                loadType = d["loadType"] as? String ?: "",
                weightTons = (d["weightTons"] as? Number)?.toDouble() ?: 0.0,
                truckSize = d["truckSize"] as? String ?: "",
                distanceKm = (d["distanceKm"] as? Number)?.toDouble() ?: 0.0,
                ratePerKm = (d["ratePerKm"] as? Number)?.toDouble() ?: 0.0,
                ratePerTon = (d["ratePerTon"] as? Number)?.toDouble() ?: 0.0,
                totalFare = (d["totalFare"] as? Number)?.toDouble() ?: 0.0,
                status = d["status"] as? String ?: "POSTED",
                interestedDriverIdsString = d["interestedDriverIdsString"] as? String ?: "",
                assignedDriverId = (d["assignedDriverId"] as? Long)?.toInt(),
                assignedDriverName = d["assignedDriverName"] as? String ?: "",
                assignedDriverPhone = d["assignedDriverPhone"] as? String ?: "",
                isCommissionPaid = d["isCommissionPaid"] as? Boolean ?: false,
                createdAt = (d["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Starts listening for ALL load changes from every device, live. Call once per app
     * session (see MainViewModel.init). The caller MUST hold onto the returned
     * ListenerRegistration and .remove() it if it ever needs to stop (not required for
     * the app's lifetime-long listener, but good practice).
     */
    fun listenToAllLoads(onChange: (String, Load) -> Unit): ListenerRegistration {
        return db.collection(COLLECTION).addSnapshotListener { snapshot, _ ->
            if (snapshot == null) return@addSnapshotListener
            for (change in snapshot.documentChanges) {
                val cloudId = change.document.id
                val load = mapToLoad(cloudId, change.document.data)
                if (load != null) {
                    onChange(cloudId, load)
                }
            }
        }
    }
}
