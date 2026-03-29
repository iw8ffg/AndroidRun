package com.androidrun.autostartblocker

import com.androidrun.autostartblocker.data.db.BlockedAppDao
import com.androidrun.autostartblocker.data.db.BlockedAppEntity
import com.androidrun.autostartblocker.data.repository.AppRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AppRepositoryTest {

    private lateinit var dao: BlockedAppDao
    private lateinit var repository: AppRepository

    @Before
    fun setup() {
        dao = mock()
        repository = AppRepository(dao)
    }

    @Test
    fun `addBlockedApp calls dao insert`() = runTest {
        repository.addBlockedApp("com.example.app", "Example App")
        verify(dao).insert(any())
    }

    @Test
    fun `removeBlockedApp calls dao deleteByPackageName`() = runTest {
        repository.removeBlockedApp("com.example.app")
        verify(dao).deleteByPackageName("com.example.app")
    }

    @Test
    fun `getBlockedPackageNames returns correct list`() = runTest {
        val expected = listOf("com.app1", "com.app2")
        whenever(dao.getBlockedPackageNames()).thenReturn(expected)

        val result = repository.getBlockedPackageNames()
        assertEquals(expected, result)
    }

    @Test
    fun `isBlocked returns true for blocked app`() = runTest {
        whenever(dao.isBlocked("com.example.app")).thenReturn(true)
        assertTrue(repository.isBlocked("com.example.app"))
    }

    @Test
    fun `isBlocked returns false for non-blocked app`() = runTest {
        whenever(dao.isBlocked("com.example.app")).thenReturn(false)
        assertFalse(repository.isBlocked("com.example.app"))
    }

    @Test
    fun `clearAll calls dao deleteAll`() = runTest {
        repository.clearAll()
        verify(dao).deleteAll()
    }

    @Test
    fun `getCount returns correct count`() = runTest {
        whenever(dao.getCount()).thenReturn(5)
        assertEquals(5, repository.getCount())
    }

    @Test
    fun `allBlockedApps returns flow from dao`() {
        val entities = listOf(
            BlockedAppEntity("com.app1", "App 1"),
            BlockedAppEntity("com.app2", "App 2")
        )
        whenever(dao.getAllBlockedApps()).thenReturn(flowOf(entities))

        val flow = repository.allBlockedApps
        // Flow is lazy - just verify it was obtained from dao
        verify(dao).getAllBlockedApps()
    }
}
