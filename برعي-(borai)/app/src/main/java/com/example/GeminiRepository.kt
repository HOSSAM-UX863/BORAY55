package com.example

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- File Resources and Models ---

@JsonClass(generateAdapter = true)
data class GeminiFile(
    @Json(name = "name") val name: String,
    @Json(name = "displayName") val displayName: String? = null,
    @Json(name = "mimeType") val mimeType: String? = null,
    @Json(name = "sizeBytes") val sizeBytes: String? = null,
    @Json(name = "createTime") val createTime: String? = null,
    @Json(name = "updateTime") val updateTime: String? = null,
    @Json(name = "expirationTime") val expirationTime: String? = null,
    @Json(name = "sha256Hash") val sha256Hash: String? = null,
    @Json(name = "uri") val uri: String? = null,
    @Json(name = "state") val state: String? = null // PROCESSING, ACTIVE, FAILED
)

@JsonClass(generateAdapter = true)
data class GeminiUploadResponse(
    @Json(name = "file") val file: GeminiFile
)

// --- Retrofit Service specifically for Files API ---

interface GeminiFileApiService {
    @POST("upload/v1beta/files")
    suspend fun uploadRawFile(
        @Body body: RequestBody,
        @Query("key") apiKey: String
    ): GeminiUploadResponse

    @GET("v1beta/{fileName}")
    suspend fun getFile(
        @Path("fileName", encoded = true) fileName: String,
        @Query("key") apiKey: String
    ): GeminiFile
}

// --- Singleton Client API for File Service ---

object GeminiFileRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiFileApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiFileApiService::class.java)
    }
}

// --- GeminiRepository Implementing File Manager and Suspend Polling Wrapper ---

class GeminiRepository {

    // Internal direct fileManager matching requested API design
    object fileManager {
        suspend fun uploadFile(
            fileBytes: ByteArray,
            mimeType: String,
            displayName: String,
            apiKey: String
        ): GeminiUploadResponse {
            val metadataJson = """{"file": {"displayName": "$displayName"}}"""
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(
                    Headers.Builder()
                        .add("Content-Disposition", "form-data; name=\"metadata\"")
                        .build(),
                    metadataJson.toRequestBody("application/json".toMediaTypeOrNull())
                )
                .addPart(
                    Headers.Builder()
                        .add("Content-Disposition", "form-data; name=\"file\"; filename=\"$displayName\"")
                        .build(),
                    fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                )
                .build()

            return GeminiFileRetrofitClient.service.uploadRawFile(requestBody, apiKey)
        }

        suspend fun getFile(fileName: String, apiKey: String): GeminiFile {
            return GeminiFileRetrofitClient.service.getFile(fileName, apiKey)
        }
    }

    /**
     * uploadAndVerifyFile: Performs robust file upload and polling verification.
     * 1. Uploads file using fileManager.uploadFile.
     * 2. Polls file status using fileManager.getFile and delay(1000).
     * 3. Proceeds when ACTIVE, throws exception when FAILED.
     */
    suspend fun uploadAndVerifyFile(
        fileBytes: ByteArray,
        mimeType: String,
        displayName: String,
        onStatusUpdate: (String) -> Unit
    ): String {
        val apiKey = getApiKeyGracefully()
        
        if (apiKey.isBlank()) {
            // Secure fallback when API key is not present
            onStatusUpdate("جاري رفع الملف تجريبياً... (محاكاة)")
            delay(1500)
            
            var progress = 0
            var simulatedFileState = "PROCESSING"
            
            onStatusUpdate("جاري فرز وقراءة المستند بالعدسة الذكية...")
            
            while (simulatedFileState != "ACTIVE") {
                delay(1000)
                progress += 1
                if (progress >= 2) {
                    simulatedFileState = "ACTIVE"
                }
            }
            
            onStatusUpdate("تم تفريز ومعالجة الملف بنجاح! الحالة: ACTIVE")
            delay(800)
            return "simulated_${System.currentTimeMillis()}"
        }

        // REAL METHOD EXECUTION VIA FILES API
        onStatusUpdate("جاري رفع المستند إلى خوادم Google AI...")
        val uploadResponse = fileManager.uploadFile(fileBytes, mimeType, displayName, apiKey)
        val fileName = uploadResponse.file.name // form: "files/abc-123"
        val fileUri = uploadResponse.file.uri ?: "https://generativelanguage.googleapis.com/v1beta/$fileName"
        
        onStatusUpdate("تم الرفع بنجاح! جاري معالجة المستند ضوئياً بالخادم...")
        
        var fileState = uploadResponse.file.state ?: "PROCESSING"
        var attempts = 0
        
        // Loop polling with 1000ms delay to verify state is ACTIVE
        while (fileState != "ACTIVE") {
            if (fileState == "FAILED") {
                throw Exception("فشلت معالجة الملف في خوادم Google AI.")
            }
            if (attempts > 30) {
                throw Exception("انتهت المهلة المحددة للتحقق من جاهزية الملف.")
            }
            delay(1000)
            attempts++
            onStatusUpdate("جاري فرز ومعالجة المستند بالخادم (المحاولة $attempts)...")
            
            val fileInfo = fileManager.getFile(fileName, apiKey)
            fileState = fileInfo.state ?: "PROCESSING"
        }
        
        onStatusUpdate("الملف جاهز وفعال الآن لمعالجته بالذكاء الاصطناعي!")
        return fileUri
    }

    // Dynamic key retrieval helper matching original app integration
    private fun getApiKeyGracefully(): String {
        val key = BuildConfig.GEMINI_API_KEY
        return if (key == "MY_GEMINI_API_KEY" || key.isBlank()) {
            ""
        } else {
            key
        }
    }
}
