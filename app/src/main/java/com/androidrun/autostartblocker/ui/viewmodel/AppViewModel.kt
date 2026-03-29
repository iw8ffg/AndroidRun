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

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository.getInstance(application.applicationContext)

    private val _allApps = mutableListOf<AppInfo>()

    private val _appList = MutableLiveData<List<AppInfo>>()
    val appList: LiveData<List<AppInfo>> get() = _appList

    private val _blockedApps = MutableLiveData<Set<String>>()
    val blockedApps: LiveData<Set<String>> get() = _blockedApps

    private var currentQuery: String = ""

    fun loadInstalledApps(context: Context, includeSystemApps: Boolean = false) {
        viewModelScope.launch {
            val blockedPackageNames = repository.getBlockedPackageNames().toSet()
            _blockedApps.value = blockedPackageNames

            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

                resolveInfos.mapNotNull { resolveInfo ->
                    val appInfo = resolveInfo.activityInfo.applicationInfo
                    val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0

                    if (!includeSystemApps && isSystem) {
                        return@mapNotNull null
                    }

                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = appInfo.loadLabel(pm).toString(),
                        icon = appInfo.loadIcon(pm),
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
            val isCurrentlyBlocked = repository.isBlocked(packageName)
            if (isCurrentlyBlocked) {
                repository.removeBlockedApp(packageName)
            } else {
                val appInfo = _allApps.find { it.packageName == packageName }
                repository.addBlockedApp(packageName, appInfo?.appName ?: packageName)
            }

            val updatedBlocked = repository.getBlockedPackageNames().toSet()
            _blockedApps.value = updatedBlocked

            _allApps.forEachIndexed { index, app ->
                if (app.packageName == packageName) {
                    _allApps[index] = app.copy(isBlocked = updatedBlocked.contains(packageName))
                }
            }
            applyFilter()
        }
    }

    fun selectAll() {
        viewModelScope.launch {
            for (app in _allApps) {
                if (!repository.isBlocked(app.packageName)) {
                    repository.addBlockedApp(app.packageName, app.appName)
                }
            }
            val updatedBlocked = repository.getBlockedPackageNames().toSet()
            _blockedApps.value = updatedBlocked

            _allApps.forEachIndexed { index, app ->
                _allApps[index] = app.copy(isBlocked = true)
            }
            applyFilter()
        }
    }

    fun deselectAll() {
        viewModelScope.launch {
            repository.clearAll()
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
