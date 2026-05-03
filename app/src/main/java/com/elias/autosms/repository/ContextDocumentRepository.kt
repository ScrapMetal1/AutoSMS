package com.elias.autosms.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.elias.autosms.data.ContextDocument
import com.elias.autosms.data.SmsScheduleDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContextDocumentRepository(context: Context) {

    private val database by lazy { SmsScheduleDatabase.getDatabase(context) }
    private val dao by lazy { database.contextDocumentDao() }

    fun getAll(): LiveData<List<ContextDocument>> = dao.getAll()

    suspend fun getEnabled(): List<ContextDocument> =
            withContext(Dispatchers.IO) { dao.getEnabled() }

    suspend fun getById(id: Long): ContextDocument? =
            withContext(Dispatchers.IO) { dao.getById(id) }

    suspend fun insert(doc: ContextDocument): Long =
            withContext(Dispatchers.IO) { dao.insert(doc) }

    suspend fun update(doc: ContextDocument) =
            withContext(Dispatchers.IO) { dao.update(doc) }

    suspend fun delete(doc: ContextDocument) =
            withContext(Dispatchers.IO) { dao.delete(doc) }

    suspend fun setEnabled(id: Long, enabled: Boolean) =
            withContext(Dispatchers.IO) { dao.updateEnabled(id, enabled) }

    /**
     * Returns the number of characters that would be enabled if [candidate]
     * (with [candidateContent]) were added or saved. Used by the editor to
     * surface a "you'd exceed the budget" warning before the user wastes time.
     */
    suspend fun projectedEnabledChars(candidateId: Long, candidateContent: String): Int =
            withContext(Dispatchers.IO) {
                dao.sumEnabledCharsExcluding(candidateId) + candidateContent.length
            }
}
