# Richie Component Storage - 金山云 KS3

## 概述

`richie-component-storage-ks3` 是金山云对象存储服务（KS3）的实现，基于金山云 KS3 SDK 提供完整的 KS3 存储能力。KS3 兼容 AWS S3 API，支持标准、低频、归档等多种存储类型。

## 核心特性

- ✅ **S3 兼容** - 兼容 AWS S3 API
- ✅ **多种存储类型** - 支持标准、低频、归档
- ✅ **断点续传** - 支持大文件断点续传
- ✅ **双模式架构** - 支持自动配置（Auto-Init）与手动注册（Manual Registry）两种初始化模式，灵活适配 Spring Boot 自动装配及非 Spring 环境
- ✅ **自动配置** - Spring Boot 自动配置

## 双模式架构

本组件支持两种初始化模式：

### 1. 自动模式（Auto-Init，默认）

`auto-init: true`（默认值）时，Spring Boot 自动装配负责：

- 根据 `engine: KSYUN_KS3` 配置创建 `Ks3StorageEngineProvider` Bean
- 调用 Provider 的 `create(properties)` 创建引擎实例
- 通过 `@Qualifier("objectStorageEngine")` 注入使用

### 2. 手动模式（Manual）

`auto-init: false` 时，引擎由 `StorageEngineRegistry` 通过 SPI 发现并管理：

- Provider 不自动注册为 Bean，由 `ServiceLoader` 发现
- 通过 `registry.switchEngine(StorageEngineEnum.KSYUN_KS3)` 运行时切换
- 适用于非 Spring 环境或多引擎动态切换场景

```java
// 手动模式：运行时切换引擎
storageEngineRegistry.switchEngine(StorageEngineEnum.KSYUN_KS3);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

每个实现包都提供 `StorageEngineProvider` SPI 实现，`Ks3StorageEngineProvider` 负责：

| 方法 | 说明 |
|------|------|
| `supportedEngineType()` | 返回 `StorageEngineEnum.KSYUN_KS3` |
| `create(properties)` | 从配置创建引擎实例 |
| `validate(properties)` | 校验 endpoint / accessKeyId / accessKeySecret / bucketName 必填 |
| `destroy(engine)` | 释放资源 |

自动模式下 Provider 在 `Ks3AutoConfiguration` 中注册为 Bean；手动模式下由 Registry 通过 SPI 发现。

## 参数校验 (ConfigValidation)

引擎创建前会通过 `ConfigValidation` 工具类校验必填参数，校验失败时抛出 `IllegalArgumentException`：

| 参数 | 校验规则 |
|------|---------|
| endpoint | 非空 |
| accessKeyId | 非空 |
| accessKeySecret | 非空 |
| bucketName | 非空 |

## 直传策略 (DirectUploadPolicy)

KS3 引擎支持通过预签名 URL 实现客户端直传到对象存储，减少服务端流量压力：

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
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-core</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-ks3</artifactId>
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
        engine: KSYUN_KS3
        # KS3访问域名（必填）
        # 格式：ks3-cn-region.ksyuncs.com
        # 示例：ks3-cn-beijing.ksyuncs.com
        endpoint: ks3-cn-beijing.ksyuncs.com
        # 区域（必填）
        # 示例：cn-beijing, cn-shanghai, cn-guangzhou
        region: cn-beijing
        # 访问密钥ID（Access Key ID）（必填）
        accessKeyId: your-access-key-id
        # 访问密钥（Secret Access Key）（必填）
        accessKeySecret: your-secret-access-key
        # 存储桶名称（Bucket）（必填）
        bucketName: my-bucket
        # 桶内基础路径（可选）
        basePath: /storage
        # 存储类型（可选，默认：STANDARD）
        # 可选值：STANDARD, STANDARD_IA, ARCHIVE
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

金山云 KS3 与其他云存储的主要配置差异：

| 配置项 | 金山云 KS3 | AWS S3 | 阿里云 OSS |
|--------|-----------|--------|-----------|
| **engine 值** | `KSYUN_KS3` | `AWS_S3` | `ALIYUN_OSS` |
| **endpoint 格式** | `ks3-cn-region.ksyuncs.com` | `s3.region.amazonaws.com` | `oss-cn-region.aliyuncs.com` |
| **region 格式** | `cn-region` | `us-east-1` | `cn-region` |
| **访问密钥名称** | Access Key ID / Secret Access Key | Access Key ID / Secret Access Key | AccessKey ID / AccessKey Secret |
| **S3 兼容性** | ✅ 兼容 | ✅ 原生 | ❌ 不兼容 |
| **存储类型** | 3种（标准、低频、归档） | 15+种 | 4种 |

### endpoint 配置

金山云 KS3 的 endpoint 格式：

- **标准格式**: `ks3-cn-region.ksyuncs.com`
  - 示例：`ks3-cn-beijing.ksyuncs.com`
  - 示例：`ks3-cn-shanghai.ksyuncs.com`
  - 示例：`ks3-cn-guangzhou.ksyuncs.com`

- **内网 endpoint**: `ks3-cn-region-internal.ksyuncs.com`
  - 适用于同区域 KEC 访问，免流量费
  - 示例：`ks3-cn-beijing-internal.ksyuncs.com`

### region 配置

金山云 KS3 支持的区域：

| 区域 | 代码 |
|------|------|
| 北京 | `cn-beijing` |
| 上海 | `cn-shanghai` |
| 广州 | `cn-guangzhou` |
| 杭州 | `cn-hangzhou` |
| 香港 | `cn-hongkong` |
| 俄罗斯 | `ru-moscow` |
| 新加坡 | `ap-singapore` |

### 存储类型

金山云 KS3 支持的存储类型：

| 存储类型 | 说明 | 适用场景 |
|---------|------|---------|
| `STANDARD` | 标准存储 | 频繁访问的数据 |
| `STANDARD_IA` | 低频访问存储 | 不经常访问但需要快速访问的数据 |
| `ARCHIVE` | 归档存储 | 长期保存、很少访问的数据 |

### 访问凭证

金山云 KS3 使用 Access Key ID 和 Secret Access Key 进行身份验证：

1. 登录金山云控制台
2. 进入访问控制（IAM）服务
3. 创建用户并分配 KS3 访问权限
4. 创建访问密钥

> **安全提示**: 
> - 使用 IAM 子用户，遵循最小权限原则
> - 不要将访问密钥提交到代码仓库
> - 使用环境变量或密钥管理服务

## 功能特性

### 1. S3 兼容性

金山云 KS3 兼容 AWS S3 API，可以无缝迁移：

```java
// 使用 KS3 的代码与使用 S3 的代码基本相同
UploadResponse response = storageEngine.putObject("file.txt", file);
```

### 2. 存储类型自动转换

上传文件时，会根据配置的 `storageType` 自动设置对象的存储类型：

```java
// 配置为 ARCHIVE 存储类型
UploadResponse response = storageEngine.putObject("backup.zip", file);
// 文件会自动设置为归档存储类型
```

### 3. 内网访问

如果应用部署在金山云 KEC 上，可以使用内网 endpoint 免流量费：

```yaml
platform:
  component:
    storage:
      object:
        endpoint: ks3-cn-beijing-internal.ksyuncs.com  # 内网 endpoint
