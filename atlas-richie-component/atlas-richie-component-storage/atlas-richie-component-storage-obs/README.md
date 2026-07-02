# Richie Component Storage - Huawei Cloud OBS

## Overview

`richie-component-storage-obs` is the implementation of Huawei Cloud Object Storage Service (OBS), built on the Huawei Cloud OBS SDK to deliver complete OBS storage capabilities, with support for advanced features such as lifecycle management and cross-region replication.

## Core Features

- ✅ **Huawei Cloud OBS Compatible** - Full support for the Huawei Cloud OBS API
- ✅ **Multiple Storage Tiers** - Standard, Infrequent Access, Archive, and Deep Archive
- ✅ **Lifecycle Management** - Automatic storage tier transitions
- ✅ **Resumable Upload** - Resumable upload support for large files
- ✅ **Dual-Mode Architecture** - Supports both Auto-Init and Manual Registry initialization modes, flexibly adapting to Spring Boot auto-configuration and non-Spring environments
- ✅ **Auto-Configuration** - Spring Boot auto-configuration

## Dual-Mode Architecture

This component supports two initialization modes:

### 1. Auto Mode (Auto-Init, Default)

When `auto-init: true` (the default value), Spring Boot auto-configuration handles:

- Creating the `ObsStorageEngineProvider` Bean based on the `engine: HUAWEI_OBS` configuration
- Calling the Provider's `create(properties)` to create the engine instance
- Injecting and using the engine through `@Qualifier("objectStorageEngine")`

### 2. Manual Mode

When `auto-init: false`, the engine is discovered and managed by `StorageEngineRegistry` through SPI:

- The Provider is not auto-registered as a Bean, but is discovered by `ServiceLoader`
- Switch engines at runtime via `registry.switchEngine(StorageEngineEnum.HUAWEI_OBS)`
- Suitable for non-Spring environments or scenarios that require dynamic engine switching

```java
// Manual mode: switch engines at runtime
storageEngineRegistry.switchEngine(StorageEngineEnum.HUAWEI_OBS);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

Each implementation package provides a `StorageEngineProvider` SPI implementation. `ObsStorageEngineProvider` is responsible for:

| Method | Description |
|------|------|
| `supportedEngineType()` | Returns `StorageEngineEnum.HUAWEI_OBS` |
| `create(properties)` | Creates an engine instance from configuration |
| `validate(properties)` | Validates that endpoint / accessKeyId / accessKeySecret / bucketName are required |
| `destroy(engine)` | Releases resources |

In auto mode, the Provider is registered as a Bean in `ObsAutoConfiguration`. In manual mode, it is discovered by the Registry through SPI.

## Config Validation

Before the engine is created, the `ConfigValidation` utility validates the required parameters. If validation fails, an `IllegalArgumentException` is thrown:

| Parameter | Validation Rule |
|------|---------|
| endpoint | Non-empty |
| accessKeyId | Non-empty |
| accessKeySecret | Non-empty |
| bucketName | Non-empty |

## Direct Upload Policy

The OBS engine supports client-side direct upload to object storage through presigned URLs, reducing server-side traffic pressure:

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
<!-- Required core library -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-core</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
<!-- Implementation library -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-obs</artifactId>
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
        engine: HUAWEI_OBS
        # OBS access endpoint (required)
        # Format: obs.region.myhuaweicloud.com
        # Example: obs.cn-north-4.myhuaweicloud.com
        endpoint: obs.cn-north-4.myhuaweicloud.com
        # Region (required)
        # Example: cn-north-4, cn-east-3, cn-south-1
        region: cn-north-4
        # Access Key Id (required)
        accessKeyId: your-access-key-id
        # Secret Access Key (required)
        accessKeySecret: your-secret-access-key
        # Bucket name (required)
        bucketName: my-bucket
        # Base path inside the bucket (optional)
        basePath: /storage
        # Storage tier (optional, default: STANDARD)
        # Allowed values: STANDARD, STANDARD_IA, ARCHIVE, DEEP_ARCHIVE
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

The main configuration differences between Huawei Cloud OBS and other cloud storage providers:

| Configuration Item | Huawei Cloud OBS | Alibaba Cloud OSS | Tencent Cloud COS |
|--------|-----------|-----------|-----------|
| **engine value** | `HUAWEI_OBS` | `ALIYUN_OSS` | `TENCENT_COS` |
| **endpoint format** | `obs.region.myhuaweicloud.com` | `oss-cn-region.aliyuncs.com` | `cos.region.myqcloud.com` |
| **region format** | `cn-region-n` | `cn-region` | `ap-region` |
| **Access key names** | Access Key Id / Secret Access Key | AccessKey ID / AccessKey Secret | SecretId / SecretKey |
| **Storage tiers** | 4 (Standard, IA, Archive, Deep Archive) | 4 | 11 |
| **Lifecycle management** | ✅ Supported | ✅ Supported | ✅ Supported |

### endpoint Configuration

Huawei Cloud OBS endpoint format:

- **Standard format**: `obs.region.myhuaweicloud.com`
  - Example: `obs.cn-north-4.myhuaweicloud.com`
  - Example: `obs.cn-east-3.myhuaweicloud.com`
  - Example: `obs.cn-south-1.myhuaweicloud.com`

- **Internal endpoint**: `obs.region-internal.myhuaweicloud.com`
  - Suitable for same-region ECS access, no traffic fees
  - Example: `obs.cn-north-4-internal.myhuaweicloud.com`

- **Custom domain**: You can bind a custom domain in the OBS console

### region Configuration

Regions supported by Huawei Cloud OBS:

| Region | Code |
|------|------|
| CN North-Beijing 4 | `cn-north-4` |
| CN North-Beijing 1 | `cn-north-1` |
| CN East-Shanghai 1 | `cn-east-3` |
| CN East-Shanghai 2 | `cn-east-2` |
| CN South-Guangzhou | `cn-south-1` |
| CN Southwest-Guiyang 1 | `cn-southwest-2` |
| AP-Bangkok | `ap-southeast-2` |
| AP-Singapore | `ap-southeast-1` |
| AF-Johannesburg | `af-south-1` |
| LA-Sao Paulo 1 | `la-south-2` |
| LA-Mexico City 2 | `la-north-2` |

### Storage Tiers

Storage tiers supported by Huawei Cloud OBS:

| Storage Tier | Description | Suitable Scenario |
|---------|------|---------|
| `STANDARD` | Standard storage | Frequently accessed data |
| `STANDARD_IA` | Infrequent access storage | Data that is not accessed often but requires fast retrieval |
| `ARCHIVE` | Archive storage | Long-term retention, rarely accessed data |
| `DEEP_ARCHIVE` | Deep archive storage | Very long-term retention, almost never accessed data |

### Access Credentials

Huawei Cloud OBS uses Access Key Id and Secret Access Key for authentication:

1. Sign in to the Huawei Cloud console
2. Open the Unified Identity Authentication Service (IAM)
3. Create a user and grant OBS access permissions
4. Create access keys

> **Security Tips**: 
> - Use IAM sub-users and follow the principle of least privilege
> - Do not commit access keys to source control
> - Use environment variables or a secrets management service (such as Huawei Cloud KMS)

## Feature Details

### 1. Lifecycle Management

Huawei Cloud OBS supports lifecycle policies that automatically transition storage tiers:

```yaml
# Configure lifecycle policies in the OBS console
# For example: transition to Infrequent Access after 30 days, and to Archive after 90 days
```

### 2. Automatic Storage Tier Conversion

When uploading files, the configured `storageType` automatically sets the object's storage tier:

```java
// Configure as ARCHIVE storage tier
UploadResponse response = storageEngine.putObject("backup.zip", file);
// The file is automatically stored as the Archive storage tier
```

### 3. Internal Network Access

If your application is deployed on Huawei Cloud ECS, you can use the internal endpoint to avoid traffic fees:

```yaml
platform:
  component:
    storage:
      object:
        endpoint: obs.cn-north-4-internal.myhuaweicloud.com  # Internal endpoint
