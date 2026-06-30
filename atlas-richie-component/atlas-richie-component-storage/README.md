# Richie Component Storage

## 概述

`richie-component-storage` 是Richie平台统一的对象存储抽象组件，提供了统一的存储接口，支持多种存储后端（本地、云存储、FTP/SFTP/SMB等）。

## 线程安全与客户端生命周期

> **本组件是线程安全的，`StorageEngine` 应作为单例使用。**

### 设计原则

- `StorageEngine` 实现类注册为 Spring `@Service` Bean，默认即为**单例**，所有线程共享同一实例。
- 各云存储 SDK 客户端（OSSClient、COSClient、ObsClient、S3Client、MinioAsyncClient、Ks3、TOSV2、BlobContainerClient）在组件内部均以 **Spring 单例 Bean** 方式注册，由容器统一管理生命周期。
- `StorageEngine` 本身是**无状态**的，所有操作所需的配置通过构造函数注入，方法调用不修改任何共享可变状态，天然支持多线程并发。

### 官方 SDK 线程安全背书

| 存储引擎       | 客户端类型                 | 官方是否声明线程安全 | 推荐模式              |
|------------|-----------------------|:----------:|-------------------|
| 阿里云 OSS    | `OSSClient`           |    ✅ 是     | 单例，复用连接池          |
| 腾讯云 COS    | `COSClient`           |    ✅ 是     | 单例，内部维护连接池        |
| 华为云 OBS    | `ObsClient`           |    ✅ 是     | 单例，可在并发场景下使用      |
| 金山云 KS3    | `Ks3Client`           |    ✅ 是     | 单例，支持并发使用         |
| AWS S3     | `S3Client`            |    ✅ 是     | 单例，内部连接池          |
| MinIO      | `MinioAsyncClient`    |    ✅ 是     | 单例，Okhttp 线程安全    |
| 火山引擎 TOS   | `TOSV2`               |    ✅ 是     | 单例，Transport 线程安全 |
| Azure Blob | `BlobContainerClient` |    ✅ 是     | 单例，微软官方保证         |

### 使用建议

1. **不要手动创建 `StorageEngine` 实例**，直接通过 Spring 依赖注入获取即可：
   ```java
   @Service
   @RequiredArgsConstructor
   public class FileService {
       private final StorageEngine storageEngine; // 单例注入，线程安全
   }
   ```
2. **不要在每次操作后关闭/销毁客户端**。各 SDK 客户端内部维护了 HTTP 连接池，频繁创建和销毁会导致连接池资源泄漏（如 `ClientBuilderConfiguration` 残留），长期运行后可能造成内存膨胀和文件描述符耗尽。
3. **不要在业务代码中自行创建底层 SDK 客户端**（如 `new OSSClientBuilder().build(...)`），应统一由组件管理，避免与组件内部的单例客户端产生冲突。

### 反面示例（请勿使用）

```java
// ❌ 错误：每次请求创建新的 StorageEngine 或 SDK 客户端
public void upload(File file) {
    OSSClient client = new OSSClientBuilder().build(endpoint, credentials);
    // ... 使用 client
    client.shutdown(); // 频繁创建/销毁，导致资源泄漏
}

// ✅ 正确：通过 Spring 注入单例 StorageEngine
@Autowired
private StorageEngine storageEngine;

public void upload(File file) {
    storageEngine.putObject(key, file); // 线程安全，连接池复用
}
```

## 核心特性

- ✅ **统一存储接口** - 提供 `StorageEngine` 接口，屏蔽底层存储差异
- ✅ **多存储后端支持** - 支持本地、云存储（S3/OSS/COS/OBS等）、FTP/SFTP/SMB
- ✅ **文件上传/下载** - 支持文件、流、JSON数据的上传和下载
- ✅ **图片处理** - 支持图片上传时的格式转换和压缩
- ✅ **断点续传** - 支持大文件的断点续传下载
- ✅ **自动配置** - Spring Boot 自动配置，开箱即用

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 2. 选择存储实现

根据需求选择对应的存储实现模块：

- **本地存储**: `richie-component-storage-local`
- **AWS S3**: `richie-component-storage-s3`
- **阿里云 OSS**: `richie-component-storage-oss`
- **腾讯云 COS**: `richie-component-storage-cos`
- **华为云 OBS**: `richie-component-storage-obs`
- **MinIO**: `richie-component-storage-minio`
- **金山云 KS3**: `richie-component-storage-ks3`
- **火山引擎 TOS**: `richie-component-storage-tos`
- **Azure Blob**: `richie-component-storage-azure`
- **SFTP**: `richie-component-storage-sftp`
- **SMB**: `richie-component-storage-smb`

### 3. 配置存储

