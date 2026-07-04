# Atlas Richie Microservice组件 (atlas-richie-component-microservice)

基于 Spring Cloud OpenFeign 和 Spring RestClient 的微服务调用组件，提供统一的 HTTP 客户端配置、请求拦截器、连接池管理等能力。

## 📖 目录

- [✨ 功能特性](#✨-功能特性)
  - [核心能力](#核心能力)
  - [高级特性](#高级特性)
- [🚀 快速开始](#🚀-快速开始)
  - [1. 添加依赖](#1-添加依赖)
  - [2. 配置微服务组件](#2-配置微服务组件)
  - [3. 使用 OpenFeign](#3-使用-openfeign)
  - [4. 使用 RestClient](#4-使用-restclient)
- [🔧 核心功能](#🔧-核心功能)
  - [1. OpenFeign 配置](#1-openfeign-配置)
  - [2. RestClient 配置](#2-restclient-配置)
  - [3. 连接池管理](#3-连接池管理)
  - [4. SSL/TLS 配置](#4-ssl/tls-配置)
- [⚙️ 配置说明](#⚙️-配置说明)
  - [基础配置](#基础配置)
  - [连接池配置](#连接池配置)
  - [OkHttp 配置](#okhttp-配置)
- [🎯 最佳实践](#🎯-最佳实践)
  - [1. OpenFeign 使用](#1-openfeign-使用)
  - [2. RestClient 使用](#2-restclient-使用)
  - [3. 请求头管理](#3-请求头管理)
  - [4. 超时配置](#4-超时配置)
  - [5. 连接池优化](#5-连接池优化)
- [❓ 常见问题](#❓-常见问题)
  - [Q1: 如何选择 OpenFeign 还是 RestClient？](#q1-如何选择-openfeign-还是-restclient？)
  - [Q2: 请求头没有传递怎么办？](#q2-请求头没有传递怎么办？)
  - [Q3: 如何自定义请求拦截器？](#q3-如何自定义请求拦截器？)
  - [Q4: 如何配置多个 Feign 客户端？](#q4-如何配置多个-feign-客户端？)
  - [Q5: 如何启用请求日志？](#q5-如何启用请求日志？)
  - [Q6: 连接池配置如何优化？](#q6-连接池配置如何优化？)
- [📎 📝 总结](#📎-📝-总结)
---

## ✨ 功能特性

### 核心能力

- ✅ **OpenFeign 集成**：基于 Spring Cloud OpenFeign，提供声明式 HTTP 客户端
- ✅ **RestClient 支持**：支持 Spring 6.0+ 的 RestClient
- ✅ **OkHttp 客户端**：使用 OkHttp 作为底层 HTTP 客户端，性能优异
- ✅ **请求头传递**：自动传递请求头信息（语言、时区、租户等）
- ✅ **连接池管理**：自动管理连接池，提升性能
- ✅ **SSL/TLS 支持**：支持自定义证书、信任库配置

### 高级特性

- ✅ **自动配置**：开箱即用，零配置启动
- ✅ **请求拦截器**：自动传递必要的请求头信息
- ✅ **日志记录**：支持请求/响应日志记录
- ✅ **缓存支持**：支持 HTTP 响应缓存（可选）

---

## 🚀 快速开始

### 1) 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-microservice</artifactId>
</dependency>

<!-- Spring Cloud OpenFeign（如果使用 OpenFeign） -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

### 2) 配置微服务组件

```yaml
# application.yml
spring:
  cloud:
    openfeign:
      okhttp:
        enabled: true  # 启用 OkHttp 客户端
      httpclient:
        # 连接池配置
        max-connections: 200
        max-connections-per-route: 50
        time-to-live: 5
        time-to-live-unit: minutes
        # OkHttp 客户端配置
        ok-http:
          # HTTP协议版本
          protocols:
            - HTTP_2
          # 超时配置
          read-timeout: 20s
          connect-timeout: 8s
          write-timeout: 15s
          call-timeout: 50s
          # 日志配置
          level: BASIC  # NONE、BASIC、HEADERS、BODY
          # 缓存配置
          enable-cache: false
          cache-path: /tmp/okhttp3/cache/
          cache-size: 100
          # 安全配置
          insecure-trust-all: false
          hostname-verification: true
```

### 3) 使用 `OpenFeign`

```java
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "http://localhost:8080")
public interface UserServiceClient {
    
    @GetMapping("/users/{id}")
    User getUser(@PathVariable String id);
    
    @GetMapping("/users")
    List<User> getUsers();
}
```

### 4) 使用 `RestClient`

```java
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    
    @Autowired
    private RestClient restClient;
    
    public User getUser(String id) {
        return restClient.get()
            .uri("http://localhost:8080/users/{id}", id)
            .retrieve()
            .body(User.class);
    }
}
```

---

## 🔧 核心功能

### 1) `OpenFeign` 配置

组件自动配置 OpenFeign 使用 OkHttp 客户端，并提供以下功能：

#### 请求头自动传递

组件通过 `FeignClientRequestInterceptor` 自动传递以下请求头：

- 语言信息（`Accept-Language`）
- 时区信息（`X-Rd-Request-Timezone`）
- 租户代码（`X-Tenant-Code-Token`）
- 店铺代码（`X-Rd-Request-Shop-Code`）
- 时间格式（`X-Time-Format-Pattern`）
- 货币格式（`X-Currency-Format-Pattern`）

#### 自定义配置

```java
@FeignClient(
    name = "user-service",
    url = "http://localhost:8080",
    configuration = CustomFeignConfiguration.class
)
public interface UserServiceClient {
    // ...
}

@Configuration
public class CustomFeignConfiguration {
    @Bean
    public RequestInterceptor customInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("X-Custom-Header", "value");
        };
    }
}
```

### 2) `RestClient` 配置

组件自动配置 RestClient，并提供请求拦截器：

```java
@Service
public class OrderService {
    
    @Autowired
    private RestClient restClient;
    
    public Order createOrder(OrderRequest request) {
        return restClient.post()
            .uri("http://order-service/orders")
            .body(request)
            .retrieve()
            .body(Order.class);
    }
}
```

**请求头自动传递**：`RestClientRequestInterceptor` 会自动传递必要的请求头信息。

### 3) 连接池管理

组件自动管理 OkHttp 连接池：

```yaml
spring:
  cloud:
    openfeign:
      httpclient:
        max-connections: 200              # 最大连接数
        max-connections-per-route: 50    # 每个路由最大连接数
        time-to-live: 5                   # 连接保持时间
        time-to-live-unit: minutes        # 时间单位
```

### 4) `SSL`/`TLS` 配置

```yaml
spring:
  cloud:
    openfeign:
      httpclient:
        ok-http:
          # 跳过所有证书校验（仅测试用）
          insecure-trust-all: false
          # 主机名校验
          hostname-verification: true
```

---

## ⚙️ 配置说明

### 基础配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `spring.cloud.openfeign.okhttp.enabled` | boolean | `true` | 是否启用 OkHttp 客户端 |

### 连接池配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `spring.cloud.openfeign.httpclient.max-connections` | int | `200` | 最大连接数 |
| `spring.cloud.openfeign.httpclient.max-connections-per-route` | int | `50` | 每个路由最大连接数 |
| `spring.cloud.openfeign.httpclient.time-to-live` | long | `5` | 连接保持时间 |
| `spring.cloud.openfeign.httpclient.time-to-live-unit` | String | `minutes` | 时间单位 |

### `OkHttp` 配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `spring.cloud.openfeign.httpclient.ok-http.protocols` | List<String> | `[HTTP_2]` | HTTP 协议版本 |
| `spring.cloud.openfeign.httpclient.ok-http.read-timeout` | String | `20s` | 读取超时 |
| `spring.cloud.openfeign.httpclient.ok-http.connect-timeout` | String | `8s` | 连接超时 |
| `spring.cloud.openfeign.httpclient.ok-http.write-timeout` | String | `15s` | 写入超时 |
| `spring.cloud.openfeign.httpclient.ok-http.call-timeout` | String | `50s` | 调用超时 |
| `spring.cloud.openfeign.httpclient.ok-http.level` | String | `BASIC` | 日志级别：`NONE`、`BASIC`、`HEADERS`、`BODY` |
| `spring.cloud.openfeign.httpclient.ok-http.enable-cache` | boolean | `false` | 是否启用缓存 |
| `spring.cloud.openfeign.httpclient.ok-http.cache-path` | String | `/tmp/okhttp3/cache/` | 缓存路径 |
| `spring.cloud.openfeign.httpclient.ok-http.cache-size` | int | `100` | 缓存大小（MB） |
| `spring.cloud.openfeign.httpclient.ok-http.insecure-trust-all` | boolean | `false` | 是否跳过所有证书校验 |
| `spring.cloud.openfeign.httpclient.ok-http.hostname-verification` | boolean | `true` | 是否启用主机名校验 |

---

## 🎯 最佳实践

### 1) `OpenFeign` 使用

#### 定义 `Feign` 客户端

```java
@FeignClient(
    name = "user-service",
    url = "${services.user.url:http://localhost:8080}",
    fallback = UserServiceFallback.class
)
public interface UserServiceClient {
    
    @GetMapping("/users/{id}")
    ResultVO<User> getUser(@PathVariable String id);
    
    @PostMapping("/users")
    ResultVO<User> createUser(@RequestBody CreateUserRequest request);
}
```

#### 实现降级处理

```java
@Component
public class UserServiceFallback implements UserServiceClient {
    
    @Override
    public ResultVO<User> getUser(String id) {
        return ResultVO.error("服务暂时不可用");
    }
    
    @Override
    public ResultVO<User> createUser(CreateUserRequest request) {
        return ResultVO.error("服务暂时不可用");
    }
}
```

### 2) `RestClient` 使用

```java
@Service
public class OrderService {
    
    @Autowired
    private RestClient restClient;
    
    public Order getOrder(String id) {
        try {
            return restClient.get()
                .uri("http://order-service/orders/{id}", id)
                .retrieve()
                .body(Order.class);
        } catch (Exception e) {
            log.error("获取订单失败", e);
            throw new BusinessException("获取订单失败");
        }
    }
}
```

### 3) 请求头管理

组件会自动传递必要的请求头，如需传递自定义请求头：

```java
// OpenFeign
@FeignClient(name = "user-service")
public interface UserServiceClient {
    @GetMapping("/users/{id}")
    User getUser(
        @PathVariable String id,
        @RequestHeader("X-Custom-Header") String customHeader
    );
}

// RestClient
restClient.get()
    .uri("http://user-service/users/{id}", id)
    .header("X-Custom-Header", "value")
    .retrieve()
    .body(User.class);
```

### 4) 超时配置

根据服务响应时间合理配置超时：

```yaml
spring:
  cloud:
    openfeign:
      httpclient:
        ok-http:
          read-timeout: 30s      # 根据接口响应时间调整
          connect-timeout: 10s   # 根据网络环境调整
          write-timeout: 20s     # 根据请求体大小调整
          call-timeout: 60s      # 整体超时时间
```

### 5) 连接池优化

```yaml
spring:
  cloud:
    openfeign:
      httpclient:
        max-connections: 200              # 根据并发需求调整
        max-connections-per-route: 50     # 根据目标服务限制调整
        time-to-live: 5                   # 连接保持时间
```

---

## ❓ 常见问题

### `Q1` — 如何选择 `OpenFeign` 还是 `RestClient`？

**A:** 
- **OpenFeign**：适合声明式 API，代码更简洁，支持降级处理
- **RestClient**：适合程序式调用，更灵活，Spring 6.0+ 推荐使用

### `Q2` — 请求头没有传递怎么办？

**A:** 
- 确保请求头在原始请求中存在
- 检查 `IgnoreHeaderContent.IGNORE_HEADERS` 是否包含该请求头
- 查看日志确认拦截器是否正常工作

### `Q3` — 如何自定义请求拦截器？

**A:** 

```java
@Configuration
public class CustomFeignConfiguration {
    @Bean
    public RequestInterceptor customInterceptor() {
        return requestTemplate -> {
            // 自定义逻辑
        };
    }
}
```

### `Q4` — 如何配置多个 `Feign` 客户端？

**A:** 

```java
@FeignClient(name = "user-service", url = "http://user-service")
public interface UserServiceClient {
    // ...
}

@FeignClient(name = "order-service", url = "http://order-service")
public interface OrderServiceClient {
    // ...
}
```

### `Q5` — 如何启用请求日志？

**A:** 

```yaml
spring:
  cloud:
    openfeign:
      httpclient:
        ok-http:
          level: BODY  # 开发环境使用 BODY，生产环境使用 BASIC
```

### `Q6` — 连接池配置如何优化？

**A:** 
- 根据并发请求数调整 `max-connections`
- 根据目标服务限制调整 `max-connections-per-route`
- 根据网络环境调整 `time-to-live`

---

## 📎 📝 总结

Richie Microservice Component 提供了统一的微服务调用解决方案，支持 OpenFeign 和 RestClient 两种方式，自动管理连接池和请求头传递，简化了微服务间的调用。

**关键要点**：

1. **选择合适的客户端**：OpenFeign 适合声明式 API，RestClient 适合程序式调用
2. **合理配置超时**：根据服务响应时间设置合适的超时时间
3. **优化连接池**：根据并发需求调整连接池大小
4. **请求头传递**：组件自动传递必要的请求头，无需手动处理
5. **错误处理**：使用降级处理提高系统可用性

