# Richie Component Storage - Local

## Overview

`richie-component-storage-local` is a local file storage implementation that provides high-performance local file storage capabilities. It supports file metadata management, cache optimization, content deduplication, and more.

## Core Features

- ✅ **Local File Storage** - File system-based local storage
- ✅ **File Metadata Management** - Automatically maintains file metadata to the database
- ✅ **Cache Optimization** - Three-level cache for file existence, metadata, and content
- ✅ **Content Deduplication** - SHA-256-based content fingerprinting for deduplication
- ✅ **Path Safety** - Prevents directory traversal attacks
- ✅ **Automatic Cleanup** - Supports scheduled cleanup tasks for cold data
- ✅ **Dual-Mode Architecture** - Supports two initialization modes: Auto-Init and Manual Registry, flexibly adapting to Spring Boot auto-configuration and non-Spring environments
- ✅ **Database Schema Management** - Supports automatic table creation and Liquibase migrations

## Dual-Mode Architecture

This component supports two initialization modes:

### 1. Auto Mode (Auto-Init, default)

When `auto-init: true` (the default), Spring Boot auto-configuration handles:

- Reading the `platform.component.storage.local` configuration
- Registering the `LocalStorageEngineProvider` Bean
- Calling the provider's `create(properties)` to create the engine instance
- Injecting it for use via `@Qualifier("localStorageEngine")`

### 2. Manual Mode

When `auto-init: false`, the engine is discovered and managed by `StorageEngineRegistry` through SPI:

- The provider is not auto-registered as a Bean; it is discovered by `ServiceLoader`
- Switch engines at runtime via `registry.switchEngine(StorageEngineEnum.LOCAL)`
- Suitable for non-Spring environments or multi-engine dynamic switching scenarios

```java
// Manual mode: switch the engine at runtime
storageEngineRegistry.switchEngine(StorageEngineEnum.LOCAL);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

`LocalStorageEngineProvider` implements the `StorageEngineProvider` SPI and is responsible for:

| Method | Description |
|------|------|
| `supportedEngineType()` | Returns `StorageEngineEnum.LOCAL` |
| `create(properties)` | Creates a `LocalStorageEngine` instance from the configuration |
| `validate(properties)` | Validates the local storage path and other configurations |

In auto mode, the provider is registered as a Bean in `LocalAutoConfiguration`. In manual mode, it is discovered by the Registry through SPI.

## Parameter Validation (ConfigValidation)

Before the engine is created, the `ConfigValidation` utility class validates required parameters. If validation fails, an `IllegalArgumentException` is thrown:

| Parameter | Validation Rule |
|------|---------|
| path | Non-null (local storage path) |

## Quick Start

### 1. Add the Dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-core</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-local</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 2. Configuration

```yaml
platform:
  component:
    storage:
      local:
        # Whether to enable local storage (default: true)
        enable: true
        # Local storage path (default: ./storage/)
        path: ./storage/
        # Cache configuration
        cache:
          # File existence cache TTL in milliseconds (default: 3600000 = 1 hour)
          existsTtl: 3600000
          # File metadata cache TTL in milliseconds (default: 1800000 = 30 minutes)
          metadataTtl: 1800000
          # File content cache TTL in milliseconds (default: 600000 = 10 minutes)
          contentTtl: 600000
          # Maximum file size for content caching in bytes (default: 1048576 = 1MB, only small files are cached)
          contentMaxSize: 1048576
          # Whether to enable cache statistics (default: true)
          statisticsEnabled: true
        # Database schema configuration
        schema:
          # Whether to detect and create missing tables on startup (default: true)
          enableAutoDdl: true
          # Table name prefix (optional, default: rd_)
          tablePrefix: rd_
          # Metadata table name (default: rd_file_metadata)
          metadataTable: rd_file_metadata
          # Whether to enable Liquibase (default: true)
          enableLiquibase: true
          # Liquibase changelog path
          liquibaseChangeLog: classpath:db/changelog/db.changelog-master.yaml
          # Liquibase execution mode: true=output SQL only, false=execute (default: false)
          liquibaseDryRun: false
        # Cold data cleanup configuration
        cleanup:
          # Whether to enable cleanup (default: false)
          enabled: false
          # Retention period in days (default: 180 days)
          retentionDays: 180
          # Maximum number of deletions per run (default: 1000)
          maxDeletePerRun: 1000
          # Whether to only print SQL/operations without executing (default: false)
          dryRun: false
          # Execution time: every day at 03:00 (default: 0 0 3 * * ?)
          cron: "0 0 3 * * ?"
          # Whether to also delete database metadata (default: true)
          removeMetadata: true
