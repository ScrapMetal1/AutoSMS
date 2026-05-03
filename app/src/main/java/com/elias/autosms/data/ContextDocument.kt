package com.elias.autosms.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * A piece of background information the user wants Gemini to reference when
 * composing replies — e.g. "my business hours", "FAQ about my Etsy shop",
 * "talking points for sales calls".
 *
 * Documents are global to the user (not per-rule) for v1: every enabled doc is
 * concatenated into the prompt for every reply. A per-rule join table can be
 * added later without a destructive migration when scoping becomes useful.
 */
@Entity(tableName = "context_documents")
@Parcelize
data class ContextDocument(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val title: String,
        // Plain text. Cap enforced at the UI layer (MAX_DOCUMENT_CHARS).
        val content: String,
        val isEnabled: Boolean = true,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
) : Parcelable {

    fun characterCount(): Int = content.length

    companion object {
        // Single-doc cap. Anything bigger probably warrants retrieval, which
        // we'll add later. 100KB ~= 25k tokens, well within Gemini's 1M window.
        const val MAX_DOCUMENT_CHARS = 100_000

        // Sum across all enabled docs. ~125k tokens — leaves plenty of room for
        // the rule prompt + inbound message + reply within the 1M context.
        const val MAX_TOTAL_ENABLED_CHARS = 500_000
    }
}
