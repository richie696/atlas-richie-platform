# Richie Component Storage - Core

## 概述

`atlas-richie-component-storage-core` 是整个存储组件体系的**核心模块**，承担着 SPI 契约定义、引擎注册、配置模型、可观测性接入等公共职责。所有存储实现模块（`storage-minio`、`storage-s3`、`storage-oss`、`storage-ftp`、`storage-sftp`、`storage-smb`、`storage-local` 等）均依赖此模块，**禁止在实现模块中重复实现本模块定义的能力**。

核心模块提供：

- **统一的存储 SPI**：`StorageEngine` 接口屏蔽各存储后端差异，业务代码面向统一 API 编程。
- **引擎扩展点**：`StorageEngineProvider` SPI 让各实现模块按需注册，按引擎类型动态发现。
- **注册中心与代理层**：`StorageEngineRegistry` 配合 JDK 动态代理，支持多引擎并存、运行时热切换。
- **双模式架构**：自动模式（YAML 配一次即用）和手动模式（运行时 `switchEngine`）共用同一套 SPI，业务注入无感知。
- **配置模型与参数校验**：`StorageProperties` 统一所有子配置的入口，`ConfigValidation` 统一参数校验风格。
- **可观测性集成**：`StorageHealthIndicator` + `StorageMetricsBinder` 接入 Spring Boot Actuator 与 Micrometer，零侵入地暴露存储引擎运行状态。

## 模块内容

```
com.richie.component.storage
├── core/        # SPI 接口 + 注册中心 + 自动配置
│   ├── StorageEngine                       # 统一存储接口
│   ├── StorageEngineProvider               # 引擎扩展点 SPI
│   ├── StorageEngineRegistry               # 引擎注册中心与代理管理
│   ├── StorageEngineAutoConfiguration      # Spring Boot 自动配置
│   ├── StorageEngineProxyFactoryBean       # @Primary 动态代理 FactoryBean
│   └── StorageEngineInvocationHandler      # JDK Proxy 调用处理器
├── bean/        # 配置模型与响应/策略对象
│   ├── ObjectConfig / FtpConfig / SftpConfig / Smb3Config / LocalConfig
│   ├── UploadResponse / DownloadResponse
│   ├── DirectUploadPolicy / DirectDownloadPolicy
│   └── image/ (ImageOptions / ImageFormat)
├── config/      # 配置属性与参数校验
│   ├── StorageProperties                   # 统一配置入口 (前缀 platform.component.storage)
│   └── ConfigValidation                    # 参数校验工具
├── enums/       # 引擎类型与 ACL 类型枚举
│   └── StorageEngineEnum
├── exception/   # 存储异常体系
│   ├── StorageException
│   └── StorageTypeUnsupportedException
├── observability/  # 可观测性
│   ├── StorageHealthIndicator              # Spring Boot HealthIndicator
│   ├── StorageMetricsBinder                # Micrometer 指标绑定
│   └── StorageEngineMetrics                # 内部计数器
├── support/     # 通用支持组件
│   └── ObjectStorageStartupProbe           # 对象存储启动探测
├── converter/   # 类型转换器
│   ├── AclTypeConverter
│   └── StorageTypeConverter
└── util/        # 工具类
    └── ObjectStorageKeys                   # 对象存储 Key 管理
```

| 包路径              | 作用                      | 关键类                                                                                              |
|------------------|-------------------------|--------------------------------------------------------------------------------------------------|
| `core/`          | SPI 入口与运行时基础设施          | `StorageEngine`、`StorageEngineProvider`、`StorageEngineRegistry`、`StorageEngineAutoConfiguration` |
| `bean/`          | 数据模型与传输对象               | `ObjectConfig`、`UploadResponse`、`DirectUploadPolicy`、`ImageOptions`                              |
| `config/`        | 统一配置入口与校验工具             | `StorageProperties`、`ConfigValidation`                                                           |
| `enums/`         | 类型枚举                    | `StorageEngineEnum`                                                                              |
| `exception/`     | 异常体系                    | `StorageException`、`StorageTypeUnsupportedException`                                             |
| `observability/` | Spring Boot Actuator 集成 | `StorageHealthIndicator`、`StorageMetricsBinder`                                                  |
| `support/`       | 跨引擎公共能力                 | `ObjectStorageStartupProbe`                                                                      |
| `converter/`     | YAML 字符串与枚举互转           | `AclTypeConverter`、`StorageTypeConverter`                                                        |
| `util/`          | 工具类                     | `ObjectStorageKeys`                                                                              |

## 架构设计

### 分层架构

整个存储组件采用清晰的三层架构，core 模块承担后两层的实现责任：

