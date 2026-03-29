package com.androidrun.autostartblocker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: AppViewModel
    private lateinit var appListAdapter: AppListAdapter

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Snackbar.make(
                binding.root,
                getString(R.string.notification_permission_denied),
                Snackbar.LENGTH_LONG
            ).show()
        }
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
        observeViewModel()
        requestNotificationPermissionIfNeeded()

        viewModel.loadInstalledApps(this, includeSystemApps = false)
    }

    private fun setupRecyclerView() {
        appListAdapter = AppListAdapter { packageName, _ ->
            viewModel.toggleBlockedApp(packageName)
            val blocked = viewModel.blockedApps.value?.contains(packageName) == true
            val message = if (!blocked) {
                getString(R.string.app_blocked, packageName)
            } else {
                getString(R.string.app_unblocked, packageName)
            }
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
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

    private fun observeViewModel() {
        viewModel.appList.observe(this) { apps ->
            appListAdapter.submitList(apps)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_select_all -> {
                viewModel.selectAll()
                Snackbar.make(
                    binding.root,
                    getString(R.string.all_apps_blocked),
                    Snackbar.LENGTH_SHORT
                ).show()
                true
            }
            R.id.action_deselect_all -> {
                viewModel.deselectAll()
                Snackbar.make(
                    binding.root,
                    getString(R.string.all_apps_unblocked),
                    Snackbar.LENGTH_SHORT
                ).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
