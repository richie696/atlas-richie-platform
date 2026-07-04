# Atlas Richie Gin Integration Example (atlas-richie-gateway-gin-example)

展示如何在Gin框架应用中使用HTTP客户端库。

## 📦 安装依赖

```bash
go get github.com/gin-gonic/gin
```

## 🚀 使用方法

### 1. 运行示例

```bash
cd example/gin
go run main.go
```

### 2. 设置环境变量（可选）

```bash
export GATEWAY_URL=https://your-gateway.com
export CLIENT_ID=gin-app
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

### 方式1: 服务类封装

```go
type ApiService struct {
    client *httpclient.HttpClient
}

func (s *ApiService) Login(username, password string) (map[string]interface{}, error) {
    return s.client.Request(example.UserLogin, &httpclient.RequestOptions{
        Body: map[string]interface{}{
            "username": username,
            "password": password,
        },
    })
}
```

### 方式2: Gin中间件

```go
func HttpClientMiddleware(client *httpclient.HttpClient) gin.HandlerFunc {
    return func(c *gin.Context) {
        c.Set("httpClient", client)
        c.Next()
    }
}

r.Use(HttpClientMiddleware(client))

r.POST("/api/endpoint", func(c *gin.Context) {
    client := c.MustGet("httpClient").(*httpclient.HttpClient)
    result, _ := client.Request(example.UserLogin, ...)
})
```

### 方式3: 单例模式

```go
// 在应用启动时创建客户端
client := httpclient.NewHttpClient(httpclient.HttpClientConfig{...})
defer client.Cleanup()

// 在路由中使用
r.POST("/api/endpoint", func(c *gin.Context) {
    result, _ := client.Request(example.UserLogin, ...)
})
```

## ⚠️ 注意事项

1. **单例模式**: 建议在应用启动时创建一个HTTP客户端实例，在多个请求间共享
2. **资源清理**: 使用 `defer client.Cleanup()` 确保资源正确释放
3. **错误处理**: 正确处理 `DUPLICATE_REQUEST` 和 `DUPLICATE_SUBMIT` 错误
4. **并发安全**: Go的HTTP客户端实例是并发安全的，可以在多个goroutine中使用

