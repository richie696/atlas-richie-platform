# Richie Component Vector - Elasticsearch

## 概述

`richie-component-vector-elasticsearch` 是 Elasticsearch 向量数据库的实现，基于 Elasticsearch 的向量检索能力提供向量存储和检索能力，支持全文+向量混合检索。

## 核心特性

- ✅ **Elasticsearch 兼容** - 完整支持 Elasticsearch 向量检索
- ✅ **混合检索** - 支持全文+向量混合检索
- ✅ **分布式架构** - 易于扩展
- ✅ **生态丰富** - 集成方便

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-vector-elasticsearch</artifactId>
    <version>${rydeen.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    vector:
      # 向量数据库提供商（必填）
      provider: ELASTICSEARCH
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
          indexType: hnsw  # 索引类型（hnsw, flat）
          replicas: 1  # 副本数量
          shards: 1  # 分片数量

# Elasticsearch配置
spring:
  elasticsearch:
    uris: http://localhost:9200  # Elasticsearch服务地址
    username: elastic  # Elasticsearch用户名（可选）
    password: elastic  # Elasticsearch密码（可选）
```

### 3. 使用

注入 `VectorService` 即可使用，接口与核心组件一致。

## 配置说明

### ⚠️ 重要配置差异

Elasticsearch 向量数据库与其他向量数据库的主要配置差异：

| 配置项 | Elasticsearch | Redis | Milvus | MongoDB |
|--------|--------------|-------|--------|---------|
| **provider 值** | `ELASTICSEARCH` | `REDIS` | `MILVUS` | `MONGODB` |
| **连接配置** | uris, username, password | host, port, password | uri, username, password | connectionString |
| **混合检索** | ✅ **支持**（全文+向量） | ❌ 不支持 | ❌ 不支持 | ✅ 支持 |
| **分片和副本** | **支持**（replicas, shards） | 不支持 | 不支持 | 支持 |

### Elasticsearch 配置

Elasticsearch 向量数据库需要配置 Elasticsearch 连接：

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200  # Elasticsearch服务地址
    username: elastic  # Elasticsearch用户名（可选）
    password: elastic  # Elasticsearch密码（可选）
```

### 索引配置

Elasticsearch 向量数据库支持分片和副本配置：

```yaml
platform:
  component:
    vector:
      indexes:
        documents:
          name: documents
          dimension: 1536  # 向量维度（必须与嵌入模型一致）
          metric: cosine  # 距离度量方式（cosine, euclidean, dot）
          indexType: hnsw  # 索引类型（hnsw, flat）
          replicas: 1  # 副本数量
          shards: 1  # 分片数量
```

## 功能特性

### 1. 混合检索

Elasticsearch 向量数据库支持全文+向量混合检索，适合需要全文检索和向量检索结合的场景。

### 2. 分布式架构

Elasticsearch 采用分布式架构，易于扩展，适合大规模数据分布式检索。

### 3. 生态丰富

Elasticsearch 生态丰富，集成方便，支持多种数据源和工具。

## 最佳实践

1. **适用场景**
   - 需要全文+向量混合检索
   - 大规模数据分布式检索
   - 日志、文档、图片等多模态检索
   - 已有 Elasticsearch 基础设施

2. **分片和副本设置**
   - 根据数据量设置合理的分片数量
   - 根据可用性要求设置副本数量
   - 考虑集群节点数量

3. **性能优化**
   - 使用批量操作减少网络开销
   - 根据业务场景设置合理的相似度阈值
   - 优化索引配置

## 常见问题

### Q: Elasticsearch 向量数据库支持哪些 Elasticsearch 版本？

A: 需要 Elasticsearch 8.0+，并启用向量检索功能。

### Q: 如何配置全文+向量混合检索？

A: 在索引配置中同时配置文本字段和向量字段，搜索时可以使用混合查询。

### Q: Elasticsearch 向量数据库的性能如何？

A: Elasticsearch 向量数据库支持大规模数据分布式检索，性能优异，适合生产环境部署。

## 相关文档

- [核心向量组件](../richie-component-vector/README.md)
- [Elasticsearch 官方文档](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)
- [Elasticsearch 向量检索](https://www.elastic.co/guide/en/elasticsearch/reference/current/dense-vector.html)

