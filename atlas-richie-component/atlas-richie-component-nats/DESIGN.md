# atlas-richie-component-nats 组件设计文档

## 1. 概述

### 1.1 组件定位

NATS 组件是面向 AI Agent 服务端的消息通信基础设施封装，提供基于 NATS 的**异步消息发布/订阅**与**同步 RPC 对话**能力。

NATS 在本组件中承担双重角色：
- **异步通信**：JetStream Pub/Sub，用于事件驱动、任务分发等场景
- **同步 RPC**：Request-Reply，用于 AI Agent 用户提问 → Agent 思考 → 响应的实时对话

### 1.2 设计目标

| 目标 | 说明 |
|------|------|
| **协议域隔离** | `NatsBus`（Core NATS）/ `JetStreamBus`（JetStream）/ `NatsEndpoint`（RPC）三个独立门面 |
| **配置驱动** | `NatsProperties` 控制所有行为开关，`@ConditionalOnProperty` 按需装配 |
| **最少知识** | 用户无需了解 Connection / Dispatcher / StreamContext / ConsumerContext 等底层 API |
| **横切透明** | 链路追踪、上下文透传、幂等去重对用户完全透明 |

### 1.3 连接模型

NATS 采用**单 TCP 连接多路复用**模型，一个连接可同时承载成百上千个订阅、并发发布和请求-响应操作，**无需连接池**。唯一需要多个连接的场景是隔离不同认证上下文或连接不同集群。

---

## 2. 设计原则

| 原则 | 在本组件中的体现 |
|------|-----------------|
| 策略模式 | 序列化、去重、追踪等均为可替换策略接口（L1） |
| 装饰器模式 | 订阅管道中各横切关注点通过装饰器链式组合（L4） |
| 模板方法 | NatsBus / JetStreamBus 在每次操作中自动注入追踪/上下文（L5） |
| 依赖倒置 | 所有横切关注点面向 L1 接口编程 |
| Builder 模式 | 管道构建器 `NatsMessageHandlerPipeline` 支持灵活组合（L4） |
| 工厂模式 | `NatsSubscriberFactory` 根据场景构建不同管道（L4） |

---

## 3. 分层架构

### 3.1 分层总览

```
┌──────────────────────────────────────────────────────────────┐
│  L7  NatsAutoConfiguration（Spring Boot 自动配置编配）         │
│  L6  NatsComponent（统一门面 + 生命周期管理）                  │
│  L5  NatsBus / JetStreamBus / NatsEndpoint（协议域隔离门面）   │
│  L4  Pipeline + Decorators（订阅管道 + 装饰器链）              │
│  L3  NatsConnectionManager / JetStreamManagementService      │
│  L2  策略实现（Jackson / OTel / Redis / Memory）              │
│  L1  策略接口（Serializer / Header / Tracing / Idempotent）   │
│  L0  原子类型（Enum / Exception / Constant）                  │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 实现顺序

严格按 L0 → L7 自底向上实现，每层只依赖下方层级。

---

## 4. 各层详细设计

### 4.1 L0 — 原子类型层

零内部依赖的纯值类型，是组件的地基。

```
com.richie.component.nats
├── enums/
│   ├── ConnectionState.java        // CONNECTED, RECONNECTING, DISCONNECTED, CLOSED
│   └── AuthType.java               // NONE, TOKEN, USERPASS, NKEY, CREDENTIALS, JWT
├── exception/
│   ├── NatsException.java          // 组件统一异常基类
│   ├── NatsConnectionException.java
│   ├── NatsRpcException.java       // 超时 / no-responders
│   └── NatsSerializationException.java
└── constant/
    └── NatsConstants.java          // header key 前缀、默认超时等
```

**设计要点：**
- `ConnectionState` 包含 `isConnected()` 便捷方法
- `NatsRpcException` 区分 Timeout / NoResponders 语义
- `NatsConstants` 定义 header 命名空间（如 `NATS_TRACE_ID`）

---

### 4.2 L1 — 策略接口层

定义所有可替换能力的**接口契约**，每个接口职责单一。

```
com.richie.component.nats.strategy
├── serializer/
│   └── NatsMessageSerializer.java
├── header/
│   ├── NatsHeaderInjector.java
│   └── NatsHeaderExtractor.java
├── tracing/
│   └── NatsTracingSupport.java
├── idempotent/
│   └── NatsIdempotentChecker.java
└── error/
    └── NatsErrorStrategy.java
```

**接口定义：**

```java
// 消息序列化：对象 ↔ byte[]
public interface NatsMessageSerializer {
    byte[] serialize(Object obj);
    <T> T deserialize(byte[] data, Class<T> type);
}

// Header 注入：HeaderContextHolder → NATS Headers（发送端）
public interface NatsHeaderInjector {
    void inject(Headers headers);
}

