package com.androidrun.autostartblocker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val dateAdded: Long = System.currentTimeMillis()
)
