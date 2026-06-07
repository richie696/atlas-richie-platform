# Richie Component Storage - 腾讯云 COS

## 概述

`richie-component-storage-cos` 是腾讯云对象存储服务（COS）的实现，基于腾讯云 COS SDK 提供完整的 COS 存储能力，支持多可用区存储、图片处理等高级功能。

## 核心特性

- ✅ **腾讯云 COS 兼容** - 完整支持腾讯云 COS API
- ✅ **多可用区存储** - 支持多可用区标准、低频、归档存储
- ✅ **图片处理** - 支持图片缩放、裁剪、水印等
- ✅ **多种存储类型** - 支持标准、低频、归档、冷归档、深冷归档
- ✅ **断点续传** - 支持大文件断点续传
- ✅ **自动配置** - Spring Boot 自动配置

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-cos</artifactId>
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
        engine: TENCENT_COS
        # COS访问域名（必填）
        # 格式：cos.region.myqcloud.com
        # 示例：cos.ap-guangzhou.myqcloud.com
        endpoint: cos.ap-guangzhou.myqcloud.com
        # 区域（必填）
        # 示例：ap-guangzhou, ap-beijing, ap-shanghai, ap-chengdu
        region: ap-guangzhou
        # 访问密钥ID（SecretId）（必填）
        accessKeyId: your-secret-id
        # 访问密钥（SecretKey）（必填）
        accessKeySecret: your-secret-key
        # 存储桶名称（Bucket）（必填）
        # 格式：bucket-name-appid（appid 为腾讯云账号 ID）
        bucketName: my-bucket-1234567890
        # 桶内基础路径（可选）
        basePath: /storage
        # 存储类型（可选，默认：STANDARD）
        # 可选值：STANDARD, STANDARD_IA, ARCHIVE, COLD_ARCHIVE, DEEP_COLD_ARCHIVE,
        #         MULTI_AZ_STANDARD, MULTI_AZ_STANDARD_IA, MULTI_AZ_ARCHIVE,
        #         MULTI_AZ_COLD_ARCHIVE, MULTI_AZ_DEEP_COLD_ARCHIVE, MULTI_AZ_INTELLIGENT_TIERING
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

腾讯云 COS 与其他云存储的主要配置差异：

| 配置项 | 腾讯云 COS | 阿里云 OSS | AWS S3 |
|--------|-----------|-----------|--------|
| **engine 值** | `TENCENT_COS` | `ALIYUN_OSS` | `AWS_S3` |
| **endpoint 格式** | `cos.region.myqcloud.com` | `oss-cn-region.aliyuncs.com` | `s3.region.amazonaws.com` |
| **region 格式** | `ap-region` | `cn-region` | `us-east-1` |
| **访问密钥名称** | SecretId / SecretKey | AccessKey ID / AccessKey Secret | Access Key ID / Secret Access Key |
| **bucketName 格式** | `bucket-name-appid` | `bucket-name` | `bucket-name` |
| **存储类型** | 11种（含多可用区） | 4种 | 15+种 |
| **多可用区存储** | ✅ 支持 | ❌ 不支持 | ❌ 不支持 |

### endpoint 配置

腾讯云 COS 的 endpoint 格式：

- **标准格式**: `cos.region.myqcloud.com`
  - 示例：`cos.ap-guangzhou.myqcloud.com`
  - 示例：`cos.ap-beijing.myqcloud.com`
  - 示例：`cos.ap-shanghai.myqcloud.com`

- **内网 endpoint**: `cos-internal.region.myqcloud.com`
  - 适用于同区域 CVM 访问，免流量费
  - 示例：`cos-internal.ap-guangzhou.myqcloud.com`

- **自定义域名**: 可在 COS 控制台绑定自定义域名

### region 配置

腾讯云 COS 支持的区域：

| 区域 | 代码 |
|------|------|
| 广州 | `ap-guangzhou` |
| 北京 | `ap-beijing` |
| 上海 | `ap-shanghai` |
| 成都 | `ap-chengdu` |
| 重庆 | `ap-chongqing` |
| 南京 | `ap-nanjing` |
| 中国香港 | `ap-hongkong` |
| 新加坡 | `ap-singapore` |
| 孟买 | `ap-mumbai` |
| 首尔 | `ap-seoul` |
| 东京 | `ap-tokyo` |
| 硅谷 | `na-siliconvalley` |
| 弗吉尼亚 | `na-ashburn` |
| 法兰克福 | `eu-frankfurt` |

### 存储类型

腾讯云 COS 支持的存储类型（含多可用区）：

