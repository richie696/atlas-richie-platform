# Atlas Richie Rust HTTP Client (High Performance) (atlas-richie-gateway-rust-client)

Rust原生enum实现，完美适配URL配置场景，无需额外的struct！

## ✨ 为什么Rust不需要额外的struct？

**Rust的enum非常强大**，原生就能完美实现URL配置所需的所有功能：

```rust
// Rust原生enum - 完美方案！
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AppUrl {
    UserLogin,
    OrderSubmit,
    MenuAll,
}

impl AppUrl {
    pub fn path(&self) -> &'static str {
        match self {
            AppUrl::UserLogin => "/api/auth/login",
            AppUrl::OrderSubmit => "/api/order/submit",
            AppUrl::MenuAll => "/api/menu/all",
        }
    }
    
    pub fn method(&self) -> Method {
        match self {
            AppUrl::UserLogin => Method::Post,
            AppUrl::OrderSubmit => Method::Post,
            AppUrl::MenuAll => Method::Get,
        }
    }
    
    pub fn need_encryption(&self) -> bool {
        match self {
            AppUrl::UserLogin => true,
            AppUrl::OrderSubmit => true,
            AppUrl::MenuAll => false,
        }
    }
}
```

### Rust enum的优势

| 特性 | 支持 | 说明 |
|------|------|------|
| 枚举值即实例 | ✅ | 每个枚举值就是URL的一个实例 |
| 通过impl添加方法 | ✅ | path()、method()等方法 |
| 类型安全 | ✅ | 编译时检查 |
| 模式匹配 | ✅ | match表达式，穷尽检查 |
| 自动name | ✅ | 通过name()方法获取 |
| Copy trait | ✅ | 可以直接传值，无需引用 |
| 零开销 | ✅ | 编译时展开，运行时零开销 |

**对比TypeScript**: TypeScript没有强大的enum，需要自定义Enum基类。

**对比Go**: Rust的enum比Go的const更类型安全。

## 🚀 快速开始

### 1. 定义URL（url.rs）

```rust
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AppUrl {
    UserLogin,
    OrderSubmit,
    MenuAll,
}

impl AppUrl {
    pub fn path(&self) -> &'static str {
        match self {
            AppUrl::UserLogin => "/api/auth/login",
            AppUrl::OrderSubmit => "/api/order/submit",
            AppUrl::MenuAll => "/api/menu/all",
        }
    }
    
    pub fn method(&self) -> Method {
        match self {
            AppUrl::UserLogin => Method::Post,
            AppUrl::OrderSubmit => Method::Post,
            AppUrl::MenuAll => Method::Get,
        }
    }
    
    pub fn need_encryption(&self) -> bool {
        match self {
            AppUrl::UserLogin => true,
            AppUrl::OrderSubmit => true,
            AppUrl::MenuAll => false,
        }
    }
    
    pub fn need_duplicate_check(&self) -> bool {
        match self {
            AppUrl::UserLogin => false,
            AppUrl::OrderSubmit => true,
            AppUrl::MenuAll => false,
        }
    }
}
```

### 2. 使用客户端

```rust
use httpclient::{HttpClient, HttpClientConfig, AppUrl};
use serde_json::json;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let client = HttpClient::new(HttpClientConfig {
        base_url: "https://your-gateway.com".to_string(),
        client_id: Some("rust-client".to_string()),
        duplicate_submit_time_window: 3000,
    });
    
    // 直接使用enum，简洁优雅！
    let result = client.request(
        AppUrl::UserLogin,  // ✅ 类型安全
        Some(json!({
            "username": "user",
            "password": "pass"
        }))
    ).await?;
    
    println!("登录成功: {}", result);
    Ok(())
}
```

## 📋 功能特性

| 功能 | 状态 | 说明 |
|------|------|------|
| HTTP客户端 | ✅ | 基于reqwest |
| enum URL配置 | ✅ | Rust原生enum |
| 防重复提交 | ✅ | 双重防护 |
| ECC密钥交换 | 🚧 | 基础实现 |
| AES-GCM加密 | 🚧 | 基础实现 |
| 异步支持 | ✅ | async/await |
| 类型安全 | ✅ | 编译时检查 |

