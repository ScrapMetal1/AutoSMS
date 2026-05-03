package com.elias.autosms.ai

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.Firebase

/**
 * Calls Gemini on Vertex AI through Firebase AI Logic. Auth/quota live on the
 * Firebase project the user configures via [google-services.json]; the SDK uses
 * App Check (registered in [com.elias.autosms.AutoSMSApplication]) to keep API
 * usage tied to legitimate installs of this app.
 *
 * Returns a sealed [Result] so the caller (the notification listener) can
 * route safety blocks vs. network errors to the right history-log status
 * without throwing through the listener callback.
 */
class AiReplyGenerator(private val context: Context) {

    sealed class Result {
        data class Success(val reply: String) : Result()
        data object BlockedBySafety : Result()
        data class Error(val message: String) : Result()
        data object NotConfigured : Result()
    }

    private val model: GenerativeModel? by lazy { buildModel() }

    private fun buildModel(): GenerativeModel? {
        // No google-services.json yet → no FirebaseApp → bail cleanly so the
        // app still runs (everything else works; AI replies just don't fire).
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.w(TAG, "Firebase not initialized — drop google-services.json into app/")
            return null
        }
        return try {
            Firebase.ai(backend = GenerativeBackend.vertexAI()).generativeModel(
                    modelName = MODEL_NAME,
                    generationConfig = generationConfig {
                        // ~one SMS segment of headroom; Gemini will usually answer in well under this.
                        maxOutputTokens = 120
                        temperature = 0.7f
                    }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to construct GenerativeModel", t)
            null
        }
    }

    suspend fun generate(systemPrompt: String, inbound: String): Result {
        val m = model ?: return Result.NotConfigured
        return try {
            val prompt = content {
                text(buildPrompt(systemPrompt, inbound))
            }
            val response = m.generateContent(prompt)
            val text = response.text?.trim().orEmpty()
            if (text.isEmpty()) Result.Error("Empty response") else Result.Success(text)
        } catch (e: PromptBlockedException) {
            Log.w(TAG, "Prompt blocked", e)
            Result.BlockedBySafety
        } catch (e: ResponseStoppedException) {
            Log.w(TAG, "Response stopped (safety / recitation)", e)
            Result.BlockedBySafety
        } catch (t: Throwable) {
            Log.e(TAG, "Generation failed", t)
            Result.Error(t.message ?: t.javaClass.simpleName)
        }
    }

    // We fold the per-rule instruction into the user turn rather than using a
    // separate systemInstruction to keep behaviour consistent across model
    // versions that don't honour systemInstruction the same way. The wrapper
    // also constrains length and tone so replies remain SMS-appropriate.
    private fun buildPrompt(systemPrompt: String, inbound: String): String = """
        You are an SMS auto-responder for the user. Reply on the user's behalf
        following these instructions:

        ${systemPrompt.trim()}

        Hard rules:
        - Reply with the message text only — no preamble, no quotes, no signature.
        - Keep it under 300 characters when possible.
        - Match the tone the instructions imply.
        - Never invent commitments, times, or facts that aren't in the instructions.

        Incoming message:
        ${inbound.trim()}
    """.trimIndent()

    companion object {
        private const val TAG = "AiReplyGenerator"
        // Cheap, fast, and good enough for short SMS replies.
        private const val MODEL_NAME = "gemini-2.5-flash"
    }
}
