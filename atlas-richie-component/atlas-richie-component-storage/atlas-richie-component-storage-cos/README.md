# Richie Component Storage - Tencent Cloud COS

## Overview

`richie-component-storage-cos` is the Tencent Cloud Object Storage (COS) implementation, built on the Tencent Cloud COS SDK to provide the full COS storage capability, including advanced features such as multi-AZ storage and image processing.

## Core Features

- ✅ **Tencent Cloud COS compatible** - full support for the Tencent Cloud COS API
- ✅ **Multi-AZ storage** - supports multi-AZ Standard, Infrequent Access, and Archive storage
- ✅ **Image processing** - supports image resizing, cropping, watermarking, and more
- ✅ **Multiple storage classes** - supports Standard, Infrequent Access, Archive, Cold Archive, and Deep Cold Archive
- ✅ **Resumable upload/download** - supports resumable transfer for large files
- ✅ **Dual-mode architecture** - supports both Auto-Init and Manual Registry initialization modes, flexibly adapting to Spring Boot auto-configuration and non-Spring environments
- ✅ **Auto-configuration** - Spring Boot auto-configuration

## Dual-Mode Architecture

This component supports two initialization modes:

### 1. Auto Mode (Auto-Init, default)

When `auto-init: true` (the default), Spring Boot auto-configuration handles:

- Creating the `CosStorageEngineProvider` Bean based on the `engine: TENCENT_COS` configuration
- Calling the Provider's `create(properties)` to build the engine instance
- The engine instance probes the bucket and validates the prefix through `@PostConstruct initializeBucket()`
- Injecting the engine for use via `@Qualifier("objectStorageEngine")`

### 2. Manual Mode (Manual)

When `auto-init: false`, the engine is discovered and managed by `StorageEngineRegistry` through SPI:

- The Provider is not auto-registered as a Bean; it is discovered by `ServiceLoader`
- Switch engines at runtime via `registry.switchEngine(StorageEngineEnum.TENCENT_COS)`
- Suitable for non-Spring environments or scenarios requiring dynamic engine switching

```java
// Manual mode: switch the engine at runtime
storageEngineRegistry.switchEngine(StorageEngineEnum.TENCENT_COS);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

Each implementation package provides a `StorageEngineProvider` SPI implementation. `CosStorageEngineProvider` is responsible for:

| Method | Description |
|------|------|
| `supportedEngineType()` | Returns `StorageEngineEnum.TENCENT_COS` |
| `create(properties)` | Creates the `COSClient` and `CosStorageEngine` from the configuration |
| `validate(properties)` | Validates that endpoint / accessKeyId / accessKeySecret / bucketName are required |
| `afterPropertiesSet(engine)` | Triggers bucket probing and prefix validation in manual mode |
| `destroy(engine)` | Releases client resources |

In auto mode, the Provider is registered as a Bean in `CosAutoConfiguration`; in manual mode, it is discovered by the Registry through SPI.

## Parameter Validation (ConfigValidation)

Before creating the engine, the `ConfigValidation` utility validates required parameters. If validation fails, an `IllegalArgumentException` is thrown:

| Parameter | Validation rule |
|------|---------|
| endpoint | Non-empty |
| accessKeyId | Non-empty |
| accessKeySecret | Non-empty |
| bucketName | Non-empty |

## Direct Upload Policy (DirectUploadPolicy)

The Tencent Cloud COS engine supports client-side direct upload to object storage through presigned URLs, reducing server-side traffic pressure:

| Field | Description |
|------|------|
| uploadUrl | Presigned upload URL |
| method | HTTP method (PUT) |
| headers | Signature headers |
| expireAt | Policy expiration time |
| success | Whether the policy is usable |

```java
DirectUploadPolicy policy = storageEngine.issueDirectUploadPolicy(
    "uploads/example.jpg", 3600);
// Returns a presigned PUT URL that the client can upload to directly
```

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-core</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-cos</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 2. Configuration

```yaml
platform:
  component:
    storage:
      object:
        # Storage engine type (required)
        engine: TENCENT_COS
        # COS access domain (required)
        # Format: cos.region.myqcloud.com
        # Example: cos.ap-guangzhou.myqcloud.com
        endpoint: cos.ap-guangzhou.myqcloud.com
        # Region (required)
        # Example: ap-guangzhou, ap-beijing, ap-shanghai, ap-chengdu
        region: ap-guangzhou
        # Access Key ID (SecretId) (required)
        accessKeyId: your-secret-id
        # Access Key (SecretKey) (required)
        accessKeySecret: your-secret-key
        # Bucket name (required)
        # Format: bucket-name-appid (appid is the Tencent Cloud account ID)
        bucketName: my-bucket-1234567890
        # Base path within the bucket (optional)
        basePath: /storage
        # Storage class (optional, default: STANDARD)
        # Allowed values: STANDARD, STANDARD_IA, ARCHIVE, COLD_ARCHIVE, DEEP_COLD_ARCHIVE,
        #         MULTI_AZ_STANDARD, MULTI_AZ_STANDARD_IA, MULTI_AZ_ARCHIVE,
        #         MULTI_AZ_COLD_ARCHIVE, MULTI_AZ_DEEP_COLD_ARCHIVE, MULTI_AZ_INTELLIGENT_TIERING
        storageType: STANDARD
