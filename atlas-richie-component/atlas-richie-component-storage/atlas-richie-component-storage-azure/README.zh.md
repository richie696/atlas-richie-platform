# Richie Component Storage - Azure Blob Storage

## 概述

`richie-component-storage-azure` 是微软 Azure Blob Storage 的实现，基于 Azure Storage SDK for Java 提供完整的 Azure Blob 存储能力。

## 核心特性

- ✅ **Azure Blob 兼容** - 完整支持 Azure Blob Storage API
- ✅ **多种访问层** - 支持热、冷、归档访问层
- ✅ **断点续传** - 支持大文件断点续传
- ✅ **双模式架构** - 支持自动配置（Auto-Init）与手动注册（Manual Registry）两种初始化模式，灵活适配 Spring Boot 自动装配及非 Spring 环境
- ✅ **自动配置** - Spring Boot 自动配置

## 双模式架构

本组件支持两种初始化模式：

### 1. 自动模式（Auto-Init，默认）

`auto-init: true`（默认值）时，Spring Boot 自动装配负责：

- 根据 `engine: AZURE_BLOB` 配置创建 `AzureBlobStorageEngineProvider` Bean
- 调用 Provider 的 `create(properties)` 创建引擎实例
- 通过 `@Qualifier("objectStorageEngine")` 注入使用

### 2. 手动模式（Manual）

`auto-init: false` 时，引擎由 `StorageEngineRegistry` 通过 SPI 发现并管理：

- Provider 不自动注册为 Bean，由 `ServiceLoader` 发现
- 通过 `registry.switchEngine(StorageEngineEnum.AZURE_BLOB)` 运行时切换
- 适用于非 Spring 环境或多引擎动态切换场景

