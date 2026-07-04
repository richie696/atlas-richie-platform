# Atlas Richie Microservice Component (atlas-richie-component-microservice)

> **Microservice infrastructure** component. Bundles **OpenFeign** for declarative HTTP clients, **Resilience4j** / Sentinel for circuit breaker + retry, **Spring Cloud LoadBalancer** for service discovery, and **Spring Cloud Gateway** helpers. One-stop for inter-service calls.

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
  - [3. Declare a Feign client](#3-declare-a-feign-client)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. OpenFeign declarative HTTP](#1-openfeign-declarative-http)
  - [2. Sentinel circuit breaker](#2-sentinel-circuit-breaker)
  - [3. Retry + fallback](#3-retry-+-fallback)
  - [4. Load balancing](#4-load-balancing)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: How is this different from `spring-cloud-starter-openfeign`?](#q1-how-is-this-different-from-spring-cloud-starter-openfeign?)
  - [Q2: Can I use Resilience4j instead of Sentinel?](#q2-can-i-use-resilience4j-instead-of-sentinel?)
  - [Q3: How do I call a service that requires mTLS?](#q3-how-do-i-call-a-service-that-requires-mtls?)
  - [Q4: Can I mock a Feign client in tests?](#q4-can-i-mock-a-feign-client-in-tests?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-microservice` |
| **Category** | Microservice infrastructure — inter-service calls + resilience |
| **Hard dependencies** | Spring Cloud OpenFeign, Spring Cloud LoadBalancer |
| **Optional** | Sentinel, Resilience4j, Eureka / Nacos / Consul discovery |

### `What` this component is — and what it isn't

| ✅ It gives you | ❌ It does not give you |
|-----------------|------------------------|
| OpenFeign declarative clients | An API gateway (use Spring Cloud Gateway separately) |
| Sentinel circuit breaker for all Feign calls | Service mesh (use Istio / Linkerd separately) |
| Retry with exponential backoff | Distributed tracing across services (use [`atlas-richie-component-tracing`](../atlas-richie-component-tracing/README.md)) |
| Service discovery (Nacos / Eureka / Consul) | A service registry itself (use external Nacos) |

## ✨ Features

### `Core` capabilities

- ✅ **OpenFeign** — declarative HTTP clients via `@FeignClient`.
- ✅ **Resilience** — Sentinel circuit breaker (block / degrade / log).
- ✅ **Retry** — built-in with backoff; configurable per client.
- ✅ **Fallback** — `Hystrix`-style fallback beans.
- ✅ **Load balancing** — Spring Cloud LoadBalancer (round-robin / random / weighted).
- ✅ **Service discovery** — Eureka / Nacos / Consul.

### `Design` choices

- ✅ **One facade** — `RestClient` (Spring's) or `OpenFeign` (declarative).
- ✅ **Sentinel first** — fail fast on dead hosts; integrates with [`atlas-richie-component-mongodb`](../atlas-richie-component-mongodb/README.md) and [`atlas-richie-component-http`](../atlas-richie-component-http/README.md).
- ✅ **Config-driven** — switch transport / pool / breaker without code change.

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-microservice
├── config/
│   ├── MicroserviceAutoConfiguration
│   ├── FeignClientOkhttpProperties
│   ├── RestClientAutoConfiguration
│   └── SentinelAutoConfiguration
├── feign/
│   ├── FeignClientBuilder              ← builds FeignClient with Sentinel interceptor
│   └── FeignRequestInterceptor
├── sentinel/
│   ├── FeignSentinelInterceptor         ← block / degrade on circuit open
│   └── SentinelRuleLoader
├── loadbalancer/
│   └── ServiceInstanceListSupplier      ← round-robin / random / weighted
└── discovery/
    └── (auto-detected: Eureka / Nacos / Consul)
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-microservice</artifactId>
</dependency>
```

### 2) `Configure`

```yaml
platform:
  component:
    microservice:
      feign:
        client:
          config:
            default:
              connect-timeout: 2000
              read-timeout: 5000
              logger-level: BASIC
      sentinel:
        enabled: true
      retry:
        max-attempts: 3
        backoff: exponential
        initial-interval: 100ms
        multiplier: 2.0
        max-interval: 5s
      discovery:
        type: nacos    # nacos | eureka | consul | none
```

### 3) `Declare` a `Feign` client

```java
@FeignClient(name = "user-service", path = "/api/users")
public interface UserClient {
    @GetMapping("/{id}")
    User getById(@PathVariable String id);

    @PostMapping
    User create(@RequestBody CreateUserRequest req);
}

@RestController
@RequiredArgsConstructor
public class OrderController {
    private final UserClient userClient;

    @PostMapping("/orders")
    public Order place(@RequestBody OrderRequest req) {
        User user = userClient.getById(req.getUserId());
        return orderService.place(user, req);
    }
}
```

## 🔧 Core Capabilities

### 1) `OpenFeign` declarative `HTTP`

```java
@FeignClient(name = "billing-service", fallback = BillingClientFallback.class)
public interface BillingClient {
    @PostMapping("/api/invoices")
    Invoice create(@RequestBody InvoiceRequest req);
}
```

### 2) `Sentinel` circuit breaker

```yaml
platform:
  component:
    microservice:
      sentinel:
        rules:
          - resource: billing-service#create
            grade: rt              # response time
            threshold: 200ms
            window-seconds: 10
```

When tripped → `DegradeException` → fallback bean invoked.

### 3) `Retry` + fallback

```java
@Component
public class BillingClientFallback implements BillingClient {
    @Override
    public Invoice create(InvoiceRequest req) {
        return Invoice.queued(req.getUserId(), req.getAmount());   // queue for async processing
    }
}
```

### 4) `Load` balancing

Spring Cloud LoadBalancer picks an instance from the registry. Default: round-robin.

```yaml
spring:
  cloud:
    loadbalancer:
      clients:
        user-service:
          loadbalancer:
            type: random       # round_robin | random | weighted
```

## ⚙️ Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `feign.client.config.<name>.connect-timeout` | int | `2000` | Connect timeout (ms) |
| `feign.client.config.<name>.read-timeout` | int | `5000` | Read timeout (ms) |
| `feign.client.config.<name>.logger-level` | enum | `BASIC` | `NONE` / `BASIC` / `HEADERS` / `FULL` |
| `sentinel.enabled` | boolean | `true` | Enable Sentinel |
| `retry.max-attempts` | int | `3` | Max retry count |
| `retry.backoff` | enum | `exponential` | `fixed` / `exponential` |
| `discovery.type` | enum | `nacos` | `nacos` / `eureka` / `consul` / `none` |

## 🎯 Best Practices

1. **Always define a fallback** for every Feign client — fail soft, queue if needed.
2. **Use `connect-timeout: 2000` / `read-timeout: 5000`** as sane defaults.
3. **Configure Sentinel per Feign method** — `resource: <client>#<method>`.
4. **Don't use retry for non-idempotent operations** — POST without idempotency key = duplicate.
5. **Combine with tracing** — see [`atlas-richie-component-tracing`](../atlas-richie-component-tracing/README.md).

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **Single discovery backend at a time** | Can't mix Eureka + Nacos | Run separate clusters |
| **Feign reactive not supported** | No `Mono<User>` return types | Use Spring `WebClient` directly |
| **No bulkhead isolation by default** | One slow service can starve threads | Configure thread pools |

## ❓ FAQ

### Q1 — How is this different from `spring-cloud-starter-openfeign`?

Adds Sentinel integration, platform-aligned configuration, and FeignClient builders that wire `HttpClient` + breaker + retry in one place.

### `Q2` — `Can` `I` use `Resilience4j` instead of `Sentinel`?

Yes — install `resilience4j-spring-boot3` and configure accordingly. The component does not bind to Sentinel exclusively.

### `Q3` — `How` do `I` call a service that requires mTLS?

Configure `feign.client.config.<name>.ssl` or set up a custom `Client` bean.

### `Q4` — `Can` `I` mock a `Feign` client in tests?

Yes — Spring Cloud Contract + WireMock integrate with Feign.

## 📚 Further Reading

- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **HTTP client (used by Feign)** — [`../atlas-richie-component-http/README.md`](../atlas-richie-component-http/README.md)
- **Tracing** — [`../atlas-richie-component-tracing/README.md`](../atlas-richie-component-tracing/README.md)
- External: [OpenFeign](https://github.com/OpenFeign/feign) · [Sentinel](https://sentinelguard.io/) · [Spring Cloud LoadBalancer](https://spring.io/projects/spring-cloud-loadbalancer)

---

**atlas-richie-component-microservice** 🚀
