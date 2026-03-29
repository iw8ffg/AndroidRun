package com.androidrun.autostartblocker.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.androidrun.autostartblocker.data.repository.AppRepository
import com.androidrun.autostartblocker.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One-shot event wrapper to prevent re-delivery on config change.
 */
class Event<out T>(private val content: T) {
    private var hasBeenHandled = false
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) null
        else {
            hasBeenHandled = true
            content
        }
    }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository.getInstance(application.applicationContext)

    private val _allApps = mutableListOf<AppInfo>()

    private val _appList = MutableLiveData<List<AppInfo>>()
    val appList: LiveData<List<AppInfo>> get() = _appList

    private val _blockedApps = MutableLiveData<Set<String>>(emptySet())
    val blockedApps: LiveData<Set<String>> get() = _blockedApps

    private val _toggleEvent = MutableLiveData<Event<Pair<String, Boolean>>>()
    val toggleEvent: LiveData<Event<Pair<String, Boolean>>> get() = _toggleEvent

    private var currentQuery: String = ""

    fun loadInstalledApps(context: Context, includeSystemApps: Boolean = false) {
        viewModelScope.launch {
            val blockedPackageNames = withContext(Dispatchers.IO) {
                repository.getBlockedPackageNames().toSet()
            }
            _blockedApps.value = blockedPackageNames

            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                @Suppress("DEPRECATION")
                val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

                resolveInfos.mapNotNull { resolveInfo ->
                    val appInfo = resolveInfo.activityInfo.applicationInfo
                    val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0

                    if (!includeSystemApps && isSystem) return@mapNotNull null

                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = appInfo.loadLabel(pm).toString(),
                        icon = try { appInfo.loadIcon(pm) } catch (_: Exception) { null },
                        isBlocked = blockedPackageNames.contains(appInfo.packageName)
                    )
                }.sortedBy { it.appName.lowercase() }
            }

            _allApps.clear()
            _allApps.addAll(apps)
            applyFilter()
        }
    }

    fun toggleBlockedApp(packageName: String) {
        viewModelScope.launch {
            val wasBlocked = withContext(Dispatchers.IO) { repository.isBlocked(packageName) }

            withContext(Dispatchers.IO) {
                if (wasBlocked) {
                    repository.removeBlockedApp(packageName)
                } else {
                    val appInfo = _allApps.find { it.packageName == packageName }
                    repository.addBlockedApp(packageName, appInfo?.appName ?: packageName)
                }
            }

            val isNowBlocked = !wasBlocked
            val updatedBlocked = withContext(Dispatchers.IO) {
                repository.getBlockedPackageNames().toSet()
            }
            _blockedApps.value = updatedBlocked

            // Update local list
            _allApps.forEachIndexed { index, app ->
                if (app.packageName == packageName) {
                    _allApps[index] = app.copy(isBlocked = isNowBlocked)
                }
            }
            applyFilter()

            // Post event AFTER db is updated (fixes race condition)
            _toggleEvent.value = Event(packageName to isNowBlocked)
        }
    }

    fun selectAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (app in _allApps) {
                    if (!repository.isBlocked(app.packageName)) {
                        repository.addBlockedApp(app.packageName, app.appName)
                    }
                }
            }
            val updatedBlocked = withContext(Dispatchers.IO) {
                repository.getBlockedPackageNames().toSet()
            }
            _blockedApps.value = updatedBlocked
            _allApps.forEachIndexed { index, app ->
                _allApps[index] = app.copy(isBlocked = true)
            }
            applyFilter()
        }
    }

    fun deselectAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.clearAll() }
            _blockedApps.value = emptySet()
            _allApps.forEachIndexed { index, app ->
                _allApps[index] = app.copy(isBlocked = false)
            }
            applyFilter()
        }
    }

    fun filter(query: String) {
        currentQuery = query
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (currentQuery.isBlank()) {
            _allApps.toList()
        } else {
            _allApps.filter { app ->
                app.appName.contains(currentQuery, ignoreCase = true) ||
                    app.packageName.contains(currentQuery, ignoreCase = true)
            }
        }
        _appList.value = filtered
    }
}
