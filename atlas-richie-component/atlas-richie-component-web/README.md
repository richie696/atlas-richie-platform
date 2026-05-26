# Richie Component Web

## 概述

`richie-component-web` 是Richie平台 Web 组件，提供 Web 应用的基础能力，包括 CORS 配置、国际化支持、异常处理、WebSocket、SSE、Undertow 配置等。

## 核心特性

- ✅ **CORS 配置** - 支持跨域资源共享配置
- ✅ **国际化支持** - 支持多语言和时区处理
- ✅ **异常处理** - 统一异常处理和错误响应
- ✅ **WebSocket 支持** - 支持 WebSocket 通信
- ✅ **SSE 支持** - 支持服务器发送事件
- ✅ **Undertow 配置** - 支持 Undertow Web 服务器配置
- ✅ **消息转换** - 统一消息体类型转换处理

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-web</artifactId>
    <version>${rydeen.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    web:
      # 支持的语言标签（IETF BCP 47 标准）
      supportedLanguageTags:
        - zh-CN
        - en-US
        - ja-JP
        - ko-KR
      # 登录地址清单
      loginUrls:
        - /api/auth/login
        - /api/auth/oauth2/callback
      # 令牌签发秘钥
      tokenSecret: your-token-secret
      # 令牌有效时长（秒）
      tokenExpirationDate: 86400
      # 跨域配置
      cors:
        # 是否启用CORS（默认：false）
        enable: true
        # 路径模式（默认：/**）
        pathPattern: /**
        # 允许的来源（可选）
        allowedOrigin:
          - http://localhost:3000
          - https://example.com
        # 允许的来源模式（默认：["*"]）
        allowedOriginPatterns:
          - "*"
        # 允许的HTTP方法（默认：["GET", "POST", "PUT", "DELETE", "OPTIONS"]）
        allowedMethods:
          - GET
          - POST
          - PUT
          - DELETE
          - OPTIONS
        # 允许的请求头（默认：["*"]）
        allowedHeaders:
          - "*"
        # 暴露的响应头（可选）
        exposedHeaders:
          - X-Request-Id
          - X-Trace-Id
        # 是否允许凭证（默认：true）
        allowCredentials: true
        # 是否允许专用网络访问（默认：false）
        allowPrivateNetwork: false
        # 预检请求缓存时间（秒，默认：3600）
        maxAge: 3600
```

### 3. 使用

组件会自动配置，无需额外代码。

## 配置说明

### CORS 配置

CORS（跨域资源共享）配置用于控制跨域请求：

```yaml
platform:
  component:
    web:
      cors:
        enable: true  # 是否启用CORS
        pathPattern: /**  # 路径模式
        allowedOriginPatterns: ["*"]  # 允许的来源模式
        allowedMethods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"]  # 允许的HTTP方法
        allowedHeaders: ["*"]  # 允许的请求头
        allowCredentials: true  # 是否允许凭证
        allowPrivateNetwork: false  # 是否允许专用网络访问
        maxAge: 3600  # 预检请求缓存时间（秒）
```

### 国际化配置

国际化配置支持多语言和时区处理：

```yaml
platform:
  component:
    web:
      # 支持的语言标签（IETF BCP 47 标准）
      supportedLanguageTags:
        - zh-CN  # 简体中文
        - en-US  # 美式英语
        - ja-JP  # 日语
        - ko-KR  # 韩语
```

### 令牌配置

令牌配置用于 JWT 令牌管理：

```yaml
platform:
  component:
    web:
      # 令牌签发秘钥
      tokenSecret: your-token-secret
      # 令牌有效时长（秒）
      tokenExpirationDate: 86400  # 24小时
```

### Undertow 配置

Undertow Web 服务器配置：

```yaml
server:
  undertow:
    # 线程配置
    threads:
      io: 4  # IO线程数
      worker: 64  # 工作线程数
    # 缓冲区配置
    buffer-size: 1024  # 缓冲区大小（字节）
    # 直接缓冲区配置
    direct-buffers: true  # 是否使用直接缓冲区
```

## 功能特性

### 1. CORS 支持

支持跨域资源共享配置，可以灵活控制跨域请求：

- 支持来源模式匹配
- 支持凭证传递
- 支持专用网络访问
- 支持预检请求缓存

### 2. 国际化支持

支持多语言和时区处理：

- 支持 IETF BCP 47 标准语言标签
- 支持 Accept-Language 请求头解析
- 支持时区处理
- 支持 Cookie 和请求头语言切换

### 3. 异常处理

统一异常处理和错误响应：

- 全局异常处理
- 统一错误响应格式
- 支持自定义异常处理

### 4. WebSocket 支持

支持 WebSocket 通信：

- 自动配置 WebSocket
- 支持消息转换
- 支持连接管理

### 5. SSE 支持

支持服务器发送事件（SSE）：

- 自动配置 SSE
- 支持事件推送
- 支持连接管理

### 6. Undertow 配置

支持 Undertow Web 服务器配置：

- 线程配置
- 缓冲区配置
- 性能优化

## 最佳实践

1. **CORS 配置**
   - 生产环境不要使用 `allowedOriginPatterns: ["*"]`
   - 明确指定允许的来源
   - 启用 `allowCredentials` 时，不能使用 `*` 作为来源

2. **国际化配置**
   - 使用 IETF BCP 47 标准语言标签
   - 支持的语言标签要明确
   - 考虑时区处理

3. **令牌配置**
   - 使用强密钥
   - 设置合理的过期时间
   - 定期轮换密钥

4. **Undertow 配置**
   - 根据服务器资源调整线程数
   - 使用直接缓冲区提高性能
   - 根据业务场景调整缓冲区大小

## 常见问题

### Q: 如何配置 CORS？

A: 在配置文件中设置 `platform.component.web.cors.enable: true`，并配置相应的 CORS 参数。

### Q: 如何支持多语言？

A: 在配置文件中设置 `platform.component.web.supportedLanguageTags`，并在请求头中添加 `Accept-Language`。

### Q: 如何配置 Undertow？

A: 在配置文件中设置 `server.undertow` 相关参数，如线程数、缓冲区大小等。

### Q: 如何支持 WebSocket？

A: 组件会自动配置 WebSocket，无需额外配置。只需在代码中使用 `@ServerEndpoint` 注解即可。

### Q: 如何支持 SSE？

A: 组件会自动配置 SSE，无需额外配置。使用 `SseManager` 可以管理 SSE 连接。

## 相关文档

- [Spring Web 官方文档](https://docs.spring.io/spring-framework/reference/web/webmvc.html)
- [Undertow 官方文档](https://undertow.io/)
- [WebSocket 官方文档](https://docs.spring.io/spring-framework/reference/web/websocket.html)

