# Richie Component Vector - Milvus

## 概述

`richie-component-vector-milvus` 是 Milvus 向量数据库的实现，基于 Milvus Java SDK 提供高性能的向量存储和检索能力。

## 核心特性

- ✅ **Milvus 兼容** - 完整支持 Milvus API
- ✅ **高性能** - 支持大规模向量检索
- ✅ **多种索引类型** - 支持 HNSW、IVF、FLAT 等索引类型
- ✅ **生产环境** - 适合生产环境部署

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-vector-milvus</artifactId>
    <version>${rydeen.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    vector:
      # 向量数据库提供商（必填）
      provider: MILVUS
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
          indexType: hnsw  # 索引类型（hnsw, ivf, flat）

# Milvus配置
spring:
  ai:
    vectorstore:
      milvus:
        uri: http://localhost:19530  # Milvus服务地址
        username: root  # Milvus用户名（可选）
        password: MilvusAdmin123  # Milvus密码（可选）
        database: default  # Milvus数据库名称（可选）
```

### 3. 使用

注入 `VectorService` 即可使用，接口与核心组件一致。

## 配置说明

### ⚠️ 重要配置差异

Milvus 向量数据库与其他向量数据库的主要配置差异：

| 配置项 | Milvus | Redis | MongoDB | PostgreSQL |
|--------|--------|-------|---------|------------|
| **provider 值** | `MILVUS` | `REDIS` | `MONGODB` | `POSTGRESQL` |
| **连接配置** | uri, username, password | host, port, password | connectionString | jdbcUrl |
| **索引类型** | **丰富**（hnsw, ivf, flat等） | 简单（dimension, metric） | 简单（dimension） | 简单（dimension） |
| **适用规模** | **大规模**（千万级以上） | 中小规模（百万级以下） | 中大规模 | 中大规模 |

### Milvus 配置

Milvus 向量数据库需要配置 Milvus 连接：

```yaml
spring:
  ai:
    vectorstore:
      milvus:
        uri: http://localhost:19530  # Milvus服务地址
        username: root  # Milvus用户名（可选）
        password: MilvusAdmin123  # Milvus密码（可选）
        database: default  # Milvus数据库名称（可选）
```

### 索引配置

Milvus 向量数据库支持丰富的索引类型：

```yaml
platform:
  component:
    vector:
      indexes:
        documents:
          name: documents
          dimension: 1536  # 向量维度（必须与嵌入模型一致）
          metric: cosine  # 距离度量方式（cosine, euclidean, dot）
          indexType: hnsw  # 索引类型（hnsw, ivf, flat）
          # 索引参数（可选）
          indexParams:
            M: 16  # HNSW参数
            efConstruction: 200  # HNSW参数
```

### 索引类型

Milvus 支持的索引类型：

| 索引类型 | 说明 | 适用场景 |
|---------|------|---------|
| `hnsw` | 分层导航小世界图 | 推荐，平衡性能和精度 |
| `ivf` | 倒排文件索引 | 适合大规模数据 |
| `flat` | 暴力搜索 | 适合小规模数据，精度最高 |

## 功能特性

### 1. 高性能

Milvus 向量数据库支持大规模向量检索，适合生产环境部署。

### 2. 多种索引类型

Milvus 支持多种索引类型，可以根据业务场景选择合适的索引类型。

### 3. 生产环境

Milvus 向量数据库适合生产环境部署，支持高可用和水平扩展。

## 最佳实践

1. **适用场景**
   - 大规模向量检索（千万级以上）
   - 生产环境部署
   - 高性能要求

2. **索引类型选择**
   - **hnsw**：推荐，平衡性能和精度
   - **ivf**：适合大规模数据
   - **flat**：适合小规模数据，精度最高

3. **性能优化**
   - 根据业务场景选择合适的索引类型
   - 设置合理的索引参数
   - 使用批量操作减少网络开销

## 常见问题

### Q: Milvus 向量数据库如何部署？

A: 参考 [Milvus 官方文档](https://milvus.io/docs/install_standalone-docker.md)。

### Q: 如何选择合适的索引类型？

A: 推荐使用 **hnsw**，平衡性能和精度。如果需要最高精度，可以使用 **flat**。

### Q: Milvus 向量数据库的性能如何？

A: Milvus 向量数据库支持大规模向量检索，性能优异，适合生产环境部署。

## 相关文档

- [核心向量组件](../richie-component-vector/README.md)
- [Milvus 官方文档](https://milvus.io/docs/)

