package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val userDao: UserDao,
    private val loadDao: LoadDao,
    private val chatDao: ChatDao,
    private val commissionDao: CommissionDao,
    private val jobDao: JobDao
) {
    fun getUser(id: Int): Flow<User?> = userDao.getUserById(id)
    suspend fun getUserSync(id: Int): User? = userDao.getUserByIdSync(id)
    suspend fun getUserByPhoneSync(phone: String): User? = userDao.getUserByPhoneSync(phone)
    suspend fun insertUser(user: User): Long {
        val newId = userDao.insertUser(user)
        UserDirectorySync.pushUser(user.copy(id = newId.toInt()))
        return newId
    }
    suspend fun updateUser(user: User) {
        userDao.updateUser(user)
        // Keep the cross-device directory in sync automatically whenever a profile changes
        // (e.g. document approval status) - no need to remember to do this at every call site.
        UserDirectorySync.pushUser(user)
    }
    fun getDriversByIds(ids: List<Int>): Flow<List<User>> = userDao.getDriversByIds(ids)
    fun getAllDrivers(): Flow<List<User>> = userDao.getAllDrivers()

    fun getAllLoads(): Flow<List<Load>> = loadDao.getAllLoads()
    fun getLoadsByShipper(shipperId: Int): Flow<List<Load>> = loadDao.getLoadsByShipper(shipperId)
    fun getLoadsByDriver(driverId: Int): Flow<List<Load>> = loadDao.getLoadsByDriver(driverId)
    fun getLoadById(id: Int): Flow<Load?> = loadDao.getLoadById(id)
    suspend fun getLoadByIdSync(id: Int): Load? = loadDao.getLoadByIdSync(id)
    suspend fun getLoadByCloudIdSync(cloudId: String): Load? = loadDao.getLoadByCloudIdSync(cloudId)

    suspend fun insertLoad(load: Load): Long {
        val newId = loadDao.insertLoad(load)
        LoadsDirectorySync.pushLoad(load.copy(id = newId.toInt()))
        return newId
    }

    suspend fun updateLoad(load: Load) {
        loadDao.updateLoad(load)
        LoadsDirectorySync.pushLoad(load)
    }

    /**
     * Merges a load received from another device into this device's local database.
     * Matches by cloudId (never by local id, which can differ per phone) - updates the
     * existing local row if found, otherwise inserts a new one.
     */
    suspend fun upsertLoadFromCloud(remoteLoad: Load) {
        val existingLocal = loadDao.getLoadByCloudIdSync(remoteLoad.cloudId)
        if (existingLocal != null) {
            loadDao.updateLoad(remoteLoad.copy(id = existingLocal.id))
        } else {
            loadDao.insertLoad(remoteLoad.copy(id = 0))
        }

        // Any driver referenced on this load (applied, or assigned) needs to actually exist
        // in THIS device's local database, or they'll be invisible in the shipper's applicant
        // list / trip screens even though the load itself synced correctly. Fetch and cache
        // any that are missing.
        val referencedDriverIds = mutableSetOf<Int>()
        remoteLoad.interestedDriverIdsString.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .forEach { referencedDriverIds.add(it) }
        remoteLoad.assignedDriverId?.let { referencedDriverIds.add(it) }

        for (id in referencedDriverIds) {
            if (userDao.getUserByIdSync(id) == null) {
                val cloudUser = UserDirectorySync.fetchUserById(id)
                if (cloudUser != null) {
                    userDao.insertUser(cloudUser) // direct DAO call - no need to re-push what we just pulled
                }
            }
        }
    }

    suspend fun getCompletedLoadsCountBetween(driverId: Int, shipperId: Int): Int =
        loadDao.getCompletedLoadsCountBetween(driverId, shipperId)

    suspend fun getCompletedLoadsCountForShipper(shipperId: Int): Int =
        loadDao.getCompletedLoadsCountForShipper(shipperId)

    suspend fun getCompletedLoadsCountForDriver(driverId: Int): Int =
        loadDao.getCompletedLoadsCountForDriver(driverId)

    fun getChatMessagesForLoad(loadId: Int): Flow<List<ChatMessage>> = chatDao.getChatMessagesForLoad(loadId)
    suspend fun insertChatMessage(message: ChatMessage) = chatDao.insertChatMessage(message)

    fun getCommissionForLoad(loadId: Int): Flow<CommissionPayment?> = commissionDao.getCommissionForLoad(loadId)
    suspend fun getCommissionForLoadSync(loadId: Int): CommissionPayment? = commissionDao.getCommissionForLoadSync(loadId)

    suspend fun insertCommission(commission: CommissionPayment): Long {
        val withCloudId = if (commission.cloudId.isBlank()) commission.copy(cloudId = java.util.UUID.randomUUID().toString()) else commission
        val newId = commissionDao.insertCommission(withCloudId)
        CommissionDirectorySync.pushCommission(withCloudId.copy(id = newId.toInt()))
        return newId
    }

    suspend fun updateCommission(commission: CommissionPayment) {
        commissionDao.updateCommission(commission)
        CommissionDirectorySync.pushCommission(commission)
    }

    /** Merges a commission payment received from another device - matches by cloudId, never local id. */
    suspend fun upsertCommissionFromCloud(remote: CommissionPayment) {
        if (remote.cloudId.isBlank()) return
        val existing = commissionDao.getCommissionByCloudIdSync(remote.cloudId)
        if (existing != null) {
            commissionDao.updateCommission(remote.copy(id = existing.id))
        } else {
            commissionDao.insertCommission(remote.copy(id = 0))
        }
    }

    suspend fun getUnpaidCommissionsForShipper(shipperId: Int): List<CommissionPayment> =
        commissionDao.getUnpaidCommissionsForShipper(shipperId)

    suspend fun getUnpaidCommissionsForDriver(driverId: Int): List<CommissionPayment> =
        commissionDao.getUnpaidCommissionsForDriver(driverId)

    fun getAllCommissions(): Flow<List<CommissionPayment>> = commissionDao.getAllCommissions()
    suspend fun getCommissionByIdSync(id: Int): CommissionPayment? = commissionDao.getCommissionByIdSync(id)

    // Job Profile functions
    fun getAllJobs(): Flow<List<JobProfile>> = jobDao.getAllJobs()
    fun getJobsByShipper(shipperId: Int): Flow<List<JobProfile>> = jobDao.getJobsByShipper(shipperId)
    suspend fun insertJob(job: JobProfile): Long {
        val withCloudId = if (job.cloudId.isBlank()) job.copy(cloudId = java.util.UUID.randomUUID().toString()) else job
        val newId = jobDao.insertJob(withCloudId)
        JobsDirectorySync.pushJob(withCloudId.copy(id = newId.toInt()))
        return newId
    }

    suspend fun updateJob(job: JobProfile) {
        jobDao.updateJob(job)
        JobsDirectorySync.pushJob(job)
    }

    suspend fun upsertJobFromCloud(remote: JobProfile) {
        if (remote.cloudId.isBlank()) return
        val existing = jobDao.getJobByCloudIdSync(remote.cloudId)
        if (existing != null) {
            jobDao.updateJob(remote.copy(id = existing.id))
        } else {
            jobDao.insertJob(remote.copy(id = 0))
        }
    }
    suspend fun getJobByIdSync(id: Int): JobProfile? = jobDao.getJobByIdSync(id)
}
