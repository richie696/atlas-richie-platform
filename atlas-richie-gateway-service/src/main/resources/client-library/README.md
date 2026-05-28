# Richie网关客户端库

多语言客户端库，支持 ECC+AES-GCM 加密和防重复提交。所有客户端采用统一的接口设计，业务代码自行定义 URL 枚举。

## 📦 支持的语言/平台

| 语言/平台                | 状态     | 目录                   | 文档                           | 适用场景                     | 运行时依赖                   |
|----------------------|--------|----------------------|------------------------------|--------------------------|-------------------------|
| **TypeScript (Web)** | ✅ 生产可用 | [web/](./web/)       | [README](./web/README.md)    | Web 应用、React、Angular、Vue | 零依赖（浏览器原生API）           |
| **Node.js**          | ✅ 生产可用 | [nodejs/](./nodejs/) | [README](./nodejs/README.md) | Node.js 服务器端应用           | 零依赖（Node.js 18+内置模块）    |
| **Go**               | ✅ 生产可用 | [go/](./go/)         | [README](./go/README.md)     | 后端服务、CLI 工具、微服务          | 零依赖（Go标准库）              |
| **Swift**            | ✅ 生产可用 | [swift/](./swift/)   | [README](./swift/README.md)  | iOS、macOS 原生应用           | 零依赖（Swift标准库）           |
| **Kotlin**           | ✅ 生产可用 | [kotlin/](./kotlin/) | [README](./kotlin/README.md) | Android 原生应用             | 零依赖（Android标准库）         |
| **Rust**             | ✅ 生产可用 | [rust/](./rust/)     | [README](./rust/README.md)   | 高性能场景、嵌入式                | 需安装依赖（见rust/Cargo.toml） |
| **C++**              | ✅ 生产可用 | [cpp/](./cpp/)       | [README](./cpp/README.md)    | Windows/Linux 桌面应用       | 需OpenSSL库               |

**图例**：
- ✅ 生产可用：完整实现，包含完整文档和示例
- 🚧 框架代码：核心代码已实现，需要完善
- 📋 计划中：未开始实现

## 🎯 快速选择

### 🌐 我要开发 Web 应用 → TypeScript ⭐

**最完整的实现**，支持三大框架：

```bash
cd web  # 或 ts
```

- ✅ 完整的 ECC+AES-GCM 加密
- ✅ 防重复提交
- ✅ React 18+ 示例
- ✅ Angular 17+ 示例
- ✅ Vue 3 示例
- ✅ 详细文档

**依赖说明**：
- **运行时**: 零外部依赖，仅使用浏览器原生API（Web Crypto API、Fetch API）
- **开发时**: 如使用TypeScript，需要 `typescript@^1.0.0`
- **框架**: 如使用框架集成，需要对应的框架包（`react@^18.0.0`、`@angular/core@^17.0.0`、`vue@^3.0.0`）

