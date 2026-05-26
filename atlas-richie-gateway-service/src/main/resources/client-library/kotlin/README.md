# Kotlin HTTP客户端库（Android）

Kotlin版本的HTTP客户端库，支持ECC+AES-GCM加密和防重复提交功能，适用于Android应用。

## 📦 特性

- ✅ **ECC+AES-GCM加密** - 使用javax.crypto（Android标准库）
- ✅ **防重复提交** - 客户端和服务器双重防护
- ✅ **灵活的配置** - 每个API端点独立配置安全选项
- ✅ **自动处理** - 密钥交换、重新握手、错误处理
- ✅ **URL接口分离** - 业务代码定义Url枚举，实现UrlInterface接口
- ✅ **Coroutines支持** - 使用Kotlin协程进行异步处理

## 🚀 快速开始

### 前置要求

- **Android API Level 21+** (Android 5.0+)
- **Kotlin 1.6+**
- **Android Studio** 或 **IntelliJ IDEA**

### 📦 依赖说明

#### 运行时依赖

需要在项目的 `build.gradle.kts`（或 `build.gradle`）中添加以下依赖：

```kotlin
dependencies {
    // HTTP客户端库（必需）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Kotlin协程（必需）
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // JSON处理（Android标准库，无需额外依赖）
    // org.json.JSONObject 是Android SDK内置的
    
    // 加密库（Android标准库，无需额外依赖）
    // javax.crypto.* 是Android SDK内置的
}
```

#### 依赖版本说明

| 依赖库 | 版本 | 用途 | 必需性 |
|--------|------|------|--------|
| **okhttp3** | ^4.12.0 | HTTP客户端 | ✅ 必需 |
| **kotlinx-coroutines-android** | ^1.7.3 | 协程支持 | ✅ 必需 |
| **javax.crypto** | - | 加密功能 | ✅ 内置（无需添加） |
| **org.json** | - | JSON处理 | ✅ 内置（无需添加） |
| **android.util.Base64** | - | Base64编码 | ✅ 内置（无需添加） |

#### Gradle配置示例

**build.gradle.kts** (Kotlin DSL):
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 34
    
    defaultConfig {
        minSdk = 21  // Android 5.0+
        targetSdk = 34
    }
}

dependencies {
    // HTTP客户端
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Kotlin协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // 其他依赖...
}
```

**build.gradle** (Groovy):
```gradle
dependencies {
    // HTTP客户端
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    
    // Kotlin协程
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### 安装

#### 方法1：直接复制文件

```bash
# 复制HttpClient.kt到你的项目
cp HttpClient.kt YourProject/app/src/main/java/com/your/app/
```

#### 方法2：作为模块导入

将整个目录作为Android Library模块导入到你的项目中。

### 基本使用

```kotlin
import com.rydeen.httpclient.*
import com.rydeen.example.Url  // 业务代码定义的Url枚举

// 创建客户端
val client = HttpClient(HttpClientConfig(
    baseUrl = "https://your-gateway.com",
    clientId = "android-app-client"
))

// 发送请求（使用协程）
lifecycleScope.launch {
    val result = client.request(
        Url.UserLogin,
        body = mapOf("username" to "user", "password" to "pass")
    )
}
```

## 📚 详细使用

### 1. 定义URL配置（业务代码）

```kotlin
// Url.kt
import com.rydeen.httpclient.*

enum class Url : UrlInterface {
    UserLogin {
        override val path = "/api/auth/login"
        override val method = Method.POST
        override val needEncryption = true
        override val needDuplicateCheck = false
    },
    OrderSubmit {
        override val path = "/api/order/submit"
        override val method = Method.POST
        override val needEncryption = true
        override val needDuplicateCheck = true
    };
}
```

### 2. 创建客户端

```kotlin
val client = HttpClient(HttpClientConfig(
    baseUrl = "https://your-gateway.com",
    clientId = "android-app",
    duplicateSubmitTimeWindow = 3000L,
    timeout = 30000L
))
```

### 3. 发送请求

```kotlin
// 在Activity/Fragment中使用
lifecycleScope.launch {
    try {
        val result = client.request(
            Url.UserLogin,
            body = mapOf("username" to user, "password" to pass)
        )
        // 处理成功结果
    } catch (e: DuplicateRequestException) {
        // 处理重复请求
    } catch (e: Exception) {
        // 处理其他错误
    }
}
```

## 🔐 加密说明

Kotlin版本使用Android标准加密库：

1. **ECC密钥交换**: `KeyPairGenerator.getInstance("EC")` + `KeyAgreement.getInstance("ECDH")`
2. **AES-256-GCM加密**: `Cipher.getInstance("AES/GCM/NoPadding")`

所有加密功能都使用Android SDK内置的 `javax.crypto` 包，无需外部依赖。

## ⚠️ 注意事项

1. **Android版本**: 需要Android API Level 21+（Android 5.0+）
2. **网络权限**: 需要在 `AndroidManifest.xml` 中添加网络权限：
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   ```
3. **协程作用域**: 确保在正确的协程作用域中调用（如 `lifecycleScope`、`viewModelScope`）
4. **内存泄漏**: 在Activity/Fragment销毁时记得调用 `client.cleanup()`

## 📖 示例代码

查看 `example/` 目录获取完整示例：

- `Url.kt` - URL配置示例
- `LoginActivity.kt` - Activity使用示例

---

**版本**: 2.0  
**更新**: 2025-11-01  
**最低Android版本**: API Level 21 (Android 5.0+)

