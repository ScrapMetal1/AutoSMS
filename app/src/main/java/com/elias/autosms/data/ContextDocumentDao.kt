package com.elias.autosms.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ContextDocumentDao {

    @Query("SELECT * FROM context_documents ORDER BY updatedAt DESC")
    fun getAll(): LiveData<List<ContextDocument>>

    @Query("SELECT * FROM context_documents WHERE isEnabled = 1 ORDER BY updatedAt DESC")
    suspend fun getEnabled(): List<ContextDocument>

    @Query("SELECT * FROM context_documents WHERE id = :id")
    suspend fun getById(id: Long): ContextDocument?

    @Query("SELECT COALESCE(SUM(LENGTH(content)), 0) FROM context_documents " +
            "WHERE isEnabled = 1 AND id != :excludingId")
    suspend fun sumEnabledCharsExcluding(excludingId: Long): Int

    @Insert
    suspend fun insert(doc: ContextDocument): Long

    @Update
    suspend fun update(doc: ContextDocument)

    @Delete
    suspend fun delete(doc: ContextDocument)

    @Query("UPDATE context_documents SET isEnabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)
}
