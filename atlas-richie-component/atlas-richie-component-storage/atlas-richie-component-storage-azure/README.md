# Richie Component Storage - Azure Blob Storage

## Overview

`richie-component-storage-azure` is the implementation of Microsoft Azure Blob Storage, built on the Azure Storage SDK for Java to deliver complete Azure Blob storage capabilities.

## Core Features

- ✅ **Azure Blob Compatible** - Full support for the Azure Blob Storage API
- ✅ **Multiple Access Tiers** - Hot, Cool, and Archive access tiers
- ✅ **Resumable Upload** - Resumable upload support for large files
- ✅ **Dual-Mode Architecture** - Supports both Auto-Init and Manual Registry initialization modes, flexibly adapting to Spring Boot auto-configuration and non-Spring environments
- ✅ **Auto-Configuration** - Spring Boot auto-configuration

## Dual-Mode Architecture

This component supports two initialization modes:

### 1. Auto Mode (Auto-Init, Default)

When `auto-init: true` (the default value), Spring Boot auto-configuration handles:

- Creating the `AzureBlobStorageEngineProvider` Bean based on the `engine: AZURE_BLOB` configuration
- Calling the Provider's `create(properties)` to create the engine instance
- Injecting and using the engine through `@Qualifier("objectStorageEngine")`

### 2. Manual Mode

When `auto-init: false`, the engine is discovered and managed by `StorageEngineRegistry` through SPI:

- The Provider is not auto-registered as a Bean, but is discovered by `ServiceLoader`
- Switch engines at runtime via `registry.switchEngine(StorageEngineEnum.AZURE_BLOB)`
- Suitable for non-Spring environments or scenarios that require dynamic engine switching

```java
// Manual mode: switch engines at runtime
storageEngineRegistry.switchEngine(StorageEngineEnum.AZURE_BLOB);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

Each implementation package provides a `StorageEngineProvider` SPI implementation. `AzureBlobStorageEngineProvider` is responsible for:

| Method | Description |
|------|------|
| `supportedEngineType()` | Returns `StorageEngineEnum.AZURE_BLOB` |
| `create(properties)` | Creates an engine instance from configuration |
| `validate(properties)` | Validates that endpoint / accessKeyId / accessKeySecret / bucketName are required |
| `destroy(engine)` | Releases resources |

In auto mode, the Provider is registered as a Bean in `AzureBlobAutoConfiguration`. In manual mode, it is discovered by the Registry through SPI.

## Config Validation

Before the engine is created, the `ConfigValidation` utility validates the required parameters. If validation fails, an `IllegalArgumentException` is thrown:

| Parameter | Validation Rule |
|------|---------|
| endpoint | Non-empty |
| accessKeyId | Non-empty |
| accessKeySecret | Non-empty |
| bucketName | Non-empty |

## Direct Upload Policy

The Azure Blob engine supports client-side direct upload to object storage through SAS-signed URLs, reducing server-side traffic pressure:

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
// Returns a SAS-signed PUT URL; the client can upload directly
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
    <artifactId>atlas-richie-component-storage-azure</artifactId>
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
        engine: AZURE_BLOB
        # Azure Blob access endpoint (required)
        # Format: account.blob.core.windows.net
        # Example: mystorageaccount.blob.core.windows.net
        endpoint: mystorageaccount.blob.core.windows.net
        # Region (required)
        # Example: eastus, westus, westeurope
        region: eastus
        # Access Key ID (Storage Account Name) (required)
        accessKeyId: mystorageaccount
        # Secret Access Key (Storage Account Key) (required)
        accessKeySecret: your-storage-account-key
        # Container name (required)
        bucketName: my-container
        # Base path inside the container (optional)
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
            log.info("Upload successful: {}", response.getUrl());
        }
    }
}
```

## Configuration Reference

### ⚠️ Important Configuration Differences

The main configuration differences between Azure Blob Storage and other cloud storage providers:

