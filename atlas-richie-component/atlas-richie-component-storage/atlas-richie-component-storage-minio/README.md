# Richie Component Storage - MinIO

## Overview

`richie-component-storage-minio` is the MinIO object storage implementation, built on the MinIO Java SDK to provide the full MinIO storage capability. MinIO is a high-performance, S3-compatible object storage service suited to private cloud and edge computing scenarios.

## Core Features

- ✅ **S3 compatible** - fully compatible with the AWS S3 API
- ✅ **Private deployment** - supports private cloud and on-premises deployment
- ✅ **High performance** - distributed architecture with high concurrency support
- ✅ **Dual-mode architecture** - supports both Auto-Init and Manual Registry initialization modes, flexibly adapting to Spring Boot auto-configuration and non-Spring environments
- ✅ **Auto-configuration** - Spring Boot auto-configuration

## Dual-Mode Architecture

This component supports two initialization modes:

### 1. Auto Mode (Auto-Init, default)

When `auto-init: true` (the default), Spring Boot auto-configuration handles:

- Creating the `MinioStorageEngineProvider` Bean based on the `engine: MINIO` configuration
- Calling the Provider's `create(properties)` to build the engine instance
- The engine instance probes the bucket and validates the prefix through `@PostConstruct initializeBucket()`
- Injecting the engine for use via `@Qualifier("objectStorageEngine")`

### 2. Manual Mode (Manual)

When `auto-init: false`, the engine is discovered and managed by `StorageEngineRegistry` through SPI:

- The Provider is not auto-registered as a Bean; it is discovered by `ServiceLoader`
- Switch engines at runtime via `registry.switchEngine(StorageEngineEnum.MINIO)`
- Suitable for non-Spring environments or scenarios requiring dynamic engine switching

```java
// Manual mode: switch the engine at runtime
storageEngineRegistry.switchEngine(StorageEngineEnum.MINIO);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

Each implementation package provides a `StorageEngineProvider` SPI implementation. `MinioStorageEngineProvider` is responsible for:

| Method | Description |
|------|------|
| `supportedEngineType()` | Returns `StorageEngineEnum.MINIO` |
| `create(properties)` | Creates the `MinioAsyncClient` and `MinioStorageEngine` from the configuration |
| `validate(properties)` | Validates that endpoint / accessKeyId / accessKeySecret / bucketName are required |
| `afterPropertiesSet(engine)` | Triggers bucket probing and prefix validation in manual mode |
| `destroy(engine)` | Releases client resources |

In auto mode, the Provider is registered as a Bean in `MinioAutoConfiguration`; in manual mode, it is discovered by the Registry through SPI.

## Parameter Validation (ConfigValidation)

Before creating the engine, the `ConfigValidation` utility validates required parameters. If validation fails, an `IllegalArgumentException` is thrown:

| Parameter | Validation rule |
|------|---------|
| endpoint | Non-empty |
| accessKeyId | Non-empty |
| accessKeySecret | Non-empty |
| bucketName | Non-empty |

## Direct Upload Policy (DirectUploadPolicy)

The MinIO engine supports client-side direct upload to object storage through presigned URLs, reducing server-side traffic pressure:

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
    <artifactId>atlas-richie-component-storage-minio</artifactId>
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
        engine: MINIO
        # MinIO access address (required)
        # Format: http://host:port or https://host:port
        # Example: http://localhost:9000
        endpoint: http://localhost:9000
        # Region (optional, default: us-east-1)
        region: us-east-1
        # Access Key ID (required)
        accessKeyId: minioadmin
        # Access Key Secret (required)
        accessKeySecret: minioadmin
        # Bucket name (required)
        bucketName: my-bucket
        # Base path within the bucket (optional)
        basePath: /storage
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

The main configuration differences between MinIO and other cloud storage services:

| Configuration | MinIO | AWS S3 | Other cloud storage |
|--------|-------|--------|-----------|
| **engine value** | `MINIO` | `AWS_S3` | Varies |
| **endpoint format** | `http://host:port` | `s3.region.amazonaws.com` | Varies by cloud provider |
| **region** | Optional (default: us-east-1) | Required | Required |
| **Credential name** | Access Key / Secret Key | Access Key ID / Secret Access Key | Varies by cloud provider |
| **Deployment** | Private deployment | Public cloud | Public cloud |
| **S3 compatibility** | ✅ Fully compatible | ✅ Native | ✅ Compatible |

