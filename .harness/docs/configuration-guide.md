# 配置策略规范

本规范约束两部分：
1. **组件开发者**：如何用 `@ConfigurationProperties` 定义组件配置
2. **组件使用者**：如何在 `application.yml` 中配置组件

> 本规范针对 **atlas-richie-platform 组件库**本身，不含 Nacos/bootstrap 等业务项目基础设施（那些属于 `atlas-richie-template` 模板工程的范畴）。

---

## 1. 配置前缀约定

所有组件统一使用 `platform.component.<module>` 前缀：

| 组件 | 前缀 | 示例 |
|---|---|---|
| cache | `platform.cache` | `platform.cache.bloom-filter.enabled` |
| http | `platform.component.http` | `platform.component.http.provider` |
| ai | `platform.component.ai` | `platform.component.ai.routing.default-model` |
| microservice | `platform.component.microservice` | `platform.component.microservice.feign.okhttp.enabled` |
| storage | `platform.component.storage` | `platform.component.storage.provider` |
| vector | `platform.component.vector` | `platform.component.vector.provider` |
| messaging | `platform.component.messaging` | `platform.component.messaging.bindings.*` |

> 注意：cache 组件使用 `platform.cache`（早期命名），其他组件统一用 `platform.component.*`。

---

## 2. 组件开发者：如何定义 Properties

### 2.1 基础模式

```java
@Data
@ConfigurationProperties(prefix = "platform.component.http")
public class HttpCoreProperties {

    private HttpProvider provider;

    /**
     * 是否跳过 SSL/TLS 证书校验。
     * <p>默认为 {@code true}（强制证书校验）。
     * 仅开发/联调环境可设为 {@code false}，生产环境请保持默认。
     */
    private boolean strictSsl = true;
}
```

### 2.2 规则

**必须包含**：
- `@Data`（Lombok，自动生成 getter/setter/equals/hashCode/toString）
- `@ConfigurationProperties(prefix = "...")` 标注前缀
- JavaDoc 注释（每个字段必须），说明含义、默认值、注意事项
- `@author` 和 `@since`（标准 Javadoc 规范，见 `docs/code-standards.md`）

**默认值**：
- 所有字段必须有**合理的默认值**，不依赖使用者必须配置
- 不设默认值的字段必须是真正必填的（如 provider 类型）

**嵌套对象**：

```java
@Data
@ConfigurationProperties(prefix = "platform.component.ai")
public class AiModelProperties {

    /**
     * 是否在启动时按配置文件初始化模型。
     * 设为 false 则仅允许运行时通过 API 动态初始化。
     */
    private boolean configInitializationEnabled = true;

    /**
     * 模型路由与降级配置
     */
    private RoutingConfig routing = new RoutingConfig();

    @Data
    public static class RoutingConfig {
        private String defaultModel = "gpt-4o";
        private Map<String, String> sceneToModel;
    }
}
```

**嵌套枚举**：

```java
public enum HttpProvider {
    OKHTTP("okhttp"),
    HTTP_CLIENT_5("http_client_5"),
    REST_CLIENT("rest_client"),
    JDK("jdk");

    HttpProvider(String value) { this.value = value; }
    public final String value;
}
```

**自动注册**：
通过 Spring Boot 的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 或 `@AutoConfiguration` 自动注册，无需使用者手动 `@EnableConfigurationProperties`。

---

## 3. 组件使用者：如何在 application.yml 中配置

### 3.1 基础结构

```yaml
platform:
  component:
    http:
      provider: okhttp          # 切换实现
      strictSsl: true
      # ... 其他字段
    ai:
      config-initialization-enabled: true
      routing:
        default-model: gpt-4o
        scene-to-model:
          chat: gpt-4o
          embedding: text-embedding-3
```

### 3.2 配置层级

```
platform.component.<module>
├── provider           # provider 类型选择（如 http、storage、vector）
├── ...通用字段
├── <能力名>          # 如 http 的连接池、ai 的 routing
└── <provider名>      # provider 特定配置（如 storage-oss、storage-s3）
```

示例——storage 切 provider：

```yaml
platform:
  component:
    storage:
      provider: oss           # 切换到阿里云 OSS
      oss:
        endpoint: https://oss-cn-hangzhou.aliyuncs.com
        bucket: my-bucket
      s3:
        region: us-east-1      # 不启用，不填也行
```

### 3.3 敏感值处理

**禁止在 `application.yml` 中写真实密钥**，用环境变量：

```yaml
platform:
  component:
    storage:
      oss:
        access-key: ${OSS_ACCESS_KEY}
        secret-key: ${OSS_SECRET_KEY}
        endpoint: ${OSS_ENDPOINT:https://oss-cn-hangzhou.aliyuncs.com}  # 带默认值
```

> 规则：所有密钥类字段（密码、AccessKey、SecretKey、Token）必须通过 `${ENV_VAR}` 或 `${ENV_VAR:default}` 注入，不得写死真实值。

---

## 4. 多环境配置

Spring Boot 多环境 profile 支持：

```yaml
# application.yml（公共默认）
platform:
  component:
    http:
      provider: okhttp

# application-dev.yml（开发环境覆盖）
platform:
  component:
    http:
      strictSsl: false          # 联调时跳过证书校验

# application-prod.yml（生产环境）
platform:
  component:
    http:
      strictSsl: true           # 生产强制证书校验
```

---

## 5. 已有 Properties 参考

| 组件 | Properties 类 | 前缀 |
|---|---|---|
| cache | `CacheProperties` | `platform.cache` |
| cache | `AtlasRedisProperties` | `platform.component.redis` |
| cache | `RedisStreamProperties` | `platform.component.redis.stream` |
| cache | `LocalCacheProperties` | `platform.component.cache.local` |
| http | `HttpCoreProperties` | `platform.component.http` |
| http | 各 provider 的 `HttpProperties` | `platform.component.http.okhttp` 等 |
| ai | `AiModelProperties` | `platform.component.ai` |
| microservice | `FeignClientOkhttpProperties` | `platform.component.microservice.feign.okhttp` |

---

## 6. 不要做的事

- 不要在 Properties 里留硬编码的真实密钥（即使"只是测试"）
- 不要定义没有默认值的必填字段（除非组件真的无法启动）
- 不要用非标准前缀（如 `my-component.*`、`app.*`）——统一 `platform.component.*`
- 不要把 provider 特定字段放在 `core/` 的 Properties 里——provider 字段属于 provider 子包