```

### 3. Usage

Inject `StorageEngine` (the Bean name is `objectStorageEngine`) to start using it:

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    @Qualifier("objectStorageEngine")
    private final StorageEngine storageEngine;
    
    public void uploadFile(String key, File file) {
        UploadResponse response = storageEngine.putObject(key, file);
        if (response.isSuccess()) {
            log.info("Upload succeeded: {}", response.getUrl());
        }
    }
}
```

## Configuration Reference

### ⚠️ Important Configuration Differences

The main configuration differences between Tencent Cloud COS and other cloud storage services:

| Configuration | Tencent Cloud COS | Alibaba Cloud OSS | AWS S3 |
|--------|-----------|-----------|--------|
| **engine value** | `TENCENT_COS` | `ALIYUN_OSS` | `AWS_S3` |
| **endpoint format** | `cos.region.myqcloud.com` | `oss-cn-region.aliyuncs.com` | `s3.region.amazonaws.com` |
| **region format** | `ap-region` | `cn-region` | `us-east-1` |
| **Credential name** | SecretId / SecretKey | AccessKey ID / AccessKey Secret | Access Key ID / Secret Access Key |
| **bucketName format** | `bucket-name-appid` | `bucket-name` | `bucket-name` |
| **Storage classes** | 11 (including multi-AZ) | 4 | 15+ |
| **Multi-AZ storage** | ✅ Supported | ❌ Not supported | ❌ Not supported |

### endpoint Configuration

The Tencent Cloud COS endpoint format:

- **Standard format**: `cos.region.myqcloud.com`
  - Example: `cos.ap-guangzhou.myqcloud.com`
  - Example: `cos.ap-beijing.myqcloud.com`
  - Example: `cos.ap-shanghai.myqcloud.com`

- **Internal endpoint**: `cos-internal.region.myqcloud.com`
  - Suitable for CVM access from the same region, traffic-free
  - Example: `cos-internal.ap-guangzhou.myqcloud.com`

- **Custom domain**: A custom domain can be bound in the COS console

### region Configuration

Regions supported by Tencent Cloud COS:

| Region | Code |
|------|------|
| Guangzhou | `ap-guangzhou` |
| Beijing | `ap-beijing` |
| Shanghai | `ap-shanghai` |
| Chengdu | `ap-chengdu` |
| Chongqing | `ap-chongqing` |
| Nanjing | `ap-nanjing` |
| Hong Kong (China) | `ap-hongkong` |
| Singapore | `ap-singapore` |
| Mumbai | `ap-mumbai` |
| Seoul | `ap-seoul` |
| Tokyo | `ap-tokyo` |
| Silicon Valley | `na-siliconvalley` |
| Virginia | `na-ashburn` |
| Frankfurt | `eu-frankfurt` |

### Storage Classes

Storage classes supported by Tencent Cloud COS (including multi-AZ):

| Storage class | Description | Use case |
|---------|------|---------|
| `STANDARD` | Standard storage | Frequently accessed data |
| `STANDARD_IA` | Infrequent access storage | Data accessed infrequently but requiring rapid access |
| `ARCHIVE` | Archive storage | Long-term retention, rarely accessed data |
| `COLD_ARCHIVE` | Cold archive storage | Very long-term retention, extremely rarely accessed data |
| `DEEP_COLD_ARCHIVE` | Deep cold archive storage | Very long-term retention, almost never accessed data |
| `MULTI_AZ_STANDARD` | Multi-AZ standard storage | Frequently accessed data requiring high availability |
| `MULTI_AZ_STANDARD_IA` | Multi-AZ infrequent access storage | Occasionally accessed data requiring high availability |
| `MULTI_AZ_ARCHIVE` | Multi-AZ archive storage | Archive data requiring high availability |
| `MULTI_AZ_COLD_ARCHIVE` | Multi-AZ cold archive storage | Cold archive data requiring high availability |
| `MULTI_AZ_DEEP_COLD_ARCHIVE` | Multi-AZ deep cold archive storage | Deep cold archive data requiring high availability |
| `MULTI_AZ_INTELLIGENT_TIERING` | Multi-AZ intelligent tiering storage | Data with unknown access patterns requiring high availability |

