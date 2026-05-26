/**
 * Kotlin HTTP客户端
 * 支持ECC+AES-GCM加密和防重复提交
 *
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

package com.richie.httpclient

import android.util.Base64
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// ==================== Kotlin原生enum ====================

/**
 * HTTP方法枚举（Kotlin原生enum class）
 */
enum class Method(val value: String) {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE"),
    PATCH("PATCH");
    
    override fun toString() = value
}

// ==================== URL接口定义（业务代码需要实现）====================

/**
 * URL接口 - 业务代码的Url枚举需要实现此接口
 * 
 * 业务代码应定义自己的Url枚举，并实现以下属性：
 * - path: URL路径
 * - method: HTTP方法
 * - needEncryption: 是否需要加密
 * - needDuplicateCheck: 是否需要防重复提交检查
 * 
 * 使用示例（业务代码）：
 * ```kotlin
 * enum class Url : UrlInterface {
 *     UserLogin {
 *         override val path = "/api/auth/login"
 *         override val method = Method.POST
 *         override val needEncryption = true
 *         override val needDuplicateCheck = false
 *     };
 * }
 * ```
 */
interface UrlInterface {
    /**
     * URL路径
     */
    val path: String
    
    /**
     * HTTP方法
     */
    val method: Method
    
    /**
     * 是否需要加密
     */
    val needEncryption: Boolean
    
    /**
     * 是否需要防重复提交检查
     */
    val needDuplicateCheck: Boolean
    
    /**
     * 获取完整URL（默认实现）
     */
    fun getFullUrl(baseUrl: String): String {
        val trimmedBase = baseUrl.trimEnd('/')
        val trimmedPath = if (path.startsWith("/")) path else "/$path"
        return trimmedBase + trimmedPath
    }
}

// ==================== HTTP客户端配置 ====================

data class HttpClientConfig(
    val baseUrl: String = "https://your-gateway.com",
    val clientId: String = "android-client-${System.currentTimeMillis()}",
    val duplicateSubmitTimeWindow: Long = 3000,
    val timeout: Long = 30000,
    /** 是否启用请求头自动管理（从SharedPreferences读取和保存），默认true */
    val enableHeaderAutoManagement: Boolean = true,
    /** SharedPreferences中存储请求头的键名，默认"http_headers" */
    val headerStorageKey: String = "http_headers"
)

// ==================== HTTP客户端 ====================

