# Richie Component Storage - FTP

## Overview

`richie-component-storage-ftp` is an FTP (File Transfer Protocol) file transfer implementation built on Apache Commons Net 3.x's `FTPClient`. It provides FTP/FTPS file storage capabilities with a built-in Apache Commons Pool 2 connection pool for reuse. It is designed for integrating with traditional FTP servers or SSL/TLS-enabled FTPS servers.

## Core Features

- ✅ **FTP/FTPS Protocols** - Supports both plain FTP and SSL/TLS-encrypted FTPS
- ✅ **Multiple Authentication Methods** - Supports regular username/password login and anonymous login
- ✅ **Dual-Mode Architecture** - Supports two initialization modes: Auto-Init and Manual Registry, flexibly adapting to Spring Boot auto-configuration and non-Spring environments
- ✅ **Connection Pool Reuse** - Built on Apache Commons Pool 2 to reuse `FTPClient` instances, avoiding the overhead of frequent connections
- ✅ **Auto-Configuration** - Spring Boot auto-configuration

## Dual-Mode Architecture

This component supports two initialization modes:

### 1. Auto Mode (Auto-Init, default)

When `auto-init: true` (the default), Spring Boot auto-configuration handles:

- Reading the `platform.component.storage.ftp` configuration
- Creating the `FtpClientPool` connection pool and `FtpStorageEngine`
- Registering the `FtpStorageEngineProvider` Bean
- Injecting it for use via `@Qualifier("ftpStorageEngine")`

### 2. Manual Mode

When `auto-init: false`, the engine is discovered and managed by `StorageEngineRegistry` through SPI:

- The provider is not auto-registered as a Bean; it is discovered by `ServiceLoader`
- Switch engines at runtime via `registry.switchEngine(StorageEngineEnum.FTP)`
- Suitable for non-Spring environments or multi-engine dynamic switching scenarios

```java
// Manual mode: switch the engine at runtime
storageEngineRegistry.switchEngine(StorageEngineEnum.FTP);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

`FtpStorageEngineProvider` implements the `StorageEngineProvider` SPI and is responsible for:

| Method | Description |
|------|------|
| `supportedEngineType()` | Returns `StorageEngineEnum.FTP` |
| `create(properties)` | Creates the `FtpClientPool` and `FtpStorageEngine` |
| `validate(properties)` | Validates that `FtpConfig` and `host` are required |
| `destroy(engine)` | Closes the FTP client and releases the connection pool |

In auto mode, the provider is registered as a Bean in `FtpAutoConfiguration`. In manual mode, it is discovered by the Registry through SPI.

## Parameter Validation (ConfigValidation)

Before the engine is created, the `ConfigValidation` utility class validates required parameters. If validation fails, an `IllegalArgumentException` is thrown:

| Parameter | Validation Rule |
|------|---------|
| ftp | Non-null (`FtpConfig`) |
| host | Non-null |

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
    <artifactId>atlas-richie-component-storage-ftp</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 2. Configuration

```yaml
platform:
  component:
    storage:
      ftp:
        # Whether to enable FTP storage (default: false)
        enable: true
        # Protocol type: FTP or FTPS (default: FTP)
        ftpType: FTP
        # Host (IP or domain, default: localhost)
        host: ftp.example.com
        # Port (default: 21)
        port: 21
        # Login type: NORMAL or ANONYMOUS (default: ANONYMOUS)
        loginType: NORMAL
        # Username (required for NORMAL login)
        username: ftpuser
        # Password (required for NORMAL login)
        password: your-password
        # Base path (default: /)
        basePath: /storage
        # Character set (default: UTF-8)
        charset: UTF-8
        # Server language code (default: zh)
        serverLanguageCode: zh
        # Whether to use passive mode (default: true)
        passiveMode: true
