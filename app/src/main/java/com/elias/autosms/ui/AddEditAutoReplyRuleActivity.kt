package com.elias.autosms.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.elias.autosms.R
import com.elias.autosms.data.AutoReplyRule
import com.elias.autosms.databinding.ActivityAddEditAutoReplyBinding
import com.elias.autosms.viewmodel.AddEditAutoReplyViewModel
import com.elias.autosms.viewmodel.AddEditAutoReplyViewModelFactory
import com.google.android.material.chip.Chip

class AddEditAutoReplyRuleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditAutoReplyBinding
    private lateinit var viewModel: AddEditAutoReplyViewModel
    private var existingId: Long? = null

    /**
     * Tracks how the rule's number was sourced. Affects which UI block we
     * display (selected-contact summary vs. manual textfield) and whether we
     * should auto-populate the display name.
     */
    private enum class ContactMode { EMPTY, PICKED, MANUAL }
    private var mode: ContactMode = ContactMode.EMPTY

    // Holds the currently chosen number — single source of truth, since both
    // contact pick and manual entry write into it.
    private var currentNumber: String = ""
    private var currentDisplayName: String = ""

    private val contactPickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let(::handleContactSelection)
                }
            }

    private val contactsPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) launchContactPicker()
                else Toast.makeText(
                        this, R.string.please_select_contact, Toast.LENGTH_SHORT
                ).show()
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditAutoReplyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val factory = AddEditAutoReplyViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[AddEditAutoReplyViewModel::class.java]

        loadExistingRule()
        setupContactSection()
        setupPromptExamples()
        setupActions()
        observeViewModel()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun loadExistingRule() {
        val rule: AutoReplyRule? = intent.getParcelableExtra(EXTRA_RULE)
        if (rule != null) {
            existingId = rule.id
            supportActionBar?.setTitle(R.string.auto_reply_edit_title)
            currentNumber = rule.phoneNumber
            currentDisplayName = rule.displayName
            binding.editDisplayName.setText(rule.displayName)
            binding.editPhoneNumber.setText(rule.phoneNumber)
            binding.editPrompt.setText(rule.systemPrompt)
            binding.switchEnabled.isChecked = rule.isEnabled
            // Existing rules go straight to the "selected" view — the user
            // already committed to a number, no need to make them re-pick.
            applyMode(ContactMode.PICKED, rule.displayName, rule.phoneNumber)
        } else {
            supportActionBar?.setTitle(R.string.auto_reply_add_title)
            applyMode(ContactMode.EMPTY)
        }
    }

    private fun setupContactSection() {
        binding.buttonPickContact.setOnClickListener { requestContactPicker() }
        binding.buttonChangeContact.setOnClickListener { requestContactPicker() }
        binding.buttonEnterManually.setOnClickListener {
            applyMode(ContactMode.MANUAL)
            binding.editPhoneNumber.requestFocus()
        }

        // Manual edits flow into the single-source-of-truth fields and keep
        // the display-name in sync if the user hasn't customised it.
        binding.editPhoneNumber.addTextChangedListener(SimpleTextWatcher { text ->
            currentNumber = text
        })
        binding.editDisplayName.addTextChangedListener(SimpleTextWatcher { text ->
            currentDisplayName = text
        })
    }

    private fun applyMode(
            newMode: ContactMode,
            name: String = currentDisplayName,
            number: String = currentNumber
    ) {
        mode = newMode
        currentDisplayName = name
        currentNumber = number
        when (newMode) {
            ContactMode.EMPTY -> {
                binding.contactEmptyGroup.visibility = View.VISIBLE
                binding.contactSelectedGroup.visibility = View.GONE
                binding.inputManualNumberLayout.visibility = View.GONE
            }
            ContactMode.MANUAL -> {
                binding.contactEmptyGroup.visibility = View.GONE
                binding.contactSelectedGroup.visibility = View.GONE
                binding.inputManualNumberLayout.visibility = View.VISIBLE
            }
            ContactMode.PICKED -> {
                binding.contactEmptyGroup.visibility = View.GONE
                binding.inputManualNumberLayout.visibility = View.GONE
                binding.contactSelectedGroup.visibility = View.VISIBLE
                binding.textSelectedName.text = name.ifBlank { number }
                binding.textSelectedNumber.text = number
            }
        }
    }

    private fun requestContactPicker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            launchContactPicker()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    private fun handleContactSelection(uri: Uri) {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
            val number = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty() else ""
            binding.editDisplayName.setText(name)
            binding.editPhoneNumber.setText(number)
            applyMode(ContactMode.PICKED, name, number)
        }
    }

    /**
     * Populates the prompt-suggestion chip row from the strings array. Chips
     * are non-checkable — a tap just stamps the example into the prompt
     * field, then we strip our chip so it doesn't get re-tapped accidentally.
     * Multi-tap doesn't append (we set, not append) — the user can edit the
     * stamped text from there.
     */
    private fun setupPromptExamples() {
        val examples = resources.getStringArray(R.array.auto_reply_prompt_examples)
        binding.chipGroupExamples.removeAllViews()
        for (text in examples) {
            val chip = Chip(this).apply {
                this.text = text
                isClickable = true
                isCheckable = false
                setOnClickListener {
                    binding.editPrompt.setText(text)
                    binding.editPrompt.setSelection(text.length)
                }
            }
            binding.chipGroupExamples.addView(chip)
        }
    }

    private fun setupActions() {
        binding.buttonTest.setOnClickListener {
            viewModel.runTest(
                    binding.editPrompt.text?.toString().orEmpty(),
                    SAMPLE_INBOUND
            )
        }

        binding.buttonSave.setOnClickListener {
            val number = currentNumber.trim()
            val prompt = binding.editPrompt.text?.toString().orEmpty().trim()
            if (number.isBlank()) {
                Toast.makeText(this, R.string.please_enter_phone_number, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (prompt.isBlank()) {
                Toast.makeText(this, R.string.auto_reply_prompt_examples_label, Toast.LENGTH_SHORT)
                        .show()
                binding.editPrompt.requestFocus()
                return@setOnClickListener
            }
            viewModel.save(
                    existingId = existingId,
                    displayName = currentDisplayName.trim().ifBlank { number },
                    phoneNumber = number,
                    prompt = prompt,
                    enabled = binding.switchEnabled.isChecked
            )
        }
    }

    private fun observeViewModel() {
        viewModel.testResult.observe(this) { text ->
            text ?: return@observe
            binding.cardTestResult.visibility = View.VISIBLE
            binding.textTestResult.text = text
        }
        viewModel.isTesting.observe(this) { busy ->
            binding.buttonTest.isEnabled = !busy
        }
        viewModel.saveComplete.observe(this) { done ->
            if (done == true) {
                Toast.makeText(this, R.string.schedule_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /** Lightweight TextWatcher — the AndroidX one's verbose, this is one-liner. */
    private class SimpleTextWatcher(private val onChange: (String) -> Unit)
            : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) { onChange(s?.toString().orEmpty()) }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    companion object {
        const val EXTRA_RULE = "rule"
        // Sample message used by the "Test reply" button.
        private const val SAMPLE_INBOUND = "Hey, are you free for lunch?"
    }
}
