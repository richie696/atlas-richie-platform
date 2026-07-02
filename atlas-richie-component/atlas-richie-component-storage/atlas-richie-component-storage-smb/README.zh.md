# Richie Component Storage - SMB

## 概述

`richie-component-storage-smb` 是 SMB（Server Message Block）文件共享的实现，基于 JCIFS 3.0.2 提供 SMB/CIFS 文件存储能力。适用于需要访问 Windows 文件共享或 Samba 服务器的场景。

## 核心特性

- ✅ **SMB 协议** - 支持 SMB 2.0/3.0 协议
- ✅ **Windows 文件共享** - 支持访问 Windows 文件共享
- ✅ **Samba 兼容** - 支持访问 Samba 服务器
- ✅ **双模式架构** - 支持自动配置（Auto-Init）与手动注册（Manual Registry）两种初始化模式，灵活适配 Spring Boot 自动装配及非 Spring 环境
- ✅ **自动配置** - Spring Boot 自动配置

## 双模式架构

本组件支持两种初始化模式：

### 1. 自动模式（Auto-Init，默认）

`auto-init: true`（默认值）时，Spring Boot 自动装配负责：

- 读取 `platform.component.storage.smb3` 配置
- 创建 `CIFSContext` 上下文（含认证信息）
- 注册 `SmbStorageEngineProvider` Bean
- 通过 `@Qualifier("smbStorageEngine")` 注入使用

### 2. 手动模式（Manual）

`auto-init: false` 时，引擎由 `StorageEngineRegistry` 通过 SPI 发现并管理：

- Provider 不自动注册为 Bean，由 `ServiceLoader` 发现
- 通过 `registry.switchEngine(StorageEngineEnum.SMB)` 运行时切换
- 适用于非 Spring 环境或多引擎动态切换场景

```java
// 手动模式：运行时切换引擎
storageEngineRegistry.switchEngine(StorageEngineEnum.SMB);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

`SmbStorageEngineProvider` 实现 `StorageEngineProvider` SPI，负责：

| 方法 | 说明 |
|------|------|
| `supportedEngineType()` | 返回 `StorageEngineEnum.SMB` |
| `create(properties)` | 创建 `CIFSContext` 和 `SmbStorageEngine` |
| `validate(properties)` | 校验 username / password / domain 必填 |
| `destroy(engine)` | 释放 CIFS 上下文资源 |

自动模式下 Provider 在 `SmbAutoConfiguration` 中注册为 Bean；手动模式下由 Registry 通过 SPI 发现。

## 参数校验 (ConfigValidation)

引擎创建前会通过 `ConfigValidation` 工具类校验必填参数，校验失败时抛出 `IllegalArgumentException`：

| 参数 | 校验规则 |
|------|---------|
| username | 非空 |
| password | 非空 |
| domain | 非空 |

## 快速开始

### 1. 添加依赖

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

### 2. 配置

```yaml
platform:
  component:
    storage:
      smb3:
        # 是否启用SMB3存储（默认：false）
        enable: true
        # 主机（IP或域名，必填）
        domain: smb.example.com
        # 用户名（必填）
        username: smbuser
        # 密码（必填）
        password: your-password
        # Base路径（默认：/storage/）
        basePath: /storage/
        # 是否启用DFS（默认：false）
        dfs: false
        # DFS TTL（默认：300）
        dfsTtl: 300
        # 是否启用严格视图（默认：false）
        strictView: false
        # 是否启用SMB3签名（默认：false）
        convertToFQDN: false
```

### 3. 使用

注入 `StorageEngine`（Bean 名称为 `smbStorageEngine`）即可使用：

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    @Qualifier("smbStorageEngine")
    private final StorageEngine storageEngine;
    
    public void uploadFile(String key, File file) {
        UploadResponse response = storageEngine.putObject(key, file);
        if (response.isSuccess()) {
            log.info("上传成功: {}", key);
        }
    }
}
```

## 配置说明

### ⚠️ 重要配置差异

SMB 与其他存储方式的主要配置差异：

