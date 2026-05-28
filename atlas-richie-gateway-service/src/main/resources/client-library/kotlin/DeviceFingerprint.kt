/**
 * Android 设备指纹生成工具（高安全版）
 * 提供高安全性的设备识别方案，适用于 Android 原生应用
 *
 * @author richie696
 * @version 2.0
 * @since 1.0.0
 */

package com.richie.httpclient

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.*

/**
 * 硬件指纹特征数据类
 */
data class HardwareFingerprint(
    /** Android ID（设备唯一标识符） */
    val androidId: String,
    /** 设备品牌（如：Xiaomi、Samsung） */
    val brand: String,
    /** 设备型号（如：Mi 14、SM-G991B） */
    val model: String,
    /** 设备制造商（如：Xiaomi、Samsung） */
    val manufacturer: String,
    /** 系统版本（如：13、14） */
    val sdkVersion: Int,
    /** 系统版本字符串（如：Android 13） */
    val release: String,
    /** 屏幕宽度（px） */
    val screenWidth: Int,
    /** 屏幕高度（px） */
    val screenHeight: Int,
    /** 屏幕密度DPI */
    val densityDpi: Int,
    /** 屏幕密度（缩放因子） */
    val density: Float,
    /** CPU架构列表 */
    val cpuAbis: List<String>,
    /** 总内存（字节） */
    val totalMemory: Long,
    /** CPU核心数 */
    val cpuCount: Int,
    /** 硬件信息（序列号等，如果可用） */
    val hardware: String
)

/**
 * 设备指纹工具类
 */
object DeviceFingerprint {
    
    private const val PREFS_NAME = "richie_device_fingerprint"
    private const val KEY_FINGERPRINT = "device_fingerprint"
    private const val KEY_DEVICE_ID = "device_id" // 兼容旧版本
    
    /**
     * 获取或创建设备指纹
     *
     * @param context Android Context
     * @return 设备指纹对象
     */
    fun getOrCreateDeviceFingerprint(context: Context): HardwareFingerprint {
        // 1. 尝试从加密存储读取（最安全）
        val storedFingerprint = loadFingerprintFromEncryptedStorage(context)
        if (storedFingerprint != null) {
            return storedFingerprint
        }
        
        // 2. 尝试从普通存储读取（备用）
        val storedFingerprintFromPrefs = loadFingerprintFromPreferences(context)
        if (storedFingerprintFromPrefs != null) {
            // 迁移到加密存储
            saveFingerprintToEncryptedStorage(context, storedFingerprintFromPrefs)
            return storedFingerprintFromPrefs
        }
        
        // 3. 生成新的设备指纹
        val fingerprint = generateHardwareFingerprint(context)
        
        // 4. 保存到加密存储（优先）和普通存储（备用）
        saveFingerprintToEncryptedStorage(context, fingerprint)
        saveFingerprintToPreferences(context, fingerprint)
        
        return fingerprint
    }
    
    /**
     * 生成动态硬件指纹（每次请求时调用）
     *
     * @param context Android Context
     * @return 硬件指纹对象
     */
    fun generateHardwareFingerprint(context: Context): HardwareFingerprint {
        // 获取 Android ID
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()
        
        // 获取设备信息
        val brand = Build.BRAND ?: "unknown"
        val model = Build.MODEL ?: "unknown"
        val manufacturer = Build.MANUFACTURER ?: "unknown"
        val sdkVersion = Build.VERSION.SDK_INT
        val release = Build.VERSION.RELEASE ?: "unknown"
        
        // 获取屏幕信息
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val densityDpi = displayMetrics.densityDpi
        val density = displayMetrics.density
        
        // 获取CPU信息
        val cpuAbis = Build.SUPPORTED_ABIS.toList()
        val cpuCount = Runtime.getRuntime().availableProcessors()
        
        // 获取内存信息
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalMemory = memoryInfo.totalMem
        
        // 获取硬件信息
        val hardware = Build.HARDWARE ?: "unknown"
        
        return HardwareFingerprint(
            androidId = androidId,
            brand = brand,
            model = model,
            manufacturer = manufacturer,
            sdkVersion = sdkVersion,
            release = release,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            densityDpi = densityDpi,
            density = density,
            cpuAbis = cpuAbis,
            totalMemory = totalMemory,
            cpuCount = cpuCount,
            hardware = hardware
        )
    }
    
