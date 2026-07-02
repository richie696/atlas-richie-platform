# Richie Component Storage - SMB

## Overview

`richie-component-storage-smb` is an SMB (Server Message Block) file sharing implementation built on JCIFS 3.0.2. It provides SMB/CIFS file storage capabilities for scenarios that require accessing Windows file shares or Samba servers.

## Core Features

- ✅ **SMB Protocol** - Supports SMB 2.0/3.0 protocols
- ✅ **Windows File Sharing** - Supports accessing Windows file shares
- ✅ **Samba Compatible** - Supports accessing Samba servers
- ✅ **Dual-Mode Architecture** - Supports two initialization modes: Auto-Init and Manual Registry, flexibly adapting to Spring Boot auto-configuration and non-Spring environments
- ✅ **Auto-Configuration** - Spring Boot auto-configuration

## Dual-Mode Architecture

This component supports two initialization modes:

### 1. Auto Mode (Auto-Init, default)

When `auto-init: true` (the default), Spring Boot auto-configuration handles:

- Reading the `platform.component.storage.smb3` configuration
- Creating the `CIFSContext` (including authentication information)
- Registering the `SmbStorageEngineProvider` Bean
- Injecting it for use via `@Qualifier("smbStorageEngine")`

### 2. Manual Mode

When `auto-init: false`, the engine is discovered and managed by `StorageEngineRegistry` through SPI:

- The provider is not auto-registered as a Bean; it is discovered by `ServiceLoader`
- Switch engines at runtime via `registry.switchEngine(StorageEngineEnum.SMB)`
- Suitable for non-Spring environments or multi-engine dynamic switching scenarios

```java
// Manual mode: switch the engine at runtime
storageEngineRegistry.switchEngine(StorageEngineEnum.SMB);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

`SmbStorageEngineProvider` implements the `StorageEngineProvider` SPI and is responsible for:

| Method | Description |
|------|------|
| `supportedEngineType()` | Returns `StorageEngineEnum.SMB` |
| `create(properties)` | Creates the `CIFSContext` and `SmbStorageEngine` |
| `validate(properties)` | Validates that username / password / domain are required |
| `destroy(engine)` | Releases CIFS context resources |

In auto mode, the provider is registered as a Bean in `SmbAutoConfiguration`. In manual mode, it is discovered by the Registry through SPI.

## Parameter Validation (ConfigValidation)

Before the engine is created, the `ConfigValidation` utility class validates required parameters. If validation fails, an `IllegalArgumentException` is thrown:

| Parameter | Validation Rule |
|------|---------|
| username | Non-null |
| password | Non-null |
| domain | Non-null |

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
    <artifactId>atlas-richie-component-storage-smb</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 2. Configuration

```yaml
platform:
  component:
    storage:
      smb3:
        # Whether to enable SMB3 storage (default: false)
        enable: true
        # Host (IP or domain, required)
        domain: smb.example.com
        # Username (required)
        username: smbuser
        # Password (required)
        password: your-password
        # Base path (default: /storage/)
        basePath: /storage/
        # Whether to enable DFS (default: false)
        dfs: false
        # DFS TTL (default: 300)
        dfsTtl: 300
        # Whether to enable strict view (default: false)
        strictView: false
        # Whether to enable SMB3 signing (default: false)
        convertToFQDN: false
```

### 3. Usage

Inject `StorageEngine` (Bean name: `smbStorageEngine`) and use it:

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    @Qualifier("smbStorageEngine")
    private final StorageEngine storageEngine;
    
    public void uploadFile(String key, File file) {
        UploadResponse response = storageEngine.putObject(key, file);
        if (response.isSuccess()) {
            log.info("Upload successful: {}", key);
        }
    }
}
```

## Configuration Details

### ⚠️ Important Configuration Differences

The main configuration differences between SMB and other storage methods:

| Configuration | SMB | Object Storage | Local Storage |
|---------------|-----------------------------------|-------------------------------------|------------------------------------|
| **Configuration Prefix** | `platform.component.storage.smb3` | `platform.component.storage.object` | `platform.component.storage.local` |
| **enable Field** | **Required** (true/false) | Not required | Not required |
| **domain** | **Required** (SMB server address) | Not required | Not required |
| **username** | **Required** | Not required | Not required |
| **password** | **Required** | Not required | Not required |
| **basePath** | Optional (default: /storage/) | Not required | Not required |
| **dfs** | Optional (DFS support) | Not required | Not required |
| **engine** | Not required | Required | Not required |
| **endpoint** | Not required | Required | Not required |
| **region** | Not required | Required (for some) | Not required |

### domain Configuration

SMB's `domain` field specifies the SMB server address. The format is:

- **IP address**: `192.168.1.100`
- **Hostname**: `smb.example.com`
- **UNC path**: `\\server\share` (Windows only)

### basePath Configuration

`basePath` specifies the base path on the SMB share. All file operations will be relative to this path:

```yaml
platform:
  component:
    storage:
      smb3:
        basePath: /storage/  # All files will be stored under this path
```

### DFS Configuration

If the SMB server uses DFS (Distributed File System), you can enable DFS support:

```yaml
platform:
  component:
    storage:
      smb3:
        dfs: true  # Enable DFS
        dfsTtl: 300  # DFS TTL (seconds)
```

### Authentication Configuration

SMB uses a username and password for authentication:

```yaml
platform:
  component:
    storage:
      smb3:
        username: domain\user  # Windows domain user format
        # or
        username: user  # Local user
        password: your-password
```

## Feature Details

### 1. SMB Protocol Support

Supports SMB 2.0/3.0 protocols, and can access:

- Windows file shares
- Samba servers
- Other SMB-compatible file servers

### 2. DFS Support

Supports Windows DFS (Distributed File System), allowing access to distributed file shares.

### 3. Automatic Directory Creation

When uploading a file, if the target directory does not exist, it is created automatically.

## Best Practices

1. **Security**
   - Do not hardcode passwords in configuration files
   - Use environment variables or a secrets management service
   - Consider using domain user authentication

2. **Network Configuration**
   - Make sure the application server can reach the SMB server
   - Configure firewall rules to allow the SMB port (445)
   - Consider using a VPN or leased-line connection

3. **Path Management**
   - Use `basePath` to organize file structure
   - Avoid overly deep directory hierarchies
   - Periodically clean up unneeded files

4. **Performance Optimization**
   - Use the SMB 3.0 protocol (if the server supports it)
   - Enable connection reuse
   - Consider using local caching

## Frequently Asked Questions

### Q: What is the difference between SMB and CIFS?

A: CIFS is a version of SMB, and they are now commonly referred to as SMB. This component supports SMB 2.0/3.0 protocols.

### Q: How do I access a Windows file share?

A: Set `domain` to the Windows server address, and `username` and `password` to the Windows user credentials.

### Q: Does this support SMB over SSL/TLS?

A: The current implementation is built on JCIFS 3.0.2 and supports the standard SMB protocol. SMB over SSL/TLS requires special server-side configuration.

### Q: How can I test the SMB connection?

A: On Windows, you can use the `net use` command to test the connection:

```cmd
net use \\server\share /user:username password
```

### Q: Does this support resumable uploads?

A: The current implementation does not support resumable uploads. If you need resumable uploads, use an object storage service.

## Related Documentation

- [Core Storage Component (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [JCIFS 3.x Official Documentation](https://jcifs.codelibs.org/)
- [SMB Protocol Specification](https://docs.microsoft.com/windows/win32/fileio/microsoft-smb-protocol-and-cifs-protocol-overview)
- [Storage Engine SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)
