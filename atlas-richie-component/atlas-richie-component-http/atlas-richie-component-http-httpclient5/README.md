# Atlas Richie HTTP HttpClient5 (atlas-richie-component-http-httpclient5)

Apache HttpClient 5 provider for the http component. Implements `HttpClient` on top of `org.apache.httpcomponents.client5:httpclient5` with classic-style `PoolingHttpClientConnectionManager`. Selected by `platform.component.http.provider=http_client_5`.

This module has the **most mature multipart support** of all four providers.

---

## 📖 Contents

- [📖 Overview](#📖-overview)
  - [What this module gives you](#what-this-module-gives-you)
- [🏗️ Architecture & Module Layout](#🏗️-architecture-&-module-layout)
- [🚀 Quick Start](#🚀-quick-start)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. `HttpClient5Adapter` — `HttpClient` implementation](#1-httpclient5adapter-—-httpclient-implementation)
  - [2. `HttpClient5SseClient` — SSE](#2-httpclient5sseclient-—-sse)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: Why isn't `strictSsl=false` working?](#q1-why-isnt-strictssl=false-working?)
  - [Q2: Why is my call hitting `ConnectTimeoutException` instead of my expected `responseTimeout`?](#q2-why-is-my-call-hitting-connecttimeoutexception-instead-of-my-expected-responsetimeout?)
  - [Q3: How do I attach request / response interceptors?](#q3-how-do-i-attach-request-/-response-interceptors?)
  - [Q4: Can I share this client across threads safely?](#q4-can-i-share-this-client-across-threads-safely?)
  - [Q5: What HTTP/2 options exist?](#q5-what-http/2-options-exist?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-http-httpclient5` |
| **Selected by** | `platform.component.http.provider=<provider>` |

### What this module gives you

| ✅ Provides | ❌ Does not provide |
|------------|---------------------|
| `CloseableHttpClient` singleton with `PoolingHttpClientConnectionManager` | HTTP/2 multiplexed streams |
| TLS 1.2 / 1.3 by default | `strictSsl=false` trust-all toggle |
| Per-route and total connection pool caps | Body streaming — body is loaded as `byte[]` |
| **Most mature multipart** (uses `MultipartEntityBuilder`) | `BodyHandlers`-style async — wraps sync via `runAsync` |
| Per-request `RequestConfig` (timeout override) | |

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-http-httpclient5
├── HttpClient5Adapter               implements HttpClient; delegates to CloseableHttpClient
├── HttpClient5SseClient             opens SSE streams via raw HTTP response bodies
├── HttpClient5SseConnection         implements SseConnection; uses SseLineParser
└── config/
    ├── HttpProperties               @ConfigurationProperties("platform.component.http.httpclient5")
    └── HttpAutoConfiguration        builds PoolingHttpClientConnectionManager + CloseableHttpClient + HttpClient5Adapter
```

## 🚀 Quick Start

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

### 1. `HttpClient5Adapter` — `HttpClient` implementation

- `execute(HttpRequest)` uses `httpClient.execute(buildRequest, this::buildResponse)` — response handler releases connection back to pool.
- `bodyEntity(HttpRequest)`:
  - If `multipart` is set → `MultipartEntityBuilder.addBinaryBody(...)`.
  - Otherwise → `ByteArrayEntity` with configured mime; **does not** set entity when body is empty.

### 2. `HttpClient5SseClient` — SSE

Reads response body as stream and feeds each line into [`SseLineParser`](../atlas-richie-component-http-core/README.md#4-sse--full-protocol-support).

## ⚙️ Configuration Reference

All under `platform.component.http.httpclient5.*`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `connection-request-timeout` / `-time-unit` | int / TimeUnit | `5` / `SECONDS` | Wait time for a connection from the pool |
| `response-timeout` / `-time-unit` | int / TimeUnit | `5` / `SECONDS` | Wait for server response after request is sent |
| `max-total` | int | `250` | Total connections in the pool |
| `default-max-per-route` | int | `25` | Per-route connection cap |

## 🎯 Best Practices

1. **Tune the pool to your concurrency** — `max-total: 500`, `default-max-per-route: 50` for high throughput.
2. **Prefer HttpClient5 for file uploads** — most mature multipart.
3. **Always close `InputStream` you pass to `multipart()`** — wrap with try-with-resources.
4. **Use the per-request `timeout()` for slow upstream** — `request.timeout(...)` overrides global.
5. **Don't manually close the `CloseableHttpClient` bean** — Spring manages lifecycle.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **No HTTP/2 by default** | Single connection per host; head-of-line blocking | Set `HttpVersionPolicy.NEGOTIATE` via custom `HttpClientBuilder` |
| **Async wraps sync via `runAsync(ForkJoinPool.commonPool())`** | Blocks a common-pool thread | For pure async, switch to OkHttp / JDK |
| **Body loaded as `byte[]`** | Large downloads inflate memory | Wait for streaming API |
| **`strictSsl=false` not honored** | Trust-all not auto-enabled | Configure trust-all on the underlying `HttpClientBuilder` |

## ❓ FAQ

### Q1: Why isn't `strictSsl=false` working?

This provider does not yet wire the trust-all `SSLContext`. Override the `CloseableHttpClient` bean with `@Primary`.

### Q2: Why is my call hitting `ConnectTimeoutException` instead of my expected `responseTimeout`?

HttpClient5 distinguishes between *getting a connection from the pool* and *waiting for the response*. Tune both.

### Q3: How do I attach request / response interceptors?

Override the `CloseableHttpClient` bean with `@Primary` and call `.addInterceptorFirst(...)` / `.addInterceptorLast(...)`.

### Q4: Can I share this client across threads safely?

Yes — `CloseableHttpClient` is thread-safe.

### Q5: What HTTP/2 options exist?

HttpClient5 supports HTTP/2 via `setVersionPolicy(HttpVersionPolicy.NEGOTIATE)` — but not default; switch providers if HTTP/2 is a hard requirement.

## 📚 Further Reading

- **Parent** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **Core** — [`../atlas-richie-component-http-core/README.md`](../atlas-richie-component-http-core/README.md)
- **Other providers** — [OkHttp](../atlas-richie-component-http-okhttp/README.md) · [HttpClient5](../atlas-richie-component-http-httpclient5/README.md) · [JDK](../atlas-richie-component-http-jdk/README.md) · [RestClient](../atlas-richie-component-http-restclient/README.md)

---

**atlas-richie-component-http-httpclient5** 🚀
