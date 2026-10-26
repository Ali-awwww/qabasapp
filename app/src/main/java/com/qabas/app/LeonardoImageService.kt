package com.qabas.app

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import androidx.compose.ui.graphics.Color

/**
 * 🎨 Leonardo.ai Image Generation Service
 * https://docs.leonardo.ai/
 */
interface LeonardoApi {
    @POST("generations")
    suspend fun createGeneration(
        @Header("Authorization") auth: String,
        @Body request: LeonardoGenerationRequest
    ): Response<LeonardoGenerationResponse>

    @GET("generations/{id}")
    suspend fun getGeneration(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<LeonardoGenerationDetailResponse>

    @GET("me")
    suspend fun getMe(
        @Header("Authorization") auth: String
    ): Response<okhttp3.ResponseBody>
}

data class LeonardoGenerationRequest(
    val prompt: String,
    val modelId: String = "6bef9f1b-29cb-40c7-b9df-32b51c1f67d3", // Leonardo Vision XL
    val width: Int = 1024,
    val height: Int = 1024,
    val num_images: Int = 1,
    val alchemy: Boolean = true
)

data class LeonardoGenerationResponse(
    val sdGenerationJob: LeonardoJobId?
)

data class LeonardoJobId(
    val generationId: String
)

data class LeonardoGenerationDetailResponse(
    val generations_by_pk: LeonardoGenerationDetail?
)

data class LeonardoGenerationDetail(
    val generated_images: List<LeonardoImage>?,
    val status: String?
)

data class LeonardoImage(
    val url: String
)

object LeonardoImageService {
    private const val TAG = "LeonardoService"
    private const val BASE_URL = "https://cloud.leonardo.ai/api/rest/v1/"

    private fun getApi(): LeonardoApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(LeonardoApi::class.java)
    }

    suspend fun generateImage(
        context: Context,
        prompt: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): String? {
        val apiKey = ApiKeyManager.getLeonardoKey(context)
        if (apiKey.isBlank()) {
            Log.e(TAG, "Leonardo API Key is missing!")
            onProgress(0f, "مفتاح Leonardo.ai غير متوفر")
            return null
        }

        val api = getApi()
        val authHeader = "Bearer $apiKey"

        try {
            onProgress(0.15f, "إرسال طلب التوليد إلى خوادم Leonardo.ai...")
            SystemLogsManager.addLog("LEONARDO", "إرسال طلب صورة Vision XL: ${prompt.take(45)}...", Color(0xFF38BDF8))

            val response = api.createGeneration(
                authHeader,
                LeonardoGenerationRequest(prompt = prompt)
            )

            if (response.isSuccessful) {
                val generationId = response.body()?.sdGenerationJob?.generationId
                if (generationId == null) {
                    onProgress(0f, "لم يستجب الخادم بمعرف للمهمة")
                    return null
                }
                
                // Polling for the result (Max 12 attempts ~ 36s)
                for (i in 1..12) {
                    val p = 0.20f + (i * 0.06f).coerceAtMost(0.70f)
                    onProgress(p, "خوادم Leonardo تقوم برسم المشهد بدقة فائقة ($i/12)...")
                    kotlinx.coroutines.delay(3000)
                    val detailResponse = api.getGeneration(authHeader, generationId)
                    if (detailResponse.isSuccessful) {
                        val detail = detailResponse.body()?.generations_by_pk
                        if (detail?.status == "COMPLETE") {
                            val imgUrl = detail.generated_images?.firstOrNull()?.url
                            if (!imgUrl.isNullOrBlank()) {
                                onProgress(1.0f, "اكتمل التوليد بنجاح عبر Leonardo.ai! 🎨✨")
                                SystemLogsManager.addLog("LEONARDO", "تم توليد الصورة بنجاح وتنزيل الرابط", Color(0xFF10B981))
                                return imgUrl
                            }
                        } else if (detail?.status == "FAILED") {
                            onProgress(0f, "فشلت مهمة التوليد على خوادم Leonardo")
                            SystemLogsManager.addLog("LEONARDO", "فشل التوليد: حالة المهمة FAILED", Color(0xFFEF4444))
                            return null
                        }
                    }
                }
                onProgress(0f, "انتهت مهلة انتظار خوادم Leonardo")
            } else {
                val err = response.errorBody()?.string() ?: ""
                Log.e(TAG, "Failed to create generation: $err")
                onProgress(0f, "خطأ في الاتصال بـ Leonardo (كود ${response.code()})")
                SystemLogsManager.addLog("LEONARDO", "استجابة غير ناجحة: ${response.code()}", Color(0xFFEF4444))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating image", e)
            onProgress(0f, "خطأ أثناء التوليد: ${e.message}")
            SystemLogsManager.addLog("LEONARDO", "استثناء: ${e.message}", Color(0xFFEF4444))
        }
        return null
    }

    /**
     * فحص الاتصال واختبار صلاحية مفتاح Leonardo.ai
     */
    suspend fun testConnection(context: Context, explicitKey: String? = null): Pair<Boolean, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val apiKey = (explicitKey ?: ApiKeyManager.getLeonardoKey(context)).trim()
        if (apiKey.isBlank()) {
            return@withContext Pair(false, "مفتاح Leonardo.ai غير مدخل")
        }

        val api = getApi()
        val authHeader = if (apiKey.startsWith("Bearer ", ignoreCase = true)) apiKey else "Bearer $apiKey"

        try {
            val response = api.getMe(authHeader)
            if (response.isSuccessful) {
                Pair(true, "تم الاتصال بنجاح بخوادم Leonardo.ai (200 OK)")
            } else {
                val code = response.code()
                if (code == 401 || code == 403) {
                    Pair(false, "المفتاح غير صالح أو مرفوض من Leonardo (كود $code)")
                } else {
                    Pair(false, "استجاب خادم Leonardo برمز خطأ ($code)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Leonardo testConnection error", e)
            Pair(false, "تعذر الاتصال بخوادم Leonardo: ${e.localizedMessage ?: e.message}")
        }
    }
}
