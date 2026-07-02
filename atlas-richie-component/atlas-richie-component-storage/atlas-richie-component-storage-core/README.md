# Richie Component Storage - Core

## Overview

`atlas-richie-component-storage-core` is the **core module** of the entire storage component system, taking on common responsibilities such as SPI contract definition, engine registration, configuration models, and observability integration. All storage implementation modules (`storage-minio`, `storage-s3`, `storage-oss`, `storage-ftp`, `storage-sftp`, `storage-smb`, `storage-local`, etc.) depend on this module. **It is forbidden to re-implement the capabilities defined in this module within implementation modules.**

The core module provides:

- **Unified storage SPI**: The `StorageEngine` interface hides differences between storage backends. Business code programs against a unified API.
- **Engine extension point**: The `StorageEngineProvider` SPI lets each implementation module register as needed and be discovered dynamically by engine type.
- **Registry and proxy layer**: `StorageEngineRegistry` works with the JDK dynamic proxy to support multiple engines coexisting and hot-switching at runtime.
- **Dual-mode architecture**: Auto mode (configure once in YAML) and manual mode (runtime `switchEngine`) share the same SPI, with no perceived difference in business injection.
- **Configuration model and parameter validation**: `StorageProperties` unifies the entry point for all sub-configurations. `ConfigValidation` unifies the parameter validation style.
- **Observability integration**: `StorageHealthIndicator` + `StorageMetricsBinder` plugs into Spring Boot Actuator and Micrometer, exposing storage engine runtime status with zero intrusion.

## Module Contents

```
com.richie.component.storage
├── core/        # SPI interfaces + registry + auto-configuration
│   ├── StorageEngine                       # Unified storage interface
│   ├── StorageEngineProvider               # Engine extension point SPI
│   ├── StorageEngineRegistry               # Engine registry and proxy management
│   ├── StorageEngineAutoConfiguration      # Spring Boot auto-configuration
│   ├── StorageEngineProxyFactoryBean       # @Primary dynamic proxy FactoryBean
│   └── StorageEngineInvocationHandler      # JDK Proxy invocation handler
├── bean/        # Configuration models and response/policy objects
│   ├── ObjectConfig / FtpConfig / SftpConfig / Smb3Config / LocalConfig
│   ├── UploadResponse / DownloadResponse
│   ├── DirectUploadPolicy / DirectDownloadPolicy
│   └── image/ (ImageOptions / ImageFormat)
├── config/      # Configuration properties and parameter validation
│   ├── StorageProperties                   # Unified configuration entry (prefix platform.component.storage)
│   └── ConfigValidation                    # Parameter validation utility
├── enums/       # Engine type and ACL type enums
│   └── StorageEngineEnum
├── exception/   # Storage exception hierarchy
│   ├── StorageException
│   └── StorageTypeUnsupportedException
├── observability/  # Observability
│   ├── StorageHealthIndicator              # Spring Boot HealthIndicator
│   ├── StorageMetricsBinder                # Micrometer metrics binder
│   └── StorageEngineMetrics                # Internal counters
├── support/     # Common support components
│   └── ObjectStorageStartupProbe           # Object storage startup probe
├── converter/   # Type converters
│   ├── AclTypeConverter
│   └── StorageTypeConverter
└── util/        # Utility classes
    └── ObjectStorageKeys                   # Object storage Key management
```

| Package path       | Responsibility                | Key classes                                                                                         |
|--------------------|-------------------------------|-----------------------------------------------------------------------------------------------------|
| `core/`            | SPI entry and runtime infrastructure | `StorageEngine`, `StorageEngineProvider`, `StorageEngineRegistry`, `StorageEngineAutoConfiguration` |
| `bean/`            | Data models and transfer objects | `ObjectConfig`, `UploadResponse`, `DirectUploadPolicy`, `ImageOptions`                              |
| `config/`          | Unified configuration entry and validation utility | `StorageProperties`, `ConfigValidation`                                                            |
| `enums/`           | Type enum                     | `StorageEngineEnum`                                                                                 |
| `exception/`       | Exception hierarchy           | `StorageException`, `StorageTypeUnsupportedException`                                              |
| `observability/`   | Spring Boot Actuator integration | `StorageHealthIndicator`, `StorageMetricsBinder`                                                  |
| `support/`         | Cross-engine common capabilities | `ObjectStorageStartupProbe`                                                                       |
| `converter/`       | YAML string and enum conversion | `AclTypeConverter`, `StorageTypeConverter`                                                         |
| `util/`            | Utility class                 | `ObjectStorageKeys`                                                                                 |

