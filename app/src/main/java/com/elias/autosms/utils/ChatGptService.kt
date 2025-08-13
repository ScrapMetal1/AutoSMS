package com.elias.autosms.utils

import android.content.Context
import android.content.SharedPreferences
import com.elias.autosms.data.ChatGptMessage
import com.elias.autosms.data.ChatGptRequest
import com.elias.autosms.data.ChatGptResponse
import com.elias.autosms.data.OpenAiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ChatGptService(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("chatgpt_prefs", Context.MODE_PRIVATE)
    private val apiKey: String?
        get() = prefs.getString("openai_api_key", null)
    
    private val retrofit: Retrofit by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
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
                model = "gpt-3.5-turbo",
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
            
            val response = apiService.generateChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )
            
            if (response.isSuccessful) {
                val chatGptResponse = response.body()
                val generatedMessage = chatGptResponse?.choices?.firstOrNull()?.message?.content?.trim()
                
                if (!generatedMessage.isNullOrBlank()) {
                    Result.success(generatedMessage)
                } else {
                    Result.failure(Exception("No message generated"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("API Error: ${response.code()} - $errorBody"))
            }
            
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
                model = "gpt-3.5-turbo",
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
            
            val response = apiService.generateChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )
            
            if (response.isSuccessful) {
                val chatGptResponse = response.body()
                val generatedMessage = chatGptResponse?.choices?.firstOrNull()?.message?.content?.trim()
                
                if (!generatedMessage.isNullOrBlank()) {
                    Result.success(generatedMessage)
                } else {
                    Result.failure(Exception("No message generated"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("API Error: ${response.code()} - $errorBody"))
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
