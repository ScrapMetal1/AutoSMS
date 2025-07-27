package com.elias.autosms.ui

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.elias.autosms.data.SmsSchedule
import com.elias.autosms.databinding.ActivityAddEditScheduleBinding
import com.elias.autosms.viewmodel.AddEditScheduleViewModel
import com.elias.autosms.viewmodel.AddEditScheduleViewModelFactory
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.*

class AddEditScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditScheduleBinding
    private lateinit var viewModel: AddEditScheduleViewModel
    private var selectedContact: Pair<String, String>? = null
    private var selectedHour = 9
    private var selectedMinute = 0
    private var isEditMode = false
    private var scheduleId: Long = 0
    private var isContactMode = true

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                handleContactSelection(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupViews()
        handleIntent()
    }

    // Initialize ViewModel with factory
    private fun setupViewModel() {
        val factory = AddEditScheduleViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[AddEditScheduleViewModel::class.java]
    }

    // Setup UI components and listeners
    private fun setupViews() {
        // Set current time as default
        val now = Calendar.getInstance()
        selectedHour = now.get(Calendar.HOUR_OF_DAY)
        selectedMinute = now.get(Calendar.MINUTE)
        updateTimeDisplay()

        // Setup toggle group for contact/phone mode
        binding.toggleGroupContactType.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.buttonContactMode -> {
                        isContactMode = true
                        binding.layoutContactMode.visibility = View.VISIBLE
                        binding.layoutPhoneMode.visibility = View.GONE
                    }
                    R.id.buttonPhoneMode -> {
                        isContactMode = false
                        binding.layoutContactMode.visibility = View.GONE
                        binding.layoutPhoneMode.visibility = View.VISIBLE
                    }
                }
            }
        }

        // Set default selection to contact mode
        binding.toggleGroupContactType.check(R.id.buttonContactMode)

        binding.buttonSelectContact.setOnClickListener {
            openContactPicker()
        }

        binding.buttonSelectTime.setOnClickListener {
            showTimePicker()
        }

        binding.buttonSave.setOnClickListener {
            saveSchedule()
        }

        binding.buttonCancel.setOnClickListener {
            finish()
        }

        // Setup phone number input listener
        binding.editTextPhoneNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val phoneNumber = s.toString().trim()
                if (phoneNumber.isNotEmpty()) {
                    binding.textPhoneNumberDisplay.text = getString(R.string.phone_display_format, phoneNumber)
                    binding.textPhoneNumberDisplay.visibility = View.VISIBLE
                } else {
                    binding.textPhoneNumberDisplay.visibility = View.GONE
                }
            }
        })

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    // Handle incoming intent for edit mode
    private fun handleIntent() {
        val schedule = intent.getParcelableExtra<SmsSchedule>("schedule")
        if (schedule != null) {
            isEditMode = true
            scheduleId = schedule.id
            title = "Edit AutoSMS Schedule"

            // Populate fields with existing data
            selectedContact = Pair(schedule.contactName, schedule.phoneNumber)
            
            // Check if the contact name looks like a phone number (no spaces, mostly digits)
            val isPhoneNumber = schedule.contactName.matches(Regex("^[0-9+\\-\\(\\)\\s]+$"))
            
            if (isPhoneNumber) {
                // Switch to phone mode
                binding.toggleGroupContactType.check(R.id.buttonPhoneMode)
                binding.editTextPhoneNumber.setText(schedule.phoneNumber)
                binding.textPhoneNumberDisplay.text = getString(R.string.phone_display_format, schedule.phoneNumber)
                binding.textPhoneNumberDisplay.visibility = View.VISIBLE
            } else {
                // Stay in contact mode
                binding.textSelectedContact.text = "${schedule.contactName} (${schedule.phoneNumber})"
            }
            
            binding.editTextMessage.setText(schedule.message)
            selectedHour = schedule.hour
            selectedMinute = schedule.minute
            updateTimeDisplay()
        } else {
            title = "Add AutoSMS Schedule"
        }
    }

    // Launch contact picker to select a contact
    private fun openContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    // Process selected contact data
    private fun handleContactSelection(uri: Uri) {
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                if (nameIndex != -1 && numberIndex != -1) {
                    val contactName = it.getString(nameIndex)
                    val phoneNumber = it.getString(numberIndex)

                    selectedContact = Pair(contactName, phoneNumber)
                    binding.textSelectedContact.text = "$contactName ($phoneNumber)"
                } else {
                    Toast.makeText(this, "Unable to retrieve contact details", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Show time picker dialog for selecting schedule time
    private fun showTimePicker() {
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(selectedHour)
            .setMinute(selectedMinute)
            .setTitleText("Select time")
            .build()

        timePicker.addOnPositiveButtonClickListener {
            selectedHour = timePicker.hour
            selectedMinute = timePicker.minute
            updateTimeDisplay()
        }

        timePicker.show(supportFragmentManager, "TIME_PICKER")
    }

    // Update time display in UI
    private fun updateTimeDisplay() {
        val hourStr = if (selectedHour == 0) "12" else if (selectedHour > 12) "${selectedHour - 12}" else "$selectedHour"
        val minuteStr = if (selectedMinute < 10) "0$selectedMinute" else "$selectedMinute"
        val amPm = if (selectedHour < 12) "AM" else "PM"
        binding.textSelectedTime.text = "$hourStr:$minuteStr $amPm"
    }

    // Save or update the schedule
    private fun saveSchedule() {
        val message = binding.editTextMessage.text.toString().trim()
        var contactName = ""
        var phoneNumber = ""

        if (isContactMode) {
            val contact = selectedContact
            if (contact == null) {
                Toast.makeText(this, getString(R.string.please_select_contact), Toast.LENGTH_SHORT).show()
                return
            }
            contactName = contact.first
            phoneNumber = contact.second
        } else {
            phoneNumber = binding.editTextPhoneNumber.text.toString().trim()
            if (phoneNumber.isEmpty()) {
                Toast.makeText(this, getString(R.string.please_enter_phone_number), Toast.LENGTH_SHORT).show()
                return
            }
            // Validate phone number format
            if (!isValidPhoneNumber(phoneNumber)) {
                Toast.makeText(this, getString(R.string.please_enter_valid_phone_number), Toast.LENGTH_SHORT).show()
                return
            }
            contactName = phoneNumber // Use phone number as contact name for non-contacts
        }

        if (message.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        val schedule = SmsSchedule(
            id = if (isEditMode) scheduleId else 0,
            contactName = contactName,
            phoneNumber = phoneNumber,
            message = message,
            hour = selectedHour,
            minute = selectedMinute,
            isEnabled = true
        )

        if (isEditMode) {
            viewModel.updateSchedule(schedule)
            Toast.makeText(this, "Schedule updated", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.insertSchedule(schedule)
            Toast.makeText(this, "Schedule saved", Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    // Validate phone number format
    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // Remove all non-digit characters for validation
        val digitsOnly = phoneNumber.replace(Regex("[^0-9]"), "")
        // Check if it has 10-15 digits (typical phone number length)
        return digitsOnly.length in 10..15
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}