## Architecture Design

### Layered Architecture

The entire storage component adopts a clear three-layer architecture. The core module is responsible for implementing the latter two layers:

```
┌──────────────────────────────────────────────────────────────┐
│                    Layer 1: Business Code Layer                │
│   @Autowired StorageEngine / @Qualifier("xxxStorageEngine") │
└────────────────────────┬─────────────────────────────────────┘
                          │ Calls the unified interface
┌────────────────────────▼─────────────────────────────────────┐
│                    Layer 2: Registry Layer                    │
│  StorageEngineRegistry  +  ProxyHolder  +  SpringContext    │
│   - registerInitialEngine / switchEngine / refreshEngine   │
│   - JDK Dynamic Proxy (volatile delegate reference)          │
└────────────────────────┬─────────────────────────────────────┘
                          │ Looks up through SPI
┌────────────────────────▼─────────────────────────────────────┐
│                    Layer 3: SPI Extension Layer               │
│  StorageEngineProvider  →  Provider Bean from each impl module│
│  create() / validate() / afterPropertiesSet() / destroy()    │
└──────────────────────────────────────────────────────────────┘
```

- **SPI Layer**: `StorageEngine` exposes the business API. `StorageEngineProvider` is the engine factory's extension point, defining the standard lifecycle of create, validate, initialize, and destroy.
- **Registry Layer**: `StorageEngineRegistry` dynamically looks up all Provider Beans through `SpringContextHolder`. It pre-creates a JDK dynamic proxy for each engine type and supports hot-swapping the delegate.
- **Auto-Configuration Layer**: `StorageEngineAutoConfiguration` selects a mode based on the `auto-init` property at Spring Boot startup, registers the corresponding Beans, and binds the initial engine.

### Dual-Mode Architecture

Switch via `platform.component.storage.auto-init`. The two modes are **mutually exclusive** and cannot be mixed:

| Mode                   | Trigger condition               | Engine creation method                                                                              | Typical scenario                                  |
|------------------------|---------------------------------|-----------------------------------------------------------------------------------------------------|---------------------------------------------------|
| **Auto mode** (default) | `auto-init: true` (or unset)   | Spring Boot creates engine Beans from YAML on startup, binds them to the Registry via `autoBindEngineToProxy` | Configuration fixed in YAML, single backend, single tenant |
| **Manual mode**        | `auto-init: false`              | Business code calls `registry.switchEngine()` at runtime to create engines explicitly                | Configuration in DB, tenant isolation, ops hot-swap, gray migration |

#### Auto Mode (Auto-Init)

- Startup flow: Spring Boot reads `StorageProperties` → each implementation module's `AutoConfiguration` creates the corresponding `StorageEngine` Bean → injects it into the `Registry` and binds it to the Proxy.
- The `@Autowired StorageEngine` in business code gets a `StorageEngineProxyFactoryBean` proxy object. Calls are forwarded to the current delegate.
- Qualifiers like `@Qualifier("objectStorageEngine")` correspond to Beans registered in `autoBindEngineToProxy` by priority (object > ftp > sftp > smb > local).

#### Manual Mode (Manual Registry)

- Startup flow: implementation modules **do not** create engine instances through `AutoConfiguration`. `StorageEngineRegistry` pre-creates 12 empty Proxy placeholders.
- Business code (such as `@PostConstruct` at startup, tenant login, admin operations) calls `registry.switchEngine(StorageEngineEnum.MINIO, properties)`. The Registry looks up the Provider through SPI, calls `create()` → `afterPropertiesSet()` to complete initialization.
- Multiple engine types (object storage + FTP + SFTP) can be registered at the same time without affecting each other.

#### Proxy Mode

`StorageEngineRegistry` maintains a `ProxyHolder` for each engine type:

```java
static class ProxyHolder {
    final StorageEngineEnum engineType;
    final StorageEngine proxy;          // JDK dynamic proxy, created at construction
    volatile StorageEngine delegate;    // Current real engine, replaceable at runtime
}
```

- Business code injects the `proxy` (generated through `Proxy.newProxyInstance` at construction).
- On every method call, the proxy lazily resolves the real engine from the `delegate` field and forwards the call.
- Switching the engine only modifies the `delegate` reference. **No need to recreate the proxy object**, so the reference held by business code remains valid at all times.

