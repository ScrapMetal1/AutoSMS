package com.elias.autosms.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.elias.autosms.databinding.ActivitySettingsBinding
import com.elias.autosms.utils.ChatGptService
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var chatGptService: ChatGptService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatGptService = ChatGptService(this)
        
        setupToolbar()
        setupApiKeySection()
        setupModelSection()
        setupTestButton()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
    }

    private fun setupApiKeySection() {
        // Load existing API key if available
        if (chatGptService.hasApiKey()) {
            binding.etApiKey.setText("••••••••••••••••••••••••••••••••")
            binding.btnSaveApiKey.text = "Update API Key"
        }

        binding.btnSaveApiKey.setOnClickListener {
            val apiKey = binding.etApiKey.text.toString().trim()
            if (apiKey.isBlank()) {
                binding.etApiKey.error = "API key is required"
                return@setOnClickListener
            }

            chatGptService.setApiKey(apiKey)
            Toast.makeText(this, "API key saved successfully", Toast.LENGTH_SHORT).show()
            binding.btnSaveApiKey.text = "Update API Key"
            binding.etApiKey.setText("••••••••••••••••••••••••••••••••")
        }
    }

    private fun setupModelSection() {
        // Populate dropdown
        val models = resources.getStringArray(com.elias.autosms.R.array.openai_models)
        binding.autoCompleteModel.setSimpleItems(models)

        // Set current selection
        val currentModel = chatGptService.getPreferredModel()
        val selectedLabel = models.find { it.equals(currentModel, ignoreCase = true) } ?: models.first()
        binding.autoCompleteModel.setText(selectedLabel, false)

        binding.autoCompleteModel.setOnItemClickListener { parent, _, position, _ ->
            val model = parent.getItemAtPosition(position) as String
            chatGptService.setPreferredModel(model)
            Toast.makeText(this, "Model set to $model", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTestButton() {
        binding.btnTestApiKey.setOnClickListener {
            if (!chatGptService.hasApiKey()) {
                Toast.makeText(this, "Please save your API key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnTestApiKey.isEnabled = false
            binding.btnTestApiKey.text = "Testing..."

            // Test the API key by generating a simple message
            lifecycleScope.launch {
                try {
                    val result = chatGptService.generateRandomMessage(
                        contactName = "Test Contact",
                        messageType = "friendly",
                        maxLength = 50
                    )
                    
                    if (result.isSuccess) {
                        Toast.makeText(
                            this@SettingsActivity,
                            "API key is valid! Test message: ${result.getOrNull()?.take(30)}...",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@SettingsActivity,
                            "API key test failed: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Error testing API key: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    binding.btnTestApiKey.isEnabled = true
                    binding.btnTestApiKey.text = "Test API Key"
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