// Header 提取：NATS Headers → HeaderContextHolder（接收端）
public interface NatsHeaderExtractor {
    void extract(Headers headers);
}

// 链路追踪：span 创建 + W3C context 注入/提取
public interface NatsTracingSupport {
    Span startProducerSpan(String subject, Headers headers);
    Span startConsumerSpan(String subject, Headers headers);
    Span startClientSpan(String subject, Headers headers);
    Span startServerSpan(String subject, Headers headers);
    void finishSpan(Span span, boolean success, String errorMsg);
}

// 幂等去重：消息 ID 级去重
public interface NatsIdempotentChecker {
    boolean isFirstTime(String messageId, long ttlMillis);
    void clear(String messageId);
}

// 错误处理策略
public interface NatsErrorStrategy {
    void onPublishError(String subject, byte[] data, Exception e);
    void onConsumeError(String subject, Message msg, Exception e);
    boolean shouldRetry(Exception e, int attempt, int maxAttempts);
}
```

---

### 4.3 L2 — 策略实现层

L1 接口的默认实现，每个实现类可独立替换。

```
com.richie.component.nats.impl
├── serializer/
│   └── JacksonNatsMessageSerializer.java
├── header/
│   ├── DefaultNatsHeaderInjector.java
│   └── DefaultNatsHeaderExtractor.java
├── tracing/
│   └── OpenTelemetryNatsTracingSupport.java
├── idempotent/
│   ├── MemoryNatsIdempotentChecker.java
│   └── RedisNatsIdempotentChecker.java
└── error/
    └── DefaultNatsErrorStrategy.java
```

**实现要点：**

| 实现类 | 依赖 | 说明 |
|--------|------|------|
| `JacksonNatsMessageSerializer` | `JsonUtils`（项目已有） | 与 messaging 组件保持一致 |
| `DefaultNatsHeaderInjector/Extractor` | `HeaderContextHolder` | 透传 timezone/language/tenant/canary 等 |
| `OpenTelemetryNatsTracingSupport` | `opentelemetry-api` | 实现 `TextMapSetter<Headers>` / `TextMapGetter<Headers>` |
| `MemoryNatsIdempotentChecker` | `ConcurrentHashMap` | 单实例部署使用 |
| `RedisNatsIdempotentChecker` | `GlobalCache`（cache 组件） | 多实例部署使用，SET NX 原子操作 |
| `DefaultNatsErrorStrategy` | 无外部依赖 | 日志记录 + 条件重试 |

---

### 4.4 L3 — 基础设施服务层

直接封装 `io.nats.client.*` 原生 API，提供面向组件内部使用的高层服务。

```
com.richie.component.nats
├── connection/
│   ├── NatsConnectionManager.java
│   ├── NatsConnectionListener.java
│   └── NatsAuthConfigurator.java
└── jetstream/
    └── JetStreamManagementService.java
```

**NatsConnectionManager 职责：**

```java
public class NatsConnectionManager {
    // 连接创建（NatsProperties → Options → Nats.connect()）
    public Connection getConnection();
    // JetStream 上下文获取（新 API）
    public StreamContext getStreamContext(String streamName);
    public ConsumerContext getConsumerContext(String streamName, String consumerName);
    // 连接状态查询
    public ConnectionState getState();
    // 优雅关闭（drain → close）
    public void shutdown(Duration drainTimeout);
    // 事件监听器注册
    public void addConnectionListener(NatsConnectionListener listener);
}
```

**NatsConnectionListener 接口：**

```java
public interface NatsConnectionListener {
    default void onConnected(Connection connection) {}
    default void onDisconnected(Connection connection) {}
    default void onReconnecting(Connection connection) {}
    default void onClosed(Connection connection) {}
    default void onError(Connection connection, Exception error) {}
}
```

**JetStreamManagementService 职责：**

```java
public class JetStreamManagementService {
    // 幂等创建 Stream（不存在才创建）
    public void ensureStreamExists(NatsProperties.StreamDefinition def);
    // 幂等创建/更新 Consumer
    public void ensureConsumerExists(String streamName, NatsProperties.ConsumerDefinition def);
}
```

> **注**：jnats 2.25.3 引入了新的简化 JetStream API（`StreamContext` / `ConsumerContext`），替代旧版 `JetStream` / `JetStreamManagement` 上下文。`NatsConnectionManager` 统一使用新 API。

---

### 4.5 L4 — 订阅管道层

**核心设计**：使用装饰器模式将横切关注点（追踪 → 上下文恢复 → 去重）链式包装在业务 Handler 外层。

```
com.richie.component.nats.pipeline
├── NatsMessageHandler.java                  // 函数式接口
├── NatsMessageHandlerPipeline.java          // 管道构建器
├── decorator/
│   ├── TracingMessageDecorator.java
│   ├── ContextRestorationDecorator.java
│   └── IdempotentMessageDecorator.java
└── NatsSubscriberFactory.java               // 订阅者工厂
```

**NatsMessageHandler — 统一消息处理函数式接口：**

```java
@FunctionalInterface
public interface NatsMessageHandler {
    void handle(Message message) throws Exception;
}
```

**NatsMessageHandlerPipeline — 管道构建器（Builder + 装饰器）：**

```java
public class NatsMessageHandlerPipeline {
    private final List<Function<NatsMessageHandler, NatsMessageHandler>> decorators;

