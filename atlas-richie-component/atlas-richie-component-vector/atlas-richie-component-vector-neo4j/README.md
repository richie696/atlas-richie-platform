# Richie Component Vector - Neo4j

## 概述

`richie-component-vector-neo4j` 是 Neo4j 向量数据库的实现，基于 Neo4j 的图数据库与向量检索能力提供向量存储和检索能力，支持向量检索与图算法结合。

## 核心特性

- ✅ **Neo4j 兼容** - 完整支持 Neo4j 向量检索
- ✅ **图数据库** - 天然支持关系型和图结构数据
- ✅ **图算法结合** - 支持向量检索与图算法结合
- ✅ **可扩展性强** - 支持大规模图数据

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-vector-neo4j</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    vector:
      # 向量数据库提供商（必填）
      provider: NEO4J
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

# Neo4j配置
spring:
  neo4j:
    uri: bolt://localhost:7687  # Neo4j服务地址
    authentication:
      username: neo4j  # Neo4j用户名
      password: password  # Neo4j密码
```

### 3. 使用

注入 `VectorService` 即可使用，接口与核心组件一致。

## 配置说明

### ⚠️ 重要配置差异

Neo4j 向量数据库与其他向量数据库的主要配置差异：

| 配置项 | Neo4j | Redis | Milvus | MongoDB |
|--------|-------|-------|--------|---------|
| **provider 值** | `NEO4J` | `REDIS` | `MILVUS` | `MONGODB` |
| **连接配置** | uri, username, password | host, port, password | uri, username, password | uri 或 host/port/database |
| **图结构** | ✅ **支持**（图数据库） | ❌ 不支持 | ❌ 不支持 | ❌ 不支持 |
| **图算法** | ✅ **支持**（图算法结合） | ❌ 不支持 | ❌ 不支持 | ❌ 不支持 |

### Neo4j 配置

Neo4j 向量数据库需要配置 Neo4j 连接：

```yaml
spring:
  neo4j:
    uri: bolt://localhost:7687  # Neo4j服务地址
    authentication:
      username: neo4j  # Neo4j用户名
      password: password  # Neo4j密码
```

### 索引配置

Neo4j 向量数据库的索引配置相对简单：

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

### 1. 图数据库

Neo4j 向量数据库是图数据库，天然支持关系型和图结构数据，适合需要图结构与向量检索结合的场景。

### 2. 图算法结合

Neo4j 向量数据库支持向量检索与图算法结合，适合知识图谱、社交网络、推荐系统等复杂关系场景。

### 3. 可扩展性强

Neo4j 向量数据库支持大规模图数据，适合复杂实体关系分析。

## 最佳实践

1. **适用场景**
   - 需要图结构与向量检索结合的场景
   - 知识图谱、社交网络、推荐系统
   - 复杂实体关系分析
   - 已有 Neo4j 基础设施

2. **性能优化**
   - 使用批量操作减少网络开销
   - 根据业务场景设置合理的相似度阈值
   - 优化索引配置

## 常见问题

### Q: Neo4j 向量数据库支持哪些 Neo4j 版本？

A: 需要 Neo4j 5.0+，并启用向量检索功能。

### Q: 如何配置图算法结合？

A: 在查询时同时使用向量搜索和图算法，Neo4j 会自动合并结果。

### Q: Neo4j 向量数据库的性能如何？

A: Neo4j 向量数据库支持大规模图数据，性能良好，适合复杂关系场景。

## 相关文档

- [核心向量组件](../richie-component-vector/README.md)
- [Neo4j 官方文档](https://neo4j.com/docs/)
- [Neo4j 向量检索](https://neo4j.com/docs/cypher-manual/current/indexes-for-vector-search/)

