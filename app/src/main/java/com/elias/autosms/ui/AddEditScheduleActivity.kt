package com.elias.autosms.ui

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModelProvider
import com.elias.autosms.R
import com.elias.autosms.data.SmsSchedule
import com.elias.autosms.databinding.ActivityAddEditScheduleBinding
import com.elias.autosms.viewmodel.AddEditScheduleViewModel
import com.elias.autosms.viewmodel.AddEditScheduleViewModelFactory
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.*

class AddEditScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditScheduleBinding
    private lateinit var viewModel: AddEditScheduleViewModel

    // contact name and number. null if not selected
    private var selectedContact: Pair<String, String>? = null

    // time selection - default to 9:00 am
    private var selectedHour = 9
    private var selectedMinute = 0

    // create or edit mode?
    private var isEditMode = false

    private var scheduleId: Long = 0
    private var isContactMode = true
    private var originalCreatedAt: Long = System.currentTimeMillis()

    // handle result from contact picker
    private val contactPickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> handleContactSelection(uri) }
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

    override fun onDestroy() {
        super.onDestroy()
        cleanupResources()
    }

    private fun cleanupResources() {
        selectedContact = null
    }

    private fun setupViewModel() {
        val factory = AddEditScheduleViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[AddEditScheduleViewModel::class.java]
    }

    private fun setupViews() {
        val now = Calendar.getInstance()
        selectedHour = now.get(Calendar.HOUR_OF_DAY)
        selectedMinute = now.get(Calendar.MINUTE)
        updateTimeDisplay()

        // handle contact vs phone number mode toggle
        binding.toggleGroupContactType.addOnButtonCheckedListener { _, checkedId, isChecked ->
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

        binding.toggleGroupContactType.check(R.id.buttonContactMode)
        binding.layoutCustomMessage.visibility = View.VISIBLE

        binding.buttonSelectContact.setOnClickListener { openContactPicker() }
        binding.buttonSelectTime.setOnClickListener { showTimePicker() }
        binding.buttonSave.setOnClickListener { saveSchedule() }
        binding.buttonCancel.setOnClickListener { finish() }

        // debounced phone number input listener
        binding.editTextPhoneNumber.addTextChangedListener(
                object : TextWatcher {
                    private var lastText = ""

                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}
                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {}

                    override fun afterTextChanged(s: Editable?) {
                        val phoneNumber = s.toString().trim()

                        if (phoneNumber != lastText) {
                            lastText = phoneNumber

                            if (phoneNumber.isNotEmpty()) {
                                binding.textPhoneNumberDisplay.text =
                                        getString(R.string.phone_display_format, phoneNumber)
                                binding.textPhoneNumberDisplay.visibility = View.VISIBLE
                            } else {
                                binding.textPhoneNumberDisplay.visibility = View.GONE
                            }
                        }
                    }
                }
        )

        // Setup frequency dropdown adapter
        val frequencyOptions = resources.getStringArray(R.array.frequency_options)
        val frequencyAdapter =
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, frequencyOptions)
        binding.autoCompleteFrequency.setAdapter(frequencyAdapter)

        // Setup period unit dropdown adapter
        val unitOptions = resources.getStringArray(R.array.period_units_options)
        val unitAdapter =
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, unitOptions)
        binding.autoCompletePeriodUnit.setAdapter(unitAdapter)

        // Handle frequency selection changes to show/hide custom period fields
        binding.autoCompleteFrequency.setOnItemClickListener { _, _, position, _ ->
            val selectedFrequency = frequencyOptions[position]
            if (selectedFrequency == SmsSchedule.FREQUENCY_CUSTOM &&
                            binding.switchRecurring.isChecked
            ) {
                binding.layoutCustomPeriod.visibility = View.VISIBLE
            } else {
                binding.layoutCustomPeriod.visibility = View.GONE
            }
            updateRecurrenceWarning()
        }

        binding.autoCompletePeriodUnit.setOnItemClickListener { _, _, _, _ ->
            updateRecurrenceWarning()
        }

        binding.switchRecurring.setOnCheckedChangeListener { _, isChecked ->
            binding.inputLayoutFrequency.isEnabled = isChecked
            binding.inputLayoutCustomPeriod.isEnabled = isChecked
            binding.inputLayoutPeriodUnit.isEnabled = isChecked

            val alpha = if (isChecked) 1.0f else 0.5f
            binding.inputLayoutFrequency.alpha = alpha
            binding.layoutCustomPeriod.alpha = alpha

            // Show/hide custom recurrence fields based on current frequency selection
            if (isChecked &&
                            binding.autoCompleteFrequency.text.toString() ==
                                    SmsSchedule.FREQUENCY_CUSTOM
            ) {
                binding.layoutCustomPeriod.visibility = View.VISIBLE
            } else {
                binding.layoutCustomPeriod.visibility = View.GONE
            }
            updateRecurrenceWarning()
        }

        binding.switchRecurring.isChecked = false
        binding.inputLayoutFrequency.isEnabled = false
        binding.inputLayoutFrequency.alpha = 0.5f

        updateRecurrenceWarning()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun handleIntent() {
        val schedule = IntentCompat.getParcelableExtra(intent, "schedule", SmsSchedule::class.java)

        if (schedule != null) {
            isEditMode = true
            scheduleId = schedule.id
            originalCreatedAt = schedule.createdAt
            title = "Edit AutoSMS Schedule"

            selectedContact = Pair(schedule.contactName, schedule.phoneNumber)

            // check if name looks like a phone number (manual entry vs contact pick)
            val isPhoneNumber = schedule.contactName.matches(Regex("^[0-9+\\-\\(\\)\\s]+$"))

            if (isPhoneNumber) {
                binding.toggleGroupContactType.check(R.id.buttonPhoneMode)
                binding.editTextPhoneNumber.setText(schedule.phoneNumber)
                binding.textPhoneNumberDisplay.text =
                        getString(R.string.phone_display_format, schedule.phoneNumber)
                binding.textPhoneNumberDisplay.visibility = View.VISIBLE
            } else {
                binding.textSelectedContact.text =
                        "${schedule.contactName} (${schedule.phoneNumber})"
            }

            binding.editTextMessage.setText(schedule.message)
            selectedHour = schedule.hour
            selectedMinute = schedule.minute
            updateTimeDisplay()

            // --- fill in frequency, unit, and recurring state ---
            binding.switchRecurring.isChecked = schedule.isRecurring

            binding.inputLayoutFrequency.isEnabled = schedule.isRecurring
            binding.inputLayoutFrequency.alpha = if (schedule.isRecurring) 1.0f else 0.5f

            binding.autoCompleteFrequency.setText(schedule.frequency, false)

            if (schedule.frequency == SmsSchedule.FREQUENCY_CUSTOM) {
                if (schedule.isRecurring) binding.layoutCustomPeriod.visibility = View.VISIBLE
                binding.editTextCustomPeriod.setText(schedule.period.toString())
                binding.autoCompletePeriodUnit.setText(schedule.periodUnit, false)
            } else {
                binding.layoutCustomPeriod.visibility = View.GONE
            }
            updateRecurrenceWarning()
        } else {
            title = "Add AutoSMS Schedule"
        }
    }

    private fun openContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    private fun handleContactSelection(uri: Uri) {
        var cursor: Cursor? = null

        try {
            cursor = contentResolver.query(uri, null, null, null, null)

            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex =
                            it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex =
                            it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    if (nameIndex != -1 && numberIndex != -1) {
                        val contactName = it.getString(nameIndex)
                        val phoneNumber = it.getString(numberIndex)

                        selectedContact = Pair(contactName, phoneNumber)

                        binding.textSelectedContact.text = "$contactName ($phoneNumber)"
                    } else {
                        Toast.makeText(
                                        this,
                                        "Unable to retrieve contact details",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error retrieving contact: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
        } finally {
            cursor?.close()
        }
    }

    private fun showTimePicker() {
        val timePicker =
                MaterialTimePicker.Builder()
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

    private fun updateTimeDisplay() {
        val hourStr =
                if (selectedHour == 0) "12"
                else if (selectedHour > 12) "${selectedHour - 12}" else "$selectedHour"

        val minuteStr = if (selectedMinute < 10) "0$selectedMinute" else "$selectedMinute"
        val amPm = if (selectedHour < 12) "AM" else "PM"

        binding.textSelectedTime.text = "$hourStr:$minuteStr $amPm"
    }

    private fun updateRecurrenceWarning() {
        val isRecurring = binding.switchRecurring.isChecked
        val frequency = binding.autoCompleteFrequency.text.toString()
        val unit = binding.autoCompletePeriodUnit.text.toString()

        val isHourly =
                frequency == SmsSchedule.FREQUENCY_HOURLY ||
                        (frequency == SmsSchedule.FREQUENCY_CUSTOM &&
                                unit == SmsSchedule.UNIT_HOURS)

        if (isRecurring && isHourly) {
            binding.textHourlyWarning.visibility = View.VISIBLE
        } else {
            binding.textHourlyWarning.visibility = View.GONE
        }
    }

    private fun saveSchedule() {
        val message = binding.editTextMessage.text.toString().trim()

        var contactName: String
        var phoneNumber: String

        if (isContactMode) {
            val contact = selectedContact

            if (contact == null) {
                Toast.makeText(this, getString(R.string.please_select_contact), Toast.LENGTH_SHORT)
                        .show()
                return
            }

            contactName = contact.first
            phoneNumber = contact.second
        } else {
            phoneNumber = binding.editTextPhoneNumber.text.toString().trim()

            if (phoneNumber.isEmpty()) {
                Toast.makeText(
                                this,
                                getString(R.string.please_enter_phone_number),
                                Toast.LENGTH_SHORT
                        )
                        .show()
                return
            }

            if (!isValidPhoneNumber(phoneNumber)) {
                Toast.makeText(
                                this,
                                getString(R.string.please_enter_valid_phone_number),
                                Toast.LENGTH_SHORT
                        )
                        .show()
                return
            }

            // for manual entries, use phone number as contact name
            contactName = phoneNumber
        }

        if (message.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        val isRecurring = binding.switchRecurring.isChecked
        val frequency = binding.autoCompleteFrequency.text.toString()
        val periodUnit = binding.autoCompletePeriodUnit.text.toString()

        var period = 1
        if (frequency == SmsSchedule.FREQUENCY_CUSTOM) {
            val periodStr = binding.editTextCustomPeriod.text.toString()
            if (periodStr.isNotEmpty()) {
                period = periodStr.toIntOrNull() ?: 1
                if (period < 1) period = 1
            }
        }

        val schedule =
                SmsSchedule(
                        id = if (isEditMode) scheduleId else 0,
                        contactName = contactName,
                        phoneNumber = phoneNumber,
                        message = message,
                        hour = selectedHour,
                        minute = selectedMinute,
                        frequency = frequency,
                        period = period,
                        periodUnit = periodUnit,
                        isRecurring = isRecurring,
                        isEnabled = true,
                        createdAt = originalCreatedAt
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

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val digitsOnly = phoneNumber.replace(Regex("[^0-9]"), "")

        // ensure at least 3 digits
        return digitsOnly.length >= 3
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