## StorageEngine — Unified Storage API

`StorageEngine` is the only interface business code faces. All implementation classes (MinIO, S3, OSS, FTP, SFTP, SMB, Local, etc.) implement this interface.

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

### Method Overview

| Method                                                                   | Description                                                                                          | Return type                  |
|--------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|------------------------------|
| `putData(String key, Map<?, ?> collection)`                              | Upload JSON data (Map form, auto-serialized)                                                          | `UploadResponse`             |
| `putData(String key, Collection<?> collection)`                          | Upload JSON data (collection form)                                                                   | `UploadResponse`             |
| `putData(String key, Object object)`                                     | Upload JSON data (any POJO)                                                                          | `UploadResponse`             |
| `putObject(String key, File file)`                                       | Upload file (File source)                                                                            | `UploadResponse`             |
| `putObject(String key, InputStream inputStream)`                         | Upload file (stream source)                                                                          | `UploadResponse`             |
| `putImage(String key, File file, ImageOptions options)`                  | Upload image and process it per `ImageOptions` (compression, format conversion, etc.)                | `UploadResponse`             |
| `putImage(String key, InputStream inputStream, ImageOptions options)`    | Upload image and process (stream)                                                                    | `UploadResponse`             |
| `getData(String key, TypeReference<T> typeReference)`                    | Download JSON data and deserialize to the specified type                                             | `DownloadResponse<T>`        |
| `getObject(String key, File targetPath, boolean returnData)`             | Download file to local path                                                                          | `DownloadResponse<byte[]>`   |
| `getResumableObject(String key, String targetPath, boolean returnData)`  | Download file with resumable (range) support                                                         | `DownloadResponse<byte[]>`   |
| `existsObject(String key)`                                               | Check whether an object exists                                                                       | `boolean`                    |
| `issueDirectUploadPolicy(String key, int expireSeconds)`                 | Generate a client direct-upload policy (presigned URL); default impl returns a fallback              | `DirectUploadPolicy`         |
| `issueDirectDownloadPolicy(String key, int expireSeconds)`               | Generate a client direct-download policy (presigned download URL); default impl returns a fallback  | `DirectDownloadPolicy`       |

> The default implementation of the direct upload/download policy is provided by the `StorageEngine` interface and returns a `fallback` policy with `success=false`, suggesting "please use server-side upload/download". Engines with native signing capability (OSS, S3, COS, etc.) can override this method to return a presigned URL.

## StorageEngineProvider — Extension SPI

`StorageEngineProvider` is the **only SPI interface** each implementation module must implement. The Registry dynamically discovers all `StorageEngineProvider` Beans through `SpringContextHolder` on `switchEngine` / `refreshEngine`, filters them by `supportedEngineType()`, and invokes the matching Provider.

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

### Contract Methods

| Method                                                | Required | Description                                                                                                                                                                |
|-------------------------------------------------------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `supportedEngineType()`                               | **Yes**  | Returns the engine type this Provider supports (e.g. `StorageEngineEnum.MINIO`). The Registry uses this field for type matching.                                          |
| `create(StorageProperties properties)`                | **Yes**  | Creates an engine instance from `StorageProperties`. The Provider decides which sub-configuration (`ObjectConfig` / `FtpConfig` / etc.) to read from.                      |
| `afterPropertiesSet(StorageEngine engine)`            | No       | Initialization callback after creation. In **manual mode** this method replaces Spring's `@PostConstruct` and is used for bucket probing, health checks, etc.              |
| `destroy(StorageEngine engine)`                       | No       | Cleans up resources created by the Provider itself (connection pools, SDK clients, etc.) when the engine is destroyed. The Registry calls this before replacing the delegate on `switchEngine`. |
| `validate(StorageProperties properties)`              | No       | Configuration parameter validation. Throws `IllegalArgumentException` on failure. **The old engine remains unchanged.** Using the `ConfigValidation` utility is recommended. |
| `supports(Class<? extends StorageEngine> engineClass)`| No       | Reverse lookup: given an engine instance class, check whether the current Provider can create it. Default returns `true`. Each Provider should override this for exact matching to avoid ambiguity. |

