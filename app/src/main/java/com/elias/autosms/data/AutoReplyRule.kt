package com.elias.autosms.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "auto_reply_rules")
@Parcelize
data class AutoReplyRule(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        // Display label shown in the rule list. Can be the contact name, or a
        // user-supplied nickname when the rule was created from a raw number.
        val displayName: String,
        // Stored in E.164 form when possible, otherwise the raw user input.
        // Matching is done via PhoneNumberMatcher to be tolerant of formatting.
        val phoneNumber: String,
        // Free-form instruction sent to Gemini, e.g.
        // "Reply politely that I'm in a meeting and will respond after 3pm".
        val systemPrompt: String,
        val isEnabled: Boolean = true,
        val createdAt: Long = System.currentTimeMillis()
) : Parcelable
