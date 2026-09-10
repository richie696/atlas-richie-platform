# Tencent Cloud VectorDB Provider

This optional provider binds one named Store to one Tencent VectorDB database/collection. The connection handle owns and redacts credentials; Core never exposes Tencent SDK types.

## ACL-safe hybrid behavior

Dense-only is the default. With `hybrid-enabled: true` and a named `SparseVectorizer`, the provider creates or validates a collection containing `vector` and `sparse_vector` indexes, writes both vectors atomically, and registers `VectorAclAwareHybridSearchOperations` plus `ACL_SAFE_HYBRID`.

The request policy is deliberately narrow:

1. Build a Tencent `HybridSearchParam` containing `AnnOption`, `MatchOption`, and the same mandatory ACL filter.
2. Use the server result when native hybrid succeeds.
3. Only when Tencent explicitly reports the hybrid/rerank request as unsupported, and only if `hybrid-fallback-mode: core-rrf`, issue separate dense and sparse requests with that same filter and apply Core weighted RRF.
4. Authentication, IAM, quota, timeout, network and ordinary data errors fail directly. They never fall back.

This prevents an ACL-denied document from participating in either candidate branch or in rank fusion.

## Configuration

```yaml
platform.component.vector:
  connections:
    tencent:
      provider: tencent-vectordb
      settings:
        url: ${TENCENT_VECTORDB_URL}
        username: ${TENCENT_VECTORDB_USERNAME}
        api-key: ${TENCENT_VECTORDB_API_KEY}
        read-consistency: strong
  stores:
    docs:
      connection-ref: tencent
      embedding-model-ref: aiEmbeddingModel
      default-index: docs
      indexes:
        docs:
          name: docs
          dimension: 1024
          metric: cosine
          index-type: hnsw
          additional-fields:
            database-name: spring_ai
            initialize-schema: true
            hybrid-enabled: true
            sparse-vectorizer: bm25SparseVectorizer
            hybrid-candidate-limit: 50
            hybrid-fallback-mode: core-rrf
```

Tencent's SDK-backed schema uses `vector` and `sparse_vector`; field aliases are rejected. `initialize-schema=true` builds the database/collection with the primary key, content/filter fields, dense index, sparse inverted index and filter-all support. Existing dense collections are not migrated in place; create a new hybrid collection and re-ingest data.

## Verification status

Factory tests cover opt-in capability registration and invalid field/encoder rejection. A true E2E is gated by `VECTOR_TENCENT_VECTORDB_IT_RUN=true` plus URL/user/API key. It remains unverified until it proves schema readiness, atomic write, ACL negative case, native path, classified fallback, delete, reconnect and cleanup.
