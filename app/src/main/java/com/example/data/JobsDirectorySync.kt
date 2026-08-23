package com.example.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Cross-device job posting sync via Firestore - same pattern as LoadsDirectorySync.
 * Makes a job a shipper posts on their phone actually show up for drivers on other phones.
 */
object JobsDirectorySync {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private const val COLLECTION = "jobs_directory"

    suspend fun pushJob(job: JobProfile) {
        if (job.cloudId.isBlank()) return
        try {
            val data = mapOf(
                "cloudId" to job.cloudId,
                "shipperId" to job.shipperId,
                "shipperName" to job.shipperName,
                "shipperPhone" to job.shipperPhone,
                "workTitle" to job.workTitle,
                "salaryText" to job.salaryText,
                "location" to job.location,
                "description" to job.description,
                "applicantsString" to job.applicantsString,
                "createdAt" to job.createdAt
            )
            db.collection(COLLECTION).document(job.cloudId).set(data).await()
        } catch (e: Exception) {
            // Best-effort only.
        }
    }

    private fun mapToJob(cloudId: String, d: Map<String, Any?>): JobProfile? {
        return try {
            JobProfile(
                id = 0,
                cloudId = cloudId,
                shipperId = (d["shipperId"] as? Long)?.toInt() ?: return null,
                shipperName = d["shipperName"] as? String ?: "",
                shipperPhone = d["shipperPhone"] as? String ?: "",
                workTitle = d["workTitle"] as? String ?: "",
                salaryText = d["salaryText"] as? String ?: "",
                location = d["location"] as? String ?: "",
                description = d["description"] as? String ?: "",
                applicantsString = d["applicantsString"] as? String ?: "",
                createdAt = (d["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    fun listenToAllJobs(onChange: (JobProfile) -> Unit): ListenerRegistration {
        return db.collection(COLLECTION).addSnapshotListener { snapshot, _ ->
            if (snapshot == null) return@addSnapshotListener
            for (change in snapshot.documentChanges) {
                val job = mapToJob(change.document.id, change.document.data)
                if (job != null) onChange(job)
            }
        }
    }
}
