# Richie Component Storage - FTP

## 概述

`richie-component-storage-ftp` 是 FTP（File Transfer Protocol）文件传输的实现，基于 Apache Commons Net 3.x 的 `FTPClient` 提供 FTP/FTPS 文件存储能力，内置 Apache Commons Pool 2 连接池复用。适用于对接传统 FTP 服务器或启用 SSL/TLS 的 FTPS 服务器场景。

## 核心特性

- ✅ **FTP/FTPS 协议** - 同时支持明文 FTP 与 SSL/TLS 加密的 FTPS
- ✅ **多种认证方式** - 支持普通账号密码登录与匿名登录
- ✅ **双模式架构** - 支持自动配置（Auto-Init）与手动注册（Manual Registry）两种初始化模式，灵活适配 Spring Boot 自动装配及非 Spring 环境
- ✅ **连接池复用** - 基于 Apache Commons Pool 2 复用 `FTPClient`，避免频繁建连开销
- ✅ **自动配置** - Spring Boot 自动配置

## 双模式架构

本组件支持两种初始化模式：

### 1. 自动模式（Auto-Init，默认）

`auto-init: true`（默认值）时，Spring Boot 自动装配负责：

- 读取 `platform.component.storage.ftp` 配置
- 创建 `FtpClientPool` 连接池与 `FtpStorageEngine`
- 注册 `FtpStorageEngineProvider` Bean
- 通过 `@Qualifier("ftpStorageEngine")` 注入使用

### 2. 手动模式（Manual）

`auto-init: false` 时，引擎由 `StorageEngineRegistry` 通过 SPI 发现并管理：

- Provider 不自动注册为 Bean，由 `ServiceLoader` 发现
- 通过 `registry.switchEngine(StorageEngineEnum.FTP)` 运行时切换
- 适用于非 Spring 环境或多引擎动态切换场景

```java
// 手动模式：运行时切换引擎
storageEngineRegistry.switchEngine(StorageEngineEnum.FTP);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

`FtpStorageEngineProvider` 实现 `StorageEngineProvider` SPI，负责：

| 方法 | 说明 |
|------|------|
| `supportedEngineType()` | 返回 `StorageEngineEnum.FTP` |
| `create(properties)` | 创建 `FtpClientPool` 与 `FtpStorageEngine` |
| `validate(properties)` | 校验 `FtpConfig` 与 `host` 必填 |
| `destroy(engine)` | 关闭 FTP 客户端，释放连接池 |

自动模式下 Provider 在 `FtpAutoConfiguration` 中注册为 Bean；手动模式下由 Registry 通过 SPI 发现。

## 参数校验 (ConfigValidation)

引擎创建前会通过 `ConfigValidation` 工具类校验必填参数，校验失败时抛出 `IllegalArgumentException`：

| 参数 | 校验规则 |
|------|---------|
| ftp | 非空（`FtpConfig`） |
| host | 非空 |

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
    <artifactId>atlas-richie-component-storage-ftp</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    storage:
      ftp:
        # 是否启用FTP存储（默认：false）
        enable: true
        # 协议类型 FTP 或 FTPS（默认：FTP）
        ftpType: FTP
        # 主机（IP或域名，默认：localhost）
        host: ftp.example.com
        # 端口（默认：21）
        port: 21
        # 登录类型 NORMAL 或 ANONYMOUS（默认：ANONYMOUS）
        loginType: NORMAL
        # 用户名（NORMAL登录时必填）
        username: ftpuser
        # 密码（NORMAL登录时必填）
        password: your-password
        # Base路径（默认：/）
        basePath: /storage
        # 字符集（默认：UTF-8）
        charset: UTF-8
        # 服务端语言码（默认：zh）
        serverLanguageCode: zh
        # 是否使用被动模式（默认：true）
        passiveMode: true
```

### 3. 使用

注入 `StorageEngine`（Bean 名称为 `ftpStorageEngine`）即可使用：

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    @Qualifier("ftpStorageEngine")
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

FTP 与其他存储方式的主要配置差异：

