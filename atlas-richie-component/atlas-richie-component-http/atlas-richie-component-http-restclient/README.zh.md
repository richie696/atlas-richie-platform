# Atlas Richie HTTP RestClient (atlas-richie-component-http-restclient)

http 组件的 Spring `RestClient` Provider。基于 [`org.springframework.web.client.RestClient`](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)（Spring 6.1+）实现 `HttpClient`。通过 `platform.component.http.provider=rest_client` 激活。**复用业务侧提供的任何 Spring `RestClient.Builder` Bean**。

---

## 📖 目录

- [📖 概述](#📖-概述)
  - [本模块提供 vs 不提供](#本模块提供-vs-不提供)
- [🏗️ 架构与模块布局](#🏗️-架构与模块布局)
- [🚀 快速开始](#🚀-快速开始)
- [🔧 核心能力](#🔧-核心能力)
  - [1. `RestClientAdapter` —— `HttpClient` 实现](#1-restclientadapter-——-httpclient-实现)
  - [2. 单请求超时](#2-单请求超时)
  - [3. SSE](#3-sse)
  - [4. Strict SSL](#4-strict-ssl)
- [⚙️ 配置参考](#⚙️-配置参考)
- [🎯 最佳实践](#🎯-最佳实践)
- [⚠️ 已知限制](#⚠️-已知限制)
- [❓ 常见问题](#❓-常见问题)
  - [Q1：为什么 `strict-ssl=false` 没生效？](#q1：为什么-strict-ssl=false-没生效？)
  - [Q2：能把 `RestClient.Builder` 与非门面调用共享吗？](#q2：能把-restclientbuilder-与非门面调用共享吗？)
  - [Q3：为什么 multipart 抛 `Unsupported`？](#q3：为什么-multipart-抛-unsupported？)
  - [Q4：如何给所有请求加拦截器？](#q4：如何给所有请求加拦截器？)
  - [Q5：async / future 的线程模型？](#q5：async-/-future-的线程模型？)
  - [Q6：没有 `spring-boot-starter-web` 能用吗？](#q6：没有-spring-boot-starter-web-能用吗？)
- [📚 相关文档](#📚-相关文档)
---

## 📖 概述

| 项 | 值 |
|---|---|
| **坐标** | `com.richie.component:atlas-richie-component-http-restclient` |
| **激活配置** | `platform.component.http.provider=<provider>` |

### 本模块提供 vs 不提供

| ✅ 提供 | ❌ 不提供 |
|--------|---------|
| 包装 `RestClient` 的 `RestClientAdapter` | Provider 专属调优（超时 / 池 / TLS） |
| 复用任何 `@Bean RestClient.Builder` | Multipart 表单字段（上传请用 HttpClient5） |
| 单请求超时包装（`CompletableFuture.orTimeout`） | HTTP/2 控制 |
| `RestClientSseClient` SSE 实现 | 响应流式读取——body 一次性 `byte[]` |
| 复用平台 `JsonUtils`（Jackson 3） | |

## 🏗️ 架构与模块布局

```
atlas-richie-component-http-restclient
├── RestClientAdapter             实现 HttpClient；委托 RestClient
├── RestClientSseClient           通过原始响应打开 SSE
├── RestClientSseConnection       实现 SseConnection
└── config/
    └── HttpAutoConfiguration     包装 RestClient.Builder → RestClient → RestClientAdapter
```

## 🚀 快速开始

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-core</artifactId>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-restclient</artifactId>
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

### 1. `RestClientAdapter` —— `HttpClient` 实现

- `execute(HttpRequest)` 调用 `buildSpec(request).retrieve().toEntity(byte[].class)` 并映射为 `HttpResponse`。
- `async(...)` / `future(...)` 委托 `CompletableFuture.runAsync(...)`——同步包异步，非原生异步。
- `buildSpec(HttpRequest)` 构造 `RestClient.RequestBodySpec`；方法、URI、Header、`Content-Type`、Body 全部显式设置。

### 2. 单请求超时

```java
http.get(url).timeout(Duration.ofSeconds(5)).execute(User.class);
```

同步路径由 `HttpRequestSupport.executeWithTimeout(...)` 包装；`future(...)` 路径由 `CompletableFuture.orTimeout(...)` 强制超时。

### 3. SSE

用 `RestClient` 的原始响应流，按行喂入 `SseLineParser`。

### 4. Strict SSL

`platform.component.http.strict-ssl=false` 在本 Provider **未接线**。依赖你提供的 `RestClient.Builder` 上的 SSL 配置。

## ⚙️ 配置参考

本 Provider **没有**专属配置。通过你的 `RestClient.Builder` 自定义：

```java
@Bean
public RestClient.Builder customRestClientBuilder() {
    return RestClient.builder()
                     .baseUrl("https://api.example.com")
                     .defaultHeader("X-Custom", "value");
}
```

## 🎯 最佳实践

1. **复用自定义 `RestClient.Builder`**：base URL、默认 Header、拦截器都自动生效。
2. **超时通过门面控制**：RestClient 本身没有单请求超时；门面用 `CompletableFuture.orTimeout` 实现。
3. **上传选其他 Provider**：RestClient + 本 Adapter 不实现 multipart。
4. **与 Spring Boot 标准功能组合**：直接用 Spring 原生 `RestClient.Builder` 模式即可。

## ⚠️ 已知限制

| 限制 | 影响 | 临时方案 |
|------|------|---------|
| **不支持 multipart** | 门面 `multipart()` 不可用 | 切 `http_client_5` Provider |
| **Async 包装同步** | 每个在途调用占用 common-pool 线程 | 低并发可接受；高并发切 OkHttp / JDK |
| **无原生 HTTP/2 控制** | 取决于底层 `ClientHttpRequestFactory` | 用 `JdkClientHttpRequestFactory` 获得 HTTP/2 |
| **`strictSsl=false` 未接线** | trust-all 需自行配置 | 在 `RestClient.Builder` 上手动配置 |
| **Body 一次性 `byte[]`** | 大下载占内存 | 用 SSE 流 |

## ❓ 常见问题

### Q1：为什么 `strict-ssl=false` 没生效？

本 Provider 未注入 trust-all `SSLContext`。在 `RestClient.Builder` 上配置。

### Q2：能把 `RestClient.Builder` 与非门面调用共享吗？

可以——`RestClient.Builder` Bean 是你的。

### Q3：为什么 multipart 抛 `Unsupported`？

RestClient Provider 未实现 `HttpRequest.multipart(...)`。文件上传用 `http_client_5`。

### Q4：如何给所有请求加拦截器？

通过 `RestClient.Builder`：

```java
@Bean
public RestClient.Builder customRestClientBuilder() {
    return RestClient.builder()
                     .requestInterceptor((request, body, execution) -> {
                         request.getHeaders().add("X-Trace-Id", TraceContext.traceId());
                         return execution.execute(request, body);
                     });
}
```

### Q5：async / future 的线程模型？

`CompletableFuture.runAsync(...)`——运行在 `ForkJoinPool.commonPool()`。需要虚拟线程友好异步，切 `jdk` Provider。

### Q6：没有 `spring-boot-starter-web` 能用吗？

不能——`RestClient` 在 `org.springframework.web.client.RestClient`，由 `spring-boot-starter-web` 提供。本 Provider 用 `@ConditionalOnClass(RestClient.class)`，缺类则不创建 Bean。

## 📚 相关文档

- **父组件** — [`../README.zh.md`](../README.zh.md)
- **门面 core** — [`../atlas-richie-component-http-core/README.zh.md`](../atlas-richie-component-http-core/README.zh.md)
- **其他 Provider** — [OkHttp](../atlas-richie-component-http-okhttp/README.zh.md) · [HttpClient5](../atlas-richie-component-http-httpclient5/README.zh.md) · [JDK](../atlas-richie-component-http-jdk/README.zh.md) · [RestClient](../atlas-richie-component-http-restclient/README.zh.md)

---

**atlas-richie-component-http-restclient** 🚀
