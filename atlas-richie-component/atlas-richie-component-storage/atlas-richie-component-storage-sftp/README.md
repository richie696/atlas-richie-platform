# Richie Component Storage - SFTP

## Overview

`richie-component-storage-sftp` is an SFTP (SSH File Transfer Protocol) file transfer implementation built on Apache MINA SSHD 3.0. It provides SFTP file storage capabilities for scenarios that require accessing remote file servers via the SSH protocol.

## Core Features

- ✅ **SFTP Protocol** - Secure file transfer over SSH
- ✅ **Key Authentication** - Supports both password and key file authentication
- ✅ **Dual-Mode Architecture** - Supports two initialization modes: Auto-Init and Manual Registry, flexibly adapting to Spring Boot auto-configuration and non-Spring environments
- ✅ **Auto-Configuration** - Spring Boot auto-configuration

## Dual-Mode Architecture

This component supports two initialization modes:

### 1. Auto Mode (Auto-Init, default)

When `auto-init: true` (the default), Spring Boot auto-configuration handles:

- Reading the `platform.component.storage.sftp` configuration
- Creating the `SshClient` and the `SftpSessionPool` connection pool
- Registering the `SftpStorageEngineProvider` Bean
- Injecting it for use via `@Qualifier("sftpStorageEngine")`

### 2. Manual Mode

When `auto-init: false`, the engine is discovered and managed by `StorageEngineRegistry` through SPI:

- The provider is not auto-registered as a Bean; it is discovered by `ServiceLoader`
- Switch engines at runtime via `registry.switchEngine(StorageEngineEnum.SFTP)`
- Suitable for non-Spring environments or multi-engine dynamic switching scenarios

```java
// Manual mode: switch the engine at runtime
storageEngineRegistry.switchEngine(StorageEngineEnum.SFTP);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

`SftpStorageEngineProvider` implements the `StorageEngineProvider` SPI and is responsible for:

| Method | Description |
|------|------|
| `supportedEngineType()` | Returns `StorageEngineEnum.SFTP` |
| `create(properties)` | Creates the `SshClient`, `SftpSessionPool`, and `SftpStorageEngine` |
| `validate(properties)` | Validates that host / username / password are required |
| `destroy(engine)` | Closes the SSH client and releases the connection pool |

In auto mode, the provider is registered as a Bean in `SftpAutoConfiguration`. In manual mode, it is discovered by the Registry through SPI.

## Parameter Validation (ConfigValidation)

Before the engine is created, the `ConfigValidation` utility class validates required parameters. If validation fails, an `IllegalArgumentException` is thrown:

| Parameter | Validation Rule |
|----------|------|
| host     | Non-null |
| username | Non-null |
| password | Non-null |

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
    <artifactId>atlas-richie-component-storage-sftp</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 2. Configuration

```yaml
platform:
  component:
    storage:
      sftp:
        # Whether to enable SFTP storage (default: false)
        enable: true
        # Host (IP or domain, default: localhost)
        host: sftp.example.com
        # Port (default: 22)
        port: 22
        # Username (required)
        username: sftpuser
        # Password (required for password-based login)
        password: your-password
        # Whether to use SSH key login (default: false, password-based login)
        sshLogin: false
        # Identity file path (required for SSH key login)
        identityFile: /path/to/private-key
        # Base path (default: /)
        basePath: /storage
