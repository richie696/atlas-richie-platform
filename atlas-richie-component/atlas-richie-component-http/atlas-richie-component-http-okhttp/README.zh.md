# Atlas Richie HTTP OkHttp (atlas-richie-component-http-okhttp)

http 组件的**默认 Provider**。基于 [OkHttp 4](https://square.github.io/okhttp/) + `okhttp-sse` + `logging-interceptor` 实现 `HttpClient`。通过 `platform.component.http.provider=okhttp`（默认）激活。

本模块注册：
- 单例 `OkHttpClient` Bean（含连接池、Dispatcher、超时、可选缓存、可选 trust-all SSL）
- 实现 `HttpClient` 的 `OkHttpAdapter` Bean

---

## 📖 目录

- [📖 概述](#📖-概述)
  - [本模块提供 vs 不提供](#本模块提供-vs-不提供)
- [🏗️ 架构与模块布局](#🏗️-架构与模块布局)
- [🚀 快速开始](#🚀-快速开始)
- [🔧 核心能力](#🔧-核心能力)
  - [1. `OkHttpAdapter` —— `HttpClient` 实现](#1-okhttpadapter-——-httpclient-实现)
  - [2. `OkHttpSseClient` —— SSE](#2-okhttpsseclient-——-sse)
  - [3. SSL 处理](#3-ssl-处理)
  - [4. 日志级别](#4-日志级别)
- [⚙️ 配置参考](#⚙️-配置参考)
- [🎯 最佳实践](#🎯-最佳实践)
- [⚠️ 已知限制](#⚠️-已知限制)
- [❓ 常见问题](#❓-常见问题)
  - [Q1：为什么我自定义的 `OkHttpClient` Bean 没生效？](#q1：为什么我自定义的-okhttpclient-bean-没生效？)
  - [Q2：如何追加自定义 OkHttp 拦截器？](#q2：如何追加自定义-okhttp-拦截器？)
  - [Q3：连接池容量看着太小](#q3：连接池容量看着太小)
  - [Q4：SSE 短暂断网会丢事件](#q4：sse-短暂断网会丢事件)
  - [Q5：`level=BODY` 日志很大](#q5：level=body-日志很大)
  - [Q6：多个 Spring Boot 应用能共享 `HttpClient` 吗？](#q6：多个-spring-boot-应用能共享-httpclient-吗？)
- [📚 相关文档](#📚-相关文档)
---

## 📖 概述

| 项 | 值 |
|---|---|
| **坐标** | `com.richie.component:atlas-richie-component-http-okhttp` |
| **激活配置** | `platform.component.http.provider=<provider>` |

### 本模块提供 vs 不提供

| ✅ 提供 | ❌ 不提供 |
|--------|---------|
| `OkHttpClient` 单例 Bean（生产级调优） | 自定义拦截器链（在 Bean 上自行追加） |
| 连接池（大小 = `Runtime.availableProcessors()`，空闲保留时长可配） | Multipart 多文件（当前单文件） |
| `Dispatcher` 全局 + 单主机并发上限 | 按路由粒度的 mTLS |
| 内置 `HttpLoggingInterceptor`，详细程度 `NONE` / `BASIC` / `HEADERS` / `BODY` | 响应流式读取——body 一次性加载为 `byte[]` |
| 响应缓存（仅 GET，可配大小与路径） | |
| 可选 `strictSsl=false` → trust-all 并输出 WARN | |
| 基于 `okhttp-sse` 的原生 SSE（`OkHttpSseClient`） | |

## 🏗️ 架构与模块布局

```
atlas-richie-component-http-okhttp
├── OkHttpAdapter                  实现 HttpClient；委托 OkHttpClient
├── OkHttpSseClient                通过 okhttp-sse 打开 SSE 流
├── OkHttpSseConnection            实现 SseConnection
└── config/
    ├── HttpProperties             @ConfigurationProperties("platform.component.http.okhttp")
    └── HttpAutoConfiguration      构建 OkHttpClient + OkHttpAdapter
```

## 🚀 快速开始

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-core</artifactId>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-okhttp</artifactId>
</dependency>
```

```yaml
platform:
  component:
    http:
      provider: <provider>
```

通过门面调用：

```java
import com.richie.component.http.core.HttpClient;

@Service
@RequiredArgsConstructor
public class UserService {
    private final HttpClient http;
    public User getById(String id) {
        return http.get("https://api/users/{id}", id).execute(User.class);
    }
}
```

完整 API 见 [`http-core`](../atlas-richie-component-http-core/README.zh.md)。

## 🔧 核心能力

### 1. `OkHttpAdapter` —— `HttpClient` 实现

实现 `HttpClient` 的所有方法；`execute()` 把 body 一次性读为 `byte[]` 并释放连接；`async()` / `future()` 提交到 OkHttp `Dispatcher`。本次请求的 `timeout()` 通过派生 client（覆盖 `readTimeout` / `callTimeout`）实现，不修改全局单例。

### 2. `OkHttpSseClient` —— SSE

基于 `okhttp-sse` 的 `EventSourceListener`，事件分发到你的 `SseListener`。**无自动重连**——如需在 `onFailure` 中自行实现。

### 3. SSL 处理

```yaml
platform:
  component:
    http:
      strict-ssl: false   # 信任所有证书；启动输出 WARN
```

### 4. 日志级别

```yaml
platform:
  component:
    http:
      okhttp:
        level: BASIC   # NONE / BASIC / HEADERS / BODY
```

推荐：**生产**用 `BASIC`，**本地开发**才用 `BODY`。

## ⚙️ 配置参考

所有属性位于 `platform.component.http.okhttp.*`：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `read-timeout` / `-time-unit` | int / TimeUnit | `5` / `SECONDS` | 读超时 |
| `write-timeout` / `-time-unit` | int / TimeUnit | `5` / `SECONDS` | 写超时 |
| `connect-timeout` / `-time-unit` | int / TimeUnit | `5` / `SECONDS` | TCP/TLS 握手超时 |
| `call-timeout` / `-time-unit` | int / TimeUnit | `15` / `SECONDS` | 端到端超时 |
| `level` | `NONE` / `BASIC` / `HEADERS` / `BODY` | `BODY` | `HttpLoggingInterceptor` 详细程度 |
| `enable-cache` / `cache-path` / `cache-size` (MB) | boolean / String / int | `false` / `/opt/okhttp3/cache/` / `100` | GET 响应缓存 |
| `max-requests` / `max-requests-per-host` | int / int | `250` / `25` | Dispatcher 并发上限 |
| `keep-alive-duration` / `-time-unit` | long / TimeUnit | `5` / `MINUTES` | 连接池空闲保留时长 |

**SSL**：`platform.component.http.strict-ssl=false` 启用 trust-all 并输出 WARN。

## 🎯 最佳实践

1. **分层调优超时**：connect / read / write / call 各自独立调参。
2. **生产用 `BASIC`**：`BODY` 会泄露凭证/token/PII。
3. **缓存仅当数据能容忍陈旧时启用**：OkHttp 按上游 `Cache-Control` 生效。
4. **配置 `maxRequestsPerHost` 保护下游**：对单一服务端设上限。
5. **把 `OkHttpClient` Bean 当单例**：不要自行 `new OkHttpClient.Builder()`。
6. **SSE 重连由你实现**：在 `SseListener.onFailure(...)` 中。

## ⚠️ 已知限制

| 限制 | 影响 | 临时方案 |
|------|------|---------|
| **Multipart 仅单文件** | 多文件上传需循环 | 循环调用；或切到 `http_client_5` |
| **Body 一次性 `byte[]`** | 大下载占内存 | 用 SSE 流；等流式 API |
| **`strictSsl=false` 静默 trust-all** | 生产配错风险 | 检查启动 WARN |
| **无内置重试 / 熔断** | 瞬时失败直接抛出 | 与 `atlas-richie-component-microservice` (microservice has no ZH README) 组合 |
| **`level=BODY` 会泄露凭证** | token / cookie / PII 进入日志 | 生产用 `BASIC`；调试路径配合脱敏组件 |

## ❓ 常见问题

### Q1：为什么我自定义的 `OkHttpClient` Bean 没生效？

本组件通过 `HttpAutoConfiguration#httpComponent` 注册了 `OkHttpClient` Bean。要自定义：优先调整 `platform.component.http.okhttp.*` 属性；或自行声明 `@Bean @Primary OkHttpClient` 覆盖。

### Q2：如何追加自定义 OkHttp 拦截器？

自行声明 `@Bean @Primary OkHttpClient`，在 builder 上 `.addInterceptor(...)`。

### Q3：连接池容量看着太小

调整 `max-requests` 和 `max-requests-per-host`。OkHttp 池容量独立于 Dispatcher——默认 `Runtime.availableProcessors()`。

### Q4：SSE 短暂断网会丢事件

OkHttp 的 SSE 不自动重连。在 `SseListener.onFailure(...)` 中自行实现；若存在，遵循最后一条 `SseEvent.retry()`。

### Q5：`level=BODY` 日志很大

切到 `BASIC`（生产）或 `HEADERS`（调试）。深度 body 检查请走 [`atlas-richie-component-desensitize-logging`](../../atlas-richie-component-desensitize/atlas-richie-component-desensitize-logging/README.zh.md)。

### Q6：多个 Spring Boot 应用能共享 `HttpClient` 吗？

可以。在共享库里声明，让每个应用分别依赖 `core` + `okhttp`。

## 📚 相关文档

- **父组件** — [`../README.zh.md`](../README.zh.md)
- **门面 core** — [`../atlas-richie-component-http-core/README.zh.md`](../atlas-richie-component-http-core/README.zh.md)
- **其他 Provider** — [OkHttp](../atlas-richie-component-http-okhttp/README.zh.md) · [HttpClient5](../atlas-richie-component-http-httpclient5/README.zh.md) · [JDK](../atlas-richie-component-http-jdk/README.zh.md) · [RestClient](../atlas-richie-component-http-restclient/README.zh.md)

---

**atlas-richie-component-http-okhttp** 🚀