## 🆚 与TypeScript对比

### TypeScript（需要自定义枚举）

```typescript
// TypeScript没有强大的enum，需要自定义
export class Url extends Enum<Url> {
    constructor(
        objectName: string,  // 需要手动传递名称
        value: string,
        method: Method,
        needEncryption?: boolean,
        needDuplicateCheck?: boolean
    ) {
        super(objectName);
    }
}

export class AppUrl {
    public static readonly USER_LOGIN = new Url(
        'USER_LOGIN',  // 需要手动传递
        '/api/auth/login',
        Method.POST,
        true,
        false
    );
}

// 使用
await client.request(AppUrl.USER_LOGIN, { body: loginData });
```

### Rust（原生enum完美支持）

```rust
// Rust原生enum，无需额外类！
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AppUrl {
    UserLogin,
}

impl AppUrl {
    pub fn path(&self) -> &'static str {
        match self {
            AppUrl::UserLogin => "/api/auth/login",
        }
    }
    
    pub fn method(&self) -> Method {
        match self {
            AppUrl::UserLogin => Method::Post,
        }
    }
    
    pub fn need_encryption(&self) -> bool {
        match self {
            AppUrl::UserLogin => true,
        }
    }
}

// 使用
client.request(AppUrl::UserLogin, Some(login_data)).await?;
```

**优势对比**：

| 特性 | TypeScript | Rust |
|-----------|-----------|------|
| 需要自定义Enum基类 | ✅ 需要 | ❌ 不需要 |
| 手动传递名称 | ✅ 需要 | ❌ 通过name()方法获取 |
| 类型安全 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 代码简洁度 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 编译时检查 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 运行时开销 | 有 | 零开销 |

## 💡 Rust enum高级特性

### 1. 模式匹配（穷尽检查）

```rust
fn handle(url: AppUrl) {
    match url {
        AppUrl::UserLogin => println!("处理登录"),
        AppUrl::OrderSubmit => println!("处理订单"),
        AppUrl::MenuAll => println!("处理菜单"),
        // 如果遗漏了某个enum，编译器会报错！
    }
}
```

### 2. Copy trait（零开销复制）

```rust
// AppUrl实现了Copy trait，可以直接传值，无需引用
let url = AppUrl::UserLogin;
client.request(url, None).await?;  // 直接传值，无需&url

// 也可以克隆（虽然Copy trait已经足够）
let url2 = url;  // 自动复制，零开销
```

### 3. 使用impl添加方法

```rust
impl AppUrl {
    pub fn get_full_url(&self, base_url: &str) -> String {
        format!("{}{}", base_url.trim_end_matches('/'), self.path())
    }
    
    pub fn is_write_operation(&self) -> bool {
        matches!(self.method(), Method::Post | Method::Put | Method::Delete | Method::Patch)
    }
    
    pub fn name(&self) -> &'static str {
        match self {
            AppUrl::UserLogin => "USER_LOGIN",
            AppUrl::OrderSubmit => "ORDER_SUBMIT",
            // ...
        }
    }
}
```

### 4. 调试输出

```rust
let url = AppUrl::UserLogin;
println!("{:?}", url);  // Debug trait
println!("{}", url);     // Display trait
```

## 🎯 使用示例

### 登录示例

```rust
let login_data = json!({
    "username": "user123",
    "password": "password123"
});

#[derive(Deserialize)]
struct User {
    user_id: String,
    username: String,
    email: String,
}

match client.request::<serde_json::Value, User, Url>(Url::UserLogin, Some(login_data)).await {
    Ok(result) => {
        if result.is_success() {
            match result.extract_data() {
                Ok(user_data) => {
                    println!("登录成功: {:?}", user_data);
                    // 注意：token现在从响应头自动获取并保存，无需手动处理
                }
                Err(e) => {
                    println!("数据提取失败: {}", e);
                }
            }
        } else {
            println!("登录失败: {}", result.msg.as_deref().unwrap_or("未知错误"));
        }
    }
    Err(e) => {
        if e.to_string().contains("DUPLICATE_REQUEST") {
            println!("请求正在处理中");
        } else {
            println!("登录失败: {}", e);
        }
    }
}
```

