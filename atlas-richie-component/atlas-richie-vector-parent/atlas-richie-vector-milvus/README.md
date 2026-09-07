# Milvus Provider Design

This module is the Milvus implementation of the provider-neutral vector component. It owns Milvus SDK calls, collection schema and lifecycle, filter compilation, and only capability claims that the request path can prove. It contains no knowledge-base, prompt, MCP, tenant, or consuming-project model.

Chinese version: [README.zh-CN.md](README.zh.md).

## 1. Scope and compatibility

| Area | Status | Contract |
|---|---|---|
| Multiple logical stores per process | Implemented | One connection handle creates independent, index-bound `VectorStoreHandle`s. |
| Basic dense search and lifecycle | Implemented | `VectorService`, lifecycle, and record-read operations retain the legacy path. |
| Provider and ACL filters | Implemented | `MilvusVectorFilterCompiler` produces an expression passed with the Milvus search request. |
| Typed query tuning | Implemented | HNSW accepts `milvus.ef`; IVF accepts `milvus.nprobe`; invalid combinations fail closed. |
| ACL-safe native hybrid | Implemented, opt-in | A new index with `hybrid-enabled: true`; both recall branches receive the same ACL expression. |
| Candidate vectors and client MMR | Implemented | Core owns diversification; Milvus projects stored dense vectors only for an explicit internal request and declares `CANDIDATE_VECTOR`. |
| Server-side MMR | Unsupported | Never declare `SERVER_SIDE_MMR`; Milvus does not provide it. |

The normal path remains the default. A store without advanced options uses the existing dense schema and does not create a V2 client, sparse field, BM25 function, or vector-output payload.

## 2. Runtime architecture

```text
VectorServiceRegistry
  -> MilvusVectorProviderFactory
       -> MilvusConnectionHandle (V1 client; V2 client only for hybrid stores)
       -> VectorStoreHandle (one configured logical store/index)
            -> MilvusVectorServiceImpl        : dense CRUD, schema, filter, typed search
            -> MilvusAdvancedSearchOperations : query defaults and tuning receipt
            -> MilvusAclAwareHybridSearchOperations (hybrid stores only)
                 -> Milvus V2 HybridSearchReq
```

`MilvusVectorProviderFactory` is the capability authority. It reads the default index's `additionalFields["hybrid-enabled"]`; an SDK class alone must not cause a capability claim. The V1 client remains necessary for existing Spring AI/lifecycle code. The V2 client is lazy because native BM25 Function and `HybridSearchReq` use the V2 API. Both close with the connection handle.

## 3. Index schema and lifecycle

### Standard dense index (default)

The legacy-compatible schema is `id`, `vector`, `content`, `metadata`, and configured scalar fields. `MilvusVectorServiceImpl` creates/loads the collection, writes dense embeddings, and uses the configured dense index and metric.

### Native hybrid index (opt-in)

Set the index's `additional-fields` when the collection is first created or rebuilt:

```yaml
additional-fields:
  hybrid-enabled: true
  tenantId: { data_type: VarChar }
  knowledgeBaseId: { data_type: VarChar }
  projectionVersionId: { data_type: VarChar }
```

| Field | Type | Purpose |
|---|---|---|
| `id` | `VarChar`, primary key | Stable vector-record identifier |
| `vector` | `FloatVector` | Dense recall and future MMR diversity calculation |
| `content` | analyzer-enabled `VarChar` | Input for server-side BM25 |
| `sparse_vector` | `SparseFloatVector` | Output of Milvus `BM25(content)` Function |
| `metadata` | `VarChar` JSON | Portable result metadata |
| declared scalar fields | configured types | Filter/ACL fields, such as tenant and version |

The provider creates the configured dense index plus a `SPARSE_INVERTED_INDEX` using `BM25` and `DAAT_MAXSCORE`. During insertion callers provide `id`, `vector`, `content`, `metadata`, and declared scalar fields; they do not calculate or write `sparse_vector`. Milvus calculates it through the Function.

An existing dense collection cannot be converted in place: it lacks the sparse field/function. Create a replacement through `rebuildIndex`, re-ingest/re-embed from the authoritative source, validate, then switch the logical store/alias. Do not set `hybrid-enabled` against an old collection and expect a safe migration.

## 4. ACL-safe hybrid search

Only a hybrid-enabled store declares `ACL_SAFE_HYBRID` and registers `VectorAclAwareHybridSearchOperations`.

```text
structured ACL VectorFilter
  -> MilvusVectorFilterCompiler.compile(filter) = expression
  -> dense AnnSearchReq(vector, expression)
  -> sparse AnnSearchReq(sparse_vector + EmbeddedText, same expression)
  -> one HybridSearchReq + WeightedRanker
  -> fused, provider-filtered results
```