### Implementation Example (from MinIO/SFTP/Local modules, etc.)

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
        // In manual mode, perform bucket probing and other initialization
        ((MinioStorageEngine) engine).probeBucket();
    }

    @Override
    public void destroy(StorageEngine engine) {
        // Shut down the MinIO client
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

## StorageEngineRegistry — Engine Registry

`StorageEngineRegistry` is the core of manual mode. All `switchEngine` / `refreshEngine` calls go through it.

### ProxyHolder Internal Structure

```java
static class ProxyHolder {
    final StorageEngineEnum engineType;
    final StorageEngine proxy;          // JDK dynamic proxy (created once at construction)
    volatile StorageEngine delegate;    // Real engine; hot-swap only changes this reference
}
```

- When constructing `StorageEngineRegistry`, it **pre-creates** a `ProxyHolder` for each type in `StorageEngineEnum.values()` (with the proxy object, delegate is `null`), and separately creates an `objectProxy` shared by all 8 object storage types.
- The object returned by `getProxy(type)` at any time is always a valid proxy. **Calling it before an engine is registered throws an explicit exception.**
- Switching engines only modifies the `volatile delegate` field. Visibility in a multi-threaded environment is guaranteed by the `volatile` semantics. Write operations are serialized by `synchronized`.

### Core Methods

| Method                                              | Description                                                              | Thread safety     |
|-----------------------------------------------------|--------------------------------------------------------------------------|-------------------|
| `registerInitialEngine(type, id, engine)`           | Called by auto mode at startup; binds the Spring-managed engine instance to the Proxy | `synchronized`    |
| `switchEngine(type, properties)`                     | Manual mode register/switch engine, shorthand form                       | `synchronized`    |
| `switchEngine(type, properties, actor, reason)`      | Manual mode register/switch engine, with audit context                   | `synchronized`    |
| `refreshEngine(type, properties)`                   | Refresh an already-initialized engine (forced replacement)               | `synchronized`    |
| `refreshEngine(type, properties, actor, reason)`    | Refresh engine, with audit context, rolls back on failure                | `synchronized`    |
| `getProxy(type)`                                    | Get the proxy object for the specified type (lazily resolves delegate)   | Non-blocking      |
| `getObjectProxy()`                                  | Get the unified object storage proxy (shared by all 8 object storages)   | Non-blocking      |
| `getDefaultProxy()`                                 | Get the default engine proxy (first registered engine)                   | Non-blocking      |
| `getEngine(type)`                                   | Get the current delegate directly (not proxied, use with care)           | Non-blocking      |
| `getDefaultEngine()`                                | Get the default engine instance                                          | Non-blocking      |
| `isInitialized()`                                   | Whether any engine has been registered                                   | Non-blocking      |
| `getCurrentEngineType()`                            | Current default engine type description                                  | Non-blocking      |
| `getRegisteredTypes()`                              | Set of registered engine types                                           | Non-blocking      |
| `snapshot()`                                        | Snapshot Map of registered engines                                       | Non-blocking      |

### Standard Switch Engine Flow (switchEngine)

```
1. Through SpringContextHolder.getBeansOfType(StorageEngineProvider.class)
   dynamically look up all Provider Beans
2. Filter out Providers for the target type by supportedEngineType()
3. provider.validate(properties)        // Validate configuration, throws on failure
4. provider.create(properties)          // Create the new engine
5. provider.afterPropertiesSet(engine)  // Initialize (replaces @PostConstruct in manual mode)
6. provider.destroy(oldEngine)          // Destroy the old engine, release connection pools
7. holder.delegate = newEngine          // volatile write, the new reference takes effect
8. For object storage, update objectProxy.delegate in sync
9. If it's the first engine or replaces the default engine, update defaultEngineType
10. metrics.incrementSwitch(engineType) // Increment counter
```

### Refresh Engine Rollback Semantics (refreshEngine)

The key difference between `refreshEngine` and `switchEngine` is the **build-then-tear-down rollback strategy**:

- **No engine currently** → throws `IllegalStateException` (distinguishing from `switchEngine` which allows first creation)
- `validate()` failure → throws an exception; the old delegate is unchanged
- `create()` + `afterPropertiesSet()` failure → automatically `destroy()`s the new engine before throwing, and the old delegate is unchanged
- Old engine `destroy()` failure → only logs `warn`, the new engine still takes effect, avoiding the situation where external resources are torn down but the old reference lingers

## StorageEngineAutoConfiguration

The Spring Boot auto-configuration class. It takes on the following responsibilities:

### 1. Register Registry + Proxy FactoryBean

| Bean name                | Description                                                  | Notes                                                                 |
|--------------------------|--------------------------------------------------------------|-----------------------------------------------------------------------|
| `storageEngineRegistry`  | Engine registry                                              | Always created, unrelated to `auto-init`                              |
| `storageEngineProxy`     | `@Primary` JDK dynamic proxy FactoryBean                     | Auto-mode-only, delegates to the current default engine               |
| `storageHealthIndicator` | Spring Boot HealthIndicator                                  | `@ConditionalOnEnabledHealthIndicator("storage")`                    |
| `storageMetricsBinder`   | Micrometer metrics binder                                    | `@ConditionalOnProperty(management.metrics.enable.storage)`          |

### 2. Manual Mode: Register Qualifier-Named Beans

When `auto-init: false`, the following 5 qualifier Beans are registered so that `@Qualifier` injection behaves the same in both modes:

| Bean name                                | Source                            | Engine type      |
|------------------------------------------|-----------------------------------|------------------|
| `objectStorageEngine`                    | `registry.getObjectProxy()`       | Shared by all object storage |
| `ftpStorageEngine`                       | `registry.getProxy(FTP)`          | FTP              |
| `sftpStorageEngine`                      | `registry.getProxy(SFTP)`         | SFTP             |
| `smbStorageEngine`                       | `registry.getProxy(SMB)`          | SMB              |
| `localStorageEngine`                     | `registry.getProxy(LOCAL)`        | Local storage    |
| `defaultStorageEngine` (`@Primary`)      | `registry.getDefaultProxy()`      | Default engine   |

### 3. Auto Mode: autoBindEngineToProxy ApplicationRunner

When `auto-init: true` (or unset), register an `ApplicationRunner` to complete startup binding:

- Select the default engine by priority: **object > ftp > sftp > smb > local**
- Call `registry.registerInitialEngine(defaultType, defaultId, engine)` to bind the default engine
- Call `proxyFactory.setDelegate(defaultEngine)` to bind the `@Primary` proxy
- Call `registry.registerInitialEngine(...)` to register other non-default engines
- Object storage engines use `Provider.supports()` to look up the type in reverse, avoiding fragile class name matching

## ConfigValidation — Parameter Validation Utility

`ConfigValidation` is a unified utility for the boilerplate code of `validate(StorageProperties)` in each implementation module, avoiding the repetitive `if (x == null) throw ...` pattern in every Provider.

```java
public final class ConfigValidation {
    public static void requireNonNull(Object value, String name);
    public static void requireNonBlank(String value, String name);
}
```

| Method                            | Validation rule                                                              | Exception on failure                       |
|-----------------------------------|------------------------------------------------------------------------------|--------------------------------------------|
| `requireNonNull(value, name)`     | `value != null`                                                              | `IllegalArgumentException(name + " 不能为空")` |
| `requireNonBlank(value, name)`    | `value != null && !value.isBlank()` (null / "" / all whitespace are considered empty) | `IllegalArgumentException(name + " 不能为空")` |

### Usage Example

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

> The error message keeps the original format (including the field name) for compatibility with existing test assertions.

## Configuration Model

### StorageProperties

`StorageProperties` is the **unified configuration entry**, bound to YAML through `@ConfigurationProperties(prefix = "platform.component.storage")`.

```java
@ConfigurationProperties(prefix = "platform.component.storage")
public class StorageProperties {
    private boolean autoInit = true;            // Whether to enable auto mode
    private ObjectConfig object = new ObjectConfig();
    private FtpConfig ftp = new FtpConfig();
    private SftpConfig sftp = new SftpConfig();
    private Smb3Config smb3 = new Smb3Config();
    private LocalConfig local = new LocalConfig();
    // builder() / all-args constructor
}
```

### Sub-Configurations

| Class                              | Description                              | Key fields                                                                                              |
|-----------------------------------|------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `ObjectConfig`                    | Shared config for 8 object storages      | `engine`, `endpoint`, `region`, `accessKeyId`, `accessKeySecret`, `bucketName`, `basePath`, `storageType` |
| `FtpConfig`                       | FTP protocol config                      | `enable`, `host`, `port`, `username`, `password`, `basePath`                                            |
| `SftpConfig`                      | SFTP protocol config                     | `enable`, `host`, `port`, `username`, `password`, `sshLogin`, `identityFile`, `basePath`                |
| `Smb3Config`                      | SMB3/CIFS config                         | `enable`, `host`, `domain`, `username`, `password`, `basePath`                                          |
| `LocalConfig`                     | Local storage config                     | `enable`, `path`, `cache`, `schema`, `cleanup`                                                          |
| `DirectUploadPolicy`              | Client direct-upload policy (presigned URL) | `method`, `uploadUrl`, `headers`, `formFields`, `bucketName`, `key`, `expireAt`, `fallback`            |
| `DirectDownloadPolicy`            | Client direct-download policy            | `downloadUrl`, `bucketName`, `key`, `expireAt`, `fallback`                                              |
| `UploadResponse`                  | Upload response                          | `success`, `url`, `key`, `hashValue`, `errorMessage`                                                    |
| `DownloadResponse<T>`             | Download response                        | `success`, `data` (byte array or deserialized object), `targetPath`, `errorMessage`                    |
| `ImageOptions` / `ImageFormat`    | Image processing options and supported format enum | `format`, `quality`, `width`, `height`                                                          |

### YAML Configuration Example

```yaml
platform:
  component:
    storage:
      auto-init: true              # Switch auto/manual mode
      object:                      # Object storage configuration
        engine: minio              # MINIO | AWS_S3 | ALIYUN_OSS | TENCENT_COS | HUAWEI_OBS | KSYUN_KS3 | VOLCENGINE_TOS | AZURE_BLOB
        endpoint: http://localhost:9000
        region: us-east-1
        accessKeyId: your-key
        accessKeySecret: your-secret
        bucketName: my-bucket
        basePath: /files/
      ftp:                         # FTP protocol configuration
        enable: true
        host: ftp.example.com
        port: 21
        username: user
        password: pass
        basePath: /storage/
      sftp:                        # SFTP protocol configuration
        enable: true
        host: sftp.example.com
        port: 22
        username: user
        password: pass
        basePath: /storage/
      smb3:                        # SMB3 configuration
        enable: true
        domain: example.com
        username: user
        password: pass
        basePath: /storage/
      local:                       # Local storage configuration
        enable: true
        path: ./storage/
```

## StorageEngineEnum

`StorageEngineEnum` is the engine type list, containing **8 object storages** + **4 file protocols** for a total of **12 enum values**.

### Enum Values

| Enum value         | `configValue`    | Description          | Object storage |
|--------------------|------------------|----------------------|:--------------:|
| `MINIO`            | `minio`          | MinIO                |       Yes      |
| `ALIYUN_OSS`       | `aliyun_oss`     | Alibaba Cloud OSS    |       Yes      |
| `TENCENT_COS`      | `tencent_cos`    | Tencent Cloud COS    |       Yes      |
| `HUAWEI_OBS`       | `huawei_obs`     | Huawei Cloud OBS     |       Yes      |
| `AWS_S3`           | `aws_s3`         | AWS S3               |       Yes      |
| `KSYUN_KS3`        | `ksyun_ks3`      | Kingsoft Cloud KS3   |       Yes      |
| `VOLCENGINE_TOS`   | `volcengine_tos` | Volcano Engine TOS   |       Yes      |
| `AZURE_BLOB`       | `azure_blob`     | Microsoft Azure Blob |       Yes      |
| `FTP`              | `ftp`            | FTP protocol         |       No       |
| `SFTP`             | `sftp`           | SFTP protocol        |       No       |
| `SMB`              | `smb`            | SMB protocol         |       No       |
| `LOCAL`            | `local`          | Local file system    |       No       |

### Field Description

- `description`: Chinese description (used for logs and error prompts)
- `configValue`: The string value in YAML, corresponding to the `havingValue` of `@ConditionalOnProperty`
- `objectStorage`: Whether it is object storage (as opposed to FTP/SFTP/SMB/LOCAL), used to sync the unified object storage proxy `objectProxy`

### Static Methods

| Method                                       | Description                                                                          |
|----------------------------------------------|--------------------------------------------------------------------------------------|
| `isObjectStorage()`                          | Check whether the current enum value is object storage                                |
| `fromConfigValue(String configValue)`        | Look up an enum by its config string (case-insensitive), returns `Optional<StorageEngineEnum>` |
| `validConfigValues()`                        | Return a comma-separated string of all valid config values, for use in error messages |

```java
StorageEngineEnum type = StorageEngineEnum.fromConfigValue("aws_s3").orElseThrow();
// Automatically matched through @ConditionalOnProperty's havingValue = "aws_s3"
```

## Observability

The core module provides two optional observability Beans that plug into Spring Boot Actuator and Micrometer:

### StorageHealthIndicator

`StorageHealthIndicator` implements the Spring Boot `HealthIndicator` interface, exposing the runtime status of storage engines:

- Exposes metadata such as the current number of registered engines, default engine type, and default engine ID
- View through the `/actuator/health` endpoint
- Switch: `management.health.storage.enabled=false` (default `true`)
- Only registered through `@ConditionalOnClass` when the Spring Boot Actuator starter is on the classpath

### StorageMetricsBinder

`StorageMetricsBinder` implements Micrometer's `MeterBinder` and registers the following metrics:

| Metric                          | Type     | Description                          |
|---------------------------------|----------|--------------------------------------|
| `storage.engine.type`           | Gauge    | Current default engine type enum value |
| `storage.engine.count`          | Gauge    | Number of registered engines         |
| `storage.engine.switch` (×12)   | Counter  | Switch count per engine type         |
| `storage.engine.register` (×12) | Counter  | Register count per engine type       |

- Switch: `management.metrics.enable.storage=NONE` (default `ALL`)
- Only registered through `@ConditionalOnClass` when Micrometer's `MeterBinder` is available

### StorageEngineMetrics

`StorageEngineMetrics` is the Registry's internal counter, used **only** when `StorageMetricsBinder` binds it to Micrometer. The Registry calls `incrementRegister` / `incrementSwitch` on `registerInitialEngine` / `switchEngine` / `refreshEngine` to update the counts.

### Disable Example (Without Monitoring)

```yaml
management:
  health:
    storage:
      enabled: false
  metrics:
    enable:
      storage: NONE
```

When fully disabled, `/actuator/health` no longer exposes storage engine status, and the Prometheus exporter no longer tries to collect storage-related metrics. Zero noise.

## Extension Guide: Adding a New Storage Engine

Follow the 6 steps below to add a new storage engine implementation (e.g. `storage-ceph`, assuming it's S3-compatible but needs an independent Provider).

### Step 1: Create a New Module

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-core</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### Step 2: Implement StorageEngine

```java
public class CephStorageEngine implements StorageEngine {
    // Implement all methods: putObject / getObject / existsObject, etc.
    // Override issueDirectUploadPolicy / issueDirectDownloadPolicy as needed
}
```

### Step 3: Implement StorageEngineProvider

```java
@Component
public class CephStorageEngineProvider implements StorageEngineProvider {

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.CEPH;  // New enum value
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

### Step 4: Create the AutoConfiguration

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

Register `AutoConfiguration` in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.richie.component.storage.ceph.CephAutoConfiguration
```

### Step 5: Add a Conditional Branch in the core Module's AutoConfiguration

Typically **no need** to modify `StorageEngineAutoConfiguration`, because `autoBindEngineToProxy` looks up the type through `Provider.supports()`. Just make sure the `objectStorageEngine` Bean exists.

### Step 6: Register a New Enum Value in StorageEngineEnum

```java
public enum StorageEngineEnum {
    // ... existing enum values
    CEPH("Ceph", "ceph", true),  // New addition
    // ... other enum values
}
```

> Note: Modifying `StorageEngineEnum` affects all components that depend on the core module. New enum values should be **appended only**. Do not modify the existing order or values, otherwise the `configValue` will become incompatible.

## Dependency Information

### Maven Coordinates

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-core</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### Key Transitive Dependencies

| Dependency                                                    | Description                                                                                       |
|---------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `com.richie.base:atlas-richie-context`                        | `SpringContextHolder` (the Registry uses it to dynamically look up Providers)                     |
| `org.springframework.boot:spring-boot-autoconfigure`          | `@AutoConfiguration` and conditional annotations                                                  |
| `org.springframework.boot:spring-boot-actuator-autoconfigure` | HealthIndicator and Micrometer integration (optional, checked at runtime through `@ConditionalOnClass`) |
| `io.micrometer:micrometer-core`                               | MeterBinder interface (optional)                                                                  |
| `com.fasterxml.jackson.core:jackson-databind`                 | `TypeReference` and JSON serialization                                                            |
| `org.projectlombok:lombok`                                    | `@Getter` / `@Slf4j` / `@RequiredArgsConstructor` (compile-time)                                  |

## Best Practices

1. **Business code only depends on the `StorageEngine` interface**
   ```java
   // ✅ Correct
   @Autowired
   private StorageEngine storageEngine;
   
   // ❌ Wrong: holding a concrete implementation class directly
   @Autowired
   private MinioStorageEngine minioEngine;
   ```

2. **Prefer `refreshEngine` to replace an already-initialized engine in manual mode**
   - Unlike `switchEngine` which allows first creation, `refreshEngine` throws an exception when no engine exists, with clearer semantics
   - Automatically rolls back to the old engine on failure, ensuring runtime stability

3. **Provider's `validate()` should use the `ConfigValidation` utility**
   - Error messages are uniformly `<field> 不能为空`
   - Test assertions can be written against this fixed format

4. **Provider overrides `supports(Class)` for exact matching**
   - The default implementation `return true` is too permissive; when multiple Providers return true at the same time, the reverse type lookup can return any of them
   - Override as `MyStorageEngine.class.equals(engineClass)` to avoid ambiguity

5. **Do not perform business initialization in the Provider constructor or `@PostConstruct`**
   - In auto mode, the Spring container handles the lifecycle
   - In manual mode, call explicitly through `afterPropertiesSet(engine)` to ensure consistent behavior across both modes

6. **Actively disable HealthIndicator / MetricsBinder when not connecting to monitoring**
   - `management.health.storage.enabled=false`
   - `management.metrics.enable.storage=NONE`
   - Avoid noise logs from `CollectorRegistry` not finding collectors

## FAQ

### Q: What's the difference between `registerInitialEngine` and `switchEngine`?

- `registerInitialEngine` is called by auto mode's `ApplicationRunner` and requires the corresponding Proxy's delegate to **be null** (startup binding); a second registration throws an exception
- `switchEngine` is the standard API for manual mode. It **allows** first creation (normal registration when delegate is null), and also allows replacing an existing engine

### Q: Why can't business code hold the return value of `getEngine()` directly?

`getEngine()` returns the **current delegate reference**, which becomes the destroyed old engine instance after `switchEngine`. Business code must inject through `@Autowired StorageEngine` (which gets the Proxy) or `getProxy(type)` (also returns the Proxy). Calls are forwarded by the Proxy to the current delegate.

### Q: What's the relationship between the `objectStorageEngine` qualifier and a specific object storage type (like `MINIO`)?

- `@Qualifier("objectStorageEngine")` gets the `objectProxy`, which is **shared by all 8 object storages**
- When switching the object storage type, `objectProxy.delegate` is updated in sync, and business code needs no modification
- Each of the 8 object storage types in `StorageEngineEnum` has its own independent Proxy. In theory, you can explicitly obtain it through `getProxy(StorageEngineEnum.MINIO)`, but this is usually not recommended (business code should not be aware of the specific type)

### Q: What does `StorageEngineInvocationHandler` do?

`StorageEngineInvocationHandler` is the `InvocationHandler` implementation for the JDK dynamic proxy. On every method call it lazily resolves the real engine from `ProxyHolder.delegate` and forwards the call. If the delegate is null, it throws an explicit exception indicating that no engine is currently registered.

### Q: Why isn't `Provider` injected into `Registry` through the constructor?

The Registry is created at construction time (`new StorageEngineRegistry()`), at which point the Spring container may not have finished registering all `StorageEngineProvider` Beans. Dynamically looking them up through `SpringContextHolder.getApplicationContext().getBeansOfType(...)` ensures that **Providers registered later in manual mode can still be discovered**.

## Related Documentation

- [Storage Component Overview](../README.md) — Quick start, dual-mode architecture, configuration guide
- [Local Storage Implementation](../atlas-richie-component-storage-local/README.md)
- [AWS S3 Implementation](../atlas-richie-component-storage-s3/README.md)
- [Alibaba Cloud OSS Implementation](../atlas-richie-component-storage-oss/README.md)
- [Tencent Cloud COS Implementation](../atlas-richie-component-storage-cos/README.md)
- [Huawei Cloud OBS Implementation](../atlas-richie-component-storage-obs/README.md)
- [MinIO Implementation](../atlas-richie-component-storage-minio/README.md)
- [Kingsoft Cloud KS3 Implementation](../atlas-richie-component-storage-ks3/README.md)
- [Volcano Engine TOS Implementation](../atlas-richie-component-storage-tos/README.md)
- [Azure Blob Implementation](../atlas-richie-component-storage-azure/README.md)
- [SFTP Implementation](../atlas-richie-component-storage-sftp/README.md)
- [SMB Implementation](../atlas-richie-component-storage-smb/README.md)