    public NatsMessageHandlerPipeline addDecorator(
            Function<NatsMessageHandler, NatsMessageHandler> decorator);

    // 构建最终管道：装饰器按添加顺序从外到内包装业务 Handler
    public NatsMessageHandler build(NatsMessageHandler businessHandler);
}
```

**各装饰器职责：**

| 装饰器 | 执行位置 | 职责 |
|--------|---------|------|
| `TracingMessageDecorator` | 最外层 | 提取 trace context + 创建 span + 结束 span |
| `ContextRestorationDecorator` | 中间层 | NATS Headers → HeaderContextHolder |
| `IdempotentMessageDecorator` | 内层（可选） | messageId 去重，重复则跳过 |

**NatsSubscriberFactory — 根据场景构建不同管道：**

```java
public class NatsSubscriberFactory {
    // 异步订阅管道: Tracing(CONSUMER) → Context → Idempotent → Business
    public NatsMessageHandler buildAsyncPipeline(NatsMessageHandler businessHandler);

    // RPC 服务端管道: Tracing(SERVER) → Context → Business（无去重）
    public NatsMessageHandler buildRpcPipeline(NatsMessageHandler businessHandler);
}
```

---

### 4.6 L5 — 协议域隔离门面层

对外暴露的核心 API，遵循**外观模式 + 协议域隔离 + 最少知识原则**。

三个门面按协议域完全隔离，每个门面内部 API 语义一致，用户选类即选协议。

```
com.richie.component.nats
├── bus/
│   └── NatsBus.java             // Core NATS 门面：发布 + 订阅 + RPC 请求
├── stream/
│   └── JetStreamBus.java        // JetStream 门面：发布 + 消费 + 批量拉取 + 单条拉取
└── endpoint/
    └── NatsEndpoint.java        // RPC 端点：Handler 注册
```

---

#### NatsBus — Core NATS 门面

所有操作基于 Core NATS 协议，fire-and-forget，无持久化，无 ACK。

```java
public class NatsBus {

    // ===== 发布（fire-and-forget）=====
    public void publish(String subject, Object message);

    // ===== 订阅 =====
    public Subscription subscribe(String subject, NatsMessageHandler handler);
    // Queue Group 订阅
    public Subscription subscribe(String subject, String queueGroup,
                                   NatsMessageHandler handler);

    // ===== RPC 同步请求-响应 =====
    public <T, R> R request(String subject, T request, Class<R> responseType,
                            Duration timeout);

    // ===== RPC 异步请求-响应 =====
    public <T, R> CompletableFuture<R> requestAsync(String subject, T request,
                                                     Class<R> responseType,
                                                     Duration timeout);
}
```

**每次 publish / subscribe / request 内部自动完成（用户无感知）：**
1. `serializer.serialize(message)` → `byte[]`
2. `headerInjector.inject(headers)` 写入上下文（tenant/language/canary 等）
3. `tracingSupport.startProducerSpan()` 或 `startClientSpan()` + W3C 注入
4. 发送消息
5. `tracingSupport.finishSpan()` 结束 span

**每次收到消息自动完成（用户无感知）：**
1. 管道自动：提取 trace context → 创建 CONSUMER span → 恢复上下文
2. `serializer.deserialize(data, type)` → 业务对象
3. 执行业务 Handler
4. `tracingSupport.finishSpan()` 结束 span

---

#### JetStreamBus — JetStream 门面

所有操作基于 JetStream 协议，持久化存储，at-least-once 投递保证。

```java
public class JetStreamBus {

    // ===== 发布（服务端确认写入）=====
    public PublishAck publish(String subject, Object message);

    // ===== 持续消费（自动 ack/nak 管理）=====
    public MessageConsumer consume(String streamName, String consumerName,
                                    NatsMessageHandler handler);

    // ===== 批量拉取 =====
    public FetchConsumer fetch(String streamName, String consumerName, int batchSize);

