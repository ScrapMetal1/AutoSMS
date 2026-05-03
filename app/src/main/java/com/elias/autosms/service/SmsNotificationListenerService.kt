package com.elias.autosms.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.elias.autosms.ai.AiReplyGenerator
import com.elias.autosms.billing.BillingManager
import com.elias.autosms.data.AutoReplyHistory
import com.elias.autosms.repository.AutoReplyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens for incoming SMS notifications posted by the user's default SMS app
 * and, when the sender matches an enabled [com.elias.autosms.data.AutoReplyRule],
 * fulfils the notification's built-in RemoteInput "Reply" action with a
 * Gemini-generated reply.
 *
 * Why this design (and not BroadcastReceiver on SMS_RECEIVED):
 *   - RECEIVE_SMS / READ_SMS are restricted by Google Play to default SMS apps.
 *     We don't want to be the default SMS app, so we read the public notification
 *     and reply through the SMS app's own RemoteInput action — no SMS perms.
 *   - The reply is sent via the user's actual messaging app, so it shows up in
 *     their normal SMS thread and uses their carrier as if they typed it.
 */
class SmsNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val repository by lazy { AutoReplyRepository(applicationContext) }
    private val generator by lazy { AiReplyGenerator(applicationContext) }
    private val billing by lazy { BillingManager.get(applicationContext) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        // Allow-list approach: only inspect known SMS apps, both to keep CPU
        // cost down and to avoid reacting to unrelated chat apps. Add more
        // packages over time as users report misses.
        if (pkg !in SMS_PACKAGES) return

        val notification = sbn.notification ?: return
        // Skip our own / group-summary / ongoing system notifications.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val sender = extractSender(notification) ?: return
        val body = extractMessageBody(notification) ?: return
        if (body.isBlank()) return

        scope.launch { handle(sbn, sender, body) }
    }

    private suspend fun handle(sbn: StatusBarNotification, sender: String, body: String) {
        try {
            val rule = repository.findMatchingRule(sender) ?: return

            if (!billing.entitlement.value.isEntitled) {
                Log.d(TAG, "Skipping reply for rule ${rule.id} — not entitled")
                repository.logHistory(
                        AutoReplyHistory(
                                ruleId = rule.id,
                                phoneNumber = sender,
                                inboundText = body,
                                replyText = null,
                                status = AutoReplyHistory.STATUS_BLOCKED_NOT_PREMIUM
                        )
                )
                return
            }

            val replyAction = findReplyAction(sbn.notification) ?: run {
                Log.w(TAG, "No RemoteInput action on notification from ${sbn.packageName}")
                repository.logHistory(
                        AutoReplyHistory(
                                ruleId = rule.id,
                                phoneNumber = sender,
                                inboundText = body,
                                replyText = null,
                                status = AutoReplyHistory.STATUS_NO_REPLY_ACTION
                        )
                )
                return
            }

            when (val result = generator.generate(rule.systemPrompt, body)) {
                is AiReplyGenerator.Result.Success -> {
                    val sent = sendReply(replyAction, result.reply)
                    repository.logHistory(
                            AutoReplyHistory(
                                    ruleId = rule.id,
                                    phoneNumber = sender,
                                    inboundText = body,
                                    replyText = result.reply,
                                    status = if (sent) AutoReplyHistory.STATUS_SENT
                                            else AutoReplyHistory.STATUS_ERROR,
                                    errorMessage = if (sent) null else "RemoteInput send failed"
                            )
                    )
                }
                AiReplyGenerator.Result.BlockedBySafety -> {
                    repository.logHistory(
                            AutoReplyHistory(
                                    ruleId = rule.id,
                                    phoneNumber = sender,
                                    inboundText = body,
                                    replyText = null,
                                    status = AutoReplyHistory.STATUS_BLOCKED_SAFETY
                            )
                    )
                }
                is AiReplyGenerator.Result.Error -> {
                    repository.logHistory(
                            AutoReplyHistory(
                                    ruleId = rule.id,
                                    phoneNumber = sender,
                                    inboundText = body,
                                    replyText = null,
                                    status = AutoReplyHistory.STATUS_ERROR,
                                    errorMessage = result.message
                            )
                    )
                }
                AiReplyGenerator.Result.NotConfigured -> {
                    Log.w(TAG, "AI not configured (no google-services.json) — skipping")
                }
            }
        } catch (t: Throwable) {
            // Listener callbacks must not throw — system would unbind us.
            Log.e(TAG, "handle() failed", t)
        }
    }

    private fun extractSender(notification: Notification): String? {
        // Try MessagingStyle first — the modern API gives us a Person with a
        // URI like "tel:+15551234567" which is exactly what we want.
        val style = NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)
        if (style != null) {
            val lastMessage = style.messages.lastOrNull()
            val person = lastMessage?.person
            val uri = person?.uri
            if (!uri.isNullOrBlank()) return uri.removePrefix("tel:")
            val name = person?.name?.toString()
            if (!name.isNullOrBlank()) return name
        }
        // Fall back to title — for SMS apps this is usually the sender name or
        // number. Number matching tolerates name vs. number mismatch through
        // PhoneNumberMatcher only when the user typed a number; if they used a
        // contact display name as the rule key, this still works.
        return notification.extras?.getString(Notification.EXTRA_TITLE)
    }

    private fun extractMessageBody(notification: Notification): String? {
        val style = NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)
        val styled = style?.messages?.lastOrNull()?.text?.toString()
        if (!styled.isNullOrBlank()) return styled
        val text = notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (!text.isNullOrBlank()) return text
        return notification.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
    }

    private fun findReplyAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        return actions.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true &&
                    action.actionIntent != null
        }
    }

    private fun sendReply(action: Notification.Action, text: String): Boolean {
        val remoteInput = action.remoteInputs?.firstOrNull() ?: return false
        return try {
            val intent = Intent()
            val bundle = Bundle().apply { putCharSequence(remoteInput.resultKey, text) }
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
            // Must mirror the SMS app's expected flag set; mutable so the system
            // can fill in the RemoteInput payload.
            (action.actionIntent as PendingIntent).send(applicationContext, 0, intent)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "RemoteInput send failed", t)
            false
        }
    }

    companion object {
        private const val TAG = "SmsAutoReplyListener"

        // Default SMS apps shipping on common OEMs. Add to this list as needed.
        private val SMS_PACKAGES = setOf(
                "com.google.android.apps.messaging",   // Google Messages
                "com.android.mms",                      // AOSP
                "com.samsung.android.messaging",        // Samsung
                "com.android.messaging",                // Older AOSP
                "com.textra",                           // Textra
                "com.handcent.app.nextsms",             // Handcent
                "com.moez.QKSMS",                       // QKSMS
                "com.simplemobiletools.smsmessenger",   // Simple SMS Messenger
                "xyz.klinker.messenger"                 // Pulse SMS
        )
    }
}
