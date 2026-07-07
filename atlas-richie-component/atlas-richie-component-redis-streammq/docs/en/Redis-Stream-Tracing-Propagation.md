# Redis Stream - OpenTelemetry Trace Propagation Guide

This document explains how the current component propagates OpenTelemetry context (traceId, spanId, sampled) through the complete "publish → poll → consume" chain, and provides the core flow diagrams along with key logic descriptions.

## 1. Design Goals
- End-to-end: the publisher generates (or continues) a Span, allowing the upstream and downstream stages (puller, consumer) to continue the same trace.
- Non-intrusive: business message classes don't need to implement extra interfaces, and business fields remain untouched.
- Standardized: OTel TextMapPropagator is used to inject and extract context.

## 2. Key Participants
- `RedisStreamManager.publish(...)`: publisher entry point, creates a Publisher Span and injects OTel context into the message wrapper.
- `TraceableMessageWrapper`: transparent wrapper, responsible for injecting and extracting trace context (via TextMapPropagator).
- `RedisStreamReactor`: puller, forwards Redis records to the in-application EventBus.
- `AbstractStreamConsumer`: consumer, extracts context from the event and creates/continues the Consumer Span.

## 3. Trace Propagation Sequence Diagram

```mermaid
sequenceDiagram
  autonumber
  participant APP as "App Business"
  participant MGR as "RedisStreamManager"
  participant OTel as "OpenTelemetry"
  participant REDIS as "Redis(Streams)"
  participant REACT as "RedisStreamReactor"
  participant BUS as "EventBus"
  participant CONS as "AbstractStreamConsumer"

  APP->>MGR: "publish(streamKey, dto)"
  MGR->>OTel: "Create Publisher Span"
  MGR->>MGR: "TraceableMessageWrapper.wrapForPublish(dto)"
  Note right of MGR: "Use TextMapPropagator to inject<br/>traceId/spanId/sampled into traceContext"
  MGR->>REDIS: "XADD add(record)"
  REDIS-->>MGR: "recordId"
  MGR->>OTel: "Span attributes (recordId,duration,traceId,spanId)"

  REACT->>REDIS: "XREADGROUP pollOnce"
  REDIS-->>REACT: "entries"
  REACT->>BUS: "Publish StreamMessageEvent(payload+traceContext)"

  CONS->>BUS: "Subscribe to messages"
  CONS->>OTel: "Extract traceContext → Create/continue Consumer Span"
  CONS->>CONS: "handle(payload, ctx)"
  CONS->>OTel: "Span attributes (duration/success/exception)"

  %% Style configuration
  %%{init: {'theme':'base', 'themeVariables': { 'primaryColor': '#ffe6f0', 'primaryTextColor': '#000', 'primaryBorderColor': '#999', 'lineColor': '#666', 'actorTextColor': '#000', 'actorLineColor': '#000', 'actorBkg': '#e6ffe6', 'activationBkgColor': '#f0e6ff', 'activationBorderColor': '#8000ff'}}}%%
```

## 4. Publisher Core Logic

```mermaid
flowchart TD
  A["Message publish entry:<br/>RedisStreamManager.publish<br/>Start the publish flow"] --> B["Create Publisher Span<br/>Begin trace tracking"]
  B --> C["wrapForPublish<br/>Wrap message and inject trace context<br/>(dto, openTelemetry)"]
  C -->|"TextMapPropagator.inject<br/>Inject trace info"| D["traceContext inject into Map<br/>Write traceId/spanId into context"]
  D --> E["opsForStream.add(record)<br/>Execute Redis Stream write"]
  E --> F["Record publish metrics<br/>Statistics on count and duration"]
  F --> G["Set Span attributes<br/>recordId, duration,<br/>traceId/spanId"]

  %% Color style definition
  style A fill:#ffe,stroke:#fa0,color:#000
  style B fill:#e6f3ff,stroke:#0066cc,color:#000
  style C fill:#f0e6ff,stroke:#8000ff,color:#000
  style D fill:#f0e6ff,stroke:#8000ff,color:#000
  style E fill:#e6ffe6,stroke:#009900,color:#000
  style F fill:#fff2cc,stroke:#cc9900,color:#000
  style G fill:#e6f3ff,stroke:#0066cc,color:#000

  %% CSS class definition
  classDef default font-size:11px;
  classDef longText font-size:10px,min-width:160px;
  
  class A,B,C,D,E,F,G longText;
```