```java
// 手动模式：运行时切换引擎
storageEngineRegistry.switchEngine(StorageEngineEnum.AZURE_BLOB);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

每个实现包都提供 `StorageEngineProvider` SPI 实现，`AzureBlobStorageEngineProvider` 负责：

| 方法 | 说明 |
|------|------|
| `supportedEngineType()` | 返回 `StorageEngineEnum.AZURE_BLOB` |
| `create(properties)` | 从配置创建引擎实例 |
| `validate(properties)` | 校验 endpoint / accessKeyId / accessKeySecret / bucketName 必填 |
| `destroy(engine)` | 释放资源 |

自动模式下 Provider 在 `AzureBlobAutoConfiguration` 中注册为 Bean；手动模式下由 Registry 通过 SPI 发现。

## 参数校验 (ConfigValidation)

引擎创建前会通过 `ConfigValidation` 工具类校验必填参数，校验失败时抛出 `IllegalArgumentException`：

| 参数 | 校验规则 |
|------|---------|
| endpoint | 非空 |
| accessKeyId | 非空 |
| accessKeySecret | 非空 |
| bucketName | 非空 |

## 直传策略 (DirectUploadPolicy)

Azure Blob 引擎支持通过 SAS 签名 URL 实现客户端直传到对象存储，减少服务端流量压力：

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
// 返回 SAS 签名 PUT URL，客户端可直接上传
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
    <artifactId>atlas-richie-component-storage-azure</artifactId>
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
        engine: AZURE_BLOB
        # Azure Blob访问域名（必填）
        # 格式：account.blob.core.windows.net
        # 示例：mystorageaccount.blob.core.windows.net
        endpoint: mystorageaccount.blob.core.windows.net
        # 区域（必填）
        # 示例：eastus, westus, westeurope
        region: eastus
        # 访问密钥ID（Storage Account Name）（必填）
        accessKeyId: mystorageaccount
        # 访问密钥（Storage Account Key）（必填）
        accessKeySecret: your-storage-account-key
        # 存储桶名称（Container Name）（必填）
        bucketName: my-container
        # 桶内基础路径（可选）
        basePath: /storage
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

Azure Blob Storage 与其他云存储的主要配置差异：

| 配置项 | Azure Blob | AWS S3 | 阿里云 OSS |
|--------|-----------|--------|-----------|
| **engine 值** | `AZURE_BLOB` | `AWS_S3` | `ALIYUN_OSS` |
| **endpoint 格式** | `account.blob.core.windows.net` | `s3.region.amazonaws.com` | `oss-cn-region.aliyuncs.com` |
| **region 格式** | Azure 区域代码（如 `eastus`） | AWS 区域代码（如 `us-east-1`） | 阿里云区域代码（如 `cn-hangzhou`） |
| **访问密钥名称** | Storage Account Name / Storage Account Key | Access Key ID / Secret Access Key | AccessKey ID / AccessKey Secret |
| **存储桶名称** | Container Name | Bucket Name | Bucket Name |
| **访问层** | 热、冷、归档 | 存储类型（15+种） | 存储类型（4种） |
| **连接字符串** | ✅ 支持 | ❌ 不支持 | ❌ 不支持 |

### endpoint 配置

Azure Blob Storage 的 endpoint 格式：

- **标准格式**: `account.blob.core.windows.net`
  - 示例：`mystorageaccount.blob.core.windows.net`
  - `account` 为存储账户名称

- **自定义域名**: 可在 Azure 门户配置自定义域名

### region 配置

Azure Blob Storage 支持的区域：

| 区域 | 代码 |
|------|------|
| 美国东部 | `eastus` |
| 美国西部 | `westus` |
| 美国中部 | `centralus` |
| 欧洲西部 | `westeurope` |
| 欧洲北部 | `northeurope` |
| 亚太东部 | `eastasia` |
| 亚太东南部 | `southeastasia` |
| 中国东部 | `chinaeast` |
| 中国北部 | `chinanorth` |

### 访问凭证

Azure Blob Storage 使用存储账户名称和存储账户密钥进行身份验证：

1. 登录 Azure 门户
2. 创建存储账户
3. 在"访问密钥"中获取存储账户名称和密钥

> **安全提示**: 
> - 使用共享访问签名（SAS）替代存储账户密钥（如可能）
> - 不要将访问密钥提交到代码仓库
> - 使用 Azure Key Vault 管理密钥

### 访问层

Azure Blob Storage 支持三种访问层：

| 访问层 | 说明 | 适用场景 |
|--------|------|---------|
| **热** | 频繁访问的数据 | 活跃数据 |
| **冷** | 不经常访问的数据 | 备份数据 |
| **归档** | 很少访问的数据 | 长期归档 |

> **注意**: Azure Blob Storage 的访问层概念与其他云存储的存储类型类似，但配置方式不同。

## 功能特性

### 1. 连接字符串支持

Azure Blob Storage 支持使用连接字符串进行身份验证。组件通过 accessKeyId（存储账户名称）和 accessKeySecret（存储账户密钥）进行身份验证，连接字符串方式可通过 Azure SDK 直接使用。

### 2. 访问层自动设置

上传文件时，可以根据配置自动设置访问层（需在 Azure 门户或通过 SDK 配置生命周期策略）。

### 3. 共享访问签名（SAS）

Azure Blob Storage 支持使用 SAS（共享访问签名）进行临时授权访问，适用于生成临时 URL 供客户端直接上传或下载。

## 最佳实践

1. **区域选择**
   - 选择距离用户最近的区域，降低延迟
   - 考虑数据合规要求

2. **访问层选择**
   - 频繁访问：热访问层
   - 偶尔访问：冷访问层
   - 长期归档：归档访问层

3. **访问凭证管理**
   - 使用 SAS 替代存储账户密钥（如可能）
   - 使用 Azure Key Vault 管理密钥
   - 定期轮换访问密钥

4. **成本优化**
   - 使用生命周期策略自动转换访问层
   - 删除不需要的 Blob
   - 使用冷或归档访问层存储不常访问的数据

## 常见问题

### Q: Azure Blob Storage 与 AWS S3 有什么区别？

A: Azure Blob Storage 是 Azure 的对象存储服务，API 与 S3 不同，但功能类似。本组件提供了统一的接口，屏蔽了底层差异。

### Q: 如何配置访问层？

A: 可以在 Azure 门户设置容器的默认访问层，或通过生命周期策略自动转换。

### Q: 支持自定义域名吗？

A: 支持，在 Azure 门户配置自定义域名后，使用自定义域名作为 endpoint。

### Q: 如何从 S3 迁移到 Azure Blob？

A: 由于 API 不同，需要修改代码。但本组件提供了统一的接口，只需修改配置即可。

## 相关文档

- [核心存储组件 (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [Azure Blob Storage 官方文档](https://docs.microsoft.com/azure/storage/blobs/)
- [Azure Storage SDK for Java](https://github.com/Azure/azure-sdk-for-java/tree/main/sdk/storage)
- [直传策略 (DirectUploadPolicy)](../atlas-richie-component-storage-core/README.md#配置模型)
- [存储引擎 SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)

