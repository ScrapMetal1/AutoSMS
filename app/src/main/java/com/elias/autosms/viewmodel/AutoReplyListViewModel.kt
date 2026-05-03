package com.elias.autosms.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elias.autosms.data.AutoReplyRule
import com.elias.autosms.repository.AutoReplyRepository
import kotlinx.coroutines.launch

class AutoReplyListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AutoReplyRepository(application)

    val rules = repository.getAllRules()

    fun setEnabled(rule: AutoReplyRule, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(rule.id, enabled) }
    }

    fun delete(rule: AutoReplyRule) {
        viewModelScope.launch { repository.delete(rule) }
    }
}

class AutoReplyListViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AutoReplyListViewModel(app) as T
}
