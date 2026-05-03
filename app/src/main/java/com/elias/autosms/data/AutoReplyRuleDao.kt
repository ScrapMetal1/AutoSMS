package com.elias.autosms.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface AutoReplyRuleDao {

    @Query("SELECT * FROM auto_reply_rules ORDER BY createdAt DESC")
    fun getAllRules(): LiveData<List<AutoReplyRule>>

    @Query("SELECT * FROM auto_reply_rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<AutoReplyRule>

    @Query("SELECT * FROM auto_reply_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): AutoReplyRule?

    @Insert
    suspend fun insert(rule: AutoReplyRule): Long

    @Update
    suspend fun update(rule: AutoReplyRule)

    @Delete
    suspend fun delete(rule: AutoReplyRule)

    @Query("DELETE FROM auto_reply_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE auto_reply_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)
}
