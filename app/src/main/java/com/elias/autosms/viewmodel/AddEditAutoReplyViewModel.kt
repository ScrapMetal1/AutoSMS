package com.elias.autosms.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elias.autosms.ai.AiReplyGenerator
import com.elias.autosms.data.AutoReplyRule
import com.elias.autosms.repository.AutoReplyRepository
import com.elias.autosms.repository.ContextDocumentRepository
import kotlinx.coroutines.launch

class AddEditAutoReplyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AutoReplyRepository(application)
    private val documents = ContextDocumentRepository(application)
    private val generator = AiReplyGenerator(application)

    val testResult = MutableLiveData<String?>()
    val isTesting = MutableLiveData(false)
    val saveComplete = MutableLiveData(false)

    fun save(existingId: Long?, displayName: String, phoneNumber: String, prompt: String, enabled: Boolean) {
        viewModelScope.launch {
            val rule = AutoReplyRule(
                    id = existingId ?: 0,
                    displayName = displayName.trim(),
                    phoneNumber = phoneNumber.trim(),
                    systemPrompt = prompt.trim(),
                    isEnabled = enabled,
                    createdAt = System.currentTimeMillis()
            )
            if (existingId == null) repository.insert(rule) else repository.update(rule)
            saveComplete.postValue(true)
        }
    }

    // Generates a sample reply against a synthetic inbound message so the user
    // can sanity-check their prompt before saving.
    fun runTest(prompt: String, syntheticInbound: String) {
        if (prompt.isBlank()) {
            testResult.value = "Add a prompt first."
            return
        }
        isTesting.value = true
        viewModelScope.launch {
            val snippets = documents.getEnabled().map {
                AiReplyGenerator.ContextSnippet(it.title, it.content)
            }
            val result = generator.generate(prompt, syntheticInbound, snippets)
            testResult.postValue(
                    when (result) {
                        is AiReplyGenerator.Result.Success -> result.reply
                        AiReplyGenerator.Result.BlockedBySafety -> "Blocked by safety filter."
                        is AiReplyGenerator.Result.Error -> "Error: ${result.message}"
                        AiReplyGenerator.Result.NotConfigured ->
                                "Firebase not configured — drop google-services.json into the app module."
                    }
            )
            isTesting.postValue(false)
        }
    }
}

class AddEditAutoReplyViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddEditAutoReplyViewModel(app) as T
}
