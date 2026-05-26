# Richie Component Storage - AWS S3

## 概述

`richie-component-storage-s3` 是 AWS S3 对象存储的实现，基于 AWS SDK for Java 2.x 提供完整的 S3 存储能力。

## 核心特性

- ✅ **AWS S3 兼容** - 完整支持 AWS S3 API
- ✅ **多种存储类型** - 支持标准、低频、归档、智能分层等
- ✅ **断点续传** - 支持大文件断点续传
- ✅ **自动配置** - Spring Boot 自动配置

## 快速开始

### 1. 添加依赖

```xml
<!-- 必备核心库 -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage</artifactId>
    <version>${rydeen.version}</version>
</dependency>
<!-- 实现库 -->
<dependency>
<groupId>com.richie.component</groupId>
<artifactId>atlas-richie-component-storage-s3</artifactId>
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
        engine: AWS_S3
        # S3访问域名（必填）
        # 格式：s3.region.amazonaws.com 或 s3-region.amazonaws.com
        # 示例：s3.us-east-1.amazonaws.com
        endpoint: s3.us-east-1.amazonaws.com
        # 区域（必填）
        # 示例：us-east-1, us-west-2, ap-southeast-1
        region: us-east-1
        # 访问密钥ID（Access Key ID）（必填）
        accessKeyId: your-access-key-id
        # 访问密钥（Secret Access Key）（必填）
        accessKeySecret: your-secret-access-key
        # 存储桶名称（必填）
        bucketName: my-bucket
        # 桶内基础路径（可选）
        basePath: /storage
        # 存储类型（可选，默认：STANDARD）
        # 可选值：STANDARD, STANDARD_IA, ONEZONE_IA, ARCHIVE, ARCHIVE_FR, 
        #         COLD_ARCHIVE, DEEP_COLD_ARCHIVE, INTELLIGENT_TIERING,
        #         REDUCED_REDUNDANCY, GLACIER, GLACIER_IR, SNOW, Outposts
        storageType: STANDARD
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

AWS S3 与其他云存储的主要配置差异：

| 配置项 | AWS S3 | 其他云存储 |
|--------|--------|-----------|
| **engine 值** | `AWS_S3` | 各不相同 |
| **endpoint 格式** | `s3.region.amazonaws.com` | 各云服务商格式不同 |
| **region 格式** | AWS 区域代码（如 `us-east-1`） | 各云服务商格式不同 |
| **访问密钥名称** | Access Key ID / Secret Access Key | 各云服务商命名不同 |
| **存储类型** | 支持最多（15+种） | 支持较少 |
| **特殊存储类型** | 支持 GLACIER、SNOW、Outposts | 不支持 |

### endpoint 配置

AWS S3 的 endpoint 格式：

- **标准格式**: `s3.region.amazonaws.com`
  - 示例：`s3.us-east-1.amazonaws.com`
  - 示例：`s3.ap-southeast-1.amazonaws.com`

- **兼容格式**: `s3-region.amazonaws.com`
  - 示例：`s3-us-east-1.amazonaws.com`

- **S3 加速端点**: `s3-accelerate.amazonaws.com`（需要启用传输加速）

### region 配置

AWS S3 支持的区域代码：

| 区域 | 代码 |
|------|------|
| 美国东部（弗吉尼亚北部） | `us-east-1` |
| 美国东部（俄亥俄） | `us-east-2` |
| 美国西部（加利福尼亚北部） | `us-west-1` |
| 美国西部（俄勒冈） | `us-west-2` |
| 亚太地区（新加坡） | `ap-southeast-1` |
| 亚太地区（东京） | `ap-northeast-1` |
| 欧洲（爱尔兰） | `eu-west-1` |
| 中国（北京） | `cn-north-1` |
| 中国（宁夏） | `cn-northwest-1` |

> **注意**: 中国区域需要单独的 AWS 账户和访问凭证。

### 存储类型

AWS S3 支持丰富的存储类型：

| 存储类型 | 说明 | 适用场景 |
|---------|------|---------|
| `STANDARD` | 标准存储 | 频繁访问的数据 |
| `STANDARD_IA` | 标准-不频繁访问 | 不经常访问但需要快速访问的数据 |
| `ONEZONE_IA` | 单区-不频繁访问 | 不经常访问且可容忍单区故障的数据 |
| `ARCHIVE` | 归档存储 | 长期保存、很少访问的数据 |
| `ARCHIVE_FR` | 归档闪回存储 | 需要快速检索的归档数据 |
| `COLD_ARCHIVE` | 冷归档存储 | 极长期保存、极少访问的数据 |
| `DEEP_COLD_ARCHIVE` | 深冷归档存储 | 极长期保存、几乎不访问的数据 |
| `INTELLIGENT_TIERING` | 智能分层 | 访问模式未知或变化的数据 |
| `REDUCED_REDUNDANCY` | 降低冗余存储 | 可重新生成的数据（已不推荐） |
| `GLACIER` | 冰川存储 | 长期归档（需要恢复时间） |
| `GLACIER_IR` | 冰川即时存取 | 需要即时访问的归档数据 |
| `SNOW` | 雪存储 | 边缘设备数据迁移 |
| `Outposts` | 本地存储 | AWS Outposts 本地存储 |

### 访问凭证

AWS S3 使用 Access Key ID 和 Secret Access Key 进行身份验证：

1. 登录 AWS 控制台
2. 进入 IAM 服务
3. 创建用户并分配 S3 访问权限
4. 生成访问密钥对

> **安全提示**: 
> - 不要将访问密钥提交到代码仓库
> - 使用环境变量或密钥管理服务（如 AWS Secrets Manager）
> - 定期轮换访问密钥

## 功能特性

### 1. 存储类型自动转换

上传文件时，会根据配置的 `storageType` 自动设置对象的存储类型：

```java
// 配置为 ARCHIVE 存储类型
UploadResponse response = storageEngine.putObject("backup.zip", file);
// 文件会自动设置为归档存储类型
```

### 2. 版本控制

AWS S3 支持对象版本控制，上传的文件会自动获得版本ID：

```java
UploadResponse response = storageEngine.putObject("file.txt", file);
String versionId = response.getVersionId(); // 获取版本ID
```

### 3. 断点续传

支持大文件的断点续传下载：

```java
DownloadResponse<byte[]> response = storageEngine.getResumableObject(
    "large-file.zip", 
    "/tmp/download/large-file.zip", 
    false
);
```

## 最佳实践

1. **区域选择**
   - 选择距离用户最近的区域，降低延迟
   - 考虑数据合规要求（如 GDPR）

2. **存储类型选择**
   - 频繁访问：`STANDARD`
   - 偶尔访问：`STANDARD_IA` 或 `ONEZONE_IA`
   - 长期归档：`ARCHIVE` 或 `COLD_ARCHIVE`
   - 访问模式未知：`INTELLIGENT_TIERING`

3. **访问凭证管理**
   - 使用 IAM 角色（在 EC2/ECS/Lambda 上）
   - 使用环境变量或密钥管理服务
   - 最小权限原则

4. **成本优化**
   - 使用生命周期策略自动转换存储类型
   - 删除不需要的对象版本
   - 使用 `INTELLIGENT_TIERING` 自动优化成本

## 常见问题

### Q: 如何配置 S3 传输加速？

A: 需要在 AWS 控制台启用存储桶的传输加速，然后使用 `s3-accelerate.amazonaws.com` 作为 endpoint。

### Q: 中国区域如何配置？

A: 中国区域需要单独的 AWS 账户，endpoint 格式为 `s3.cn-north-1.amazonaws.com` 或 `s3.cn-northwest-1.amazonaws.com`。

### Q: 支持 S3 兼容的其他服务吗？

A: 理论上支持，但需要确保 endpoint 和 region 配置正确。建议使用专门的实现（如 MinIO）。

### Q: 如何设置对象的访问权限？

A: 需要在 AWS 控制台或通过 AWS CLI/SDK 单独设置存储桶和对象的访问策略。

## 相关文档

- [核心存储组件](../richie-component-storage/README.md)
- [AWS S3 官方文档](https://docs.aws.amazon.com/s3/)
- [AWS SDK for Java 2.x](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)

