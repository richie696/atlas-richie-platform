# Richie Component Storage - Volcengine TOS

## Overview

`richie-component-storage-tos` is the implementation of Volcengine Object Storage Service (TOS), built on the Volcengine TOS SDK to deliver complete TOS storage capabilities. TOS is compatible with the AWS S3 API and supports advanced features such as image processing and video processing.

## Core Features

- ✅ **S3 Compatible** - Compatible with the AWS S3 API
- ✅ **Image Processing** - Image resizing, cropping, watermarking, and more
- ✅ **Multiple Storage Tiers** - Standard, Infrequent Access, Archive, and Cold Archive
- ✅ **Resumable Upload** - Resumable upload support for large files
- ✅ **Dual-Mode Architecture** - Supports both Auto-Init and Manual Registry initialization modes, flexibly adapting to Spring Boot auto-configuration and non-Spring environments
- ✅ **Auto-Configuration** - Spring Boot auto-configuration

## Dual-Mode Architecture

This component supports two initialization modes:

### 1. Auto Mode (Auto-Init, Default)

When `auto-init: true` (the default value), Spring Boot auto-configuration handles:

- Creating the `TosStorageEngineProvider` Bean based on the `engine: VOLCENGINE_TOS` configuration
- Calling the Provider's `create(properties)` to create the engine instance
- Injecting and using the engine through `@Qualifier("objectStorageEngine")`

### 2. Manual Mode

When `auto-init: false`, the engine is discovered and managed by `StorageEngineRegistry` through SPI:

- The Provider is not auto-registered as a Bean, but is discovered by `ServiceLoader`
- Switch engines at runtime via `registry.switchEngine(StorageEngineEnum.VOLCENGINE_TOS)`
- Suitable for non-Spring environments or scenarios that require dynamic engine switching

```java
// Manual mode: switch engines at runtime
storageEngineRegistry.switchEngine(StorageEngineEnum.VOLCENGINE_TOS);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

Each implementation package provides a `StorageEngineProvider` SPI implementation. `TosStorageEngineProvider` is responsible for:

| Method | Description |
|------|------|
| `supportedEngineType()` | Returns `StorageEngineEnum.VOLCENGINE_TOS` |
| `create(properties)` | Creates an engine instance from configuration |
| `validate(properties)` | Validates that endpoint / accessKeyId / accessKeySecret / bucketName are required |
| `destroy(engine)` | Releases resources |

In auto mode, the Provider is registered as a Bean in `TosAutoConfiguration`. In manual mode, it is discovered by the Registry through SPI.

## Config Validation

Before the engine is created, the `ConfigValidation` utility validates the required parameters. If validation fails, an `IllegalArgumentException` is thrown:

| Parameter | Validation Rule |
|------|---------|
| endpoint | Non-empty |
| accessKeyId | Non-empty |
| accessKeySecret | Non-empty |
| bucketName | Non-empty |

## Direct Upload Policy

The TOS engine supports client-side direct upload to object storage through presigned URLs, reducing server-side traffic pressure:

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
// Returns a presigned PUT URL; the client can upload directly
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
    <artifactId>atlas-richie-component-storage-tos</artifactId>
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
        engine: VOLCENGINE_TOS
        # TOS access endpoint (required)
        # Format: tos-cn-region.volces.com
        # Example: tos-cn-beijing.volces.com
        endpoint: tos-cn-beijing.volces.com
        # Region (required)
        # Example: cn-beijing, cn-shanghai, ap-singapore
        region: cn-beijing
        # Access Key ID (required)
        accessKeyId: your-access-key-id
        # Secret Access Key (required)
        accessKeySecret: your-secret-access-key
        # Bucket name (required)
        bucketName: my-bucket
        # Base path inside the bucket (optional)
        basePath: /storage
        # Storage tier (optional, default: STANDARD)
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
            log.info("Upload successful: {}", response.getUrl());
        }
    }
    
    // Upload an image with processing
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

The main configuration differences between Volcengine TOS and other cloud storage providers:

| Configuration Item | Volcengine TOS | AWS S3 | Alibaba Cloud OSS |
|--------|-------------|--------|-----------|
| **engine value** | `VOLCENGINE_TOS` | `AWS_S3` | `ALIYUN_OSS` |
| **endpoint format** | `tos-cn-region.volces.com` | `s3.region.amazonaws.com` | `oss-cn-region.aliyuncs.com` |
| **region format** | `cn-region` or `ap-region` | `us-east-1` | `cn-region` |
| **Access key names** | Access Key ID / Secret Access Key | Access Key ID / Secret Access Key | AccessKey ID / AccessKey Secret |
| **S3 compatibility** | ✅ Compatible | ✅ Native | ❌ Not compatible |
| **Image processing** | ✅ Supported | ❌ Not supported | ✅ Supported |
| **Storage tiers** | 4 (Standard, IA, Archive, Cold Archive) | 15+ | 4 |

### endpoint Configuration

Volcengine TOS endpoint format:

- **Standard format**: `tos-cn-region.volces.com`
  - Example: `tos-cn-beijing.volces.com`
  - Example: `tos-cn-shanghai.volces.com`
  - Example: `tos-ap-singapore.volces.com`

- **Internal endpoint**: `tos-cn-region-internal.volces.com`
  - Suitable for same-region ECS access, no traffic fees
  - Example: `tos-cn-beijing-internal.volces.com`

### region Configuration

Regions supported by Volcengine TOS:

| Region | Code |
|------|------|
| CN North 2 (Beijing) | `cn-beijing` |
| CN East 2 (Shanghai) | `cn-shanghai` |
| CN South 1 (Guangzhou) | `cn-guangzhou` |
| CN East 1 (Hangzhou) | `cn-hangzhou` |
| AP (Singapore) | `ap-singapore` |
| AP (Jakarta) | `ap-jakarta` |
| US (Silicon Valley) | `us-east-1` |
| US (Virginia) | `us-west-1` |

### Storage Tiers

Storage tiers supported by Volcengine TOS:

| Storage Tier | Description | Suitable Scenario |
|---------|------|---------|
| `STANDARD` | Standard storage | Frequently accessed data |
| `STANDARD_IA` | Infrequent access storage | Data that is not accessed often but requires fast retrieval |
| `ARCHIVE` | Archive storage | Long-term retention, rarely accessed data |
| `COLD_ARCHIVE` | Cold archive storage | Very long-term retention, almost never accessed data |

### Access Credentials

Volcengine TOS uses Access Key ID and Secret Access Key for authentication:

1. Sign in to the Volcengine console
2. Open the Identity and Access Management (IAM) service
3. Create a user and grant TOS access permissions
4. Create access keys

> **Security Tips**: 
> - Use IAM sub-users and follow the principle of least privilege
> - Do not commit access keys to source control
> - Use environment variables or a secrets management service

## Feature Details

### 1. S3 Compatibility

Volcengine TOS is compatible with the AWS S3 API, enabling seamless migration:

```java
// Code that uses TOS is essentially the same as code that uses S3
UploadResponse response = storageEngine.putObject("file.txt", file);
```

### 2. Image Processing

Volcengine TOS supports image processing (must be enabled in the console):

- **Format conversion**: JPEG, PNG, WEBP, GIF, etc.
- **Resizing**: By ratio, by dimensions, by long/short edge
- **Cropping**: Rectangle, circle, rounded rectangle
- **Watermarks**: Image watermark, text watermark
- **Rotation**: Auto rotation, manual rotation
- **Quality adjustment**: JPEG/WebP quality compression

```java
ImageOptions options = ImageOptions.builder()
    .format(ImageFormat.WEBP)           // Convert to WEBP format
    .quality(80)                         // Quality 80%
    .resize(800, 600)                    // Resize to 800x600
    .watermark("text", "Richie")        // Add text watermark
    .build();

