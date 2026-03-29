package com.androidrun.autostartblocker.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidrun.autostartblocker.R
import com.androidrun.autostartblocker.databinding.ActivityMainBinding
import com.androidrun.autostartblocker.ui.adapter.AppListAdapter
import com.androidrun.autostartblocker.ui.viewmodel.AppViewModel
import com.androidrun.autostartblocker.worker.AppKillerWorker
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: AppViewModel
    private lateinit var appListAdapter: AppListAdapter

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Snackbar.make(binding.root, R.string.notification_permission_denied, Snackbar.LENGTH_LONG).show()
        }
        updateSetupCard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        viewModel = ViewModelProvider(this)[AppViewModel::class.java]

        setupRecyclerView()
        setupSearchView()
        setupSystemAppsToggle()
        setupPermissionButtons()
        observeViewModel()
        requestNotificationPermissionIfNeeded()

        // Schedule periodic blocking
        AppKillerWorker.schedulePeriodic(this)

        viewModel.loadInstalledApps(this, includeSystemApps = false)
    }

    override fun onResume() {
        super.onResume()
        updateSetupCard()
    }

    // ---- Setup Card (permission guidance) ----

    private fun setupPermissionButtons() {
        binding.btnUsageStats.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        binding.btnBatteryOpt.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }

        binding.btnDismissSetup.setOnClickListener {
            binding.setupCard.visibility = View.GONE
        }
    }

    private fun updateSetupCard() {
        val hasUsageStats = hasUsageStatsPermission()
        val hasBatteryExemption = isBatteryOptimizationDisabled()

        binding.btnUsageStats.text = if (hasUsageStats) {
            getString(R.string.setup_usage_stats) + " ✓"
        } else {
            getString(R.string.setup_usage_stats)
        }
        binding.btnUsageStats.isEnabled = !hasUsageStats

        binding.btnBatteryOpt.text = if (hasBatteryExemption) {
            getString(R.string.setup_battery) + " ✓"
        } else {
            getString(R.string.setup_battery)
        }
        binding.btnBatteryOpt.isEnabled = !hasBatteryExemption

        if (hasUsageStats && hasBatteryExemption) {
            binding.setupCard.visibility = View.GONE
        } else {
            binding.setupCard.visibility = View.VISIBLE
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    // ---- RecyclerView & Search ----

    private fun setupRecyclerView() {
        appListAdapter = AppListAdapter { packageName, _ ->
            viewModel.toggleBlockedApp(packageName)
        }

        binding.rvApps.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appListAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.filter(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.filter(newText.orEmpty())
                return true
            }
        })
    }

    private fun setupSystemAppsToggle() {
        binding.switchSystemApps.setOnCheckedChangeListener { _, isChecked ->
            viewModel.loadInstalledApps(this, includeSystemApps = isChecked)
        }
    }

    // ---- Observe ViewModel ----

    private fun observeViewModel() {
        viewModel.appList.observe(this) { apps ->
            appListAdapter.submitList(apps)
        }

        viewModel.blockedApps.observe(this) { blocked ->
            binding.tvBlockedCount.text = getString(R.string.blocked_count, blocked.size)
            binding.tvBlockedCount.visibility = if (blocked.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.toggleEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { (pkgName, isBlocked) ->
                val msg = if (isBlocked) {
                    getString(R.string.app_blocked, pkgName)
                } else {
                    getString(R.string.app_unblocked, pkgName)
                }
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    // ---- Notifications ----

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ---- Menu ----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_select_all -> {
                viewModel.selectAll()
                Snackbar.make(binding.root, R.string.all_apps_blocked, Snackbar.LENGTH_SHORT).show()
                true
            }
            R.id.action_deselect_all -> {
                viewModel.deselectAll()
                Snackbar.make(binding.root, R.string.all_apps_unblocked, Snackbar.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
