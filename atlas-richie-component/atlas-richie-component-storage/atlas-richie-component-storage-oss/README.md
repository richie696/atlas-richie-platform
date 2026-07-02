# Richie Component Storage - Alibaba Cloud OSS

## Overview

`richie-component-storage-oss` is the Alibaba Cloud Object Storage Service (OSS) implementation, built on the Alibaba Cloud OSS SDK to provide the full OSS storage capability, including advanced features such as image and video processing.

## Core Features

- ✅ **Alibaba Cloud OSS compatible** - full support for the Alibaba Cloud OSS API
- ✅ **Image processing** - supports image resizing, cropping, watermarking, and more
- ✅ **Multiple storage classes** - supports Standard, Infrequent Access, Archive, and Cold Archive
- ✅ **Resumable upload/download** - supports resumable transfer for large files
- ✅ **Dual-mode architecture** - supports both Auto-Init and Manual Registry initialization modes, flexibly adapting to Spring Boot auto-configuration and non-Spring environments
- ✅ **Auto-configuration** - Spring Boot auto-configuration

## Dual-Mode Architecture

This component supports two initialization modes:

### 1. Auto Mode (Auto-Init, default)

When `auto-init: true` (the default), Spring Boot auto-configuration handles:

- Creating the `OssStorageEngineProvider` Bean based on the `engine: ALIYUN_OSS` configuration
- Calling the Provider's `create(properties)` to build the engine instance
- The engine instance probes the bucket and validates the prefix through `@PostConstruct initializeBucket()`
- Injecting the engine for use via `@Qualifier("objectStorageEngine")`

### 2. Manual Mode (Manual)

When `auto-init: false`, the engine is discovered and managed by `StorageEngineRegistry` through SPI:

- The Provider is not auto-registered as a Bean; it is discovered by `ServiceLoader`
- Switch engines at runtime via `registry.switchEngine(StorageEngineEnum.ALIYUN_OSS)`
- Suitable for non-Spring environments or scenarios requiring dynamic engine switching

```java
// Manual mode: switch the engine at runtime
storageEngineRegistry.switchEngine(StorageEngineEnum.ALIYUN_OSS);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

Each implementation package provides a `StorageEngineProvider` SPI implementation. `OssStorageEngineProvider` is responsible for:

| Method | Description |
|------|------|
| `supportedEngineType()` | Returns `StorageEngineEnum.ALIYUN_OSS` |
| `create(properties)` | Creates the `OSSClient` and `OssStorageEngine` from the configuration |
| `validate(properties)` | Validates that endpoint / accessKeyId / accessKeySecret / bucketName are required |
| `afterPropertiesSet(engine)` | Triggers bucket probing and prefix validation in manual mode |
| `destroy(engine)` | Releases client resources |

In auto mode, the Provider is registered as a Bean in `OssAutoConfiguration`; in manual mode, it is discovered by the Registry through SPI.

## Parameter Validation (ConfigValidation)

Before creating the engine, the `ConfigValidation` utility validates required parameters. If validation fails, an `IllegalArgumentException` is thrown:

| Parameter | Validation rule |
|------|---------|
| endpoint | Non-empty |
| accessKeyId | Non-empty |
| accessKeySecret | Non-empty |
| bucketName | Non-empty |

## Direct Upload Policy (DirectUploadPolicy)

The Alibaba Cloud OSS engine supports client-side direct upload to object storage through presigned URLs, reducing server-side traffic pressure:

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
<!-- Required core library -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-core</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
<!-- Implementation library -->
<dependency>
<groupId>com.richie.component</groupId>
<artifactId>atlas-richie-component-storage-oss</artifactId>
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
        engine: ALIYUN_OSS
        # OSS access domain (required)
        # Format: oss-cn-region.aliyuncs.com
        # Example: oss-cn-hangzhou.aliyuncs.com
        endpoint: oss-cn-hangzhou.aliyuncs.com
        # Region (required)
        # Example: cn-hangzhou, cn-beijing, cn-shanghai, cn-shenzhen
        region: cn-hangzhou
        # AccessKey ID (required)
        accessKeyId: your-access-key-id
        # AccessKey Secret (required)
        accessKeySecret: your-access-key-secret
        # Bucket name (required)
        bucketName: my-bucket
        # Base path within the bucket (optional)
        basePath: /storage
        # Storage class (optional, default: STANDARD)
        # Allowed values: STANDARD, STANDARD_IA, ARCHIVE, COLD_ARCHIVE
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
    
    // Upload an image and process it
    public void uploadImage(String key, File imageFile) {
        ImageOptions options = ImageOptions.builder()
            .format(ImageFormat.WEBP)
            .quality(80)
            .resize(800, 600)
            .build();
        UploadResponse response = storageEngine.putImage(key, imageFile, options);
    }
}
```

