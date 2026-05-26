# Richie Component Storage - 火山引擎 TOS

## 概述

`richie-component-storage-tos` 是火山引擎对象存储服务（TOS）的实现，基于火山引擎 TOS SDK 提供完整的 TOS 存储能力。TOS 兼容 AWS S3 API，支持图片处理、视频处理等高级功能。

## 核心特性

- ✅ **S3 兼容** - 兼容 AWS S3 API
- ✅ **图片处理** - 支持图片缩放、裁剪、水印等
- ✅ **多种存储类型** - 支持标准、低频、归档、冷归档
- ✅ **断点续传** - 支持大文件断点续传
- ✅ **自动配置** - Spring Boot 自动配置

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-tos</artifactId>
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
        engine: VOLCENGINE_TOS
        # TOS访问域名（必填）
        # 格式：tos-cn-region.volces.com
        # 示例：tos-cn-beijing.volces.com
        endpoint: tos-cn-beijing.volces.com
        # 区域（必填）
        # 示例：cn-beijing, cn-shanghai, ap-singapore
        region: cn-beijing
        # 访问密钥ID（Access Key ID）（必填）
        accessKeyId: your-access-key-id
        # 访问密钥（Secret Access Key）（必填）
        accessKeySecret: your-secret-access-key
        # 存储桶名称（Bucket）（必填）
        bucketName: my-bucket
        # 桶内基础路径（可选）
        basePath: /storage
        # 存储类型（可选，默认：STANDARD）
        # 可选值：STANDARD, STANDARD_IA, ARCHIVE, COLD_ARCHIVE
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
    
    // 上传图片并处理
    public void uploadImage(String key, File imageFile) {
        ImageOptions options = ImageOptions.builder()
            .format(ImageFormat.WEBP)
            .quality(80)
            .resize(800, 600)
            .build();
        UploadResponse response = storageEngine.putImage(key, imageFile, options);
    }
}
```

## 配置说明

### ⚠️ 重要配置差异

火山引擎 TOS 与其他云存储的主要配置差异：

| 配置项 | 火山引擎 TOS | AWS S3 | 阿里云 OSS |
|--------|-------------|--------|-----------|
| **engine 值** | `VOLCENGINE_TOS` | `AWS_S3` | `ALIYUN_OSS` |
| **endpoint 格式** | `tos-cn-region.volces.com` | `s3.region.amazonaws.com` | `oss-cn-region.aliyuncs.com` |
| **region 格式** | `cn-region` 或 `ap-region` | `us-east-1` | `cn-region` |
| **访问密钥名称** | Access Key ID / Secret Access Key | Access Key ID / Secret Access Key | AccessKey ID / AccessKey Secret |
| **S3 兼容性** | ✅ 兼容 | ✅ 原生 | ❌ 不兼容 |
| **图片处理** | ✅ 支持 | ❌ 不支持 | ✅ 支持 |
| **存储类型** | 4种（标准、低频、归档、冷归档） | 15+种 | 4种 |

### endpoint 配置

火山引擎 TOS 的 endpoint 格式：

- **标准格式**: `tos-cn-region.volces.com`
  - 示例：`tos-cn-beijing.volces.com`
  - 示例：`tos-cn-shanghai.volces.com`
  - 示例：`tos-ap-singapore.volces.com`

- **内网 endpoint**: `tos-cn-region-internal.volces.com`
  - 适用于同区域 ECS 访问，免流量费
  - 示例：`tos-cn-beijing-internal.volces.com`

### region 配置

火山引擎 TOS 支持的区域：

| 区域 | 代码 |
|------|------|
| 华北2（北京） | `cn-beijing` |
| 华东2（上海） | `cn-shanghai` |
| 华南1（广州） | `cn-guangzhou` |
| 华东1（杭州） | `cn-hangzhou` |
| 亚太（新加坡） | `ap-singapore` |
| 亚太（雅加达） | `ap-jakarta` |
| 美国（硅谷） | `us-east-1` |
| 美国（弗吉尼亚） | `us-west-1` |

### 存储类型

火山引擎 TOS 支持的存储类型：

| 存储类型 | 说明 | 适用场景 |
|---------|------|---------|
| `STANDARD` | 标准存储 | 频繁访问的数据 |
| `STANDARD_IA` | 低频访问存储 | 不经常访问但需要快速访问的数据 |
| `ARCHIVE` | 归档存储 | 长期保存、很少访问的数据 |
| `COLD_ARCHIVE` | 冷归档存储 | 极长期保存、极少访问的数据 |

### 访问凭证

火山引擎 TOS 使用 Access Key ID 和 Secret Access Key 进行身份验证：

1. 登录火山引擎控制台
2. 进入访问控制（IAM）服务
3. 创建用户并分配 TOS 访问权限
4. 创建访问密钥

> **安全提示**: 
> - 使用 IAM 子用户，遵循最小权限原则
> - 不要将访问密钥提交到代码仓库
> - 使用环境变量或密钥管理服务

## 功能特性

### 1. S3 兼容性

火山引擎 TOS 兼容 AWS S3 API，可以无缝迁移：

```java
// 使用 TOS 的代码与使用 S3 的代码基本相同
UploadResponse response = storageEngine.putObject("file.txt", file);
```

### 2. 图片处理

火山引擎 TOS 支持图片处理功能（需在控制台开启）：

- **格式转换**: JPEG、PNG、WEBP、GIF 等
- **缩放**: 按比例、按尺寸、按长边/短边
- **裁剪**: 矩形裁剪、圆形裁剪、圆角矩形
- **水印**: 图片水印、文字水印
- **旋转**: 自动旋转、手动旋转
- **质量调整**: JPEG/WebP 质量压缩

```java
ImageOptions options = ImageOptions.builder()
    .format(ImageFormat.WEBP)           // 转换为 WEBP 格式
    .quality(80)                         // 质量 80%
    .resize(800, 600)                    // 缩放到 800x600
    .watermark("text", "Richie")        // 添加文字水印
    .build();

