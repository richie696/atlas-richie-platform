# Richie Component Vector - Qdrant

## 概述

`richie-component-vector-qdrant` 是 Qdrant 向量数据库的实现，基于 Qdrant Java SDK 提供高性能的向量存储和检索能力。

## 核心特性

- ✅ **Qdrant 兼容** - 完整支持 Qdrant API
- ✅ **高性能** - Rust 开发，性能优异
- ✅ **多种距离度量** - 支持多种距离度量方式
- ✅ **实时更新** - 支持实时更新

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-vector-qdrant</artifactId>
    <version>${rydeen.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    vector:
      # 向量数据库提供商（必填）
      provider: QDRANT
      # 嵌入模型提供商（必填）
      embeddingProvider: OPENAI
      # OpenAI API密钥（OpenAI嵌入模型时必填）
      apiKey: your-api-key
      # 默认索引名称（可选，默认：documents）
      defaultIndex: documents
      # 索引配置（可选）
      indexes:
        documents:
          name: documents
          dimension: 1536  # 向量维度（必须与嵌入模型一致）
          metric: cosine  # 距离度量方式（cosine, euclidean, dot）

# Qdrant配置
spring:
  ai:
    vectorstore:
      qdrant:
        host: localhost  # Qdrant服务地址
        port: 6333  # Qdrant服务端口
        api-key: your-api-key  # Qdrant API密钥（可选）
        use-tls: false  # 是否使用TLS（可选）
```

### 3. 使用

注入 `VectorService` 即可使用，接口与核心组件一致。

## 配置说明

### ⚠️ 重要配置差异

Qdrant 向量数据库与其他向量数据库的主要配置差异：

| 配置项 | Qdrant | Redis | Milvus | MongoDB |
|--------|--------|-------|--------|---------|
| **provider 值** | `QDRANT` | `REDIS` | `MILVUS` | `MONGODB` |
| **连接配置** | host, port, api-key | host, port, password | uri, username, password | uri 或 host/port/database |
| **性能** | ✅ **高性能**（Rust） | 中等 | 高性能 | 中等 |
| **实时更新** | ✅ **支持** | 支持 | 支持 | 支持 |

### Qdrant 配置

Qdrant 向量数据库需要配置 Qdrant 连接：

```yaml
spring:
  ai:
    vectorstore:
      qdrant:
        host: localhost  # Qdrant服务地址
        port: 6333  # Qdrant服务端口
        api-key: your-api-key  # Qdrant API密钥（可选）
        use-tls: false  # 是否使用TLS（可选）
```

### 索引配置

Qdrant 向量数据库的索引配置相对简单：

```yaml
platform:
  component:
    vector:
      indexes:
        documents:
          name: documents
          dimension: 1536  # 向量维度（必须与嵌入模型一致）
          metric: cosine  # 距离度量方式（cosine, euclidean, dot）
```

## 功能特性

### 1. 高性能

Qdrant 向量数据库采用 Rust 开发，性能优异，适合高性能要求的场景。

### 2. 多种距离度量

Qdrant 支持多种距离度量方式，可以根据业务场景选择合适的度量方式。

### 3. 实时更新

Qdrant 向量数据库支持实时更新，适合需要实时更新的场景。

## 最佳实践

1. **适用场景**
   - 高性能要求
   - 大规模向量检索
   - 实时更新需求
   - 生产环境部署

2. **性能优化**
   - 使用批量操作减少网络开销
   - 根据业务场景设置合理的相似度阈值
   - 优化索引配置

## 常见问题

### Q: Qdrant 向量数据库如何部署？

A: 参考 [Qdrant 官方文档](https://qdrant.tech/documentation/guides/installation/)。

### Q: Qdrant 向量数据库的性能如何？

A: Qdrant 向量数据库采用 Rust 开发，性能优异，适合高性能要求的场景。

### Q: 如何配置 TLS？

A: 设置 `use-tls: true`，并配置相应的 TLS 证书。

## 相关文档

- [核心向量组件](../richie-component-vector/README.md)
- [Qdrant 官方文档](https://qdrant.tech/documentation/)

