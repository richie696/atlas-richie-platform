# Richie Component Storage - 华为云 OBS

## 概述

`richie-component-storage-obs` 是华为云对象存储服务（OBS）的实现，基于华为云 OBS SDK 提供完整的 OBS 存储能力，支持生命周期管理、跨区域复制等高级功能。

## 核心特性

- ✅ **华为云 OBS 兼容** - 完整支持华为云 OBS API
- ✅ **多种存储类型** - 支持标准、低频、归档、深度归档
- ✅ **生命周期管理** - 支持自动转换存储类型
- ✅ **断点续传** - 支持大文件断点续传
- ✅ **双模式架构** - 支持自动配置（Auto-Init）与手动注册（Manual Registry）两种初始化模式，灵活适配 Spring Boot 自动装配及非 Spring 环境
- ✅ **自动配置** - Spring Boot 自动配置

## 双模式架构

本组件支持两种初始化模式：

### 1. 自动模式（Auto-Init，默认）

`auto-init: true`（默认值）时，Spring Boot 自动装配负责：

- 根据 `engine: HUAWEI_OBS` 配置创建 `ObsStorageEngineProvider` Bean
- 调用 Provider 的 `create(properties)` 创建引擎实例
- 通过 `@Qualifier("objectStorageEngine")` 注入使用

### 2. 手动模式（Manual）

`auto-init: false` 时，引擎由 `StorageEngineRegistry` 通过 SPI 发现并管理：

- Provider 不自动注册为 Bean，由 `ServiceLoader` 发现
- 通过 `registry.switchEngine(StorageEngineEnum.HUAWEI_OBS)` 运行时切换
- 适用于非 Spring 环境或多引擎动态切换场景

```java
// 手动模式：运行时切换引擎
storageEngineRegistry.switchEngine(StorageEngineEnum.HUAWEI_OBS);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

每个实现包都提供 `StorageEngineProvider` SPI 实现，`ObsStorageEngineProvider` 负责：

| 方法 | 说明 |
|------|------|
| `supportedEngineType()` | 返回 `StorageEngineEnum.HUAWEI_OBS` |
| `create(properties)` | 从配置创建引擎实例 |
| `validate(properties)` | 校验 endpoint / accessKeyId / accessKeySecret / bucketName 必填 |
| `destroy(engine)` | 释放资源 |

自动模式下 Provider 在 `ObsAutoConfiguration` 中注册为 Bean；手动模式下由 Registry 通过 SPI 发现。

## 参数校验 (ConfigValidation)

引擎创建前会通过 `ConfigValidation` 工具类校验必填参数，校验失败时抛出 `IllegalArgumentException`：

| 参数 | 校验规则 |
|------|---------|
| endpoint | 非空 |
| accessKeyId | 非空 |
| accessKeySecret | 非空 |
| bucketName | 非空 |

## 直传策略 (DirectUploadPolicy)

OBS 引擎支持通过预签名 URL 实现客户端直传到对象存储，减少服务端流量压力：

| 字段 | 说明 |
|------|------|
| uploadUrl | 预签名上传 URL |
| method | HTTP 方法（PUT） |
| headers | 签名头信息 |
| expireAt | 策略过期时间 |
| success | 策略是否可用 |

```java
DirectUploadPolicy policy = storageEngine.issueDirectUploadPolicy(
    "uploads/example.jpg", 3600);
// 返回预签名 PUT URL，客户端可直接上传
```

## 快速开始

### 1. 添加依赖

```xml
<!-- 必备核心库 -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-core</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
<!-- 实现库 -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-obs</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    storage:
      object:
        # 存储引擎类型（必填）
        engine: HUAWEI_OBS
        # OBS访问域名（必填）
        # 格式：obs.region.myhuaweicloud.com
        # 示例：obs.cn-north-4.myhuaweicloud.com
        endpoint: obs.cn-north-4.myhuaweicloud.com
        # 区域（必填）
        # 示例：cn-north-4, cn-east-3, cn-south-1
        region: cn-north-4
        # 访问密钥ID（Access Key Id）（必填）
        accessKeyId: your-access-key-id
        # 访问密钥（Secret Access Key）（必填）
        accessKeySecret: your-secret-access-key
        # 存储桶名称（Bucket）（必填）
        bucketName: my-bucket
        # 桶内基础路径（可选）
        basePath: /storage
        # 存储类型（可选，默认：STANDARD）
        # 可选值：STANDARD, STANDARD_IA, ARCHIVE, DEEP_ARCHIVE
        storageType: STANDARD
```

### 3. 使用

注入 `StorageEngine`（Bean 名称为 `objectStorageEngine`）即可使用：

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    @Qualifier("objectStorageEngine")
    private final StorageEngine storageEngine;
    
    public void uploadFile(String key, File file) {
        UploadResponse response = storageEngine.putObject(key, file);
        if (response.isSuccess()) {
            log.info("上传成功: {}", response.getUrl());
        }
    }
}
```

## 配置说明

### ⚠️ 重要配置差异

华为云 OBS 与其他云存储的主要配置差异：

| 配置项 | 华为云 OBS | 阿里云 OSS | 腾讯云 COS |
|--------|-----------|-----------|-----------|
| **engine 值** | `HUAWEI_OBS` | `ALIYUN_OSS` | `TENCENT_COS` |
| **endpoint 格式** | `obs.region.myhuaweicloud.com` | `oss-cn-region.aliyuncs.com` | `cos.region.myqcloud.com` |
| **region 格式** | `cn-region-n` | `cn-region` | `ap-region` |
| **访问密钥名称** | Access Key Id / Secret Access Key | AccessKey ID / AccessKey Secret | SecretId / SecretKey |
| **存储类型** | 4种（标准、低频、归档、深度归档） | 4种 | 11种 |
| **生命周期管理** | ✅ 支持 | ✅ 支持 | ✅ 支持 |