```

### 3. Usage

Inject `StorageEngine` (Bean name: `sftpStorageEngine`) and use it:

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    @Qualifier("sftpStorageEngine")
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

The main configuration differences between SFTP and other storage methods:

| Configuration | SFTP | Object Storage | Local Storage |
|----------------|-----------------------------------|-------------------------------------|------------------------------------|
| **Configuration Prefix** | `platform.component.storage.sftp` | `platform.component.storage.object` | `platform.component.storage.local` |
| **enable Field** | **Required** (true/false) | Not required | Not required |
| **host** | **Required** (SFTP server address) | Not required | Not required |
| **port** | **Required** (default: 22) | Not required | Not required |
| **username** | **Required** | Not required | Not required |
| **password** | Required for password-based login | Not required | Not required |
| **identityFile** | Required for SSH key login | Not required | Not required |
| **sshLogin** | **Required** (true=key, false=password) | Not required | Not required |
| **basePath** | Optional (default: /) | Not required | Not required |
| **engine** | Not required | Required | Not required |
| **endpoint** | Not required | Required | Not required |
| **region** | Not required | Required (for some) | Not required |

### Authentication Methods

SFTP supports two authentication methods:

#### 1. Password Authentication (default)

```yaml
platform:
  component:
    storage:
      sftp:
        enable: true
        host: sftp.example.com
        port: 22
        username: sftpuser
        password: your-password
        sshLogin: false  # Use password-based login
```

#### 2. SSH Key Authentication (recommended)

```yaml
platform:
  component:
    storage:
      sftp:
        enable: true
        host: sftp.example.com
        port: 22
        username: sftpuser
        sshLogin: true  # Use SSH key login
        identityFile: /path/to/private-key  # Private key file path
```

### Key File Formats

The following private key file formats are supported:

- **OpenSSH format**: `-----BEGIN OPENSSH PRIVATE KEY-----`
- **PEM format**: `-----BEGIN RSA PRIVATE KEY-----` or `-----BEGIN DSA PRIVATE KEY-----`
- **PuTTY format**: needs to be converted to OpenSSH format

### basePath Configuration

`basePath` specifies the base path on the SFTP server. All file operations will be relative to this path:

```yaml
platform:
  component:
    storage:
      sftp:
        basePath: /data/storage  # All files will be stored under this path
```

## Feature Details

### 1. Secure Transmission

SFTP is based on the SSH protocol and provides encrypted file transfer, which is more secure than FTP.

### 2. Key Authentication

Supports SSH key authentication, so no password needs to be stored in configuration, making it more secure.

### 3. Automatic Directory Creation

When uploading a file, if the target directory does not exist, it is created automatically.

## Best Practices

1. **Authentication Method Selection**
   - SSH key authentication is recommended in production environments
   - Set key file permissions to 600 (owner-readable only)

2. **Security**
   - Do not hardcode passwords in configuration files
   - Use environment variables or a secrets management service
   - Rotate keys regularly

3. **Network Configuration**
   - Make sure the application server can reach the SFTP server
   - Configure firewall rules to allow SSH connections
   - Consider using a VPN or leased-line connection

4. **Path Management**
   - Use `basePath` to organize file structure
   - Avoid overly deep directory hierarchies
   - Periodically clean up unneeded files

## Frequently Asked Questions

### Q: What is the difference between SFTP and FTP?

A: SFTP is based on the SSH protocol and provides encrypted transmission, which is more secure than FTP. FTP transmits data in cleartext and is not secure.

### Q: How do I generate an SSH key pair?

A: Use the `ssh-keygen` command to generate a key pair:

```bash
ssh-keygen -t rsa -b 4096 -f ~/.ssh/sftp_key
```

### Q: Does this support SFTP over SSL/TLS?

A: This component is built on Apache MINA SSHD 3.0 and supports the standard SFTP protocol. SFTP over SSL/TLS requires special server-side configuration.

### Q: How can I test the SFTP connection?

A: You can use the `sftp` command-line tool to test the connection:

```bash
sftp -i /path/to/private-key sftpuser@sftp.example.com
```

### Q: Does this support resumable uploads?

A: The current implementation does not support resumable uploads. If you need resumable uploads, use an object storage service.

## Related Documentation

- [Core Storage Component (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [Apache MINA SSHD Official Documentation](https://mina.apache.org/sshd-project/)
- [SFTP Protocol Specification](https://tools.ietf.org/html/draft-ietf-secsh-filexfer-02)
- [Storage Engine SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)
