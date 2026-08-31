package mo.dev.ctrus.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

sealed interface RecoveryCodeVerification {
    data class Valid(val recentUnlockCount: Int?) : RecoveryCodeVerification
    data object Invalid : RecoveryCodeVerification
    data object NetworkError : RecoveryCodeVerification
}

/**
 * Port of Ctrus/Utils/RecoveryCodeUtil.swift, calling the same production CtrusRecoveryWorker
 * backend (https://recover.ctrus.net) the iOS app already uses — a plain JSON/HTTPS API with no
 * platform-specific payload, so it needs no server-side changes for Android.
 */
class RecoveryCodeClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://recover.ctrus.net",
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

    @Serializable private data class VerifyRequest(val deviceId: String, val code: String)
    @Serializable private data class VerifyResponse(val valid: Boolean, val recentUnlockCount: Int? = null)

    suspend fun verifyCode(deviceId: String, code: String): RecoveryCodeVerification = withContext(Dispatchers.IO) {
        val body = json.encodeToString(VerifyRequest.serializer(), VerifyRequest(deviceId, code)).toRequestBody(jsonMediaType)
        val request = Request.Builder().url("$baseUrl/verify-code").post(body).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext RecoveryCodeVerification.NetworkError
                val payload = response.body.string()
                val parsed = json.decodeFromString(VerifyResponse.serializer(), payload)
                if (parsed.valid) RecoveryCodeVerification.Valid(parsed.recentUnlockCount) else RecoveryCodeVerification.Invalid
            }
        } catch (e: IOException) {
            RecoveryCodeVerification.NetworkError
        }
    }
}
