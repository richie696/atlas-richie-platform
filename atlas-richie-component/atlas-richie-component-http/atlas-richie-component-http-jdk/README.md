# Atlas Richie HTTP JDK (atlas-richie-component-http-jdk)

JDK-native provider for the http component. Implements `HttpClient` on top of [`java.net.http.HttpClient`](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html) (JDK 11+). Selected by `platform.component.http.provider=jdk`. **Zero third-party dependencies** — perfect for environments that avoid pulling external HTTP libraries.

---

## 📖 Contents

- [📖 Overview](#📖-overview)
  - [What this module gives you](#what-this-module-gives-you)
- [🏗️ Architecture & Module Layout](#🏗️-architecture-&-module-layout)
- [🚀 Quick Start](#🚀-quick-start)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. `JdkHttpAdapter` — `HttpClient` implementation](#1-jdkhttpadapter-—-httpclient-implementation)
  - [2. `JdkSseClient` — SSE](#2-jdksseclient-—-sse)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: Does the JDK provider actually use HTTP/2 by default?](#q1-does-the-jdk-provider-actually-use-http/2-by-default?)
  - [Q2: Why does my call hang for a long time after connection failure?](#q2-why-does-my-call-hang-for-a-long-time-after-connection-failure?)
  - [Q3: Virtual threads in async — does it really help?](#q3-virtual-threads-in-async-—-does-it-really-help?)
  - [Q4: Can I disable HTTP/2?](#q4-can-i-disable-http/2?)
  - [Q5: Why isn't my proxy picked up?](#q5-why-isnt-my-proxy-picked-up?)
  - [Q6: Is `strictSsl=false` honored?](#q6-is-strictssl=false-honored?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-http-jdk` |
| **Selected by** | `platform.component.http.provider=<provider>` |

### What this module gives you

| ✅ Provides | ❌ Does not provide |
|------------|---------------------|
| Native JDK 11+ `java.net.http.HttpClient` (HTTP/1.1 + HTTP/2) | Custom connection pool tuning |
| Virtual-thread-friendly async executor | Per-method auth keys |
| HTTP/2 priority + concurrent streams | Multipart form fields |
| Optional proxy (`proxyHost` + `proxyPort`) | Response streaming |
| `strictSsl=false` → trust-all with WARN | |

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-http-jdk
├── JdkHttpAdapter               implements HttpClient; delegates to java.net.http.HttpClient
├── JdkSseClient                 opens SSE streams via BodyHandlers.ofInputStream + SseLineParser
├── JdkSseConnection             implements SseConnection
└── config/
    ├── HttpProperties           @ConfigurationProperties("platform.component.http.jdk")
    └── HttpAutoConfiguration    builds java.net.http.HttpClient + JdkHttpAdapter
```

## 🚀 Quick Start

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

Use the facade:

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

See [`http-core`](../atlas-richie-component-http-core/README.md) for the full API.

## 🔧 Core Capabilities

### 1. `JdkHttpAdapter` — `HttpClient` implementation

- `execute(HttpRequest)` uses `httpClient.send(buildJdkRequest, BodyHandlers.ofByteArray())` — body collected as `byte[]` once.
- `async(...)` and `future(...)` delegate to `httpClient.sendAsync(...)` — actual JDK async.
- `buildJdkRequest(HttpRequest)`: adds URL params, headers, per-request timeout, supports `DELETE` with body.

### 2. `JdkSseClient` — SSE

Uses `BodyHandlers.ofInputStream()` and feeds each line to [`SseLineParser`](../atlas-richie-component-http-core/README.md#4-sse--full-protocol-support).

## ⚙️ Configuration Reference

All under `platform.component.http.jdk.*`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `connect-timeout` | Duration | `5s` | TCP/TLS handshake timeout |
| `version` | `HTTP_1_1` / `HTTP_2` | `HTTP_2` | Protocol version |
| `follow-redirects` | boolean | `false` | Auto-follow 3xx |
| `priority` | int (1..256) | `16` | HTTP/2 stream priority |
| `keep-alive-time` | Duration | `30s` | Idle connection retention |
| `max-concurrent-streams` | int | `100` | HTTP/2 max concurrent streams |
| `proxy-host` | String | – | Optional proxy host |
| `proxy-port` | int | `80` | Optional proxy port |
| `use-virtual-threads` | boolean | `true` | Async executor = `Executors.newVirtualThreadPerTaskExecutor()` |

## 🎯 Best Practices

1. **Pick `jdk` for zero-deps environments** — perfect for locked-down dependency policies.
2. **Tune HTTP/2 streams for batch workloads** — `max-concurrent-streams: 500`, `priority: 8`.
3. **Use virtual threads for fan-out** — default `use-virtual-threads: true`.
4. **Set `follow-redirects=true` only when needed**.
5. **Disable virtual threads only for classic pool control** — override the bean.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **No multipart form fields** | Cannot add auxiliary form fields alongside a file | Loop calls or use `http_client_5` |
| **Body loaded as `byte[]`** | Large downloads inflate memory | Stream via SSE |
| **JDK connection pool is opaque** | Less fine-grained control | Switch provider if needed |
| **`use-virtual-threads=true` is opinionated** | Hard to integrate with a classic `ExecutorService` | Override the `JdkHttpAdapter` bean |

## ❓ FAQ

### Q1: Does the JDK provider actually use HTTP/2 by default?

Yes — `version=HTTP_2` is the default.

### Q2: Why does my call hang for a long time after connection failure?

JDK `HttpClient` may not surface connection errors as fast as OkHttp. Set `connect-timeout` to a tight value (3-5s).

### Q3: Virtual threads in async — does it really help?

Yes — `CompletableFuture` chains scheduled on the virtual-thread executor avoid pinning OS threads on blocking I/O.

### Q4: Can I disable HTTP/2?

```yaml
platform:
  component:
    http:
      jdk:
        version: HTTP_1_1
```

### Q5: Why isn't my proxy picked up?

Both `proxy-host` and `proxy-port` must be set; `proxy-host` alone is treated as `null` and the proxy is skipped.

### Q6: Is `strictSsl=false` honored?

Yes — this provider explicitly wires the trust-all `SSLContext` and disables hostname verification.

## 📚 Further Reading

- **Parent** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **Core** — [`../atlas-richie-component-http-core/README.md`](../atlas-richie-component-http-core/README.md)
- **Other providers** — [OkHttp](../atlas-richie-component-http-okhttp/README.md) · [HttpClient5](../atlas-richie-component-http-httpclient5/README.md) · [JDK](../atlas-richie-component-http-jdk/README.md) · [RestClient](../atlas-richie-component-http-restclient/README.md)

---

**atlas-richie-component-http-jdk** 🚀