## Configuration Reference

### ⚠️ Important Configuration Differences

The main configuration differences between Alibaba Cloud OSS and other cloud storage services:

| Configuration | Alibaba Cloud OSS | AWS S3 | Tencent Cloud COS |
|--------|-----------|--------|-----------|
| **engine value** | `ALIYUN_OSS` | `AWS_S3` | `TENCENT_COS` |
| **endpoint format** | `oss-cn-region.aliyuncs.com` | `s3.region.amazonaws.com` | `cos.region.myqcloud.com` |
| **region format** | `cn-region` | `us-east-1` | `ap-region` |
| **Credential name** | AccessKey ID / AccessKey Secret | Access Key ID / Secret Access Key | SecretId / SecretKey |
| **Storage classes** | 4 (Standard, IA, Archive, Cold Archive) | 15+ | 10+ |
| **Image processing** | ✅ Supported (built-in) | ❌ Not supported | ✅ Supported (requires configuration) |

### endpoint Configuration

The Alibaba Cloud OSS endpoint format:

- **Standard format**: `oss-cn-region.aliyuncs.com`
  - Example: `oss-cn-hangzhou.aliyuncs.com`
  - Example: `oss-cn-beijing.aliyuncs.com`
  - Example: `oss-cn-shanghai.aliyuncs.com`

- **Internal endpoint**: `oss-cn-region-internal.aliyuncs.com`
  - Suitable for ECS access from the same region, traffic-free
  - Example: `oss-cn-hangzhou-internal.aliyuncs.com`

- **Custom domain**: A custom domain can be bound in the OSS console

### region Configuration

Regions supported by Alibaba Cloud OSS:

| Region | Code |
|------|------|
| China East 1 (Hangzhou) | `cn-hangzhou` |
| China East 2 (Shanghai) | `cn-shanghai` |
| China North 1 (Qingdao) | `cn-qingdao` |
| China North 2 (Beijing) | `cn-beijing` |
| China North 3 (Zhangjiakou) | `cn-zhangjiakou` |
| China North 5 (Hohhot) | `cn-huhehaote` |
| China South 1 (Shenzhen) | `cn-shenzhen` |
| China South 2 (Heyuan) | `cn-heyuan` |
| China Southwest 1 (Chengdu) | `cn-chengdu` |
| China (Hong Kong) | `cn-hongkong` |
| US (Silicon Valley) | `us-west-1` |
| US (Virginia) | `us-east-1` |
| Singapore | `ap-southeast-1` |
| Australia (Sydney) | `ap-southeast-2` |
| Japan (Tokyo) | `ap-northeast-1` |
| India (Mumbai) | `ap-south-1` |
| Germany (Frankfurt) | `eu-central-1` |
| UK (London) | `eu-west-1` |
| UAE (Dubai) | `me-east-1` |

### Storage Classes

Storage classes supported by Alibaba Cloud OSS:

