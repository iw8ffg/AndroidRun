package com.androidrun.autostartblocker

import com.androidrun.autostartblocker.data.db.BlockedAppEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedAppEntityTest {

    @Test
    fun `entity creation with all fields`() {
        val entity = BlockedAppEntity(
            packageName = "com.example.app",
            appName = "Example App",
            dateAdded = 1000L
        )
        assertEquals("com.example.app", entity.packageName)
        assertEquals("Example App", entity.appName)
        assertEquals(1000L, entity.dateAdded)
    }

    @Test
    fun `default dateAdded uses current time`() {
        val before = System.currentTimeMillis()
        val entity = BlockedAppEntity(
            packageName = "com.example.app",
            appName = "Example App"
        )
        val after = System.currentTimeMillis()
        assertTrue(entity.dateAdded in before..after)
    }

    @Test
    fun `entities with same packageName are equal`() {
        val entity1 = BlockedAppEntity("com.example.app", "App 1", 1000L)
        val entity2 = BlockedAppEntity("com.example.app", "App 1", 1000L)
        assertEquals(entity1, entity2)
    }

    @Test
    fun `entities with different packageName are not equal`() {
        val entity1 = BlockedAppEntity("com.example.app1", "App 1")
        val entity2 = BlockedAppEntity("com.example.app2", "App 2")
        assertNotEquals(entity1, entity2)
    }
}