```
┌──────────────────────────────────────────────────────────────┐
│                    第一层：业务代码层                          │
│   @Autowired StorageEngine / @Qualifier("xxxStorageEngine") │
└────────────────────────┬─────────────────────────────────────┘
                         │ 调用统一接口
┌────────────────────────▼─────────────────────────────────────┐
│                    第二层：注册层（Registry）                  │
│  StorageEngineRegistry  +  ProxyHolder  +  SpringContext    │
│   - registerInitialEngine / switchEngine / refreshEngine   │
│   - JDK 动态代理 (volatile delegate 引用)                    │
└────────────────────────┬─────────────────────────────────────┘
                         │ 通过 SPI 查找
┌────────────────────────▼─────────────────────────────────────┐
│                    第三层：SPI 扩展层                          │
│  StorageEngineProvider  →  各实现模块的 Provider Bean        │
│  create() / validate() / afterPropertiesSet() / destroy()    │
└──────────────────────────────────────────────────────────────┘
```

- **SPI 层**：`StorageEngine` 暴露业务 API；`StorageEngineProvider` 是引擎工厂的扩展点，定义创建、校验、初始化、销毁的标准生命周期。
- **注册层**：`StorageEngineRegistry` 通过 `SpringContextHolder` 动态查找所有 Provider Bean；为每种引擎类型预创建 JDK 动态代理，支持热替换 delegate。
- **自动配置层**：`StorageEngineAutoConfiguration` 在 Spring Boot 启动时按 `auto-init` 属性选择模式，注册相应的 Bean 并绑定初始引擎。

### 双模式架构

通过 `platform.component.storage.auto-init` 切换，两种模式**互斥**，不可混用：

| 模式           | 触发条件                    | 引擎创建方式                                                                     | 典型场景                   |
|--------------|-------------------------|----------------------------------------------------------------------------|------------------------|
| **自动模式**（默认） | `auto-init: true`（或不配置） | Spring Boot 启动时根据 YAML 自动创建引擎 Bean，通过 `autoBindEngineToProxy` 绑定到 Registry | 配置固定写在 YAML，单后端单租户     |
| **手动模式**     | `auto-init: false`      | 业务代码在运行时调用 `registry.switchEngine()` 显式创建                                  | 配置存数据库、租户隔离、运维热切换、灰度迁移 |

#### 自动模式（Auto-Init）

- 启动流程：Spring Boot 读取 `StorageProperties` → 各实现模块的 `AutoConfiguration` 创建对应 `StorageEngine` Bean → 注入到 `Registry` 并绑定到 Proxy。
- 业务代码 `@Autowired StorageEngine` 拿到的是 `StorageEngineProxyFactoryBean` 代理对象；调用会被转发到当前 delegate。
- `@Qualifier("objectStorageEngine")` 等限定符对应 `autoBindEngineToProxy` 中按优先级（object > ftp > sftp > smb > local）注册的 Bean。

#### 手动模式（Manual Registry）

- 启动流程：各实现模块**不**通过 `AutoConfiguration` 创建引擎实例；`StorageEngineRegistry` 预创建 12 个空 Proxy 占位。
- 业务代码（如启动 `@PostConstruct`、租户登录、管理员操作）调用 `registry.switchEngine(StorageEngineEnum.MINIO, properties)`，Registry 通过 SPI 查找 Provider，调用 `create()` → `afterPropertiesSet()` 完成初始化。
- 同一时刻可注册多种引擎类型（对象存储 + FTP + SFTP），互不影响。

#### 代理模式

`StorageEngineRegistry` 为每种引擎类型维护一个 `ProxyHolder`：

```java
static class ProxyHolder {
    final StorageEngineEnum engineType;
    final StorageEngine proxy;          // JDK 动态代理，构造时创建
    volatile StorageEngine delegate;    // 当前真实引擎，可运行时替换
}
```

- 业务代码注入的是 `proxy`（在构造时通过 `Proxy.newProxyInstance` 生成）。
- 每次方法调用时，代理从 `delegate` 字段懒解析真实引擎并转发。
- 切换引擎时只修改 `delegate` 引用，**不需要重新创建代理对象**，业务代码持有的引用始终有效。

## StorageEngine — 统一存储 API

`StorageEngine` 是业务代码面向的唯一接口。所有实现类（MinIO、S3、OSS、FTP、SFTP、SMB、Local 等）均实现此接口。

```java
public interface StorageEngine {
    UploadResponse putData(String key, Map<?, ?> collection);
    UploadResponse putData(String key, Collection<?> collection);
    UploadResponse putData(String key, Object object);
    UploadResponse putObject(String key, File file);
    UploadResponse putObject(String key, InputStream inputStream);
    UploadResponse putImage(String key, File file, ImageOptions options);
    UploadResponse putImage(String key, InputStream inputStream, ImageOptions options);
    <T> DownloadResponse<T> getData(String key, TypeReference<T> typeReference);
    DownloadResponse<byte[]> getObject(String key, File targetPath, boolean returnData);
    DownloadResponse<byte[]> getResumableObject(String key, String targetPath, boolean returnData);
    boolean existsObject(String key);
    default DirectUploadPolicy issueDirectUploadPolicy(String key, int expireSeconds);
    default DirectDownloadPolicy issueDirectDownloadPolicy(String key, int expireSeconds);
}
```

### 方法一览

