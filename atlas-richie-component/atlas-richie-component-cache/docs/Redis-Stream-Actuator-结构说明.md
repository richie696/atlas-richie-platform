# Redis Stream - Actuator 接入说明

## 概述
本组件基于 Spring Boot Actuator 与 Micrometer 暴露 Redis Stream 的健康、指标、管理能力，支持手动健康刷新、单组件健康查询、拉取器启停与积压统计。

## 暴露的端点

- GET `/actuator/redisstream`
  - 返回整体状态（健康摘要、拉取器状态、指标摘要、JVM 信息）

- GET `/actuator/redisstream/metrics`
  - 返回业务/性能/系统/错误/积压等详细指标

- GET `/actuator/redisstream/{streamKey}`
  - 返回指定 Stream 的基础信息（长度、groups、first/lastId）、响应式拉取器与指标

- GET `/actuator/redisstream/{streamKey}/groups`
  - 返回指定 Stream 的消费者组信息

- GET `/actuator/redisstream/health/{component}`
  - 返回单组件健康（支持：`redis|stream|consumerGroup|poller|business`）

- POST `/actuator/redisstream/health/refresh`
  - 强制刷新健康检查（立即执行各项检查并缓存）

- POST `/actuator/redisstream/{streamKey}/start`
  - 启动指定 Stream 的拉取器（示例默认组/消费者）

- POST `/actuator/redisstream/{streamKey}/stop`
  - 停止指定 Stream 的拉取器

## 指标说明（部分）

- 业务计数：`messagesPublished`、`messagesConsumed`、`messagesAcknowledged`、`messagesFailed`、`messagesRetried`
- 性能耗时：
  - 处理耗时 `redis.stream.processing.duration`（标签：`stream`）
  - 拉取耗时 `redis.stream.polling.duration`（标签：`stream`）
  - 发布耗时 `redis.stream.publishing.duration`（标签：`stream`）
  - 以上同时有“无标签/全局”统计，用于总体观测
- 系统指标：`activeConsumers`、`activePollers`、`messageBacklog`、`activeConnections`
- 错误分类：`timeoutErrors`、`connectionErrors`、`serializationErrors`、`totalErrors`

## 健康检查聚合

`RedisStreamHealthIndicator` 对组件（`redis/stream/consumerGroup/poller/business`）分别检查，使用 `HealthLevel(UP/DEGRADED/DOWN)` 汇总：
- 关键组件（`redis`、`stream`）失败 → 整体 `DOWN`
- 非关键组件失败/告警 → 整体 `DEGRADED`
- 全部正常 → 整体 `UP`

## Actuator 工作流

```mermaid
flowchart TD
  A[Client/运维人员] -->|HTTP请求| B["/actuator/redisstream<br/>入口端点"]
  
  subgraph ActuatorEndpoints["Actuator 监控端点"]
    C["getStatus<br/>获取整体状态"]
    D["getMetrics<br/>获取详细指标"]
    E["getStreamInfo<br/>获取Stream信息<br/>{streamKey}"]
    F["getConsumerGroups<br/>获取消费者组<br/>{streamKey}"]
    G["getComponentHealth<br/>获取组件健康状态<br/>{component}"]
    H["refreshHealth<br/>刷新健康检查<br/>(POST)"]
    I["startPoller<br/>启动拉取器<br/>{streamKey} (POST)"]
    J["stopPoller<br/>停止拉取器<br/>{streamKey} (POST)"]
  end

  B --> C
  B --> D
  B --> E
  B --> F
  B --> G
  B --> H
  B --> I
  B --> J

  C --> C1["healthIndicator.<br/>health()<br/>健康指示器检查"]
  C --> C2["getPollerStatus()<br/>拉取器状态查询"]
  C --> C3["getMetricsSummary()<br/>指标摘要统计"]
  C --> C4["getSystemInfo()<br/>系统信息获取"]

  D --> D1["Business Metrics<br/>业务指标"]
  D --> D2["Performance Metrics<br/>性能指标"]
  D --> D3["System Metrics<br/>系统指标"]
  D --> D4["Error Metrics<br/>错误指标"]
  D --> D5["Backlog Metrics<br/>积压指标"]

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

  %% 自定义CSS样式来调整节点大小
  classDef default font-size:12px;
  classDef longText font-size:11px,min-width:140px;
  
  class E,F,G,H,I,J,C1,C2,C3,C4 longText;
```

## 组件内部工作流

```mermaid
flowchart TD
  P["消息发布器:<br/>RedisStreamManager.publish<br/>发布消息到Stream"] -->|"包装发布 & 链路追踪"| P1["opsForStream.add<br/>执行Redis写入操作"]
  P1 -->|成功| P2["recordMessagePublished<br/>记录消息发布成功"]
  P2 --> P3["recordPublishingDuration<br/>记录发布耗时(全局)"]
  P3 --> P4["Sample.stop<br/>停止发布耗时计时器<br/>(带标签统计)"]
  P1 -->|异常| PE["MetricsErrorRecorder<br/>错误分类记录器"]

  subgraph ReactorGroup["响应式模块 - 消息拉取"]
    R["startPolling<br/>启动轮询调度器"] --> R1["pollOnce<br/>执行一次拉取"]
    R1 -->|"读取成功"| R2["发布到<br/>EventBus<br/>事件总线"]
    R1 -->|"读取异常"| RE["错误分类处理"]
    R --> R3["recordPollingDuration<br/>记录拉取耗时(全局)"]
    R --> R4["Sample.stop<br/>停止拉取耗时计时器<br/>(带标签统计)"]
  end

  subgraph ConsumerGroup["消费者模块 - 消息处理"]
    C["订阅EventBus<br/>监听事件总线"] --> C1["创建/延续<br/>链路追踪Span"]
    C1 --> C2["handle<br/>业务处理方法<br/>(payload, ctx)"]
    C2 -->|处理成功| C3["recordMessageConsumed<br/>记录消息消费成功<br/>+ ACK确认"]
    C3 --> C4["recordProcessingDuration<br/>记录处理耗时(全局)"]
    C4 --> C5["Sample.stop<br/>停止处理耗时计时器<br/>(带标签统计)"]
    C2 -->|处理异常| CE["recordMessageFailed<br/>记录消息处理失败<br/>+ 错误分类 + Span标记"]
    CE --> CR["异常处理策略:<br/>跳过/重试/不确认<br/>SKIP/RETRY/NO_ACK"]
  end

  %% 颜色样式定义
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

  %% CSS类定义
  classDef default font-size:11px;
  classDef longText font-size:10px,min-width:160px;
  
  class P,P1,P2,P3,P4,PE,R,R1,R2,R3,R4,C,C1,C2,C3,C4,C5,CE,CR longText;
```

## 生产配置建议

```yaml
platform:
  cache:
    redis:
      stream:
        monitoring:
          enabled: true
          metrics:
            enabled: true
            detailed: false          # 直方图/分位数默认关闭，必要时临时开启
            sampling-rate: 0.05      # 高QPS建议 0.05~0.3；低QPS/压测可调高
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

## 使用提示

- 标签计时器与无标签计时器已统一采样逻辑，避免重复统计冲突。
- 单组件健康可用于精准定位：`/actuator/redisstream/health/{component}`。
- 未使用的 Endpoint 工具方法（如计时器/仪表读取）建议清理，减少冗余。
