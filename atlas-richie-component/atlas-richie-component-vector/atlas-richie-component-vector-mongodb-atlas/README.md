# Richie Component Vector - MongoDB Atlas

## 概述

`richie-component-vector-mongodb-atlas` 是 MongoDB Atlas 向量数据库的实现，基于 MongoDB 7.0+ 的向量搜索功能提供向量存储和检索能力，支持向量+标量混合查询。

## 核心特性

- ✅ **MongoDB Atlas 兼容** - 完整支持 MongoDB Atlas 向量搜索
- ✅ **混合查询** - 支持向量+标量混合查询
- ✅ **文档友好** - 天然支持复杂文档结构
- ✅ **扩展性好** - 分片集群支持大规模数据

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-vector-mongodb-atlas</artifactId>
    <version>${rydeen.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    vector:
      # 向量数据库提供商（必填）
      provider: MONGODB
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

# MongoDB配置
spring:
  data:
    mongodb:
      uri: mongodb+srv://username:password@cluster.mongodb.net/database?retryWrites=true&w=majority
      # 或
      host: localhost
      port: 27017
      database: my-database
      username: username
      password: password
```

### 3. 使用

注入 `VectorService` 即可使用，接口与核心组件一致。

## 配置说明

### ⚠️ 重要配置差异

MongoDB Atlas 向量数据库与其他向量数据库的主要配置差异：

| 配置项 | MongoDB Atlas | Redis | Milvus | PostgreSQL |
|--------|--------------|-------|--------|------------|
| **provider 值** | `MONGODB` | `REDIS` | `MILVUS` | `POSTGRESQL` |
| **连接配置** | uri 或 host/port/database | host, port, password | uri, username, password | jdbcUrl |
| **混合查询** | ✅ **支持**（向量+标量） | ❌ 不支持 | ❌ 不支持 | ✅ 支持 |
| **文档结构** | ✅ **支持**（复杂文档） | 不支持 | 不支持 | 不支持 |

### MongoDB 配置

MongoDB Atlas 向量数据库需要配置 MongoDB 连接：

```yaml
spring:
  data:
    mongodb:
      # 方式1：使用连接字符串（推荐）
      uri: mongodb+srv://username:password@cluster.mongodb.net/database?retryWrites=true&w=majority
      # 方式2：使用独立配置
      host: localhost
      port: 27017
      database: my-database
      username: username
      password: password
```

### 索引配置

MongoDB Atlas 向量数据库的索引配置相对简单：

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

### 1. 混合查询

MongoDB Atlas 向量数据库支持向量+标量混合查询，适合需要向量检索和文档查询结合的场景。

### 2. 文档友好

MongoDB Atlas 向量数据库天然支持复杂文档结构，适合需要存储丰富元数据的场景。

### 3. 扩展性好

MongoDB Atlas 向量数据库支持分片集群，适合大规模数据存储和检索。

## 最佳实践

1. **适用场景**
   - 需要向量+标量混合查询
   - 大规模数据存储和检索
   - 复杂业务逻辑场景
   - 已有 MongoDB 基础设施

2. **连接配置**
   - 推荐使用连接字符串（uri）
   - 生产环境使用 MongoDB Atlas 连接字符串
   - 开发环境可以使用本地 MongoDB

3. **性能优化**
   - 使用批量操作减少网络开销
   - 根据业务场景设置合理的相似度阈值
   - 优化索引配置

## 常见问题

### Q: MongoDB Atlas 向量数据库支持哪些 MongoDB 版本？

A: 需要 MongoDB 7.0+，并启用向量搜索功能。

### Q: 如何配置向量+标量混合查询？

A: 在查询时同时使用向量搜索和标量过滤条件，MongoDB 会自动合并结果。

### Q: MongoDB Atlas 向量数据库的性能如何？

A: MongoDB Atlas 向量数据库支持大规模数据存储和检索，性能优异，适合生产环境部署。

## 相关文档

- [核心向量组件](../richie-component-vector/README.md)
- [MongoDB Atlas 官方文档](https://www.mongodb.com/docs/atlas/)
- [MongoDB 向量搜索](https://www.mongodb.com/docs/atlas/atlas-vector-search/)