class HttpClient(
    private val context: Context,
    private val config: HttpClientConfig = HttpClientConfig()
) {
    private val eccModule = EccCryptoModule()
    private val duplicateModule = DuplicateSubmitModule(config.duplicateSubmitTimeWindow)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(config.timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(config.timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()
    
    // 系统内置的请求头，这些不应该被保存
    private companion object {
        val SYSTEM_HEADERS = setOf(
            "content-type", "content-length", "cache-control", "expires",
            "date", "server", "connection", "transfer-encoding",
            "accept", "accept-encoding", "accept-language", "referer",
            "user-agent", "origin",
            "x-client-id", "x-client-timestamp", "x-client-public-key",
            "x-gateway-keyid", "x-encrypted-data", "x-response-encrypted",
            "access-control-allow-origin", "access-control-allow-methods",
            "access-control-allow-headers", "access-control-expose-headers",
            "access-control-max-age", "access-control-allow-credentials"
        )
    }

    /**
     * 发送HTTP请求
     * 
     * @param T 响应数据的类型
     * @param url 实现UrlInterface的枚举值（业务代码定义）
     * @param body 请求体（可选）
     * @param headers 额外请求头（可选）
     * @return 完整的ApiResult<T>对象
     */
    suspend inline fun <reified T> request(
        url: UrlInterface,
        body: Any? = null,
        headers: Map<String, String>? = null
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val fullUrl = url.getFullUrl(config.baseUrl)

        // 1. 防重复提交检查
        val requestId = if (url.needDuplicateCheck) {
            val bodyStr = body?.let { JSONObject(it as Map<*, *>).toString() } ?: ""
            val id = duplicateModule.generateRequestId(fullUrl, url.method.value, bodyStr)

            if (duplicateModule.isDuplicateRequest(id)) {
                throw DuplicateRequestException("DUPLICATE_REQUEST")
            }

            duplicateModule.recordRequest(id, fullUrl)
            id
        } else null

        try {
            // 2. 发送请求
            val response = if (url.needEncryption) {
                sendEncryptedRequest(fullUrl, url.method, body, headers)
            } else {
                sendPlainRequest(fullUrl, url.method, body, headers)
            }

            // 3. 自动保存响应头到SharedPreferences（在解析响应体之前，确保响应头可用）
            if (config.enableHeaderAutoManagement) {
                saveResponseHeaders(response)
            }

            // 4. 处理响应（完整透传ApiResult<T>，不做任何处理）
            handleResponse<T>(response, url.needEncryption)
        } finally {
            // 5. 清理
            requestId?.let { duplicateModule.clearRequest(it) }
        }
    }

    private suspend fun sendEncryptedRequest(
        url: String,
        method: Method,
        body: Any?,
        headers: Map<String, String>?
    ): Response {
        // 确保已初始化
        if (!eccModule.isInitialized()) {
            eccModule.exchangeKeys(config.baseUrl, config.clientId)
        }

        // 读取缓存的请求头（包括token等业务请求头）
        val cachedHeaders = if (config.enableHeaderAutoManagement) loadCachedHeaders() else emptyMap()

        // 构建设备相关请求头（deviceId + 硬件指纹），用于网关侧的设备绑定和安全校验
        val deviceHeaders = buildDeviceHeaders()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("X-Client-Id", config.clientId)
            .header("X-Gateway-KeyId", eccModule.gatewayKeyId ?: "")
            .header("X-Client-Timestamp", System.currentTimeMillis().toString())
            .apply {
                deviceHeaders.forEach { (k, v) -> header(k, v) }
            }

        // 添加缓存的请求头（包括token）
        cachedHeaders.forEach { (k, v) ->
            requestBuilder.header(k, v)
        }

        headers?.forEach { (k, v) ->
            requestBuilder.header(k, v)
        }

        // 加密请求体
        if (body != null && method != Method.GET) {
            val jsonBody = JSONObject(body as Map<*, *>).toString()
            val encryptedData = eccModule.encrypt(jsonBody)
            requestBuilder.header("X-Encrypted-Data", encryptedData)
            requestBuilder.method(
                method.value,
                jsonBody.toRequestBody("application/json".toMediaType())
            )
        } else {
            requestBuilder.method(method.value, null)
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()

        // 处理423状态码
        if (response.code == 423) {
            val json = JSONObject(response.body?.string() ?: "{}")
            if (json.optBoolean("needReHandshake", false)) {
                val keyId = json.getString("keyId")
                val gatewayPublicKey = json.getString("gatewayPublicKey")
                eccModule.reHandshake(keyId, gatewayPublicKey)
                return sendEncryptedRequest(url, method, body, headers)
            }
        }

        return response
    }

    private fun sendPlainRequest(
        url: String,
        method: Method,
        body: Any?,
        headers: Map<String, String>?
    ): Response {
        // 读取缓存的请求头（包括token等业务请求头）
        val cachedHeaders = if (config.enableHeaderAutoManagement) loadCachedHeaders() else emptyMap()

        // 构建设备相关请求头（deviceId + 硬件指纹），用于网关侧的设备绑定和安全校验
        val deviceHeaders = buildDeviceHeaders()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("X-Client-Timestamp", System.currentTimeMillis().toString())
            .apply {
                deviceHeaders.forEach { (k, v) -> header(k, v) }
            }

        // 添加缓存的请求头（包括token）
        cachedHeaders.forEach { (k, v) ->
            requestBuilder.header(k, v)
        }

        headers?.forEach { (k, v) ->
            requestBuilder.header(k, v)
        }

        if (body != null && method != Method.GET) {
            val jsonBody = JSONObject(body as Map<*, *>).toString()
            requestBuilder.method(
                method.value,
                jsonBody.toRequestBody("application/json".toMediaType())
            )
        } else {
            requestBuilder.method(method.value, null)
        }

        return httpClient.newCall(requestBuilder.build()).execute()
    }

    private inline fun <reified T> handleResponse(response: Response, needDecryption: Boolean): ApiResult<T> {
        // 先读取响应体（只能读取一次）
        val bodyString = response.body?.string() ?: "{}"
        var responseBodyString = bodyString

        // 解密响应
        if (needDecryption && response.header("X-Response-Encrypted") == "true") {
            responseBodyString = eccModule.decrypt(bodyString)
        }

        if (response.code != 200) {
            if (response.code == 429) {
                val json = JSONObject(responseBodyString)
                if (json.optString("code") == "DUPLICATE_SUBMIT") {
                    throw DuplicateSubmitException("DUPLICATE_SUBMIT")
                }
            }
            throw HttpException("HTTP ${response.code}: ${response.message}")
        }

        // 完整透传ApiResult<T>，不做任何处理
        val json = JSONObject(responseBodyString)
        return ApiResult(
            data = json.opt("data") as? T,
            code = json.optString("code", "200"),
            msg = json.optString("msg", null),
            i18nDict = null, // 简化处理，实际可以从json中解析
            timestamp = json.optLong("timestamp", System.currentTimeMillis())
        )
    }
    
    /**
     * 从SharedPreferences读取缓存的请求头
     */
    private fun loadCachedHeaders(): Map<String, String> {
        if (!config.enableHeaderAutoManagement) return emptyMap()

        return try {
            val prefs = context.getSharedPreferences(config.headerStorageKey, Context.MODE_PRIVATE)
            val json = prefs.getString("headers", null) ?: return emptyMap()
            val jsonObj = JSONObject(json)
            val result = mutableMapOf<String, String>()
            jsonObj.keys().forEach { key ->
                result[key] = jsonObj.getString(key)
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * 保存响应头到SharedPreferences
     * 只保存业务相关的请求头，排除系统内置的header
     * 网关签发的token（x-rd-request-apitoken）等业务请求头会自动保存
     */
    private fun saveResponseHeaders(response: Response) {
        val businessHeaders = mutableMapOf<String, String>()
        
        // 遍历所有响应头
        response.headers.forEach { (key, value) ->
            val lowerKey = key.lowercase()
            
            // 排除系统内置的header，其他所有业务请求头都保存（包括x-rd-request-apitoken等）
            if (!SYSTEM_HEADERS.contains(lowerKey)) {
                businessHeaders[key] = value
            }
        }
        
        // 如果有业务请求头，保存到SharedPreferences
        if (businessHeaders.isNotEmpty()) {
            try {
                val prefs = context.getSharedPreferences(config.headerStorageKey, Context.MODE_PRIVATE)
                val existing = loadCachedHeaders()
                val merged = existing.toMutableMap().apply { putAll(businessHeaders) }
                val jsonObj = JSONObject()
                merged.forEach { (k, v) -> jsonObj.put(k, v) }
                prefs.edit().putString("headers", jsonObj.toString()).apply()
                println("[HttpClient] 已保存响应头到SharedPreferences: $businessHeaders")
            } catch (e: Exception) {
                println("[HttpClient] 保存响应头失败: ${e.message}")
            }
        }
    }

    fun cleanup() {
        eccModule.cleanup()
        duplicateModule.clearAll()
    }

    // ==================== 设备指纹集成 ====================

    /**
     * 构建设备相关请求头
     * 自动生成并附加以下请求头：
     * - X-Device-Id：设备ID（基于Android ID和硬件特征生成并持久化）
     * - X-Hardware-Fingerprint：硬件指纹（每次请求动态生成）
     */
    private fun buildDeviceHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()

        // 设备ID（兼容旧版本）
        val deviceId = DeviceFingerprint.getOrCreateDeviceId(context)
        if (deviceId.isNotEmpty()) {
            headers["X-Device-Id"] = deviceId
        }

        // 硬件指纹（动态生成）
        val fingerprint = DeviceFingerprint.generateHardwareFingerprint(context)
        val fingerprintJson = DeviceFingerprint.fingerprintToString(fingerprint)
        if (fingerprintJson.isNotEmpty()) {
            headers["X-Hardware-Fingerprint"] = fingerprintJson
        }

        return headers
    }
}

// ==================== ECC加密模块 ====================

class EccCryptoModule {
    private var keyPair: KeyPair? = null
    private var sharedSecret: ByteArray? = null
    var gatewayKeyId: String? = null
        private set

    fun isInitialized(): Boolean = sharedSecret != null && gatewayKeyId != null

    fun exchangeKeys(baseUrl: String, clientId: String) {
        // 生成客户端ECC密钥对
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(256)
        keyPair = keyPairGenerator.generateKeyPair()

        val publicKeyBytes = keyPair!!.public.encoded
        val clientPublicKey = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)

        // 发送密钥交换请求
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$baseUrl/api/crypto/exchange")
            .header("Content-Type", "application/json")
            .header("X-Client-Public-Key", clientPublicKey)
            .header("X-Client-Id", clientId)
            .post("".toRequestBody())
            .build()

        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")

        gatewayKeyId = json.getString("keyId")
        val gatewayPublicKeyB64 = json.getString("gatewayPublicKey")

        // 计算共享密钥
        val gatewayPublicKeyBytes = Base64.decode(gatewayPublicKeyB64, Base64.NO_WRAP)
        val gatewayPublicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(gatewayPublicKeyBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(keyPair!!.private)
        keyAgreement.doPhase(gatewayPublicKey, true)
        sharedSecret = keyAgreement.generateSecret()

        println("[ECC] 密钥交换成功, KeyId: $gatewayKeyId")
    }

    fun reHandshake(keyId: String, gatewayPublicKey: String) {
        gatewayKeyId = keyId
        // 重新计算共享密钥...
    }

    fun encrypt(plaintext: String): String {
        val secret = sharedSecret ?: throw IllegalStateException("共享密钥未初始化")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val secretKey = SecretKeySpec(secret.copyOf(32), "AES")

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray())

        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(ciphertext: String): String {
        val secret = sharedSecret ?: throw IllegalStateException("共享密钥未初始化")

        val combined = Base64.decode(ciphertext, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(secret.copyOf(32), "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

        return String(cipher.doFinal(encrypted))
    }

    fun cleanup() {
        keyPair = null
        sharedSecret = null
        gatewayKeyId = null
    }
}

// ==================== 防重复提交模块 ====================

class DuplicateSubmitModule(private val timeWindow: Long) {
    private val requestQueue = ConcurrentHashMap<String, RequestInfo>()

    data class RequestInfo(val timestamp: Long, val url: String)

    fun generateRequestId(url: String, method: String, body: String): String {
        val timestamp = System.currentTimeMillis() / timeWindow * timeWindow
        val data = "$url$method$timestamp$body"
        return data.md5Hash()
    }

    fun isDuplicateRequest(requestId: String): Boolean {
        val info = requestQueue[requestId] ?: return false
        val now = System.currentTimeMillis()
        return now - info.timestamp < timeWindow
    }

    fun recordRequest(requestId: String, url: String) {
        requestQueue[requestId] = RequestInfo(System.currentTimeMillis(), url)
        cleanupExpired()
    }

    fun clearRequest(requestId: String) {
        requestQueue.remove(requestId)
    }

    fun clearAll() {
        requestQueue.clear()
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val expiredTime = now - timeWindow
        requestQueue.entries.removeIf { it.value.timestamp < expiredTime }
    }
}

// ==================== 工具扩展 ====================

fun String.md5Hash(): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(this.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

// ==================== 异常定义 ====================

class DuplicateRequestException(message: String) : Exception(message)
class DuplicateSubmitException(message: String) : Exception(message)
class HttpException(message: String) : Exception(message)