    /**
     * 将硬件指纹转换为字符串（用于传输）
     *
     * @param fingerprint 硬件指纹对象
     * @return JSON字符串
     */
    fun fingerprintToString(fingerprint: HardwareFingerprint): String {
        val jsonObj = JSONObject().apply {
            put("androidId", fingerprint.androidId)
            put("brand", fingerprint.brand)
            put("model", fingerprint.model)
            put("manufacturer", fingerprint.manufacturer)
            put("sdkVersion", fingerprint.sdkVersion)
            put("release", fingerprint.release)
            put("screenWidth", fingerprint.screenWidth)
            put("screenHeight", fingerprint.screenHeight)
            put("densityDpi", fingerprint.densityDpi)
            put("density", fingerprint.density.toDouble())
            put("totalMemory", fingerprint.totalMemory)
            put("cpuCount", fingerprint.cpuCount)
            put("hardware", fingerprint.hardware)
            
            val cpuAbisArray = JSONArray()
            fingerprint.cpuAbis.forEach { cpuAbisArray.put(it) }
            put("cpuAbis", cpuAbisArray)
        }
        return jsonObj.toString()
    }
    
    /**
     * 生成硬件指纹的哈希值（用于快速比较）
     *
     * @param fingerprint 硬件指纹对象
     * @return SHA-256哈希值（64字符）
     */
    fun fingerprintToHash(fingerprint: HardwareFingerprint): String {
        val fingerprintStr = fingerprintToString(fingerprint)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(fingerprintStr.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 计算两个硬件指纹的相似度
     *
     * @param fingerprint1 第一个硬件指纹
     * @param fingerprint2 第二个硬件指纹
     * @return 相似度（0-1之间，1表示完全匹配）
     */
    fun calculateFingerprintSimilarity(
        fingerprint1: HardwareFingerprint,
        fingerprint2: HardwareFingerprint
    ): Double {
        var score = 0.0
        var totalWeight = 0.0
        
        // Android ID（权重最高，必须匹配）
        val androidIdWeight = 0.4
        totalWeight += androidIdWeight
        if (fingerprint1.androidId == fingerprint2.androidId) {
            score += androidIdWeight
        }
        
        // 设备品牌和型号（权重高）
        val brandModelWeight = 0.2
        totalWeight += brandModelWeight
        if (fingerprint1.brand == fingerprint2.brand && fingerprint1.model == fingerprint2.model) {
            score += brandModelWeight
        }
        
        // 屏幕分辨率（权重中等）
        val screenWeight = 0.15
        totalWeight += screenWeight
        if (fingerprint1.screenWidth == fingerprint2.screenWidth &&
            fingerprint1.screenHeight == fingerprint2.screenHeight) {
            score += screenWeight
        }
        
        // 屏幕密度（权重中等）
        val densityWeight = 0.1
        totalWeight += densityWeight
        if (kotlin.math.abs(fingerprint1.density - fingerprint2.density) < 0.1) {
            score += densityWeight
        }
        
        // 系统版本（权重较低，允许更新）
        val systemWeight = 0.1
        totalWeight += systemWeight
        if (fingerprint1.sdkVersion == fingerprint2.sdkVersion) {
            score += systemWeight
        } else {
            // 主版本号匹配（如 13 和 14）
            val diff = kotlin.math.abs(fingerprint1.sdkVersion - fingerprint2.sdkVersion)
            if (diff <= 1) {
                score += systemWeight * 0.5
            }
        }
        
        // CPU架构（权重较低）
        val cpuWeight = 0.05
        totalWeight += cpuWeight
        val commonAbis = fingerprint1.cpuAbis.intersect(fingerprint2.cpuAbis.toSet())
        val cpuSimilarity = if (fingerprint1.cpuAbis.isNotEmpty()) {
            commonAbis.size.toDouble() / fingerprint1.cpuAbis.size
        } else {
            1.0
        }
        score += cpuWeight * cpuSimilarity
        
        return if (totalWeight > 0) score / totalWeight else 0.0
    }
    
    /**
     * 获取设备名称（用于显示）
     *
     * @param context Android Context
     * @return 设备名称（例如："Xiaomi Mi 14 on Android 13"）
     */
    fun getDeviceName(context: Context): String {
        val brand = Build.BRAND ?: "Unknown"
        val model = Build.MODEL ?: "Device"
        val release = Build.VERSION.RELEASE ?: "Unknown"
        return "$brand $model on Android $release"
    }
    
    /**
     * 获取设备ID（兼容旧版本）
     *
     * @param context Android Context
     * @return 设备ID（SHA-256哈希，64字符）
     */
    fun getOrCreateDeviceId(context: Context): String {
        // 1. 尝试从存储读取（兼容旧版本）
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedId = prefs.getString(KEY_DEVICE_ID, null)
        if (!storedId.isNullOrEmpty() && storedId.length == 64) {
            return storedId
        }
        
        // 2. 生成新的设备ID（基于设备指纹）
        val fingerprint = getOrCreateDeviceFingerprint(context)
        val deviceId = fingerprintToHash(fingerprint)
        
        // 3. 保存到存储（兼容旧版本）
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        
        return deviceId
    }
    
    /**
     * 清除设备指纹
     *
     * @param context Android Context
     */
    fun clearDeviceFingerprint(context: Context) {
        deleteFingerprintFromEncryptedStorage(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_FINGERPRINT).remove(KEY_DEVICE_ID).apply()
    }
    
    // MARK: - Private Methods
    
    /**
     * 从加密存储加载设备指纹
     */
    private fun loadFingerprintFromEncryptedStorage(context: Context): HardwareFingerprint? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            val fingerprintJson = encryptedPrefs.getString(KEY_FINGERPRINT, null)
            if (fingerprintJson != null) {
                // 解析JSON（简化版，实际应使用Gson或Moshi）
                parseFingerprintFromJson(fingerprintJson)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 保存设备指纹到加密存储
     */
    private fun saveFingerprintToEncryptedStorage(context: Context, fingerprint: HardwareFingerprint) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            val fingerprintJson = fingerprintToString(fingerprint)
            encryptedPrefs.edit().putString(KEY_FINGERPRINT, fingerprintJson).apply()
        } catch (e: Exception) {
            // 忽略错误，降级到普通存储
        }
    }
    
    /**
     * 从加密存储删除设备指纹
     */
    private fun deleteFingerprintFromEncryptedStorage(context: Context) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            encryptedPrefs.edit().remove(KEY_FINGERPRINT).apply()
        } catch (e: Exception) {
            // 忽略错误
        }
    }
    
    /**
     * 从普通存储加载设备指纹（备用）
     */
    private fun loadFingerprintFromPreferences(context: Context): HardwareFingerprint? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fingerprintJson = prefs.getString(KEY_FINGERPRINT, null)
        return if (fingerprintJson != null) {
            parseFingerprintFromJson(fingerprintJson)
        } else {
            null
        }
    }
    
    /**
     * 保存设备指纹到普通存储（备用）
     */
    private fun saveFingerprintToPreferences(context: Context, fingerprint: HardwareFingerprint) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fingerprintJson = fingerprintToString(fingerprint)
        prefs.edit().putString(KEY_FINGERPRINT, fingerprintJson).apply()
    }
    
    /**
     * 从JSON解析设备指纹
     */
    private fun parseFingerprintFromJson(json: String): HardwareFingerprint? {
        return try {
            val jsonObj = JSONObject(json)
            
            val cpuAbisJson = jsonObj.optJSONArray("cpuAbis")
            val cpuAbis = mutableListOf<String>()
            if (cpuAbisJson != null) {
                for (i in 0 until cpuAbisJson.length()) {
                    cpuAbis.add(cpuAbisJson.getString(i))
                }
            }
            
            HardwareFingerprint(
                androidId = jsonObj.getString("androidId"),
                brand = jsonObj.getString("brand"),
                model = jsonObj.getString("model"),
                manufacturer = jsonObj.getString("manufacturer"),
                sdkVersion = jsonObj.getInt("sdkVersion"),
                release = jsonObj.getString("release"),
                screenWidth = jsonObj.getInt("screenWidth"),
                screenHeight = jsonObj.getInt("screenHeight"),
                densityDpi = jsonObj.getInt("densityDpi"),
                density = jsonObj.getDouble("density").toFloat(),
                cpuAbis = cpuAbis,
                totalMemory = jsonObj.getLong("totalMemory"),
                cpuCount = jsonObj.getInt("cpuCount"),
                hardware = jsonObj.getString("hardware")
            )
        } catch (e: Exception) {
            null
        }
    }
}