    // ===== 单条拉取 =====
    public Message next(String streamName, String consumerName, Duration timeout);
}
```

**每次 publish 内部自动完成（用户无感知）：**
1. `serializer.serialize(message)` → `byte[]`
2. `headerInjector.inject(headers)` 写入上下文
3. `tracingSupport.startProducerSpan()` + W3C 注入
4. `JetStream.publish(subject, headers, data)` → 等待 `PublishAck`
5. `tracingSupport.finishSpan()` 结束 span

**每次 consume 收到消息自动完成（用户无感知）：**
1. 管道自动：提取 trace context → 创建 CONSUMER span → 恢复上下文 → 去重检查
2. `serializer.deserialize(data, type)` → 业务对象
3. 执行业务 Handler
4. 成功 → `msg.ack()` / 失败 → `msg.nak()`（触发服务端重投递）
5. `tracingSupport.finishSpan()` 结束 span

---

#### NatsEndpoint — RPC 端点注册

基于 Core NATS Request-Reply 模式的服务端 Handler 注册。

```java
public class NatsEndpoint {

    // 注册 RPC Handler（自动组装完整管道）
    public <T, R> void registerHandler(String subject, Class<T> requestType,
                                        Function<T, R> handler);

    // 注册 RPC Handler（Queue Group 负载均衡）
    public <T, R> void registerHandler(String subject, String queueGroup,
                                        Class<T> requestType, Function<T, R> handler);
}
```

**每次收到 RPC 请求自动完成（用户无感知）：**
1. 管道自动：提取 trace context → 创建 SERVER span → 恢复上下文
2. `serializer.deserialize(data, requestType)` → `T`
3. 执行业务 Handler：`handler.apply(request)` → `R`
4. 序列化响应 + 注入追踪 header → 发布到 `replyTo`
5. `tracingSupport.finishSpan()` 结束 span

---

### 4.7 L6 — 统一门面编排层

```
com.richie.component.nats
└── NatsComponent.java           // 组件统一门面 + SmartLifecycle
```

```java
public class NatsComponent implements SmartLifecycle {

    // ===== 协议域入口（选类即选协议）=====
    public NatsBus bus();                // Core NATS 门面
    public JetStreamBus stream();        // JetStream 门面
    public NatsEndpoint endpoint();      // RPC 端点注册

    // ===== 生命周期管理 =====
    @Override public void start();       // 初始化连接 → 声明 Stream/Consumer
    @Override public void stop();        // drain 所有订阅 → 关闭连接
}
```

---

### 4.8 L7 — 配置编配层

```
com.richie.component.nats.config
├── NatsProperties.java              // 全量配置属性（映射 jnats Options.Builder 全量能力）
└── NatsAutoConfiguration.java       // 自动配置（最终编排）
```

**NatsProperties 设计理念：**

> 封装组件 ≠ 屏蔽驱动能力。组件应**暴露 jnats 原生驱动的全部配置能力**，仅在以下情况隐藏：
> - 组件内部已托管（如 `errorListener` / `connectionListener` / `executor`）
> - 仅用于测试的内部参数（如 `bufferSize` / `dataPortType`）
>
> 所有暴露项均提供**组件默认值**，使用者可零配置启动，按需覆盖。

**配置属性映射关系：**

| NatsProperties 配置项 | jnats Options.Builder 方法 | 组件默认值 | jnats 默认值 | 说明 |
|---|---|---|---|---|
| `server` | `server()` / `servers()` | `nats://localhost:4222` | — | 支持逗号分隔多地址 |
| `connection.name` | `connectionName()` | `nats-client` | null | 连接名称 |
| `connection.connection-timeout` | `connectionTimeout()` | `5s` | `2s` | 连接超时 |
| `connection.drain-timeout` | — (组件层 drain) | `30s` | — | 优雅关闭超时 |
| `connection.no-echo` | `noEcho()` | `false` | `false` | 禁用自身消息回显 |
| `connection.no-randomize` | `noRandomize()` | `false` | `false` | 禁用服务器池随机化 |
| `connection.inbox-prefix` | `inboxPrefix()` | `_INBOX` | `_INBOX` | Inbox 前缀 |
| `connection.support-utf8-subjects` | `supportUTF8Subjects()` | `false` | `false` | UTF-8 Subject 支持 |
| `reconnect.enabled` | `noReconnect()` (取反) | `true` | `true` | 重连开关 |
| `reconnect.max-reconnects` | `maxReconnects()` | `-1` | `60` | 最大重连次数（-1=无限） |
| `reconnect.reconnect-wait` | `reconnectWait()` | `2s` | `2s` | 重连等待间隔 |
| `reconnect.jitter` | `reconnectJitter()` | `100ms` | `100ms` | 重连抖动（非TLS） |
| `reconnect.jitter-tls` | `reconnectJitterTls()` | `1s` | `1s` | 重连抖动（TLS） |
| `reconnect.buffer-size` | `reconnectBufferSize()` | `8388608` (8MB) | `8MB` | 重连缓冲区大小 |
| `reconnect.retry-on-failed-connect` | `retryOnFailedConnect()` | `false` | `false` | 首次连接失败直接进入重连 |
| `ping.interval` | `pingInterval()` | `20s` | `2min` | Ping 间隔 |
| `ping.max-outstanding` | `maxPingsOut()` | `2` | `2` | 最大未响应 Ping 数 |
| `tls.enabled` | `sslContext()` / `secure()` | `false` | `false` | TLS 总开关 |
| `tls.keystore-path` | `sslContext()` | — | — | KeyStore 路径 |
| `tls.keystore-password` | `sslContext()` | — | — | KeyStore 密码 |
| `tls.truststore-path` | `sslContext()` | — | — | TrustStore 路径 |
| `tls.truststore-password` | `sslContext()` | — | — | TrustStore 密码 |
| `tls.opentls` | `opentls()` | `false` | `false` | 接受任意证书（调试用） |
| `protocol.verbose` | `verbose()` | `false` | `false` | 协议 Verbose 模式 |
| `protocol.pedantic` | `pedantic()` | `false` | `false` | 协议 Pedantic 模式 |
| `protocol.no-headers` | `noHeaders()` | `false` | `false` | 禁用 Headers 支持 |
| `protocol.no-responders` | `noResponders()` | `false` | `false` | 禁用 No-Responders 支持 |
| `protocol.max-control-line` | `maxControlLine()` | `4096` | `4096` | 控制行最大字节数 |
| `request.old-style` | `oldRequestStyle()` | `false` | `false` | 使用旧版请求模式 |
| `request.cleanup-interval` | `requestCleanupInterval()` | `5s` | `5s` | 请求 Future 清理间隔 |
| `queue.max-outgoing-messages` | `maxMessagesInOutgoingQueue()` | `-1` | `-1` | 发送队列最大消息数（-1=无限） |
| `queue.discard-when-full` | `discardMessagesWhenOutgoingQueueFull()` | `false` | `false` | 队列满时丢弃消息 |
| `auth.type` | — | `none` | — | 认证方式 |
| `auth.token` | `token()` | — | — | Token 认证 |
| `auth.username` / `auth.password` | `userInfo()` | — | — | 用户名密码认证 |
| `auth.nkey` | `authHandler(NKeyAuthHandler)` | — | — | NKey 认证 |
| `auth.credentials-file` | `credentialPath()` | — | — | 凭证文件路径 |
| `auth.jwt` | `jwt()` | — | — | JWT Token |
| `auth.seed` | `seed()` | — | — | NKey Seed |