```yaml
platform:
  component:
    storage:
      # 本地存储配置
      local:
        path: ./storage/
      # 对象存储配置
      object:
        engine: minio  # 或 aws_s3, aliyun_oss, tencent_cos, huawei_obs, ksyun_ks3, volcengine_tos, azure_blob
        endpoint: http://localhost:9000
        region: us-east-1
        accessKeyId: your-access-key
        accessKeySecret: your-secret-key
        bucketName: my-bucket
        basePath: /files/
```

### 4. 使用示例

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    private final StorageEngine storageEngine;
    
    // 上传文件
    public void uploadFile(String key, File file) {
        UploadResponse response = storageEngine.putObject(key, file);
        if (response.isSuccess()) {
            log.info("上传成功: {}", response.getUrl());
        }
    }
    
    // 下载文件
    public void downloadFile(String key, File targetPath) {
        DownloadResponse<byte[]> response = storageEngine.getObject(key, targetPath, false);
        if (response.isSuccess()) {
            log.info("下载成功: {}", targetPath);
        }
    }
    
    // 上传JSON数据
    public void uploadData(String key, Map<String, Object> data) {
        UploadResponse response = storageEngine.putData(key, data);
        if (response.isSuccess()) {
            log.info("数据上传成功: {}", key);
        }
    }
    
    // 下载JSON数据
    public <T> T downloadData(String key, TypeReference<T> typeRef) {
        DownloadResponse<T> response = storageEngine.getData(key, typeRef);
        if (response.isSuccess()) {
            return response.getData();
        }
        return null;
    }
}
```

## 核心接口

### StorageEngine

```java
public interface StorageEngine {
    // 上传文件
    UploadResponse putObject(String key, File file);
    UploadResponse putObject(String key, InputStream inputStream);
    
    // 上传数据（JSON）
    UploadResponse putData(String key, Object object);
    UploadResponse putData(String key, Map<?, ?> collection);
    UploadResponse putData(String key, Collection<?> collection);
    
    // 上传图片（支持处理）
    UploadResponse putImage(String key, File file, ImageOptions options);
    UploadResponse putImage(String key, InputStream inputStream, ImageOptions options);
    
    // 下载文件
    DownloadResponse<byte[]> getObject(String key, File targetPath, boolean returnData);
    DownloadResponse<byte[]> getResumableObject(String key, String targetPath, boolean returnData);
    
    // 下载数据（JSON）
    <T> DownloadResponse<T> getData(String key, TypeReference<T> typeReference);
    
    // 检查文件是否存在
    boolean existsObject(String key);
}
```

## 配置说明

### 本地存储配置

```yaml
platform:
  component:
    storage:
      local:
        path: ./storage/  # 存储路径
        cache:
          contentMaxSize: 1048576  # 内容最大大小（字节）
```

### 对象存储配置

```yaml
platform:
  component:
    storage:
      object:
        engine: minio  # 存储引擎
        storageType: STANDARD  # 存储类型（STANDARD, IA, ARCHIVE等）
        endpoint: http://localhost:9000  # 访问端点
        region: us-east-1  # 区域
        accessKeyId: your-access-key  # 访问密钥ID
        accessKeySecret: your-secret-key  # 访问密钥
        bucketName: my-bucket  # 存储桶名称
        basePath: /files/  # 基础路径
```

### FTP/SFTP/SMB 配置

```yaml
platform:
  component:
    storage:
      ftp:
        enable: true
        host: ftp.example.com
        port: 21
        username: user
        password: pass
        basePath: /storage/
      sftp:
        enable: true
        host: sftp.example.com
        port: 22
        username: user
        password: pass
        identityFile: /path/to/key
        basePath: /storage/
      smb3:
        enable: true
        domain: example.com
        username: user
        password: pass
        basePath: /storage/
```

## 存储引擎对比

| 存储引擎 | engine 值 | endpoint 格式 | region 要求 | 特殊说明 |
|---------|----------|--------------|-------------|---------|
| MinIO | `MINIO` | `http://host:port` | 可选 | 支持自定义域名 |
| AWS S3 | `AWS_S3` | `s3.region.amazonaws.com` | 必填 | 支持多种存储类型 |
| 阿里云 OSS | `ALIYUN_OSS` | `oss-cn-region.aliyuncs.com` | 必填 | 支持图片处理 |
| 腾讯云 COS | `TENCENT_COS` | `cos.region.myqcloud.com` | 必填 | 支持多可用区存储 |
| 华为云 OBS | `HUAWEI_OBS` | `obs.region.myhuaweicloud.com` | 必填 | 支持生命周期管理 |
| 金山云 KS3 | `KSYUN_KS3` | `ks3-cn-region.ksyuncs.com` | 必填 | 兼容 S3 协议 |
| 火山引擎 TOS | `VOLCENGINE_TOS` | `tos-cn-region.volces.com` | 必填 | 支持图片处理 |
| Azure Blob | `AZURE_BLOB` | `account.blob.core.windows.net` | 必填 | 需要连接字符串 |