UploadResponse response = storageEngine.putImage("image.jpg", file, options);
```

### 3. 存储类型自动转换

上传文件时，会根据配置的 `storageType` 自动设置对象的存储类型：

```java
// 配置为 ARCHIVE 存储类型
UploadResponse response = storageEngine.putObject("backup.zip", file);
// 文件会自动设置为归档存储类型
```

### 4. 内网访问

如果应用部署在火山引擎 ECS 上，可以使用内网 endpoint 免流量费：

```yaml
platform:
  component:
    storage:
      object:
        endpoint: tos-cn-beijing-internal.volces.com  # 内网 endpoint
```

## 最佳实践

1. **区域选择**
   - 选择距离用户最近的区域，降低延迟
   - 考虑数据合规要求

2. **存储类型选择**
   - 频繁访问：`STANDARD`
   - 偶尔访问：`STANDARD_IA`
   - 长期归档：`ARCHIVE` 或 `COLD_ARCHIVE`

3. **图片处理**
   - 上传时进行格式转换和压缩，节省存储空间
   - 使用 CDN 加速图片访问
   - 根据设备类型返回不同尺寸的图片

4. **访问凭证管理**
   - 使用 IAM 子用户，遵循最小权限原则
   - 使用环境变量或密钥管理服务
   - 定期轮换访问密钥

5. **成本优化**
   - 使用生命周期策略自动转换存储类型
   - 删除不需要的对象
   - 使用内网 endpoint 免流量费

## 常见问题

### Q: TOS 与 AWS S3 有什么区别？

A: TOS 是 S3 兼容的对象存储服务，API 与 S3 基本一致，但存储类型和支持的功能略有差异。

### Q: 如何从 S3 迁移到 TOS？

A: 由于 API 兼容，只需修改 endpoint 和访问凭证即可，代码基本无需修改。

### Q: 如何配置图片处理？

A: 在 TOS 控制台开启图片处理功能，然后通过 `ImageOptions` 配置处理参数。

### Q: 支持自定义域名吗？

A: 支持，在 TOS 控制台绑定自定义域名后，使用自定义域名作为 endpoint。

### Q: 内网访问有什么优势？

A: 内网访问免流量费，延迟更低，但仅限同区域 ECS 访问。

## 相关文档

- [核心存储组件](../richie-component-storage/README.md)
- [火山引擎 TOS 官方文档](https://www.volcengine.com/docs/6349)
- [TOS Java SDK](https://www.volcengine.com/docs/6349/1099475)

