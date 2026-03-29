package com.androidrun.autostartblocker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.androidrun.autostartblocker.data.db.AppDatabase
import com.androidrun.autostartblocker.data.db.BlockedAppDao
import com.androidrun.autostartblocker.data.db.BlockedAppEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockedAppDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: BlockedAppDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.blockedAppDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndQuery() = runTest {
        val entity = BlockedAppEntity("com.example.app", "Example App")
        dao.insert(entity)

        val all = dao.getAllBlockedApps().first()
        assertEquals(1, all.size)
        assertEquals("com.example.app", all[0].packageName)
    }

    @Test
    fun deleteByPackageName() = runTest {
        dao.insert(BlockedAppEntity("com.example.app", "Example App"))
        dao.deleteByPackageName("com.example.app")

        val all = dao.getAllBlockedApps().first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun getBlockedPackageNames() = runTest {
        dao.insert(BlockedAppEntity("com.app1", "App 1"))
        dao.insert(BlockedAppEntity("com.app2", "App 2"))

        val names = dao.getBlockedPackageNames()
        assertEquals(2, names.size)
        assertTrue(names.contains("com.app1"))
        assertTrue(names.contains("com.app2"))
    }

    @Test
    fun isBlockedReturnsTrueForBlockedApp() = runTest {
        dao.insert(BlockedAppEntity("com.example.app", "Example"))
        assertTrue(dao.isBlocked("com.example.app"))
    }

    @Test
    fun isBlockedReturnsFalseForNonBlockedApp() = runTest {
        assertFalse(dao.isBlocked("com.nonexistent.app"))
    }

    @Test
    fun deleteAllClearsDatabase() = runTest {
        dao.insert(BlockedAppEntity("com.app1", "App 1"))
        dao.insert(BlockedAppEntity("com.app2", "App 2"))
        dao.deleteAll()

        val count = dao.getCount()
        assertEquals(0, count)
    }

    @Test
    fun getCountReturnsCorrectNumber() = runTest {
        dao.insert(BlockedAppEntity("com.app1", "App 1"))
        dao.insert(BlockedAppEntity("com.app2", "App 2"))
        dao.insert(BlockedAppEntity("com.app3", "App 3"))

        assertEquals(3, dao.getCount())
    }

    @Test
    fun insertReplaceOnConflict() = runTest {
        dao.insert(BlockedAppEntity("com.app1", "App Original"))
        dao.insert(BlockedAppEntity("com.app1", "App Updated"))

        val all = dao.getAllBlockedApps().first()
        assertEquals(1, all.size)
        assertEquals("App Updated", all[0].appName)
    }
}