| 方法                                                                      | 说明                                   | 返回值                        |
|-------------------------------------------------------------------------|--------------------------------------|----------------------------|
| `putData(String key, Map<?, ?> collection)`                             | 上传 JSON 数据（Map 形式，自动序列化）             | `UploadResponse`           |
| `putData(String key, Collection<?> collection)`                         | 上传 JSON 数据（集合形式）                     | `UploadResponse`           |
| `putData(String key, Object object)`                                    | 上传 JSON 数据（任意 POJO）                  | `UploadResponse`           |
| `putObject(String key, File file)`                                      | 上传文件（File 来源）                        | `UploadResponse`           |
| `putObject(String key, InputStream inputStream)`                        | 上传文件（流式来源）                           | `UploadResponse`           |
| `putImage(String key, File file, ImageOptions options)`                 | 上传图片并按 `ImageOptions` 处理（压缩、格式转换等）   | `UploadResponse`           |
| `putImage(String key, InputStream inputStream, ImageOptions options)`   | 上传图片并处理（流式）                          | `UploadResponse`           |
| `getData(String key, TypeReference<T> typeReference)`                   | 下载 JSON 数据并反序列化为指定类型                 | `DownloadResponse<T>`      |
| `getObject(String key, File targetPath, boolean returnData)`            | 下载文件到本地路径                            | `DownloadResponse<byte[]>` |
| `getResumableObject(String key, String targetPath, boolean returnData)` | 下载文件，支持断点续传                          | `DownloadResponse<byte[]>` |
| `existsObject(String key)`                                              | 判断对象是否存在                             | `boolean`                  |
| `issueDirectUploadPolicy(String key, int expireSeconds)`                | 生成客户端直传策略（预签名 URL）；默认实现返回 fallback   | `DirectUploadPolicy`       |
| `issueDirectDownloadPolicy(String key, int expireSeconds)`              | 生成客户端直读策略（预签名下载 URL）；默认实现返回 fallback | `DirectDownloadPolicy`     |

> 直传/直读策略的默认实现由 `StorageEngine` 接口提供，返回 `success=false` 的 `fallback` 策略，提示"请使用服务端上传/下载"。具备原生签名能力的引擎（OSS、S3、COS 等）可重写此方法返回预签名 URL。

## StorageEngineProvider — 扩展 SPI

`StorageEngineProvider` 是各实现模块**唯一需要实现**的 SPI 接口。Registry 在 `switchEngine` / `refreshEngine` 时通过 `SpringContextHolder` 动态发现所有 `StorageEngineProvider` Bean，按 `supportedEngineType()` 过滤后调用对应 Provider。

```java
public interface StorageEngineProvider {
    StorageEngineEnum supportedEngineType();
    StorageEngine create(StorageProperties properties);
    default void afterPropertiesSet(StorageEngine engine);
    default void destroy(StorageEngine engine);
    default void validate(StorageProperties properties);
    default boolean supports(Class<? extends StorageEngine> engineClass);
}
```

### 契约方法

| 方法                                                     | 是否必实现 | 说明                                                                                   |
|--------------------------------------------------------|-------|--------------------------------------------------------------------------------------|
| `supportedEngineType()`                                | **是** | 返回当前 Provider 支持的引擎类型（如 `StorageEngineEnum.MINIO`），Registry 用此字段做类型匹配                |
| `create(StorageProperties properties)`                 | **是** | 根据 `StorageProperties` 创建引擎实例。Provider 自行决定从哪个子配置（`ObjectConfig` / `FtpConfig` 等）取参数 |
| `afterPropertiesSet(StorageEngine engine)`             | 否     | 创建完成后的初始化回调。**手动模式下**此方法替代 Spring 的 `@PostConstruct`，用于执行桶探测、健康检查等动作                 |
| `destroy(StorageEngine engine)`                        | 否     | 引擎销毁时清理 Provider 自行创建的资源（连接池、SDK 客户端等）。Registry 在 `switchEngine` 替换 delegate 之前调用    |
| `validate(StorageProperties properties)`               | 否     | 配置参数校验。校验失败抛 `IllegalArgumentException`，**旧引擎保持不变**。推荐使用 `ConfigValidation` 工具类      |
| `supports(Class<? extends StorageEngine> engineClass)` | 否     | 反查：给定引擎实例类型，判断当前 Provider 是否能创建。默认返回 `true`；各 Provider 应重写为精确匹配以避免歧义                 |

### 实现示例（来自 MinIO/SFTP/Local 等模块）