**隐藏项（组件内部托管，不暴露）：**

| jnats Options.Builder 方法 | 隐藏原因 |
|---|---|
| `errorListener()` | 组件内部 `DefaultNatsErrorStrategy` 已托管 |
| `connectionListener()` | 组件内部 `NatsConnectionManager` 已托管 |
| `executor()` | 组件使用线程池组件统一管理 |
| `bufferSize()` | 仅用于测试，生产环境无需调整 |
| `dataPortType()` | 底层数据传输类型，不应暴露 |
| `authHandler()` | 使用简化配置替代自定义 AuthHandler |
| `traceConnection()` | 仅用于驱动调试，通过 `logging.level` 控制 |
| `turnOnAdvancedStats()` | 高级统计，非通用需求 |

**NatsProperties Java 类结构：**

```java
@Data
@ConfigurationProperties(prefix = "platform.nats")
public class NatsProperties {
    private boolean enabled = true;
    private String server = "nats://localhost:4222";
    private Auth auth = new Auth();
    private Connection connection = new Connection();
    private Reconnect reconnect = new Reconnect();
    private Ping ping = new Ping();
    private Tls tls = new Tls();
    private Protocol protocol = new Protocol();
    private Request request = new Request();
    private Queue queue = new Queue();
    private Tracing tracing = new Tracing();
    private HeaderPropagation headerPropagation = new HeaderPropagation();
    private Idempotent idempotent = new Idempotent();
    private Error error = new Error();
    private JetStream jetstream = new JetStream();

    // ===== 转换为 jnats Options.Builder =====
    public Options.Builder toOptionsBuilder() { ... }

    // ----- 内部配置类 -----
    @Data public static class Auth { ... }
    @Data public static class Connection { ... }
    @Data public static class Reconnect { ... }
    @Data public static class Ping { ... }
    @Data public static class Tls { ... }
    @Data public static class Protocol { ... }
    @Data public static class Request { ... }
    @Data public static class Queue { ... }
    @Data public static class Tracing { ... }
    @Data public static class HeaderPropagation { ... }
    @Data public static class Idempotent { ... }
    @Data public static class Error { ... }
    @Data public static class JetStream { ... }
    @Data public static class StreamDefinition { ... }
    @Data public static class ConsumerDefinition { ... }
}
```

**`toOptionsBuilder()` 方法职责**：将 NatsProperties 全量配置转换为 `Options.Builder`，供 `NatsConnectionManager` 调用 `Nats.connect(options.build())`。该方法内部按分组依次应用：server → auth → connection → reconnect → ping → protocol → request → queue → TLS。

---

## 5. 配置 Schema

