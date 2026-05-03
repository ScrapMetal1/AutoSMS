package com.elias.autosms.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.elias.autosms.R
import com.elias.autosms.databinding.ActivityMainBinding
import com.elias.autosms.repository.SmsScheduleRepository
import com.elias.autosms.ui.adapter.SmsScheduleAdapter
import com.elias.autosms.viewmodel.MainViewModel
import com.elias.autosms.viewmodel.MainViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: SmsScheduleAdapter
    private var isReady = false

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    permissions ->
                val allGranted = permissions.all { it.value }
                if (allGranted) {
                    Toast.makeText(this, getString(R.string.permissions_granted), Toast.LENGTH_SHORT).show()
                    checkSpecialPermissions()
                } else {
                    showPermissionDeniedDialog()
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Add smooth exit animation
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView
                    .view
                    .animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setListener(
                            object : android.animation.AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: android.animation.Animator) {
                                    splashScreenView.remove()
                                }
                            }
                    )
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupViewModel()
        setupRecyclerView()
        setupFab()
        setupAutoReplyCard()
        setupSortButton() // Call the new setupSortButton
        checkPermissions()
        observeSchedules()
        restoreAlarmsIfNeeded()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources to prevent memory leaks
        cleanupResources()
    }

    private fun cleanupResources() {
        // Clear adapter to prevent memory leaks
        binding.recyclerView.adapter = null
    }

    // Setup toolbar with menu
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_auto_reply -> {
                startActivity(Intent(this, AutoReplyListActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Initialize ViewModel with factory
    private fun setupViewModel() {
        val factory = MainViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]
    }

    // Setup RecyclerView with adapter for displaying schedules
    private fun setupRecyclerView() {
        adapter =
                SmsScheduleAdapter(
                        onToggleClick = { schedule ->
                            viewModel.toggleSchedule(schedule.id, !schedule.isEnabled)
                        },
                        onEditClick = { schedule ->
                            val intent = Intent(this, AddEditScheduleActivity::class.java)
                            intent.putExtra("schedule", schedule)
                            startActivity(intent)
                        },
                        onDeleteClick = { schedule ->
                            showDeleteConfirmation(schedule.id, schedule.contactName)
                        }
                )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Set fixed size for better performance
        binding.recyclerView.setHasFixedSize(true)
    }

    private fun setupAutoReplyCard() {
        binding.cardAutoReply.setOnClickListener {
            startActivity(Intent(this, AutoReplyListActivity::class.java))
        }
    }

    // Setup FloatingActionButton to add new schedules
    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            if (hasRequiredPermissions()) {
                startActivity(Intent(this, AddEditScheduleActivity::class.java))
            } else {
                checkPermissions()
            }
        }
    }

    // Observe LiveData for schedule updates with lifecycle awareness
    private fun observeSchedules() {
        viewModel.allSchedules.observe(this) { schedules ->
            adapter.submitList(schedules)
            binding.emptyView.visibility =
                    if (schedules.isEmpty()) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }
            // signal that data is ready so splash screen can dismiss
            isReady = true
        }
    }

    private fun setupSortButton() {
        binding.btnSort.setOnClickListener {
            val dialog = BottomSheetDialog(this)
            val sheetView = layoutInflater.inflate(R.layout.dialog_sort_options, binding.root, false)
            dialog.setContentView(sheetView)

            val toggleOrder = sheetView.findViewById<MaterialButtonToggleGroup>(R.id.toggleOrder)
            val radioGroup = sheetView.findViewById<RadioGroup>(R.id.radioGroupCriteria)

            // Set initial state
            val currentDir = viewModel.getSortDirection()
            toggleOrder.check(
                    if (currentDir == MainViewModel.SortDirection.ASC) R.id.btnAsc else R.id.btnDesc
            )

            val currentField = viewModel.getSortField()
            val radioId =
                    when (currentField) {
                        MainViewModel.SortField.CREATED -> R.id.radioCreated
                        MainViewModel.SortField.TIME -> R.id.radioTime
                        MainViewModel.SortField.START_DATE -> R.id.radioDate
                        MainViewModel.SortField.ENABLED -> R.id.radioEnabled
                    }
            radioGroup.check(radioId)

            // Listeners
            toggleOrder.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val newDir =
                            if (checkedId == R.id.btnAsc) MainViewModel.SortDirection.ASC
                            else MainViewModel.SortDirection.DESC
                    viewModel.setSortOption(viewModel.getSortField(), newDir)
                }
            }

            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                val newField =
                        when (checkedId) {
                            R.id.radioCreated -> MainViewModel.SortField.CREATED
                            R.id.radioTime -> MainViewModel.SortField.TIME
                            R.id.radioDate -> MainViewModel.SortField.START_DATE
                            R.id.radioEnabled -> MainViewModel.SortField.ENABLED
                            else -> MainViewModel.SortField.CREATED
                        }
                viewModel.setSortOption(newField, viewModel.getSortDirection())
            }

            dialog.show()
        }
    }

    // Re-sets alarms for all enabled schedules. Covers the case where alarms were
    // silently lost (e.g. user force-stopped the app or exact-alarm permission was revoked).
    // Idempotent: if alarms are already set, this just overwrites them with the same values.
    private fun restoreAlarmsIfNeeded() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SmsScheduleRepository(applicationContext).rescheduleAllEnabled()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to restore alarms", e)
            }
        }
    }

    // Check required permissions (SMS)
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.SEND_SMS)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            // Permissions granted, check for special permissions
            checkSpecialPermissions()
        }
    }

    private fun checkSpecialPermissions() {
        checkAlarmPermission()
        checkBatteryOptimization()
    }

    // Check SCHEDULE_EXACT_ALARM permission for Android 12+
    private fun checkAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                showBatteryOptimizationDialog()
            }
        }
    }

    private fun showBatteryOptimizationDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.battery_optimization)
                .setMessage(R.string.battery_optimization_message)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.fromParts("package", packageName, null)
                    startActivity(intent)
                }
                .setNegativeButton(R.string.later, null)
                .show()
    }

    // Verify if all required permissions are granted
    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
    }

    // Show dialog if permissions are denied
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
                .setTitle(R.string.permissions_required)
                .setMessage(R.string.permissions_required_message)
                .setPositiveButton(R.string.settings_button) { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", packageName, null)
                    startActivity(intent)
                }
                .setNegativeButton(R.string.cancel_button, null)
                .show()
    }

    // Show confirmation dialog before deleting a schedule
    private fun showDeleteConfirmation(scheduleId: Long, contactName: String) {
        AlertDialog.Builder(this)
                .setTitle(R.string.delete_schedule)
                .setMessage(getString(R.string.delete_schedule_confirm, contactName))
                .setPositiveButton(R.string.delete_button) { _, _ ->
                    viewModel.deleteSchedule(scheduleId)
                    Toast.makeText(this, getString(R.string.schedule_deleted), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel_button, null)
                .show()
    }
}
