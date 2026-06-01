# 组件使用规范

本规范汇总 Atlas Richie 技术中台的基础包与组件（`atlas-richie-base`、`atlas-richie-component-*`），约定在编码时**优先使用中台组件**，禁止直接操作底层 SDK 或自行重复封装。

> 说明：本文档是"选型与使用场景"的指导，具体 API 细节以各组件自身的 README 为准。

---

## 1. 基础包：atlas-richie-base

### 1.1 atlas-richie-dependencies（依赖与插件管理）

- **作用**：统一管理 Spring Boot/Cloud、MyBatis Plus、Redis、对象存储 SDK、工具库等第三方依赖版本；集中管理 Maven 插件。
- **何时使用**：所有工程根 POM **必须继承** `com.richie.base:atlas-richie-dependencies`；禁止在子模块随意指定第三方依赖版本。
- **如何使用**：

  ```xml
  <parent>
      <groupId>com.richie.base</groupId>
      <artifactId>atlas-richie-dependencies</artifactId>
      <version>${revision}</version>
      <relativePath/>
  </parent>
  ```

- 子模块只声明 `groupId/artifactId`，不写 `<version>`，版本由 BOM 统一管理。

### 1.2 atlas-richie-context（工具类，不含异常）

- **作用**：提供纯工具类，不涉及业务契约。
- **包含**：
  - 上下文传递：`LoginUserContextHolder`、`HeaderContextHolder`、`SpringContextHolder`
  - JSON/XML：`JsonUtils`、`XmlUtils`
  - 工具：`JwtUtils`、`HashUtils`、`RSAUtils`、`SignatureUtils`、`SpringBeanUtils`、`CommonUtils`
  - 集合：`Collections`、`Collection2MapUtils`、`CharacterUtils`
  - 时间：`Timer`
  - 枚举：`UnicodeEnum`
- **何时使用**：
  - 在异步/线程池中传递登录用户、Header 信息时 → `LoginUserContextHolder`、`HeaderContextHolder`
  - 需要在非 Spring 上下文获取 Bean 时 → `SpringContextHolder`
  - JSON 序列化/反序列化 → `JsonUtils`
  - JWT 编解码 → `JwtUtils`
- **使用示例**：

  ```java
  // 登录用户上下文（线程安全，自动清理）
  LoginUserContextHolder.setUserInfo(userVO);
  try {
      // 业务逻辑
  } finally {
      LoginUserContextHolder.clear();
  }
  ```

### 1.3 atlas-richie-contract（契约层：统一响应 + 异常体系）

- **作用**：提供跨服务共享的契约类型（DTO、异常、响应包装）。
- **统一响应：`ApiResult<T>`**（替代旧版 `ResultVO`）：

  ```java
  @Data
  @Accessors(chain = true)
  public class ApiResult<T> implements Serializable {
      private boolean success = true;
      private T data;
      private String code;
      private String msg;
      private Map<String, Map<String, String>> i18nDict; // 主档数据国际化
      private final long timestamp = System.currentTimeMillis();
  }
  ```

  使用方式：

  ```java
  // 成功
  return ApiResult.success(userDetail);
  return ApiResult.success("操作成功", userDetail);
  return ApiResult.success("200", "操作成功", userDetail);

  // 失败
  return ApiResult.error("500", "服务器异常");
  return ApiResult.error("USER_NOT_FOUND", "用户不存在");
  ```

- **异常体系**：
  - `BaseException extends RuntimeException` — 异常基类
  - `BusinessException extends BaseException` — 业务异常（首选）
  - `PlatformRuntimeException extends RuntimeException` — 平台运行时异常
  - `PlatformDataAccessException extends RuntimeException` — 平台数据访问异常

  使用示例：

  ```java
  if (user == null) {
      throw new BusinessException("USER_NOT_FOUND", "用户不存在");
  }
  ```

- **何时使用**：
  - Controller 返回类型统一用 `ApiResult<T>`
  - 业务异常使用/继承 `BusinessException`
  - 不随意使用裸 `RuntimeException`

---

## 2. 微服务与 HTTP 通信

### 2.1 atlas-richie-component-microservice（微服务间调用）

- **作用**：封装基于 Spring Cloud OpenFeign 和 Spring RestClient 的微服务调用，统一连接池、超时、日志、请求头传递。
- **何时使用**：服务间的 HTTP/RPC 调用**必须使用本组件**，不直接 `new OkHttpClient` 或写裸 URL。
- **如何使用**：

  Feign Client：

  ```java
  @FeignClient(name = "user-service")
  public interface UserServiceClient {
      @GetMapping("/users/{id}")
      ApiResult<UserVO> getUser(@PathVariable String id);
  }
  ```

  RestClient（Spring 内置）：

  ```java
  @Service
  public class UserQueryService {
      private final RestClient restClient;
      public UserVO getUser(String id) {
          return restClient.get()
              .uri("http://user-service/users/{id}", id)
              .retrieve()
              .body(UserVO.class);
      }
  }
  ```