```

### 3. Usage

Inject `StorageEngine` (Bean name: `ftpStorageEngine`) and use it:

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    @Qualifier("ftpStorageEngine")
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

The main configuration differences between FTP and other storage methods:

| Configuration | FTP | Object Storage | Local Storage |
|--------|-----|---------|---------|
| **Configuration Prefix** | `platform.component.storage.ftp` | `platform.component.storage.object` | `platform.component.storage.local` |
| **enable Field** | **Required** (true/false) | Not required | Not required |
| **host** | **Required** (FTP server address) | Not required | Not required |
| **port** | **Required** (default: 21) | Not required | Not required |
| **ftpType** | **Required** (FTP/FTPS) | Not required | Not required |
| **loginType** | **Required** (NORMAL/ANONYMOUS) | Not required | Not required |
| **username** | Required for NORMAL login | Not required | Not required |
| **password** | Required for NORMAL login | Not required | Not required |
| **basePath** | Optional (default: /) | Not required | Not required |
| **engine** | Not required | Required | Not required |
| **endpoint** | Not required | Required | Not required |
| **region** | Not required | Required (for some) | Not required |

### Protocol Types

FTP supports two protocol types:

#### 1. Plain FTP (default)

```yaml
platform:
  component:
    storage:
      ftp:
        enable: true
        ftpType: FTP  # Plain FTP
        host: ftp.example.com
        port: 21
        loginType: NORMAL
        username: ftpuser
        password: your-password
```

#### 2. FTPS (SSL/TLS encryption, recommended)

```yaml
platform:
  component:
    storage:
      ftp:
        enable: true
        ftpType: FTPS  # Enable SSL/TLS encryption
        host: ftps.example.com
        port: 21
        loginType: NORMAL
        username: ftpuser
        password: your-password
```

### Login Methods

FTP supports two login methods:

#### 1. Regular Account Login (NORMAL)

```yaml
platform:
  component:
    storage:
      ftp:
        enable: true
        host: ftp.example.com
        port: 21
        loginType: NORMAL  # Username/password login
        username: ftpuser
        password: your-password
```

#### 2. Anonymous Login (ANONYMOUS)

Anonymous login is typically used for public FTP file download servers, requiring no username or password:

```yaml
platform:
  component:
    storage:
      ftp:
        enable: true
        host: ftp.example.com
        port: 21
        loginType: ANONYMOUS  # Anonymous login
```

### Connection Pool Configuration

`FtpClientPool` is implemented on top of Apache Commons Pool 2. The default configuration is as follows:

| Configuration | Default | Description |
|--------|-------|------|
| `maxTotal` | 8 | Maximum number of connections |
| `maxIdle` | 4 | Maximum number of idle connections |
| `minIdle` | 1 | Minimum number of idle connections |
| `testOnBorrow` | true | Validate connection availability on borrow |
| `testWhileIdle` | true | Validate connection availability while idle |
| `connectTimeout` | 15 seconds | Connection timeout |
| `dataTimeout` | 30 seconds | Data transfer timeout |

> See `com.richie.component.storage.bean.FtpConfig` for the detailed field definitions.

### basePath Configuration

`basePath` specifies the base path on the FTP server. All file operations will be relative to this path:

```yaml
platform:
  component:
    storage:
      ftp:
        basePath: /data/storage  # All files will be stored under this path
```

### Character Set and Server Type

For non-ASCII file names such as Chinese, you can adjust the character set and server system type:

```yaml
platform:
  component:
    storage:
      ftp:
        charset: UTF-8              # File name encoding (default UTF-8)
        serverLanguageCode: zh      # FTP server language code (default zh)
        systemKey: UNIX            # Directory listing parser (UNIX/VMS/WINDOWS, etc.)
```

Supported `SystemKey` values: `UNIX`, `UNIX_LTRIM`, `VMS`, `WINDOWS`, `OS/2`, `OS/400`, `AS/400`, `MVS`, `L8`, `NETWARE`, `MACOS_PETER`, corresponding to different FTP server implementations.

### Active/Passive Mode

FTP supports two connection modes for data transfer:

- **Passive Mode (PASV, default)**: The client opens the data connection, and the server responds. Suitable for scenarios where the client is behind a firewall/router.
- **Active Mode (PORT)**: The server opens the data connection back to the client, which requires allowing inbound ports on the client.

```yaml
platform:
  component:
    storage:
      ftp:
        passiveMode: false  # Switch to active mode