```java
@Component
public class MinioStorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.MINIO;
    }

    @Override
    public StorageEngine create(StorageProperties properties) {
        ObjectConfig cfg = properties.getObject();
        return new MinioStorageEngine(cfg);
    }

    @Override
    public void afterPropertiesSet(StorageEngine engine) {
        // 手动模式下执行桶探测等初始化
        ((MinioStorageEngine) engine).probeBucket();
    }

    @Override
    public void destroy(StorageEngine engine) {
        // 清理 MinIO 客户端
        ((MinioStorageEngine) engine).shutdown();
    }

    @Override
    public void validate(StorageProperties properties) {
        ObjectConfig c = properties.getObject();
        ConfigValidation.requireNonNull(c, "对象存储配置 (object)");
        ConfigValidation.requireNonBlank(c.getEndpoint(), "endpoint");
        ConfigValidation.requireNonBlank(c.getAccessKeyId(), "accessKeyId");
        ConfigValidation.requireNonBlank(c.getAccessKeySecret(), "accessKeySecret");
        ConfigValidation.requireNonBlank(c.getBucketName(), "bucketName");
    }

    @Override
    public boolean supports(Class<? extends StorageEngine> engineClass) {
        return MinioStorageEngine.class.equals(engineClass);
    }
}
```

## StorageEngineRegistry — 引擎注册中心

`StorageEngineRegistry` 是手动模式的核心，所有 `switchEngine` / `refreshEngine` 调用都通过它完成。

### ProxyHolder 内部结构

```java
static class ProxyHolder {
    final StorageEngineEnum engineType;
    final StorageEngine proxy;          // JDK 动态代理（构造时一次创建）
    volatile StorageEngine delegate;    // 真实引擎，热切换只改此引用
}
```

- 构造 `StorageEngineRegistry` 时**预先**为 `StorageEngineEnum.values()` 中每种类型创建一个 `ProxyHolder`（含代理对象，delegate 为 `null`），并独立创建一个 `objectProxy` 供所有 8 种对象存储共享。
- 业务代码在任何时刻调用 `getProxy(type)` 拿到的都是有效代理，**未注册引擎时调用会抛明确异常**。
- 切换引擎只修改 `volatile delegate` 字段，多线程环境下的可见性由 `volatile` 语义保证；写操作由 `synchronized` 串行化。

### 核心方法

| 方法                                               | 作用                                  | 线程安全           |
|--------------------------------------------------|-------------------------------------|----------------|
| `registerInitialEngine(type, id, engine)`        | 自动模式启动时调用，将 Spring 管理的引擎实例绑定到 Proxy | `synchronized` |
| `switchEngine(type, properties)`                 | 手动模式注册/切换引擎，简写形式                    | `synchronized` |
| `switchEngine(type, properties, actor, reason)`  | 手动模式注册/切换引擎，带审计上下文                  | `synchronized` |
| `refreshEngine(type, properties)`                | 刷新已初始化的引擎（强制替换）                     | `synchronized` |
| `refreshEngine(type, properties, actor, reason)` | 刷新引擎，带审计上下文，失败时回滚                   | `synchronized` |
| `getProxy(type)`                                 | 获取指定类型的代理对象（懒解析 delegate）           | 非阻塞            |
| `getObjectProxy()`                               | 获取对象存储统一代理（8 种对象存储共享）               | 非阻塞            |
| `getDefaultProxy()`                              | 获取默认引擎代理（首个注册的引擎）                   | 非阻塞            |
| `getEngine(type)`                                | 直接获取当前 delegate（非代理，谨慎使用）           | 非阻塞            |
| `getDefaultEngine()`                             | 获取默认引擎实例                            | 非阻塞            |
| `isInitialized()`                                | 是否已注册任何引擎                           | 非阻塞            |
| `getCurrentEngineType()`                         | 当前默认引擎类型描述                          | 非阻塞            |
| `getRegisteredTypes()`                           | 已注册的引擎类型集合                          | 非阻塞            |
| `snapshot()`                                     | 已注册引擎的快照 Map                        | 非阻塞            |

### 切换引擎的标准流程（switchEngine）

```
1. 通过 SpringContextHolder.getBeansOfType(StorageEngineProvider.class)
   动态查找所有 Provider Bean
2. 按 supportedEngineType() 过滤出目标类型的 Provider
3. provider.validate(properties)        // 校验配置，失败抛异常
4. provider.create(properties)          // 创建新引擎
5. provider.afterPropertiesSet(engine)  // 初始化（手动模式替代 @PostConstruct）
6. provider.destroy(oldEngine)          // 销毁旧引擎，释放连接池
7. holder.delegate = newEngine          // volatile 写入，新引用生效
8. 若为对象存储，同步更新 objectProxy.delegate
9. 若为首个引擎或替换的是默认引擎，更新 defaultEngineType
10. metrics.incrementSwitch(engineType) // 计数
```

### 刷新引擎的回滚语义（refreshEngine）

`refreshEngine` 与 `switchEngine` 的关键差异在于**先建后拆的回滚策略**：

- **当前无引擎** → 抛 `IllegalStateException`（区别于 `switchEngine` 的允许首次创建）
- `validate()` 失败 → 抛异常，旧 delegate 不变
- `create()` + `afterPropertiesSet()` 失败 → 自动 `destroy()` 新引擎后抛异常，旧 delegate 不变
- 旧引擎 `destroy()` 失败 → 仅记 `warn`，新引擎仍生效，避免外部资源被拆但旧引用残留

