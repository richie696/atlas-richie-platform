# Atlas Richie HTTP JDK (atlas-richie-component-http-jdk)

http 组件的 **JDK 原生 Provider**。基于 [`java.net.http.HttpClient`](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html)（JDK 11+）实现 `HttpClient`。通过 `platform.component.http.provider=jdk` 激活。**零三方依赖**——适合无法引入外部 HTTP 库的环境。

---

## 📖 目录

- [📖 概述](#📖-概述)
  - [本模块提供 vs 不提供](#本模块提供-vs-不提供)
- [🏗️ 架构与模块布局](#🏗️-架构与模块布局)
- [🚀 快速开始](#🚀-快速开始)
- [🔧 核心能力](#🔧-核心能力)
  - [1. `JdkHttpAdapter` —— `HttpClient` 实现](#1-jdkhttpadapter-——-httpclient-实现)
  - [2. `JdkSseClient` —— SSE](#2-jdksseclient-——-sse)
- [⚙️ 配置参考](#⚙️-配置参考)
- [🎯 最佳实践](#🎯-最佳实践)
- [⚠️ 已知限制](#⚠️-已知限制)
- [❓ 常见问题](#❓-常见问题)
  - [Q1：JDK Provider 默认用 HTTP/2 吗？](#q1：jdk-provider-默认用-http/2-吗？)
  - [Q2：连接失败为何要等很久？](#q2：连接失败为何要等很久？)
  - [Q3：异步虚拟线程真的有用吗？](#q3：异步虚拟线程真的有用吗？)
  - [Q4：如何禁用 HTTP/2？](#q4：如何禁用-http/2？)
  - [Q5：代理没生效？](#q5：代理没生效？)
  - [Q6：`strictSsl=false` 生效吗？](#q6：strictssl=false-生效吗？)
- [📚 相关文档](#📚-相关文档)
---

## 📖 概述

| 项 | 值 |
|---|---|
| **坐标** | `com.richie.component:atlas-richie-component-http-jdk` |
| **激活配置** | `platform.component.http.provider=<provider>` |

### 本模块提供 vs 不提供

| ✅ 提供 | ❌ 不提供 |
|--------|---------|
| JDK 11+ 原生 `java.net.http.HttpClient`（HTTP/1.1 + HTTP/2） | 自定义连接池（由 JDK 管理） |
| 虚拟线程友好的异步执行器 | trust-all `strictSsl=false` 任意 `SSLContext` 注入（用 JDK `SSLParameters`） |
| HTTP/2 优先级 + 并发流数 | Multipart（单文件可走原始 body；完整 multipart 表单字段未暴露） |
| 可选代理（`proxyHost` + `proxyPort`） | 响应流式读取——body 一次性 `byte[]` |
| `strictSsl=false` → trust-all 并 WARN | |
| `JdkSseClient` SSE 实现 | |

## 🏗️ 架构与模块布局

```
atlas-richie-component-http-jdk
├── JdkHttpAdapter               实现 HttpClient；委托 java.net.http.HttpClient
├── JdkSseClient                 通过 BodyHandlers.ofInputStream 打开 SSE
├── JdkSseConnection             实现 SseConnection
└── config/
    ├── HttpProperties           @ConfigurationProperties("platform.component.http.jdk")
    └── HttpAutoConfiguration    构建 java.net.http.HttpClient + JdkHttpAdapter
```

## 🚀 快速开始

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-core</artifactId>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-jdk</artifactId>
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

### 1. `JdkHttpAdapter` —— `HttpClient` 实现

- `execute(HttpRequest)` 用 `httpClient.send(buildJdkRequest, BodyHandlers.ofByteArray())`——body 一次性读为 `byte[]`。
- `async(...)` / `future(...)` 委托 `httpClient.sendAsync(...)`——真正的 JDK 异步，不是 `runAsync` 包装。
- `buildJdkRequest(HttpRequest)`：通过 `HttpRequestSupport.buildUrlWithParams(...)` 添加 URL 参数，添加 header，`timeout()` 设为单请求超时，`DELETE` 通过 `builder.method("DELETE", bodyPublisher)` 支持 body。

### 2. `JdkSseClient` —— SSE

用 `BodyHandlers.ofInputStream()` 拿输入流，按行喂入 [`SseLineParser`](../atlas-richie-component-http-core/README.zh.md#4-sse--全协议支持)。

## ⚙️ 配置参考

所有属性位于 `platform.component.http.jdk.*`：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `connect-timeout` | Duration | `5s` | TCP/TLS 握手超时 |
| `version` | `HTTP_1_1` / `HTTP_2` | `HTTP_2` | 协议版本 |
| `follow-redirects` | boolean | `false` | 自动跟随 3xx |
| `priority` | int (1..256) | `16` | HTTP/2 流优先级（越小越高） |
| `keep-alive-time` | Duration | `30s` | 空闲连接保留时长 |
| `max-concurrent-streams` | int | `100` | HTTP/2 最大并发流数 |
| `proxy-host` | String | – | 可选代理主机 |
| `proxy-port` | int | `80` | 可选代理端口 |
| `use-virtual-threads` | boolean | `true` | 异步执行器 = `Executors.newVirtualThreadPerTaskExecutor()` |

## 🎯 最佳实践

1. **零三方依赖环境首选**：适合依赖政策严格 / 许可证审计严格的环境。
2. **批任务调优 HTTP/2 流**：`max-concurrent-streams: 500`、`priority: 8`。
3. **异步扇出用虚拟线程**：默认 `use-virtual-threads: true`。
4. **仅在需要时打开自动重定向**：保持 `false` 便于 URL 审计。
5. **需要经典线程池时关闭虚拟线程**：把 `use-virtual-threads=false` 并自行提供。

## ⚠️ 已知限制

| 限制 | 影响 | 临时方案 |
|------|------|---------|
| **无 multipart 表单字段** | 无法在文件外附带表单字段 | 循环调用，或切到 `http_client_5` |
| **Body 一次性 `byte[]`** | 大下载占内存 | 用 SSE 流；等流式 API |
| **JDK 连接池不透明** | 不如 OkHttp / HttpClient5 精细 | 接受；需要按路由调优切 Provider |
| **`use-virtual-threads=true` 是默认偏好** | 难接入经典 `ExecutorService` | 覆盖 `JdkHttpAdapter` Bean |

## ❓ 常见问题

### Q1：JDK Provider 默认用 HTTP/2 吗？

是——`version=HTTP_2` 是默认值。上游不支持时切 `HTTP_1_1`。

### Q2：连接失败为何要等很久？

JDK `HttpClient` 在连接错误上的反馈不如 OkHttp 快。设置 `connect-timeout` 为紧值（3-5s）。

### Q3：异步虚拟线程真的有用吗？

是——`CompletableFuture` 链调度在虚拟线程执行器上，避免阻塞 I/O 时 pin OS 线程。

### Q4：如何禁用 HTTP/2？

```yaml
platform:
  component:
    http:
      jdk:
        version: HTTP_1_1
```

### Q5：代理没生效？

必须同时设置 `proxy-host` 和 `proxy-port`。

### Q6：`strictSsl=false` 生效吗？

是——本 Provider 显式注入 trust-all `SSLContext` 并关闭主机名校验。

## 📚 相关文档

- **父组件** — [`../README.zh.md`](../README.zh.md)
- **门面 core** — [`../atlas-richie-component-http-core/README.zh.md`](../atlas-richie-component-http-core/README.zh.md)
- **其他 Provider** — [OkHttp](../atlas-richie-component-http-okhttp/README.zh.md) · [HttpClient5](../atlas-richie-component-http-httpclient5/README.zh.md) · [JDK](../atlas-richie-component-http-jdk/README.zh.md) · [RestClient](../atlas-richie-component-http-restclient/README.zh.md)

---

**atlas-richie-component-http-jdk** 🚀