```

### 3. Usage

Inject `StorageEngine` (Bean name: `localStorageEngine`) and use it:

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    @Qualifier("localStorageEngine")
    private final StorageEngine storageEngine;
    
    public void uploadFile(String key, File file) {
        UploadResponse response = storageEngine.putObject(key, file);
        if (response.isSuccess()) {
            log.info("Upload successful: key={}, hash={}", key, response.getHashValue());
        }
    }
}
```

## Configuration Details

### ⚠️ Important Configuration Differences

The main configuration differences between local storage and cloud storage:

| Configuration | Local Storage | Cloud Storage |
|--------|---------|--------|
| **Configuration Prefix** | `platform.component.storage.local` | `platform.component.storage.object` |
| **engine Field** | Not required | Required (e.g. `MINIO`, `AWS_S3`, etc.) |
| **endpoint** | Not required | Required |
| **region** | Not required | Required (for some cloud storage) |
| **accessKeyId** | Not required | Required |
| **accessKeySecret** | Not required | Required |
| **bucketName** | Not required | Required |
| **path** | **Required** (storage path) | Not required |
| **Cache Configuration** | **Supported** (three-level cache) | Not supported |
| **Database Metadata** | **Supported** | Not supported |
| **Content Deduplication** | **Supported** (SHA-256) | Not supported |

### Path Configuration

- **Relative path**: `./storage/` - relative to the application's working directory
- **Absolute path**: `/data/storage/` - absolute path
- **Default path**: If not configured, defaults to `./storage/`

### Cache Configuration

Local storage provides a three-level cache mechanism:

1. **File Existence Cache** - Caches whether a file exists, reducing file system queries
2. **File Metadata Cache** - Caches file size, hash value, and other metadata
3. **File Content Cache** - Caches the content of small files only (default: under 1MB)

### Database Schema

Local storage automatically creates the `rd_file_metadata` table to store file metadata, including:

- File path (key)
- Original file name
- Content type
- File size
- SHA-256 hash value
- Version ID
- Physical path
- Upload time
- Access count

## Feature Details

### 1. Content Deduplication

Based on SHA-256 content fingerprinting, files with identical content are not stored multiple times:

```java
// Uploading files with the same content: the second upload will return directly without writing to disk again
UploadResponse response1 = storageEngine.putObject("file1.txt", file);
UploadResponse response2 = storageEngine.putObject("file2.txt", file); // Same content
// response1 and response2 will have the same hashValue
```

### 2. Path Safety

Automatically prevents directory traversal attacks:

```java
// ❌ These paths will be rejected
storageEngine.putObject("../etc/passwd", file);  // Contains ..
storageEngine.putObject("C:/Windows/System32", file);  // Contains :
storageEngine.putObject("/etc/passwd", file);  // Absolute path

// ✅ These paths are safe
storageEngine.putObject("user/123/avatar.jpg", file);
storageEngine.putObject("2024/01/15/document.pdf", file);
```

### 3. Atomic Writes

Uses a temporary file plus an atomic move to ensure write atomicity and avoid file corruption.

### 4. Cold Data Cleanup

Supports scheduled cleanup of files older than a specified number of days:

```yaml
platform:
  component:
    storage:
      local:
        cleanup:
          enabled: true
          retentionDays: 180  # Retain for 180 days
          cron: "0 0 3 * * ?"  # Execute at 3:00 AM every day
```

## Best Practices

1. **Path Organization**
   - Organize paths by business dimension: `/user/{userId}/avatar.jpg`
   - Organize paths by date dimension: `/2024/01/15/document.pdf`
   - Avoid overly deep directory hierarchies

2. **Cache Strategy**
   - Small files (< 1MB) will have their content cached automatically
   - Large files only cache metadata, not content
   - Adjust cache TTLs based on your business scenario

3. **Database Metadata**
   - Enable database metadata management for easier file querying and statistics
   - Periodically clean up expired metadata records

4. **Storage Path**
   - Use an absolute path in production environments
   - Make sure the application has read/write permissions
   - Consider disk space and backup strategy

## Frequently Asked Questions

### Q: Does local storage support distributed deployment?

A: No. Local storage is based on the file system, and in a multi-instance deployment, each instance can only access its own local file system. For distributed storage, use cloud storage or MinIO.

### Q: How do I migrate files from local storage?

A: Copy the file directory directly, and make sure the database metadata is migrated in sync.

### Q: What is the cache invalidation strategy?

A: It is a time-based expiration strategy. The file existence cache is 1 hour, the metadata cache is 30 minutes, and the content cache is 10 minutes.

### Q: How do I clear the cache?

A: You can use the `LocalStorageEngine.clearFileCaches(key)` method to manually clear the cache for a specified file.

## Related Documentation

- [Core Storage Component (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [Storage Engine SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)
