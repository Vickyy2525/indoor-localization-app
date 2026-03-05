package com.example.appcode

import android.content.Context
import android.util.Log
import com.example.appcode.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

object CsvUploader {

    fun uploadCsvFile(csvFile: File, onComplete: (Boolean, String, List<Int>?) -> Unit) {
        val requestFile = csvFile.asRequestBody("text/csv".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", csvFile.name, requestFile)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val service = RetrofitClient.apiService
                val response = service.uploadCsv(body)

                if (response.isSuccessful) {
                    val position = response.body()?.position
                    if (position != null) {
                        onComplete(true, "定位成功", position)
                    } else {
                        onComplete(false, "伺服器回傳位置為 null", null)
                    }
                } else {
                    onComplete(false, "伺服器錯誤：${response.code()}", null)
                }

            } catch (e: Exception) {
                onComplete(false, "上傳失敗：${e.message}", null)
            }
        }
    }
}