- 请求头（租户、语言、traceId 等）由组件内拦截器自动透传，无需业务手动处理。

### 2.2 atlas-richie-component-http（对外 HTTP 调用）

- **作用**：封装外部系统 HTTP 调用，提供统一的 `HttpClient` 接口，支持 OkHttp、RestClient（Spring 内置）、JDK HttpClient、HttpClient5 四种底层实现。
- **何时使用**：调用**外部系统**（第三方支付、开放平台、无注册中心的老系统）时使用。
- **核心接口**：

  ```java
  public interface HttpClient {
      HttpResponse get(String url);
      <T> T get(Class<T> responseType, String url);
      <T> T get(TypeReference<T> type, String url);
      HttpResponse post(String url, Object body);
      <T> T post(Class<T> responseType, String url, Object body);
      // ... 其他方法
  }
  ```

- **使用示例**：

  ```java
  @Service
  public class ExternalUserApi {
      private final HttpClient httpClient;
      public UserInfo getUser(String userId) {
          String url = "https://api.example.com/users/" + userId;
          return httpClient.get(UserInfo.class, url);
      }
  }
  ```

- **4 个 provider**（按需引入依赖）：
  - `atlas-richie-component-http-okhttp`：OkHttp 实现
  - `atlas-richie-component-http-restclient`：Spring RestClient 实现
  - `atlas-richie-component-http-jdk`：JDK 原生 `java.net.http.HttpClient` 实现
  - `atlas-richie-component-http-httpclient5`：Apache HttpClient5 实现
- 按需引入对应 provider 的依赖即可切换实现，无需改业务代码。

---

## 3. 消息队列与事件驱动

### atlas-richie-component-messaging（统一 MQ 抽象）

- **作用**：基于 Spring Cloud Stream 的统一 MQ 组件，支持 Kafka、RabbitMQ、RocketMQ、Kinesis、GCP Pub/Sub、Azure Event Hubs、AWS SQS/SNS、Pulsar、Solace 等多种实现。
- **何时使用**：所有基于 MQ 的业务场景**必须通过本组件**，严禁直接使用 `KafkaTemplate`、`RabbitTemplate` 等底层模板。
- **核心接口**：
  - `MessageService`：统一发送消息
  - `BaseConsumer` / `AbstractBaseConsumer`：统一消费入口

- **使用示例**：

  发送：

  ```java
  @Service
  public class OrderEventPublisher {
      private final MessageService messageService;
      public void publishOrderCreated(OrderCreatedEvent event) {
          messageService.sendMessage("order-created", event);
      }
  }
  ```

  消费：

  ```java
  @Component
  public class OrderCreatedConsumer extends AbstractBaseConsumer {
      @Override
      protected boolean handle(MessageEvent event) {
          OrderCreatedEvent body = event.getBody(OrderCreatedEvent.class);
          // 调用 service 处理
          return true; // true = ACK，false = 重试/DLQ
      }
  }
  ```

- 幂等去重、重试、死信队列由组件统一处理，业务层只需实现消费逻辑。

---

## 4. 缓存与 Redis：atlas-richie-component-cache

- **作用**：提供统一的 Redis 能力，通过 `GlobalCache` 静态门面访问：
  - KV、Hash、List、Set、ZSet、GEO、HyperLogLog、Bitmap
  - Lua 脚本
  - 分布式锁（含自动续期）
  - 限流
  - Redis Stream 消息队列
  - 事件订阅（KeySpace Notifications）
  - 发布订阅（Pub/Sub）
  - L2 本地缓存（Caffeine）
- **何时使用**：所有 Redis 相关场景**必须通过 `GlobalCache`**，禁止直接操作 `RedisTemplate`、Jedis 客户端。
- **`GlobalCache` 是静态门面**，依赖 Spring 注入 `GlobalCacheManager` 实例：
  - `GlobalCache.string()` → `StringFunction`（KV）
  - `GlobalCache.hash()` → `HashFunction`
  - `GlobalCache.list()` → `ListFunction`
  - `GlobalCache.set()` → `SetFunction`
  - `GlobalCache.zSet()` → `ZSetFunction`
  - `GlobalCache.geo()` → `GeoFunction`
  - `GlobalCache.stream()` → `StreamFunction`
  - `GlobalCache.hyperLog()` → `HyperLogFunction`
  - `GlobalCache.bitmap()` → `BitmapFunction`
  - `GlobalCache.lua()` → `LuaFunction`
  - `GlobalCache.limiter()` → `LimiterFunction`
  - `GlobalCache.event()` → `EventFunction`（KeySpace Notifications）
  - `GlobalCache.notification()` → `NotificationFunction`（Pub/Sub）
  - `GlobalCache.lock()` → `LockFunction`（分布式锁）

