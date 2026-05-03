package com.elias.autosms.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elias.autosms.data.ContextDocument
import com.elias.autosms.repository.ContextDocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class AddEditContextDocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ContextDocumentRepository(application)

    sealed class SaveResult {
        data object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    val importedText = MutableLiveData<String?>()
    val importError = MutableLiveData<String?>()
    val saveResult = MutableLiveData<SaveResult?>()

    fun importTextFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = readFile(uri)
                if (text.length > ContextDocument.MAX_DOCUMENT_CHARS) {
                    importError.postValue(
                            "File is too large (${text.length} chars). " +
                                    "Max is ${ContextDocument.MAX_DOCUMENT_CHARS}."
                    )
                    return@launch
                }
                importedText.postValue(text)
            } catch (t: Throwable) {
                importError.postValue("Could not read file: ${t.message}")
            }
        }
    }

    private fun readFile(uri: Uri): String {
        val cr = getApplication<Application>().contentResolver
        return cr.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        } ?: ""
    }

    fun save(existingId: Long?, title: String, content: String) {
        viewModelScope.launch {
            val trimmedTitle = title.trim()
            val trimmedContent = content.trim()
            if (trimmedTitle.isBlank()) {
                saveResult.postValue(SaveResult.Error("Add a title."))
                return@launch
            }
            if (trimmedContent.isBlank()) {
                saveResult.postValue(SaveResult.Error("Add some content."))
                return@launch
            }
            if (trimmedContent.length > ContextDocument.MAX_DOCUMENT_CHARS) {
                saveResult.postValue(SaveResult.Error(
                        "Document exceeds the per-doc cap of " +
                                "${ContextDocument.MAX_DOCUMENT_CHARS} chars."
                ))
                return@launch
            }
            // Soft-cap across all enabled docs so a single huge doc can't blow
            // the prompt budget. Disabled docs don't count against the limit.
            val candidateId = existingId ?: 0L
            val projected = withContext(Dispatchers.IO) {
                repository.projectedEnabledChars(candidateId, trimmedContent)
            }
            if (projected > ContextDocument.MAX_TOTAL_ENABLED_CHARS) {
                saveResult.postValue(SaveResult.Error(
                        "Saving this would exceed the total enabled-context budget of " +
                                "${ContextDocument.MAX_TOTAL_ENABLED_CHARS} chars " +
                                "(would be $projected). Disable other docs first."
                ))
                return@launch
            }

            val now = System.currentTimeMillis()
            if (existingId == null) {
                repository.insert(
                        ContextDocument(
                                title = trimmedTitle,
                                content = trimmedContent,
                                createdAt = now,
                                updatedAt = now
                        )
                )
            } else {
                val existing = repository.getById(existingId)
                if (existing == null) {
                    saveResult.postValue(SaveResult.Error("Document no longer exists."))
                    return@launch
                }
                repository.update(
                        existing.copy(
                                title = trimmedTitle,
                                content = trimmedContent,
                                updatedAt = now
                        )
                )
            }
            saveResult.postValue(SaveResult.Success)
        }
    }
}

class AddEditContextDocumentViewModelFactory(private val app: Application) :
        ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddEditContextDocumentViewModel(app) as T
}
