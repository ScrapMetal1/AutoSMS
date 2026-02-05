package com.elias.autosms.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.elias.autosms.R
import com.elias.autosms.databinding.ActivityMainBinding
import com.elias.autosms.ui.adapter.SmsScheduleAdapter
import com.elias.autosms.viewmodel.MainViewModel
import com.elias.autosms.viewmodel.MainViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup

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
                    Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
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

        setupToolbar()
        setupViewModel()
        setupRecyclerView()
        setupFab()
        setupSortButton() // Call the new setupSortButton
        checkPermissions()
        observeSchedules()
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
        supportActionBar?.title = "AutoSMS"
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
            val sheetView = layoutInflater.inflate(R.layout.dialog_sort_options, null)
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
                .setTitle("Battery Optimization")
                .setMessage(
                        "To ensure your scheduled messages are sent exactly on time, AutoSMS needs to operate without background restrictions.\n\nPlease select 'No restrictions' or 'Unrestricted' in the following settings screen."
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.fromParts("package", packageName, null)
                    startActivity(intent)
                }
                .setNegativeButton("Later", null)
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
                .setTitle("Permissions Required")
                .setMessage(
                        "AutoSMS requires SMS permission to function properly. Please grant this permission in Settings."
                )
                .setPositiveButton("Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", packageName, null)
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
    }

    // Show confirmation dialog before deleting a schedule
    private fun showDeleteConfirmation(scheduleId: Long, contactName: String) {
        AlertDialog.Builder(this)
                .setTitle("Delete Schedule")
                .setMessage("Are you sure you want to delete the SMS schedule for $contactName?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteSchedule(scheduleId)
                    Toast.makeText(this, "Schedule deleted", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
    }
}
