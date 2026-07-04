# Atlas Richie Vector组件 (atlas-richie-component-vector)

## 📖 目录

- [📖 概述](#📖-概述)
- [✨ 功能特性](#✨-功能特性)
- [🚀 快速开始](#🚀-快速开始)
  - [1. 添加依赖](#1-添加依赖)
  - [2. 选择向量数据库实现](#2-选择向量数据库实现)
  - [3. 配置（向量库侧）](#3-配置（向量库侧）)
  - [4. EmbeddingModel 注入方式](#4-embeddingmodel-注入方式)
  - [5. 使用示例](#5-使用示例)
- [✨ 功能特性](#✨-功能特性)
  - [VectorService](#vectorservice)
- [📎 向量数据库对比](#📎-向量数据库对比)
- [⚙️ 配置参考](#⚙️-配置参考)
  - [向量数据库配置](#向量数据库配置)
- [🔧 核心能力](#🔧-核心能力)
- [❓ 常见问题](#❓-常见问题)
  - [Q: 如何切换向量数据库？](#q-如何切换向量数据库？)
  - [Q: 如何切换嵌入模型？](#q-如何切换嵌入模型？)
  - [Q: 向量维度如何确定？](#q-向量维度如何确定？)
  - [Q: 如何选择合适的距离度量方式？](#q-如何选择合适的距离度量方式？)
  - [Q: 如何优化搜索性能？](#q-如何优化搜索性能？)
- [📚 相关文档](#📚-相关文档)
---

## 📖 概述

`richie-component-vector` 是Richie平台向量数据库组件，提供统一的向量存储和检索能力，支持多种向量数据库后端（Redis、Milvus、MongoDB、PostgreSQL、Qdrant、Neo4j、Elasticsearch、Weaviate等），屏蔽不同向量数据库的差异，提供一致的 API。

## ✨ 功能特性

- ✅ **统一向量接口** - 提供 `VectorService` 接口，屏蔽底层向量数据库差异
- ✅ **多向量数据库支持** - 支持 Redis、Milvus、MongoDB、PostgreSQL、Qdrant、Neo4j、Elasticsearch、Weaviate
- ✅ **灵活嵌入模型接入** - 支持自动注入或业务侧手工注入 `EmbeddingModel`
- ✅ **向量搜索** - 支持向量相似度搜索、文本搜索
- ✅ **自动配置** - Spring Boot 自动配置，开箱即用

## 🚀 快速开始

### 1) 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-vector-core</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

### 2) 选择向量数据库实现

根据需求选择对应的向量数据库实现模块：

- **Redis**: `richie-component-vector-redis`
- **Milvus**: `richie-component-vector-milvus`
- **MongoDB Atlas**: `richie-component-vector-mongodb-atlas`
- **PostgreSQL**: `richie-component-vector-postgresql`
- **Qdrant**: `richie-component-vector-qdrant`
- **Neo4j**: `richie-component-vector-neo4j`
- **Elasticsearch**: `richie-component-vector-elasticsearch`
- **Weaviate**: `richie-component-vector-weaviate`

### 3) 配置（向量库侧）

```yaml
platform:
  component:
    vector:
      # 向量数据库提供商（必填）
      provider: REDIS  # 或 MILVUS, MONGODB, POSTGRESQL, QDRANT, NEO4J, ELASTICSEARCH, WEAVIATE
      # 默认索引名称（可选，默认：documents）
      defaultIndex: documents
      # 索引配置（可选）
      indexes:
        documents:
          name: documents
          dimension: 1536  # 向量维度
          metric: cosine  # 距离度量方式（cosine, euclidean, dot）
          indexType: hnsw  # 索引类型
          replicas: 1
          shards: 1
```

### 4) `EmbeddingModel` 注入方式

`richie-component-vector` 只消费 `EmbeddingModel`，不再在组件内自行创建。你可以选择以下两种方式：

#### 方式A：引入 `richie-component-ai`（推荐）

- `richie-component-ai` 会在配置初始化模式下自动注入默认 `EmbeddingModel`
- `VectorService` 会直接复用该 Bean，无需额外配置

#### 方式B：不引入 `richie-component-ai`，业务手工声明 `EmbeddingModel`

```java
@Configuration
public class VectorEmbeddingConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        OpenAiApi api = OpenAiApi.builder()
                .apiKey("your-api-key")
                .baseUrl("https://api.openai.com")
                .build();
        return new OpenAiEmbeddingModel(api);
    }
}
```

若既未引入 `richie-component-ai`，也未手工声明 `EmbeddingModel`，`VectorService` 将无法初始化并在启动期报错。

### 5) 使用示例

```java
@Service
@RequiredArgsConstructor
public class VectorService {
    
    private final VectorService vectorService;
    
    // 添加文档
    public String addDocument(String text, Map<String, Object> metadata) {
        VectorDocument document = new VectorDocument()
            .setText(text)
            .setMetadata(metadata);
        return vectorService.addDocument(document);
    }
    
    // 批量添加文档
    public List<String> addDocuments(List<VectorDocument> documents) {
        return vectorService.addDocuments(documents);
    }
    
    // 文本搜索
    public List<VectorSearchResult> searchByText(String query, int limit) {
        return vectorService.searchByText(query, limit);
    }
    
    // 向量搜索
    public List<VectorSearchResult> searchByVector(float[] vector, int limit) {
        return vectorService.searchByVector(vector, limit);
    }
    
    // 带相似度阈值的搜索
    public List<VectorSearchResult> searchByVector(float[] vector, int limit, double minScore) {
        return vectorService.searchByVector(vector, limit, minScore);
    }
    
    // 删除文档
    public void deleteDocument(String id) {
        vectorService.deleteDocument(id);
    }
    
    // 获取文档
    public VectorDocument getDocument(String id) {
        return vectorService.getDocument(id);
    }
}
```

## ✨ 功能特性

### `VectorService`

```java
public interface VectorService {
    // 添加文档
    String addDocument(VectorDocument document);
    List<String> addDocuments(List<VectorDocument> documents);
    
    // 更新文档
    void updateDocument(String id, VectorDocument document);
    
    // 删除文档
    void deleteDocument(String id);
    void deleteDocuments(List<String> ids);
    
    // 获取文档
    VectorDocument getDocument(String id);
    List<VectorDocument> getDocuments(List<String> ids);
    
    // 搜索
    List<VectorSearchResult> search(VectorQuery query);
    List<VectorSearchResult> searchByVector(float[] vector, int limit);
    List<VectorSearchResult> searchByText(String text, int limit);
    List<VectorSearchResult> searchByVector(float[] vector, int limit, double minScore);
    List<VectorSearchResult> searchByText(String text, int limit, double minScore);
}
```

## 📎 向量数据库对比

| 向量数据库 | provider 值 | 特点 | 适用场景 |
|-----------|------------|------|---------|
| Redis | `REDIS` | 内存存储，检索速度快，配置简单 | 中小规模向量检索（百万级以下） |
| Milvus | `MILVUS` | 高性能，支持大规模向量检索 | 大规模向量检索，生产环境 |
| MongoDB Atlas | `MONGODB` | 文档友好，支持向量+标量混合查询 | 需要向量+文档混合查询 |
| PostgreSQL | `POSTGRESQL` | 关系型数据库，ACID事务支持 | 需要事务支持的场景 |
| Qdrant | `QDRANT` | 高性能，Rust开发，支持多种距离度量 | 高性能要求，大规模向量检索 |
| Neo4j | `NEO4J` | 图数据库，支持向量检索与图算法结合 | 知识图谱、社交网络等复杂关系场景 |
| Elasticsearch | `ELASTICSEARCH` | 支持全文+向量混合检索 | 需要全文+向量混合检索 |
| Weaviate | `WEAVIATE` | 支持多种向量数据库后端 | 需要灵活的向量数据库选择 |

> **注意**: 各向量数据库的配置差异较大，请参考对应的子组件文档了解详细配置说明。

## ⚙️ 配置参考

### 向量数据库配置

```yaml
platform:
  component:
    vector:
      provider: REDIS  # 向量数据库提供商
      defaultIndex: documents  # 默认索引名称
      indexes:
        documents:
          name: documents
          dimension: 1536  # 向量维度（必须与嵌入模型一致）
          metric: cosine  # 距离度量方式（cosine, euclidean, dot）
          indexType: hnsw  # 索引类型（不同数据库支持不同）
          replicas: 1  # 副本数量
          shards: 1  # 分片数量
```

## 🔧 核心能力

1. **选择合适的向量数据库**
   - 中小规模：Redis
   - 大规模：Milvus、Qdrant
   - 需要混合查询：MongoDB、Elasticsearch
   - 需要事务支持：PostgreSQL
   - 需要图结构：Neo4j

2. **向量维度设置**
   - OpenAI text-embedding-ada-002: 1536 维
   - OpenAI text-embedding-3-small: 1536 维
   - OpenAI text-embedding-3-large: 3072 维
   - 智谱 embedding-2: 1024 维
   - 确保向量维度与嵌入模型一致

3. **距离度量方式选择**
   - **cosine**（余弦相似度）：推荐，适合大多数场景
   - **euclidean**（欧氏距离）：适合需要精确距离的场景
   - **dot**（点积）：适合归一化向量的场景

4. **索引类型选择**
   - **hnsw**（分层导航小世界图）：推荐，平衡性能和精度
   - **ivf**（倒排文件索引）：适合大规模数据
   - **flat**（暴力搜索）：适合小规模数据，精度最高

5. **相似度阈值设置**
   - 高精度匹配：0.8-0.9
   - 一般匹配：0.6-0.8
   - 宽松匹配：0.4-0.6
   - 根据实际业务场景调整

## ❓ 常见问题

### `Q` — 如何切换向量数据库？

A: 修改配置中的 `provider` 字段，并引入对应的向量数据库实现模块依赖。

### `Q` — 如何切换嵌入模型？

A: 如果使用 `richie-component-ai`，在 AI 组件模型配置中调整默认模型即可；如果是手工声明 `EmbeddingModel`，则替换业务侧 Bean 实现。

### `Q` — 向量维度如何确定？

A: 向量维度由嵌入模型决定，必须与嵌入模型的输出维度一致。例如，OpenAI text-embedding-ada-002 输出 1536 维向量。

### `Q` — 如何选择合适的距离度量方式？

A: 推荐使用 **cosine**（余弦相似度），适合大多数场景。如果需要精确距离，可以使用 **euclidean**（欧氏距离）。

### `Q` — 如何优化搜索性能？

A: 
- 选择合适的索引类型（如 HNSW）
- 设置合理的分片和副本数量
- 使用批量操作减少网络开销
- 根据业务场景设置合理的相似度阈值

## 📚 相关文档

- Redis 向量数据库实现
- Milvus 向量数据库实现
- MongoDB Atlas 向量数据库实现
- PostgreSQL 向量数据库实现
- Qdrant 向量数据库实现
- Neo4j 向量数据库实现
- Elasticsearch 向量数据库实现
- Weaviate 向量数据库实现