```

## Feature Details

### 1. FTP and FTPS Dual Protocol Support

Based on `ftpType`, the component automatically selects `FTPClient` (plain) or `FTPSClient` (SSL/TLS). FTPS enables encryption on both the control connection and the data connection, satisfying scenarios with strict transmission security requirements.

### 2. Connection Pool Reuse

`FtpClientPool` is built on Apache Commons Pool 2. It borrows and returns `FTPClient` instances on demand, avoiding the need to establish a new TCP connection for every operation, significantly improving throughput and reducing the risk of connection storms.

### 3. Automatic Directory Creation

When uploading a file, if the target directory does not exist, it is created automatically.

### 4. Character Set and Server Adaptation

The `charset` and `serverLanguageCode` settings handle file name encoding issues across FTP servers in different regions. The `systemKey` setting switches the directory listing parser to adapt to Windows / UNIX / VMS / AS400 servers.

## Best Practices

1. **Protocol and Authentication Selection**
   - Public, open, or non-sensitive scenarios: use anonymous ANONYMOUS for downloads only
   - Trusted internal network environments: use plain FTP with username and password
   - Sensitive data: use FTPS (SSL/TLS) encrypted transmission

2. **Security**
   - Do not hardcode passwords in configuration files
   - Use environment variables or a secrets management service
   - Prefer FTPS over plain FTP
   - Rotate usernames and passwords regularly

3. **Network Configuration**
   - In passive mode (default), make sure the application server can reach the FTP server's high ports for outbound traffic
   - In active mode, make sure the FTP server can connect back to the application server
   - Configure firewall rules to allow FTP control/data ports
   - Consider using a leased line or VPN to improve transfer stability

4. **Connection Pool Tuning**
   - High-concurrency upload scenarios: increase `maxTotal` appropriately, and raise `maxIdle` accordingly to reduce jitter
   - Low-frequency scenarios: keep the default 8/4/1 to avoid occupying too many FTP server sessions
   - Long idle scenarios: keep `testWhileIdle=true` so the pool automatically reclaims invalid connections

5. **Path Management**
   - Use `basePath` to organize file structure
   - Avoid overly deep directory hierarchies
   - Periodically clean up unneeded files

## Frequently Asked Questions

### Q: What is the difference between FTP and SFTP?

A: FTP and SFTP are two completely different protocols. FTP is based on plain TCP, with usernames, passwords, and file contents transmitted in cleartext. SFTP is based on encrypted SSH transport and is more secure. This component implements FTP/FTPS. For SFTP, use `richie-component-storage-sftp`.

### Q: How should I choose between FTP and FTPS?

A: For open public network environments or scenarios with security requirements, FTPS is recommended. Simply set `ftpType: FTPS`, with no code changes required. FTPS uses the same port 21 as FTP and upgrades the connection via SSL/TLS handshake.

### Q: Why does an uploaded file with a Chinese name show garbled characters?

A: Make sure `charset` matches the server's character set. The common practice is to keep `UTF-8` and pair it with `serverLanguageCode: zh`. If the server is Windows, you can also set `systemKey: WINDOWS`.

### Q: What should I do if an upload fails with a connection timeout?

A: First check the network and firewall: in passive mode you need to allow the FTP server's data ports to connect back to the client; in active mode you need to open inbound ports on the application server. You can also increase `connectTimeout` / `dataTimeout` appropriately.

### Q: How can I test the FTP connection?

A: You can use the `ftp` / `lftp` command-line tools to test the connection:

```bash
# Plain FTP
ftp ftp.example.com

# FTPS
lftp -e "set ssl:verify-certificate no; login ftpuser your-password; ls" ftps://ftp.example.com
```

### Q: Does this support resumable uploads and presigned uploads?

A: The FTP protocol itself does not support presigned direct upload URLs. `issueDirectUploadPolicy` always returns `fallback=true`, forcing the business side to use server-side proxy upload. Resumable uploads are also outside the scope of the FTP protocol. If you need these capabilities, use an object storage service.

### Q: Does this support image processing (`putImage`)?

A: No. FTP has no native image processing capability. Calling `putImage` will throw an `UnsupportedOperationException`. For image processing, use an object storage engine.

## Related Documentation

- [Core Storage Component (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [Apache Commons Net Official Documentation](https://commons.apache.org/proper/commons-net/)
- [FTP Protocol Specification (RFC 959)](https://tools.ietf.org/html/rfc959)
- [FTPS Protocol Specification (RFC 4217)](https://tools.ietf.org/html/rfc4217)
- [Storage Engine SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)
