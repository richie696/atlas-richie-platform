# Richie Component Storage - 阿里云 OSS

## 概述

`richie-component-storage-oss` 是阿里云对象存储服务（OSS）的实现，基于阿里云 OSS SDK 提供完整的 OSS 存储能力，支持图片处理、视频处理等高级功能。

## 核心特性

- ✅ **阿里云 OSS 兼容** - 完整支持阿里云 OSS API
- ✅ **图片处理** - 支持图片缩放、裁剪、水印等
- ✅ **多种存储类型** - 支持标准、低频、归档、冷归档
- ✅ **断点续传** - 支持大文件断点续传
- ✅ **双模式架构** - 支持自动配置（Auto-Init）与手动注册（Manual Registry）两种初始化模式，灵活适配 Spring Boot 自动装配及非 Spring 环境
- ✅ **自动配置** - Spring Boot 自动配置

## 双模式架构

本组件支持两种初始化模式：

### 1. 自动模式（Auto-Init，默认）

`auto-init: true`（默认值）时，Spring Boot 自动装配负责：

- 根据 `engine: ALIYUN_OSS` 配置创建 `OssStorageEngineProvider` Bean
- 调用 Provider 的 `create(properties)` 创建引擎实例
- 引擎实例通过 `@PostConstruct initializeBucket()` 探测桶 / 校验前缀
- 通过 `@Qualifier("objectStorageEngine")` 注入使用

### 2. 手动模式（Manual）

`auto-init: false` 时，引擎由 `StorageEngineRegistry` 通过 SPI 发现并管理：

- Provider 不自动注册为 Bean，由 `ServiceLoader` 发现
- 通过 `registry.switchEngine(StorageEngineEnum.ALIYUN_OSS)` 运行时切换
- 适用于非 Spring 环境或多引擎动态切换场景

```java
// 手动模式：运行时切换引擎
storageEngineRegistry.switchEngine(StorageEngineEnum.ALIYUN_OSS);
UploadResponse response = storageEngineRegistry.getCurrentEngine()
    .putObject("key", file);
```

## StorageEngineProvider

每个实现包都提供 `StorageEngineProvider` SPI 实现，`OssStorageEngineProvider` 负责：

| 方法 | 说明 |
|------|------|
| `supportedEngineType()` | 返回 `StorageEngineEnum.ALIYUN_OSS` |
| `create(properties)` | 从配置创建 `OSSClient` 和 `OssStorageEngine` |
| `validate(properties)` | 校验 endpoint / accessKeyId / accessKeySecret / bucketName 必填 |
| `afterPropertiesSet(engine)` | 手动模式下触发桶探测与前缀校验 |
| `destroy(engine)` | 释放客户端资源 |

自动模式下 Provider 在 `OssAutoConfiguration` 中注册为 Bean；手动模式下由 Registry 通过 SPI 发现。

## 参数校验 (ConfigValidation)

引擎创建前会通过 `ConfigValidation` 工具类校验必填参数，校验失败时抛出 `IllegalArgumentException`：

| 参数 | 校验规则 |
|------|---------|
| endpoint | 非空 |
| accessKeyId | 非空 |
| accessKeySecret | 非空 |
| bucketName | 非空 |

## 直传策略 (DirectUploadPolicy)

阿里云 OSS 引擎支持通过预签名 URL 实现客户端直传到对象存储，减少服务端流量压力：

| 字段 | 说明 |
|------|------|
| uploadUrl | 预签名上传 URL |
| method | HTTP 方法（PUT） |
| headers | 签名头信息 |
| expireAt | 策略过期时间 |
| success | 策略是否可用 |

```java
DirectUploadPolicy policy = storageEngine.issueDirectUploadPolicy(
    "uploads/example.jpg", 3600);
// 返回预签名 PUT URL，客户端可直接上传
```

## 快速开始

### 1. 添加依赖