| Storage class | Description | Use case |
|---------|------|---------|
| `STANDARD` | Standard storage | Frequently accessed data |
| `STANDARD_IA` | Infrequent access storage | Data accessed infrequently but requiring rapid access |
| `ARCHIVE` | Archive storage | Long-term retention, rarely accessed data |
| `COLD_ARCHIVE` | Cold archive storage | Very long-term retention, extremely rarely accessed data |

### Access Credentials

Alibaba Cloud OSS uses AccessKey ID and AccessKey Secret for authentication:

1. Sign in to the Alibaba Cloud console
2. Open Resource Access Management (RAM)
3. Create a user and grant OSS access permissions
4. Create an access key

> **Security tips**:
> - Use a RAM sub-account and follow the principle of least privilege
> - Do not commit access keys to the code repository
> - Use environment variables or a secret management service (e.g., Alibaba Cloud KMS)

## Features

### 1. Image Processing

Alibaba Cloud OSS has built-in image processing capabilities and supports:

- **Format conversion**: JPEG, PNG, WEBP, GIF, and more
- **Resizing**: by ratio, by dimension, or by long/short edge
- **Cropping**: rectangular, circular, rounded rectangle
- **Watermarking**: image watermark, text watermark
- **Rotation**: automatic, manual
- **Quality adjustment**: JPEG/WebP quality compression

```java
ImageOptions options = ImageOptions.builder()
    .format(ImageFormat.WEBP)           // Convert to WEBP format
    .quality(80)                         // Quality 80%
    .resize(800, 600)                    // Resize to 800x600
    .watermark("text", "Richie")        // Add a text watermark
    .build();

UploadResponse response = storageEngine.putImage("image.jpg", file, options);
```

### 2. Automatic Storage Class Conversion

When uploading a file, the object storage class is set automatically based on the configured `storageType`:

```java
// Configure the ARCHIVE storage class
UploadResponse response = storageEngine.putObject("backup.zip", file);
// The file is automatically set to the archive storage class
```

### 3. Internal Network Access

If your application runs on Alibaba Cloud ECS, you can use the internal endpoint to avoid traffic charges:

```yaml
platform:
  component:
    storage:
      object:
        endpoint: oss-cn-hangzhou-internal.aliyuncs.com  # Internal endpoint
```

## Best Practices

1. **Region selection**
   - Choose the region closest to your users to reduce latency
   - Consider data compliance requirements

2. **Storage class selection**
   - Frequently accessed: `STANDARD`
   - Occasionally accessed: `STANDARD_IA`
   - Long-term archive: `ARCHIVE` or `COLD_ARCHIVE`

3. **Image processing**
   - Convert formats and compress at upload time to save storage space
   - Use CDN to accelerate image access
   - Return different image sizes based on device type

4. **Access credential management**
   - Use RAM sub-accounts and follow the principle of least privilege
   - Use environment variables or a secret management service
   - Rotate access keys regularly

5. **Cost optimization**
   - Use lifecycle policies to transition storage classes automatically
   - Remove unnecessary objects
   - Use internal endpoints to avoid traffic charges

## FAQ

### Q: How do I configure image processing?

A: Enable image processing in the OSS console, then configure processing parameters through `ImageOptions`.

### Q: Are custom domains supported?

A: Yes. After binding a custom domain in the OSS console, use that custom domain as the endpoint.

### Q: How do I set object access permissions?

A: You can set bucket and object access permissions in the OSS console, or set the ACL through the SDK.

### Q: What are the advantages of internal network access?

A: Internal network access is traffic-free and has lower latency, but it is limited to ECS in the same region.

## Related Documentation

- [Core Storage Component (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [Alibaba Cloud OSS Official Documentation](https://help.aliyun.com/product/31815.html)
- [OSS Java SDK](https://help.aliyun.com/document_detail/32011.html)
- [OSS Image Processing](https://help.aliyun.com/document_detail/44688.html)
- [Direct Upload Policy (DirectUploadPolicy)](../atlas-richie-component-storage-core/README.md#configuration-model)
- [Storage Engine SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)