Key points:
- Inside `TraceableMessageWrapper.wrapForPublish(dto, otel)`:
  - Reads the current `Span.current().getSpanContext()`;
  - Uses `openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), traceContext, setter)` to inject;
  - Business objects are not modified; the propagated data lives in the wrapper's `traceContext` Map.

## 5. Consumer Core Logic

```mermaid
flowchart TD
  A["Receive StreamMessageEvent(payload, traceContext)"] --> B{"Does traceContext exist?"}
  B -- Yes --> C["TextMapPropagator.extract"]
  C --> D["Create Consumer Span (continue)"]
  B -- No --> E["Create new Consumer Span"]
  D --> F["handle(payload, ctx)"]
  E --> F["handle(payload, ctx)"]
  F --> G["Span attributes: processing.duration/success"]

  %% Color style definition
  style A fill:#ffe,stroke:#fa0,color:#000
  style B fill:#fff2cc,stroke:#cc9900,color:#000
  style C fill:#f0e6ff,stroke:#8000ff,color:#000
  style D fill:#e6f3ff,stroke:#0066cc,color:#000
  style E fill:#ffe6e6,stroke:#cc0000,color:#000
  style F fill:#e6ffe6,stroke:#009900,color:#000
  style G fill:#e6f3ff,stroke:#0066cc,color:#000

  %% CSS class definition
  classDef default font-size:11px;
  classDef longText font-size:10px,min-width:160px;
  
  class A,B,C,D,E,F,G longText;
```

Key points:
- Inside `AbstractStreamConsumer`:
  - If `TraceableMessageWrapper` is present and `hasValidTraceContext()` returns true, the trace context is extracted first and the Consumer Span is created on top of that context; otherwise a new Span is created.
  - On exception, `recordError(...)` is recorded and `processing.success=false` is tagged.

## 6. TraceableMessageWrapper Role Description

```mermaid
flowchart TD
  A["Receive StreamMessageEvent<br/>Message event<br/>(payload, traceContext)"] --> B{"Does<br/>traceContext exist?<br/>Check trace context"}
  B -->|"Trace context exists"| C["TextMapPropagator.extract<br/>Extract and restore trace context"]
  C --> D["Create Consumer Span<br/>Continue trace chain"]
  B -->|"No trace context"| E["Create new Consumer Span<br/>Start new trace chain"]
  D --> F["handle(payload, ctx)<br/>Execute business logic"]
  E --> F
  F --> G["Set Span attributes<br/>processing.duration<br/>processing.success"]

  %% Color style definition
  style A fill:#ffe,stroke:#fa0,color:#000
  style B fill:#fff2cc,stroke:#cc9900,color:#000
  style C fill:#f0e6ff,stroke:#8000ff,color:#000
  style D fill:#e6f3ff,stroke:#0066cc,color:#000
  style E fill:#ffe6e6,stroke:#cc0000,color:#000
  style F fill:#e6ffe6,stroke:#009900,color:#000
  style G fill:#e6f3ff,stroke:#0066cc,color:#000

  %% CSS class definition
  classDef default font-size:11px;
  classDef longText font-size:10px,min-width:160px;
  
  class A,B,C,D,E,F,G longText;
```

## 7. Coordination with Metrics (Micrometer)

- Publishing, polling, and processing all adopt a "dual-channel" timing strategy: untagged (global aggregation) plus a per-`stream`-tagged `Timer`;
- Sampling is uniformly controlled by `metrics.samplingRate` and independent switches (processing/polling/publishing), avoiding duplication and inconsistency;
- Fine-grained exception classification (timeout / connection / serialization) is performed through `MetricsErrorRecorder`, compatible with common Lettuce/Jedis exceptions.

## 8. End-to-End Verification Recommendations

- Print `traceId/spanId` at the publisher entry and on the consumer side (already exposed as Span attributes);
- Use an OTel backend (e.g. Tempo/Jaeger) to search for the publisher's `traceId`; it should chain the Reactor and Consumer Spans together;
- Combine with the Actuator endpoints `/actuator/redisstream` and `/actuator/redisstream/metrics` to verify metrics and health information.

## 9. FAQ
- Failed to continue the trace: check whether the event object correctly carries `traceContext`, and whether the consumer invoked `extract`;
- Multiple wrapping: make sure `wrapForPublish` is only called at the publisher entry;
- High overhead: tune `sampling-rate` and set `detailed=false`, and disable some of the expensive statistics switches as needed.
