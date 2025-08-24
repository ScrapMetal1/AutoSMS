package com.elias.autosms.utils

import android.content.Context
import android.content.SharedPreferences
import com.elias.autosms.data.ChatGptMessage
import com.elias.autosms.data.ChatGptRequest
import com.elias.autosms.data.ChatGptResponse
import com.elias.autosms.data.OpenAiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.random.Random

class ChatGptService(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("chatgpt_prefs", Context.MODE_PRIVATE)
    private val apiKey: String?
        get() = prefs.getString("openai_api_key", null)
    
    private val retrofit: Retrofit by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = Interceptor { chain ->
            val key = apiKey
            val original = chain.request()
            val request = original.newBuilder().apply {
                if (!key.isNullOrBlank()) {
                    header("Authorization", "Bearer $key")
                }
            }.build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    private val apiService: OpenAiApiService by lazy {
        retrofit.create(OpenAiApiService::class.java)
    }
    
    fun setApiKey(apiKey: String) {
        prefs.edit().putString("openai_api_key", apiKey).apply()
    }
    
    fun hasApiKey(): Boolean {
        return !apiKey.isNullOrBlank()
    }

    fun getPreferredModel(): String {
        return prefs.getString("openai_model", "gpt-4o-mini") ?: "gpt-4o-mini"
    }

    fun setPreferredModel(model: String) {
        prefs.edit().putString("openai_model", model).apply()
    }
    
    private fun parseDurationToMillis(raw: String): Long? {
        // Accept plain seconds (e.g., "2"), decimal seconds (e.g., "1.5"), or strings with units (e.g., "200ms", "2s", "1m")
        val trimmed = raw.trim().lowercase()
        return when {
            trimmed.endsWith("ms") -> trimmed.removeSuffix("ms").toDoubleOrNull()?.toLong()
            trimmed.endsWith("s") -> trimmed.removeSuffix("s").toDoubleOrNull()?.times(1000)?.toLong()
            trimmed.endsWith("m") -> trimmed.removeSuffix("m").toDoubleOrNull()?.times(60_000)?.toLong()
            else -> trimmed.toDoubleOrNull()?.times(1000)?.toLong()
        }
    }

    private fun computeRetryDelayMillis(headers: okhttp3.Headers, attempt: Int): Long {
        val retryAfter = headers["Retry-After"]?.let { parseDurationToMillis(it) }
        val rlRequests = headers["x-ratelimit-reset-requests"]?.let { parseDurationToMillis(it) }
        val rlTokens = headers["x-ratelimit-reset-tokens"]?.let { parseDurationToMillis(it) }

        val headerDelay = listOfNotNull(retryAfter, rlRequests, rlTokens).minOrNull()
        val baseBackoff = 1500L shl attempt // 1.5s, 3s, 6s, 12s
        val jitter = Random.nextLong(200, 700)
        return max(headerDelay ?: 0L, baseBackoff) + jitter
    }

    suspend fun generateRandomMessage(
        contactName: String,
        messageType: String = "friendly",
        maxLength: Int = 100
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = apiKey ?: return@withContext Result.failure(
                Exception("API key not set. Please configure your OpenAI API key in settings.")
            )
            
            val prompt = when (messageType.lowercase()) {
                "friendly" -> "Generate a friendly, casual text message to send to $contactName. Keep it under $maxLength characters. Make it sound natural and personal."
                "professional" -> "Generate a professional text message to send to $contactName. Keep it under $maxLength characters. Make it business-appropriate."
                "funny" -> "Generate a funny or humorous text message to send to $contactName. Keep it under $maxLength characters. Make it lighthearted and entertaining."
                "romantic" -> "Generate a romantic text message to send to $contactName. Keep it under $maxLength characters. Make it sweet and affectionate."
                else -> "Generate a random text message to send to $contactName. Keep it under $maxLength characters. Make it engaging and appropriate."
            }
            
            val request = ChatGptRequest(
                model = getPreferredModel(),
                messages = listOf(
                    ChatGptMessage(
                        role = "system",
                        content = "You are a helpful assistant that generates text messages. Always respond with just the message content, no quotes or additional text."
                    ),
                    ChatGptMessage(
                        role = "user",
                        content = prompt
                    )
                ),
                max_tokens = 150,
                temperature = 0.8
            )
            
            // Retry/backoff for 429 rate limits
            val maxRetries = 4
            var attempt = 0
            var result: Result<String>? = null
            while (true) {
                val response = apiService.generateChatCompletion(request = request)
                if (response.isSuccessful) {
                    val chatGptResponse = response.body()
                    val generatedMessage = chatGptResponse?.choices?.firstOrNull()?.message?.content?.trim()
                    result = if (!generatedMessage.isNullOrBlank()) {
                        Result.success(generatedMessage)
                    } else {
                        Result.failure(Exception("No message generated"))
                    }
                    break
                } else if (response.code() == 429 && attempt < maxRetries) {
                    val retryDelayMs = computeRetryDelayMillis(response.headers(), attempt)
                    attempt += 1
                    delay(retryDelayMs)
                    continue
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    val message = if (response.code() == 429) {
                        "Rate limited by API (429). Please try again in a moment."
                    } else {
                        "API Error: ${response.code()} - $errorBody"
                    }
                    result = Result.failure(Exception(message))
                    break
                }
            }
            result ?: Result.failure(Exception("Unknown error"))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun generateMessageWithContext(
        contactName: String,
        context: String,
        maxLength: Int = 100
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = apiKey ?: return@withContext Result.failure(
                Exception("API key not set. Please configure your OpenAI API key in settings.")
            )
            
            val prompt = "Generate a text message to send to $contactName based on this context: '$context'. Keep it under $maxLength characters. Make it relevant and appropriate."
            
            val request = ChatGptRequest(
                model = getPreferredModel(),
                messages = listOf(
                    ChatGptMessage(
                        role = "system",
                        content = "You are a helpful assistant that generates text messages. Always respond with just the message content, no quotes or additional text."
                    ),
                    ChatGptMessage(
                        role = "user",
                        content = prompt
                    )
                ),
                max_tokens = 150,
                temperature = 0.7
            )
            
            // Retry/backoff for 429 rate limits
            val maxRetries = 4
            var attempt = 0
            var result: Result<String>? = null
            while (true) {
                val response = apiService.generateChatCompletion(request = request)
                if (response.isSuccessful) {
                    val chatGptResponse = response.body()
                    val generatedMessage = chatGptResponse?.choices?.firstOrNull()?.message?.content?.trim()
                    result = if (!generatedMessage.isNullOrBlank()) {
                        Result.success(generatedMessage)
                    } else {
                        Result.failure(Exception("No message generated"))
                    }
                    break
                } else if (response.code() == 429 && attempt < maxRetries) {
                    val retryDelayMs = computeRetryDelayMillis(response.headers(), attempt)
                    attempt += 1
                    delay(retryDelayMs)
                    continue
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    val message = if (response.code() == 429) {
                        "Rate limited by API (429). Please try again in a moment."
                    } else {
                        "API Error: ${response.code()} - $errorBody"
                    }
                    result = Result.failure(Exception(message))
                    break
                }
            }
            result ?: Result.failure(Exception("Unknown error"))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