| Configuration Item | Azure Blob | AWS S3 | Alibaba Cloud OSS |
|--------|-----------|--------|-----------|
| **engine value** | `AZURE_BLOB` | `AWS_S3` | `ALIYUN_OSS` |
| **endpoint format** | `account.blob.core.windows.net` | `s3.region.amazonaws.com` | `oss-cn-region.aliyuncs.com` |
| **region format** | Azure region code (e.g., `eastus`) | AWS region code (e.g., `us-east-1`) | Alibaba Cloud region code (e.g., `cn-hangzhou`) |
| **Access key names** | Storage Account Name / Storage Account Key | Access Key ID / Secret Access Key | AccessKey ID / AccessKey Secret |
| **Bucket name** | Container Name | Bucket Name | Bucket Name |
| **Access tiers** | Hot, Cool, Archive | Storage tiers (15+) | Storage tiers (4) |
| **Connection string** | ✅ Supported | ❌ Not supported | ❌ Not supported |

### endpoint Configuration

Azure Blob Storage endpoint format:

- **Standard format**: `account.blob.core.windows.net`
  - Example: `mystorageaccount.blob.core.windows.net`
  - `account` is the storage account name

- **Custom domain**: You can configure a custom domain in the Azure portal

### region Configuration

Regions supported by Azure Blob Storage:

| Region | Code |
|------|------|
| East US | `eastus` |
| West US | `westus` |
| Central US | `centralus` |
| West Europe | `westeurope` |
| North Europe | `northeurope` |
| East Asia | `eastasia` |
| Southeast Asia | `southeastasia` |
| China East | `chinaeast` |
| China North | `chinanorth` |

### Access Credentials

Azure Blob Storage uses the storage account name and storage account key for authentication:

1. Sign in to the Azure portal
2. Create a storage account
3. Retrieve the storage account name and key from "Access Keys"

> **Security Tips**: 
> - Use Shared Access Signatures (SAS) instead of storage account keys when possible
> - Do not commit access keys to source control
> - Use Azure Key Vault to manage keys

### Access Tiers

Azure Blob Storage supports three access tiers:

| Access Tier | Description | Suitable Scenario |
|--------|------|---------|
| **Hot** | Frequently accessed data | Active data |
| **Cool** | Infrequently accessed data | Backup data |
| **Archive** | Rarely accessed data | Long-term archiving |

> **Note**: The access tier concept in Azure Blob Storage is similar to storage tiers in other cloud storage services, but is configured differently.

## Feature Details

### 1. Connection String Support

Azure Blob Storage supports authentication using a connection string. This component authenticates through accessKeyId (storage account name) and accessKeySecret (storage account key). The connection string approach can be used directly through the Azure SDK.

### 2. Automatic Access Tier Setting

When uploading files, the access tier can be set automatically based on configuration (lifecycle policies must be configured in the Azure portal or through the SDK).

### 3. Shared Access Signature (SAS)

Azure Blob Storage supports using SAS (Shared Access Signature) for temporary authorized access, which is suitable for generating temporary URLs for direct client-side upload or download.

## Best Practices

1. **Region Selection**
   - Choose the region closest to your users to minimize latency
   - Consider data compliance requirements

2. **Access Tier Selection**
   - Frequent access: Hot tier
   - Occasional access: Cool tier
   - Long-term archiving: Archive tier

3. **Access Credential Management**
   - Use SAS instead of storage account keys when possible
   - Use Azure Key Vault to manage keys
   - Rotate access keys regularly

4. **Cost Optimization**
   - Use lifecycle policies to automatically transition access tiers
   - Delete unnecessary blobs
   - Use the Cool or Archive tier to store infrequently accessed data

## FAQ

### Q: What is the difference between Azure Blob Storage and AWS S3?

A: Azure Blob Storage is Azure's object storage service. Its API differs from S3, but the features are similar. This component provides a unified interface that hides the underlying differences.

### Q: How do I configure access tiers?

A: You can set the default access tier for a container in the Azure portal, or use lifecycle policies to automatically transition access tiers.

### Q: Does it support custom domains?

A: Yes. After configuring a custom domain in the Azure portal, use the custom domain as the endpoint.

### Q: How do I migrate from S3 to Azure Blob?

A: Because the APIs differ, code changes are required. However, this component provides a unified interface, so you only need to change the configuration.

## Related Documentation

- [Core Storage Component (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [Azure Blob Storage Official Documentation](https://docs.microsoft.com/azure/storage/blobs/)
- [Azure Storage SDK for Java](https://github.com/Azure/azure-sdk-for-java/tree/main/sdk/storage)
- [Direct Upload Policy (DirectUploadPolicy)](../atlas-richie-component-storage-core/README.md#配置模型)
- [Storage Engine SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)