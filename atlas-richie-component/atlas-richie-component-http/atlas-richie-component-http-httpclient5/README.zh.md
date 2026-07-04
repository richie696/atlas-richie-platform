# Atlas Richie HTTP HttpClient5 (atlas-richie-component-http-httpclient5)

http 组件的 Apache HttpClient 5 Provider。基于 `org.apache.httpcomponents.client5:httpclient5` 与经典 `PoolingHttpClientConnectionManager` 实现 `HttpClient`。通过 `platform.component.http.provider=http_client_5` 激活。

**4 个 Provider 中 multipart 支持最成熟**的就是这一个。

---

## 📖 目录

- [📖 概述](#📖-概述)
  - [本模块提供 vs 不提供](#本模块提供-vs-不提供)
- [🏗️ 架构与模块布局](#🏗️-架构与模块布局)
- [🚀 快速开始](#🚀-快速开始)
- [🔧 核心能力](#🔧-核心能力)
  - [1. `HttpClient5Adapter` —— `HttpClient` 实现](#1-httpclient5adapter-——-httpclient-实现)
  - [2. `HttpClient5SseClient` —— SSE](#2-httpclient5sseclient-——-sse)
- [⚙️ 配置参考](#⚙️-配置参考)
- [🎯 最佳实践](#🎯-最佳实践)
- [⚠️ 已知限制](#⚠️-已知限制)
- [❓ 常见问题](#❓-常见问题)
  - [Q1：为什么 `strictSsl=false` 没生效？](#q1：为什么-strictssl=false-没生效？)
  - [Q2：为什么命中 `ConnectTimeoutException` 而不是预期的 `responseTimeout`？](#q2：为什么命中-connecttimeoutexception-而不是预期的-responsetimeout？)
  - [Q3：如何挂拦截器？](#q3：如何挂拦截器？)
  - [Q4：跨线程安全吗？](#q4：跨线程安全吗？)
  - [Q5：HTTP/2 选项？](#q5：http/2-选项？)
- [📚 相关文档](#📚-相关文档)
---

## 📖 概述

| 项 | 值 |
|---|---|
| **坐标** | `com.richie.component:atlas-richie-component-http-httpclient5` |
| **激活配置** | `platform.component.http.provider=<provider>` |

### 本模块提供 vs 不提供

| ✅ 提供 | ❌ 不提供 |
|--------|---------|
| `CloseableHttpClient` 单例 + `PoolingHttpClientConnectionManager` | HTTP/2 多路复用流 |
| TLS 1.2 / 1.3（默认） | `strictSsl=false` trust-all 开关（继承平台 SSLContext） |
| 连接池总容量 + 单路由上限 | 响应流式读取——body 一次性 `byte[]` |
| **最成熟的 multipart**（用 `MultipartEntityBuilder`） | 真正的异步——async 通过 `CompletableFuture.runAsync` 包装同步 |
| 每请求 `RequestConfig`（超时覆盖） | |
| `HttpClient5SseClient` SSE 实现 | |

## 🏗️ 架构与模块布局

```
atlas-richie-component-http-httpclient5
├── HttpClient5Adapter               实现 HttpClient；委托 CloseableHttpClient
├── HttpClient5SseClient             通过原始响应流打开 SSE
├── HttpClient5SseConnection         实现 SseConnection；用 SseLineParser
└── config/
    ├── HttpProperties               @ConfigurationProperties("platform.component.http.httpclient5")
    └── HttpAutoConfiguration        构建 PoolingHttpClientConnectionManager + CloseableHttpClient + HttpClient5Adapter
```

## 🚀 快速开始

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-core</artifactId>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-httpclient5</artifactId>
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

### 1. `HttpClient5Adapter` —— `HttpClient` 实现

- `execute(HttpRequest)` 用 `httpClient.execute(buildRequest, this::buildResponse)`——Response Handler 自动归还连接到池。
- `bodyEntity(HttpRequest)`：
  - `multipart` 已设 → `MultipartEntityBuilder.addBinaryBody(...)`。
  - 否则 → `ByteArrayEntity`（携带配置的 mime）；**空 body 不设置 entity**，避免 GET/DELETE 强制带 body。

### 2. `HttpClient5SseClient` —— SSE

HttpClient5 没有原生 SSE，本模块把响应体当流读取，按行喂入 [`SseLineParser`](../atlas-richie-component-http-core/README.zh.md#4-sse--全协议支持)，解析后的事件投递到 `SseListener`。

## ⚙️ 配置参考

所有属性位于 `platform.component.http.httpclient5.*`：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `connection-request-timeout` / `-time-unit` | int / TimeUnit | `5` / `SECONDS` | 从连接池取连接的超时 |
| `response-timeout` / `-time-unit` | int / TimeUnit | `5` / `SECONDS` | 等待服务端响应的超时 |
| `max-total` | int | `250` | 连接池总连接数 |
| `default-max-per-route` | int | `25` | 单路由最大连接数 |

## 🎯 最佳实践

1. **按并发调优池**：`max-total: 500`、`default-max-per-route: 50`。
2. **文件上传首选本 Provider**：multipart 最成熟。
3. **`multipart()` 传入的 `InputStream` 由调用方关闭**：门面**不**自动关闭。
4. **用 `timeout()` 调长慢上游**：单请求 `RequestConfig` 不影响全局客户端。
5. **不要手动关闭 `CloseableHttpClient` Bean**：由 Spring 管理生命周期。

## ⚠️ 已知限制

| 限制 | 影响 | 临时方案 |
|------|------|---------|
| **默认无 HTTP/2** | 单连接单 host，存在队头阻塞 | 通过自定义 `HttpClientBuilder` 设置 `HttpVersionPolicy.NEGOTIATE` |
| **Async 包装同步**（`runAsync(ForkJoinPool.commonPool())`） | 占用 common-pool 线程 | 真正异步需求切 OkHttp / JDK Provider |
| **Body 一次性 `byte[]`** | 大下载占内存 | 等流式 API |
| **`strictSsl=false` 未生效** | trust-all 未自动启用 | 在自定义 `HttpClientBuilder` 上手动配置 |

## ❓ 常见问题

### Q1：为什么 `strictSsl=false` 没生效？

本 Provider 未注入 trust-all `SSLContext`。需要时覆盖 `CloseableHttpClient` Bean。

### Q2：为什么命中 `ConnectTimeoutException` 而不是预期的 `responseTimeout`？

HttpClient5 区分**从池取连接**与**等待响应**。两者都要调。

### Q3：如何挂拦截器？

覆盖 `@Primary CloseableHttpClient` Bean，在 `HttpClientBuilder` 上 `.addInterceptorFirst(...)`。

### Q4：跨线程安全吗？

是——`CloseableHttpClient` 线程安全。

### Q5：HTTP/2 选项？

通过 `setVersionPolicy(HttpVersionPolicy.NEGOTIATE)` 支持，但**非**默认。若 HTTP/2 是硬性需求，切到 OkHttp 或 JDK Provider。

## 📚 相关文档

- **父组件** — [`../README.zh.md`](../README.zh.md)
- **门面 core** — [`../atlas-richie-component-http-core/README.zh.md`](../atlas-richie-component-http-core/README.zh.md)
- **其他 Provider** — [OkHttp](../atlas-richie-component-http-okhttp/README.zh.md) · [HttpClient5](../atlas-richie-component-http-httpclient5/README.zh.md) · [JDK](../atlas-richie-component-http-jdk/README.zh.md) · [RestClient](../atlas-richie-component-http-restclient/README.zh.md)

---

**atlas-richie-component-http-httpclient5** 🚀
