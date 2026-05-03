package com.elias.autosms.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "auto_reply_history")
data class AutoReplyHistory(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        // Nullable: a reply may be triggered without matching a stored rule
        // (e.g. test runs from the editor) — null in that case.
        val ruleId: Long?,
        val phoneNumber: String,
        val inboundText: String,
        val replyText: String?,
        val status: String,
        val errorMessage: String? = null,
        val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_SENT = "sent"
        const val STATUS_BLOCKED_NOT_PREMIUM = "blocked_not_premium"
        const val STATUS_BLOCKED_SAFETY = "blocked_safety"
        const val STATUS_NO_REPLY_ACTION = "no_reply_action"
        const val STATUS_ERROR = "error"
    }
}
