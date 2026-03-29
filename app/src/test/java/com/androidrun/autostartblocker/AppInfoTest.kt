package com.androidrun.autostartblocker

import com.androidrun.autostartblocker.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInfoTest {

    @Test
    fun `default values are correct`() {
        val appInfo = AppInfo(
            packageName = "com.example.app",
            appName = "Example"
        )
        assertNull(appInfo.icon)
        assertFalse(appInfo.isBlocked)
    }

    @Test
    fun `copy with isBlocked changed`() {
        val original = AppInfo("com.example.app", "Example", isBlocked = false)
        val blocked = original.copy(isBlocked = true)
        assertTrue(blocked.isBlocked)
        assertEquals(original.packageName, blocked.packageName)
    }

    @Test
    fun `equality based on all fields`() {
        val app1 = AppInfo("com.example.app", "Example", isBlocked = true)
        val app2 = AppInfo("com.example.app", "Example", isBlocked = true)
        assertEquals(app1, app2)
    }
}