```xml
<!-- 必备核心库 -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-core</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
<!-- 实现库 -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-oss</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    storage:
      object:
        # 存储引擎类型（必填）
        engine: ALIYUN_OSS
        # OSS访问域名（必填）
        # 格式：oss-cn-region.aliyuncs.com
        # 示例：oss-cn-hangzhou.aliyuncs.com
        endpoint: oss-cn-hangzhou.aliyuncs.com
        # 区域（必填）
        # 示例：cn-hangzhou, cn-beijing, cn-shanghai, cn-shenzhen
        region: cn-hangzhou
        # 访问密钥ID（AccessKey ID）（必填）
        accessKeyId: your-access-key-id
        # 访问密钥（AccessKey Secret）（必填）
        accessKeySecret: your-access-key-secret
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

阿里云 OSS 与其他云存储的主要配置差异：

| 配置项 | 阿里云 OSS | AWS S3 | 腾讯云 COS |
|--------|-----------|--------|-----------|
| **engine 值** | `ALIYUN_OSS` | `AWS_S3` | `TENCENT_COS` |
| **endpoint 格式** | `oss-cn-region.aliyuncs.com` | `s3.region.amazonaws.com` | `cos.region.myqcloud.com` |
| **region 格式** | `cn-region` | `us-east-1` | `ap-region` |
| **访问密钥名称** | AccessKey ID / AccessKey Secret | Access Key ID / Secret Access Key | SecretId / SecretKey |
| **存储类型** | 4种（标准、低频、归档、冷归档） | 15+种 | 10+种 |
| **图片处理** | ✅ 支持（内置） | ❌ 不支持 | ✅ 支持（需配置） |

### endpoint 配置

阿里云 OSS 的 endpoint 格式：

- **标准格式**: `oss-cn-region.aliyuncs.com`
  - 示例：`oss-cn-hangzhou.aliyuncs.com`
  - 示例：`oss-cn-beijing.aliyuncs.com`
  - 示例：`oss-cn-shanghai.aliyuncs.com`

- **内网 endpoint**: `oss-cn-region-internal.aliyuncs.com`
  - 适用于同区域 ECS 访问，免流量费
  - 示例：`oss-cn-hangzhou-internal.aliyuncs.com`

- **自定义域名**: 可在 OSS 控制台绑定自定义域名

### region 配置

阿里云 OSS 支持的区域：

| 区域 | 代码 |
|------|------|
| 华东1（杭州） | `cn-hangzhou` |
| 华东2（上海） | `cn-shanghai` |
| 华北1（青岛） | `cn-qingdao` |
| 华北2（北京） | `cn-beijing` |
| 华北3（张家口） | `cn-zhangjiakou` |
| 华北5（呼和浩特） | `cn-huhehaote` |
| 华南1（深圳） | `cn-shenzhen` |
| 华南2（河源） | `cn-heyuan` |
| 西南1（成都） | `cn-chengdu` |
| 中国（香港） | `cn-hongkong` |
| 美国（硅谷） | `us-west-1` |
| 美国（弗吉尼亚） | `us-east-1` |
| 新加坡 | `ap-southeast-1` |
| 澳大利亚（悉尼） | `ap-southeast-2` |
| 日本（东京） | `ap-northeast-1` |
| 印度（孟买） | `ap-south-1` |
| 德国（法兰克福） | `eu-central-1` |
| 英国（伦敦） | `eu-west-1` |
| 阿联酋（迪拜） | `me-east-1` |

### 存储类型

阿里云 OSS 支持的存储类型：

| 存储类型 | 说明 | 适用场景 |
|---------|------|---------|
| `STANDARD` | 标准存储 | 频繁访问的数据 |
| `STANDARD_IA` | 低频访问存储 | 不经常访问但需要快速访问的数据 |
| `ARCHIVE` | 归档存储 | 长期保存、很少访问的数据 |
| `COLD_ARCHIVE` | 冷归档存储 | 极长期保存、极少访问的数据 |

### 访问凭证

阿里云 OSS 使用 AccessKey ID 和 AccessKey Secret 进行身份验证：

1. 登录阿里云控制台
2. 进入访问控制（RAM）服务
3. 创建用户并分配 OSS 访问权限
4. 创建访问密钥

> **安全提示**: 
> - 使用 RAM 子账号，遵循最小权限原则
> - 不要将访问密钥提交到代码仓库
> - 使用环境变量或密钥管理服务（如阿里云 KMS）

## 功能特性

### 1. 图片处理

阿里云 OSS 内置图片处理功能，支持：

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

### 2. 存储类型自动转换

上传文件时，会根据配置的 `storageType` 自动设置对象的存储类型：

```java
// 配置为 ARCHIVE 存储类型
UploadResponse response = storageEngine.putObject("backup.zip", file);
// 文件会自动设置为归档存储类型
```

### 3. 内网访问

如果应用部署在阿里云 ECS 上，可以使用内网 endpoint 免流量费：

```yaml
platform:
  component:
    storage:
      object:
        endpoint: oss-cn-hangzhou-internal.aliyuncs.com  # 内网 endpoint
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
   - 使用 RAM 子账号，遵循最小权限原则
   - 使用环境变量或密钥管理服务
   - 定期轮换访问密钥

5. **成本优化**
   - 使用生命周期策略自动转换存储类型
   - 删除不需要的对象
   - 使用内网 endpoint 免流量费

## 常见问题

### Q: 如何配置图片处理？

A: 在 OSS 控制台开启图片处理功能，然后通过 `ImageOptions` 配置处理参数。

### Q: 支持自定义域名吗？

A: 支持，在 OSS 控制台绑定自定义域名后，使用自定义域名作为 endpoint。

### Q: 如何设置对象的访问权限？

A: 可以在 OSS 控制台设置存储桶和对象的访问权限，或通过 SDK 设置 ACL。

### Q: 内网访问有什么优势？

A: 内网访问免流量费，延迟更低，但仅限同区域 ECS 访问。

## 相关文档

- [核心存储组件 (Core SPI)](../atlas-richie-component-storage-core/README.md)
- [阿里云 OSS 官方文档](https://help.aliyun.com/product/31815.html)
- [OSS Java SDK](https://help.aliyun.com/document_detail/32011.html)
- [OSS 图片处理](https://help.aliyun.com/document_detail/44688.html)
- [直传策略 (DirectUploadPolicy)](../atlas-richie-component-storage-core/README.md#配置模型)
- [存储引擎 SPI (StorageEngineProvider)](../atlas-richie-component-storage-core/README.md)