The filter is not applied after fusion. The operation rejects a missing ACL filter, conflicting `SearchOptions.filter`, invalid/unnormalised weights, empty query, and invalid limit before the SDK call. The overload without an explicit ACL filter is deliberately rejected. This prevents broad recall plus JVM filtering.

The result score is Milvus's weighted fused score and is only meaningful within that request; it is not a globally comparable dense similarity score.

## 5. Client-side MMR and candidate-vector support

> **Implementation status:** Core owns the bounded, deterministic MMR algorithm. Milvus now returns stored dense candidate vectors only when `SearchOptions.includeCandidateVectors=true`; basic retrieval remains vector-free. The Provider declares `CANDIDATE_VECTOR`, not `SERVER_SIDE_MMR`.

### 5.1 What MMR does

Top-K vector search is intentionally relevance-first. When a document is chunked into similar passages, ordinary search can return five near-identical chunks and omit a second relevant source. Maximum Marginal Relevance (MMR) reorders a **provider-filtered candidate set** to balance relevance with diversity. It does not generate vectors, improve an incorrect embedding model, or bypass Milvus ranking.

At each selection step, MMR chooses the candidate `d` with the largest value:

```text
MMR(d) = λ × relevance(d, query)
         − (1 − λ) × max similarity(d, alreadySelected)
```

For this component:

- `relevance(d, query)` is the final score returned by the provider: dense score for ordinary search, or Milvus weighted fused score for hybrid search.
- `similarity(d, alreadySelected)` is cosine similarity between the stored dense embeddings.
- `λ` is in `[0, 1]`: `1.0` is ordinary relevance ranking; lower values penalise duplicate passages more strongly.
- The first result has no selected neighbour, therefore its redundancy penalty is zero.

The algorithm is greedy and deterministic: ties use the normal result-score and record-id ordering. The planned implementation caches a candidate's largest similarity to any selected result; this preserves the formula while avoiding repeated full scans.

### 5.2 Safe execution sequence

```mermaid
sequenceDiagram
    participant C as Consumer / Knowledge service
    participant P as Milvus Provider
    participant M as Milvus
    participant D as VectorResultDiversifier

    C->>P: search(query, candidateK, ACL filter, includeCandidateVectors=true)
    P->>M: ANN or HybridSearch; ACL/filter at recall stage
    M-->>P: authorised candidates + score + dense vector
    P-->>D: provider-filtered candidate set only
    D->>D: validate count, dimension and byte limits
    loop until topK selected
        D->>D: λ×relevance − (1−λ)×max cosine(selected)
    end
    D->>D: remove vectors unless explicitly requested
    D-->>C: diversified topK results / citations
```

The ordering is security-critical: ACL and all structured filters run in Milvus **before** MMR. MMR never receives an unauthorised candidate and is never used as a substitute for filter pushdown.

### 5.3 When to use MMR

MMR is recommended when all of the following are true:

| Situation | Why MMR helps |
|---|---|
| RAG question answering over chunked documents | Prevents adjacent chunks of one document from consuming every evidence slot. |
| A question should be answered from several documents, policies, or source types | Increases evidence coverage and source variety. |
| Candidate set is meaningfully wider than final output | MMR needs alternatives; start with `candidateK = 3–5 × topK`. |
| Some relevance trade-off is acceptable | Diversity can deliberately select a slightly lower-ranked but non-duplicate chunk. |

Suggested starting values are `topK=5`, `candidateK=20`, and `lambda=0.6`. Increase `lambda` toward `0.7–0.8` when answer precision is more important than coverage; decrease it toward `0.4–0.5` when duplicate chunks dominate. `lambda=1.0` is equivalent to relevance-only selection and normally means MMR should simply be disabled.

Conceptual knowledge-search use after this design is implemented:

```java
new KnowledgeSearchRequest(
        /* query */ "What changed in the retention policy?",
        /* candidateK */ 20,
        /* topK */ 5,
        /* mmr */ true,
        /* mmrLambda */ 0.6D,
        ...);
```

For the advanced Store-bound API, enable `VectorDiversificationOptions(false, true, 0.6D)`. `false` means vectors are available only inside the component for MMR; an advanced caller must explicitly set `includeVectors=true` if it has a legitimate reason to receive raw vectors.

### 5.4 When not to use MMR

Do not enable it by default for every query. Keep it disabled for:

| Situation | Reason |
|---|---|
| Exact ID, keyword, compliance, or known-document lookup | Relevance/order is the requirement; diversity can replace the best exact evidence. |
| `candidateK` is equal or close to `topK` | There are no meaningful alternatives to diversify. |
| One contiguous document passage is required, such as reconstruction or legal quotation | Penalising neighbouring chunks can harm completeness and continuity. |
| Strict low-latency or high-throughput retrieval where the extra vector payload is unacceptable | Candidate vectors add network, memory, and CPU cost. |
| Provider/store cannot safely return candidate vectors or resource limits are exceeded | The component must reject the request, not pretend MMR ran. |
| Cross-store score fusion without an explicit comparable-score contract | MMR relevance input would be semantically ambiguous. |

MMR is also not a remedy for poor chunking, missing ACL predicates, irrelevant embeddings, or an overly small candidate pool. Fix those causes first.

### 5.5 Expected effect and trade-offs

With a sufficiently broad authorised candidate pool, MMR should reduce near-duplicate results, improve document/source coverage, and give an LLM more complementary evidence. It may reduce the average raw relevance score and can select a lower-scoring chunk intentionally. It adds vector projection and greedy comparison work, so its benefit should be verified with representative queries, answer quality, latency, and citation/source diversity rather than only an offline similarity score.

### 5.6 Required implementation

1. **Core contract first.** Add `SearchOptions.includeCandidateVectors` with a default of `false`. It is an internal data-plane request, not an instruction to expose a vector to a caller. `DefaultKnowledgeBaseVectorService` sets it only when `KnowledgeSearchRequest.mmr=true`; `HybridSearchOptions` already carries the same `SearchOptions`, so dense and hybrid retrieval share one propagation path. This is necessary: implementing only `VectorAdvancedSearchOperations` would leave normal and hybrid knowledge retrieval unable to perform MMR.
2. Make `MilvusVectorServiceImpl` add `vector` to Milvus `outFields` only when `SearchOptions.includeCandidateVectors=true`, parse returned floats into `VectorSearchResult.vector`, and preserve current default output otherwise. The ACL-safe hybrid implementation reads the same option and projects `vector` only from the dense branch's result entity.
3. Replace the duplicate knowledge-layer MMR loop with `VectorResultDiversifier` (or make it call that single implementation), so all paths get the same candidate/dimension/byte/concurrency protections and remove internal vectors before producing citations.
4. Change `MilvusAdvancedSearchOperations.search` to set the same option when `query.diversification().requiresCandidateVectors()` and apply `VectorResultDiversifier.apply(candidates, topK, diversification)` rather than taking the first `topK`.
5. Add `CANDIDATE_VECTOR` with `default=disabled`, `client-mmr=true`, `server-mmr=false`. Never add `SERVER_SIDE_MMR`. A provider asked for candidate vectors but unable to return them must reject the request rather than silently run non-MMR retrieval.
6. Preserve privacy: MMR receives vectors internally, while the diversifier removes them unless the advanced caller explicitly requested `includeVectors=true`.

MMR relevance uses the provider final dense or fused score; redundancy uses cosine similarity between dense candidate embeddings. Core limits candidates, dimensions, candidate-vector bytes, response bytes, timeout, and concurrency. The current deterministic diversifier may cache each candidate's maximum selected similarity to reduce repeated comparisons without changing output semantics.

## 6. Test and acceptance plan

| Layer | Required proof |
|---|---|
| Unit | `outFields` excludes `vector` by default and includes it only for candidate mode; parser handles valid/missing/malformed vectors. |
| Unit | Capability: `CANDIDATE_VECTOR` yes, `SERVER_SIDE_MMR` no; default behavior remains unchanged. |
| Contract | Candidate dimensions are checked; MMR is deterministic, hides vectors by default, and enforces Core limits. |
| Local Milvus E2E | Insert near-duplicates plus a relevant diverse vector; assert MMR selects diversity, ACL excludes denied tenant before MMR, and default output exposes no vector. |
| Hybrid E2E | With the shared `SearchOptions` propagation, prove fused hybrid MMR has no unauthorised candidate and cleans up its unique collection. |

Run one provider at a time on constrained machines. Every E2E uses a unique collection, deletes it in `finally`, and stops the Milvus compose stack afterwards.

## 7. Current validation

ACL-safe hybrid and candidate-vector projection are covered by factory/request-contract tests and opt-in `MilvusAclSafeHybridLiveIT` (`VECTOR_MILVUS_IT_RUN=true`). It creates an isolated hybrid collection, writes allowed and denied records with identical content/vector, searches with the allowed ACL while requesting candidate vectors, verifies the denied record is absent and the allowed dense vector is returned, and deletes the collection.

See also [cross-provider capability policy](../docs/advanced-query-capabilities.md).
