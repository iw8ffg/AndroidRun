package com.androidrun.autostartblocker.data.repository

import android.content.Context
import com.androidrun.autostartblocker.data.db.AppDatabase
import com.androidrun.autostartblocker.data.db.BlockedAppDao
import com.androidrun.autostartblocker.data.db.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

class AppRepository(private val dao: BlockedAppDao) {

    val allBlockedApps: Flow<List<BlockedAppEntity>> = dao.getAllBlockedApps()

    suspend fun addBlockedApp(packageName: String, appName: String) {
        dao.insert(BlockedAppEntity(packageName = packageName, appName = appName))
    }

    suspend fun removeBlockedApp(packageName: String) {
        dao.deleteByPackageName(packageName)
    }

    suspend fun isBlocked(packageName: String): Boolean {
        return dao.isBlocked(packageName)
    }

    suspend fun getBlockedPackageNames(): List<String> {
        return dao.getBlockedPackageNames()
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    suspend fun getCount(): Int {
        return dao.getCount()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppRepository? = null

        fun getInstance(context: Context): AppRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                INSTANCE ?: AppRepository(db.blockedAppDao()).also { INSTANCE = it }
            }
        }
    }
}
