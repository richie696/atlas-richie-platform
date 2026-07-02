# Richie Component Storage - AWS S3

## Overview

`richie-component-storage-s3` is the AWS S3 object storage implementation, built on AWS SDK for Java 2.x to provide the full S3 storage capability.

## Core Features

- ✅ **AWS S3 compatible** - full support for the AWS S3 API
- ✅ **Multiple storage classes** - supports Standard, Infrequent Access, Archive, Intelligent Tiering, and more
- ✅ **Resumable upload/download** - supports resumable transfer for large files
- ✅ **Dual-mode architecture** - supports both Auto-Init and Manual Registry initialization modes, flexibly adapting to Spring Boot auto-configuration and non-Spring environments
- ✅ **Auto-configuration** - Spring Boot auto-configuration

## Dual-Mode Architecture

This component supports two initialization modes:

### 1. Auto Mode (Auto-Init, default)

When `auto-init: true` (the default), Spring Boot auto-configuration handles:

- Creating the `S3StorageEngineProvider` Bean based on the `engine: AWS_S3` configuration
- Calling the Provider's `create(properties)` to build the engine instance
- The engine instance probes the bucket and validates the prefix through `@PostConstruct initializeBucket()`
- Injecting the engine for use via `@Qualifier("objectStorageEngine")`

### 2. Manual Mode (Manual)

When `auto-init: false`, the engine is discovered and managed by `StorageEngineRegistry` through SPI:

- The Provider is not auto-registered as a Bean; it is discovered by `ServiceLoader`
- Switch engines at runtime via `registry.switchEngine(StorageEngineEnum.AWS_S3)`
- Suitable for non-Spring environments or scenarios requiring dynamic engine switching

