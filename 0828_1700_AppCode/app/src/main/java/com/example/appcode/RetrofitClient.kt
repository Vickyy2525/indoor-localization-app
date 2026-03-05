package com.example.appcode

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// -----------------------------
// Data classes
// -----------------------------
data class QueryPoint(
    val point: List<Int>?,
    val userinput: String
)

data class AllocationResponse(
    val closest_label: String,
    val ai_response: String,
    val response_time: Double
)

data class PositionResponse(
    val position: List<Int>  // 例如 [x, y]
)
// -----------------------------
// API service
// -----------------------------
interface ApiService {
    @POST("ask_location")
    suspend fun allocation(@Body query: QueryPoint): AllocationResponse

    @Multipart
    @POST("upload_csv")
    suspend fun uploadCsv(@Part file: MultipartBody.Part): retrofit2.Response<PositionResponse>
}

// -----------------------------
// Retrofit client
// -----------------------------
object RetrofitClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://192.168.1.103:8000/")  // 統一使用一個 baseUrl
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