```

## 最佳实践

1. **区域选择**
   - 选择距离用户最近的区域，降低延迟
   - 考虑数据合规要求

2. **存储类型选择**
   - 频繁访问：`STANDARD`
   - 偶尔访问：`STANDARD_IA`
   - 长期归档：`ARCHIVE`

3. **访问凭证管理**
   - 使用 IAM 子用户，遵循最小权限原则
   - 使用环境变量或密钥管理服务
   - 定期轮换访问密钥

4. **成本优化**
   - 使用生命周期策略自动转换存储类型
   - 删除不需要的对象
   - 使用内网 endpoint 免流量费

## 常见问题

### Q: KS3 与 AWS S3 有什么区别？

A: KS3 是 S3 兼容的对象存储服务，API 与 S3 基本一致，但存储类型和支持的功能略有差异。

### Q: 如何从 S3 迁移到 KS3？

A: 由于 API 兼容，只需修改 endpoint 和访问凭证即可，代码基本无需修改。

### Q: 支持自定义域名吗？

A: 支持，在 KS3 控制台绑定自定义域名后，使用自定义域名作为 endpoint。

### Q: 内网访问有什么优势？

A: 内网访问免流量费，延迟更低，但仅限同区域 KEC 访问。

## 相关文档

- [核心存储组件 (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [金山云 KS3 官方文档](https://docs.ksyun.com/product/ks3)
- [KS3 Java SDK](https://docs.ksyun.com/documents/2320)
- [直传策略 (DirectUploadPolicy)](../atlas-richie-component-storage-core/README.md#配置模型)
- [存储引擎 SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)