> **Note**: Multi-AZ storage provides higher availability and data durability, but at a slightly higher cost.

### bucketName Configuration

Tencent Cloud COS bucket names have a special format and must include the AppID:

- **Format**: `bucket-name-appid`
- **Example**: `my-bucket-1234567890`
- **Where to find AppID**: View your account information in the top-right corner of the Tencent Cloud console

### Access Credentials

Tencent Cloud COS uses SecretId and SecretKey for authentication:

1. Sign in to the Tencent Cloud console
2. Open Cloud Access Management (CAM)
3. Create a sub-user and grant COS access permissions
4. Create an access key

> **Security tips**:
> - Use a CAM sub-user and follow the principle of least privilege
> - Do not commit access keys to the code repository
> - Use environment variables or a secret management service (e.g., Tencent Cloud KMS)

## Features

### 1. Multi-AZ Storage

Tencent Cloud COS supports multi-AZ storage, providing higher availability and data durability:

```yaml
platform:
  component:
    storage:
      object:
        storageType: MULTI_AZ_STANDARD  # Multi-AZ standard storage
```

### 2. Image Processing

Tencent Cloud COS supports image processing features (must be enabled in the console):

- **Format conversion**: JPEG, PNG, WEBP, GIF, and more
- **Resizing**: by ratio, by dimension, or by long/short edge
- **Cropping**: rectangular, circular, rounded rectangle
- **Watermarking**: image watermark, text watermark
- **Rotation**: automatic, manual
- **Quality adjustment**: JPEG/WebP quality compression

### 3. Automatic Storage Class Conversion

When uploading a file, the object storage class is set automatically based on the configured `storageType`:

```java
// Configure the MULTI_AZ_STANDARD storage class
UploadResponse response = storageEngine.putObject("file.txt", file);
// The file is automatically set to the multi-AZ standard storage class
```

### 4. Internal Network Access

If your application runs on Tencent Cloud CVM, you can use the internal endpoint to avoid traffic charges:

```yaml
platform:
  component:
    storage:
      object:
        endpoint: cos-internal.ap-guangzhou.myqcloud.com  # Internal endpoint
```

## Best Practices

1. **Region selection**
   - Choose the region closest to your users to reduce latency
   - Consider data compliance requirements

2. **Storage class selection**
   - Frequently accessed: `STANDARD` or `MULTI_AZ_STANDARD`
   - Occasionally accessed: `STANDARD_IA` or `MULTI_AZ_STANDARD_IA`
   - Long-term archive: `ARCHIVE` or `MULTI_AZ_ARCHIVE`
   - High availability required: choose multi-AZ storage classes

3. **bucketName configuration**
   - Must include the AppID, format: `bucket-name-appid`
   - The AppID can be found in the Tencent Cloud console

4. **Access credential management**
   - Use CAM sub-users and follow the principle of least privilege
   - Use environment variables or a secret management service
   - Rotate access keys regularly

5. **Cost optimization**
   - Use lifecycle policies to transition storage classes automatically
   - Remove unnecessary objects
   - Use internal endpoints to avoid traffic charges

## FAQ

### Q: What is the AppID in the bucketName?

A: The AppID is the unique identifier of your Tencent Cloud account and can be found in the top-right corner of the console. The bucket name must include the AppID, in the format `bucket-name-appid`.

### Q: How do I configure image processing?

A: Enable image processing in the COS console, then configure processing parameters through `ImageOptions`.

### Q: Are custom domains supported?

A: Yes. After binding a custom domain in the COS console, use that custom domain as the endpoint.

### Q: What are the advantages of multi-AZ storage?

A: Multi-AZ storage provides higher availability (99.995%) and data durability (99.999999999%), making it ideal for scenarios with strict availability requirements.

### Q: What are the advantages of internal network access?

A: Internal network access is traffic-free and has lower latency, but it is limited to CVM in the same region.

## Related Documentation

- [Core Storage Component (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [Tencent Cloud COS Official Documentation](https://cloud.tencent.com/document/product/436)
- [COS Java SDK](https://cloud.tencent.com/document/product/436/10199)
- [COS Image Processing](https://cloud.tencent.com/document/product/436/44880)
- [Direct Upload Policy (DirectUploadPolicy)](../atlas-richie-component-storage-core/README.md#configuration-model)
- [Storage Engine SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)