## StorageEngineAutoConfiguration

Spring Boot 自动配置类，承担以下职责：

### 1. 注册 Registry + Proxy FactoryBean

| Bean 名                   | 作用                                | 备注                                                          |
|--------------------------|-----------------------------------|-------------------------------------------------------------|
| `storageEngineRegistry`  | 引擎注册中心                            | 始终创建，与 `auto-init` 无关                                       |
| `storageEngineProxy`     | `@Primary` 的 JDK 动态代理 FactoryBean | 自动模式专用，委托到当前默认引擎                                            |
| `storageHealthIndicator` | Spring Boot HealthIndicator       | `@ConditionalOnEnabledHealthIndicator("storage")`           |
| `storageMetricsBinder`   | Micrometer 指标绑定器                  | `@ConditionalOnProperty(management.metrics.enable.storage)` |

### 2. 手动模式：注册 qualifier-named Bean

当 `auto-init: false` 时，注册以下 5 个限定符 Bean，使 `@Qualifier` 注入在两种模式下行为一致：

| Bean 名                             | 来源                           | 对应引擎类型   |
|------------------------------------|------------------------------|----------|
| `objectStorageEngine`              | `registry.getObjectProxy()`  | 所有对象存储共享 |
| `ftpStorageEngine`                 | `registry.getProxy(FTP)`     | FTP      |
| `sftpStorageEngine`                | `registry.getProxy(SFTP)`    | SFTP     |
| `smbStorageEngine`                 | `registry.getProxy(SMB)`     | SMB      |
| `localStorageEngine`               | `registry.getProxy(LOCAL)`   | 本地存储     |
| `defaultStorageEngine`（`@Primary`） | `registry.getDefaultProxy()` | 默认引擎     |

### 3. 自动模式：autoBindEngineToProxy ApplicationRunner

当 `auto-init: true`（或未配置）时，注册一个 `ApplicationRunner` 完成启动绑定：

- 按 **object > ftp > sftp > smb > local** 优先级选择默认引擎
- 调用 `registry.registerInitialEngine(defaultType, defaultId, engine)` 绑定默认引擎
- 调用 `proxyFactory.setDelegate(defaultEngine)` 绑定 `@Primary` 代理
- 调用 `registry.registerInitialEngine(...)` 注册其他非默认引擎
- 对象存储引擎通过 `Provider.supports()` 反查类型，避免脆弱的类名匹配

## ConfigValidation — 参数校验工具

`ConfigValidation` 是各实现模块 `validate(StorageProperties)` 的样板代码统一工具，避免在每个 Provider 中重复 `if (x == null) throw ...` 模式。

```java
public final class ConfigValidation {
    public static void requireNonNull(Object value, String name);
    public static void requireNonBlank(String value, String name);
}
```

| 方法                             | 校验规则                                                      | 失败时异常                                      |
|--------------------------------|-----------------------------------------------------------|--------------------------------------------|
| `requireNonNull(value, name)`  | `value != null`                                           | `IllegalArgumentException(name + " 不能为空")` |
| `requireNonBlank(value, name)` | `value != null && !value.isBlank()`（null / "" / 全部空白均视为空） | `IllegalArgumentException(name + " 不能为空")` |

### 使用示例

```java
@Override
public void validate(StorageProperties properties) {
    ObjectConfig c = properties.getObject();
    ConfigValidation.requireNonNull(c, "对象存储配置 (object)");
    ConfigValidation.requireNonBlank(c.getEndpoint(), "endpoint");
    ConfigValidation.requireNonBlank(c.getAccessKeyId(), "accessKeyId");
    ConfigValidation.requireNonBlank(c.getAccessKeySecret(), "accessKeySecret");
    ConfigValidation.requireNonBlank(c.getBucketName(), "bucketName");
}
```

> 错误信息保持原格式（含字段名），以兼容现有测试断言。

## 配置模型

### StorageProperties

`StorageProperties` 是**统一配置入口**，通过 `@ConfigurationProperties(prefix = "platform.component.storage")` 绑定 YAML。

```java
@ConfigurationProperties(prefix = "platform.component.storage")
public class StorageProperties {
    private boolean autoInit = true;            // 是否启用自动模式
    private ObjectConfig object = new ObjectConfig();
    private FtpConfig ftp = new FtpConfig();
    private SftpConfig sftp = new SftpConfig();
    private Smb3Config smb3 = new Smb3Config();
    private LocalConfig local = new LocalConfig();
    // builder() / 全参构造
}
```

### 各子配置

