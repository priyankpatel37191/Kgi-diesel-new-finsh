package com.example.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Cross-device commission payment sync via Firestore.
 *
 * This is what lets you (the admin) open the Admin panel on ANY phone that has this
 * app installed - not just the one a specific driver/shipper happened to submit their
 * payment proof from - and see every pending request live, from all your users.
 *
 * Matching key: cloudId (a UUID), same pattern as LoadsDirectorySync - the local Room
 * `id` can differ per device, cloudId is what stays the same everywhere.
 */
object CommissionDirectorySync {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private const val COLLECTION = "commissions_directory"

    suspend fun pushCommission(commission: CommissionPayment) {
        if (commission.cloudId.isBlank()) return
        try {
            val data = mapOf(
                "cloudId" to commission.cloudId,
                "driverId" to commission.driverId,
                "shipperId" to commission.shipperId,
                "loadId" to commission.loadId,
                "amount" to commission.amount,
                "isPaid" to commission.isPaid,
                "upiIdUsed" to commission.upiIdUsed,
                "utrNumber" to commission.utrNumber,
                "payeePhone" to commission.payeePhone,
                "verificationStatus" to commission.verificationStatus,
                "screenshotPath" to commission.screenshotPath,
                "timestamp" to commission.timestamp
            )
            db.collection(COLLECTION).document(commission.cloudId).set(data).await()
        } catch (e: Exception) {
            // Best-effort - never block local app usage on a failed cloud push.
        }
    }

    private fun mapToCommission(cloudId: String, d: Map<String, Any?>): CommissionPayment? {
        return try {
            CommissionPayment(
                id = 0,
                cloudId = cloudId,
                driverId = (d["driverId"] as? Long)?.toInt() ?: 0,
                shipperId = (d["shipperId"] as? Long)?.toInt() ?: 0,
                loadId = (d["loadId"] as? Long)?.toInt() ?: return null,
                amount = (d["amount"] as? Number)?.toDouble() ?: 0.0,
                isPaid = d["isPaid"] as? Boolean ?: false,
                upiIdUsed = d["upiIdUsed"] as? String ?: "",
                utrNumber = d["utrNumber"] as? String ?: "",
                payeePhone = d["payeePhone"] as? String ?: "",
                verificationStatus = d["verificationStatus"] as? String ?: "UNPAID",
                screenshotPath = d["screenshotPath"] as? String ?: "",
                timestamp = (d["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Starts a live listener for every commission change from every device. Call once per app session. */
    fun listenToAllCommissions(onChange: (Load: CommissionPayment) -> Unit): ListenerRegistration {
        return db.collection(COLLECTION).addSnapshotListener { snapshot, _ ->
            if (snapshot == null) return@addSnapshotListener
            for (change in snapshot.documentChanges) {
                val commission = mapToCommission(change.document.id, change.document.data)
                if (commission != null) onChange(commission)
            }
        }
    }
}
