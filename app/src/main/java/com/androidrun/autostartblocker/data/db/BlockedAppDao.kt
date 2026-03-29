package com.androidrun.autostartblocker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: BlockedAppEntity)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    fun getAllBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT packageName FROM blocked_apps")
    suspend fun getBlockedPackageNames(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_apps WHERE packageName = :packageName)")
    suspend fun isBlocked(packageName: String): Boolean

    @Query("DELETE FROM blocked_apps")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM blocked_apps")
    suspend fun getCount(): Int
}
