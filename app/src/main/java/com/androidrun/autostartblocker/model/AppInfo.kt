package com.androidrun.autostartblocker.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    var isBlocked: Boolean = false
)
