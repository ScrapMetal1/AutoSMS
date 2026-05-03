package com.elias.autosms.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface AutoReplyHistoryDao {

    @Query("SELECT * FROM auto_reply_history ORDER BY timestamp DESC LIMIT 200")
    fun getRecent(): LiveData<List<AutoReplyHistory>>

    @Insert
    suspend fun insert(entry: AutoReplyHistory): Long

    // Cap log size so we don't grow forever; called opportunistically after inserts.
    @Query("DELETE FROM auto_reply_history WHERE id NOT IN " +
            "(SELECT id FROM auto_reply_history ORDER BY timestamp DESC LIMIT 500)")
    suspend fun trim()
}
