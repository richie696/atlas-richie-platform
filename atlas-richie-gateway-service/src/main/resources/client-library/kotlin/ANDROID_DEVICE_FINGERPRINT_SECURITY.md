# Android 设备指纹安全方案使用指南

## 一、概述

Android 原生应用具有强大的系统访问能力，可以获取完整的设备信息。本方案提供了**高安全性的设备识别方案**，适用于 Android 原生应用。

## 二、安全策略

### 2.1 Android 环境优势

| 特性 | Android 原生 | 小程序 | 浏览器端 |
|------|------------|--------|---------|
| **Android ID** | ✅ 可用（最稳定） | ❌ 不可用 | ❌ 不可用 |
| **EncryptedSharedPreferences** | ✅ 可用（加密存储） | ❌ 不可用 | ❌ 不可用 |
| **系统信息** | ✅ 完整 | ⚠️ 有限 | ⚠️ 有限 |
| **硬件信息** | ✅ 完整（CPU、内存、MAC） | ❌ 不可用 | ⚠️ 有限 |

### 2.2 安全策略

1. **Android ID（基础标识）**：
   - 使用 `Settings.Secure.ANDROID_ID` 作为基础标识
   - 同一设备上所有应用Android ID相同
   - 恢复出厂设置后Android ID会变化

2. **EncryptedSharedPreferences 加密存储**：
   - 使用 AndroidX Security 库的 `EncryptedSharedPreferences`
   - 自动加密存储设备指纹
   - 普通 SharedPreferences 作为备用存储

3. **硬件指纹（动态验证）**：
   - 每次请求时动态生成硬件指纹
   - 基于设备品牌、型号、系统版本、屏幕信息等
   - 与存储的指纹匹配验证

4. **双重验证**：
   - 存储的设备ID（稳定性）
   - 动态硬件指纹（安全性）

## 三、使用方法

### 3.1 基础使用

```kotlin
import com.richie.httpclient.DeviceFingerprint

// 1. 获取或创建设备指纹（首次启动时会生成并保存）
val fingerprint = DeviceFingerprint.getOrCreateDeviceFingerprint(context)
println("Android ID: ${fingerprint.androidId}")
println("设备品牌: ${fingerprint.brand}")

// 2. 获取设备名称（用于显示）
val deviceName = DeviceFingerprint.getDeviceName(context)
println("设备名称: $deviceName") // "Xiaomi Mi 14 on Android 13"
```

### 3.2 登录时使用

```kotlin
import com.richie.httpclient.DeviceFingerprint

fun login(username: String, password: String, mfaCode: String?) {
    // 1. 获取存储的设备指纹（用于稳定性）
    val storedFingerprint = DeviceFingerprint.getOrCreateDeviceFingerprint(context)
    
    // 2. 生成动态硬件指纹（用于安全性验证）
    val dynamicFingerprint = DeviceFingerprint.generateHardwareFingerprint(context)
    
    // 3. 构建登录请求
    val loginRequest = mapOf(
        "username" to username,
        "password" to password,
        "mfaCode" to (mfaCode ?: ""),
        "deviceId" to DeviceFingerprint.getOrCreateDeviceId(context),  // 设备ID
        "hardwareFingerprint" to DeviceFingerprint.fingerprintToString(dynamicFingerprint)  // 硬件指纹
    )
    
    // 4. 发送登录请求
    val request = Request.Builder()
        .url("https://your-gateway.com/api/login")
        .post(JSONObject(loginRequest).toString().toRequestBody("application/json".toMediaType()))
        .addHeader("X-Hardware-Fingerprint", DeviceFingerprint.fingerprintToString(dynamicFingerprint))
        .build()
    
    httpClient.newCall(request).enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            // 处理响应
        }
        
        override fun onFailure(call: Call, e: IOException) {
            // 处理错误
        }
    })
}
```

### 3.3 集成到 HTTP 客户端

```kotlin
import com.richie.httpclient.DeviceFingerprint

class SecureHttpClient(private val context: Context) {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            
            // 每次请求时生成动态硬件指纹
            val fingerprint = DeviceFingerprint.generateHardwareFingerprint(context)
            val fingerprintJson = DeviceFingerprint.fingerprintToString(fingerprint)
            
            // 自动添加硬件指纹
            val newRequest = request.newBuilder()
                .addHeader("X-Hardware-Fingerprint", fingerprintJson)
                .build()
            
            chain.proceed(newRequest)
        }
        .build()
}
```

## 四、硬件指纹特征说明

### 4.1 包含的特征

| 特征 | 说明 | 稳定性 | 权重 |
|------|------|--------|------|
| **Android ID** | 设备唯一标识符 | ⭐⭐⭐⭐⭐ | 0.4 |
| **设备品牌和型号** | 品牌 + 型号 | ⭐⭐⭐⭐⭐ | 0.2 |
| **屏幕分辨率** | 屏幕宽度和高度（px） | ⭐⭐⭐⭐ | 0.15 |
| **屏幕密度** | 屏幕密度DPI和缩放因子 | ⭐⭐⭐⭐ | 0.1 |
| **系统版本** | Android SDK版本 | ⭐⭐⭐ | 0.1 |
| **CPU架构** | CPU架构列表 | ⭐⭐⭐⭐ | 0.05 |

