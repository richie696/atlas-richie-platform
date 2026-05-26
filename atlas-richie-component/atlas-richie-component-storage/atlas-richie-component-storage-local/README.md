# Richie Component Storage - Local

## 概述

`richie-component-storage-local` 是本地文件存储实现，提供高性能的本地文件存储能力，支持文件元数据管理、缓存优化、内容去重等功能。

## 核心特性

- ✅ **本地文件存储** - 基于文件系统的本地存储
- ✅ **文件元数据管理** - 自动维护文件元数据到数据库
- ✅ **缓存优化** - 支持文件存在性、元数据、内容三级缓存
- ✅ **内容去重** - 基于 SHA-256 的内容指纹去重
- ✅ **路径安全** - 防止目录穿越攻击
- ✅ **自动清理** - 支持冷数据自动清理任务
- ✅ **数据库 Schema 管理** - 支持自动建表和 Liquibase 迁移

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-storage-local</artifactId>
    <version>${rydeen.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    storage:
      local:
        # 是否启用本地存储（默认：true）
        enable: true
        # 本地存储路径（默认：./storage/）
        path: ./storage/
        # 缓存配置
        cache:
          # 文件存在性缓存过期时间（毫秒，默认：3600000 = 1小时）
          existsTtl: 3600000
          # 文件元数据缓存过期时间（毫秒，默认：1800000 = 30分钟）
          metadataTtl: 1800000
          # 文件内容缓存过期时间（毫秒，默认：600000 = 10分钟）
          contentTtl: 600000
          # 文件内容缓存大小限制（字节，默认：1048576 = 1MB，仅缓存小文件）
          contentMaxSize: 1048576
          # 是否启用缓存统计（默认：true）
          statisticsEnabled: true
        # 数据库 Schema 配置
        schema:
          # 启动时是否自动检测并创建缺失表（默认：true）
          enableAutoDdl: true
          # 表名前缀（可选，默认：rd_）
          tablePrefix: rd_
          # 元数据表名（默认：rd_file_metadata）
          metadataTable: rd_file_metadata
          # 是否启用Liquibase（默认：true）
          enableLiquibase: true
          # Liquibase变更日志路径
          liquibaseChangeLog: classpath:db/changelog/db.changelog-master.yaml
          # Liquibase执行模式：true=仅输出SQL，false=实际执行（默认：false）
          liquibaseDryRun: false
        # 冷数据清理配置
        cleanup:
          # 是否启用清理（默认：false）
          enabled: false
          # 保留天数（默认：180天）
          retentionDays: 180
          # 每次最大删除数量（默认：1000）
          maxDeletePerRun: 1000
          # 是否仅打印SQL/操作而不执行（默认：false）
          dryRun: false
          # 执行时间：每天03:00（默认：0 0 3 * * ?）
          cron: "0 0 3 * * ?"
          # 是否同时删除数据库元数据（默认：true）
          removeMetadata: true
```

### 3. 使用

注入 `StorageEngine`（Bean 名称为 `localStorageEngine`）即可使用：

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    @Qualifier("localStorageEngine")
    private final StorageEngine storageEngine;
    
    public void uploadFile(String key, File file) {
        UploadResponse response = storageEngine.putObject(key, file);
        if (response.isSuccess()) {
            log.info("上传成功: key={}, hash={}", key, response.getHashValue());
        }
    }
}
```

## 配置说明

### ⚠️ 重要配置差异

本地存储与其他云存储的主要配置差异：

| 配置项 | 本地存储 | 云存储 |
|--------|---------|--------|
| **配置前缀** | `platform.component.storage.local` | `platform.component.storage.object` |
| **engine 字段** | 不需要 | 必填（如 `MINIO`, `AWS_S3` 等） |
| **endpoint** | 不需要 | 必填 |
| **region** | 不需要 | 必填（部分云存储） |
| **accessKeyId** | 不需要 | 必填 |
| **accessKeySecret** | 不需要 | 必填 |
| **bucketName** | 不需要 | 必填 |
| **path** | **必填**（存储路径） | 不需要 |
| **缓存配置** | **支持**（三级缓存） | 不支持 |
| **数据库元数据** | **支持** | 不支持 |
| **内容去重** | **支持**（SHA-256） | 不支持 |

### 路径配置

- **相对路径**: `./storage/` - 相对于应用运行目录
- **绝对路径**: `/data/storage/` - 绝对路径
- **默认路径**: 如果不配置，默认为 `./storage/`

### 缓存配置

本地存储提供三级缓存机制：

1. **文件存在性缓存** - 缓存文件是否存在，减少文件系统查询
2. **文件元数据缓存** - 缓存文件大小、哈希值等元数据
3. **文件内容缓存** - 仅缓存小文件（默认 1MB 以下）的内容

### 数据库 Schema

本地存储会自动创建 `rd_file_metadata` 表用于存储文件元数据，包括：
- 文件路径（key）
- 原始文件名
- 内容类型
- 文件大小
- SHA-256 哈希值
- 版本ID
- 物理路径
- 上传时间
- 访问次数

## 功能特性

### 1. 内容去重

基于 SHA-256 的内容指纹，相同内容的文件不会重复存储：

```java
// 上传相同内容的文件，第二次上传会直接返回，不会重复写盘
UploadResponse response1 = storageEngine.putObject("file1.txt", file);
UploadResponse response2 = storageEngine.putObject("file2.txt", file); // 相同内容
// response1 和 response2 的 hashValue 相同
```

### 2. 路径安全

自动防止目录穿越攻击：

```java
// ❌ 这些路径会被拒绝
storageEngine.putObject("../etc/passwd", file);  // 包含 ..
storageEngine.putObject("C:/Windows/System32", file);  // 包含 :
storageEngine.putObject("/etc/passwd", file);  // 绝对路径

// ✅ 这些路径是安全的
storageEngine.putObject("user/123/avatar.jpg", file);
storageEngine.putObject("2024/01/15/document.pdf", file);
```

### 3. 原子写入

使用临时文件 + 原子移动确保写入的原子性，避免文件损坏。

### 4. 冷数据清理

支持定时清理超过指定天数的文件：

```yaml
platform:
  component:
    storage:
      local:
        cleanup:
          enabled: true
          retentionDays: 180  # 保留180天
          cron: "0 0 3 * * ?"  # 每天凌晨3点执行
```

## 最佳实践

1. **路径组织**
   - 使用业务维度组织路径：`/user/{userId}/avatar.jpg`
   - 使用日期维度组织路径：`/2024/01/15/document.pdf`
   - 避免过深的目录层级

2. **缓存策略**
   - 小文件（< 1MB）会自动缓存内容
   - 大文件只缓存元数据，不缓存内容
   - 根据业务场景调整缓存过期时间

3. **数据库元数据**
   - 启用数据库元数据管理，便于文件查询和统计
   - 定期清理过期的元数据记录

4. **存储路径**
   - 生产环境使用绝对路径
   - 确保应用有读写权限
   - 考虑磁盘空间和备份策略

## 常见问题

### Q: 本地存储支持分布式部署吗？

A: 不支持。本地存储基于文件系统，多实例部署时每个实例只能访问自己的本地文件系统。如需分布式存储，请使用云存储或 MinIO。

### Q: 如何迁移本地存储的文件？

A: 直接复制文件目录，并确保数据库元数据同步迁移。

### Q: 缓存失效策略是什么？

A: 基于时间的过期策略，文件存在性缓存 1 小时，元数据缓存 30 分钟，内容缓存 10 分钟。

### Q: 如何清理缓存？

A: 可以通过 `LocalStorageEngine.clearFileCaches(key)` 方法手动清理指定文件的缓存。

## 相关文档

- [核心存储组件](../richie-component-storage/README.md)

