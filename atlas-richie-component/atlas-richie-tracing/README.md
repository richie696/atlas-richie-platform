# Atlas Richie Tracing Component (atlas-richie-tracing)

> **Dependency Management Module** — provides the official OpenTelemetry Spring Boot Starter with one aligned
> instrumentation/SDK release line and a single configuration entry point.

This module **contains no custom Java code**. It is a dependency aggregator that bundles the core OpenTelemetry
ecosystem dependencies with locked versions. Teams only need to import this single module to obtain the OTel Starter,
API, annotations, semantic conventions, SDK auto-configuration, and the standard OTLP exporters it owns.

---

## 📖 Table of Contents

- [📖 Overview](#📖-overview)
    - [Design Purpose](#design-purpose)
    - [What This Module Is and Is Not](#what-this-module-is-and-is-not)
- [📦 Managed Dependencies Overview](#📦-managed-dependencies-overview)
    - [Core Dependencies](#core-dependencies)
    - [Exporter Dependencies](#exporter-dependencies)
    - [Spring Boot Integration](#spring-boot-integration)
- [🔧 Usage Scenarios](#🔧-usage-scenarios)
    - [Scenario A: Java Agent — Zero-Code Full Instrumentation](#scenario-a-java-agent--zero-code-full-instrumentation)
    - [Scenario B: Spring Boot Starter — Code-First Integration (Recommended)](#scenario-b-spring-boot-starter--code-first-integration-recommended)
    - [Scenario C: Manual API / Annotations — Fine-Grained Control](#scenario-c-manual-api--annotations--fine-grained-control)
    - [Scenario D: Trace ID Propagation Only — Use `web-core`](#scenario-d-trace-id-propagation-only--use-web-core)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ Frequently Asked Questions](#❓-frequently-asked-questions)
- [📚 Related Documentation](#📚-related-documentation)

---

## 📖 Overview

| Item                 | Value                                                   |
|----------------------|---------------------------------------------------------|
| **Coordinates**      | `cn.richie696.component:atlas-richie-tracing` |
| **Category**         | Dependency Management — Distributed Tracing             |
| **Scope**            | Spring Boot 3.x / 4.x                                   |
| **Managed Versions** | OpenTelemetry SDK 1.65.0 / Instrumentation 2.31.1       |

### Design Purpose

**Why this module?** Adopting OpenTelemetry in a microservice architecture involves 8-12 dependencies (API, SDK, Spring
Boot integration, exporters, annotations...). Version alignment is error-prone — `opentelemetry-api` v1.40 mixed with
`opentelemetry-sdk-trace` v1.38 can introduce incompatible API changes.

This module serves as a **controlled dependency set**, allowing teams to add a single dependency and get:

- ✅ **Version locking** — all OTel dependency versions are governed by the `atlas-richie-component-dependencies` BOM,
  eliminating version fragmentation
- ✅ **Standard protocol** — OTLP is the single exporter path; the Starter owns its compatible exporter versions
- ✅ **Annotations ready** — `@WithSpan` / `@SpanAttribute` usable out of the box without adding
  `opentelemetry-instrumentation-annotations` separately
- ✅ **Auto-configuration** — `opentelemetry-spring-boot-starter` is included transitively and is controlled by
  `otel.sdk.disabled` / `OTEL_SDK_DISABLED`
- ✅ **Full signal configuration** — traces, metrics, and logs use the standard `otel.*` / `OTEL_*` configuration

### What This Module Is and Is Not

| ✅ Provides                                                             | ❌ Does Not Provide                                                   |
|-------------------------------------------------------------------------|-----------------------------------------------------------------------|
| OpenTelemetry core dependency version management                        | Custom Java auto-configuration or Beans                               |
| Spring Boot auto-configuration (from OTel Starter)                      | Custom Sampler / SpanProcessor / Exporter                             |
| OTLP exporters supplied by the official Starter                         | Application-layer instrumentation (requires Java Agent or manual API) |
| `@WithSpan` / `@SpanAttribute` annotations                              | Console UI / rule push center                                         |
| Starter-based Micrometer metrics bridge (explicitly enable it)          | Log aggregation (use `atlas-richie-logging`)                |
| Configuration properties for service name, sampling, exporter endpoints | Profiling / Continuous Profiling                                      |

---

## 📦 Managed Dependencies Overview

All dependencies are declared in `pom.xml`. Below is a breakdown by functional group with purpose and typical usage.

### Core Dependencies

| Dependency                                  | Purpose                                                                                     | When It Is Used                                                                 |
|---------------------------------------------|---------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `opentelemetry-api`                         | OTel interface definitions — `Tracer`, `Span`, `OpenTelemetry`                              | Any code using OTel API (manual spans, instrumentation)                         |
| `opentelemetry-semconv`                     | Semantic convention constants — `SemanticAttributes.*` (e.g. `HTTP_METHOD`, `DB_STATEMENT`) | Setting standardized attribute names in manual spans                            |
| `opentelemetry-instrumentation-annotations` | `@WithSpan`, `@SpanAttribute` annotations                                                   | When annotation-based instrumentation is needed (with Java Agent or Spring AOP) |

The SDK, SDK auto-configuration, and exporters are transitive implementation details of the official Starter. They are
intentionally not declared again by this component.

### Exporter Dependencies

| Dependency                       | Protocol                    | Scenario                                                                            |
|----------------------------------|-----------------------------|-------------------------------------------------------------------------------------|
| Starter-managed OTLP exporter    | OTLP (gRPC or HTTP/protobuf) | **Preferred** — send to an OTel Collector or any OTLP-compatible backend             |

### Spring Boot Integration

| Dependency                          | Purpose                        | Scope    | Notes                                                                                                                                               |
|-------------------------------------|--------------------------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `opentelemetry-spring-boot-starter` | Spring Boot auto-configuration | required | Provides `OpenTelemetry` Bean and owns SDK/exporter auto-configuration |

**What the Starter auto-configures** (from `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter`):

- Automatically creates `OpenTelemetry` / `SdkTracerProvider` Beans
- Reads configuration from `otel.*` env vars / `application.yml` (service name, sampling rate, exporter endpoint)
- Auto-registers `OtlpHttpSpanExporter` (or gRPC variant)
- AOP instrumentation for `spring-web` / `spring-webmvc` / `spring-webflux`
- Supports programmatic SDK customization (`AutoConfigurationCustomizerProvider`)

---

## 🔧 Usage Scenarios

OpenTelemetry integration in Spring Boot follows three routes. **Choose based on the level of automatic instrumentation
you need:**

```
                    ┌────────────────────────────────────────┐
                    │  Java Agent                             │
                    │  Zero-code · 150+ libraries · Fully auto│
                    │  ❌ No Native Image support             │
                    └────────────────────────────────────────┘

                    ┌────────────────────────────────────────┐
                    │  Spring Boot Starter                    │
                    │  Code dependency · AOP instrumentation  │
                    │  ✅ Native Image support                │
                    └────────────────────────────────────────┘

                    ┌────────────────────────────────────────┐
                    │  Manual API / @WithSpan                 │
                    │  Fine-grained control · No auto-inst    │
                    └────────────────────────────────────────┘

                    ┌────────────────────────────────────────┐
                    │  web-core Trace ID Propagation         │
                    │  Lightweight — trace ID only            │
                    └────────────────────────────────────────┘
```

### Scenario A: Java Agent — Zero-Code Full Instrumentation

**Suitable for**: Existing Spring Boot applications where you want zero code changes and full automatic instrumentation
for HTTP / JDBC / messaging / gRPC / etc.

```
# Start with javaagent (download required separately)
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.service.name=my-app \
     -Dotel.traces.exporter=otlp \
     -Dotel.exporter.otlp.endpoint=http://otel-collector:4317 \
     -jar my-app.jar
```

**Pros**: Bytecode instrumentation for 150+ libraries (Spring MVC, WebFlux, JDBC, JPA, Kafka, gRPC, HTTP clients...),
completely non-intrusive.

**Cons**:

- ❌ No GraalVM Native Image support
- ❌ Agent startup adds overhead (typically 50-200ms)
- ❌ Cannot configure via `application.yml` (use env vars / system properties)
- ❌ Potential conflicts with multiple agents

**What this module provides**: Even with the Java Agent, this module supplies API + SDK + annotations for adding custom
spans in code (`@WithSpan` / `tracer.spanBuilder()`).

### Scenario B: Spring Boot Starter — Code-First Integration (Recommended)

**Suitable for**: Newer Spring Boot (3.x+) applications that need type-safe configuration, GraalVM Native support, or
environments where the Agent approach is restricted.

**Steps**:

1. **Dependencies**

The tracing module already contains the Starter; add only this dependency:

```xml
<dependency>
    <groupId>cn.richie696.component</groupId>
    <artifactId>atlas-richie-tracing</artifactId>
</dependency>
```

2. **Configure `application.yml`**

```yaml
# Standard OTel configuration. The same keys can be supplied by Nacos.
otel:
  sdk:
    disabled: ${OTEL_SDK_DISABLED:true}
  service:
    name: ${OTEL_SERVICE_NAME:${spring.application.name:unknown_service}}
  traces:
    exporter: ${OTEL_TRACES_EXPORTER:otlp}
    sampler: ${OTEL_TRACES_SAMPLER:parent_based_traceidratio}
    sampler.arg: ${OTEL_TRACES_SAMPLER_ARG:0.1}
  metrics:
    exporter: ${OTEL_METRICS_EXPORTER:otlp}
  logs:
    exporter: ${OTEL_LOGS_EXPORTER:otlp}
  exporter:
    otlp:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://otel-collector:4318}
      protocol: ${OTEL_EXPORTER_OTLP_PROTOCOL:http/protobuf}
  instrumentation:
    micrometer:
      enabled: ${OTEL_INSTRUMENTATION_MICROMETER_ENABLED:true}
```

`otel.sdk.disabled` is evaluated during Spring context bootstrap. It is therefore a startup switch: a Nacos change
requires an application restart to rebuild the OTel SDK. It does not dynamically replace the SDK in a running context.
When declarative OTel configuration (`otel.file_format`) is enabled, use `otel.disabled` instead.

3. **Done — traces appear automatically**

Auto-configuration from the Spring Boot Starter covers:

- **HTTP server** — `@RestController` / `@Controller` auto-generate spans
- **HTTP client** — `RestTemplate`, `RestClient`, `WebClient`
- **JDBC** — datasource operations auto-generate spans
- **Logging** — `trace_id` / `span_id` injected automatically

**Value of this module**: Teams don't manage 8+ OTel dependency versions — they import one module, and all versions are
locked by the `dependencies` BOM.

### Scenario C: Manual API / Annotations — Fine-Grained Control

**Suitable for**: Instrumenting only specific business methods without full auto-instrumentation, or adding custom spans
on top of the Java Agent.

**Approach 1: Annotations**

```java
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;

@Service
public class OrderService {

    @WithSpan("OrderService.placeOrder")
    public Order placeOrder(@SpanAttribute("order.id") String orderId,
                            @SpanAttribute("order.amount") BigDecimal amount) {
        // Span auto-created, auto-closed on return
        return doPlace(orderId, amount);
    }
}
```

**Approach 2: Manual spans**

```java
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final Tracer tracer;

    public Payment charge(PaymentRequest req) {
        Span span = tracer.spanBuilder("PaymentService.charge")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("payment.amount", req.getAmount())
                .startSpan();
        try (var scope = span.makeCurrent()) {
            return paymentGateway.charge(req);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

**Dependencies this module provides**:

- `opentelemetry-api` — `Tracer`, `Span`, `StatusCode`, etc.
- `opentelemetry-instrumentation-annotations` — `@WithSpan`, `@SpanAttribute`
- `opentelemetry-semconv` — `SemanticAttributes.*` constants ensuring standard attribute names

### Scenario D: Trace ID Propagation Only — Use `web-core`

**Suitable for**: Cases where you don't need full traces (no OTel Collector), only need `traceId` in response headers
and MDC for log correlation.

Use [`web-core`](../atlas-richie-web-parent/README.md) **§3 Trace ID Propagation**:

```yaml
platform.component.web.tracing.enabled=true
```

This mode requires **no OTel Collector**, does not start an exporter, and produces **no spans**. It does one thing:
generates a `traceId` at the front of the servlet interceptor chain, writing it to the `X-Trace-Id` response header +
SLF4J MDC. Combined with `atlas-richie-logging`, logs automatically contain `trace_id` for correlation.

**When to choose this**:

- No need for call-chain topology (flame graphs, span details)
- Only need traceId for log indexing
- Simple request chains (1-2 hops), no cross-service cross-referencing needed

**Relationship**: The `web-core` trace propagation and this `tracing` module **can coexist**. `web-core` injects traceId
at the servlet container layer (ORDER=50, frontmost), while `tracing` provides the full OTel SDK + export capability.
When both are enabled, the traceId generated by `web-core` is naturally inherited by the OTel SDK as span context —
complementary, not conflicting.

---

## ⚙️ Configuration Reference

This module defines no configuration properties itself (zero custom code). All signal configuration comes from the
official `opentelemetry-spring-boot-starter`; there is no component-specific exporter or SDK switch.

The former `platform.cache.redis.stream.tracing.*` and `management.otlp.metrics.*` settings are not read anymore. Use
the standard `otel.*` keys below so every component shares the same SDK, sampler, and exporter configuration.

### OTel Environment Variables (Recommended — Deployment Decoupled)

| Property                      | Example Value                             | Description             |
|-------------------------------|-------------------------------------------|-------------------------|
| `OTEL_SERVICE_NAME`           | `my-app`                                  | Service name, required  |
| `OTEL_TRACES_EXPORTER`        | `otlp` / `none`                           | Trace exporter          |
| `OTEL_METRICS_EXPORTER`       | `otlp` / `none`                           | Metrics exporter        |
| `OTEL_LOGS_EXPORTER`          | `otlp` / `none`                           | Logs exporter           |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://otel-collector:4317`              | OTLP backend address    |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` / `http/protobuf`                  | OTLP transport protocol |
| `OTEL_EXPORTER_OTLP_HEADERS`  | `api-key=xxx`                             | OTLP request headers    |
| `OTEL_TRACES_SAMPLER`         | `parent_based_always_on` / `traceidratio` | Sampler type            |
| `OTEL_TRACES_SAMPLER_ARG`     | `0.1`                                     | Sampling ratio          |

### Spring Boot `application.yml` Approach

```yaml
spring:
  application:
    name: my-app

management:
otel:
  sdk:
    disabled: ${OTEL_SDK_DISABLED:true}
  service:
    name: ${OTEL_SERVICE_NAME:${spring.application.name:unknown_service}}
  traces:
    exporter: ${OTEL_TRACES_EXPORTER:otlp}
    sampler: ${OTEL_TRACES_SAMPLER:parent_based_traceidratio}
    sampler.arg: ${OTEL_TRACES_SAMPLER_ARG:0.1}
  metrics:
    exporter: ${OTEL_METRICS_EXPORTER:otlp}
  logs:
    exporter: ${OTEL_LOGS_EXPORTER:otlp}
  exporter:
    otlp:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}
      protocol: ${OTEL_EXPORTER_OTLP_PROTOCOL:http/protobuf}
  instrumentation:
    micrometer:
      enabled: ${OTEL_INSTRUMENTATION_MICROMETER_ENABLED:true}
```

### Sampling Strategy Quick Reference

| Scenario                             | Recommended Configuration                     | Notes                                                         |
|--------------------------------------|-----------------------------------------------|---------------------------------------------------------------|
| Development / Debug                  | `always_on` or `probability: 1.0`             | 100% sampling, no traces missed                               |
| Production (low traffic, <100 req/s) | `parent_based_always_on`                      | Inherits parent's sampling decision, root span always sampled |
| Production (high traffic)            | `traceidratio: 0.1` or `probability: 0.1`     | 10% random sampling, sufficient for statistics                |
| Production (critical path 100%)      | `parent_based_ratio: 1.0` + `@WithSpan` marks | Critical methods always sampled, others inherit               |
| Fully disabled                       | `none`                                        | Trace export disabled (SDK still loads but does not send)     |

---

## 🎯 Best Practices

1. **Use Scenario B as your primary route** — Spring Boot Starter + `application.yml` for maintainability and
   flexibility
2. **Java Agent is for legacy app retrofitting** — use Agent when you can't change code; use Starter for new projects
3. **Set 10% sampling in production** — `probability: 0.1` is sufficient for statistical observability and greatly
   reduces storage cost
4. **Add `@WithSpan` at cross-service boundaries** — mark key interfaces (e.g. `@WithSpan("PaymentService.charge")`) so
   the call chain has semantic names at critical nodes
5. **Use OTel semantic attributes** — use `SemanticAttributes.HTTP_METHOD` instead of `"http_method"` to ensure backend
   compatibility
6. **Correlate logs with traces** — combine with `atlas-richie-logging` for JSON logs containing `trace_id`,
   `span_id`
7. **Do not put PII in span names/attributes** — emails, phone numbers and other sensitive data should not appear in
   spans
8. **Coexisting with `web-core` trace propagation** — `web-core` injects traceId first, OTel SDK inherits it; spans
   automatically include `http.method`, `http.target` etc.
9. **Use OTLP as the only exporter path** — route traces, metrics, and logs through an OTel Collector when possible

---

## ⚠️ Known Limitations

| Limitation                                         | Impact                                                                       | Notes                                                                 |
|----------------------------------------------------|------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| **This module does not include the Java Agent**    | No bytecode-level auto-instrumentation                                       | Download `opentelemetry-javaagent.jar` separately                     |
| **Starter's instrumentation scope is limited**     | Only Spring Web / JDBC etc.                                                  | Deep instrumentation (Kafka / gRPC / Redis) needs Agent or manual API |
| **OTLP gRPC exporter**                             | More dependencies, slightly slower startup                                   | Use `http/protobuf` by default to avoid this                          |
| **Micrometer bridge is opt-in**                    | Micrometer metrics are not exported unless enabled                          | Set `otel.instrumentation.micrometer.enabled=true`                    |
| **Nacos switch is startup-scoped**                 | Changing the property does not rebuild a running SDK                        | Restart the application after changing `otel.sdk.disabled`             |

---

## ❓ Frequently Asked Questions

### Q1: Java Agent or Spring Boot Starter — which should I choose?

| Factor                   | Agent                         | Starter                                               |
|--------------------------|-------------------------------|-------------------------------------------------------|
| Code changes             | Zero                          | Add dependency + configure                            |
| Instrumentation coverage | 150+ libraries auto           | Spring Web / JDBC / Logging                           |
| GraalVM Native           | ❌                            | ✅                                                    |
| Configuration            | Env vars / system props       | `application.yml`                                     |
| Startup overhead         | 50-200ms                      | None                                                  |
| Fine control             | Weak (needs programmatic API) | Strong (custom `AutoConfigurationCustomizerProvider`) |

**Bottom line**: Agent = "fully automatic data, env-only configuration". Starter = "basic auto-instrumentation with
type-safe control". **New projects should choose Starter; existing projects should try the Agent first**.

### Q2: Does OTel SDK activate automatically when I import this module?

Yes. `opentelemetry-spring-boot-starter` is transitively included and activates on the classpath when no application
`OpenTelemetry` Bean overrides it. Set `otel.service.name` (or `OTEL_SERVICE_NAME`) and choose exporters explicitly.

### Q3: What if I need more instrumentation (Kafka / Redis / gRPC)?

Option 1: Java Agent (covers Kafka, Redis, gRPC automatically); Option 2: Manually add the corresponding instrumentation
library (e.g. `opentelemetry-instrumentation-kafka-clients`); Option 3: Mark key methods manually with `@WithSpan`.

### Q4: How do I get trace ID into logs?

Use `atlas-richie-logging`: the JSON log layout automatically injects `trace_id` / `span_id`. See that
component's README for details.

### Q5: How do I use Jaeger instead of OTel Collector?

Set `OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317` and `OTEL_EXPORTER_OTLP_PROTOCOL=grpc` — Jaeger accepts OTLP.

### Q6: Can I trace only a subset of requests?

Yes, control this via `Sampler`:

- Env var: `OTEL_TRACES_SAMPLER=traceidratio` + `OTEL_TRACES_SAMPLER_ARG=0.1`
- Spring Boot configuration: `otel.traces.sampler=traceidratio` + `otel.traces.sampler.arg=0.1`
- Programmatic: implement `AutoConfigurationCustomizerProvider` with a custom `Sampler`

---

## 📚 Related Documentation

- **Parent module** — [`../README.md`](../README.md)
- **Web-Core Trace Propagation** — [`../atlas-richie-web-parent/README.md`](../atlas-richie-web-parent/README.md)
  §3
- **Logging** — [`../atlas-richie-logging/README.md`](../atlas-richie-logging/README.md)
- **OpenTelemetry Official**:
    - [Spring Boot Starter](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/)
    - [Java Agent](https://opentelemetry.io/docs/zero-code/java/agent/)
    - [Java API / SDK](https://opentelemetry.io/docs/languages/java/)
    - [Spring Boot 4 OTel Support](https://docs.spring.io/spring-boot/reference/actuator/observability.html)

---

**atlas-richie-tracing** — Dependency managed, version consistent, tracing out of the box.
