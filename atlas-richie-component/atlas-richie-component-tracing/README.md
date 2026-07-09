# Atlas Richie Tracing Component (atlas-richie-component-tracing)

> **Dependency Management Module** — centrally manages OpenTelemetry SDK, Spring Boot Starter, and exporter versions, providing a distributed tracing dependency set ready for production use.

This module **contains no custom Java code**. It is a dependency aggregator that bundles the core OpenTelemetry ecosystem dependencies with locked versions. Teams only need to import this single module to obtain the full OTel SDK + annotations + multiple exporter support. The Spring Boot auto-configuration starter (`opentelemetry-spring-boot-starter`) is **optional** — declare it explicitly when you need auto-configuration (see Scenario B).

---

## 📖 Table of Contents

- [📖 Overview](#📖-overview)
  - [Design Purpose](#design-purpose)
  - [What This Module Is and Is Not](#what-this-module-is-and-is-not)
- [📦 Managed Dependencies Overview](#📦-managed-dependencies-overview)
  - [Core Dependencies](#core-dependencies)
  - [Exporter Dependencies](#exporter-dependencies)
  - [Spring Boot Integration](#spring-boot-integration)
  - [Optional Dependencies](#optional-dependencies)
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

| Item                 | Value                                                 |
|----------------------|-------------------------------------------------------|
| **Coordinates**      | `com.richie.component:atlas-richie-component-tracing` |
| **Category**         | Dependency Management — Distributed Tracing           |
| **Scope**            | Spring Boot 3.x / 4.x                                 |
| **Managed Versions** | OpenTelemetry SDK 1.40+ / Instrumentation BOM 2.x     |

### Design Purpose

**Why this module?** Adopting OpenTelemetry in a microservice architecture involves 8-12 dependencies (API, SDK, Spring Boot integration, exporters, annotations...). Version alignment is error-prone — `opentelemetry-api` v1.40 mixed with `opentelemetry-sdk-trace` v1.38 can introduce incompatible API changes.

This module serves as a **controlled dependency set**, allowing teams to add a single dependency and get:

- ✅ **Version locking** — all OTel dependency versions are governed by the `atlas-richie-component-dependencies` BOM, eliminating version fragmentation
- ✅ **Protocol coverage** — standard OTLP exporter + Zipkin exporter (legacy) bundled, no need to decide which exporter version to add
- ✅ **Annotations ready** — `@WithSpan` / `@SpanAttribute` usable out of the box without adding `opentelemetry-instrumentation-annotations` separately
- ✅ **Auto-configuration (optional)** — `opentelemetry-spring-boot-starter` is marked as optional; add it explicitly when you need Spring Boot auto-configuration. This avoids unwanted Connection refused errors when no OTel Collector is running
- ✅ **Optional Metrics** — `micrometer-registry-otlp` marked as optional, teams enable OTLP Metrics on demand

### What This Module Is and Is Not

| ✅ Provides                                                              | ❌ Does Not Provide                                                    |
|-------------------------------------------------------------------------|-----------------------------------------------------------------------|
| OpenTelemetry core dependency version management                        | Custom Java auto-configuration or Beans                               |
| Spring Boot auto-configuration (from OTel Starter)                      | Custom Sampler / SpanProcessor / Exporter                             |
| OTLP / Zipkin / Logging exporters                                       | Application-layer instrumentation (requires Java Agent or manual API) |
| `@WithSpan` / `@SpanAttribute` annotations                              | Console UI / rule push center                                         |
| Micrometer OTLP Metrics (optional)                                      | Log aggregation (use `atlas-richie-component-logging`)                |
| Configuration properties for service name, sampling, exporter endpoints | Profiling / Continuous Profiling                                      |

---

## 📦 Managed Dependencies Overview

All dependencies are declared in `pom.xml`. Below is a breakdown by functional group with purpose and typical usage.

### Core Dependencies

| Dependency                                  | Purpose                                                                                     | When It Is Used                                                                 |
|---------------------------------------------|---------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `opentelemetry-api`                         | OTel interface definitions — `Tracer`, `Span`, `OpenTelemetry`                              | Any code using OTel API (manual spans, instrumentation)                         |
| `opentelemetry-sdk`                         | Full SDK (trace + metrics + logs)                                                           | Required for production operation                                               |
| `opentelemetry-sdk-trace`                   | Trace-specific SDK (`SdkTracerProvider`, `SpanProcessor`)                                   | Used when SDK auto-configuration is active                                      |
| `opentelemetry-sdk-extension-autoconfigure` | Initializes SDK from env vars / `otel.*` configuration                                      | Foundation for `OTEL_SERVICE_NAME`, `OTEL_TRACES_EXPORTER` etc. to take effect  |
| `opentelemetry-semconv`                     | Semantic convention constants — `SemanticAttributes.*` (e.g. `HTTP_METHOD`, `DB_STATEMENT`) | Setting standardized attribute names in manual spans                            |
| `opentelemetry-instrumentation-annotations` | `@WithSpan`, `@SpanAttribute` annotations                                                   | When annotation-based instrumentation is needed (with Java Agent or Spring AOP) |

### Exporter Dependencies

| Dependency                       | Protocol                    | Scenario                                                                            |
|----------------------------------|-----------------------------|-------------------------------------------------------------------------------------|
| `opentelemetry-exporter-otlp`    | OTLP (gRPC + HTTP/protobuf) | **Preferred** — send to OTel Collector / Jaeger / Tempo / Uptrace etc.              |
| `opentelemetry-exporter-zipkin`  | Zipkin JSON (HTTP)          | **Legacy** — use when the backend only supports Zipkin (deprecated by OTel project) |
| `opentelemetry-exporter-logging` | Console output              | **Dev/Test** — spans printed to logs, not sent to any backend                       |

### Spring Boot Integration

| Dependency                          | Purpose                        | Scope    | Notes                                                                                     |
|-------------------------------------|--------------------------------|----------|-------------------------------------------------------------------------------------------|
| `opentelemetry-spring-boot-starter` | Spring Boot auto-configuration | optional | Provides `OpenTelemetry` Bean, auto-registers `SdkTracerProvider`, `OtlpHttpSpanExporter`. Not included transitively — declare explicitly to enable |

**What the Starter auto-configures** (from `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter`):

- Automatically creates `OpenTelemetry` / `SdkTracerProvider` Beans
- Reads configuration from `otel.*` env vars / `application.yml` (service name, sampling rate, exporter endpoint)
- Auto-registers `OtlpHttpSpanExporter` (or gRPC variant)
- AOP instrumentation for `spring-web` / `spring-webmvc` / `spring-webflux`
- Supports programmatic SDK customization (`AutoConfigurationCustomizerProvider`)

### Optional Dependencies

| Dependency                          | Purpose                            | Activation                                  |
|-------------------------------------|------------------------------------|---------------------------------------------|
| `opentelemetry-spring-boot-starter` | Spring Boot auto-configuration     | Declare explicitly in the project `pom.xml` |
| `micrometer-registry-otlp`          | Export Micrometer metrics via OTLP | Declare explicitly in the project `pom.xml` |

---

## 🔧 Usage Scenarios

OpenTelemetry integration in Spring Boot follows three routes. **Choose based on the level of automatic instrumentation you need:**

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

**Suitable for**: Existing Spring Boot applications where you want zero code changes and full automatic instrumentation for HTTP / JDBC / messaging / gRPC / etc.

```
# Start with javaagent (download required separately)
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.service.name=my-app \
     -Dotel.traces.exporter=otlp \
     -Dotel.exporter.otlp.endpoint=http://otel-collector:4317 \
     -jar my-app.jar
```

**Pros**: Bytecode instrumentation for 150+ libraries (Spring MVC, WebFlux, JDBC, JPA, Kafka, gRPC, HTTP clients...), completely non-intrusive.

**Cons**:
- ❌ No GraalVM Native Image support
- ❌ Agent startup adds overhead (typically 50-200ms)
- ❌ Cannot configure via `application.yml` (use env vars / system properties)
- ❌ Potential conflicts with multiple agents

**What this module provides**: Even with the Java Agent, this module supplies API + SDK + annotations for adding custom spans in code (`@WithSpan` / `tracer.spanBuilder()`).

### Scenario B: Spring Boot Starter — Code-First Integration (Recommended)

**Suitable for**: Newer Spring Boot (3.x+) applications that need type-safe configuration, GraalVM Native support, or environments where the Agent approach is restricted.

**Steps**:

1. **Dependencies**

The starter is now optional — add both the tracing module and the starter explicitly:

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-tracing</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>
```

2. **Configure `application.yml`**

```yaml
# OTel environment variable approach (recommended — decoupled from deployment)
otel:
  service.name: my-app
  traces.exporter: otlp
  exporter:
    otlp:
      endpoint: http://otel-collector:4317
      protocol: grpc

# Or Spring Boot managed properties approach
management:
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://otel-collector:4318/v1/traces
  tracing:
    sampling:
      probability: 0.1
```

3. **Done — traces appear automatically**

Auto-configuration from the Spring Boot Starter covers:
- **HTTP server** — `@RestController` / `@Controller` auto-generate spans
- **HTTP client** — `RestTemplate`, `RestClient`, `WebClient`
- **JDBC** — datasource operations auto-generate spans
- **Logging** — `trace_id` / `span_id` injected automatically

**Value of this module**: Teams don't manage 8+ OTel dependency versions — they import one module, and all versions are locked by the `dependencies` BOM.

### Scenario C: Manual API / Annotations — Fine-Grained Control

**Suitable for**: Instrumenting only specific business methods without full auto-instrumentation, or adding custom spans on top of the Java Agent.

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

**Suitable for**: Cases where you don't need full traces (no OTel Collector), only need `traceId` in response headers and MDC for log correlation.

Use [`atlas-richie-component-web-core`](../atlas-richie-component-web/README.md) **§3 Trace ID Propagation**:

```yaml
platform.component.web.tracing.enabled=true
```

This mode requires **no OTel Collector**, does not start an exporter, and produces **no spans**. It does one thing: generates a `traceId` at the front of the servlet interceptor chain, writing it to the `X-Trace-Id` response header + SLF4J MDC. Combined with `atlas-richie-component-logging`, logs automatically contain `trace_id` for correlation.

**When to choose this**:
- No need for call-chain topology (flame graphs, span details)
- Only need traceId for log indexing
- Simple request chains (1-2 hops), no cross-service cross-referencing needed

**Relationship**: The `web-core` trace propagation and this `tracing` module **can coexist**. `web-core` injects traceId at the servlet container layer (ORDER=50, frontmost), while `tracing` provides the full OTel SDK + export capability. When both are enabled, the traceId generated by `web-core` is naturally inherited by the OTel SDK as span context — complementary, not conflicting.

---

## ⚙️ Configuration Reference

This module defines no configuration properties itself (zero custom code). All configuration comes from `opentelemetry-spring-boot-starter` and Spring Boot's native OTel support.

### OTel Environment Variables (Recommended — Deployment Decoupled)

| Property                      | Example Value                             | Description             |
|-------------------------------|-------------------------------------------|-------------------------|
| `OTEL_SERVICE_NAME`           | `my-app`                                  | Service name, required  |
| `OTEL_TRACES_EXPORTER`        | `otlp` / `zipkin` / `none`                | Trace exporter          |
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
  tracing:
    sampling:
      probability: 0.1
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/traces
    logging:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/logs
  otlp:
    metrics:
      export:
        url: http://localhost:4318/v1/metrics
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

1. **Use Scenario B as your primary route** — Spring Boot Starter + `application.yml` for maintainability and flexibility
2. **Java Agent is for legacy app retrofitting** — use Agent when you can't change code; use Starter for new projects
3. **Set 10% sampling in production** — `probability: 0.1` is sufficient for statistical observability and greatly reduces storage cost
4. **Add `@WithSpan` at cross-service boundaries** — mark key interfaces (e.g. `@WithSpan("PaymentService.charge")`) so the call chain has semantic names at critical nodes
5. **Use OTel semantic attributes** — use `SemanticAttributes.HTTP_METHOD` instead of `"http_method"` to ensure backend compatibility
6. **Correlate logs with traces** — combine with `atlas-richie-component-logging` for JSON logs containing `trace_id`, `span_id`
7. **Do not put PII in span names/attributes** — emails, phone numbers and other sensitive data should not appear in spans
8. **Coexisting with `web-core` trace propagation** — `web-core` injects traceId first, OTel SDK inherits it; spans automatically include `http.method`, `http.target` etc.
9. **Zipkin exporter is deprecated** — new projects should use the OTLP exporter (`otlp`), compatible with Jaeger / Tempo / Uptrace / Grafana and all major backends

---

## ⚠️ Known Limitations

| Limitation                                         | Impact                                                                       | Notes                                                                 |
|----------------------------------------------------|------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| **This module does not include the Java Agent**    | No bytecode-level auto-instrumentation                                       | Download `opentelemetry-javaagent.jar` separately                     |
| **Starter's instrumentation scope is limited**     | Only Spring Web / JDBC etc.                                                  | Deep instrumentation (Kafka / gRPC / Redis) needs Agent or manual API |
| **OTLP gRPC exporter**                             | More dependencies, slightly slower startup                                   | Use `http/protobuf` by default to avoid this                          |
| **Zipkin exporter deprecated**                     | May be removed in future OTel versions                                       | Use OTLP for new projects                                             |
| **Micrometer integration requires Spring Boot 4+** | `management.opentelemetry.*` properties may be incomplete in Spring Boot 3.x | Check the official docs for your specific version                     |

---

## ❓ Frequently Asked Questions

### Q1: Java Agent or Spring Boot Starter — which should I choose?

| Factor                   | Agent                         | Starter                                               |
|--------------------------|-------------------------------|-------------------------------------------------------|
| Code changes             | Zero                          | Add dependency + configure                            |
| Instrumentation coverage | 150+ libraries auto           | Spring Web / JDBC / Logging                           |
| GraalVM Native           | ❌                             | ✅                                                     |
| Configuration            | Env vars / system props       | `application.yml`                                     |
| Startup overhead         | 50-200ms                      | None                                                  |
| Fine control             | Weak (needs programmatic API) | Strong (custom `AutoConfigurationCustomizerProvider`) |

**Bottom line**: Agent = "fully automatic data, env-only configuration". Starter = "basic auto-instrumentation with type-safe control". **New projects should choose Starter; existing projects should try the Agent first**.

### Q2: Does OTel SDK activate automatically when I import this module?

Yes. `opentelemetry-spring-boot-starter` activates upon being on the classpath (condition: Spring Boot 3.x+, no manually defined `OpenTelemetry` Bean detected). You must also set `OTEL_SERVICE_NAME` or `otel.service.name` — otherwise traces have no service name.

### Q3: What if I need more instrumentation (Kafka / Redis / gRPC)?

Option 1: Java Agent (covers Kafka, Redis, gRPC automatically);
Option 2: Manually add the corresponding instrumentation library (e.g. `opentelemetry-instrumentation-kafka-clients`);
Option 3: Mark key methods manually with `@WithSpan`.

### Q4: How do I get trace ID into logs?

Use `atlas-richie-component-logging`: the JSON log layout automatically injects `trace_id` / `span_id`. See that component's README for details.

### Q5: How do I use Jaeger instead of OTel Collector?

Set `OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317` — Jaeger natively accepts OTLP gRPC. No separate Zipkin bridge is needed.

### Q6: Can I trace only a subset of requests?

Yes, control this via `Sampler`:
- Env var: `OTEL_TRACES_SAMPLER=traceidratio` + `OTEL_TRACES_SAMPLER_ARG=0.1`
- Spring Boot property: `management.tracing.sampling.probability=0.1`
- Programmatic: implement `AutoConfigurationCustomizerProvider` with a custom `Sampler`

---

## 📚 Related Documentation

- **Parent module** — [`../README.md`](../README.md)
- **Web-Core Trace Propagation** — [`../atlas-richie-component-web/README.md`](../atlas-richie-component-web/README.md) §3
- **Logging** — [`../atlas-richie-component-logging/README.md`](../atlas-richie-component-logging/README.md)
- **OpenTelemetry Official**:
  - [Spring Boot Starter](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/)
  - [Java Agent](https://opentelemetry.io/docs/zero-code/java/agent/)
  - [Java API / SDK](https://opentelemetry.io/docs/languages/java/)
  - [Spring Boot 4 OTel Support](https://docs.spring.io/spring-boot/reference/actuator/observability.html)

---

**atlas-richie-component-tracing** — Dependency managed, version consistent, tracing out of the box.