| 类                              | 作用               | 关键字段                                                                                               |
|--------------------------------|------------------|----------------------------------------------------------------------------------------------------|
| `ObjectConfig`                 | 8 种对象存储共享配置      | `engine`、`endpoint`、`region`、`accessKeyId`、`accessKeySecret`、`bucketName`、`basePath`、`storageType` |
| `FtpConfig`                    | FTP 协议配置         | `enable`、`host`、`port`、`username`、`password`、`basePath`                                            |
| `SftpConfig`                   | SFTP 协议配置        | `enable`、`host`、`port`、`username`、`password`、`sshLogin`、`identityFile`、`basePath`                  |
| `Smb3Config`                   | SMB3/CIFS 配置     | `enable`、`host`、`domain`、`username`、`password`、`basePath`                                          |
| `LocalConfig`                  | 本地存储配置           | `enable`、`path`、`cache`、`schema`、`cleanup`                                                         |
| `DirectUploadPolicy`           | 客户端直传策略（预签名 URL） | `method`、`uploadUrl`、`headers`、`formFields`、`bucketName`、`key`、`expireAt`、`fallback`               |
| `DirectDownloadPolicy`         | 客户端直读策略          | `downloadUrl`、`bucketName`、`key`、`expireAt`、`fallback`                                             |
| `UploadResponse`               | 上传响应             | `success`、`url`、`key`、`hashValue`、`errorMessage`                                                   |
| `DownloadResponse<T>`          | 下载响应             | `success`、`data`（字节数组或反序列化对象）、`targetPath`、`errorMessage`                                          |
| `ImageOptions` / `ImageFormat` | 图片处理选项与支持格式枚举    | `format`、`quality`、`width`、`height`                                                                |

### YAML 配置示例

```yaml
platform:
  component:
    storage:
      auto-init: true              # 切换自动/手动模式
      object:                      # 对象存储配置
        engine: minio              # MINIO | AWS_S3 | ALIYUN_OSS | TENCENT_COS | HUAWEI_OBS | KSYUN_KS3 | VOLCENGINE_TOS | AZURE_BLOB
        endpoint: http://localhost:9000
        region: us-east-1
        accessKeyId: your-key
        accessKeySecret: your-secret
        bucketName: my-bucket
        basePath: /files/
      ftp:                         # FTP 协议配置
        enable: true
        host: ftp.example.com
        port: 21
        username: user
        password: pass
        basePath: /storage/
      sftp:                        # SFTP 协议配置
        enable: true
        host: sftp.example.com
        port: 22
        username: user
        password: pass
        basePath: /storage/
      smb3:                        # SMB3 配置
        enable: true
        domain: example.com
        username: user
        password: pass
        basePath: /storage/
      local:                       # 本地存储配置
        enable: true
        path: ./storage/
```

## StorageEngineEnum

`StorageEngineEnum` 是引擎类型清单，包含 **8 种对象存储** + **4 种文件协议**共 **12 个枚举值**。

### 枚举值

| 枚举值              | `configValue`    | 说明            | 是否对象存储 |
|------------------|------------------|---------------|:------:|
| `MINIO`          | `minio`          | MinIO         |   是    |
| `ALIYUN_OSS`     | `aliyun_oss`     | 阿里云 OSS       |   是    |
| `TENCENT_COS`    | `tencent_cos`    | 腾讯云 COS       |   是    |
| `HUAWEI_OBS`     | `huawei_obs`     | 华为云 OBS       |   是    |
| `AWS_S3`         | `aws_s3`         | AWS S3        |   是    |
| `KSYUN_KS3`      | `ksyun_ks3`      | 金山云 KS3       |   是    |
| `VOLCENGINE_TOS` | `volcengine_tos` | 火山引擎 TOS      |   是    |
| `AZURE_BLOB`     | `azure_blob`     | 微软 Azure Blob |   是    |
| `FTP`            | `ftp`            | FTP 协议        |   否    |
| `SFTP`           | `sftp`           | SFTP 协议       |   否    |
| `SMB`            | `smb`            | SMB 协议        |   否    |
| `LOCAL`          | `local`          | 本地文件系统        |   否    |

### 字段说明

- `description`：中文描述（用于日志与错误提示）
- `configValue`：YAML 中的字符串值，与 `@ConditionalOnProperty` 的 `havingValue` 对应
- `objectStorage`：是否为对象存储（区别于 FTP/SFTP/SMB/LOCAL），用于对象存储统一代理 `objectProxy` 的同步更新

### 静态方法

| 方法                                    | 说明                                                     |
|---------------------------------------|--------------------------------------------------------|
| `isObjectStorage()`                   | 判断当前枚举值是否为对象存储                                         |
| `fromConfigValue(String configValue)` | 根据配置字符串反查枚举（不区分大小写敏感），返回 `Optional<StorageEngineEnum>` |
| `validConfigValues()`                 | 返回所有有效配置值的逗号分隔字符串，用于错误提示                               |

```java
StorageEngineEnum type = StorageEngineEnum.fromConfigValue("aws_s3").orElseThrow();
// 通过 @ConditionalOnProperty 的 havingValue = "aws_s3" 自动匹配
```

## 可观测性

core 模块提供两个可选的观测 Bean，统一接入 Spring Boot Actuator 与 Micrometer：

### StorageHealthIndicator

`StorageHealthIndicator` 实现 Spring Boot `HealthIndicator` 接口，暴露存储引擎运行状态：