| 存储类型 | 说明 | 适用场景 |
|---------|------|---------|
| `STANDARD` | 标准存储 | 频繁访问的数据 |
| `STANDARD_IA` | 低频访问存储 | 不经常访问但需要快速访问的数据 |
| `ARCHIVE` | 归档存储 | 长期保存、很少访问的数据 |
| `COLD_ARCHIVE` | 冷归档存储 | 极长期保存、极少访问的数据 |
| `DEEP_COLD_ARCHIVE` | 深冷归档存储 | 极长期保存、几乎不访问的数据 |
| `MULTI_AZ_STANDARD` | 多可用区标准存储 | 需要高可用性的频繁访问数据 |
| `MULTI_AZ_STANDARD_IA` | 多可用区低频访问存储 | 需要高可用性的偶尔访问数据 |
| `MULTI_AZ_ARCHIVE` | 多可用区归档存储 | 需要高可用性的归档数据 |
| `MULTI_AZ_COLD_ARCHIVE` | 多可用区冷归档存储 | 需要高可用性的冷归档数据 |
| `MULTI_AZ_DEEP_COLD_ARCHIVE` | 多可用区深冷归档存储 | 需要高可用性的深冷归档数据 |
| `MULTI_AZ_INTELLIGENT_TIERING` | 多可用区智能分层存储 | 需要高可用性的访问模式未知数据 |

> **注意**: 多可用区存储提供更高的可用性和数据持久性，但成本略高。

### bucketName 配置

腾讯云 COS 的存储桶名称格式特殊，必须包含 AppID：

- **格式**: `bucket-name-appid`
- **示例**: `my-bucket-1234567890`
- **AppID 获取**: 在腾讯云控制台右上角查看账号信息

### 访问凭证

腾讯云 COS 使用 SecretId 和 SecretKey 进行身份验证：

1. 登录腾讯云控制台
2. 进入访问管理（CAM）服务
3. 创建子用户并分配 COS 访问权限
4. 创建访问密钥

> **安全提示**: 
> - 使用 CAM 子用户，遵循最小权限原则
> - 不要将访问密钥提交到代码仓库
> - 使用环境变量或密钥管理服务（如腾讯云 KMS）

## 功能特性

### 1. 多可用区存储

腾讯云 COS 支持多可用区存储，提供更高的可用性和数据持久性：

```yaml
platform:
  component:
    storage:
      object:
        storageType: MULTI_AZ_STANDARD  # 多可用区标准存储
```

### 2. 图片处理

腾讯云 COS 支持图片处理功能（需在控制台开启）：

- **格式转换**: JPEG、PNG、WEBP、GIF 等
- **缩放**: 按比例、按尺寸、按长边/短边
- **裁剪**: 矩形裁剪、圆形裁剪、圆角矩形
- **水印**: 图片水印、文字水印
- **旋转**: 自动旋转、手动旋转
- **质量调整**: JPEG/WebP 质量压缩

### 3. 存储类型自动转换

上传文件时，会根据配置的 `storageType` 自动设置对象的存储类型：

```java
// 配置为 MULTI_AZ_STANDARD 存储类型
UploadResponse response = storageEngine.putObject("file.txt", file);
// 文件会自动设置为多可用区标准存储类型
```

### 4. 内网访问

如果应用部署在腾讯云 CVM 上，可以使用内网 endpoint 免流量费：

```yaml
platform:
  component:
    storage:
      object:
        endpoint: cos-internal.ap-guangzhou.myqcloud.com  # 内网 endpoint
```

## 最佳实践

1. **区域选择**
   - 选择距离用户最近的区域，降低延迟
   - 考虑数据合规要求

2. **存储类型选择**
   - 频繁访问：`STANDARD` 或 `MULTI_AZ_STANDARD`
   - 偶尔访问：`STANDARD_IA` 或 `MULTI_AZ_STANDARD_IA`
   - 长期归档：`ARCHIVE` 或 `MULTI_AZ_ARCHIVE`
   - 需要高可用性：选择多可用区存储类型

3. **bucketName 配置**
   - 必须包含 AppID，格式：`bucket-name-appid`
   - AppID 可在腾讯云控制台查看

4. **访问凭证管理**
   - 使用 CAM 子用户，遵循最小权限原则
   - 使用环境变量或密钥管理服务
   - 定期轮换访问密钥

5. **成本优化**
   - 使用生命周期策略自动转换存储类型
   - 删除不需要的对象
   - 使用内网 endpoint 免流量费

## 常见问题

### Q: bucketName 中的 AppID 是什么？

A: AppID 是腾讯云账号的唯一标识，可在控制台右上角查看。存储桶名称必须包含 AppID，格式为 `bucket-name-appid`。

### Q: 如何配置图片处理？

A: 在 COS 控制台开启图片处理功能，然后通过 `ImageOptions` 配置处理参数。

### Q: 支持自定义域名吗？

A: 支持，在 COS 控制台绑定自定义域名后，使用自定义域名作为 endpoint。

### Q: 多可用区存储有什么优势？

A: 多可用区存储提供更高的可用性（99.995%）和数据持久性（99.999999999%），适合对可用性要求高的场景。

### Q: 内网访问有什么优势？

A: 内网访问免流量费，延迟更低，但仅限同区域 CVM 访问。

## 相关文档

- [核心存储组件](../richie-component-storage/README.md)
- [腾讯云 COS 官方文档](https://cloud.tencent.com/document/product/436)
- [COS Java SDK](https://cloud.tencent.com/document/product/436/10199)
- [COS 图片处理](https://cloud.tencent.com/document/product/436/44880)

