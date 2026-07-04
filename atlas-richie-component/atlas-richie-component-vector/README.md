# Atlas Richie Vector Component (atlas-richie-component-vector)

> Parent module for **unified vector database** access. Aggregates `core` (facade) and multiple provider modules (Redis / Milvus / MongoDB Atlas / PostgreSQL pgvector / Qdrant / Neo4j / Elasticsearch / Weaviate). Selected via `platform.component.vector.provider`.

---

## 📖 Contents

- [📖 Overview](#📖-overview)
  - [What this component is — and what it isn't](#what-this-component-is-—-and-what-it-isnt)
- [✨ Features](#✨-features)
  - [Core capabilities](#core-capabilities)
  - [Design choices](#design-choices)
- [🏗️ Architecture & Module Layout](#🏗️-architecture-&-module-layout)
- [🚀 Quick Start](#🚀-quick-start)
  - [1. Add the dependency](#1-add-the-dependency)
  - [2. Configure](#2-configure)
  - [3. Use VectorService](#3-use-vectorservice)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. Document CRUD](#1-document-crud)
  - [2. Similarity search](#2-similarity-search)
  - [3. Embedding integration](#3-embedding-integration)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: Which provider should I use?](#q1-which-provider-should-i-use?)
  - [Q2: Can I use multiple providers at once?](#q2-can-i-use-multiple-providers-at-once?)
  - [Q3: How do I migrate from one provider to another?](#q3-how-do-i-migrate-from-one-provider-to-another?)
  - [Q4: Are vectors stored normalized?](#q4-are-vectors-stored-normalized?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-vector` (parent POM) |
| **Category** | Storage & retrieval — vector similarity search |
| **Hard dependencies** | `atlas-richie-context` (for `JsonUtils`) |
| **Default provider** | `redis` |

### `What` this component is — and what it isn't

| ✅ It gives you | ❌ It does not give you |
|-----------------|------------------------|
| One `VectorService` facade across 8 backends | An embedding model (use OpenAI / DashScope / local model) |
| Provider choice via `platform.component.vector.provider` | Index auto-tuning (per-provider feature) |
| Hybrid search (vector + metadata filter) | Vector compression / quantization |
| Pluggable `EmbeddingProvider` SPI | Cross-provider migration tooling |

## ✨ Features

### `Core` capabilities

- ✅ **8 providers** — Redis, Milvus, MongoDB Atlas Vector Search, PostgreSQL `pgvector`, Qdrant, Neo4j, Elasticsearch, Weaviate.
- ✅ **Unified API** — `addDocuments`, `searchByText`, `searchByVector`, `delete`, `update`.
- ✅ **Pluggable embedding** — plug OpenAI / DashScope / local models via `EmbeddingProvider` SPI.
- ✅ **Hybrid search** — combine vector similarity with metadata filtering.
- ✅ **Batch operations** — bulk insert / delete.

### `Design` choices

- ✅ **One facade, eight engines** — switch by config, not by code.
- ✅ **Provider-specific optimizations surfaced** — `VectorIndexHints`, `VectorSearchOptions`.
- ✅ **Pluggable serialization** — uses platform `JsonUtils` (Jackson 3).

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-vector                  ← parent POM
├── atlas-richie-component-vector-core         ← VectorService / VectorDocument / SPI
├── atlas-richie-component-vector-redis         ← provider: Redis
├── atlas-richie-component-vector-milvus        ← provider: Milvus
├── atlas-richie-component-vector-mongodb-atlas ← provider: MongoDB Atlas Vector Search
├── atlas-richie-component-vector-postgresql    ← provider: pgvector
├── atlas-richie-component-vector-qdrant        ← provider: Qdrant
├── atlas-richie-component-vector-neo4j         ← provider: Neo4j
├── atlas-richie-component-vector-elasticsearch ← provider: Elasticsearch
└── atlas-richie-component-vector-weaviate      ← provider: Weaviate
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-vector-core</artifactId>
</dependency>
<!-- Pick exactly one provider -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-vector-redis</artifactId>
</dependency>
```

### 2) `Configure`

```yaml
platform:
  component:
    vector:
      provider: redis                       # redis | milvus | mongodb_atlas | postgresql | qdrant | neo4j | elasticsearch | weaviate
      embedding-provider: openai
      openai:
        api-key: ${OPENAI_API_KEY}
        model: text-embedding-3-small
      collection: documents
      dimensions: 1536
```

### 3) `Use` `VectorService`

```java
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorService vectorService;

    public void index(String id, String content) {
        VectorDocument doc = new VectorDocument()
                .setId(id)
                .setContent(content)
                .setMetadata(Map.of("source", "kb"));
        vectorService.addDocument(doc);
    }

    public List<SearchResult> search(String query) {
        return vectorService.searchByText(query, 5);
    }
}
```

## 🔧 Core Capabilities

### 1) `Document` `CRUD`

```java
// Insert
vectorService.addDocument(new VectorDocument()
        .setId("doc-1")
        .setContent("...")
        .setMetadata(Map.of("type", "faq")));

// Batch
vectorService.addDocuments(List.of(...));

// Update
vectorService.updateDocument(doc);

// Delete
vectorService.deleteDocument("doc-1");
```

### 2) `Similarity` search

```java
// Text search (auto-embed)
List<SearchResult> results = vectorService.searchByText("how to reset password", 10);

// Vector search (use pre-computed embedding)
float[] embedding = openai.embed(query);
results = vectorService.searchByVector(embedding, 10);

// Hybrid: vector + metadata filter
results = vectorService.search(VectorSearchOptions.builder()
        .text(query)
        .topK(10)
        .filter("source", "kb")
        .minScore(0.75)
        .build());
```

### 3) `Embedding` integration

```java
@Component
public class OpenAiEmbeddingProvider implements EmbeddingProvider {
    @Override public float[] embed(String text) { /* call OpenAI */ }
    @Override public int dimensions() { return 1536; }
}
```

## ⚙️ Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `provider` | enum | `redis` | One of 8 providers |
| `embedding-provider` | String | `noop` | Embedding SPI name |
| `collection` | String | – | Collection / index name |
| `dimensions` | int | `1536` | Vector dimensions |
| `distance-metric` | enum | `cosine` | `cosine` / `euclidean` / `dot_product` |
| `index-type` | enum | provider-specific | `flat` / `hnsw` / `ivf_flat` etc. |

## 🎯 Best Practices

1. **Pick the right provider for your scale** — Redis < 1M vectors; Milvus / Qdrant > 1M.
2. **Always set `dimensions` explicitly** — mismatched dims = runtime error.
3. **Use `filter` for hybrid search** — vector-only is rarely the right answer.
4. **Tune `topK` and `minScore`** — `topK=100, minScore=0.5` is a sane starting point.
5. **Monitor index size + recall** — every provider has metrics; wire them to Prometheus.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **No cross-provider migration tool** | Vendor lock-in | Use the unified API so migration is config-only |
| **Embedding provider must match dimensions** | Mismatched = runtime error | Configure `dimensions` and validate at startup |
| **Hybrid search syntax differs per provider** | Some operators missing | Fall back to post-filter in Java |

## ❓ FAQ

### `Q1` — `Which` provider should `I` use?

- **Redis** — already using Redis, < 1M vectors, simple ANN.
- **Milvus / Qdrant** — large-scale (10M+), HNSW, IVF, GPU indexing.
- **MongoDB Atlas** — already on Atlas, vector + document in one query.
- **PostgreSQL pgvector** — already on Postgres, no extra infra.
- **Neo4j / Elasticsearch** — graph or text-search with vector.

### `Q2` — `Can` `I` use multiple providers at once?

Yes — declare multiple provider modules and use `@Qualifier("milvusVectorService")` etc.

### `Q3` — `How` do `I` migrate from one provider to another?

1. Configure new provider
2. Read from old, write to new (`vectorService.export` / `import`)
3. Switch `platform.component.vector.provider`
4. Drop old provider

### `Q4` — `Are` vectors stored normalized?

Provider-dependent. Cosine metric usually expects normalized vectors; check your embedding provider.

## 📚 Further Reading

- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **AI** — [`../atlas-richie-component-ai/README.md`](../atlas-richie-component-ai/README.md)
- External: [Milvus docs](https://milvus.io/docs) · [Qdrant docs](https://qdrant.tech/documentation/) · [pgvector](https://github.com/pgvector/pgvector)

---

**atlas-richie-component-vector** 🚀