> **设计原则**：暴露 jnats 驱动全量配置能力 + 组件增强配置 + 组件默认值。使用者只需覆盖需要调整的部分。

```yaml
platform:
  nats:
    enabled: true                           # 组件总开关
    server: nats://localhost:4222            # 支持逗号分隔多地址: nats://s1:4222,nats://s2:4222

    # ===== 认证配置 =====
    auth:
      type: none                            # none / token / userpass / nkey / credentials / jwt
      token:                                # type=token 时必填
      username:                             # type=userpass 时必填
      password:                             # type=userpass 时必填
      nkey:                                 # type=nkey 时必填
      credentials-file:                     # type=credentials 时必填，凭证文件路径
      jwt:                                  # type=jwt 时必填
      seed:                                 # type=jwt 时可选，NKey seed 签名用

    # ===== 连接配置（映射 jnats Options.Builder）=====
    connection:
      name: nats-client                     # → connectionName()
      connection-timeout: 5s                # → connectionTimeout()，jnats 默认 2s
      drain-timeout: 30s                    # 组件层优雅关闭超时
      no-echo: false                        # → noEcho()
      no-randomize: false                   # → noRandomize()
      inbox-prefix: _INBOX                  # → inboxPrefix()
      support-utf8-subjects: false          # → supportUTF8Subjects()

    # ===== 重连配置（映射 jnats Options.Builder）=====
    reconnect:
      enabled: true                         # → noReconnect() 取反
      max-reconnects: -1                    # → maxReconnects()，-1=无限重连，jnats 默认 60
      reconnect-wait: 2s                    # → reconnectWait()
      jitter: 100ms                         # → reconnectJitter()
      jitter-tls: 1s                        # → reconnectJitterTls()
      buffer-size: 8388608                  # → reconnectBufferSize()，8MB
      retry-on-failed-connect: false        # → retryOnFailedConnect()

    # ===== Ping 配置（映射 jnats Options.Builder）=====
    ping:
      interval: 20s                         # → pingInterval()，jnats 默认 2min
      max-outstanding: 2                    # → maxPingsOut()

    # ===== TLS/SSL 配置 =====
    tls:
      enabled: false                        # TLS 总开关
      opentls: false                        # → opentls()，接受任意证书（调试用）
      keystore-path:                        # → sslContext()，KeyStore 文件路径
      keystore-password:                    # → sslContext()，KeyStore 密码
      truststore-path:                      # → sslContext()，TrustStore 文件路径
      truststore-password:                  # → sslContext()，TrustStore 密码

    # ===== 协议配置（映射 jnats Options.Builder）=====
    protocol:
      verbose: false                        # → verbose()
      pedantic: false                       # → pedantic()
      no-headers: false                     # → noHeaders()
      no-responders: false                  # → noResponders()
      max-control-line: 4096                # → maxControlLine()

    # ===== Request 配置（映射 jnats Options.Builder）=====
    request:
      old-style: false                      # → oldRequestStyle()
      cleanup-interval: 5s                  # → requestCleanupInterval()
      default-timeout: 5s                   # 组件默认 RPC 请求超时

    # ===== 发送队列配置（映射 jnats Options.Builder）=====
    queue:
      max-outgoing-messages: -1             # → maxMessagesInOutgoingQueue()，-1=无限
      discard-when-full: false              # → discardMessagesWhenOutgoingQueueFull()

    # ===== 链路追踪（组件增强）=====
    tracing:
      enabled: true                         # 链路追踪开关

    # ===== 上下文透传（组件增强）=====
    header-propagation:
      enabled: true                         # 上下文透传开关
      headers:
        - x-tenant-id
        - x-rd-request-timezone
        - x-rd-request-language
        - x-rd-canary-tag

    # ===== 幂等去重（组件增强）=====
    idempotent:
      enabled: false                        # 应用层幂等去重开关
      datasource: memory                    # memory / redis
      ttl: 120000                           # 去重 TTL（ms）

    # ===== 错误处理（组件增强）=====
    error:
      max-retries: 3                        # 最大重试次数
      retry-delay: 1s                       # 重试间隔

    # ===== JetStream 配置 =====
    jetstream:
      enabled: false                        # JetStream 总开关（@ConditionalOnProperty）
      auto-provision: true                  # 启动时自动声明 Stream/Consumer
      streams:
        - name: AGENT_DIALOG
          subjects:
            - agent.dialog.>
          storage-type: file                # file / memory
          retention: limits                 # limits / interest / work-queue
          max-age: 7d                       # 消息最大保留时间
          max-bytes: -1                     # 最大字节数（-1=无限制）
          max-messages: -1                  # 最大消息数（-1=无限制）
          max-message-size: -1              # 单条消息最大字节数（-1=无限制）
          num-replicas: 1                   # 副本数
          discard: old                      # old / new（超出限制时丢弃策略）
          allow-rollup: false               # 是否允许 rollup header
          deny-delete: false                # 是否禁止删除消息
          consumers:
            - name: agent-worker
              filter-subject: agent.dialog.>
              ack-policy: explicit          # explicit / none / all
              ack-wait: 30s                 # ACK 等待超时
              max-deliver: 3                # 最大投递次数（超出进 Dead Letter）
              max-ack-pending: 1000         # 最大未确认消息数
              max-waiting: 512              # 最大等待拉取请求数
              inactive-threshold: 5m        # 非活跃消费者自动清理阈值
              deliver-policy: all           # all / last / new / by-start-sequence / by-start-time
              replay-policy: instant        # instant / original
              rate-limit: 0                 # 投递速率限制（bps，0=不限）
              sample-frequency: 0           # ACK 采样频率（百分比，0=关闭）
```

