# Richie Component Vector - PostgreSQL

## 概述

`richie-component-vector-postgresql` 是 PostgreSQL 向量数据库的实现，基于 pgvector 扩展提供向量存储和检索能力，支持 ACID 事务和复杂的向量+关系查询。

## 核心特性

- ✅ **PostgreSQL 兼容** - 基于 pgvector 扩展
- ✅ **ACID 事务** - 支持事务，数据一致性好
- ✅ **混合查询** - 支持向量+关系查询
- ✅ **SQL 查询** - 成熟的 SQL 查询能力

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-vector-postgresql</artifactId>
    <version>${rydeen.version}</version>
</dependency>
```

### 2. 安装 pgvector 扩展

在 PostgreSQL 数据库中安装 pgvector 扩展：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. 配置

```yaml
platform:
  component:
    vector:
      # 向量数据库提供商（必填）
      provider: POSTGRESQL
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

# PostgreSQL配置
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/my-database
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
```

### 4. 使用

注入 `VectorService` 即可使用，接口与核心组件一致。

## 配置说明

### ⚠️ 重要配置差异

PostgreSQL 向量数据库与其他向量数据库的主要配置差异：

| 配置项 | PostgreSQL | Redis | Milvus | MongoDB |
|--------|-----------|-------|--------|---------|
| **provider 值** | `POSTGRESQL` | `REDIS` | `MILVUS` | `MONGODB` |
| **连接配置** | jdbcUrl, username, password | host, port, password | uri, username, password | uri 或 host/port/database |
| **事务支持** | ✅ **支持**（ACID） | ❌ 不支持 | ❌ 不支持 | ✅ 支持 |
| **SQL 查询** | ✅ **支持**（成熟SQL） | ❌ 不支持 | ❌ 不支持 | ✅ 支持 |

### PostgreSQL 配置

PostgreSQL 向量数据库需要配置 PostgreSQL 连接：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/my-database
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
```

### pgvector 扩展

PostgreSQL 向量数据库需要安装 pgvector 扩展：

```sql
-- 安装 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 验证扩展是否安装
SELECT * FROM pg_extension WHERE extname = 'vector';
```

### 索引配置

PostgreSQL 向量数据库的索引配置相对简单：

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

### 1. ACID 事务

PostgreSQL 向量数据库支持 ACID 事务，数据一致性好，适合需要事务支持的场景。

### 2. 混合查询

PostgreSQL 向量数据库支持向量+关系查询，适合需要复杂查询的场景。

### 3. SQL 查询

PostgreSQL 向量数据库支持成熟的 SQL 查询能力，适合需要复杂查询的场景。

## 最佳实践

1. **适用场景**
   - 需要事务支持的场景
   - 复杂的向量+关系查询
   - 已有 PostgreSQL 基础设施
   - 对数据一致性要求高的场景

2. **pgvector 扩展**
   - 确保 PostgreSQL 版本 >= 11
   - 安装 pgvector 扩展
   - 验证扩展是否安装成功

3. **性能优化**
   - 使用批量操作减少网络开销
   - 根据业务场景设置合理的相似度阈值
   - 优化索引配置

## 常见问题

### Q: PostgreSQL 向量数据库支持哪些 PostgreSQL 版本？

A: 需要 PostgreSQL 11+，并安装 pgvector 扩展。

### Q: 如何安装 pgvector 扩展？

A: 在 PostgreSQL 数据库中执行 `CREATE EXTENSION IF NOT EXISTS vector;`。

### Q: PostgreSQL 向量数据库的性能如何？

A: PostgreSQL 向量数据库支持中等规模向量检索，性能良好，适合需要事务支持的场景。

## 相关文档

- [核心向量组件](../richie-component-vector/README.md)
- [PostgreSQL 官方文档](https://www.postgresql.org/docs/)
- [pgvector 官方文档](https://github.com/pgvector/pgvector)

