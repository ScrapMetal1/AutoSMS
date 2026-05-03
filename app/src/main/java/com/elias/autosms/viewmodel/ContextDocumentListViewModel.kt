package com.elias.autosms.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elias.autosms.data.ContextDocument
import com.elias.autosms.repository.ContextDocumentRepository
import kotlinx.coroutines.launch

class ContextDocumentListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ContextDocumentRepository(application)

    val documents = repository.getAll()

    fun setEnabled(doc: ContextDocument, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(doc.id, enabled) }
    }

    fun delete(doc: ContextDocument) {
        viewModelScope.launch { repository.delete(doc) }
    }
}

class ContextDocumentListViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ContextDocumentListViewModel(app) as T
}