详见: [web/README.md](./web/README.md#📦-依赖说明)

### 🖥️ 我要开发后端服务/CLI → Go ⭐

**性能优秀**，单文件部署：

```bash
cd go
```

- ✅ 零外部依赖（只用标准库）
- ✅ 跨平台编译
- ✅ 防重复提交
- ✅ ECC 加密

### 📱 我要开发 iOS 应用 → Swift

**原生 iOS 开发**：

```bash
cd swift
```

- ✅ 使用 CryptoKit（Apple 官方）
- ✅ SwiftUI 示例
- ✅ URL 协议分离

### 🤖 我要开发 Android 应用 → Kotlin

**原生 Android 开发**：

```bash
cd kotlin
```

- ✅ 使用 javax.crypto
- ✅ OkHttp 网络库
- ✅ Coroutines 异步
- ✅ URL 接口分离

### ⚡ 我要开发高性能应用 → Rust

**内存安全，极致性能**：

```bash
cd rust
```

- ✅ 原生 enum + trait
- ✅ 零成本抽象
- ✅ 编译时保证

### 💻 我要开发 Node.js 应用 → Node.js

**服务器端应用**：

```bash
cd nodejs
```

- ✅ 完整的 ECC+AES-GCM 加密
- ✅ 防重复提交
- ✅ 零外部运行时依赖

**依赖说明**：
- **运行时**: 零外部依赖，仅使用Node.js内置模块（`crypto`、`fetch`）
- **要求**: Node.js 18.0.0+
- **开发时**: 如使用TypeScript，需要 `typescript@^1.0.0`、`@types/node@^20.0.0`、`ts-node@^10.9.0`

详见: [nodejs/README.md](./nodejs/README.md#依赖说明)

### 🔧 我要开发桌面应用 → C++

**Windows/Linux 桌面应用**：

```bash
cd cpp
```

- ✅ C++11 兼容
- ✅ 抽象基类接口
- ✅ 使用 OpenSSL

## 🏗️ 架构设计：接口分离

所有客户端都采用**接口分离**的设计：

- **库代码**：定义 URL 接口（Protocol/Trait/Interface）
- **业务代码**：定义自己的 URL 枚举，实现接口

这样设计的好处：
- ✅ 库代码更简洁，只关注 HTTP 客户端功能
- ✅ 业务代码完全控制 URL 定义
- ✅ 符合各语言的最佳实践

### 接口定义对比

| 语言 | 库定义 | 业务代码实现 |
|------|--------|-------------|
| **Swift** | `protocol UrlProtocol` | `enum Url: UrlProtocol` |
| **Rust** | `trait UrlTrait` | `enum Url` + `impl UrlTrait` |
| **C++** | `class UrlInterface` | `enum class Url` + `class UrlHelper` |
| **Kotlin** | `interface UrlInterface` | `enum class Url : UrlInterface` |
| **Go** | `interface UrlInterface` | `type Url int` + 方法集 |

## 📚 各语言使用示例

### TypeScript

**业务代码定义 URL**（UPPER_CASE，模拟 Java）：

```typescript
export class AppUrl {
    public static readonly USER_LOGIN = new Url(
        'USER_LOGIN',
        '/api/auth/login',
        Method.POST,
        true,   // needEncryption
        false   // needDuplicateCheck
    );
}
```

**使用**：
```typescript
const client = new HttpClient({ baseUrl: 'https://your-gateway.com' });
await client.request(AppUrl.USER_LOGIN, { body: { username, password } });
```

### Node.js

**业务代码定义 URL**（与Web版本相同，UPPER_CASE，模拟 Java）：

```typescript
export class AppUrl {
    public static readonly USER_LOGIN = new Url(
        'USER_LOGIN',
        '/api/auth/login',
        Method.POST,
        true,   // needEncryption
        false   // needDuplicateCheck
    );
}
```

**使用**：
```typescript
import { ResultVO, isSuccess, extractData } from './framework/http-client';

const client = new HttpClient({
    baseUrl: 'https://your-gateway.com',
    userId: 'user123',
    enableHeaderAutoManagement: true  // 默认启用请求头自动管理
});

interface User {
    userId: string;
    username: string;
    email: string;
}

const result: ResultVO<User> = await client.request<User>(AppUrl.USER_LOGIN, { 
    body: { username, password } 
});

if (isSuccess(result)) {
    const userData = extractData(result);
    console.log('登录成功:', userData);
    // 注意：token现在从响应头自动获取并保存，无需手动处理
}
```

### Go

**业务代码定义 URL**（PascalCase，类型别名 + iota + 方法集）：

```go
// 定义类型
type Url int

// 枚举常量
const (
    UserLogin Url = iota
    OrderSubmit
)

// 实现接口
func (u Url) Path() string {
    switch u {
    case UserLogin: return "/api/auth/login"
    case OrderSubmit: return "/api/order/submit"
    }
}

func (u Url) Method() Method { return POST }
func (u Url) NeedEncryption() bool { return true }
func (u Url) NeedDuplicateCheck() bool { return false }
```

**使用**：
```go
client := NewHttpClient(HttpClientConfig{
    BaseUrl: "https://your-gateway.com",
})
result, err := client.Request(UserLogin, &RequestOptions{
    Body: map[string]string{"username": "user", "password": "pass"},
})
```

### Swift

**业务代码定义 URL**（camelCase，enum 实现协议）：

```swift
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

**使用**：
```swift
let client = HttpClient(config: HttpClientConfig(
    baseUrl: "https://your-gateway.com"
))
let result = try await client.request(Url.userLogin, 
    body: ["username": "user", "password": "pass"])
```

### Kotlin

**业务代码定义 URL**（camelCase，enum class 实现接口）：

```kotlin
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

**使用**：
```kotlin
val client = HttpClient(HttpClientConfig(
    baseUrl = "https://your-gateway.com"
))
val result = client.request(Url.UserLogin, 
    body = mapOf("username" to "user", "password" to "pass"))
```

### Rust

**业务代码定义 URL**（PascalCase，enum 实现 trait）：

```rust
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Url {
    UserLogin,
    OrderSubmit,
}

impl UrlTrait for Url {
    fn path(&self) -> &'static str {
        match self {
            Url::UserLogin => "/api/auth/login",
            Url::OrderSubmit => "/api/order/submit",
        }
    }
    
    fn method(&self) -> Method { Method::Post }
    fn need_encryption(&self) -> bool { true }
    fn need_duplicate_check(&self) -> bool { false }
}
```

**使用**：
```rust
let client = HttpClient::new(HttpClientConfig {
    base_url: "https://your-gateway.com".to_string(),
});
let result = client.request(Url::UserLogin, Some(body)).await?;
```

### C++

**业务代码定义 URL**（PascalCase，enum class + Helper 类）：

```cpp
enum class Url {
    UserLogin,
    OrderSubmit,
};

class UrlHelper : public UrlInterface {
public:
    explicit UrlHelper(Url url) : url_(url) {}
    
    std::string path() const override {
        switch (url_) {
        case Url::UserLogin: return "/api/auth/login";
        case Url::OrderSubmit: return "/api/order/submit";
        }
    }
    
    Method method() const override { return Method::POST; }
    bool needEncryption() const override { return true; }
    bool needDuplicateCheck() const override { return false; }
    
private:
    Url url_;
};
```

**使用**：
```cpp
HttpClient client(HttpClientConfig{
    .baseUrl = "https://your-gateway.com"
});
UrlHelper helper(Url::UserLogin);
Response response = client.request(helper, body);
```

## 📊 功能对比表

| 功能 | TypeScript (Web) | Node.js | Go | Swift | Kotlin | Rust | C++ |
|------|----------------|---------|----|----|--------|------|-----|
| **核心功能** |
| HTTP 客户端 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| URL 接口分离 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 防重复提交 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **加密功能** |
| ECC 密钥交换 | ✅ | ✅ | ✅ | ✅ | ✅ | 🚧 | ✅ |
| AES-GCM 加密 | ✅ | ✅ | ✅ | ✅ | ✅ | 🚧 | ✅ |
| 自动重新握手 | ✅ | ✅ | ✅ | ✅ | ✅ | 🚧 | ✅ |
| **文档和示例** |
| 详细文档 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 完整示例 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 框架集成示例 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 📋 |

图例：
- ✅ 完整实现
- 🚧 部分实现/框架代码
- 📋 计划中/未实现

## 🎨 设计理念

### 1. 遵循语言惯用法

每种语言都使用该语言的**最佳实践**，而非强行统一语法：

| 语言 | URL 定义方式 | 命名风格 | 原因 |
|------|------------|---------|------|
| TypeScript | `class AppUrl { static readonly }` | UPPER_CASE | 无真正的枚举，模拟 Java |
| Go | `type Url int` + `iota` + 方法集 | PascalCase | Go 惯用法：类型别名 + 接口 |
| Rust | `enum Url` + `impl UrlTrait` | PascalCase | Rust 原生 enum 完美支持 |
| Swift | `enum Url: UrlProtocol` | camelCase | Swift 规范：协议 + 枚举 |
| Kotlin | `enum class Url : UrlInterface` | camelCase | Kotlin 规范：接口 + 枚举 |
| C++ | `enum class Url` + `class UrlHelper` | PascalCase | C++ 规范：抽象基类 |

### 2. 四种配置组合

| 加密 | 防重复 | 适用场景 | 示例 |
|------|--------|---------|------|
| ❌ | ❌ | 公开数据 | 菜单列表、字典数据 |
| ✅ | ❌ | 敏感数据，可重试 | 用户登录 |
| ❌ | ✅ | 普通操作，防重复 | 文件上传 |
| ✅ | ✅ | 高安全操作 | 支付、订单提交 |

### 3. 错误处理

所有语言都支持两种错误：

| 错误 | 说明 | 处理建议 |
|------|------|---------|
| DUPLICATE_REQUEST | 客户端检测到重复 | 提示用户请求正在处理中 |
| DUPLICATE_SUBMIT | 服务器检测到重复（HTTP 429） | 提示用户请求过于频繁 |

## 🔧 实现要点

### 必需实现的核心功能

1. **ECC 密钥交换（ECDH）**
   - 生成 P-256 曲线密钥对
   - 与网关交换公钥
   - 计算共享密钥（256 位）

2. **AES-GCM 加密/解密**
   - 256 位密钥
   - 12 字节 IV（每次加密随机生成）
   - 128 位 Tag 验证

3. **防重复提交**
   - requestId 生成（MD5 哈希）
   - 本地队列管理
   - 时间窗口检查

4. **HTTP 通信**
   - 设置正确的请求头
   - 处理 HTTP 423（重新握手）
   - 处理 HTTP 429（重复提交）

### 加密库选择与依赖

| 语言 | 推荐库 | 说明 | 运行时依赖 |
|------|--------|------|----------|
| **TypeScript (Web)** | Web Crypto API | 浏览器内置 | ✅ 零依赖 |
| **Node.js** | crypto模块 | Node.js内置 | ✅ 零依赖（Node.js 18+） |
| **Go** | crypto/ecdh, crypto/aes | Go标准库 | ✅ 零依赖 |
| **Rust** | ring | 高性能加密库 | 📦 需安装（见Cargo.toml） |
| **C++** | OpenSSL | 工业标准 | 📦 需安装OpenSSL库 |
| **Swift** | CryptoKit | Apple官方库 | ✅ 零依赖（iOS/macOS系统库） |
| **Kotlin** | javax.crypto | Android标准库 | ✅ 零依赖（Android系统库） |

### 依赖详细说明

#### Web/TypeScript 客户端

**运行时依赖**: ✅ **零外部依赖**

- 使用浏览器原生API：`window.crypto.subtle`（Web Crypto API）、`fetch`（Fetch API）
- 浏览器要求：Chrome 37+, Firefox 34+, Safari 11+, Edge 79+

**开发依赖**（可选）:
```bash
# TypeScript
npm install --save-dev typescript@^1.0.0

# React框架
npm install react@^18.0.0 react-dom@^18.0.0

# Angular框架
npm install @angular/core@^17.0.0 @angular/common@^17.0.0

# Vue框架
npm install vue@^3.0.0
```

详见: [web/README.md](./web/README.md#📦-依赖说明)

#### Node.js 客户端

**运行时依赖**: ✅ **零外部依赖**（Node.js 18.0.0+）

- 使用Node.js内置模块：`crypto`、`fetch`（Node.js 18+内置）

**开发依赖**（可选）:
```bash
npm install --save-dev typescript@^1.0.0
npm install --save-dev @types/node@^20.0.0
npm install --save-dev ts-node@^10.9.0
```

详见: [nodejs/README.md](./nodejs/README.md#依赖说明)

#### Go 客户端

**运行时依赖**: ✅ **零外部依赖**

- 使用Go标准库：`crypto/ecdh`、`crypto/aes`、`net/http`等
- 要求：Go 1.18+（推荐1.20+，`crypto/ecdh`在1.20+中可用）

详见: [go/README.md](./go/README.md#📦-依赖说明)

#### Rust 客户端

**运行时依赖**: 📦 **需要外部依赖**

- `reqwest@^0.11` - HTTP客户端库
- `serde@^1.0` + `serde_json@^1.0` - 序列化框架
- `tokio@^1.0` - 异步运行时
- 加密库（待完善，可选`ring@0.17`或`openssl@0.10`）

详见: [rust/README.md](./rust/README.md#📦-依赖说明) 和 [rust/Cargo.toml](./rust/Cargo.toml)

#### Swift 客户端

**运行时依赖**: ✅ **零外部依赖**（iOS/macOS系统库）

- `Foundation` - 基础框架（iOS 13.0+ / macOS 10.15+）
- `CryptoKit` - 加密框架（iOS 13.0+ / macOS 10.15+）

详见: [swift/README.md](./swift/README.md#📦-依赖说明)

#### Kotlin 客户端

**运行时依赖**: 📦 **需要外部依赖**

- `okhttp3@^4.12.0` - HTTP客户端库（必需）
- `kotlinx-coroutines-android@^1.7.3` - 协程支持（必需）
- `javax.crypto` - 加密功能（Android SDK内置）
- `org.json` - JSON处理（Android SDK内置）
- 要求：Android API Level 21+ (Android 5.0+)

详见: [kotlin/README.md](./kotlin/README.md#📦-依赖说明)

#### C++ 客户端

**运行时依赖**: 📦 **需要系统库**

- `libcurl` - HTTP客户端库（7.0+）
- `OpenSSL` - 加密库（1.1.0+，推荐1.1.1+）
- 要求：C++11编译器（GCC 4.7+, Clang 3.1+, MSVC 2012+）

详见: [cpp/README.md](./cpp/README.md#📦-依赖说明)

## 🚀 快速开始

### TypeScript/Web（推荐从这里开始）

```bash
# 1. 进入目录
cd web  # 或 ts

# 2. 查看文档和依赖说明
cat README.md

# 3. 选择你的框架
cd examples/react     # 或 angular, vue

# 4. 复制代码到你的项目
# 注意：无需安装任何npm包（零依赖），直接使用浏览器原生API
# 完成！
```

### Node.js（服务器端）

```bash
# 1. 进入目录
cd nodejs

# 2. 查看文档和依赖说明
cat README.md

# 3. 运行示例（需要Node.js 18+）
# 注意：运行时零依赖，仅使用Node.js内置模块
npm run example  # 如果安装了开发依赖
```

### Go（后端开发）

```bash
# 1. 查看文档
cd go
cat README.md

# 2. 运行示例
cd example
go run main.go

# 3. 集成到你的项目
# 导入并使用
```

### Swift/Kotlin（移动应用）

```bash
# 查看示例代码
cat swift/example/LoginView.swift
cat kotlin/example/LoginActivity.kt

# 复制到你的 Xcode/Android Studio 项目
```

### Rust（高性能场景）

```bash
# 1. 进入目录
cd rust

# 2. 查看文档
cat README.md

# 3. 运行示例
cargo run --example main
```

## 📚 参考文档

- **[TypeScript 客户端文档](./ts/README.md)** - 最详细
- **[Go 客户端文档](./go/README.md)** - 基础说明
- **[Rust 客户端文档](./rust/README.md)** - 高性能实现
- **[网关设计文档](../../文档/网关设计文档.md)** - 服务端架构
- **[兼容性说明](../../文档/服务端客户端兼容性.md)** - 兼容性检查

## 🤝 贡献

如果你实现了其他语言的客户端，欢迎贡献：

1. 遵循统一的接口设计
2. 实现完整的加密和防重复提交功能
3. 提供完整的示例代码
4. 添加详细的 README 文档

## 📝 实现进度

### 已完成 ✅

- **TypeScript**: 100% 完成
  - HTTP 客户端 ✅
  - ECC+AES-GCM 加密 ✅
  - 防重复提交 ✅
  - React/Angular/Vue 示例 ✅
  - 完整文档 ✅

- **Go**: 90% 完成
  - HTTP 客户端 ✅
  - 防重复提交 ✅
  - ECC 加密（使用 crypto/ecdh）✅
  - URL 接口分离 ✅
  - 基础文档 ✅

- **Swift**: 90% 完成
  - HTTP 客户端 ✅
  - 防重复提交 ✅
  - URL 协议分离 ✅
  - SwiftUI 示例 ✅
  - ECC 加密 ✅

- **Kotlin**: 90% 完成
  - HTTP 客户端 ✅
  - 防重复提交 ✅
  - URL 接口分离 ✅
  - Activity 示例 ✅
  - ECC 加密 ✅

- **Rust**: 85% 完成
  - HTTP 客户端 ✅
  - 防重复提交 ✅
  - URL trait 分离 ✅
  - 完整示例 ✅
  - ECC 加密（需要完善）🚧

- **C++**: 85% 完成
  - HTTP 客户端 ✅
  - 防重复提交 ✅
  - URL 接口分离 ✅
  - 完整示例 ✅
  - ECC 加密 ✅

---

**版本**: 2.0  
**更新**: 2025-11-01  
**作者**: richie696