---

## 6. 使用示例

### 6.1 Core NATS 发布（fire-and-forget）

```java
@Autowired NatsBus natsBus;

natsBus.publish("agent.event.completed", event);
```

### 6.2 Core NATS 订阅

```java
@Autowired NatsBus natsBus;

natsBus.subscribe("agent.event.>", message -> {
    // 追踪/上下文/去重已自动处理
    handleEvent(message);
});
```

### 6.3 RPC 请求（AI Agent 对话）

```java
@Autowired NatsBus natsBus;

AgentResponse resp = natsBus.request(
    "agent.dialog.session-123",
    request,
    AgentResponse.class,
    Duration.ofSeconds(30)
);
```

### 6.4 注册 RPC Handler

```java
@Autowired NatsEndpoint natsEndpoint;

natsEndpoint.registerHandler("agent.dialog.*", AgentRequest.class, req -> {
    return agentService.chat(req);
    // 追踪/上下文/错误处理全自动
});
```

### 6.5 JetStream 发布（持久化）

```java
@Autowired JetStreamBus jetStreamBus;

PublishAck ack = jetStreamBus.publish("agent.dialog.completed", event);
// ack.getSeqno() → 服务端分配的序列号
```

### 6.6 JetStream 持续消费

```java
@Autowired JetStreamBus jetStreamBus;

MessageConsumer mc = jetStreamBus.consume("AGENT_DIALOG", "agent-worker", message -> {
    handleEvent(message);
    // 成功自动 ack，失败自动 nak（服务端重投递）
});
```

### 6.7 JetStream 批量拉取

```java
@Autowired JetStreamBus jetStreamBus;

FetchConsumer fc = jetStreamBus.fetch("AGENT_DIALOG", "agent-worker", 10);
Message msg;
while ((msg = fc.nextMessage()) != null) {
    process(msg);
    msg.ack();
}
```

---

## 7. 横切关注点处理流程

### 7.1 发布端（NatsBus.publish / JetStreamBus.publish）

```
用户调用 bus.publish(subject, message) 或 stream.publish(subject, message)
    │
    ├── 1. NatsMessageSerializer.serialize(message) → byte[]
    ├── 2. NatsHeaderInjector.inject(headers)         // 上下文注入
    ├── 3. NatsTracingSupport.startProducerSpan()      // span 创建 + W3C 注入
    ├── 4. connection.publish(...) 或 jetStream.publish(...)  // 发送
    └── 5. NatsTracingSupport.finishSpan()             // span 结束
```

### 7.2 Core NATS 消费端（NatsBus.subscribe 管道）

```
NATS 消息到达
    │
    ├── 1. TracingMessageDecorator                      // 提取 trace context + 创建 CONSUMER span
    │       └── ContextRestorationDecorator             // NATS Headers → HeaderContextHolder
    │               └── IdempotentMessageDecorator       // messageId 去重检查
    │                       └── 业务 Handler              // 用户代码
    │
    └── 各层装饰器 finally 块负责 span 结束 / 上下文清理
```

### 7.3 JetStream 消费端（JetStreamBus.consume 管道）

```
JetStream 消息到达
    │
    ├── 1. TracingMessageDecorator                      // 提取 trace context + 创建 CONSUMER span
    │       └── ContextRestorationDecorator             // NATS Headers → HeaderContextHolder
    │               └── IdempotentMessageDecorator       // messageId 去重检查
    │                       └── 业务 Handler              // 用户代码
    │
    ├── 成功 → msg.ack()
    ├── 失败 → msg.nak()（触发服务端重投递，受 maxDeliver 限制）
    └── 各层装饰器 finally 块负责 span 结束 / 上下文清理
```

### 7.4 RPC 服务端（NatsEndpoint 管道）

```
NATS Request 消息到达
    │
    ├── 1. TracingMessageDecorator (SERVER span)        // 提取 trace context + 创建 SERVER span
    │       └── ContextRestorationDecorator             // NATS Headers → HeaderContextHolder
    │               └── 反序列化请求 → 执行 Handler → 序列化响应
    │                       └── 注入追踪 header → 发布到 replyTo
    │
    └── finally: span 结束 / 上下文清理
```

