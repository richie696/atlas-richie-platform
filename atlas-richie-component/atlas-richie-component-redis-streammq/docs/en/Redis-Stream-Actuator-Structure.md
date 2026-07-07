# Redis Stream - Actuator Integration Guide

## Overview
This component builds on Spring Boot Actuator and Micrometer to expose health, metrics, and management capabilities for Redis Stream. It supports manual health refresh, single-component health queries, poller start/stop, and backlog statistics.

## Exposed Endpoints

- GET `/actuator/redisstream`
  - Returns the overall status (health summary, poller status, metrics summary, JVM info)

- GET `/actuator/redisstream/metrics`
  - Returns detailed metrics: business / performance / system / error / backlog

- GET `/actuator/redisstream/{streamKey}`
  - Returns basic info for the specified Stream (length, groups, first/lastId), reactive puller, and metrics

- GET `/actuator/redisstream/{streamKey}/groups`
  - Returns consumer group information for the specified Stream

- GET `/actuator/redisstream/health/{component}`
  - Returns health for a single component (supported: `redis|stream|consumerGroup|poller|business`)

- POST `/actuator/redisstream/health/refresh`
  - Force refresh of health checks (run all checks immediately and cache the results)

- POST `/actuator/redisstream/{streamKey}/start`
  - Start the puller for the specified Stream (sample uses the default group/consumer)

- POST `/actuator/redisstream/{streamKey}/stop`
  - Stop the puller for the specified Stream

## Metrics Description (Selected)

- Business counts: `messagesPublished`, `messagesConsumed`, `messagesAcknowledged`, `messagesFailed`, `messagesRetried`
- Performance timings:
  - Processing duration `redis.stream.processing.duration` (tag: `stream`)
  - Polling duration `redis.stream.polling.duration` (tag: `stream`)
  - Publishing duration `redis.stream.publishing.duration` (tag: `stream`)
  - All of the above also have an "untagged/global" series for overall observation
- System metrics: `activeConsumers`, `activePollers`, `messageBacklog`, `activeConnections`
- Error classification: `timeoutErrors`, `connectionErrors`, `serializationErrors`, `totalErrors`

## Health Check Aggregation

`RedisStreamHealthIndicator` checks each component (`redis/stream/consumerGroup/poller/business`) individually, then aggregates with `HealthLevel(UP/DEGRADED/DOWN)`:
- Critical components (`redis`, `stream`) fail → overall `DOWN`
- Non-critical components fail or warn → overall `DEGRADED`
- All components healthy → overall `UP`

## Actuator Workflow

```mermaid
flowchart TD
  A[Client/Operations] -->|HTTP Request| B["/actuator/redisstream<br/>Entry Endpoint"]
  
  subgraph ActuatorEndpoints["Actuator Monitoring Endpoints"]
    C["getStatus<br/>Get Overall Status"]
    D["getMetrics<br/>Get Detailed Metrics"]
    E["getStreamInfo<br/>Get Stream Info<br/>{streamKey}"]
    F["getConsumerGroups<br/>Get Consumer Groups<br/>{streamKey}"]
    G["getComponentHealth<br/>Get Component Health<br/>{component}"]
    H["refreshHealth<br/>Refresh Health Check<br/>(POST)"]
    I["startPoller<br/>Start Puller<br/>{streamKey} (POST)"]
    J["stopPoller<br/>Stop Puller<br/>{streamKey} (POST)"]
  end

  B --> C
  B --> D
  B --> E
  B --> F
  B --> G
  B --> H
  B --> I
  B --> J

  C --> C1["healthIndicator.<br/>health()<br/>Health Indicator Check"]
  C --> C2["getPollerStatus()<br/>Puller Status Query"]
  C --> C3["getMetricsSummary()<br/>Metrics Summary Statistics"]
  C --> C4["getSystemInfo()<br/>System Info Retrieval"]

  D --> D1["Business Metrics<br/>Business Metrics"]
  D --> D2["Performance Metrics<br/>Performance Metrics"]
  D --> D3["System Metrics<br/>System Metrics"]
  D --> D4["Error Metrics<br/>Error Metrics"]
  D --> D5["Backlog Metrics<br/>Backlog Metrics"]

  style A fill:#ffe,stroke:#fa0,color:#000
  style B fill:#ffe6f0,stroke:#999,color:#000
  style C fill:#efe,stroke:#4a4,color:#000
  style D fill:#efe,stroke:#4a4,color:#000
  style E fill:#efe,stroke:#4a4,color:#000
  style F fill:#efe,stroke:#4a4,color:#000
  style G fill:#efe,stroke:#4a4,color:#000
  style H fill:#efe,stroke:#4a4,color:#000
  style I fill:#efe,stroke:#4a4,color:#000
  style J fill:#efe,stroke:#4a4,color:#000

  %% Custom CSS styles to adjust node sizes
  classDef default font-size:12px;
  classDef longText font-size:11px,min-width:140px;
  
  class E,F,G,H,I,J,C1,C2,C3,C4 longText;
```