> **注意**: 各存储后端的配置差异较大，请参考对应的子组件文档了解详细配置说明。

## 最佳实践

1. **选择合适的存储后端**
   - 开发/测试环境：使用本地存储或 MinIO
   - 生产环境：根据云服务商选择对应的云存储

2. **文件路径规范**
   - 使用相对路径，避免绝对路径
   - 使用日期/业务维度组织路径，如：`/2024/01/15/user-123/avatar.jpg`

3. **大文件处理**
   - 使用 `getResumableObject` 支持断点续传
   - 设置 `returnData=false` 避免内存溢出

4. **错误处理**
   - 检查 `UploadResponse.isSuccess()` 和 `DownloadResponse.isSuccess()`
   - 记录错误信息用于排查问题

5. **组件边界约束（推荐）**
   - `richie-component-storage` 只提供能力接口与引擎实现，不内置 HTTP Controller
   - 业务服务自行定义上传/签发接口，避免所有引用方自动暴露同一路由
   - Controller 中仅做鉴权、参数校验、业务 key 生成，实际能力委托给 `StorageEngine`

## 业务侧 Controller 参考实现

> 说明：以下示例为“业务服务侧”的标准 MVC 用法，不应放入 storage 组件本身。

```java
package com.example.storage.controller;

import bean.com.richie.component.storage.DirectUploadPolicy;
import core.com.richie.component.storage.StorageEngine;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/storage/upload")
@Validated
@RequiredArgsConstructor
public class StorageUploadController {

    private final StorageUploadService storageUploadService;

    @PostMapping("/policy")
    public ApiResponse<DirectUploadPolicy> issuePolicy(@RequestBody @Validated IssuePolicyRequest request) {
        return ApiResponse.ok(storageUploadService.issuePolicy(request));
    }

    @Data
    public static class IssuePolicyRequest {
        @NotBlank
        private String bizType; // 例如: agent-attachment
        @NotBlank
        private String fileName;
        @Min(60)
        @Max(3600)
        private int expireSeconds = 600;
    }

    @Data
    public static class ApiResponse<T> {
        private Integer code;
        private String message;
        private T data;
        public static <T> ApiResponse<T> ok(T data) {
            ApiResponse<T> r = new ApiResponse<>();
            r.code = 0;
            r.message = "OK";
            r.data = data;
            return r;
        }
    }
}
```

```java
package com.example.storage.controller;

import bean.com.richie.component.storage.DirectUploadPolicy;

public interface StorageUploadService {
    DirectUploadPolicy issuePolicy(StorageUploadController.IssuePolicyRequest request);
}
```

```java
package com.example.storage.controller;

import bean.com.richie.component.storage.DirectUploadPolicy;
import core.com.richie.component.storage.StorageEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageUploadServiceImpl implements StorageUploadService {

    private final StorageEngine storageEngine;

    @Override
    public DirectUploadPolicy issuePolicy(StorageUploadController.IssuePolicyRequest request) {
        // 业务系统自己定义 key 规则（租户、日期、业务域等）
        String key = buildObjectKey(request.getBizType(), request.getFileName());
        // 透传调用组件能力
        return storageEngine.issueDirectUploadPolicy(key, request.getExpireSeconds());
    }

    private String buildObjectKey(String bizType, String fileName) {
        String safeBiz = (bizType == null || bizType.isBlank()) ? "default" : bizType.trim();
        String safeName = fileName.replaceAll("\\s+", "_");
        return safeBiz + "/" + java.time.LocalDate.now() + "/" + safeName;
    }
}
```

## 常见问题

### Q: 如何切换存储后端？

A: 修改配置中的 `engine` 字段，并引入对应的存储实现模块依赖。

### Q: 支持哪些图片格式？

A: 通过 `ImageOptions` 配置，支持常见的图片格式转换和压缩。

### Q: 如何实现文件去重？

A: 本地存储实现已支持基于 SHA-256 的内容去重，云存储需要根据具体实现。

## 相关文档

- [本地存储实现](./richie-component-storage-local/README.md)
- [AWS S3 实现](./richie-component-storage-s3/README.md)
- [阿里云 OSS 实现](./richie-component-storage-oss/README.md)
- [腾讯云 COS 实现](./richie-component-storage-cos/README.md)
- [华为云 OBS 实现](./richie-component-storage-obs/README.md)
- [MinIO 实现](./richie-component-storage-minio/README.md)
- [金山云 KS3 实现](./richie-component-storage-ks3/README.md)
- [火山引擎 TOS 实现](./richie-component-storage-tos/README.md)
- [Azure Blob 实现](./richie-component-storage-azure/README.md)
- [SFTP 实现](./richie-component-storage-sftp/README.md)
- [SMB 实现](./richie-component-storage-smb/README.md)