### endpoint 配置

华为云 OBS 的 endpoint 格式：

- **标准格式**: `obs.region.myhuaweicloud.com`
  - 示例：`obs.cn-north-4.myhuaweicloud.com`
  - 示例：`obs.cn-east-3.myhuaweicloud.com`
  - 示例：`obs.cn-south-1.myhuaweicloud.com`

- **内网 endpoint**: `obs.region-internal.myhuaweicloud.com`
  - 适用于同区域 ECS 访问，免流量费
  - 示例：`obs.cn-north-4-internal.myhuaweicloud.com`

- **自定义域名**: 可在 OBS 控制台绑定自定义域名

### region 配置

华为云 OBS 支持的区域：

| 区域 | 代码 |
|------|------|
| 华北-北京四 | `cn-north-4` |
| 华北-北京一 | `cn-north-1` |
| 华东-上海一 | `cn-east-3` |
| 华东-上海二 | `cn-east-2` |
| 华南-广州 | `cn-south-1` |
| 西南-贵阳一 | `cn-southwest-2` |
| 亚太-曼谷 | `ap-southeast-2` |
| 亚太-新加坡 | `ap-southeast-1` |
| 非洲-约翰内斯堡 | `af-south-1` |
| 拉美-圣保罗一 | `la-south-2` |
| 拉美-墨西哥城二 | `la-north-2` |

### 存储类型

华为云 OBS 支持的存储类型：

| 存储类型 | 说明 | 适用场景 |
|---------|------|---------|
| `STANDARD` | 标准存储 | 频繁访问的数据 |
| `STANDARD_IA` | 低频访问存储 | 不经常访问但需要快速访问的数据 |
| `ARCHIVE` | 归档存储 | 长期保存、很少访问的数据 |
| `DEEP_ARCHIVE` | 深度归档存储 | 极长期保存、几乎不访问的数据 |

### 访问凭证

华为云 OBS 使用 Access Key Id 和 Secret Access Key 进行身份验证：

1. 登录华为云控制台
2. 进入统一身份认证服务（IAM）
3. 创建用户并分配 OBS 访问权限
4. 创建访问密钥

> **安全提示**: 
> - 使用 IAM 子用户，遵循最小权限原则
> - 不要将访问密钥提交到代码仓库
> - 使用环境变量或密钥管理服务（如华为云 KMS）

## 功能特性

### 1. 生命周期管理

华为云 OBS 支持生命周期策略，自动转换存储类型：

```yaml
# 在 OBS 控制台配置生命周期策略
# 例如：30天后转换为低频存储，90天后转换为归档存储
```

### 2. 存储类型自动转换

上传文件时，会根据配置的 `storageType` 自动设置对象的存储类型：

```java
// 配置为 ARCHIVE 存储类型
UploadResponse response = storageEngine.putObject("backup.zip", file);
// 文件会自动设置为归档存储类型
```

### 3. 内网访问

如果应用部署在华为云 ECS 上，可以使用内网 endpoint 免流量费：

```yaml
platform:
  component:
    storage:
      object:
        endpoint: obs.cn-north-4-internal.myhuaweicloud.com  # 内网 endpoint
```

### 4. 跨区域复制

华为云 OBS 支持跨区域复制功能（需在控制台配置），实现数据异地备份。

## 最佳实践

1. **区域选择**
   - 选择距离用户最近的区域，降低延迟
   - 考虑数据合规要求

2. **存储类型选择**
   - 频繁访问：`STANDARD`
   - 偶尔访问：`STANDARD_IA`
   - 长期归档：`ARCHIVE` 或 `DEEP_ARCHIVE`

3. **生命周期管理**
   - 配置生命周期策略，自动转换存储类型
   - 根据访问频率设置转换时间点

4. **访问凭证管理**
   - 使用 IAM 子用户，遵循最小权限原则
   - 使用环境变量或密钥管理服务
   - 定期轮换访问密钥

5. **成本优化**
   - 使用生命周期策略自动转换存储类型
   - 删除不需要的对象
   - 使用内网 endpoint 免流量费

## 常见问题

### Q: 如何配置生命周期管理？

A: 在 OBS 控制台配置生命周期策略，设置对象在不同时间点的存储类型转换规则。

### Q: 支持自定义域名吗？

A: 支持，在 OBS 控制台绑定自定义域名后，使用自定义域名作为 endpoint。

### Q: 如何设置对象的访问权限？

A: 可以在 OBS 控制台设置存储桶和对象的访问权限，或通过 SDK 设置 ACL。

### Q: 内网访问有什么优势？

A: 内网访问免流量费，延迟更低，但仅限同区域 ECS 访问。

### Q: 深度归档存储有什么特点？

A: 深度归档存储成本最低，但恢复时间较长（通常需要 12-24 小时），适合极长期保存的数据。

## 相关文档

- [核心存储组件 (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [华为云 OBS 官方文档](https://support.huaweicloud.com/obs/)
- [OBS Java SDK](https://support.huaweicloud.com/sdk-java-devg-obs/obs_21_0101.html)
- [OBS 生命周期管理](https://support.huaweicloud.com/usermanual-obs/obs_03_0049.html)
- [直传策略 (DirectUploadPolicy)](../atlas-richie-component-storage-core/README.md#配置模型)
- [存储引擎 SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)

