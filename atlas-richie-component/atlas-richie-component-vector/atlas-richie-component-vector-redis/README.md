# Richie Component Vector - Redis

## 概述

`richie-component-vector-redis` 是 Redis 向量数据库的实现，基于 Redis Stack 的向量搜索功能提供向量存储和检索能力。

## 核心特性

- ✅ **Redis Stack 兼容** - 基于 Redis Stack 的向量搜索功能
- ✅ **内存存储** - 检索速度快，延迟低
- ✅ **配置简单** - 学习成本低，运维经验丰富
- ✅ **成本低** - 适合中小规模应用

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-vector-redis</artifactId>
    <version>${rydeen.version}</version>
</dependency>
```

### 2. 配置

```yaml
platform:
  component:
    vector:
      # 向量数据库提供商（必填）
      provider: REDIS
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

# Redis配置
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: your-password
      database: 0
```

### 3. 使用

注入 `VectorService` 即可使用，接口与核心组件一致。

## 配置说明

### ⚠️ 重要配置差异

Redis 向量数据库与其他向量数据库的主要配置差异：

| 配置项 | Redis | Milvus | MongoDB | PostgreSQL |
|--------|-------|--------|---------|------------|
| **provider 值** | `REDIS` | `MILVUS` | `MONGODB` | `POSTGRESQL` |
| **Redis配置** | **必填**（spring.data.redis） | 不需要 | 不需要 | 不需要 |
| **连接配置** | host, port, password | uri, host, port | connectionString | jdbcUrl |
| **索引配置** | 简单（dimension, metric） | 复杂（多种索引类型） | 简单（dimension） | 简单（dimension） |
| **适用规模** | 中小规模（百万级以下） | 大规模（千万级以上） | 中大规模 | 中大规模 |

### Redis 配置

Redis 向量数据库需要配置 Redis 连接：

```yaml
spring:
  data:
    redis:
      host: localhost  # Redis主机地址
      port: 6379  # Redis端口
      password: your-password  # Redis密码（可选）
      database: 0  # Redis数据库索引
      # 连接池配置（可选）
      lettuce:
        pool:
          max-active: 100
          max-idle: 10
          min-idle: 5
```

### 索引配置

Redis 向量数据库的索引配置相对简单：

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

### 距离度量方式

Redis 支持的距离度量方式：

| 度量方式 | 说明 | 适用场景 |
|---------|------|---------|
| `cosine` | 余弦相似度 | 推荐，适合大多数场景 |
| `euclidean` | 欧氏距离 | 适合需要精确距离的场景 |
| `dot` | 点积 | 适合归一化向量的场景 |

## 功能特性

### 1. 内存存储

Redis 向量数据库基于内存存储，检索速度快，延迟低，适合对延迟要求极高的场景。

### 2. 配置简单

Redis 向量数据库配置简单，学习成本低，适合快速原型开发。

### 3. 成本低

Redis 向量数据库成本低，适合中小规模应用。

## 最佳实践

1. **适用场景**
   - 中小规模向量检索（百万级以下）
   - 对延迟要求极高的场景
   - 已有 Redis 基础设施的项目
   - 快速原型开发

2. **向量维度设置**
   - OpenAI text-embedding-ada-002: 1536 维
   - OpenAI text-embedding-3-small: 1536 维
   - OpenAI text-embedding-3-large: 3072 维
   - 确保向量维度与嵌入模型一致

3. **距离度量方式选择**
   - 推荐使用 **cosine**（余弦相似度）
   - 如果需要精确距离，可以使用 **euclidean**（欧氏距离）

4. **性能优化**
   - 使用 Redis 连接池减少连接开销
   - 使用批量操作减少网络开销
   - 根据业务场景设置合理的相似度阈值

## 常见问题

### Q: Redis 向量数据库支持哪些 Redis 版本？

A: 需要 Redis Stack（Redis 7.0+），并启用 RedisSearch 模块。

### Q: 如何安装 Redis Stack？

A: 参考 [Redis Stack 官方文档](https://redis.io/docs/stack/get-started/install/)。

### Q: Redis 向量数据库的性能如何？

A: Redis 向量数据库基于内存存储，检索速度快，延迟低，适合中小规模应用。

### Q: 如何迁移到其他向量数据库？

A: 由于接口统一，只需修改配置中的 `provider` 字段，代码无需修改。

## 相关文档

- [核心向量组件](../richie-component-vector/README.md)
- [Redis Stack 官方文档](https://redis.io/docs/stack/)
- [RedisSearch 文档](https://redis.io/docs/stack/search/)