## Component Internal Workflow

```mermaid
flowchart TD
  P["Message Publisher:<br/>RedisStreamManager.publish<br/>Publish Message to Stream"] -->|"Wrap Publish & Trace"| P1["opsForStream.add<br/>Execute Redis Write"]
  P1 -->|Success| P2["recordMessagePublished<br/>Record Message Published"]
  P2 --> P3["recordPublishingDuration<br/>Record Publishing Duration (Global)"]
  P3 --> P4["Sample.stop<br/>Stop Publishing Duration Timer<br/>(Tagged Statistics)"]
  P1 -->|Exception| PE["MetricsErrorRecorder<br/>Error Classifier"]

  subgraph ReactorGroup["Reactive Module - Message Polling"]
    R["startPolling<br/>Start Polling Scheduler"] --> R1["pollOnce<br/>Execute One Poll"]
    R1 -->|"Read Success"| R2["Publish to<br/>EventBus<br/>Event Bus"]
    R1 -->|"Read Exception"| RE["Error Classification Handler"]
    R --> R3["recordPollingDuration<br/>Record Polling Duration (Global)"]
    R --> R4["Sample.stop<br/>Stop Polling Duration Timer<br/>(Tagged Statistics)"]
  end

  subgraph ConsumerGroup["Consumer Module - Message Processing"]
    C["Subscribe EventBus<br/>Listen on Event Bus"] --> C1["Create/Continue<br/>Trace Span"]
    C1 --> C2["handle<br/>Business Handler<br/>(payload, ctx)"]
    C2 -->|Processing Success| C3["recordMessageConsumed<br/>Record Message Consumed<br/>+ ACK Confirm"]
    C3 --> C4["recordProcessingDuration<br/>Record Processing Duration (Global)"]
    C4 --> C5["Sample.stop<br/>Stop Processing Duration Timer<br/>(Tagged Statistics)"]
    C2 -->|Processing Exception| CE["recordMessageFailed<br/>Record Message Failed<br/>+ Error Classification + Span Mark"]
    CE --> CR["Exception Handling Strategy:<br/>Skip/Retry/No-Ack<br/>SKIP/RETRY/NO_ACK"]
  end

  %% Color style definitions
  style P fill:#ffe,stroke:#fa0,color:#000
  style P1 fill:#e6f3ff,stroke:#0066cc,color:#000
  style P2 fill:#e6f3ff,stroke:#0066cc,color:#000
  style P3 fill:#e6f3ff,stroke:#0066cc,color:#000
  style P4 fill:#e6f3ff,stroke:#0066cc,color:#000
  style PE fill:#ffe6e6,stroke:#cc0000,color:#000
  
  style R fill:#f0e6ff,stroke:#8000ff,color:#000
  style R1 fill:#f0e6ff,stroke:#8000ff,color:#000
  style R2 fill:#f0e6ff,stroke:#8000ff,color:#000
  style RE fill:#ffe6e6,stroke:#cc0000,color:#000
  style R3 fill:#f0e6ff,stroke:#8000ff,color:#000
  style R4 fill:#f0e6ff,stroke:#8000ff,color:#000
  
  style C fill:#e6ffe6,stroke:#009900,color:#000
  style C1 fill:#e6ffe6,stroke:#009900,color:#000
  style C2 fill:#e6ffe6,stroke:#009900,color:#000
  style C3 fill:#e6ffe6,stroke:#009900,color:#000
  style C4 fill:#e6ffe6,stroke:#009900,color:#000
  style C5 fill:#e6ffe6,stroke:#009900,color:#000
  style CE fill:#ffe6e6,stroke:#cc0000,color:#000
  style CR fill:#fff2cc,stroke:#cc9900,color:#000

  %% CSS class definitions
  classDef default font-size:11px;
  classDef longText font-size:10px,min-width:160px;
  
  class P,P1,P2,P3,P4,PE,R,R1,R2,R3,R4,C,C1,C2,C3,C4,C5,CE,CR longText;
```

## Production Configuration Recommendations

```yaml
platform:
  cache:
    redis:
      stream:
        monitoring:
          enabled: true
          metrics:
            enabled: true
            detailed: false          # histograms/quantiles are off by default; enable temporarily when needed
            sampling-rate: 0.05      # for high QPS, suggest 0.05~0.3; tune higher for low QPS / stress tests
          performance:
            enabled: true
            record-processing-time: true
            record-polling-time: true
            record-publishing-time: true
          error-monitoring:
            enabled: true
            classify-by-type: true
            record-stack-trace: false
          business-monitoring:
            enabled: true
            record-message-count: true
            record-retry-count: true
            record-ack-count: true
```

## Usage Tips

- Tagged timers and untagged timers now share a unified sampling logic, avoiding duplicate-stat conflicts.
- Use single-component health for precise pinpointing: `/actuator/redisstream/health/{component}`.
- Unused endpoint utility methods (e.g., timer/gauge reads) should be cleaned up to reduce redundancy.
