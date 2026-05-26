# Richie Component Vector - Weaviate

## 概述

`richie-component-vector-weaviate` 是 Weaviate 向量数据库的实现，基于 Weaviate Java SDK 提供向量存储和检索能力。

## 核心特性

- ✅ **Weaviate 兼容** - 完整支持 Weaviate API
- ✅ **多种后端** - 支持多种向量数据库后端
- ✅ **灵活配置** - 支持灵活的向量数据库选择

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-vector-weaviate</artifactId>
    <version>${rydeen.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    vector:
      # 向量数据库提供商（必填）
      provider: WEAVIATE
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

# Weaviate配置
spring:
  ai:
    vectorstore:
      weaviate:
        scheme: http  # Weaviate协议（http或https）
        host: localhost  # Weaviate服务地址
        port: 8080  # Weaviate服务端口
        api-key: your-api-key  # Weaviate API密钥（可选）
```

### 3. 使用

注入 `VectorService` 即可使用，接口与核心组件一致。

## 配置说明

### ⚠️ 重要配置差异

Weaviate 向量数据库与其他向量数据库的主要配置差异：

| 配置项 | Weaviate | Redis | Milvus | MongoDB |
|--------|---------|-------|--------|---------|
| **provider 值** | `WEAVIATE` | `REDIS` | `MILVUS` | `MONGODB` |
| **连接配置** | scheme, host, port, api-key | host, port, password | uri, username, password | uri 或 host/port/database |
| **多种后端** | ✅ **支持** | ❌ 不支持 | ❌ 不支持 | ❌ 不支持 |

### Weaviate 配置

Weaviate 向量数据库需要配置 Weaviate 连接：

```yaml
spring:
  ai:
    vectorstore:
      weaviate:
        scheme: http  # Weaviate协议（http或https）
        host: localhost  # Weaviate服务地址
        port: 8080  # Weaviate服务端口
        api-key: your-api-key  # Weaviate API密钥（可选）
```

### 索引配置

Weaviate 向量数据库的索引配置相对简单：

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

### 1. 多种后端

Weaviate 向量数据库支持多种向量数据库后端，可以根据业务场景选择合适的后端。

### 2. 灵活配置

Weaviate 向量数据库支持灵活的向量数据库选择，适合需要灵活配置的场景。

## 最佳实践

1. **适用场景**
   - 需要灵活的向量数据库选择
   - 多种后端支持
   - 灵活的配置需求

2. **性能优化**
   - 使用批量操作减少网络开销
   - 根据业务场景设置合理的相似度阈值
   - 优化索引配置

## 常见问题

### Q: Weaviate 向量数据库如何部署？

A: 参考 [Weaviate 官方文档](https://weaviate.io/developers/weaviate/installation)。

### Q: Weaviate 向量数据库的性能如何？

A: Weaviate 向量数据库性能良好，适合生产环境部署。

## 相关文档

- [核心向量组件](../richie-component-vector/README.md)
- [Weaviate 官方文档](https://weaviate.io/developers/weaviate)

