# Atlas Richie Actix-web Integration Example (atlas-richie-gateway-actix-example)

展示如何在Actix-web框架应用中使用HTTP客户端库。

## 📦 安装依赖

在项目的 `Cargo.toml` 中添加依赖：

```toml
[dependencies]
httpclient = { path = "../.." }  # 或使用crate名称
actix-web = "4.4"
actix-rt = "2.9"
serde = { version = "1.0", features = ["derive"] }
serde_json = "1.0"
tokio = { version = "1", features = ["full"] }
```

## 🚀 使用方法

### 1. 运行示例

```bash
cd examples/actix-web
cargo run
```

### 2. 设置环境变量（可选）

```bash
export GATEWAY_URL=https://your-gateway.com
export CLIENT_ID=actix-web-app
export PORT=8080
```

### 3. 测试API

```bash
# 登录
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"pass"}'

# 提交订单
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"items":[{"productId":"P001","quantity":2}],"totalAmount":199.98}'

# 获取菜单
curl http://localhost:8080/api/menu

# 获取用户信息
curl http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer your-token"
```

## 📚 集成方式

### 方式1: 服务结构体封装

```rust
pub struct ApiService {
    client: Arc<HttpClient>,
}

impl ApiService {
    pub async fn login(&self, username: String, password: String) -> Result<Value, Error> {
        self.client.request::<Value, Url>(Url::UserLogin, Some(json!({...}))).await
    }
}
```

### 方式2: 使用web::Data

```rust
// 创建服务和客户端数据
let api_service = web::Data::new(ApiService::new(client.clone()));
let client_data = web::Data::from(client);

App::new()
    .app_data(api_service)
    .app_data(client_data)
    .service(web::resource("/api/login").route(web::post().to(login_handler)))
```

### 方式3: 在处理器中直接使用

```rust
async fn custom_handler(
    req: web::Json<Value>,
    client: web::Data<Arc<HttpClient>>,
) -> Result<HttpResponse> {
    let result = client.request::<Value, Url>(Url::UserLogin, Some(req.into_inner())).await?;
    Ok(HttpResponse::Ok().json(result))
}
```

## ⚠️ 注意事项

1. **单例模式**: 使用 `Arc<HttpClient>` 在多个请求间共享客户端实例
2. **并发安全**: Rust的 `Arc` 和 `RwLock` 确保客户端的并发安全
3. **错误处理**: 正确处理 `DUPLICATE_REQUEST` 和 `DUPLICATE_SUBMIT` 错误
4. **异步处理**: 确保处理器函数是 `async` 的，并使用 `.await` 调用客户端方法

