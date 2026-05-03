package com.elias.autosms.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.elias.autosms.data.AutoReplyHistory
import com.elias.autosms.data.AutoReplyRule
import com.elias.autosms.data.SmsScheduleDatabase
import com.elias.autosms.utils.PhoneNumberMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoReplyRepository(context: Context) {

    private val database by lazy { SmsScheduleDatabase.getDatabase(context) }
    private val ruleDao by lazy { database.autoReplyRuleDao() }
    private val historyDao by lazy { database.autoReplyHistoryDao() }

    fun getAllRules(): LiveData<List<AutoReplyRule>> = ruleDao.getAllRules()

    fun getRecentHistory(): LiveData<List<AutoReplyHistory>> = historyDao.getRecent()

    suspend fun getRuleById(id: Long): AutoReplyRule? =
            withContext(Dispatchers.IO) { ruleDao.getRuleById(id) }

    suspend fun insert(rule: AutoReplyRule): Long =
            withContext(Dispatchers.IO) { ruleDao.insert(rule) }

    suspend fun update(rule: AutoReplyRule) =
            withContext(Dispatchers.IO) { ruleDao.update(rule) }

    suspend fun delete(rule: AutoReplyRule) =
            withContext(Dispatchers.IO) { ruleDao.delete(rule) }

    suspend fun setEnabled(id: Long, enabled: Boolean) =
            withContext(Dispatchers.IO) { ruleDao.updateEnabled(id, enabled) }

    // Called from the notification listener (background thread). Walks the
    // enabled rules and returns the first whose phone number matches the sender.
    suspend fun findMatchingRule(sender: String): AutoReplyRule? =
            withContext(Dispatchers.IO) {
                ruleDao.getEnabledRules().firstOrNull { rule ->
                    PhoneNumberMatcher.matches(rule.phoneNumber, sender)
                }
            }

    suspend fun logHistory(entry: AutoReplyHistory) {
        withContext(Dispatchers.IO) {
            historyDao.insert(entry)
            historyDao.trim()
        }
    }
}