```

### 4. Cross-Region Replication

Huawei Cloud OBS supports cross-region replication (configured in the console), enabling off-site data backup.

## Best Practices

1. **Region Selection**
   - Choose the region closest to your users to minimize latency
   - Consider data compliance requirements

2. **Storage Tier Selection**
   - Frequent access: `STANDARD`
   - Occasional access: `STANDARD_IA`
   - Long-term archiving: `ARCHIVE` or `DEEP_ARCHIVE`

3. **Lifecycle Management**
   - Configure lifecycle policies to automatically transition storage tiers
   - Set transition time points based on access frequency

4. **Access Credential Management**
   - Use IAM sub-users and follow the principle of least privilege
   - Use environment variables or a secrets management service
   - Rotate access keys regularly

5. **Cost Optimization**
   - Use lifecycle policies to automatically transition storage tiers
   - Delete unnecessary objects
   - Use the internal endpoint to avoid traffic fees

## FAQ

### Q: How do I configure lifecycle management?

A: Configure lifecycle policies in the OBS console to define storage tier transition rules for objects at different time points.

### Q: Does it support custom domains?

A: Yes. After binding a custom domain in the OBS console, use the custom domain as the endpoint.

### Q: How do I set object access permissions?

A: You can configure bucket and object access permissions in the OBS console, or set ACLs through the SDK.

### Q: What are the benefits of internal network access?

A: Internal network access is free of traffic fees and offers lower latency, but is limited to same-region ECS access.

### Q: What are the characteristics of Deep Archive storage?

A: Deep Archive storage has the lowest cost, but restoration takes longer (typically 12 to 24 hours), making it suitable for very long-term retention.

## Related Documentation

- [Core Storage Component (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [Huawei Cloud OBS Official Documentation](https://support.huaweicloud.com/obs/)
- [OBS Java SDK](https://support.huaweicloud.com/sdk-java-devg-obs/obs_21_0101.html)
- [OBS Lifecycle Management](https://support.huaweicloud.com/usermanual-obs/obs_03_0049.html)
- [Direct Upload Policy (DirectUploadPolicy)](../atlas-richie-component-storage-core/README.md#配置模型)
- [Storage Engine SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)