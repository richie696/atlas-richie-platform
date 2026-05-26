# Richie Component Storage - SFTP

## 概述

`richie-component-storage-sftp` 是 SFTP（SSH File Transfer Protocol）文件传输的实现，基于 JSch 提供 SFTP 文件存储能力。适用于需要通过 SSH 协议访问远程文件服务器的场景。

## 核心特性

- ✅ **SFTP 协议** - 基于 SSH 的安全文件传输
- ✅ **密钥认证** - 支持密码和密钥文件两种认证方式
- ✅ **自动配置** - Spring Boot 自动配置

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-sftp</artifactId>
    <version>${rydeen.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    storage:
      sftp:
        # 是否启用SFTP存储（默认：false）
        enable: true
        # 主机（IP或域名，默认：localhost）
        host: sftp.example.com
        # 端口（默认：22）
        port: 22
        # 用户名（必填）
        username: sftpuser
        # 密码（密码登录时必填）
        password: your-password
        # 是否使用SSH登录（默认：false，使用密码登录）
        sshLogin: false
        # 密钥文件路径（SSH登录时必填）
        identityFile: /path/to/private-key
        # Base路径（默认：/）
        basePath: /storage
```

### 3. 使用

注入 `StorageEngine`（Bean 名称为 `sftpStorageEngine`）即可使用：

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    @Qualifier("sftpStorageEngine")
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

SFTP 与其他存储方式的主要配置差异：

| 配置项 | SFTP | 对象存储 | 本地存储 |
|--------|------|---------|---------|
| **配置前缀** | `platform.component.storage.sftp` | `platform.component.storage.object` | `platform.component.storage.local` |
| **enable 字段** | **必填**（true/false） | 不需要 | 不需要 |
| **host** | **必填**（SFTP服务器地址） | 不需要 | 不需要 |
| **port** | **必填**（默认：22） | 不需要 | 不需要 |
| **username** | **必填** | 不需要 | 不需要 |
| **password** | 密码登录时必填 | 不需要 | 不需要 |
| **identityFile** | SSH登录时必填 | 不需要 | 不需要 |
| **sshLogin** | **必填**（true=密钥，false=密码） | 不需要 | 不需要 |
| **basePath** | 可选（默认：/） | 不需要 | 不需要 |
| **engine** | 不需要 | 必填 | 不需要 |
| **endpoint** | 不需要 | 必填 | 不需要 |
| **region** | 不需要 | 必填（部分） | 不需要 |

### 认证方式

SFTP 支持两种认证方式：

#### 1. 密码认证（默认）

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
        sshLogin: false  # 使用密码登录
```

#### 2. SSH 密钥认证（推荐）

```yaml
platform:
  component:
    storage:
      sftp:
        enable: true
        host: sftp.example.com
        port: 22
        username: sftpuser
        sshLogin: true  # 使用SSH密钥登录
        identityFile: /path/to/private-key  # 私钥文件路径
```

### 密钥文件格式

支持以下格式的私钥文件：

- **OpenSSH 格式**: `-----BEGIN OPENSSH PRIVATE KEY-----`
- **PEM 格式**: `-----BEGIN RSA PRIVATE KEY-----` 或 `-----BEGIN DSA PRIVATE KEY-----`
- **PuTTY 格式**: 需要转换为 OpenSSH 格式

### basePath 配置

`basePath` 指定 SFTP 服务器上的基础路径，所有文件操作都会基于此路径：

```yaml
platform:
  component:
    storage:
      sftp:
        basePath: /data/storage  # 所有文件都会存储在此路径下
```

## 功能特性

### 1. 安全传输

SFTP 基于 SSH 协议，提供加密的文件传输，比 FTP 更安全。

### 2. 密钥认证

支持 SSH 密钥认证，无需在配置中存储密码，更安全。

### 3. 目录自动创建

上传文件时，如果目标目录不存在，会自动创建。

## 最佳实践

1. **认证方式选择**
   - 生产环境推荐使用 SSH 密钥认证
   - 密钥文件权限设置为 600（仅所有者可读）

2. **安全性**
   - 不要在配置文件中硬编码密码
   - 使用环境变量或密钥管理服务
   - 定期轮换密钥

3. **网络配置**
   - 确保应用服务器可以访问 SFTP 服务器
   - 配置防火墙规则允许 SSH 连接
   - 考虑使用 VPN 或专线连接

4. **路径管理**
   - 使用 `basePath` 组织文件结构
   - 避免过深的目录层级
   - 定期清理不需要的文件

## 常见问题

### Q: SFTP 与 FTP 有什么区别？

A: SFTP 基于 SSH 协议，提供加密传输，比 FTP 更安全。FTP 是明文传输，不安全。

### Q: 如何生成 SSH 密钥对？

A: 使用 `ssh-keygen` 命令生成密钥对：
```bash
ssh-keygen -t rsa -b 4096 -f ~/.ssh/sftp_key
```

### Q: 支持 SFTP over SSL/TLS 吗？

A: 本组件基于 JSch，支持标准的 SFTP 协议。SFTP over SSL/TLS 需要特殊的服务器配置。

### Q: 如何测试 SFTP 连接？

A: 可以使用 `sftp` 命令行工具测试连接：
```bash
sftp -i /path/to/private-key sftpuser@sftp.example.com
```

### Q: 支持断点续传吗？

A: 当前实现不支持断点续传。如需断点续传，建议使用对象存储服务。

## 相关文档

- [核心存储组件](../richie-component-storage/README.md)
- [JSch 官方文档](http://www.jcraft.com/jsch/)
- [SFTP 协议说明](https://tools.ietf.org/html/draft-ietf-secsh-filexfer-02)