### 订单提交示例

```rust
let order_data = json!({
    "items": vec![
        json!({"productId": "P001", "quantity": 2})
    ],
    "totalAmount": 199.98
});

match client.request(AppUrl::OrderSubmit, Some(order_data)).await {
    Ok(result) => {
        println!("订单提交成功: {}", result);
    }
    Err(e) => {
        if e.to_string().contains("DUPLICATE_REQUEST") {
            println!("检测到重复提交");
        } else {
            println!("订单提交失败: {}", e);
        }
    }
}
```

### 获取枚举信息

```rust
let url = AppUrl::UserLogin;
println!("名称: {}", url.name());
println!("路径: {}", url.path());
println!("方法: {}", url.method());
println!("需要加密: {}", url.need_encryption());
println!("需要防重复: {}", url.need_duplicate_check());
println!("是否写操作: {}", url.is_write_operation());
println!("完整URL: {}", url.get_full_url("https://api.example.com"));
```

## 📦 依赖说明

### 运行时依赖

本库需要以下外部依赖（通过Cargo.toml配置）：

| 依赖库 | 版本 | 用途 | 必需性 |
|--------|------|------|--------|
| **reqwest** | ^0.11 | HTTP客户端库 | ✅ 必需 |
| **serde** | ^1.0 | 序列化框架 | ✅ 必需（带derive特性） |
| **serde_json** | ^1.0 | JSON处理 | ✅ 必需 |
| **tokio** | ^1.0 | 异步运行时 | ✅ 必需（features = ["full"]） |

### 加密库依赖（可选，待完善）

目前Rust客户端的ECC加密实现需要完善，可选择以下加密库之一：

**选项1：使用ring（推荐，高性能）**
```toml
[dependencies]
ring = "0.17"
```

**选项2：使用openssl**
```toml
[dependencies]
openssl = "0.10"
openssl-sys = "0.9"
```

### 安装依赖

在项目根目录创建或编辑 `Cargo.toml`：

```toml
[package]
name = "your-project"
version = "0.1.0"
edition = "2021"

[dependencies]
# HTTP客户端
reqwest = { version = "0.11", features = ["json"] }

# 序列化
serde = { version = "1.0", features = ["derive"] }
serde_json = "1.0"

# 异步运行时
tokio = { version = "1", features = ["full"] }

# 如果使用ring加密库
# ring = "0.17"
```

然后运行：
```bash
cargo build
```

### 完整Cargo.toml示例

参见项目根目录的 [Cargo.toml](./Cargo.toml) 文件获取完整的依赖配置。

### 版本要求

- **Rust 1.70+**（推荐使用最新稳定版）
- **Cargo**（Rust包管理器，随Rust一起安装）
- **Edition**: 2021

## 🎉 总结

**Rust的enum是实现URL配置的最佳方案**：

1. ✅ **无需额外struct** - 原生enum功能完整
2. ✅ **类型安全** - 编译时检查
3. ✅ **零开销** - Copy trait，编译时展开
4. ✅ **代码简洁** - 比TypeScript简洁很多
5. ✅ **模式匹配** - match表达式，穷尽检查
6. ✅ **易于扩展** - impl块添加方法

**这就是Rust的优雅之处！** 🎨

## 📖 框架集成示例

### Actix-web框架集成

查看 `examples/actix-web/` 目录获取Actix-web框架集成示例：

- `main.rs` - Actix-web应用集成示例
- `README.md` - Actix-web集成说明

**包含内容**：
- ✅ 服务结构体封装
- ✅ web::Data集成
- ✅ 异步处理器示例
- ✅ 错误处理示例

**运行示例**：
```bash
cd examples/actix-web
cargo run
```

---

**版本**: 2.0  
**更新**: 2025-11-01