```java
// Manual mode: switch the engine at runtime
storageEngineRegistry.switchEngine(StorageEngineEnum.AWS_S3);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

Each implementation package provides a `StorageEngineProvider` SPI implementation. `S3StorageEngineProvider` is responsible for:

| Method | Description |
|------|------|
| `supportedEngineType()` | Returns `StorageEngineEnum.AWS_S3` |
| `create(properties)` | Creates the `S3Client` and `S3StorageEngine` from the configuration |
| `validate(properties)` | Validates that endpoint / accessKeyId / accessKeySecret / bucketName are required |
| `afterPropertiesSet(engine)` | Triggers bucket probing and prefix validation in manual mode |
| `destroy(engine)` | Releases client resources |

In auto mode, the Provider is registered as a Bean in `S3AutoConfiguration`; in manual mode, it is discovered by the Registry through SPI.

## Parameter Validation (ConfigValidation)

Before creating the engine, the `ConfigValidation` utility validates required parameters. If validation fails, an `IllegalArgumentException` is thrown:

| Parameter | Validation rule |
|------|---------|
| endpoint | Non-empty |
| accessKeyId | Non-empty |
| accessKeySecret | Non-empty |
| bucketName | Non-empty |

## Direct Upload Policy (DirectUploadPolicy)

The AWS S3 engine supports client-side direct upload to object storage through presigned URLs, reducing server-side traffic pressure:

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
<artifactId>atlas-richie-component-storage-s3</artifactId>
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
        engine: AWS_S3
        # S3 access domain (required)
        # Format: s3.region.amazonaws.com or s3-region.amazonaws.com
        # Example: s3.us-east-1.amazonaws.com
        endpoint: s3.us-east-1.amazonaws.com
        # Region (required)
        # Example: us-east-1, us-west-2, ap-southeast-1
        region: us-east-1
        # Access Key ID (required)
        accessKeyId: your-access-key-id
        # Secret Access Key (required)
        accessKeySecret: your-secret-access-key
        # Bucket name (required)
        bucketName: my-bucket
        # Base path within the bucket (optional)
        basePath: /storage
        # Storage class (optional, default: STANDARD)
        # Allowed values: STANDARD, STANDARD_IA, ONEZONE_IA, ARCHIVE, ARCHIVE_FR, 
        #         COLD_ARCHIVE, DEEP_COLD_ARCHIVE, INTELLIGENT_TIERING,
        #         REDUCED_REDUNDANCY, GLACIER, GLACIER_IR, SNOW, Outposts
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

The main configuration differences between AWS S3 and other cloud storage services:

| Configuration | AWS S3 | Other cloud storage |
|--------|--------|-----------|
| **engine value** | `AWS_S3` | Varies |
| **endpoint format** | `s3.region.amazonaws.com` | Varies by cloud provider |
| **region format** | AWS region code (e.g., `us-east-1`) | Varies by cloud provider |
| **Credential name** | Access Key ID / Secret Access Key | Varies by cloud provider |
| **Storage classes** | The most extensive (15+) | Fewer |
| **Special storage classes** | Supports GLACIER, SNOW, Outposts | Not supported |

### endpoint Configuration

The AWS S3 endpoint format:

- **Standard format**: `s3.region.amazonaws.com`
  - Example: `s3.us-east-1.amazonaws.com`
  - Example: `s3.ap-southeast-1.amazonaws.com`

- **Compatible format**: `s3-region.amazonaws.com`
  - Example: `s3-us-east-1.amazonaws.com`

- **S3 Transfer Acceleration endpoint**: `s3-accelerate.amazonaws.com` (requires Transfer Acceleration to be enabled)

### region Configuration

Region codes supported by AWS S3:

| Region | Code |
|------|------|
| US East (N. Virginia) | `us-east-1` |
| US East (Ohio) | `us-east-2` |
| US West (N. California) | `us-west-1` |
| US West (Oregon) | `us-west-2` |
| Asia Pacific (Singapore) | `ap-southeast-1` |
| Asia Pacific (Tokyo) | `ap-northeast-1` |
| Europe (Ireland) | `eu-west-1` |
| China (Beijing) | `cn-north-1` |
| China (Ningxia) | `cn-northwest-1` |

> **Note**: China regions require a separate AWS account and credentials.

### Storage Classes

AWS S3 supports a rich set of storage classes:

| Storage class | Description | Use case |
|---------|------|---------|
| `STANDARD` | Standard storage | Frequently accessed data |
| `STANDARD_IA` | Standard - Infrequent Access | Data accessed infrequently but requiring rapid access |
| `ONEZONE_IA` | One Zone - Infrequent Access | Infrequently accessed data that can tolerate single-zone failure |
| `ARCHIVE` | Archive storage | Long-term retention, rarely accessed data |
| `ARCHIVE_FR` | Archive with Flash Retrieval | Archive data requiring fast retrieval |
| `COLD_ARCHIVE` | Cold archive storage | Very long-term retention, extremely rarely accessed data |
| `DEEP_COLD_ARCHIVE` | Deep cold archive storage | Very long-term retention, almost never accessed data |
| `INTELLIGENT_TIERING` | Intelligent tiering | Data with unknown or changing access patterns |
| `REDUCED_REDUNDANCY` | Reduced redundancy storage | Reproducible data (no longer recommended) |
| `GLACIER` | Glacier storage | Long-term archive (requires retrieval time) |
| `GLACIER_IR` | Glacier Instant Retrieval | Archive data requiring instant access |
| `SNOW` | Snow storage | Edge device data migration |
| `Outposts` | On-premises storage | AWS Outposts on-premises storage |

### Access Credentials

AWS S3 uses Access Key ID and Secret Access Key for authentication:

1. Sign in to the AWS console
2. Open IAM
3. Create a user and grant S3 access permissions
4. Generate the access key pair

> **Security tips**:
> - Do not commit access keys to the code repository
> - Use environment variables or a secret management service (e.g., AWS Secrets Manager)
> - Rotate access keys regularly

## Features

### 1. Automatic Storage Class Conversion

When uploading a file, the object storage class is set automatically based on the configured `storageType`:

```java
// Configure the ARCHIVE storage class
UploadResponse response = storageEngine.putObject("backup.zip", file);
// The file is automatically set to the archive storage class
```

### 2. Versioning

AWS S3 supports object versioning, so uploaded files automatically receive a version ID:

```java
UploadResponse response = storageEngine.putObject("file.txt", file);
String versionId = response.getVersionId(); // Retrieve the version ID
```

### 3. Resumable Transfer

Resumable download is supported for large files:

```java
DownloadResponse<byte[]> response = storageEngine.getResumableObject(
    "large-file.zip", 
    "/tmp/download/large-file.zip", 
    false
);
```

## Best Practices

1. **Region selection**
   - Choose the region closest to your users to reduce latency
   - Consider data compliance requirements (e.g., GDPR)

2. **Storage class selection**
   - Frequently accessed: `STANDARD`
   - Occasionally accessed: `STANDARD_IA` or `ONEZONE_IA`
   - Long-term archive: `ARCHIVE` or `COLD_ARCHIVE`
   - Unknown access pattern: `INTELLIGENT_TIERING`

3. **Access credential management**
   - Use IAM roles (on EC2/ECS/Lambda)
   - Use environment variables or a secret management service
   - Follow the principle of least privilege

4. **Cost optimization**
   - Use lifecycle policies to transition storage classes automatically
   - Remove unnecessary object versions
   - Use `INTELLIGENT_TIERING` to optimize costs automatically

## FAQ

### Q: How do I configure S3 Transfer Acceleration?

A: Enable Transfer Acceleration for the bucket in the AWS console, then use `s3-accelerate.amazonaws.com` as the endpoint.

### Q: How do I configure the China regions?

A: The China regions require a separate AWS account. The endpoint format is `s3.cn-north-1.amazonaws.com` or `s3.cn-northwest-1.amazonaws.com`.

### Q: Are other S3-compatible services supported?

A: In theory, yes, but you must ensure the endpoint and region are configured correctly. We recommend using a dedicated implementation (e.g., MinIO).

### Q: How do I set object access permissions?

A: Configure bucket and object access policies separately through the AWS console or via the AWS CLI/SDK.

## Related Documentation

- [Core Storage Component (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [AWS S3 Official Documentation](https://docs.aws.amazon.com/s3/)
- [AWS SDK for Java 2.x](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)
- [Direct Upload Policy (DirectUploadPolicy)](../atlas-richie-component-storage-core/README.md#configuration-model)
- [Storage Engine SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)
