# Atlas Richie Go HTTP Client (atlas-richie-gateway-go-client)

Go语言HTTP客户端，支持ECC+AES-GCM加密和防重复提交。

## 特性

- ✅ ECC+AES-GCM加密（使用crypto/ecdh和crypto/aes）
- ✅ 防重复提交
- ✅ 线程安全
- ✅ 跨平台（Windows/Linux/macOS）
- ✅ 零外部依赖（只使用标准库）

## 快速开始

### 1. 定义URL配置

```go
// app_url.go
package example

import httpclient "path/to/httpclient"

var (
    UserLogin = httpclient.NewUrl(
        "USER_LOGIN",
        "/api/auth/login",
        httpclient.POST,
        true,  // 加密
        false, // 不防重复
    )
    
    OrderSubmit = httpclient.NewUrl(
        "ORDER_SUBMIT",
        "/api/order/submit",
        httpclient.POST,
        true,  // 加密
        true,  // 防重复
    )
)
```

### 2. 使用客户端

```go
package main

import (
    "fmt"
    httpclient "path/to/httpclient"
    "path/to/example"
)

func main() {
    // 创建客户端
    client := httpclient.NewHttpClient(httpclient.HttpClientConfig{
        BaseUrl:  "https://your-gateway.com",
        ClientId: "my-go-app",
    })
    defer client.Cleanup()

    // 登录
    result, err := client.Request(example.UserLogin, &httpclient.RequestOptions{
        Body: map[string]string{
            "username": "user",
            "password": "pass",
        },
    })
    
    if err != nil {
        panic(err)
    }
    
    fmt.Printf("登录成功: %v\n", result)
}
```

## 错误处理

```go
result, err := client.Request(url, options)
if err != nil {
    switch err.Error() {
    case "DUPLICATE_REQUEST":
        fmt.Println("请求正在处理中")
    case "DUPLICATE_SUBMIT":
        fmt.Println("服务器检测到重复提交")
    default:
        fmt.Printf("请求失败: %v\n", err)
    }
}
```

## 📦 依赖说明

### 运行时依赖

**✅ 零外部依赖**，仅使用Go标准库：

| 标准库模块 | 用途 |
|-----------|------|
| `crypto/ecdh` | ECC密钥交换（ECDH） |
| `crypto/aes` | AES-GCM加密/解密 |
| `crypto/cipher` | 加密算法实现 |
| `crypto/md5` | MD5哈希（防重复提交） |
| `net/http` | HTTP客户端 |
| `encoding/json` | JSON序列化/反序列化 |
| `encoding/base64` | Base64编码/解码 |
| `sync` | 并发安全（Map、锁） |
| `strings` | 字符串处理 |
| `time` | 时间戳处理 |

**无需安装任何第三方包！**

### 安装

```bash
# 初始化Go模块（如果需要）
go mod init your-project

# 直接使用，无需 go get
```

### 版本要求

- **Go 1.18+**（推荐使用最新版本）

**注意**：`crypto/ecdh` 模块在 Go 1.20+ 中可用。如果使用较早版本，可能需要使用 `crypto/elliptic`。

## 编译

```bash
# Windows
GOOS=windows GOARCH=amd64 go build -o app.exe

# Linux
GOOS=linux GOARCH=amd64 go build -o app

# macOS
GOOS=darwin GOARCH=amd64 go build -o app
```

## 📖 框架集成示例

### Gin框架集成

查看 `example/gin/` 目录获取Gin框架集成示例：

- `main.go` - Gin应用集成示例
- `README.md` - Gin集成说明

**包含内容**：
- ✅ Gin中间件集成
- ✅ 服务类封装
- ✅ 路由处理器示例
- ✅ 错误处理示例

**运行示例**：
```bash
cd example/gin
go run main.go
```

