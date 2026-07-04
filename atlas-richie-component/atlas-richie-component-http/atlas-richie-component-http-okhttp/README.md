# Atlas Richie HTTP OkHttp (atlas-richie-component-http-okhttp)

Default provider for the http component. Implements `HttpClient` on top of [OkHttp 4](https://square.github.io/okhttp/) + `okhttp-sse` + `logging-interceptor`. Selected by `platform.component.http.provider=okhttp` (default).

This module registers:
- A single `OkHttpClient` bean (connection pool, dispatcher, timeouts, optional cache, optional trust-all SSL)
- An `OkHttpAdapter` bean that implements `HttpClient`

---

## 📖 Contents

- [📖 Overview](#📖-overview)
  - [What this module gives you](#what-this-module-gives-you)
- [🏗️ Architecture & Module Layout](#🏗️-architecture-&-module-layout)
- [🚀 Quick Start](#🚀-quick-start)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. `OkHttpAdapter` — `HttpClient` implementation](#1-okhttpadapter-—-httpclient-implementation)
  - [2. `OkHttpSseClient` — SSE](#2-okhttpsseclient-—-sse)
  - [3. SSL handling](#3-ssl-handling)
  - [4. Logging levels](#4-logging-levels)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: Why does my `OkHttpClient` bean override not take effect?](#q1-why-does-my-okhttpclient-bean-override-not-take-effect?)
  - [Q2: How do I attach a custom OkHttp interceptor?](#q2-how-do-i-attach-a-custom-okhttp-interceptor?)
  - [Q3: Connection pool seems too small](#q3-connection-pool-seems-too-small)
  - [Q4: SSE drops events on transient network failure](#q4-sse-drops-events-on-transient-network-failure)
  - [Q5: `level: BODY` produces huge logs](#q5-level-body-produces-huge-logs)
  - [Q6: Can I share one `HttpClient` across multiple Spring Boot apps?](#q6-can-i-share-one-httpclient-across-multiple-spring-boot-apps?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-http-okhttp` |
| **Selected by** | `platform.component.http.provider=<provider>` |

### What this module gives you

| ✅ Provides | ❌ Does not provide |
|------------|---------------------|
| `OkHttpClient` singleton bean (production-tuned) | Custom interceptor pipelines |
| Connection pool (size = `Runtime.availableProcessors()`, idle retention configurable) | Multipart file lists (single file per request today) |
| `Dispatcher` with global + per-host concurrency caps | mTLS per-route |
| Built-in `HttpLoggingInterceptor` (`NONE`/`BASIC`/`HEADERS`/`BODY`) | Response streaming — body is loaded as `byte[]` |
| Response cache (GET-only, configurable size + path) | |
| Optional `strictSsl=false` → trust-all with WARN log | |
| Native SSE via `okhttp-sse` | |

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-http-okhttp
├── OkHttpAdapter                  implements HttpClient; delegates to OkHttpClient
├── OkHttpSseClient                opens SSE streams via okhttp-sse
├── OkHttpSseConnection            implements SseConnection
└── config/
    ├── HttpProperties             @ConfigurationProperties("platform.component.http.okhttp")
    └── HttpAutoConfiguration      builds OkHttpClient + OkHttpAdapter
```

## 🚀 Quick Start

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

### 1. `OkHttpAdapter` — `HttpClient` implementation

Every `HttpClient` method is implemented. `execute()` loads body into `byte[]` and releases the connection. `async()` / `future()` submit to OkHttp `Dispatcher`. Per-request `timeout()` is honored via a derived client with overridden `readTimeout` / `callTimeout`.

### 2. `OkHttpSseClient` — SSE

Uses `okhttp-sse`'s `EventSourceListener` and feeds events to your `SseListener`. **No auto-reconnect** — implement in `onFailure` if needed.

### 3. SSL handling

```yaml
platform:
  component:
    http:
      strict-ssl: false   # trust-all; WARN at startup
```

### 4. Logging levels

```yaml
platform:
  component:
    http:
      okhttp:
        level: BASIC   # NONE / BASIC / HEADERS / BODY
```

Recommended: `BASIC` (production), `HEADERS` (debug), `BODY` (local dev only).

## ⚙️ Configuration Reference

All under `platform.component.http.okhttp.*`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `read-timeout` / `-time-unit` | int / TimeUnit | `5` / `SECONDS` | Socket read timeout |
| `write-timeout` / `-time-unit` | int / TimeUnit | `5` / `SECONDS` | Socket write timeout |
| `connect-timeout` / `-time-unit` | int / TimeUnit | `5` / `SECONDS` | TCP/TLS handshake timeout |
| `call-timeout` / `-time-unit` | int / TimeUnit | `15` / `SECONDS` | End-to-end timeout |
| `level` | `NONE` / `BASIC` / `HEADERS` / `BODY` | `BODY` | `HttpLoggingInterceptor` verbosity |
| `enable-cache` / `cache-path` / `cache-size` (MB) | boolean / String / int | `false` / `/opt/okhttp3/cache/` / `100` | GET-only response cache |
| `max-requests` / `max-requests-per-host` | int / int | `250` / `25` | Dispatcher concurrency caps |
| `keep-alive-duration` / `-time-unit` | long / TimeUnit | `5` / `MINUTES` | Connection-pool idle retention |

**SSL**: `platform.component.http.strict-ssl=false` enables trust-all with WARN log.

## 🎯 Best Practices

1. **Tune timeout layers separately** — `connect`, `read`, `write`, `call`.
2. **Set `level: BASIC` in production** — `BODY` leaks credentials/tokens/PII.
3. **Use response cache only when data tolerates staleness** — OkHttp respects upstream `Cache-Control`.
4. **Configure `maxRequestsPerHost` to protect downstream** — cap per upstream.
5. **Treat the `OkHttpClient` bean as a singleton** — don't `new OkHttpClient.Builder()`.
6. **SSE reconnect in your code** — implement in `SseListener.onFailure(...)`.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **Multipart is single-file** | Multi-file uploads need a loop | Loop calls; or switch to `http_client_5` |
| **Body loaded as `byte[]` once** | Large downloads inflate memory | Stream via SSE; wait for streaming API |
| **`strictSsl=false` injects trust-all silently** | Misconfigured production risk | Check startup WARN |
| **No retry / circuit-breaker built in** | Transient failures bubble up | Combine with [`atlas-richie-component-microservice`](../../atlas-richie-component-microservice/README.md) |
| **`level=BODY` logs credentials** | Token / cookie / PII leak | Use `BASIC` in production |

## ❓ FAQ

### Q1: Why does my `OkHttpClient` bean override not take effect?

This component already registers an `OkHttpClient` bean via `HttpAutoConfiguration#httpComponent`. Customize via `platform.component.http.okhttp.*` properties; or declare your own `@Bean @Primary OkHttpClient`.

### Q2: How do I attach a custom OkHttp interceptor?

Define your own `OkHttpClient.Builder` as a `@Bean` marked `@Primary`.

### Q3: Connection pool seems too small

Tune `max-requests` and `max-requests-per-host`. OkHttp pool size is independent — equals `Runtime.availableProcessors()` by default.

### Q4: SSE drops events on transient network failure

OkHttp's SSE does not auto-reconnect. Implement reconnect in `SseListener.onFailure(...)`.

### Q5: `level: BODY` produces huge logs

Switch to `BASIC` (production) or `HEADERS` (debug). For deep body inspection, route through [`atlas-richie-component-desensitize-logging`](../../atlas-richie-component-desensitize/atlas-richie-component-desensitize-logging/README.md).

### Q6: Can I share one `HttpClient` across multiple Spring Boot apps?

Yes — declare in a shared library; each app depends on `core` + `okhttp`.

## 📚 Further Reading

- **Parent** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **Core** — [`../atlas-richie-component-http-core/README.md`](../atlas-richie-component-http-core/README.md)
- **Other providers** — [OkHttp](../atlas-richie-component-http-okhttp/README.md) · [HttpClient5](../atlas-richie-component-http-httpclient5/README.md) · [JDK](../atlas-richie-component-http-jdk/README.md) · [RestClient](../atlas-richie-component-http-restclient/README.md)

---

**atlas-richie-component-http-okhttp** 🚀
