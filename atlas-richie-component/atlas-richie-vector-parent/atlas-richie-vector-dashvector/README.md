# DashVector Provider

`atlas-richie-vector-dashvector` is an optional named-Store provider. One platform Store maps to one DashVector collection; one connection may serve many independently named Stores. A project that does not configure DashVector does not create its client or require a sparse encoder.

## Capabilities and safety boundary

| Capability | Dense Store (default) | `hybrid-enabled: true` |
|---|---:|---:|
| Dense write/search | yes | yes |
| Typed provider filter / ACL filter | yes | yes |
| `ACL_SAFE_HYBRID` | no | yes, after configuration validation |
| Execution | n/a | native dense+sparse request |

The hybrid writer embeds text once, encodes the same text with the configured `SparseVectorizer`, and sends both vectors in **one DashVector upsert**. Delete remains an ID deletion of that same document. A Store is not allowed to claim hybrid support when it could write only dense vectors.

Each hybrid request requires an explicit structured `VectorFilter`. The filter is compiled once and attached to the DashVector query before candidate selection. The overload without an ACL filter is rejected; JVM-side post-filtering is never used as an authorization substitute.

## Configuration

```yaml
platform:
  component:
    vector:
      connections:
        dash-prod:
          provider: dashvector
          settings:
            endpoint: ${DASHVECTOR_ENDPOINT}
            api-key: ${DASHVECTOR_API_KEY}
            timeout-seconds: 30
      stores:
        docs:
          connection-ref: dash-prod
          embedding-model-ref: aiEmbeddingModel
          default-index: docs
          indexes:
            docs:
              name: docs
              dimension: 1024
              metric: ip
              index-type: hnsw
              additional-fields:
                initialize-schema: true
                partition: default
                metadata-fields: tenantId:STRING;principalId:STRING
                hybrid-enabled: true
                sparse-vectorizer: bm25SparseVectorizer
                hybrid-candidate-limit: 50
                hybrid-fallback-mode: reject
```

`hybrid-enabled` defaults to `false`; all existing dense-only configurations retain their previous write, search, schema and startup behavior. DashVector accepts `sparse_vector` only when the collection metric is `dotproduct`; configure `metric: ip` (or `dot`) for a hybrid Store. The current SDK binding uses DashVector's root `vector` and `sparse_vector` representation, so aliases for `dense-field` and `sparse-field` are intentionally rejected rather than silently ignored. ACL metadata fields must be declared in `metadata-fields`. Keep credentials in a secret-backed environment, never in YAML committed to source control.

## Request flow

```text
VectorRecord -> embedding + SparseVectorizer -> one DashVector upsert(dense,sparse,metadata)
query + mandatory VectorFilter -> DashVector native dense+sparse query(filter before recall)
                              -> only authorized results returned
```

DashVector's provider score is returned as supplied by the SDK. Cross-provider scores must not be fused unless an application defines a comparable-score contract.

## Verification status

Factory validation, reserved-field write mapping and hybrid capability-registration tests pass. Cloud E2E is environment-gated by `VECTOR_DASHVECTOR_IT_RUN=true`, `DASHVECTOR_ENDPOINT`, and `DASHVECTOR_API_KEY`. On 2026-09-10 the service version `2.4.12.3-20-gb6fd1a8f` was used to verify two collections on one connection: schema creation, dense+sparse writes, native ACL-filtered exclusion, cross-Store isolation, deletion and collection cleanup all passed. The DashVector path is native-only; unsupported native hybrid is reported rather than being silently replaced with an unsafe fallback.
