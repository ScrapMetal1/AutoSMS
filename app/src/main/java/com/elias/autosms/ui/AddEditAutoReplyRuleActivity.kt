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

class AddEditAutoReplyRuleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditAutoReplyBinding
    private lateinit var viewModel: AddEditAutoReplyViewModel
    private var existingId: Long? = null

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
        setupListeners()
        observeViewModel()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun loadExistingRule() {
        val rule: AutoReplyRule? =
                intent.getParcelableExtra(EXTRA_RULE)
        if (rule != null) {
            existingId = rule.id
            supportActionBar?.setTitle(R.string.auto_reply_edit_title)
            binding.editDisplayName.setText(rule.displayName)
            binding.editPhoneNumber.setText(rule.phoneNumber)
            binding.editPrompt.setText(rule.systemPrompt)
            binding.switchEnabled.isChecked = rule.isEnabled
        } else {
            supportActionBar?.setTitle(R.string.auto_reply_add_title)
        }
    }

    private fun setupListeners() {
        binding.buttonPickContact.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED) {
                launchContactPicker()
            } else {
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }

        binding.buttonTest.setOnClickListener {
            viewModel.runTest(
                    binding.editPrompt.text?.toString().orEmpty(),
                    SAMPLE_INBOUND
            )
        }

        binding.buttonSave.setOnClickListener {
            val name = binding.editDisplayName.text?.toString().orEmpty().trim()
            val number = binding.editPhoneNumber.text?.toString().orEmpty().trim()
            val prompt = binding.editPrompt.text?.toString().orEmpty().trim()
            if (number.isBlank()) {
                Toast.makeText(this, R.string.please_enter_phone_number, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (prompt.isBlank()) {
                Toast.makeText(this, R.string.auto_reply_prompt_helper, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.save(
                    existingId = existingId,
                    displayName = name.ifBlank { number },
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

    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    private fun handleContactSelection(uri: Uri) {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIdx >= 0) binding.editDisplayName.setText(cursor.getString(nameIdx))
            if (numberIdx >= 0) binding.editPhoneNumber.setText(cursor.getString(numberIdx))
        }
    }

    companion object {
        const val EXTRA_RULE = "rule"
        // Sample message used by the "Test reply" button.
        private const val SAMPLE_INBOUND = "Hey, are you free for lunch?"
    }
}