| 配置项           | SMB                               | 对象存储                                | 本地存储                               |
|---------------|-----------------------------------|-------------------------------------|------------------------------------|
| **配置前缀**      | `platform.component.storage.smb3` | `platform.component.storage.object` | `platform.component.storage.local` |
| **enable 字段** | **必填**（true/false）                | 不需要                                 | 不需要                                |
| **domain**    | **必填**（SMB服务器地址）                  | 不需要                                 | 不需要                                |
| **username**  | **必填**                            | 不需要                                 | 不需要                                |
| **password**  | **必填**                            | 不需要                                 | 不需要                                |
| **basePath**  | 可选（默认：/storage/）                  | 不需要                                 | 不需要                                |
| **dfs**       | 可选（DFS支持）                         | 不需要                                 | 不需要                                |
| **engine**    | 不需要                               | 必填                                  | 不需要                                |
| **endpoint**  | 不需要                               | 必填                                  | 不需要                                |
| **region**    | 不需要                               | 必填（部分）                              | 不需要                                |

### domain 配置

SMB 的 `domain` 字段指定 SMB 服务器地址，格式：

- **IP 地址**: `192.168.1.100`
- **主机名**: `smb.example.com`
- **UNC 路径**: `\\server\share`（仅 Windows）

### basePath 配置

`basePath` 指定 SMB 共享上的基础路径，所有文件操作都会基于此路径：

```yaml
platform:
  component:
    storage:
      smb3:
        basePath: /storage/  # 所有文件都会存储在此路径下
```

### DFS 配置

如果 SMB 服务器使用 DFS（分布式文件系统），可以启用 DFS 支持：

```yaml
platform:
  component:
    storage:
      smb3:
        dfs: true  # 启用DFS
        dfsTtl: 300  # DFS TTL（秒）
```

### 认证配置

SMB 使用用户名和密码进行身份验证：

```yaml
platform:
  component:
    storage:
      smb3:
        username: domain\user  # Windows域用户格式
        # 或
        username: user  # 本地用户
        password: your-password
```

## 功能特性

### 1. SMB 协议支持

支持 SMB 2.0/3.0 协议，可以访问：
- Windows 文件共享
- Samba 服务器
- 其他 SMB 兼容的文件服务器

### 2. DFS 支持

支持 Windows DFS（分布式文件系统），可以访问分布式文件共享。

### 3. 目录自动创建

上传文件时，如果目标目录不存在，会自动创建。

## 最佳实践

1. **安全性**
   - 不要在配置文件中硬编码密码
   - 使用环境变量或密钥管理服务
   - 考虑使用域用户认证

2. **网络配置**
   - 确保应用服务器可以访问 SMB 服务器
   - 配置防火墙规则允许 SMB 端口（445）
   - 考虑使用 VPN 或专线连接

3. **路径管理**
   - 使用 `basePath` 组织文件结构
   - 避免过深的目录层级
   - 定期清理不需要的文件

4. **性能优化**
   - 使用 SMB 3.0 协议（如果服务器支持）
   - 启用连接复用
   - 考虑使用本地缓存

## 常见问题

### Q: SMB 与 CIFS 有什么区别？

A: CIFS 是 SMB 的一个版本，现在通常统称为 SMB。本组件支持 SMB 2.0/3.0 协议。

### Q: 如何访问 Windows 文件共享？

A: 配置 `domain` 为 Windows 服务器地址，`username` 和 `password` 为 Windows 用户凭据。

### Q: 支持 SMB over SSL/TLS 吗？

A: 当前实现基于 JCIFS 3.0.2，支持标准的 SMB 协议。SMB over SSL/TLS 需要特殊的服务器配置。

### Q: 如何测试 SMB 连接？

A: 在 Windows 上可以使用 `net use` 命令测试连接：
```cmd
net use \\server\share /user:username password
```

### Q: 支持断点续传吗？

A: 当前实现不支持断点续传。如需断点续传，建议使用对象存储服务。

## 相关文档

- [核心存储组件 (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [JCIFS 3.x 官方文档](https://jcifs.codelibs.org/)
- [SMB 协议说明](https://docs.microsoft.com/windows/win32/fileio/microsoft-smb-protocol-and-cifs-protocol-overview)
- [存储引擎 SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)