| 配置项 | FTP | 对象存储 | 本地存储 |
|--------|-----|---------|---------|
| **配置前缀** | `platform.component.storage.ftp` | `platform.component.storage.object` | `platform.component.storage.local` |
| **enable 字段** | **必填**（true/false） | 不需要 | 不需要 |
| **host** | **必填**（FTP服务器地址） | 不需要 | 不需要 |
| **port** | **必填**（默认：21） | 不需要 | 不需要 |
| **ftpType** | **必填**（FTP/FTPS） | 不需要 | 不需要 |
| **loginType** | **必填**（NORMAL/ANONYMOUS） | 不需要 | 不需要 |
| **username** | NORMAL登录时必填 | 不需要 | 不需要 |
| **password** | NORMAL登录时必填 | 不需要 | 不需要 |
| **basePath** | 可选（默认：/） | 不需要 | 不需要 |
| **engine** | 不需要 | 必填 | 不需要 |
| **endpoint** | 不需要 | 必填 | 不需要 |
| **region** | 不需要 | 必填（部分） | 不需要 |

### 协议类型

FTP 支持两种协议类型：

#### 1. 明文 FTP（默认）

```yaml
platform:
  component:
    storage:
      ftp:
        enable: true
        ftpType: FTP  # 明文 FTP
        host: ftp.example.com
        port: 21
        loginType: NORMAL
        username: ftpuser
        password: your-password
```

#### 2. FTPS（SSL/TLS 加密，推荐）

```yaml
platform:
  component:
    storage:
      ftp:
        enable: true
        ftpType: FTPS  # 启用 SSL/TLS 加密
        host: ftps.example.com
        port: 21
        loginType: NORMAL
        username: ftpuser
        password: your-password
```

### 登录方式

FTP 支持两种登录方式：

#### 1. 普通账号登录（NORMAL）

```yaml
platform:
  component:
    storage:
      ftp:
        enable: true
        host: ftp.example.com
        port: 21
        loginType: NORMAL  # 账号密码登录
        username: ftpuser
        password: your-password
```

#### 2. 匿名登录（ANONYMOUS）

匿名登录通常用于公开的 FTP 文件下载服务器，无需账号密码：

```yaml
platform:
  component:
    storage:
      ftp:
        enable: true
        host: ftp.example.com
        port: 21
        loginType: ANONYMOUS  # 匿名登录
```

### 连接池配置

`FtpClientPool` 基于 Apache Commons Pool 2 实现，默认配置如下：

| 配置项 | 默认值 | 说明 |
|--------|-------|------|
| `maxTotal` | 8 | 最大连接数 |
| `maxIdle` | 4 | 最大空闲连接数 |
| `minIdle` | 1 | 最小空闲连接数 |
| `testOnBorrow` | true | 借出时校验连接可用性 |
| `testWhileIdle` | true | 空闲时校验连接可用性 |
| `connectTimeout` | 15 秒 | 连接超时 |
| `dataTimeout` | 30 秒 | 数据传输超时 |

> 详细字段定义见 `com.richie.component.storage.bean.FtpConfig`。

### basePath 配置

`basePath` 指定 FTP 服务器上的基础路径，所有文件操作都会基于此路径：

```yaml
platform:
  component:
    storage:
      ftp:
        basePath: /data/storage  # 所有文件都会存储在此路径下
```

### 字符集与服务端类型

针对中文等非 ASCII 文件名场景，可调整字符集与服务端系统类型：

```yaml
platform:
  component:
    storage:
      ftp:
        charset: UTF-8              # 文件名编码（默认 UTF-8）
        serverLanguageCode: zh      # FTP 服务端语言码（默认 zh）
        systemKey: UNIX            # 目录列表解析器（UNIX/VMS/WINDOWS 等）
```

支持的 `SystemKey` 取值：`UNIX`、`UNIX_LTRIM`、`VMS`、`WINDOWS`、`OS/2`、`OS/400`、`AS/400`、`MVS`、`L8`、`NETWARE`、`MACOS_PETER`，对应不同的 FTP 服务端实现。

### 主动/被动模式

FTP 传输数据时支持两种连接模式：

- **被动模式（PASV，默认）**：客户端打开数据连接，服务端响应，适用于客户端位于防火墙/路由器后的场景。
- **主动模式（PORT）**：服务端打开数据连接回连客户端，需要放行客户端入站端口。

```yaml
platform:
  component:
    storage:
      ftp:
        passiveMode: false  # 切换为主动模式
```

## 功能特性

### 1. FTP 与 FTPS 双协议支持

根据 `ftpType` 自动选择 `FTPClient`（明文）或 `FTPSClient`（SSL/TLS），FTPS 在控制连接与数据连接上都会启用加密，满足对传输安全有要求的场景。