- **使用示例**：

  ```java
  // 基础 KV（O(1)）
  GlobalCache.addStringCache("user:" + userId, userName, 3600_000L);
  String userName = GlobalCache.getStringCache("user:" + userId);

  // 分布式锁（自动续期）
  try (var lock = GlobalCache.lock().tryLock("lock:order:" + orderId, 5L)) {
      if (lock.success()) {
          // 临界区
      }
  }

  // Redis Stream 生产
  GlobalCache.stream().addMessage("stream:order:created", orderEvent);

  // Redis Stream 消费（通过 stream() 获取 StreamFunction）
  StreamFunction stream = GlobalCache.stream();
  // 具体消费方式见组件 README
  ```

- **性能复杂度注意**：
  - O(1)：String、Hash field、List push/single item — 核心链路推荐
  - O(log n)：ZSet 有序集合 — 可控范围内使用，限制成员量和区间宽度
  - O(n) 及以上：Set 全量、List 全量、Hash 全量扫描 — **核心链路禁止**，仅限离线/管理端

- 配置前缀：`platform.component.redis`

---

## 5. 对象存储：atlas-richie-component-storage（12 个实现）

- **作用**：为 S3、OSS、COS、MinIO、TOS、KS3、OBS、Azure Blob、Local、FTP、SFTP、SMB 提供统一接口。
- **何时使用**：文件存储场景**通过存储组件**，不直接依赖云厂商 SDK。
- **使用示例**（通用接口）：

  ```java
  StorageService storageService; // 由组件自动注入
  // 上传
  String url = storageService.upload(fileName, inputStream);
  // 下载
  InputStream is = storageService.download(url);
  // 删除
  storageService.delete(url);
  ```

- 按需引入对应 provider 的依赖（`atlas-richie-component-storage-s3`、`atlas-richie-component-storage-oss` 等），底层自动切换。

---

## 6. 向量存储：atlas-richie-component-vector（8 个实现）

- **作用**：为 Elasticsearch、Milvus、MongoDB Atlas、Neo4j、PostgreSQL、Qdrant、Redis、Weaviate 提供统一向量检索接口。
- **何时使用**：AI 场景下的向量存储与检索。
- 使用方式见 `atlas-richie-component-vector-core` 的 README。

---

## 7. 其他组件速查

| 组件 | 作用 | 使用场景 |
|---|---|---|
| `atlas-richie-component-dao` | MyBatis Plus 封装，分页、审计字段、多数据源路由 | 数据库访问 |
| `atlas-richie-component-dao-tenant` | 多租户 DAO 扩展 | 多租户场景 |
| `atlas-richie-component-logging` | 统一日志格式、脱敏、traceId 透传 | 所有日志 |
| `atlas-richie-component-tracing` | OpenTelemetry 接入 | 链路追踪 |
| `atlas-richie-component-skywalking` | SkyWalking APM 接入 | 应用监控 |
| `atlas-richie-component-threadpool` | 统一线程池配置、动态调整 | 异步任务 |
| `atlas-richie-component-web` | 统一异常处理、响应包装、全局过滤器 | Controller 层 |
| `atlas-richie-component-i18n` | 国际化消息、错误码多语言 | 错误提示 |
| `atlas-richie-component-statemachine` | 复杂状态流转（订单、审批等） | 状态驱动业务 |
| `atlas-richie-component-mfa` | TOTP / MFA 认证 | 二次验证 |
| `atlas-richie-component-mqtt` | MQTT 客户端封装 | IoT 设备通信 |
| `atlas-richie-component-liquibase` | 数据库变更管理 | DB migration |
| `atlas-richie-component-mongodb` | MongoDB 操作封装 | NoSQL 文档存储 |
| `atlas-richie-component-search` | 统一搜索能力（ES/Solr） | 全文检索 |
| `atlas-richie-component-desensitize` | 数据脱敏 | 敏感信息处理 |
| `atlas-richie-component-ai` | Spring AI + AgentScope 封装 | AI/LLM 调用 |
| `atlas-richie-gateway-service` | 网关服务，含鉴权、限流等 | API 网关 |

---

## 8. 总体约束（必须遵守）

1. **能用中台组件解决的场景，禁止直接使用底层 SDK 或手写重复封装。**
2. **微服务间调用 → `atlas-richie-component-microservice`；对外 HTTP 调用 → `atlas-richie-component-http`。**
3. **缓存/Redis/锁/限流 → `atlas-richie-component-cache`；MQ → `atlas-richie-component-messaging`。**
4. **上下文工具 → `atlas-richie-context`；统一响应/异常 → `atlas-richie-contract`。**
5. **跨服务的契约类型（DTO、异常、ApiResult）放在 `atlas-richie-contract`，不放业务代码里。**
6. **Redis Stream 通过 `GlobalCache.stream()` 获取 API**，不用 `GlobalCache.readStreamMessages(...)` 等旧 API。
7. **组件配置通过 `@ConfigurationProperties`**（前缀如 `platform.component.redis`），不放硬编码值。
