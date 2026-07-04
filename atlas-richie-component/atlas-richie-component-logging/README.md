# Atlas Richie Logging Component (atlas-richie-component-logging)

> Unified **structured logging** component. Provides Logback appenders, JSON layout, sensitive-data masking, MDC propagation, and an **operation log** interceptor (`@OperateLog`) for audit trails. Compatible with ELK / Loki / ClickHouse sinks.

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
  - [3. Use structured logging](#3-use-structured-logging)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. JSON layout](#1-json-layout)
  - [2. Sensitive-data masking](#2-sensitive-data-masking)
  - [3. Operation log (`@OperateLog`)](#3-operation-log-@operatelog)
  - [4. MDC propagation](#4-mdc-propagation)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: Does this conflict with `atlas-richie-component-desensitize-logging`?](#q1-does-this-conflict-with-atlas-richie-component-desensitize-logging?)
  - [Q2: How does `@OperateLog` differ from a regular `log.info`?](#q2-how-does-@operatelog-differ-from-a-regular-loginfo?)
  - [Q3: Can I customize the JSON layout's field names?](#q3-can-i-customize-the-json-layouts-field-names?)
  - [Q4: How do I send logs to Kafka?](#q4-how-do-i-send-logs-to-kafka?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-logging` |
| **Category** | Observability — structured logging + audit |
| **Hard dependencies** | Logback, `atlas-richie-context` (for `HeaderContextHolder`) |
| **Compatible with** | ELK, Loki, ClickHouse, Splunk, Datadog Logs |

### `What` this component is — and what it isn't

| ✅ It gives you | ❌ It does not give you |
|-----------------|------------------------|
| JSON layout for Logback | A metrics / tracing solution (use [`atlas-richie-component-tracing`](../atlas-richie-component-tracing/README.md)) |
| Sensitive-field masking (phone, ID card, email) | An APM / RUM solution |
| `@OperateLog` AOP interceptor for audit logs | Long-term log archival (use external sinks) |
| MDC auto-population from `HeaderContextHolder` | Custom sinks (write your own appender) |

## ✨ Features

### `Core` capabilities

- ✅ **JSON layout** — structured fields (timestamp, level, thread, logger, message, mdc, exception).
- ✅ **Sensitive-data masking** — pluggable `LogMasker` for `phone`, `idCard`, `email`, etc.
- ✅ **`@OperateLog` annotation** — AOP-based audit logging on methods.
- ✅ **MDC propagation** — auto-pull tenant / user / trace-id from `HeaderContextHolder`.
- ✅ **Logback appender abstraction** — sync / async, file / console / Kafka.

### `Design` choices

- ✅ **Logback-native** — no extra log facade (SLF4J stays the API).
- ✅ **Auto-configuration** — drop-in, no XML.
- ✅ **No runtime overhead when disabled** — `@OperateLog` uses AOP; if the bean isn't present, no proxying.

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-logging
├── config/
│   ├── LoggingAutoConfiguration
│   ├── OperateLogAutoConfiguration
│   └── OperateLogProperties
├── layout/
│   └── JsonLayout                          ← Logback Layout<ILoggingEvent>
├── mask/
│   ├── LogMasker                           ← SPI
│   └── DefaultLogMasker                    ← phone / idCard / email
├── mdc/
│   └── HeaderContextMdcContributor          ← pulls HeaderContextHolder → MDC
├── operate/
│   ├── OperateLog                          ← annotation
│   ├── OperateLogAspect                    ← AOP
│   └── OperateLogRecord                    ← record to sink
└── sink/
    ├── OperateLogSink                      ← SPI (DB / Kafka / Mongo)
    └── DefaultOperateLogSink               ← in-memory + async flush
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-logging</artifactId>
</dependency>
```

### 2) `Configure`

```yaml
platform:
  component:
    logging:
      layout: json                            # plain | json
      masker:
        enabled: true
        patterns: [phone, idCard, email, bankCard]
      operate-log:
        enabled: true
        sink: db                               # db | kafka | mongo
      mdc:
        tenant-id: true
        user-id: true
```

### 3) `Use` structured logging

```java
@Slf4j
@Service
public class OrderService {

    public void placeOrder(OrderRequest req) {
        log.info("order.placed", kv("orderId", req.getId()), kv("amount", req.getAmount()));
    }
}
```

## 🔧 Core Capabilities

### 1) `JSON` layout

```json
{
  "timestamp": "2026-07-04T12:34:56.789Z",
  "level": "INFO",
  "logger": "com.richie.app.OrderService",
  "thread": "http-nio-8080-exec-1",
  "message": "order.placed",
  "mdc": {"tenant_id": "t1", "user_id": "u1", "trace_id": "..."},
  "kv": {"orderId": "o-123", "amount": 99.5}
}
```

### 2) `Sensitive`-data masking

```java
@Slf4j
public class UserService {
    public void updatePhone(String userId, String phone) {
        log.info("user.phone.update userId={} phone={}", userId, phone);
        // Masker rewrites the rendered message:
        // user.phone.update userId=u1 phone=138****8000
    }
}
```

### 3) Operation log (`@OperateLog`)

```java
@OperateLog(module = "user", action = "update_phone", recordArgs = true)
public void updatePhone(String userId, String phone) { ... }
```

Auto-recorded fields:
- `tenant_id`, `user_id` (from `HeaderContextHolder`)
- `module`, `action`
- Request args (JSON)
- Duration, success / failure
- Exception (if any)

### 4) `MDC` propagation

```java
HeaderContextHolder.setHeader("x-tenant-id", "t1");
log.info("...");  // MDC auto-includes "tenant_id": "t1"
```

## ⚙️ Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `layout` | enum | `plain` | `plain` / `json` |
| `masker.enabled` | boolean | `true` | Enable sensitive-data masking |
| `masker.patterns` | List<String> | `[phone, idCard, email]` | Mask types to apply |
| `operate-log.enabled` | boolean | `true` | Enable `@OperateLog` AOP |
| `operate-log.sink` | enum | `db` | `db` / `kafka` / `mongo` |
| `mdc.tenant-id` | boolean | `true` | Add tenant_id to MDC |
| `mdc.user-id` | boolean | `true` | Add user_id to MDC |

## 🎯 Best Practices

1. **Use JSON in production, plain in dev** — easier debugging locally.
2. **Always set `mdc.tenant-id` + `mdc.user-id`** — multi-tenant systems require it.
3. **Mask all PII fields explicitly** — don't rely on conventions.
4. **Use `@OperateLog` for sensitive write operations** — create / update / delete, not reads.
5. **Sink operate logs to Kafka for audit** — DB sinks are useful for queries, Kafka for compliance.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **Logback only** | No Log4j2 / java.util.logging support | Stick to SLF4J + Logback |
| **No async sink batching out of the box** | High-throughput loss risk | Use Kafka appender with batching |
| **Masker is regex-based** | May miss obfuscated patterns | Write custom `LogMasker` SPI |

## ❓ FAQ

### Q1 — Does this conflict with `atlas-richie-component-desensitize-logging`?

No — desensitize-logging masks sensitive values in your *code* before they reach the log call. This component masks in the *appender layer*. They're complementary.

### Q2 — How does `@OperateLog` differ from a regular `log.info`?

`@OperateLog` is AOP — it captures entry / exit / exception automatically, structured as an audit record. `log.info` is a free-form string.

### `Q3` — `Can` `I` customize the `JSON` layout's field names?

Yes — set `platform.component.logging.field-aliases` to map standard fields.

### `Q4` — `How` do `I` send logs to `Kafka`?

Use Logback's `KafkaAppender`. Configure under `platform.component.logging.kafka.*`.

## 📚 Further Reading

- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **Desensitize (mask at code layer)** — [`../atlas-richie-component-desensitize/atlas-richie-component-desensitize-logging/README.md`](../atlas-richie-component-desensitize/atlas-richie-component-desensitize-logging/README.md)
- **Tracing** — [`../atlas-richie-component-tracing/README.md`](../atlas-richie-component-tracing/README.md)
- External: [Logback manual](https://logback.qos.ch/manual/) · [ELK stack](https://www.elastic.co/elastic-stack)

---

**atlas-richie-component-logging** 🚀