### 2. 连接池复用

`FtpClientPool` 基于 Apache Commons Pool 2 实现，按需借用与归还 `FTPClient` 实例，避免每次操作都建立新的 TCP 连接，显著提升吞吐并降低连接风暴风险。

### 3. 目录自动创建

上传文件时，如果目标目录不存在，会自动创建。

### 4. 字符集与服务端适配

通过 `charset` 与 `serverLanguageCode` 处理跨区域 FTP 服务器的文件名编码问题，通过 `systemKey` 切换目录列表解析器以适配 Windows / UNIX / VMS / AS400 等服务端。

## 最佳实践

1. **协议与认证方式选择**
   - 公网开放或非敏感场景：使用匿名 ANONYMOUS 仅下载
   - 内网受信任环境：使用明文 FTP + 账号密码
   - 涉及敏感数据：使用 FTPS（SSL/TLS）加密传输

2. **安全性**
   - 不要在配置文件中硬编码密码
   - 使用环境变量或密钥管理服务
   - 优先启用 FTPS 而非明文 FTP
   - 定期轮换账号密码

3. **网络配置**
   - 被动模式（默认）下确保应用服务器出站到 FTP 服务器的高位端口可达
   - 主动模式下确保 FTP 服务器能反向连入应用服务器
   - 配置防火墙规则放行 FTP 控制/数据端口
   - 考虑使用专线或 VPN 提升传输稳定性

4. **连接池调优**
   - 高并发上传场景：适当调高 `maxTotal`，并相应调高 `maxIdle` 减少抖动
   - 低频场景：保持默认 8/4/1 即可，避免占用过多 FTP 服务端会话
   - 长时间闲置场景：保持 `testWhileIdle=true` 让池自动回收失效连接

5. **路径管理**
   - 使用 `basePath` 组织文件结构
   - 避免过深的目录层级
   - 定期清理不需要的文件

## 常见问题

### Q: FTP 与 SFTP 有什么区别？

A: FTP 与 SFTP 是两种完全不同的协议。FTP 基于 TCP 明文传输，账号密码与文件内容均为明文；SFTP 基于 SSH 加密传输，更安全。本组件对应 FTP/FTPS，SFTP 请使用 `richie-component-storage-sftp`。

### Q: 如何选择 FTP 与 FTPS？

A: 公网开放环境或对安全有要求的场景建议使用 FTPS，配置 `ftpType: FTPS` 即可，无需修改代码。FTPS 与 FTP 使用相同的 21 端口，通过 SSL/TLS 握手升级连接。

### Q: 为什么上传中文文件名出现乱码？

A: 请确认 `charset` 与服务端字符集一致，常用做法是保持 `UTF-8`，并配合 `serverLanguageCode: zh`。若服务端为 Windows，也可以设置 `systemKey: WINDOWS`。

### Q: 上传失败提示连接超时怎么办？

A: 优先排查网络与防火墙：被动模式下需要放行 FTP 服务器的数据端口回连到客户端；主动模式则要开放应用服务器入站端口。也可以将 `connectTimeout` / `dataTimeout` 适当调大。

### Q: 如何测试 FTP 连接？

A: 可以使用 `ftp` / `lftp` 命令行工具测试连接：

```bash
# 明文 FTP
ftp ftp.example.com

# FTPS
lftp -e "set ssl:verify-certificate no; login ftpuser your-password; ls" ftps://ftp.example.com
```

### Q: 支持断点续传和预签名上传吗？

A: FTP 协议本身不支持预签名直传 URL，`issueDirectUploadPolicy` 始终返回 `fallback=true` 让业务侧走服务端代理上传。断点续传同样不在 FTP 协议范围内，如需相关能力建议使用对象存储服务。

### Q: 支持图片处理（`putImage`）吗？

A: 不支持。FTP 没有原生的图片处理能力，调用 `putImage` 会抛出 `UnsupportedOperationException`，图片处理请使用对象存储引擎。

## 相关文档

- [核心存储组件 (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [Apache Commons Net 官方文档](https://commons.apache.org/proper/commons-net/)
- [FTP 协议说明（RFC 959）](https://tools.ietf.org/html/rfc959)
- [FTPS 协议说明（RFC 4217）](https://tools.ietf.org/html/rfc4217)
- [存储引擎 SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)