### endpoint Configuration

The MinIO endpoint format:

- **HTTP**: `http://host:port`
  - Example: `http://localhost:9000`
  - Example: `http://minio.example.com:9000`

- **HTTPS**: `https://host:port`
  - Example: `https://minio.example.com:9000`
  - Requires SSL certificate configuration

- **Custom domain**: A custom domain can be configured through a reverse proxy

### region Configuration

The MinIO region is optional and defaults to `us-east-1`. For distributed deployments, you can set different regions.

### Access Credentials

MinIO uses Access Key and Secret Key for authentication:

1. **Default credentials** (for development and testing only):
   - Access Key: `minioadmin`
   - Secret Key: `minioadmin`

2. **Production environment**:
   - Create users and access keys through the MinIO console or the `mc` command-line tool
   - Follow the principle of least privilege

> **Security tips**:
> - The default credentials must be changed in production
> - Do not commit access keys to the code repository
> - Use environment variables or a secret management service

## MinIO Deployment

### Docker Deployment (Single Node)

```bash
docker run -d \
  -p 9000:9000 \
  -p 9001:9001 \
  -e "MINIO_ROOT_USER=minioadmin" \
  -e "MINIO_ROOT_PASSWORD=minioadmin" \
  --name minio \
  minio/minio server /data --console-address ":9001"
```

### Docker Compose Deployment (Distributed)

```yaml
version: '3.8'
services:
  minio:
    image: minio/minio
    command: server http://minio{1...4}/data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes:
      - minio1-data:/data
      - minio2-data:/data
      - minio3-data:/data
      - minio4-data:/data
    ports:
      - "9000:9000"
      - "9001:9001"
```

## Features

### 1. S3 Compatibility

MinIO is fully compatible with the AWS S3 API and can replace S3 seamlessly:

```java
// The code used for MinIO is identical to that used for S3
UploadResponse response = storageEngine.putObject("file.txt", file);
```

### 2. Private Deployment

MinIO supports private deployment and is suited to:

- Private cloud environments
- Edge computing scenarios
- Scenarios with strict data security requirements
- Cost-sensitive scenarios

### 3. High Performance

MinIO uses a distributed architecture and supports:

- High concurrency
- Horizontal scaling
- Data redundancy
- Automatic failover

## Best Practices

1. **Deployment**
   - Development/testing: single-node deployment
   - Production: distributed deployment (at least 4 nodes)

2. **Access credential management**
   - The default credentials must be changed in production
   - Use IAM policies to control access permissions
   - Rotate access keys regularly

3. **Bucket management**
   - Create separate buckets for different businesses
   - Configure bucket access policies
   - Enable versioning when needed

4. **Monitoring and alerting**
   - Monitor the MinIO service status
   - Monitor storage usage
   - Set up alerting rules

5. **Backup strategy**
   - Back up important data regularly
   - Use MinIO's replication feature for offsite backup

## FAQ

### Q: What is the difference between MinIO and AWS S3?

A: MinIO is an S3-compatible object storage service that can be privately deployed, while AWS S3 is a public cloud service. MinIO's API is fully compatible with S3.

### Q: How do I migrate from S3 to MinIO?

A: Because the APIs are compatible, you only need to change the endpoint and access credentials. No code changes are required.

### Q: What storage classes does MinIO support?

A: MinIO itself does not differentiate storage classes; all objects use standard storage. Similar functionality can be achieved through lifecycle policies.

### Q: How do I configure HTTPS?

A: Configure SSL certificates when starting MinIO, or use a reverse proxy such as Nginx to provide HTTPS access.

### Q: How is MinIO's performance?

A: MinIO uses a distributed architecture and delivers excellent performance, supporting high concurrency. Actual performance depends on the deployment configuration and hardware resources.

## Related Documentation

- [Core Storage Component (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [MinIO Official Documentation](https://min.io/docs/)
- [MinIO Java SDK](https://min.io/docs/minio/linux/developers/java/API.html)
- [MinIO Docker Deployment](https://min.io/docs/minio/container/index.html)
- [Direct Upload Policy (DirectUploadPolicy)](../atlas-richie-component-storage-core/README.md#configuration-model)
- [Storage Engine SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)
