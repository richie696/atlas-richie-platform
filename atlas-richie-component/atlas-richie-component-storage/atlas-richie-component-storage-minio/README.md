# Richie Component Storage - MinIO

## 概述

`richie-component-storage-minio` 是 MinIO 对象存储的实现，基于 MinIO Java SDK 提供完整的 MinIO 存储能力。MinIO 是一个高性能、S3 兼容的对象存储服务，适合私有云和边缘计算场景。

## 核心特性

- ✅ **S3 兼容** - 完全兼容 AWS S3 API
- ✅ **私有部署** - 支持私有云和本地部署
- ✅ **高性能** - 分布式架构，支持高并发
- ✅ **自动配置** - Spring Boot 自动配置

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-minio</artifactId>
    <version>${rydeen.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    storage:
      object:
        # 存储引擎类型（必填）
        engine: MINIO
        # MinIO访问地址（必填）
        # 格式：http://host:port 或 https://host:port
        # 示例：http://localhost:9000
        endpoint: http://localhost:9000
        # 区域（可选，默认：us-east-1）
        region: us-east-1
        # 访问密钥ID（Access Key）（必填）
        accessKeyId: minioadmin
        # 访问密钥（Secret Key）（必填）
        accessKeySecret: minioadmin
        # 存储桶名称（Bucket）（必填）
        bucketName: my-bucket
        # 桶内基础路径（可选）
        basePath: /storage
```

### 3. 使用

注入 `StorageEngine`（Bean 名称为 `objectStorageEngine`）即可使用：

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    @Qualifier("objectStorageEngine")
    private final StorageEngine storageEngine;
    
    public void uploadFile(String key, File file) {
        UploadResponse response = storageEngine.putObject(key, file);
        if (response.isSuccess()) {
            log.info("上传成功: {}", response.getUrl());
        }
    }
}
```

## 配置说明

### ⚠️ 重要配置差异

MinIO 与其他云存储的主要配置差异：

| 配置项 | MinIO | AWS S3 | 其他云存储 |
|--------|-------|--------|-----------|
| **engine 值** | `MINIO` | `AWS_S3` | 各不相同 |
| **endpoint 格式** | `http://host:port` | `s3.region.amazonaws.com` | 各云服务商格式不同 |
| **region** | 可选（默认：us-east-1） | 必填 | 必填 |
| **访问密钥名称** | Access Key / Secret Key | Access Key ID / Secret Access Key | 各云服务商命名不同 |
| **部署方式** | 私有部署 | 公有云 | 公有云 |
| **S3 兼容性** | ✅ 完全兼容 | ✅ 原生 | ✅ 兼容 |

### endpoint 配置

MinIO 的 endpoint 格式：

- **HTTP**: `http://host:port`
  - 示例：`http://localhost:9000`
  - 示例：`http://minio.example.com:9000`

- **HTTPS**: `https://host:port`
  - 示例：`https://minio.example.com:9000`
  - 需要配置 SSL 证书

- **自定义域名**: 可通过反向代理配置自定义域名

### region 配置

MinIO 的 region 是可选的，默认为 `us-east-1`。如果使用分布式部署，可以设置不同的 region。

### 访问凭证

MinIO 使用 Access Key 和 Secret Key 进行身份验证：

1. **默认凭证**（仅用于开发测试）:
   - Access Key: `minioadmin`
   - Secret Key: `minioadmin`

2. **生产环境**:
   - 通过 MinIO 控制台或 `mc` 命令行工具创建用户和访问密钥
   - 遵循最小权限原则

> **安全提示**: 
> - 生产环境必须修改默认凭证
> - 不要将访问密钥提交到代码仓库
> - 使用环境变量或密钥管理服务

## MinIO 部署

### Docker 部署（单节点）

```bash
docker run -d \
  -p 9000:9000 \
  -p 9001:9001 \
  -e "MINIO_ROOT_USER=minioadmin" \
  -e "MINIO_ROOT_PASSWORD=minioadmin" \
  --name minio \
  minio/minio server /data --console-address ":9001"
```

### Docker Compose 部署（分布式）

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

## 功能特性

### 1. S3 兼容性

MinIO 完全兼容 AWS S3 API，可以无缝替换 S3：

```java
// 使用 MinIO 的代码与使用 S3 的代码完全相同
UploadResponse response = storageEngine.putObject("file.txt", file);
```

### 2. 私有部署

MinIO 支持私有部署，适合：
- 私有云环境
- 边缘计算场景
- 数据安全要求高的场景
- 成本敏感的场景

### 3. 高性能

MinIO 采用分布式架构，支持：
- 高并发访问
- 水平扩展
- 数据冗余
- 自动故障恢复

## 最佳实践

1. **部署方式**
   - 开发/测试：单节点部署
   - 生产环境：分布式部署（至少 4 个节点）

2. **访问凭证管理**
   - 生产环境必须修改默认凭证
   - 使用 IAM 策略控制访问权限
   - 定期轮换访问密钥

3. **存储桶管理**
   - 为不同业务创建不同的存储桶
   - 设置存储桶的访问策略
   - 启用版本控制（如需要）

4. **监控和告警**
   - 监控 MinIO 服务状态
   - 监控存储空间使用情况
   - 设置告警规则

5. **备份策略**
   - 定期备份重要数据
   - 使用 MinIO 的复制功能实现异地备份

## 常见问题

### Q: MinIO 与 AWS S3 有什么区别？

A: MinIO 是 S3 兼容的对象存储，可以私有部署，而 AWS S3 是公有云服务。MinIO 的 API 与 S3 完全兼容。

### Q: 如何迁移从 S3 到 MinIO？

A: 由于 API 兼容，只需修改 endpoint 和访问凭证即可，代码无需修改。

### Q: MinIO 支持哪些存储类型？

A: MinIO 本身不区分存储类型，所有对象都使用标准存储。可以通过生命周期策略实现类似功能。

### Q: 如何配置 HTTPS？

A: 在 MinIO 启动时配置 SSL 证书，或使用反向代理（如 Nginx）提供 HTTPS 访问。

### Q: MinIO 的性能如何？

A: MinIO 采用分布式架构，性能优异，可以支持高并发访问。具体性能取决于部署配置和硬件资源。

## 相关文档

- [核心存储组件](../richie-component-storage/README.md)
- [MinIO 官方文档](https://min.io/docs/)
- [MinIO Java SDK](https://min.io/docs/minio/linux/developers/java/API.html)
- [MinIO Docker 部署](https://min.io/docs/minio/container/index.html)