---

## 8. 类清单总览

| 层 | 包 | 类名 | 类型 | 说明 |
|----|-----|------|------|------|
| L0 | enums | `ConnectionState` | enum | 连接状态 |
| L0 | enums | `AuthType` | enum | 认证类型 |
| L0 | exception | `NatsException` | class | 统一异常基类 |
| L0 | exception | `NatsConnectionException` | class | 连接异常 |
| L0 | exception | `NatsRpcException` | class | RPC 异常 |
| L0 | exception | `NatsSerializationException` | class | 序列化异常 |
| L0 | constant | `NatsConstants` | class | 常量定义 |
| L1 | strategy.serializer | `NatsMessageSerializer` | interface | 序列化契约 |
| L1 | strategy.header | `NatsHeaderInjector` | interface | Header 注入契约 |
| L1 | strategy.header | `NatsHeaderExtractor` | interface | Header 提取契约 |
| L1 | strategy.tracing | `NatsTracingSupport` | interface | 追踪契约 |
| L1 | strategy.idempotent | `NatsIdempotentChecker` | interface | 去重契约 |
| L1 | strategy.error | `NatsErrorStrategy` | interface | 错误处理契约 |
| L2 | impl.serializer | `JacksonNatsMessageSerializer` | class | Jackson 实现 |
| L2 | impl.header | `DefaultNatsHeaderInjector` | class | 默认注入实现 |
| L2 | impl.header | `DefaultNatsHeaderExtractor` | class | 默认提取实现 |
| L2 | impl.tracing | `OpenTelemetryNatsTracingSupport` | class | OTel 实现 |
| L2 | impl.idempotent | `MemoryNatsIdempotentChecker` | class | 内存去重实现 |
| L2 | impl.idempotent | `RedisNatsIdempotentChecker` | class | Redis 去重实现 |
| L2 | impl.error | `DefaultNatsErrorStrategy` | class | 默认错误策略 |
| L3 | connection | `NatsConnectionManager` | class | 连接管理（新 API: StreamContext） |
| L3 | connection | `NatsConnectionListener` | interface | 连接事件监听 |
| L3 | connection | `NatsAuthConfigurator` | class | 认证配置 |
| L3 | jetstream | `JetStreamManagementService` | class | JetStream 管理 |
| L4 | pipeline | `NatsMessageHandler` | interface | 消息处理函数式接口 |
| L4 | pipeline | `NatsMessageHandlerPipeline` | class | 管道构建器 |
| L4 | pipeline.decorator | `TracingMessageDecorator` | class | 追踪装饰器 |
| L4 | pipeline.decorator | `ContextRestorationDecorator` | class | 上下文恢复装饰器 |
| L4 | pipeline.decorator | `IdempotentMessageDecorator` | class | 去重装饰器 |
| L4 | pipeline | `NatsSubscriberFactory` | class | 订阅者工厂 |
| L5 | bus | `NatsBus` | class | Core NATS 门面（publish + subscribe + request） |
| L5 | stream | `JetStreamBus` | class | JetStream 门面（publish + consume + fetch + next） |
| L5 | endpoint | `NatsEndpoint` | class | RPC 端点注册 |
| L6 | - | `NatsComponent` | class | 统一门面 + SmartLifecycle |
| L7 | config | `NatsProperties` | class | 配置属性（15 个内部静态类） |
| L7 | config | `NatsProperties.Auth` | inner class | 认证配置 |
| L7 | config | `NatsProperties.Connection` | inner class | 连接配置 |
| L7 | config | `NatsProperties.Reconnect` | inner class | 重连配置 |
| L7 | config | `NatsProperties.Ping` | inner class | Ping 配置 |
| L7 | config | `NatsProperties.Tls` | inner class | TLS/SSL 配置 |
| L7 | config | `NatsProperties.Protocol` | inner class | 协议配置 |
| L7 | config | `NatsProperties.Request` | inner class | Request 配置 |
| L7 | config | `NatsProperties.Queue` | inner class | 发送队列配置 |
| L7 | config | `NatsProperties.Tracing` | inner class | 链路追踪配置 |
| L7 | config | `NatsProperties.HeaderPropagation` | inner class | 上下文透传配置 |
| L7 | config | `NatsProperties.Idempotent` | inner class | 幂等去重配置 |
| L7 | config | `NatsProperties.Error` | inner class | 错误处理配置 |
| L7 | config | `NatsProperties.JetStream` | inner class | JetStream 总配置 |
| L7 | config | `NatsProperties.StreamDefinition` | inner class | Stream 定义 |
| L7 | config | `NatsProperties.ConsumerDefinition` | inner class | Consumer 定义 |
| L7 | config | `NatsAutoConfiguration` | class | 自动配置 |