### 4.2 相似度计算

硬件指纹验证使用相似度比较，允许硬件特征有轻微变化：

- **相似度 >= 0.8**：认为匹配（允许通过）
- **相似度 < 0.8**：认为不匹配（拒绝请求）

**容错场景**：
- 系统版本更新（如 Android 13 → Android 14）
- CPU架构变化（如果设备支持多架构）
- 屏幕密度变化（如果设备支持）

## 五、安全优势

### 5.1 相比小程序和浏览器端

✅ **Android ID 稳定性高**：
- 同一设备上所有应用Android ID相同
- 不受应用卸载影响
- 恢复出厂设置后才会变化

✅ **EncryptedSharedPreferences 加密存储**：
- 使用 AndroidX Security 库自动加密
- 比普通 SharedPreferences 更安全
- 无需手动管理加密密钥

✅ **完整的系统信息**：
- 可以获取设备品牌、型号、系统版本等
- 可以获取 CPU、内存、屏幕等硬件信息
- 可以获取 MAC 地址（如果权限允许）

### 5.2 攻击难度

即使攻击者盗取了 Token 和 deviceId：
- ❌ **无法伪造硬件指纹**：Android ID、设备型号等系统信息无法伪造
- ❌ **无法访问加密存储**：EncryptedSharedPreferences 数据加密存储，权限受限
- ❌ **无法模拟系统环境**：即使知道部分信息，也无法完全匹配硬件指纹

## 六、依赖说明

### 6.1 必需依赖

**AndroidX Security 库**（用于加密存储）：
```gradle
dependencies {
    implementation "androidx.security:security-crypto:1.1.0-alpha06"
}
```

**系统框架**：
- `android.content.Context`：上下文
- `android.os.Build`：设备信息
- `android.provider.Settings`：系统设置（Android ID）
- `org.json.JSONObject`：JSON处理（Android内置）

### 6.2 配置说明

无需额外配置，使用系统API：
- `EncryptedSharedPreferences`：自动处理加密
- `MasterKey`：自动生成和管理密钥

## 七、最佳实践

### 7.1 加密存储使用

```kotlin
// EncryptedSharedPreferences 自动处理加密，无需手动配置
val fingerprint = DeviceFingerprint.getOrCreateDeviceFingerprint(context)
// 数据会自动保存到 EncryptedSharedPreferences（加密存储）
```

### 7.2 错误处理

```kotlin
try {
    val fingerprint = DeviceFingerprint.getOrCreateDeviceFingerprint(context)
    // 使用设备指纹
} catch (e: Exception) {
    Log.e("DeviceFingerprint", "获取设备指纹失败", e)
    // 降级处理：使用临时指纹或提示用户
}
```

### 7.3 定期验证

```kotlin
// 定期验证设备指纹是否仍然有效
val stored = DeviceFingerprint.getOrCreateDeviceFingerprint(context)
val current = DeviceFingerprint.generateHardwareFingerprint(context)

val similarity = DeviceFingerprint.calculateFingerprintSimilarity(stored, current)

if (similarity < 0.8) {
    Log.w("DeviceFingerprint", "设备指纹不匹配，可能需要重新认证")
    // 可以触发重新登录流程
}
```

## 八、常见问题

### Q1: Android ID 会变化吗？

**A**: 
- 同一设备上所有应用Android ID相同（稳定）
- 恢复出厂设置后Android ID会变化（预期行为）
- 某些厂商定制ROM可能影响Android ID的稳定性

### Q2: EncryptedSharedPreferences 数据会丢失吗？

**A**: 
- 应用卸载：数据会丢失（需要重新生成）
- 用户清除应用数据：数据会丢失
- 恢复出厂设置：数据会丢失

### Q3: 如果硬件发生变化怎么办？

**A**: 
- 硬件指纹相似度会降低
- 如果相似度 < 0.8，请求会被拒绝
- 用户需要重新登录，重新生成设备指纹

### Q4: 需要什么权限？

**A**: 
- **无需特殊权限**：Android ID、设备信息等都可以直接获取
- 如果需要获取 MAC 地址，需要 `ACCESS_WIFI_STATE` 权限（Android 6.0+）

## 九、总结

Android 端的设备指纹方案具有**最高的安全性和稳定性**：

1. ✅ **Android ID**：基于系统提供的标识符，稳定可靠
2. ✅ **EncryptedSharedPreferences**：使用 AndroidX Security 库，自动加密
3. ✅ **硬件指纹**：动态生成，难以伪造
4. ✅ **双重验证**：设备ID + 硬件指纹，提高安全性

相比小程序和浏览器端，Android 端可以访问更多系统级信息，提供更强的安全保护。
