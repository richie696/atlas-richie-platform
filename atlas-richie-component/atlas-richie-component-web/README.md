# Atlas Richie Web Component (atlas-richie-component-web)

> **Web / Servlet infrastructure** component. Bundles Spring Boot Web autoconfig, CORS, exception handling, i18n message resolution, WebSocket / SSE endpoints, and security filter chain conventions. One import away from a "production-grade" Spring MVC application.

---

## 📖 Contents

- [📖 Overview](#📖-overview)
  - [What this component is — and what it isn't](#what-this-component-is-—-and-what-it-isnt)
- [✨ Features](#✨-features)
  - [Core capabilities](#core-capabilities)
  - [Design choices](#design-choices)
- [🏗️ Architecture & Module Layout](#🏗️-architecture-&-module-layout)
- [🚀 Quick Start](#🚀-quick-start)
  - [1. Add the dependency](#1-add-the-dependency)
  - [2. Configure](#2-configure)
  - [3. Your first controller](#3-your-first-controller)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. CORS](#1-cors)
  - [2. Global exception handling](#2-global-exception-handling)
  - [3. WebSocket](#3-websocket)
  - [4. SSE endpoint](#4-sse-endpoint)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: How do I add a custom CORS origin dynamically?](#q1-how-do-i-add-a-custom-cors-origin-dynamically?)
  - [Q2: How do I throw errors with i18n messages?](#q2-how-do-i-throw-errors-with-i18n-messages?)
  - [Q3: Can I disable WebSocket?](#q3-can-i-disable-websocket?)
  - [Q4: How do I add CSRF protection?](#q4-how-do-i-add-csrf-protection?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-web` |
| **Category** | Web framework — Spring MVC infrastructure |
| **Hard dependencies** | `spring-boot-starter-web` |
| **Compatible with** | Spring Boot 4.x, JDK 25 |

### `What` this component is — and what it isn't

| ✅ It gives you | ❌ It does not give you |
|-----------------|------------------------|
| CORS preconfiguration | An API gateway (use Spring Cloud Gateway) |
| Global exception handling (`@RestControllerAdvice`) | Auth / permission rules (use `atlas-richie-component-oauth`) |
| WebSocket / SSE endpoints | Rate limiting (use Sentinel or gateway) |
| I18n message resolution (Locale resolver) | A web framework replacement (still Spring MVC) |

## ✨ Features

### `Core` capabilities

- ✅ **CORS** — declarative allowed origins / methods / headers.
- ✅ **Global exception handling** — typed `@RestControllerAdvice` with i18n message support.
- ✅ **WebSocket** — STOMP and raw WebSocket endpoints.
- ✅ **SSE** — `SseEmitter` helpers + auto-completion.
- ✅ **Locale resolver** — header / cookie / session based.
- ✅ **Static resource handling** — with caching headers.

### `Design` choices

- ✅ **Spring Boot native** — no override of WebMvcConfigurer.
- ✅ **Convention over configuration** — sensible defaults, opt-in overrides.
- ✅ **Header propagation** — auto-read tenant / user / trace from `HeaderContextHolder`.

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-web
├── config/
│   ├── WebAutoConfiguration
│   ├── WebProperties
│   ├── CorsAutoConfiguration
│   └── LocaleAutoConfiguration
├── cors/
│   ├── CorsProperties
│   └── CorsFilter
├── exception/
│   ├── GlobalExceptionHandler          ← @RestControllerAdvice
│   └── BusinessException               ← canonical error type
├── websocket/
│   ├── WebSocketConfig
│   └── StompEndpoint
├── sse/
│   └── SseEndpoint                     ← @GetMapping(produces="text/event-stream")
└── locale/
    ├── HeaderLocaleResolver
    └── MessageSourceConfiguration
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-web</artifactId>
</dependency>
```

### 2) `Configure`

```yaml
platform:
  component:
    web:
      cors:
        enabled: true
        allowed-origins: [https://app.example.com]
        allowed-methods: [GET, POST, PUT, DELETE, OPTIONS]
        allowed-headers: [*]
        allow-credentials: true
        max-age: 3600
      locale:
        default: en
        supported: [en, zh]
        header: Accept-Language
      exception:
        include-stack-trace: false
        include-binding-errors: true
```

### 3) `Your` first controller

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/{id}")
    public User get(@PathVariable String id) {
        return userService.findById(id);
    }
}
```

## 🔧 Core Capabilities

### 1) `CORS`

```yaml
platform:
  component:
    web:
      cors:
        allowed-origins: [https://app.example.com, https://admin.example.com]
        allowed-methods: [GET, POST, PUT, DELETE]
        allow-credentials: true
```

### 2) `Global` exception handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResultVO<?> handleBusiness(BusinessException e) {
        return ResultVO.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResultVO<?> handleOther(Exception e) {
        log.error("unexpected error", e);
        return ResultVO.error("INTERNAL_ERROR", "Internal server error");
    }
}
```

### 3) `WebSocket`

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new MyHandler(), "/ws")
                .setAllowedOrigins("*");
    }
}
```

### 4) `SSE` endpoint

```java
@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream() {
    SseEmitter emitter = new SseEmitter(60_000L);
    eventBus.subscribe(emitter::send);
    return emitter;
}
```

## ⚙️ Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `cors.enabled` | boolean | `true` | Enable CORS filter |
| `cors.allowed-origins` | List<String> | `[]` | Allowed origins (use `*` for any) |
| `cors.allowed-methods` | List<String> | `[GET, POST, OPTIONS]` | Allowed HTTP methods |
| `cors.allowed-headers` | List<String> | `[*]` | Allowed request headers |
| `cors.allow-credentials` | boolean | `false` | Allow cookies / auth headers |
| `cors.max-age` | long | `1800` | Pre-flight cache duration (s) |
| `locale.default` | String | `en` | Default locale |
| `locale.supported` | List<String> | `[en]` | Supported locales |
| `locale.header` | String | `Accept-Language` | Locale source header |
| `exception.include-stack-trace` | boolean | `false` | Include trace in API response |
| `exception.include-binding-errors` | boolean | `true` | Include validation errors |

## 🎯 Best Practices

1. **Never set `cors.allowed-origins: *` with `allow-credentials: true`** — browsers reject this combo.
2. **Always use `BusinessException`** + global handler — never throw raw `Exception`.
3. **Use i18n message keys, not literals** — `messageSource.getMessage("user.notFound", null, locale)`.
4. **Configure SSE timeouts explicitly** — default 0 = forever (memory leak risk).
5. **Validate `Origin` server-side too** — CORS is a browser convenience, not security.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **No built-in rate limiting** | Clients can spam endpoints | Use Sentinel at gateway layer |
| **No CSRF token helper** | SPA / mobile clients struggle | Custom CSRF filter or use SameSite cookies |
| **WebSocket auth not built in** | You wire your own handshake | Inject `HandshakeInterceptor` |

## ❓ FAQ

### `Q1` — `How` do `I` add a custom `CORS` origin dynamically?

Implement `CorsConfigurationSource` and register as `@Bean`.

### `Q2` — `How` do `I` throw errors with i18n messages?

```java
throw new BusinessException("USER_NOT_FOUND", locale);  // looks up messages.properties
```

### `Q3` — `Can` `I` disable `WebSocket`?

Don't include `spring-boot-starter-websocket` — the autoconfig won't activate.

### `Q4` — `How` do `I` add `CSRF` protection?

Extend `WebSecurityConfigurerAdapter` (legacy) or use `SecurityFilterChain` bean with `csrf()` config.

## 📚 Further Reading

- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **HTTP client** — [`../atlas-richie-component-http/README.md`](../atlas-richie-component-http/README.md)
- **OAuth** — [`../atlas-richie-component-oauth/README.md`](../atlas-richie-component-oauth/README.md)
- **i18n** — [`../atlas-richie-component-i18n/README.md`](../atlas-richie-component-i18n/README.md)
- **Microservice / Sentinel** — [`../atlas-richie-component-microservice/README.md`](../atlas-richie-component-microservice/README.md)

---

**atlas-richie-component-web** 🚀
