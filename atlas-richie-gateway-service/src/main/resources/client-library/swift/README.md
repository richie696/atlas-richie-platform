# Atlas Richie Swift HTTP Client (atlas-richie-gateway-swift-client)

Swift版本的HTTP客户端库，支持ECC+AES-GCM加密和防重复提交功能。

## 📦 特性

- ✅ **ECC+AES-GCM加密** - 使用CryptoKit（Apple官方库）
- ✅ **防重复提交** - 客户端和服务器双重防护
- ✅ **灵活的配置** - 每个API端点独立配置安全选项
- ✅ **自动处理** - 密钥交换、重新握手、错误处理
- ✅ **URL协议分离** - 业务代码定义Url枚举，实现UrlProtocol协议
- ✅ **零外部依赖** - 仅使用Swift标准库和系统框架

## 🚀 快速开始

### 前置要求

- **iOS 13.0+** 或 **macOS 10.15+**
- **Xcode 12.0+**
- **Swift 5.0+**

### 📦 依赖说明

#### 运行时依赖

**✅ 零外部依赖**，仅使用Swift标准库和Apple系统框架：

| 框架/模块 | 用途 | 版本要求 |
|----------|------|---------|
| **Foundation** | 基础功能（URLSession、URLRequest等） | iOS 13.0+ / macOS 10.15+ |
| **CryptoKit** | ECC密钥交换和AES-GCM加密 | iOS 13.0+ / macOS 10.15+ |

**无需通过Swift Package Manager或CocoaPods安装任何第三方库！**

#### Swift Package Manager（如果作为包使用）

如果要将此库作为Swift Package使用，创建 `Package.swift`：

```swift
// swift-tools-version:5.5
import PackageDescription

let package = Package(
    name: "RichieHttpClient",
    platforms: [
        .iOS(.v13),
        .macOS(.v10_15)
    ],
    products: [
        .library(name: "RichieHttpClient", targets: ["RichieHttpClient"])
    ],
    targets: [
        .target(name: "RichieHttpClient", dependencies: [])
    ]
)
```

#### CocoaPods（可选）

如果使用CocoaPods管理项目，无需添加任何pod依赖，直接集成源码即可。

### 安装

#### 方法1：直接复制文件

```bash
# 复制HttpClient.swift到你的项目
cp HttpClient.swift YourProject/Sources/
```

#### 方法2：Swift Package Manager

```swift
dependencies: [
    .package(path: "../client-library/swift")
]
```

### 基本使用

```swift
import Foundation
// 注意：导入业务代码定义的Url枚举（示例中使用example目录下的Url）
import Url  // 或 YourApp.Url

// 创建客户端
let client = HttpClient(config: HttpClientConfig(
    baseUrl: "https://your-gateway.com"
))

// 发送请求
let result = try await client.request(Url.userLogin, 
    body: ["username": "user", "password": "pass"])
```

## 📚 详细使用

### 1. 定义URL配置（业务代码）

```swift
// Url.swift
import Foundation
import httpclient  // 导入HttpClient库

enum Url: UrlProtocol {
    case userLogin
    case orderSubmit
    
    var path: String {
        switch self {
        case .userLogin: return "/api/auth/login"
        case .orderSubmit: return "/api/order/submit"
        }
    }
    
    var method: Method { .post }
    var needEncryption: Bool { true }
    var needDuplicateCheck: Bool { false }
}
```

### 2. 创建客户端

```swift
let client = HttpClient(config: HttpClientConfig(
    baseUrl: "https://your-gateway.com",
    clientId: "ios-app-client",
    duplicateSubmitTimeWindow: 3000,
    timeout: 30.0
))
```

### 3. 发送请求

```swift
// 基本请求
let result = try await client.request(Url.userLogin, 
    body: ["username": "user", "password": "pass"])
```

## 🔐 加密说明

Swift版本使用Apple官方CryptoKit框架：

1. **ECC密钥交换**: `P256.KeyAgreement`
2. **共享密钥**: `sharedSecretFromKeyAgreement`
3. **AES-256-GCM加密**: `AES.GCM.seal()`

所有加密功能都使用iOS/macOS系统框架，无需外部依赖。

## ⚠️ 注意事项

1. **iOS/macOS版本**: 需要iOS 13.0+或macOS 10.15+（CryptoKit的最低要求）
2. **HTTPS要求**: 加密功能需要在HTTPS环境下使用
3. **异步API**: 使用Swift async/await，需要在异步上下文中调用
4. **内存管理**: Swift自动管理内存，无需手动清理

## 📖 示例代码

查看 `example/` 目录获取完整示例：

- `Url.swift` - URL配置示例
- `LoginView.swift` - SwiftUI使用示例
- `AppUrlExample.swift` - 完整使用示例

---

**版本**: 2.0  
**更新**: 2025-11-01  
**最低系统版本**: iOS 13.0+ / macOS 10.15+