UploadResponse response = storageEngine.putImage("image.jpg", file, options);
```

### 3. Automatic Storage Tier Conversion

When uploading files, the configured `storageType` automatically sets the object's storage tier:

```java
// Configure as ARCHIVE storage tier
UploadResponse response = storageEngine.putObject("backup.zip", file);
// The file is automatically stored as the Archive storage tier
```

### 4. Internal Network Access

If your application is deployed on Volcengine ECS, you can use the internal endpoint to avoid traffic fees:

```yaml
platform:
  component:
    storage:
      object:
        endpoint: tos-cn-beijing-internal.volces.com  # Internal endpoint
```

## Best Practices

1. **Region Selection**
   - Choose the region closest to your users to minimize latency
   - Consider data compliance requirements

2. **Storage Tier Selection**
   - Frequent access: `STANDARD`
   - Occasional access: `STANDARD_IA`
   - Long-term archiving: `ARCHIVE` or `COLD_ARCHIVE`

3. **Image Processing**
   - Perform format conversion and compression on upload to save storage space
   - Use a CDN to accelerate image access
   - Return different image sizes based on device type

4. **Access Credential Management**
   - Use IAM sub-users and follow the principle of least privilege
   - Use environment variables or a secrets management service
   - Rotate access keys regularly

5. **Cost Optimization**
   - Use lifecycle policies to automatically transition storage tiers
   - Delete unnecessary objects
   - Use the internal endpoint to avoid traffic fees

## FAQ

### Q: What is the difference between TOS and AWS S3?

A: TOS is an S3-compatible object storage service. Its API is essentially the same as S3, but storage tiers and supported features differ slightly.

### Q: How do I migrate from S3 to TOS?

A: Because the API is compatible, you only need to change the endpoint and access credentials; the code essentially remains unchanged.

### Q: How do I configure image processing?

A: Enable image processing in the TOS console, then configure processing parameters through `ImageOptions`.

### Q: Does it support custom domains?

A: Yes. After binding a custom domain in the TOS console, use the custom domain as the endpoint.

### Q: What are the benefits of internal network access?

A: Internal network access is free of traffic fees and offers lower latency, but is limited to same-region ECS access.

## Related Documentation

- [Core Storage Component (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [Volcengine TOS Official Documentation](https://www.volcengine.com/docs/6349)
- [TOS Java SDK](https://www.volcengine.com/docs/6349/1099475)
- [Direct Upload Policy (DirectUploadPolicy)](../atlas-richie-component-storage-core/README.md#配置模型)
- [Storage Engine SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)