- 暴露当前已注册引擎数量、默认引擎类型、默认引擎 ID 等元信息
- 通过 `/actuator/health` 端点查看
- 开关：`management.health.storage.enabled=false`（默认 `true`）
- 仅在 Spring Boot Actuator 启动器存在时通过 `@ConditionalOnClass` 注册

### StorageMetricsBinder

`StorageMetricsBinder` 实现 Micrometer `MeterBinder`，注册以下指标：

| 指标                              | 类型      | 说明          |
|---------------------------------|---------|-------------|
| `storage.engine.type`           | Gauge   | 当前默认引擎类型枚举值 |
| `storage.engine.count`          | Gauge   | 已注册引擎数量     |
| `storage.engine.switch` (×12)   | Counter | 每种引擎类型的切换次数 |
| `storage.engine.register` (×12) | Counter | 每种引擎类型的注册次数 |

- 开关：`management.metrics.enable.storage=NONE`（默认 `ALL`）
- 仅在 Micrometer `MeterBinder` 可用时通过 `@ConditionalOnClass` 注册

### StorageEngineMetrics

`StorageEngineMetrics` 是 Registry 内部的计数器，**仅供** `StorageMetricsBinder` 绑定到 Micrometer 时使用。Registry 在 `registerInitialEngine` / `switchEngine` / `refreshEngine` 时调用 `incrementRegister` / `incrementSwitch` 更新计数。

### 关闭示例（不接监控）

```yaml
management:
  health:
    storage:
      enabled: false
  metrics:
    enable:
      storage: NONE
```

完全关闭后，`/actuator/health` 不再暴露存储引擎状态，Prometheus exporter 也不再尝试收集存储相关 metric，零噪音。

## 扩展指南：新增一个存储引擎

按以下 6 步即可新增一个存储引擎实现（如 `storage-ceph`，假设它兼容 S3 协议但需要独立 Provider）。

### 第 1 步：创建新模块

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-core</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 第 2 步：实现 StorageEngine

```java
public class CephStorageEngine implements StorageEngine {
    // 实现 putObject / getObject / existsObject 等所有方法
    // 直传/直读策略按需重写 issueDirectUploadPolicy / issueDirectDownloadPolicy
}
```

### 第 3 步：实现 StorageEngineProvider

```java
@Component
public class CephStorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.CEPH;  // 新增枚举值
    }

    @Override
    public StorageEngine create(StorageProperties properties) {
        ObjectConfig cfg = properties.getObject();
        return new CephStorageEngine(cfg);
    }

    @Override
    public void afterPropertiesSet(StorageEngine engine) {
        ((CephStorageEngine) engine).probeBucket();
    }

    @Override
    public void destroy(StorageEngine engine) {
        ((CephStorageEngine) engine).shutdown();
    }

    @Override
    public void validate(StorageProperties properties) {
        ObjectConfig c = properties.getObject();
        ConfigValidation.requireNonNull(c, "对象存储配置 (object)");
        ConfigValidation.requireNonBlank(c.getEndpoint(), "endpoint");
        ConfigValidation.requireNonBlank(c.getBucketName(), "bucketName");
    }

    @Override
    public boolean supports(Class<? extends StorageEngine> engineClass) {
        return CephStorageEngine.class.equals(engineClass);
    }
}
```

### 第 4 步：创建 AutoConfiguration

```java
@Configuration
@AutoConfiguration
public class CephAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "ceph")
    public StorageEngine objectStorageEngine(StorageProperties properties) {
        return new CephStorageEngineProvider().create(properties);
    }
}
```

注册 `AutoConfiguration` 到 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```
com.richie.component.storage.ceph.CephAutoConfiguration
```

### 第 5 步：在 core 模块的 AutoConfiguration 中添加条件分支

通常**不需要**修改 `StorageEngineAutoConfiguration`，因为 `autoBindEngineToProxy` 通过 `Provider.supports()` 反查类型。只需确保 `objectStorageEngine` Bean 已存在即可。

### 第 6 步：在 StorageEngineEnum 中注册新枚举值

```java
public enum StorageEngineEnum {
    // ... 现有枚举值
    CEPH("Ceph", "ceph", true),  // 新增
    // ... 其他枚举值
}
```

> 注意：`StorageEngineEnum` 修改会影响所有依赖 core 模块的组件。新增枚举值**仅追加**，不要修改现有顺序或值，否则会导致 `configValue` 不兼容。

## 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-core</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 关键传递依赖

| 依赖                                                            | 作用                                                                 |
|---------------------------------------------------------------|--------------------------------------------------------------------|
| `com.richie.base:atlas-richie-context`                        | `SpringContextHolder`（Registry 通过它动态查找 Provider）                   |
| `org.springframework.boot:spring-boot-autoconfigure`          | `@AutoConfiguration` 与条件注解                                         |
| `org.springframework.boot:spring-boot-actuator-autoconfigure` | HealthIndicator 与 Micrometer 集成（可选，运行时通过 `@ConditionalOnClass` 判断） |
| `io.micrometer:micrometer-core`                               | MeterBinder 接口（可选）                                                 |
| `com.fasterxml.jackson.core:jackson-databind`                 | `TypeReference` 与 JSON 序列化                                         |
| `org.projectlombok:lombok`                                    | `@Getter` / `@Slf4j` / `@RequiredArgsConstructor`（编译期）             |

