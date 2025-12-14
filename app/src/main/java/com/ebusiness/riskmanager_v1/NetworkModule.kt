package com.ebusiness.riskmanager_v1

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- 기존 Gemini API 연동 코드 (수정 없음) ---
interface GeminiApiService {
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val instance: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }
}

// --- [새로 추가] Ebiz-API 연동을 위한 코드 ---

private const val EBIZ_API_BASE_URL = "https://ebizapi.zeabur.app/"

// API 요청 본문 (Request Body)
data class InitialBusinessInput(
    @SerializedName("business_name")
    val businessName: String,
    @SerializedName("business_description")
    val businessDescription: String,
    @SerializedName("investment_amount")
    val investmentAmount: Int?
)

// API 응답 (Response Body)
data class InitialBusinessResponse(
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("selected_methods")
    val selectedMethods: List<String>
)

// API 인터페이스 정의
interface EbizApiService {
    @POST("api/v1/analyze/initial")
    suspend fun analyzeInitial(@Body request: InitialBusinessInput): InitialBusinessResponse
}

// Retrofit 인스턴스를 제공하는 객체 (HybRiskRepository에서 이 객체를 사용)
object NetworkModule {
    private val ebizApiOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(EBIZ_API_BASE_URL)
        .client(ebizApiOkHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: EbizApiService = retrofit.create(EbizApiService::class.java)
}
