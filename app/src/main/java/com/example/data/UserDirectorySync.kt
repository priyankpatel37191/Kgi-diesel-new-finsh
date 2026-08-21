package com.example.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Cross-device account directory, backed by Firestore.
 *
 * THE PROBLEM THIS SOLVES: this app's database (Room) lives entirely on each individual
 * phone. Signing up on Phone A never told Phone B that account exists - so logging in
 * with the same number on a different phone correctly (but unhelpfully) said "user not
 * found." This file is the shared, cloud-side "phone book" all devices check against,
 * so an account created anywhere is findable and loginable everywhere.
 *
 * How it's used (see MainViewModel.login / signupDriver / signupShipper):
 *  - On signup: after creating the account locally, also push a copy of it here.
 *  - On login: if the phone number isn't found in this device's local database yet,
 *    check here - if found, pull it down into the local database, then log in normally.
 */
object UserDirectorySync {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private const val COLLECTION = "users_directory"

    /** Saves/updates this user's full profile in the shared cloud directory, keyed by phone number. */
    suspend fun pushUser(user: User) {
        try {
            val data = mapOf(
                "role" to user.role,
                "name" to user.name,
                "phone" to user.phone,
                "truckSize" to user.truckSize,
                "truckNumber" to user.truckNumber,
                "rcPath" to user.rcPath,
                "dlPath" to user.dlPath,
                "aadhaarPath" to user.aadhaarPath,
                "permitPath" to user.permitPath,
                "isApproved" to user.isApproved,
                "dlStatus" to user.dlStatus,
                "rcStatus" to user.rcStatus,
                "aadhaarStatus" to user.aadhaarStatus,
                "permitStatus" to user.permitStatus,
                "rejectionReason" to user.rejectionReason
            )
            db.collection(COLLECTION).document(user.phone).set(data).await()
        } catch (e: Exception) {
            // Best-effort: a failed cloud sync should never block using the app on this device.
        }
    }

    /** Looks up a phone number in the shared cloud directory. Returns null if not found or offline. */
    suspend fun fetchUserByPhone(phone: String): User? {
        return try {
            val doc = db.collection(COLLECTION).document(phone).get().await()
            if (!doc.exists()) return null

            User(
                id = phone.takeLast(9).toIntOrNull() ?: kotlin.math.abs(phone.hashCode()), // must match stableIdFromPhone()
                role = doc.getString("role") ?: return null,
                name = doc.getString("name") ?: "",
                phone = doc.getString("phone") ?: phone,
                truckSize = doc.getString("truckSize") ?: "",
                truckNumber = doc.getString("truckNumber") ?: "",
                rcPath = doc.getString("rcPath") ?: "",
                dlPath = doc.getString("dlPath") ?: "",
                aadhaarPath = doc.getString("aadhaarPath") ?: "",
                permitPath = doc.getString("permitPath") ?: "",
                isApproved = doc.getBoolean("isApproved") ?: false,
                dlStatus = doc.getString("dlStatus") ?: "PENDING",
                rcStatus = doc.getString("rcStatus") ?: "PENDING",
                aadhaarStatus = doc.getString("aadhaarStatus") ?: "PENDING",
                permitStatus = doc.getString("permitStatus") ?: "PENDING",
                rejectionReason = doc.getString("rejectionReason") ?: ""
            )
        } catch (e: Exception) {
            null
        }
    }
}
