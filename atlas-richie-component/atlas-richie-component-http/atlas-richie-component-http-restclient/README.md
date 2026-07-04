# Atlas Richie HTTP RestClient (atlas-richie-component-http-restclient)

Spring `RestClient` provider for the http component. Implements `HttpClient` on top of [`org.springframework.web.client.RestClient`](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient) (Spring 6.1+). Selected by `platform.component.http.provider=rest_client`. **Reuses any Spring `RestClient.Builder` bean you provide.**

---

## 📖 Contents

- [📖 Overview](#📖-overview)
  - [What this module gives you](#what-this-module-gives-you)
- [🏗️ Architecture & Module Layout](#🏗️-architecture-&-module-layout)
- [🚀 Quick Start](#🚀-quick-start)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. `RestClientAdapter` — `HttpClient` implementation](#1-restclientadapter-—-httpclient-implementation)
  - [2. Per-request timeout](#2-per-request-timeout)
  - [3. SSE](#3-sse)
  - [4. Strict SSL](#4-strict-ssl)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: Why is `strict-ssl=false` not working?](#q1-why-is-strict-ssl=false-not-working?)
  - [Q2: Can I share my `RestClient.Builder` with non-facade callers?](#q2-can-i-share-my-restclientbuilder-with-non-facade-callers?)
  - [Q3: Why is multipart throwing "Unsupported"?](#q3-why-is-multipart-throwing-unsupported?)
  - [Q4: How do I add an interceptor to all requests?](#q4-how-do-i-add-an-interceptor-to-all-requests?)
  - [Q5: What's the threading model for async / future?](#q5-whats-the-threading-model-for-async-/-future?)
  - [Q6: Does this provider work without `spring-boot-starter-web`?](#q6-does-this-provider-work-without-spring-boot-starter-web?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-http-restclient` |
| **Selected by** | `platform.component.http.provider=<provider>` |

### What this module gives you

| ✅ Provides | ❌ Does not provide |
|------------|---------------------|
| `RestClientAdapter` wrapping `RestClient` | Provider-specific tuning (timeouts, pool, TLS) |
| Reuse of any `@Bean RestClient.Builder` | Multipart form fields (use HttpClient5 for uploads) |
| Per-request timeout wrapping (`CompletableFuture.orTimeout`) | HTTP/2 control |
| `RestClientSseClient` SSE implementation | Body streaming |

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-http-restclient
├── RestClientAdapter             implements HttpClient; delegates to RestClient
├── RestClientSseClient           opens SSE streams via raw response
├── RestClientSseConnection       implements SseConnection
└── config/
    └── HttpAutoConfiguration     wraps RestClient.Builder → RestClient → RestClientAdapter
```

## 🚀 Quick Start

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

### 1. `RestClientAdapter` — `HttpClient` implementation

- `execute(HttpRequest)` calls `buildSpec(request).retrieve().toEntity(byte[].class)`.
- `async(...)` and `future(...)` delegate to `CompletableFuture.runAsync(...)` — sync wrapped in async.
- `buildSpec(HttpRequest)` builds a `RestClient.RequestBodySpec`; method, URI, headers, `Content-Type`, body all set explicitly.

### 2. Per-request timeout

```java
http.get(url).timeout(Duration.ofSeconds(5)).execute(User.class);
```

### 3. SSE

Uses raw response stream from `RestClient` and feeds lines to `SseLineParser`.

### 4. Strict SSL

`platform.component.http.strict-ssl=false` is **not** wired here. Configure trust-all on the underlying `RestClient.Builder`.

## ⚙️ Configuration Reference

There is **no provider-specific config**. Customize via your `RestClient.Builder`:

```java
@Bean
public RestClient.Builder customRestClientBuilder() {
    return RestClient.builder()
                     .baseUrl("https://api.example.com")
                     .defaultHeader("X-Custom", "value");
}
```

## 🎯 Best Practices

1. **Reuse a customized `RestClient.Builder`** — base URL, default headers, interceptors carry over.
2. **Timeouts via the facade, not the builder** — RestClient has no per-request timeout; facade implements it.
3. **Pick a different provider for uploads** — RestClient adapter doesn't implement multipart fields.
4. **Combine with Spring Boot's standard features** — `RestClient.Builder` patterns work unchanged.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **No multipart** | File uploads not supported via the facade | Use `http_client_5` provider |
| **Async wraps sync** | Blocks a `ForkJoinPool.commonPool()` thread | Switch to OkHttp / JDK for high-concurrency |
| **No native HTTP/2 control** | Whatever RestClient's underlying ClientHttpRequestFactory provides | Use a `JdkClientHttpRequestFactory` for HTTP/2 |
| **`strictSsl=false` not wired** | Trust-all must be configured on the `RestClient.Builder` | Configure `ClientHttpRequestFactory` manually |

## ❓ FAQ

### Q1: Why is `strict-ssl=false` not working?

This provider does not wire trust-all `SSLContext`. Configure it via your `RestClient.Builder` with a custom `ClientHttpRequestFactory`.

### Q2: Can I share my `RestClient.Builder` with non-facade callers?

Yes — the `RestClient.Builder` bean is yours.

### Q3: Why is multipart throwing "Unsupported"?

The RestClient provider does not implement `HttpRequest.multipart(...)`. Use `http_client_5` for file uploads.

### Q4: How do I add an interceptor to all requests?

Via your `RestClient.Builder`:

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

### Q5: What's the threading model for async / future?

`CompletableFuture.runAsync(...)` — runs on `ForkJoinPool.commonPool()`. For virtual-thread-friendly async, switch to the `jdk` provider.

### Q6: Does this provider work without `spring-boot-starter-web`?

No — `RestClient` lives in `org.springframework.web.client.RestClient`, provided by `spring-boot-starter-web`. The provider is `@ConditionalOnClass(RestClient.class)`.

## 📚 Further Reading

- **Parent** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **Core** — [`../atlas-richie-component-http-core/README.md`](../atlas-richie-component-http-core/README.md)
- **Other providers** — [OkHttp](../atlas-richie-component-http-okhttp/README.md) · [HttpClient5](../atlas-richie-component-http-httpclient5/README.md) · [JDK](../atlas-richie-component-http-jdk/README.md) · [RestClient](../atlas-richie-component-http-restclient/README.md)

---

**atlas-richie-component-http-restclient** 🚀
