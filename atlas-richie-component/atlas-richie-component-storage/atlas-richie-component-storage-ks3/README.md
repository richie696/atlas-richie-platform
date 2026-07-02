# Richie Component Storage - KSYUN KS3

## Overview

`richie-component-storage-ks3` is the implementation of KSYUN Object Storage Service (KS3), built on the KSYUN KS3 SDK to deliver complete KS3 storage capabilities. KS3 is compatible with the AWS S3 API and supports multiple storage tiers, including Standard, Infrequent Access, and Archive.

## Core Features

- ✅ **S3 Compatible** - Compatible with the AWS S3 API
- ✅ **Multiple Storage Tiers** - Standard, Infrequent Access, and Archive
- ✅ **Resumable Upload** - Resumable upload support for large files
- ✅ **Dual-Mode Architecture** - Supports both Auto-Init and Manual Registry initialization modes, flexibly adapting to Spring Boot auto-configuration and non-Spring environments
- ✅ **Auto-Configuration** - Spring Boot auto-configuration

## Dual-Mode Architecture

This component supports two initialization modes:

### 1. Auto Mode (Auto-Init, Default)

When `auto-init: true` (the default value), Spring Boot auto-configuration handles:

- Creating the `Ks3StorageEngineProvider` Bean based on the `engine: KSYUN_KS3` configuration
- Calling the Provider's `create(properties)` to create the engine instance
- Injecting and using the engine through `@Qualifier("objectStorageEngine")`

### 2. Manual Mode

When `auto-init: false`, the engine is discovered and managed by `StorageEngineRegistry` through SPI:

- The Provider is not auto-registered as a Bean, but is discovered by `ServiceLoader`
- Switch engines at runtime via `registry.switchEngine(StorageEngineEnum.KSYUN_KS3)`
- Suitable for non-Spring environments or scenarios that require dynamic engine switching

```java
// Manual mode: switch engines at runtime
storageEngineRegistry.switchEngine(StorageEngineEnum.KSYUN_KS3);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

Each implementation package provides a `StorageEngineProvider` SPI implementation. `Ks3StorageEngineProvider` is responsible for:

| Method | Description |
|------|------|
| `supportedEngineType()` | Returns `StorageEngineEnum.KSYUN_KS3` |
| `create(properties)` | Creates an engine instance from configuration |
| `validate(properties)` | Validates that endpoint / accessKeyId / accessKeySecret / bucketName are required |
| `destroy(engine)` | Releases resources |

In auto mode, the Provider is registered as a Bean in `Ks3AutoConfiguration`. In manual mode, it is discovered by the Registry through SPI.

## Config Validation

Before the engine is created, the `ConfigValidation` utility validates the required parameters. If validation fails, an `IllegalArgumentException` is thrown:

| Parameter | Validation Rule |
|------|---------|
| endpoint | Non-empty |
| accessKeyId | Non-empty |
| accessKeySecret | Non-empty |
| bucketName | Non-empty |

## Direct Upload Policy

The KS3 engine supports client-side direct upload to object storage through presigned URLs, reducing server-side traffic pressure:

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
    <artifactId>atlas-richie-component-storage-ks3</artifactId>
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
        engine: KSYUN_KS3
        # KS3 access endpoint (required)
        # Format: ks3-cn-region.ksyuncs.com
        # Example: ks3-cn-beijing.ksyuncs.com
        endpoint: ks3-cn-beijing.ksyuncs.com
        # Region (required)
        # Example: cn-beijing, cn-shanghai, cn-guangzhou
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
        # Allowed values: STANDARD, STANDARD_IA, ARCHIVE
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
}
```

## Configuration Reference

### ⚠️ Important Configuration Differences

The main configuration differences between KSYUN KS3 and other cloud storage providers:

| Configuration Item | KSYUN KS3 | AWS S3 | Alibaba Cloud OSS |
|--------|-----------|--------|-----------|
| **engine value** | `KSYUN_KS3` | `AWS_S3` | `ALIYUN_OSS` |
| **endpoint format** | `ks3-cn-region.ksyuncs.com` | `s3.region.amazonaws.com` | `oss-cn-region.aliyuncs.com` |
| **region format** | `cn-region` | `us-east-1` | `cn-region` |
| **Access key names** | Access Key ID / Secret Access Key | Access Key ID / Secret Access Key | AccessKey ID / AccessKey Secret |
| **S3 compatibility** | ✅ Compatible | ✅ Native | ❌ Not compatible |
| **Storage tiers** | 3 (Standard, IA, Archive) | 15+ | 4 |