## 最佳实践

1. **业务代码只依赖 `StorageEngine` 接口**
   ```java
   // ✅ 正确
   @Autowired
   private StorageEngine storageEngine;
   
   // ❌ 错误：直接持有具体实现类
   @Autowired
   private MinioStorageEngine minioEngine;
   ```

2. **手动模式优先用 `refreshEngine` 替换已初始化引擎**
   - 区别于 `switchEngine` 允许首次创建，`refreshEngine` 在当前无引擎时会抛异常，语义更明确
   - 失败时自动回滚到旧引擎，保证运行态稳定

3. **Provider 的 `validate()` 用 `ConfigValidation` 工具类**
   - 错误信息统一为 `<field> 不能为空`
   - 测试断言可基于固定格式编写

4. **Provider 重写 `supports(Class)` 做精确匹配**
   - 默认实现 `return true` 过于宽松，多个 Provider 同时返回 true 时反查类型会得到任意一个
   - 重写为 `MyStorageEngine.class.equals(engineClass)` 避免歧义

5. **不要在 Provider 构造或 `@PostConstruct` 中执行业务初始化**
   - 自动模式下 Spring 容器负责生命周期
   - 手动模式下通过 `afterPropertiesSet(engine)` 显式调用，确保两种模式行为一致

6. **HealthIndicator / MetricsBinder 不接监控时主动关闭**
   - `management.health.storage.enabled=false`
   - `management.metrics.enable.storage=NONE`
   - 避免 `CollectorRegistry` 找不到收集器的噪音日志

## 常见问题

### Q: `registerInitialEngine` 与 `switchEngine` 的区别？

- `registerInitialEngine` 由自动模式的 `ApplicationRunner` 调用，要求对应 Proxy 的 delegate **必须为 null**（启动绑定）；二次注册会抛异常
- `switchEngine` 是手动模式的标准 API，**允许**首次创建（delegate 为 null 时正常注册），也允许替换已存在的引擎

### Q: 业务代码为什么不能直接持有 `getEngine()` 的返回值？

`getEngine()` 返回的是**当前 delegate 引用**，在 `switchEngine` 后会变成已销毁的旧引擎实例。业务代码必须通过 `@Autowired StorageEngine`（拿到的是 Proxy）或 `getProxy(type)`（拿到的也是 Proxy）注入，调用由 Proxy 转发到当前 delegate。

### Q: `objectStorageEngine` 限定符与具体对象存储类型（如 `MINIO`）的关系？

- `@Qualifier("objectStorageEngine")` 拿到的是 `objectProxy`，**所有 8 种对象存储共享**
- 切换对象存储类型时，`objectProxy.delegate` 同步更新，业务代码无需修改
- 8 种对象存储类型在 `StorageEngineEnum` 中各自有独立 Proxy，理论上可通过 `getProxy(StorageEngineEnum.MINIO)` 显式获取，但通常不推荐（业务代码不应感知具体类型）

### Q: `StorageEngineInvocationHandler` 的作用？

`StorageEngineInvocationHandler` 是 JDK 动态代理的 `InvocationHandler` 实现，每次方法调用时从 `ProxyHolder.delegate` 懒解析真实引擎并转发。如果 delegate 为 null，抛出明确异常提示当前未注册引擎。

### Q: 为什么 `Provider` 不通过构造函数注入到 `Registry`？

`Registry` 在构造时（`new StorageEngineRegistry()`）就会被创建，而此时 Spring 容器可能尚未完成所有 `StorageEngineProvider` Bean 的注册。通过 `SpringContextHolder.getApplicationContext().getBeansOfType(...)` 动态查找，可以确保**手动模式下后续注册的 Provider 也能被发现**。

## 相关文档

- [存储组件总览](../README.md) — 快速开始、双模式架构、配置说明
- [本地存储实现](../atlas-richie-component-storage-local/README.md)
- [AWS S3 实现](../atlas-richie-component-storage-s3/README.md)
- [阿里云 OSS 实现](../atlas-richie-component-storage-oss/README.md)
- [腾讯云 COS 实现](../atlas-richie-component-storage-cos/README.md)
- [华为云 OBS 实现](../atlas-richie-component-storage-obs/README.md)
- [MinIO 实现](../atlas-richie-component-storage-minio/README.md)
- [金山云 KS3 实现](../atlas-richie-component-storage-ks3/README.md)
- [火山引擎 TOS 实现](../atlas-richie-component-storage-tos/README.md)
- [Azure Blob 实现](../atlas-richie-component-storage-azure/README.md)
- [SFTP 实现](../atlas-richie-component-storage-sftp/README.md)
- [SMB 实现](../atlas-richie-component-storage-smb/README.md)
