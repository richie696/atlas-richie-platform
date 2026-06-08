# Richie HTTP Component

> 基于 **HttpClient 链式 API** 的统一 HTTP 客户端组件，支持同步 / 异步 / Future 三种执行方式，
> 内置 **OkHttp、Apache HttpClient5、JDK 11+ HttpClient、Spring RestClient** 四种底层实现，
> 通过配置即可一键切换，业务代码零侵入。

## 📋 目录

- [✨ 功能特性](#-功能特性)
- [🏗️ 模块结构](#-模块结构)
- [🚀 快速开始](#-快速开始)
- [🔧 同步请求](#-同步请求)
- [🔀 异步请求](#-异步请求)
- [📤 文件上传](#-文件上传)
- [📥 文件下载](#-文件下载)
- [🔐 SSL / HTTPS](#-ssl--https)
- [⚙️ 配置文件](#-配置文件)
- [🎯 最佳实践](#-最佳实践)
- [⚠️ 已知限制](#-已知限制)
- [❓ 常见问题](#-常见问题)

---

## ✨ 功能特性

### 核心能力

- ✅ **统一门面 `HttpClient`**：屏蔽 OkHttp / HttpClient5 / JDK / RestClient 四种底层实现差异，业务代码只依赖一个接口
- ✅ **链式 Builder `HttpRequest`**：通过 `http.get(url).param().header().timeout().execute()` 一行表达完整请求
- ✅ **三种执行方式**：同步阻塞 / 异步回调 / `CompletableFuture` 任选
- ✅ **四种内容类型**：`asJson()` / `asXml()` / `asSoap()` / `asForm()` 显式表达业务语义
- ✅ **多 Provider 热切换**：通过 `platform.component.http.provider` 切换底层实现，无需改业务代码
- ✅ **自动配置**：Spring Boot 启动时按 `provider` 自动装配对应 Adapter，无需手动注册 Bean
- ✅ **请求级超时覆盖**：每次请求可独立设置 `timeout(Duration)`，不影响全局默认

### 高级特性

- ✅ **OkHttp**：连接池、HTTP 缓存（GET 专用）、可调日志级别（NONE/BASIC/HEADERS/BODY）
- ✅ **HttpClient5**：连接池、TLS 1.2/1.3、`maxTotal` / `defaultMaxPerRoute` 调优
- ✅ **JDK HttpClient**：HTTP/1.1 + HTTP/2、虚拟线程、可配置代理、连接复用时间
- ✅ **RestClient**：支持外部注入 `RestClient.Builder` 复用业务侧的 `RestClient` 实例
- ✅ **SSL 灵活控制**：`strictSsl=true` 强制证书校验，`false` 进入 trust-all 模式（带警告日志）
- ✅ **泛型反序列化**：`TypeReference<T>` 支持嵌套泛型（`Page<User>`、`Map<String, List<X>>` 等）
- ✅ **文件上传**：标准 `multipart/form-data` 单文件上传（流式 `InputStream`）
- ✅ **流式下载钩子**：`HttpResponse.bodyStream()` 已暴露大文件下载入口

---

## 🏗️ 模块结构

```
atlas-richie-component-http              ← 父模块（POM 包装，不含代码）
├── atlas-richie-component-http-core     ← 门面 API：HttpClient / HttpRequest / HttpResponse
├── atlas-richie-component-http-okhttp   ← Provider：OkHttp 实现
├── atlas-richie-component-http-httpclient5  ← Provider：Apache HttpClient 5 实现
├── atlas-richie-component-http-jdk      ← Provider：JDK 11+ HttpClient 实现
└── atlas-richie-component-http-restclient   ← Provider：Spring RestClient 实现
```

### Provider 选型建议

| Provider | 适用场景 | 关键优势 |
|----------|---------|---------|
| **`okhttp`**（默认） | 通用 Web API 调用、HTTPS、需要连接池/缓存 | 性能均衡、连接池成熟、日志可调 |
| **`http_client_5`** | 多线程高并发、与 Apache 生态混用 | 连接池精细可控、TLS 配置丰富 |
| **`jdk`** | 无三方依赖诉求、HTTP/2 + 虚拟线程 | 零三方依赖、JDK 21+ 虚拟线程原生支持 |
| **`rest_client`** | 业务侧已用 Spring 6+ RestClient | 可复用业务侧已构造的 `RestClient.Builder` |

切换方式：仅修改 `platform.component.http.provider` 配置值，业务代码零修改。

---

## 🚀 快速开始

### 1. 添加依赖

```xml
<!-- 必选：core 门面 API -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-core</artifactId>
</dependency>

<!-- 必选：选择一个 Provider（四选一） -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-okhttp</artifactId>
</dependency>
<!--
其他可选 Provider（二选一或都不加）：
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-httpclient5</artifactId>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-jdk</artifactId>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-restclient</artifactId>
</dependency>
-->
```

> **注意**：必须引入一个 Provider 才能拿到 `HttpClient` Bean。未引入任何 Provider 时，启动会报 `NoSuchBeanDefinitionException`。

### 2. 选择 Provider

```yaml
# application.yml
platform:
  component:
    http:
      provider: okhttp   # okhttp | http_client_5 | jdk | rest_client
```

### 3. 注入 `HttpClient`

```java
import com.richie.component.http.core.HttpClient;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final HttpClient http;

    public UserService(HttpClient http) {
        this.http = http;
    }

    public User getById(String id) {
        return http.get("https://api.example.com/users/{id}", id)
                   .execute(User.class);
    }
}
```

### 4. 第一个请求

```java
// 同步 GET，自动反序列化为 User
User user = http.get("https://api.example.com/users/123").execute(User.class);

// 同步 POST，自动序列化 newUser 为 JSON
String response = http.post("https://api.example.com/users", newUser).execute();

// 异步（回调）
http.get("https://api.example.com/users")
    .async(new AsyncCallback<List<User>>() {
        @Override public void onResponse(HttpResponse resp, List<User> data) { /* ... */ }
        @Override public void onFailure(IOException ex) { /* ... */ }
    }, new TypeReference<List<User>>() {});

// CompletableFuture
CompletableFuture<User> f = http.get(url).future(User.class);
User user = f.get(5, TimeUnit.SECONDS);
```

---

## 🔧 同步请求

所有同步入口都从 `HttpClient` 拿到一个链式 `HttpRequest`，配置完成后调用 `execute()` 系列方法。

### GET 请求

```java
// 1. 最简：自动反序列化为对象
User user = http.get("https://api.example.com/users/1").execute(User.class);

// 2. 只拿字符串
String json = http.get("https://api.example.com/users/1").execute().bodyAsString();

// 3. 拿原始响应（需自行处理状态码/反序列化）
HttpResponse resp = http.get("https://api.example.com/users/1").execute();
if (resp.isSuccessful()) {
    User user = resp.bodyAs(User.class);
}
```

### GET 带查询参数 + 请求头 + 超时

```java
// 4. 链式三件套
List<Order> orders = http.get("https://api.example.com/orders")
    .param("status", "PAID")
    .param("page", "1")
    .header("Authorization", "Bearer " + token)
    .timeout(Duration.ofSeconds(10))
    .execute(new TypeReference<List<Order>>() {});

// 5. 批量添加 params / headers
Map<String, String> params = Map.of("status", "PAID", "page", "1");
Map<String, String> headers = Map.of("X-Trace-Id", traceId);

http.get(url)
    .params(params)
    .headers(headers)
    .execute(JsonNode.class);
```

### POST 请求

```java
// 6. JSON 序列化（默认即 JSON，可省略 asJson()）
UserCreateRequest req = new UserCreateRequest("alice", "alice@example.com");
User created = http.post("https://api.example.com/users", req)
                   .asJson()
                   .execute(User.class);

// 7. XML 请求体
OrderRequest xmlBody = new OrderRequest(...);
http.post("https://api.example.com/orders", xmlBody)
    .asXml()
    .execute(OrderResponse.class);

// 8. SOAP 1.2
http.post("https://ws.example.com/orderService", soapXml)
    .asSoap()
    .execute(SoapResponse.class);

// 9. application/x-www-form-urlencoded
http.post("https://api.example.com/login", Map.of(
        "username", "alice",
        "password", "secret"))
    .asForm()
    .execute(TokenResponse.class);

// 10. POST 无 body（只关心状态码）
HttpResponse resp = http.post("https://api.example.com/refresh").execute();
```

### PUT 请求

```java
// 11. 全量更新
UserUpdateRequest update = new UserUpdateRequest("alice", "alice@example.com");
User updated = http.put("https://api.example.com/users/1", update).execute(User.class);
```

### DELETE 请求

```java
// 12. 带 body 的 DELETE（部分业务接口需要）
http.delete("https://api.example.com/orders/batch", List.of(1L, 2L, 3L))
    .execute();

// 13. 无 body 的 DELETE
HttpResponse resp = http.delete("https://api.example.com/users/1").execute();
if (resp.isSuccessful()) {
    // 删除成功
}
```

### 响应处理

```java
HttpResponse resp = http.get(url).execute();

// 状态码
int code = resp.statusCode();
boolean ok = resp.isSuccessful();   // 2xx 判定

// 响应头（原始 Map<String, List<String>>，与 JDK HttpClient / OkHttp 头模型一致）
Map<String, List<String>> headers = resp.headers();
String contentType = headers.getOrDefault("Content-Type", List.of()).get(0);

// 响应体三种形式
byte[] raw = resp.body();                       // 原始字节
String text = resp.bodyAsString();              // UTF-8 解码
User user = resp.bodyAs(User.class);            // JSON 反序列化为对象
Page<User> page = resp.bodyAs(new TypeReference<Page<User>>() {});  // 泛型反序列化
```

---

## 🔀 异步请求

组件提供两种异步执行方式：**回调** 与 **`CompletableFuture`**。

### 方式一：异步回调

```java
http.get("https://api.example.com/users")
    .param("page", "1")
    .async(new AsyncCallback<List<User>>() {
        @Override
        public void onResponse(HttpResponse response, @Nullable List<User> data) {
            // 1. 网络/协议层成功；data 为反序列化结果，解析失败时可能为 null
            // 2. 不论 2xx/4xx/5xx 都会进入这里，业务层自行判断 response.isSuccessful()
            if (response.isSuccessful() && data != null) {
                log.info("Got {} users", data.size());
            } else {
                log.warn("Failed: status={} body={}", response.statusCode(), response.bodyAsString());
            }
        }

        @Override
        public void onFailure(IOException exception) {
            // 连接超时、DNS 失败、读取异常等场景进入这里
            log.error("Request failed", exception);
        }
    }, new TypeReference<List<User>>() {});
```

> **回调线程**：回调在底层 Provider 的 IO 线程池中执行（OkHttp Dispatcher / HttpClient5 Reaper / JDK Executor）。注意线程安全。

### 方式二：`CompletableFuture`（推荐）

```java
CompletableFuture<User> future = http.get(url).future(User.class);

// 1. 阻塞等待
User user = future.get(5, TimeUnit.SECONDS);

// 2. 链式处理
future.thenAccept(u -> cache.put(u.getId(), u))
      .exceptionally(ex -> {
          log.error("Failed", ex);
          return null;
      });

// 3. 多请求并发 + 全部完成
CompletableFuture<User>    f1 = http.get(url1).future(User.class);
CompletableFuture<Account> f2 = http.get(url2).future(Account.class);
CompletableFuture<Void>    all = CompletableFuture.allOf(f1, f2);
all.join();
User user    = f1.join();
Account acct = f2.join();
```

### 异步错误处理

```java
try {
    User user = http.get(url).future(User.class).get(5, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    log.warn("Request timeout");
} catch (ExecutionException e) {
    // 4xx/5xx 会走异常分支（HttpResponse 状态码可在 Provider 实现中决定是否抛异常）
    log.error("Request failed", e.getCause());
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

---

## 📤 文件上传

`HttpRequest.multipart(fieldName, fileName, InputStream)` 是当前支持的唯一上传方式。
文件以**流式 InputStream** 传入，Provider 负责把流包装为 `multipart/form-data` 实体。

### 单文件上传

```java
try (InputStream in = new FileInputStream("/tmp/report.pdf")) {
    HttpResponse resp = http.post("https://api.example.com/files")
        .param("bucket", "reports")                        // 业务表单字段
        .param("category", "monthly")
        .header("Authorization", "Bearer " + token)
        .multipart("file", "report.pdf", in)               // ← multipart 字段
        .timeout(Duration.ofMinutes(2))                    // 大文件建议加大超时
        .execute();

    if (resp.isSuccessful()) {
        UploadResult result = resp.bodyAs(UploadResult.class);
        log.info("Uploaded: fileId={}", result.getFileId());
    }
}
```

### 多业务字段 + 文件

```java
// 业务参数通过 param() 传递，与文件一同作为 multipart field 提交
try (InputStream avatar = new FileInputStream("/tmp/avatar.png")) {
    http.post("https://api.example.com/profile")
        .param("nickname", "alice")
        .param("bio", "hello world")
        .multipart("avatar", "avatar.png", avatar)
        .execute(ProfileResponse.class);
}
```

### 已知限制

> **当前 `multipart()` 只支持单文件**。多文件上传请循环调用或后续等待多文件 API。
> 当前 **HttpClient5 Provider** 完整支持 multipart 实体构建；其他 Provider 在文件上传场景请使用 HttpClient5。

---

## 📥 文件下载

`HttpResponse` 同时提供 **字节数组** 和 **输入流** 两种响应体访问方式。

### 字节数组（适合中小文件）

```java
// 完整下载
HttpResponse resp = http.get("https://cdn.example.com/installer.exe").execute();
if (resp.isSuccessful()) {
    byte[] data = resp.body();
    Files.write(Paths.get("/tmp/installer.exe"), data);
    log.info("Downloaded {} bytes", data.length);
}

// 拿到文件名字段
String contentDisposition = resp.headers().getOrDefault("Content-Disposition", List.of()).get(0);
```

### 流式下载（API 已就位，Provider 暂以一次性读取实现）

```java
HttpResponse resp = http.get("https://cdn.example.com/big-archive.zip").execute();
try (InputStream in = resp.bodyStream();
     OutputStream out = Files.newOutputStream(Paths.get("/tmp/big.zip"))) {
    in.transferTo(out);
}
```

> **注意**：当前 4 个 Provider 内部都使用 `BodyHandlers.ofByteArray()` 一次性读取到内存。
> `bodyStream()` API 已经暴露为未来"流式下载"的扩展点，但**当前实现下不推荐对超大文件（GB 级）使用**，
> 以免 OOM。Provider 后续将基于此 API 切换为 `BodyHandlers.ofInputStream()`。

---

## 🔐 SSL / HTTPS

### 严格模式（默认，生产环境推荐）

```yaml
platform:
  component:
    http:
      provider: okhttp
      strict-ssl: true   # 默认值，校验证书链 + 主机名
```

### 跳过证书校验（仅开发/联调）

```yaml
platform:
  component:
    http:
      provider: okhttp
      strict-ssl: false   # ⚠️ 启动日志会输出 WARN
```

开启后 Provider 会：
- 注入 trust-all 的 `X509TrustManager`
- 关闭主机名校验
- 日志中输出 `⚠️ Insecure mode: skipping all certificate verification — do NOT use in production`

> **OkHttp / JDK Provider 已实现 strict-ssl=false；HttpClient5 / RestClient Provider 暂未处理此开关。**

---

## ⚙️ 配置文件

### 核心配置 `platform.component.http`

```yaml
platform:
  component:
    http:
      # 选择底层实现（必填）
      provider: okhttp         # okhttp | http_client_5 | jdk | rest_client

      # 全局 SSL 开关（默认 true，强制证书校验）
      strict-ssl: true
```

### OkHttp Provider `platform.component.http.okhttp`

```yaml
platform:
  component:
    http:
      provider: okhttp
      okhttp:
        # === 超时（秒）===
        read-timeout: 5                    # 读取超时
        read-timeout-time-unit: SECONDS
        write-timeout: 5                   # 写入超时（大文件上传建议 30~60）
        write-timeout-time-unit: SECONDS
        connect-timeout: 5                 # 连接超时（内网 3-5，外网 5-10）
        connect-timeout-time-unit: SECONDS
        call-timeout: 15                   # 整体调用超时（建议 = connect+read+write 的 3 倍以内）
        call-timeout-time-unit: SECONDS

        # === 日志级别 ===
        level: BODY                        # NONE / BASIC / HEADERS / BODY

        # === 缓存（仅对 GET 生效）===
        enable-cache: false
        cache-path: /opt/okhttp3/cache/
        cache-size: 100                    # MB

        # === 连接池 ===
        max-requests: 250                  # 全局最大并发请求
        max-requests-per-host: 25          # 同一 Host 最大并发
        keep-alive-duration: 5             # 空闲连接保持时间
        keep-alive-time-unit: MINUTES
```

### HttpClient5 Provider `platform.component.http.httpclient5`

```yaml
platform:
  component:
    http:
      provider: http_client_5
      httpclient5:
        connection-request-timeout: 5      # 从连接池获取连接的超时（秒）
        connection-request-timeout-time-unit: SECONDS
        response-timeout: 5                # 等待服务端响应的超时（秒）
        response-timeout-time-unit: SECONDS
        max-total: 250                     # 连接池总连接数
        default-max-per-route: 25          # 每个路由最大连接数
```

### JDK HttpClient Provider `platform.component.http.jdk`

```yaml
platform:
  component:
    http:
      provider: jdk
      jdk:
        # === 超时 ===
        connect-timeout: 5s                # Duration 类型（5 seconds / 500ms）

        # === 协议与重定向 ===
        version: HTTP_2                    # HTTP_1_1 | HTTP_2
        follow-redirects: false            # 自动跟随 3xx
        priority: 16                       # HTTP/2 优先级 1~256，越小越高

        # === 连接复用 ===
        keep-alive-time: 30s               # 空闲连接保持时间
        max-concurrent-streams: 100        # HTTP/2 最大并发流数

        # === 代理 ===
        proxy-host: proxy.example.com      # 可选
        proxy-port: 8080

        # === 异步执行器 ===
        use-virtual-threads: true          # JDK 21+/25 推荐开启
```

### RestClient Provider

RestClient Provider **没有自己的 `HttpProperties`**，仅复用 core 的 `provider` + `strict-ssl` 配置。
其余行为完全继承业务侧已配置的 `RestClient`（如有）。

```yaml
platform:
  component:
    http:
      provider: rest_client
      strict-ssl: true
```

业务侧可通过注入 `RestClient.Builder` 来定制底层 `RestClient`：

```java
@Bean
public RestClient.Builder customRestClientBuilder() {
    return RestClient.builder()
                     .baseUrl("https://api.example.com")
                     .defaultHeader("X-Custom", "value");
}
```

---

## 🎯 最佳实践

### 1. Provider 选择

```text
# 默认首选 OkHttp（性能均衡、配置最丰富）
spring-boot-starter  →  atlas-richie-component-http-okhttp

# 已有 Apache HttpClient 5 依赖、与 Camel / CXF 混用  →  httpclient5
# 不想引入三方 HTTP 依赖、JDK 21+ 虚拟线程  →  jdk
# 业务侧已统一用 Spring RestClient  →  rest_client
```

### 2. 超时调优

```yaml
# 内部服务：低延迟高并发
connect-timeout: 3
read-timeout: 5
write-timeout: 5
call-timeout: 15

# 外部服务：容忍慢响应
connect-timeout: 10
read-timeout: 30
write-timeout: 30
call-timeout: 90

# 文件上传：放大 write-timeout
write-timeout: 120
call-timeout: 300
```

### 3. 大文件上传

```java
try (InputStream in = new BufferedInputStream(new FileInputStream(bigFile))) {
    http.post(uploadUrl, null)              // body 不再传业务对象
        .multipart("file", bigFile.getName(), in)
        .timeout(Duration.ofMinutes(5))     // 覆盖默认超时
        .execute();
}
```

### 4. 统一 Header 管理

```java
// 在 Service 顶部把 trace/auth header 抽成工具方法
private HttpRequest withCommonHeaders(HttpRequest req) {
    return req.header("X-Trace-Id", TraceContext.traceId())
              .header("X-Request-Time", Instant.now().toString());
}

// 用法
http.get(url)
    .params(Map.of("page", "1"))
    .header("Authorization", "Bearer " + token)
    .timeout(Duration.ofSeconds(10))
    .execute(JsonNode.class);
```

### 5. 异步请求线程安全

回调在 Provider 内部线程池中执行，业务回调中**仅做线程安全的数据写入**（如 `ConcurrentHashMap`），
不要在回调里做阻塞/重量级操作；如需重活，转交业务线程池或 `CompletableFuture` 链式调度。

### 6. 错误响应不要直接反序列化

```java
HttpResponse resp = http.get(url).execute();
if (resp.isSuccessful()) {
    User user = resp.bodyAs(User.class);
} else {
    // 错误响应体的结构通常与成功响应不同，记录原文更稳妥
    log.warn("Failed: status={} body={}", resp.statusCode(), resp.bodyAsString());
    throw new BizException("REMOTE_ERROR", resp.statusCode());
}
```

### 7. Provider 切换时的兼容检查

```text
1. 是否使用了 file upload？  →  HttpClient5 Provider 已实现 multipart，其他 Provider 暂未实现
2. 是否用了 HTTP/2 only 特性？  →  仅 jdk provider 支持（version=HTTP_2）
3. 是否依赖 OkHttp 拦截器？  →  切换到非 OkHttp Provider 会丢失
```

---

## ⚠️ 已知限制

| 限制项 | 说明 | 临时方案 |
|--------|------|---------|
| **HTTP 方法** | `HttpMethod` 枚举仅有 `GET / POST / PUT / DELETE`，**未实现 `PATCH / HEAD / OPTIONS`** | 暂时绕开；后续按需扩展 |
| **multipart 上传** | `multipart()` 当前仅支持**单文件** | 多文件场景循环调用，或走 `HttpClient5 Provider` |
| **流式下载** | `bodyStream()` API 已暴露，但 Provider 内部仍一次性读为 `byte[]` | 中小文件用 `body()`，大文件等待 Provider 升级 |
| **strict-ssl=false** | OkHttp / JDK 已实现 trust-all；HttpClient5 / RestClient 暂未实现该开关 | 切换 OkHttp / JDK Provider 即可开启 |
| **响应拦截器** | 当前无统一拦截器钩子（如鉴权刷新、重试） | 业务层封装 `HttpRequest` 装饰方法或自行注入 Provider 内部客户端 |

---

## ❓ 常见问题

### Q1: 启动报 `NoSuchBeanDefinitionException: HttpClient` 怎么办？

**A:** 你引入了 `http-core` 但没引入任何 Provider。补一个 Provider 依赖即可：

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-okhttp</artifactId>
</dependency>
```

### Q2: 切换 `provider` 后启动报 `BeanDefinitionOverrideException`？

**A:** 多个 Provider 都被装配了。请保留**唯一一个** Provider 依赖，并删除其他 Provider 的 `org.springframework.boot.autoconfigure.AutoConfiguration` 引入。

### Q3: 如何只对单个请求设置超时？

```java
http.get(url)
    .timeout(Duration.ofSeconds(5))   // 覆盖全局默认
    .execute(JsonNode.class);
```

### Q4: 异步回调在哪个线程执行？

**A:** 取决于 Provider：
- OkHttp：内置 `Dispatcher` 线程池
- HttpClient5：内置 IOReactor 线程池
- JDK：`use-virtual-threads=true` 时为虚拟线程，否则默认 `Executors.newCachedThreadPool()`
- RestClient：Spring `AsyncRestClient` 内部线程池

**注意线程安全**，重活请转交业务线程池。

### Q5: 上传文件应该用哪个 Provider？

**A:** **HttpClient5 Provider**。其他 Provider 当前对 `multipart()` 的支持尚未完整，调用会得到空 body 或异常。

### Q6: 如何禁用请求日志？

```yaml
platform:
  component:
    http:
      provider: okhttp
      okhttp:
        level: NONE   # NONE / BASIC / HEADERS / BODY
```

> 当前仅 OkHttp Provider 暴露日志级别配置。

### Q7: RestClient Provider 报错 `RestClient.Builder` 找不到？

**A:** RestClient Provider 依赖 `org.springframework.web.client.RestClient`（Spring Framework 6.1+）。
请确认项目使用的 Spring 版本 ≥ 6.1，或切到 `restclient` 专用 starter：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### Q8: 如何处理 4xx / 5xx 响应？

```java
HttpResponse resp = http.get(url).execute();
if (!resp.isSuccessful()) {
    log.warn("Remote call failed: status={} body={}",
             resp.statusCode(), resp.bodyAsString());
    throw new BizException("REMOTE_CALL_FAILED", resp.statusCode());
}
```

Provider 不会把 4xx/5xx 包装为异常 — 通过 `isSuccessful()` 自行判定。

### Q9: 未来会支持 PATCH / HEAD / OPTIONS 吗？

**A:** 计划中。当前为最小可用集，需要扩展时可在 `HttpMethod` 枚举追加值 + Provider 中处理新 case，
**业务层 API 形态不变**（链式 `execute()` 保持兼容）。

---

## 🔗 相关链接

- [Spring Boot Auto-Configuration](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#features.external-config)
- [OkHttp 官方文档](https://square.github.io/okhttp/)
- [Apache HttpClient 5 文档](https://hc.apache.org/httpcomponents-client-5.2.x/current/httpclient5/apidocs/)
- [JDK 11+ HttpClient 文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html)
- [Spring RestClient 文档](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)

---

**Richie HTTP Component** — 一行切换底层，专注业务逻辑 🚀