### endpoint Configuration

KSYUN KS3 endpoint format:

- **Standard format**: `ks3-cn-region.ksyuncs.com`
  - Example: `ks3-cn-beijing.ksyuncs.com`
  - Example: `ks3-cn-shanghai.ksyuncs.com`
  - Example: `ks3-cn-guangzhou.ksyuncs.com`

- **Internal endpoint**: `ks3-cn-region-internal.ksyuncs.com`
  - Suitable for same-region KEC access, no traffic fees
  - Example: `ks3-cn-beijing-internal.ksyuncs.com`

### region Configuration

Regions supported by KSYUN KS3:

| Region | Code |
|------|------|
| Beijing | `cn-beijing` |
| Shanghai | `cn-shanghai` |
| Guangzhou | `cn-guangzhou` |
| Hangzhou | `cn-hangzhou` |
| Hong Kong | `cn-hongkong` |
| Russia | `ru-moscow` |
| Singapore | `ap-singapore` |

### Storage Tiers

Storage tiers supported by KSYUN KS3:

| Storage Tier | Description | Suitable Scenario |
|---------|------|---------|
| `STANDARD` | Standard storage | Frequently accessed data |
| `STANDARD_IA` | Infrequent access storage | Data that is not accessed often but requires fast retrieval |
| `ARCHIVE` | Archive storage | Long-term retention, rarely accessed data |

### Access Credentials

KSYUN KS3 uses Access Key ID and Secret Access Key for authentication:

1. Sign in to the KSYUN console
2. Open the Identity and Access Management (IAM) service
3. Create a user and grant KS3 access permissions
4. Create access keys

> **Security Tips**: 
> - Use IAM sub-users and follow the principle of least privilege
> - Do not commit access keys to source control
> - Use environment variables or a secrets management service

## Feature Details

### 1. S3 Compatibility

KSYUN KS3 is compatible with the AWS S3 API, enabling seamless migration:

```java
// Code that uses KS3 is essentially the same as code that uses S3
UploadResponse response = storageEngine.putObject("file.txt", file);
```

### 2. Automatic Storage Tier Conversion

When uploading files, the configured `storageType` automatically sets the object's storage tier:

```java
// Configure as ARCHIVE storage tier
UploadResponse response = storageEngine.putObject("backup.zip", file);
// The file is automatically stored as the Archive storage tier
```

### 3. Internal Network Access

If your application is deployed on KSYUN KEC, you can use the internal endpoint to avoid traffic fees:

```yaml
platform:
  component:
    storage:
      object:
        endpoint: ks3-cn-beijing-internal.ksyuncs.com  # Internal endpoint
```

## Best Practices

1. **Region Selection**
   - Choose the region closest to your users to minimize latency
   - Consider data compliance requirements

2. **Storage Tier Selection**
   - Frequent access: `STANDARD`
   - Occasional access: `STANDARD_IA`
   - Long-term archiving: `ARCHIVE`

3. **Access Credential Management**
   - Use IAM sub-users and follow the principle of least privilege
   - Use environment variables or a secrets management service
   - Rotate access keys regularly

4. **Cost Optimization**
   - Use lifecycle policies to automatically transition storage tiers
   - Delete unnecessary objects
   - Use the internal endpoint to avoid traffic fees

## FAQ

### Q: What is the difference between KS3 and AWS S3?

A: KS3 is an S3-compatible object storage service. Its API is essentially the same as S3, but storage tiers and supported features differ slightly.

### Q: How do I migrate from S3 to KS3?

A: Because the API is compatible, you only need to change the endpoint and access credentials; the code essentially remains unchanged.

### Q: Does it support custom domains?

A: Yes. After binding a custom domain in the KS3 console, use the custom domain as the endpoint.

### Q: What are the benefits of internal network access?

A: Internal network access is free of traffic fees and offers lower latency, but is limited to same-region KEC access.

## Related Documentation

- [Core Storage Component (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [KSYUN KS3 Official Documentation](https://docs.ksyun.com/product/ks3)
- [KS3 Java SDK](https://docs.ksyun.com/documents/2320)
- [Direct Upload Policy (DirectUploadPolicy)](../atlas-richie-component-storage-core/README.md#配置模型)
- [Storage Engine SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)