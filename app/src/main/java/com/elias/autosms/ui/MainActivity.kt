package com.elias.autosms.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.elias.autosms.databinding.ActivityMainBinding
import com.elias.autosms.ui.adapter.SmsScheduleAdapter
import com.elias.autosms.viewmodel.MainViewModel
import com.elias.autosms.viewmodel.MainViewModelFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: SmsScheduleAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            checkAlarmPermission()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupRecyclerView()
        setupFab()
        checkPermissions()
        observeSchedules()
    }

    // Initialize ViewModel with factory
    private fun setupViewModel() {
        val factory = MainViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]
    }

    // Setup RecyclerView with adapter for displaying schedules
    private fun setupRecyclerView() {
        adapter = SmsScheduleAdapter(
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

    // Observe LiveData for schedule updates
    private fun observeSchedules() {
        viewModel.allSchedules.observe(this) { schedules ->
            adapter.submitList(schedules)
            binding.emptyView.visibility = if (schedules.isEmpty()) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
    }

    // Check required permissions (SMS and Contacts)
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            checkAlarmPermission()
        }
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

    // Verify if all required permissions are granted
    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    // Show dialog if permissions are denied
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("AutoSMS requires SMS and Contacts permissions to function properly. Please grant these permissions in Settings.")
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