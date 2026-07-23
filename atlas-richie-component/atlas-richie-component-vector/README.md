# Atlas Richie Vector Component (`atlas-richie-component-vector`)

> Multi-modal vector search, batch pipeline, and operations API for seven vector database providers.

`atlas-richie-component-vector` exposes one application-facing vector API over seven provider modules. The core module owns the stable contract, the multi-modal content model, the reactive batch pipeline, the operations API, and the multi-provider dispatcher. Each provider module owns its SDK calls and connection settings, and contributes one `VectorService` implementation to the Spring container.

## Contents

- [Overview](#overview)
  - [What this component is](#what-this-component-is)
  - [What this component is not](#what-this-component-is-not)
- [Features](#features)
- [Architecture](#architecture)
  - [Module layout](#module-layout)
  - [Core package layout](#core-package-layout)
- [API Reference](#api-reference)
  - [`VectorService` interface](#vectorservice-interface)
  - [Single-record operations](#single-record-operations)
  - [Search and index operations](#search-and-index-operations)
  - [Batch operations](#batch-operations)
  - [Core model types](#core-model-types)
- [Batch Async Pipeline](#batch-async-pipeline)
  - [Three-stage data path](#three-stage-data-path)
  - [Stage A — embed producer](#stage-a--embed-producer)
  - [Stage B — write consumer](#stage-b--write-consumer)
  - [Stage C — terminal summary](#stage-c--terminal-summary)
  - [Sinks backpressure](#sinks-backpressure)
  - [`bufferTimeout` chunking](#buffertimeout-chunking)
  - [Batch configuration](#batch-configuration)
  - [Failure semantics](#failure-semantics)
  - [Deduplication and counters](#deduplication-and-counters)
- [Operations API](#operations-api)
  - [Five operational methods](#five-operational-methods)
  - [Per-provider support matrix](#per-provider-support-matrix)
  - [`throwUnsupportedOps` helper](#throwunsupportedops-helper)
- [Multi-Modal Embedding](#multi-modal-embedding)
- [Multi-Provider Operations](#multi-provider-operations)
  - [`VectorOperationsFacade`](#vectoroperationsfacade)
  - [Access and execution methods](#access-and-execution-methods)
  - [Failure aggregation and observability](#failure-aggregation-and-observability)
- [Configuration Reference](#configuration-reference)
  - [Common properties](#common-properties)
  - [Batch properties](#batch-properties)
  - [Facade properties](#facade-properties)
  - [Provider properties](#provider-properties)
- [Provider Comparison](#provider-comparison)
- [Implementation Details](#implementation-details)
  - [`AbstractVectorService` base class](#abstractvectorservice-base-class)
  - [`BatchPipelineCoordinator` extraction](#batchpipelinecoordinator-extraction)
  - [Provider implementation pattern](#provider-implementation-pattern)
  - [Why sealed types](#why-sealed-types)
  - [Why the facade is a concrete class](#why-the-facade-is-a-concrete-class)
- [Known Limitations](#known-limitations)
- [FAQ](#faq)
- [Further Reading](#further-reading)

---

## Overview

The component presents a unified `VectorService` contract over seven provider modules:

- Milvus
- Qdrant
- Redis
- PostgreSQL with `pgvector`
- MongoDB Atlas
- Neo4j
- Weaviate

The split is deliberate. The core owns everything that must look the same to callers — the content model, the modality router, the reactive batch event protocol, the operations API, and the multi-provider dispatcher. Provider modules own everything that is native to a single backend — the SDK call, the connection factory, and the lifecycle hooks for index, alias, snapshot, and restore operations that the backend supports.

### What this component is

| Capability | Behavior |
|---|---|
| Storage | Stores text and image records through a unified `VectorRecord` model with `VectorContent` payload. |
| Retrieval | Text search, image search, vector search, hybrid search, and multi-vector search contracts. |
| Embedding | Routes `TEXT` and `IMAGE` content to the appropriate `EmbeddingModel` through `ModalityAwareEmbeddingService`. |
| Index lifecycle | Create, delete, inspect, list, truncate, clone, await readiness, describe, statistics, and health operations. |
| Operations | Exposes `optimize`, `createAlias`, `switchAlias`, `backup`, and `restore`. |
| Batch ingestion | Emits a `Flux<BatchEvent>` with embedding and persistence progress across all in-flight items. |
| Provider choice | Selects the active provider through `platform.component.vector.provider`. |
| Provider failover | Optional `VectorOperationsFacade` adds retry, fallback chain, and metrics. |

### What this component is not

The component does not create an embedding model for you, normalize vectors for every provider, or make unsupported provider features appear portable. The operations API is deliberately honest: a method can be part of the common contract while a particular provider reports `UnsupportedOperationException`. The pipeline buffer is an in-memory safety bound, not a durable queue. The facade is not a multi-store merger and does not make writes atomic across providers.

---

## Features

- **Multi-modal routing** for text and image content, backed by a `ModalityAwareEmbeddingService` with text model as a required baseline and image model as an optional extension.
- **Sealed content types** that make the supported content domain visible to the compiler.
- **Unified `VectorRecord`** for single-record writes, search-result carries, and batch ingestion inputs.
- **42 operational methods in the `VectorService` contract** grouped by capability, plus two convenience overloads for `addImage(Path)` and `addBatch(List)`.
- **Reactive `BatchEvent` protocol** with six event types and an eight-value `Stage` enum, suitable for UI progress and operational dashboards.
- **Three-stage batch pipeline** with Sinks backpressure between Stage A (embed) and Stage B (write), and `bufferTimeout` chunking on Stage B.
- **Configurable concurrency** for embedding producers and write consumers, set independently.
- **Configurable failure semantics** through `failFast`, `dedupCacheSize`, and `itemIdSource`.
- **Five operations APIs** — `optimize`, `createAlias`, `switchAlias`, `backup`, `restore` — backed by a per-provider support matrix.
- **`throwUnsupportedOps` helper** that produces a uniform, trace-friendly message for unsupported operation paths.
- **Seven provider implementations** that share one abstract base class and one pipeline coordinator.
- **Multi-provider facade** with primary selection, retry, ordered fallback, failure aggregation, and Micrometer metrics.
- **Reranking for text search** when a `RerankService` is available.
- **Provider-aware index metadata** through `IndexInfo` and `IndexStatus`.

The reason for exposing these concerns from one core is composability. Applications can change a provider, a model, a batch profile, or an operational policy without rewriting business flows.

---

## Architecture

### Module layout

The parent contains nine modules: one parent POM, one core module, and seven provider modules.

```text
atlas-richie-component-vector
├── atlas-richie-component-vector-core
├── atlas-richie-component-vector-milvus
├── atlas-richie-component-vector-qdrant
├── atlas-richie-component-vector-redis
├── atlas-richie-component-vector-postgresql
├── atlas-richie-component-vector-mongodb-atlas
├── atlas-richie-component-vector-neo4j
└── atlas-richie-component-vector-weaviate
```

The parent does not select a database by classpath alone. A provider module supplies its client, its connection factory, and one `AbstractVectorService` subclass; `platform.component.vector.provider` controls conditional activation. `VectorMultiProviderGuard` refuses to start when more than one `VectorService` bean is detected, making the single-provider constraint explicit at startup.

### Core package layout

The core module is split into nine packages. The split keeps stable contracts separate from orchestration, and provider-independent mechanics separate from the public surface.

| Package | Responsibility |
|---|---|
| `config` | `VectorProperties`, `VectorFacadeProperties`, `VectorAutoConfiguration`, `VectorMultiProviderGuard`. Binds to `platform.component.vector.*`. |
| `embeddings` | `ModalityAwareEmbeddingService`. Routes `TEXT` / `IMAGE` content to the right `EmbeddingModel`; reports `supportsModality` and `dimensionFor`. |
| `enums` | `VectorProvider` and `EmbeddingProvider` selections shared by configuration. |
| `exceptions` | `UnsupportedModalityException` and `VectorStoreNotExistException`. |
| `model` | `VectorRecord`, `VectorContent`, `BatchEvent`, `BatchStats`, `Stage`, `IndexInfo`, `IndexStatus`, `Modality`, `SearchOptions`, `HybridSearchOptions`, `VectorSearchResult`. |
| `operations` | `VectorOperationsFacade`. Cross-provider dispatcher with primary selection, retry, fallback, and metrics. |
| `pipeline` | `BatchPipelineCoordinator`. Owns the reactive Stage A / Stage B / Stage C ingestion pipeline. |
| `service` | The public `VectorService` contract used by applications and implemented by every provider. |
| `service.impl` | `AbstractVectorService`. Shared single-record behavior, provider hooks, and operation delegation. |

A provider module only adds its connection/configuration classes and its `service.impl` implementation. It does not copy the batch pipeline or reimplement modality behavior.

---

## API Reference

### `VectorService` interface

The public contract is grouped by capability rather than provider. The interface exposes 42 operational methods and two convenience overloads (`addImage(Path)` and `addBatch(List)`).

| Group | Operational methods | Convenience overloads | Purpose |
|---|---:|---:|---|
| Add | 4 | 1 | Add text, a complete record, image bytes, or an image URL. |
| Update | 4 | 0 | Update text, a complete record, or image content from bytes/path. |
| Delete | 2 | 0 | Delete by ID or by a record predicate. |
| Get | 2 | 0 | Read one record or an ordered collection of IDs. |
| Text search | 3 | 0 | Search by text with default, threshold, or full options. |
| Image search | 3 | 0 | Search by image bytes with default, threshold, or path input. |
| Index base | 6 | 0 | Create, delete, inspect, count, and page through an index. |
| Index extension | 6 | 0 | List, truncate, update, clone, wait, and describe indexes. |
| Stats and health | 2 | 0 | Read index statistics and run a health check. |
| Advanced search | 2 | 0 | Hybrid search and multi-vector search. |
| Operations | 5 | 0 | `optimize`, `createAlias`, `switchAlias`, `backup`, `restore`. |
| Batch | 3 | 1 | Add from `Flux`, update from `Flux`, delete from `Flux`. |
| **Total** | **42** | **2** | The two overloads let callers avoid manual byte loading or `Flux` construction. |

### Single-record operations

The add family is modality-aware:

- `addText` constructs a `TextContent` record and routes through the text model.
- `add` accepts a `VectorRecord` and routes from `record.getContent().modality()`.
- `addImage` accepts raw bytes or a `Path` and uses `ImageContent`.
- `addImageUrl` downloads the remote image before entering the image path.

The update family uses delete-plus-insert semantics in `AbstractVectorService`. This is intentionally explicit because provider update semantics differ. `update` requires an ID; add operations generate a UUID when the ID is absent.

The delete and get families keep provider-specific retrieval behind `deleteByIds` and `getByIds`. `deleteIf` is a management-oriented predicate path that may enumerate records and should not be treated as a high-throughput delete API.

### Search and index operations

Text search supports a simple limit, a minimum score, or a complete `SearchOptions` object. When a `RerankService` exists and reranking is enabled, text results are reranked after vector recall. Image search does not rerank by default because a dual-encoder image model already expresses similarity in its aligned embedding space.

`SearchOptions` carries:

- `rerank` (Boolean, default `true`)
- `minScore` (Double)
- `filterExpression` (String, provider-specific DSL)
- `namespace` (String)
- `limit` (Integer)
- `type` (String, document type filter)

`HybridSearchOptions` carries vector and keyword weights, a keyword query, and nested `SearchOptions`. Providers that support full BM25-plus-vector semantics execute the hybrid path; other providers fall back to vector search and log the degradation.

Index lifecycle methods cover schema creation, deletion, existence, configuration, counts, pagination, listing, truncation, updates, cloning, readiness, description, statistics, and health. `IndexInfo` reports name, modality, dimension, metric, index type, status, document count, timestamps, and provider metadata. `IndexStatus` exposes `CREATING`, `READY`, `UPDATING`, `DELETING`, `FAILED`, and `UNKNOWN`. A provider maps its native lifecycle to the closest state.

### Batch operations

The batch contract returns `Flux<BatchEvent>`:

- `addBatch(String, Flux<VectorRecord>)`
- `addBatch(String, List<VectorRecord>)` convenience overload
- `updateBatch(String, Flux<VectorRecord>)`
- `deleteBatch(String, Flux<String>)`

The add and update paths share `BatchPipelineCoordinator`. The coordinator is optimized for add semantics; update reuses the same embedded write path and relies on the single-record update contract for delete-plus-insert behavior where needed. Delete emits per-ID progress through its own provider delete path and does not flow through the Sinks-backed pipeline.

### Core model types

#### `Modality`

`Modality` is an enum with two values today: `TEXT` and `IMAGE`. The enum is the right shape because the routing decision is finite, exhaustive, and used in Java switch expressions. It is not a substitute for content data — the content payload is carried by `VectorContent`.

#### `VectorContent`

`VectorContent` is a sealed interface permitting `TextContent` and `ImageContent`.

- `TextContent(text, mimeType)` validates non-blank text and defaults MIME type to `text/plain`.
- `ImageContent(data, mimeType)` validates non-empty bytes and an `image/*` MIME type.
- `ImageContent.ofPath` reads a `Path` and preserves the same validation rules.

A sealed interface is preferable to a broad enum-plus-object map because the content payload has different invariants. Pattern matching can enforce that `TEXT` receives `TextContent` and `IMAGE` receives `ImageContent`, while future content kinds can be added deliberately through the permits list.

#### `VectorRecord`

`VectorRecord` is the unified mutable Java bean used at service boundaries. It carries:

- `id` and `indexName`
- `content`
- `metadata`
- `tags`, `source`, `namespace`, `status`
- `score`, `createdAt`, `updatedAt`

The factories `text`, `image`, and `imageUrl` make common paths readable. `imageUrl` stores the URL in reserved metadata while the pipeline or caller resolves the actual bytes. `metadata.__itemId` is reserved for batch item tracking; `itemId()` falls back to the record ID.

The record is intentionally separate from Spring AI `Document`. The core converts at the provider boundary, so application code does not depend on Spring AI document metadata conventions.

#### `SearchOptions` and `HybridSearchOptions`

These option objects prevent overload growth and let a provider receive common filtering intent without changing the service signature. Provider-specific filter expressions remain strings because their DSLs are not interchangeable.

#### `IndexInfo` and `IndexStatus`

`IndexInfo` is a normalized observation, not a promise that every provider exposes identical metadata. `IndexStatus` is the lifecycle enum; a provider maps its native lifecycle to the closest state.

#### `BatchStats`

`BatchStats` includes `total`, `succeeded`, `failed`, elapsed duration, `embeddingApiCalls`, and `writeApiCalls`. The call counters measure provider/API invocations, not records. One write of a 100-record chunk is one write API call.

---

## Batch Async Pipeline

### Three-stage data path

The batch path is coordinated by `BatchPipelineCoordinator` and has two concurrent data stages plus a terminal summary.

```text
Flux<VectorRecord>
      │
      ▼
Stage A: embed producer
  flatMap(embeddingConcurrency)
  ├─ resolve itemId
  ├─ optional text deduplication
  ├─ route to text/image model
  ├─ emit ItemStarted(LOADED)
  ├─ emit StageChanged(LOADED → EMBEDDED)
  └─ push EmbeddedItem to Sinks.Many
      │
      ▼
Sinks.Many<EmbeddedItem>
      │
      ▼
Stage B: write consumer
  bufferTimeout(writeBatchSize, 50 ms)
  flatMap(writeConcurrency)
  ├─ emit ItemStarted(PERSISTING)
  ├─ one writer.write(index, docs) per chunk
  ├─ count one writeApiCalls per successful chunk
  ├─ emit StageChanged(PERSISTING → PERSISTED)
  └─ emit ItemCompleted per item
      │
      ▼
Stage C: BatchCompleted(BatchStats)
```

The separation is deliberate. Embedding calls are often remote and model-limited, while writes are limited by provider throughput and payload size. Independent concurrency controls prevent one bottleneck from dictating the other.

### Stage A — embed producer

Stage A subscribes to the input `Flux<VectorRecord>` and processes each record through `flatMap(..., embeddingConcurrency)`:

1. Resolve the per-batch item id through `resolveItemId(record, contentHash, cfg)` based on the configured `itemIdSource`.
2. Optionally check the dedup cache; a duplicate short-circuits with `ItemCompleted` and skips both embedding and writing.
3. Route the record to the text or image model through `ModalityAwareEmbeddingService` (delegated by `AbstractVectorService.embedForBatch`).
4. Emit `ItemStarted(LOADED)` and `StageChanged(LOADED → EMBEDDED)` for the record.
5. Push an `EmbeddedItem` (record + embedding + itemId + modality) into the Sinks-backed handoff.

### Stage B — write consumer

Stage B consumes the Sinks-backed embedded stream and groups items into provider-sized chunks:

1. `bufferTimeout(writeBatchSize, 50 ms)` flushes a chunk when either `writeBatchSize` items have accumulated or the 50 ms timeout expires.
2. `flatMap(..., writeConcurrency)` lets up to `writeConcurrency` chunks call `writer.write(index, docs)` concurrently.
3. Per chunk: emit `ItemStarted(PERSISTING)` for each item, invoke the writer once, count `writeApiCalls += 1` on success, then emit `StageChanged(PERSISTING → PERSISTED)` and `ItemCompleted` for each item.
4. On writer exception with `failFast=false`, emit one `ItemFailed(PERSISTING, error)` for each item in the chunk and continue with the next chunk.

The producer and consumer run on `Schedulers.boundedElastic()`. Provider writes are blocking calls; the elastic scheduler keeps them off the parallel scheduler pool.

### Stage C — terminal summary

Stage C emits exactly one `BatchCompleted(BatchStats)` after the merged Stage A and Stage B streams complete. The summary is emitted even after non-`failFast` failures and after pipeline-level errors, so consumers can always rely on the terminal event for batch-level cleanup.

### Sinks backpressure

Stage A does not write directly into Stage B. It pushes embedded items into a unicast `Sinks.Many<EmbeddedItem>` backed by an `ArrayBlockingQueue` configured with `onBackpressureBuffer(backpressureBuffer)`.

When the queue is full, the embedded-item push uses `Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(1))` — a short busy-loop retry. This applies backpressure at the handoff rather than allowing unbounded memory growth. The result is a bounded in-flight embedded-item set:

- The embedding producer can continue while the buffer has capacity.
- A slow provider writer eventually makes the producer wait.
- The input `Flux` is not silently dropped because the queue is full.
- Terminal and cancelled sinks are treated as terminal conditions rather than retried forever.

The queue is not a durability mechanism. If the process terminates, the in-memory batch is lost; durable retry belongs to the application or ingestion infrastructure.

### `bufferTimeout` chunking

Stage B applies `bufferTimeout(writeBatchSize, Duration.ofMillis(50))`. A chunk is flushed as soon as either condition is met:

1. `writeBatchSize` embedded items have accumulated.
2. The 50 ms timeout expires after items enter the buffer.

This gives large streams efficient provider calls and gives small streams bounded latency. `writeConcurrency` controls how many chunks are sent to the writer simultaneously. `writeBatchSize` is both the buffer upper bound and the document count passed to one `writer.write` call.

`writeApiCalls` therefore means successful chunk-level writer calls. It is not the number of items, embeddings, or emitted events. This definition makes the metric useful for provider cost and throughput analysis.

### Batch configuration

`VectorProperties.Batch` is bound below `platform.component.vector.batch`. The defaults match `BatchPipelineCoordinator.DEFAULT_BATCH`, so a missing YAML block leaves behavior unchanged.

| Property | Default | Meaning |
|---|---:|---|
| `embeddingBatchSize` | `32` | Reserved model batch size for embedding adapters that support list input. |
| `embeddingConcurrency` | `8` | Maximum concurrent Stage A embedding tasks. |
| `writeBatchSize` | `100` | Maximum records per provider write chunk. |
| `writeBatchSize` also bounds the buffer upper bound for `bufferTimeout`. | | |
| `writeConcurrency` | `4` | Maximum concurrent Stage B chunks. |
| `backpressureBuffer` | `1024` | Maximum embedded items waiting in the Sinks handoff. |
| `failFast` | `false` | Whether the first failure terminates the batch. |
| `dedupCacheSize` | `10000` | Enables content deduplication when greater than zero. |
| `itemIdSource` | `METADATA` | Selects `METADATA`, `ID`, or `HASH` item IDs. |

Values are normalized to safe minimums by the coordinator (`Math.max(1, ...)`) so a misconfigured zero or negative does not deadlock the pipeline.

`ConfigProvider` is a callback rather than a copied field. Construction can happen before optional Spring setter injection completes, and a callback can observe the latest `VectorProperties.Batch` when a batch starts. Setter-injected configuration is therefore visible on the next batch run without reconstructing the service.

### Failure semantics

With `failFast=false`:

- An embedding exception creates `ItemFailed(EMBEDDING, error)` for that record. Other records continue.
- A chunk write exception creates one `ItemFailed(PERSISTING, error)` for each item in that chunk. Other chunks continue.
- The pipeline still emits a terminal `BatchCompleted`.

With `failFast=true`:

- An embedding or writer exception terminates the active pipeline. The terminal handling still attempts to emit a final `BatchCompleted` summary.
- Already-running provider calls follow that provider's cancellation behavior; use an external idempotency key for safe retries.

Consumers should use `ItemFailed.failedStage()` for per-item recovery and the terminal `BatchCompleted.stats()` for batch-level cleanup.

### Deduplication and counters

Text content can be hashed with SHA-256 for the optional dedup cache. A duplicate is short-circuited and emits `ItemCompleted` without another embedding or write. The image path does not compute a content hash; image deduplication should be handled by a stable application item ID or an external content-addressed store.

`itemIdSource` selects:

- `METADATA`: `metadata.__itemId`, then the record ID.
- `ID`: the record ID, then the record item ID fallback.
- `HASH`: the content SHA-256, then the record ID fallback.

A batch item ID is an event correlation key. It is not necessarily the provider record ID.

`embeddingApiCalls` and `writeApiCalls` are atomic counters incremented on each successful embed or chunk write. They are surfaced through `BatchStats` for monitoring and capacity planning.

---

## Operations API

### Five operational methods

The five common operational methods are part of the contract. Their per-provider support varies; the support matrix describes what is implemented today, not what the contract pretends is portable.

| Method | Meaning | Typical use |
|---|---|---|
| `optimize(indexName)` | Ask the provider to compact, merge, or rebuild internal structures. | Reclaim space or improve query performance. |
| `createAlias(indexName, alias)` | Create an alias that resolves to an index. | Stable application names and staged releases. |
| `switchAlias(oldIndexName, newIndexName, alias)` | Atomically move an alias to a new index when supported. | Blue-green index swap with zero downtime. |
| `backup(indexName, targetPath)` | Export or snapshot an index to a target path. | Disaster recovery and offline retention. |
| `restore(sourcePath, indexName)` | Restore an index from a provider-compatible backup. | Recovery or environment bootstrap. |

These methods return `boolean` because successful provider implementations report a success/failure result. Unsupported implementations do not return `false`; they throw `UnsupportedOperationException` through `throwUnsupportedOps`.

### Per-provider support matrix

The matrix describes what each provider implementation actually does today.

| Provider | `optimize` | `createAlias` | `switchAlias` | `backup` | `restore` |
|---|:---:|:---:|:---:|:---:|:---:|
| Milvus | Real | Real | Real | UOE | UOE |
| Qdrant | Real | UOE | UOE | UOE | UOE |
| PostgreSQL | Real | UOE | UOE | UOE | UOE |
| Redis | UOE | UOE | UOE | UOE | UOE |
| MongoDB Atlas | UOE | UOE | UOE | UOE | UOE |
| Neo4j | UOE | UOE | UOE | UOE | UOE |
| Weaviate | UOE | UOE | UOE | UOE | UOE |

- **Milvus** calls `manualCompact` for `optimize`, `createAlias` for `createAlias`, and `alterAlias` for `switchAlias`. Backup and restore are delegated to the `milvus-backup` out-of-process tool; the provider implementation throws UOE so callers know to use the external tool.
- **Qdrant** implements `optimize` as a snapshot trigger (`createSnapshotAsync`) that causes the segments optimizer and index merger to run. Backup and restore go through `qdrant-backup`. Aliases are not supported.
- **PostgreSQL** implements `optimize` as `VACUUM ANALYZE` on the underlying table. Aliases, backup, and restore rely on `CREATE VIEW`, `pg_dump`, and `pg_restore` respectively; the provider throws UOE.
- **Redis, MongoDB Atlas, Neo4j, Weaviate** report UOE for all five operations; their primary capabilities are search and persistence, not native alias/snapshot primitives.

There are 35 provider-operation slots: five real implementations and 30 `UnsupportedOperationException` paths. The matrix is the contract for what is portable.

### `throwUnsupportedOps` helper

Provider implementations call the shared helper for unsupported boolean operations:

```java
@Override
protected boolean backupImpl(String indexName, String targetPath) {
    return throwUnsupportedOps("backup", indexName, "qdrant");
}
```

The helper always throws a message containing the operation, provider, and index:

```text
backup 未实现: provider=qdrant, index=demo
```

Its return type is `boolean` for Java's unconditional-throw pattern: callers can write `return throwUnsupportedOps(...)` in a method whose declared result is boolean without adding an unreachable return statement.

The helper centralizes what would otherwise be 30+ scattered `throw new UnsupportedOperationException(...)` sites across the seven provider implementations. The message is structured for log clustering and stack-trace correlation, with both the operation name and the provider identifier.

The helper is not a capability probe and never returns `false`. Applications that need capability discovery should maintain provider policy from the matrix or catch `UnsupportedOperationException` at the operational boundary.

---

## Multi-Modal Embedding

`ModalityAwareEmbeddingService` is the central router. Its `embed(modality, content)` method performs an exhaustive switch:

```text
TEXT  + TextContent  → textModel.embed(text)
IMAGE + ImageContent → imageModel.embed(data URL)
```

`textModel` is mandatory because text is the baseline capability. `imageModel` is optional. `supportsModality(TEXT)` is always true after construction, while `supportsModality(IMAGE)` is true only when the image model exists. `dimensionFor` exposes the selected model dimension and returns zero for an unavailable image model.

The service validates modality/content alignment. A `TEXT` modality with `ImageContent`, or an `IMAGE` modality with `TextContent`, is rejected rather than coerced. This keeps model input errors close to the caller.

The same vector-space constraint applies to cross-modal retrieval. Text-to-image and image-to-text search are meaningful only when both model outputs are comparable, for example when both sides use a compatible CLIP/SigLIP or multimodal embedding model. Adding an image model is therefore not enough by itself; its dimension and semantic space must match the target index.

For image embedding, `ImageContent` (bytes + MIME type) is converted into a `data:<mime>;base64,...` URL that Spring AI `EmbeddingModel.embed(String)` consumes. The format aligns with the multimodal embedding adapters exposed by Spring AI, including the Bailian/DashScope multimodal embedding models.

Future extensions can add additional permitted `VectorContent` variants and a corresponding modality route, but the sealed type and exhaustive switch make that extension explicit.

---

## Multi-Provider Operations

### `VectorOperationsFacade`

`VectorOperationsFacade` is the optional multi-provider dispatcher. It is intentionally a concrete class rather than another interface: there is one core dispatch algorithm, and a single implementation does not benefit from an abstraction indirection.

The facade is created by `VectorAutoConfiguration` whenever at least one `VectorService` bean is registered in the Spring container. Each provider module contributes its bean by bean name, and the facade indexes them in a `Map<String, VectorService>`.

### Access and execution methods

- `primary()` returns the configured default provider (`platform.component.vector.facade.default-provider`).
- `get(providerName)` returns a named provider.
- `providerNames()` returns the immutable registered provider list.
- `execute(operation, action)` runs an operation through the primary and fallback chain.

The attempt sequence is:

```text
configured primary
  → fallback-chain[0]
  → fallback-chain[1]
  → ...
```

Names are de-duplicated while preserving order. Each provider is retried `maxRetries` times after the initial attempt, with exponential backoff based on `retryBackoffMillis` (`base * 2^attempt`). If all attempts fail, `VectorFacadeExecutionException` contains one `ProviderFailure` per provider, preserving the provider name and root cause.

### Failure aggregation and observability

`ProviderFailure(provider, cause)` records a single provider's last error. `VectorFacadeExecutionException` aggregates the list and exposes it through `getFailures()` so callers can log or route per-provider failures without losing context.

When a `MeterRegistry` is available, the facade records:

- `vector.facade.operation` timer tagged by `provider` and `operation`.
- `vector.facade.failure` counter tagged by `provider`, `operation`, and exception class.

The metrics cover individual retry attempts, making provider degradation visible before the fallback chain is exhausted. Logs identify the failed provider and the transition to the next provider.

The facade is useful when the application needs a controlled fallback policy. It does not merge results from multiple stores and does not make writes transactionally atomic across providers.

---

## Configuration Reference

### Common properties

| Property | Default | Description |
|---|---|---|
| `platform.component.vector.provider` | `REDIS` | Active provider enum value. |
| `platform.component.vector.embedding-provider` | `OPENAI` | Embedding configuration selection. |
| `platform.component.vector.default-index` | `documents` | Default logical index. |
| `platform.component.vector.indexes` | unset | Named `IndexConfig` map. |
| `platform.component.vector.indexes.*.dimension` | `1536` | Vector dimension; must match the model. |
| `platform.component.vector.indexes.*.metric` | `cosine` | Distance metric. |
| `platform.component.vector.indexes.*.index-type` | `hnsw` | Provider-supported index type. |
| `platform.component.vector.indexes.*.replicas` | `1` | Desired replica count where supported. |
| `platform.component.vector.indexes.*.shards` | `1` | Desired shard count where supported. |

### Batch properties

`platform.component.vector.batch.*` controls the reactive pipeline. See [Batch configuration](#batch-configuration) for the full table.

### Facade properties

`platform.component.vector.facade.*` controls `VectorOperationsFacade`.

| Property | Default | Description |
|---|---|---|
| `platform.component.vector.facade.default-provider` | `redisVectorServiceImpl` | Bean name of the primary provider. |
| `platform.component.vector.facade.fallback-chain` | empty | Ordered list of fallback bean names. |
| `platform.component.vector.facade.max-retries` | `2` | Retries per provider before fallback. Total attempts = `max-retries + 1`. |
| `platform.component.vector.facade.retry-backoff-millis` | `100` | Base for exponential backoff (`base, base*2, base*4, ...`). |

### Provider properties

Connection properties are intentionally not flattened into the common contract. Keep host, port, database, collection, schema, credentials, and native tuning under the corresponding provider configuration class.

The common index configuration is an intent. A provider may ignore a field it cannot express, preserve it in `IndexInfo.metadata`, or report an unsupported operation. This avoids pretending that Milvus shards, PostgreSQL tables, Redis indexes, and Weaviate classes have identical lifecycle semantics.

### Dimension rules

The configured index dimension, embedding model dimension, and provider schema dimension must agree. PostgreSQL specifically resolves the configured `IndexConfig.dimension` when creating the `vector(N)` column and uses its default only when no value is available. A pgvector table or native adapter must not be initialized with a stale hard-coded dimension while the application is configured for another model.

---

## Provider Comparison

| Provider | Provider value | Strength | Typical fit |
|---|---|---|---|
| Milvus | `milvus` | Native collections, ANN indexes, compaction, aliases. | Large-scale vector workloads. |
| Qdrant | `qdrant` | High-performance vector search, snapshot-triggered optimization. | Dedicated vector retrieval. |
| Redis | `redis` | Low-latency RediSearch vector fields. | Existing Redis deployments and moderate data. |
| PostgreSQL | `postgresql` | SQL, transactions, and `pgvector` control. | Systems already centered on PostgreSQL. |
| MongoDB Atlas | `mongodb` | Document and vector data in one platform. | Metadata-heavy document retrieval. |
| Neo4j | `neo4j` | Graph relationships plus vector indexes. | Knowledge graphs and recommendations. |
| Weaviate | `weaviate` | Native vector schema and hybrid-search direction. | Vector-first applications with schema APIs. |

`VectorMultiProviderGuard` refuses to start when more than one `VectorService` bean is detected. To switch providers, remove the previous provider module, add the new one, and update `platform.component.vector.provider`.

---

## Implementation Details

### `AbstractVectorService` base class

`AbstractVectorService` is the shared base class for all provider implementations. It owns the common Spring AI `Document` mapping and routes every call through provider hooks:

- Required hooks: `similaritySearchByVector`, `addEmbeddings`, `deleteByIds`, `getByIds`, `listDocumentsImpl`.
- Optional hooks with UOE defaults: every `createIndexImpl`, `deleteIndexImpl`, `indexExistsImpl`, `getIndexConfigImpl`, `countDocumentsImpl`, `listIndexesImpl`, `truncateIndexImpl`, `updateIndexConfigImpl`, `cloneIndexImpl`, `getIndexStatsImpl`, `describeIndexImpl`, `hybridSearchImpl`, `optimizeImpl`, `createAliasImpl`, `switchAliasImpl`, `backupImpl`, `restoreImpl`, plus `awaitIndexReadyImpl` (which polls `indexExists` on a 500 ms interval until the deadline).

The base class also owns:

- Single-record `add` / `addText` / `addImage(byte[], Path, URL)` / `update` / `updateText` / `updateImage(byte[], Path)`.
- Modality routing through the optional `ModalityAwareEmbeddingService` (injected through `@Autowired(required = false)`).
- Text rerank through the optional `RerankService` (`tryRerank`).
- Hybrid search fallback (`hybridSearchImpl` defaults to `searchByText`).
- Three-step health probe (`healthCheckImpl`: schema exists → document count readable → count non-negative).
- `BatchPipelineCoordinator` construction through three callbacks (`ConfigProvider`, `Embedder`, `DocumentWriter`).
- Public batch entry points that delegate to the coordinator.
- The shared `throwUnsupportedOps` helper.

The base class is intentionally large because the cross-cutting behavior is shared. The batch pipeline, however, is not in this class — it lives in `BatchPipelineCoordinator`.

### `BatchPipelineCoordinator` extraction

The coordinator lives in its own `pipeline` package because the base class should contain provider-facing contracts and shared single-record semantics, not a second orchestration engine. The independent package now owns the only batch implementation.

It is connected through three callbacks:

- `ConfigProvider`: reads current `VectorProperties.Batch` at each run.
- `Embedder`: turns a `VectorRecord` into an embedding (production delegates to `AbstractVectorService.embedForBatch`).
- `DocumentWriter`: writes one provider chunk of Spring AI documents (production delegates to `AbstractVectorService.addEmbeddings`).

Callbacks are preferable to copied fields here. They keep the coordinator independent from `AbstractVectorService`, support setter injection, and ensure the next run sees current configuration.

### Provider implementation pattern

Every provider module follows the same skeleton:

1. A `service.impl` class extends `AbstractVectorService`.
2. The constructor takes `(RerankService, VectorStore, EmbeddingModel)` and lets the base class wire the batch coordinator.
3. The required hooks call the provider's native SDK or SQL: `MilvusClient`, `QdrantClient`, `RedisCommands`, `JdbcTemplate`, `MongoCollection`, `Neo4jSession`, or `WeaviateClient`.
4. The optional hooks are overridden only when the provider has native support; otherwise they call `throwUnsupportedOps(op, indexName, providerName)`.
5. The provider module contributes Spring auto-configuration with a `@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "<name>")` so only one `VectorService` bean activates per application.

The pattern keeps the core small and the providers honest. Each provider has a clear surface of "what I do natively" versus "what I delegate to an external tool".

### Why sealed types

An enum can label content, but it cannot guarantee that text data has a text payload or that image data has bytes and an image MIME type. `VectorContent` puts the payload and invariant together. `Modality` then remains a small routing discriminator. This separation gives readable records, exhaustive switches, and clearer validation errors.

### Why the facade is a concrete class

`VectorOperationsFacade` is a single cross-provider algorithm with no provider-specific implementations of its own. Introducing an interface would add a substitution point without adding a second behavior. Callers still depend on the concrete facade because its retry/fallback semantics are part of the core utility.

---

## Known Limitations

| Limitation | Effect | Guidance |
|---|---|---|
| Operations are not uniformly native | 30 of 35 provider-operation slots currently throw UOE. | Check the matrix before using operational workflows. |
| Image model is optional | Image add/search fails without `imageEmbeddingModel`. | Configure a compatible image model and matching dimension. |
| Image content hashing is not enabled in the coordinator | Image duplicates are not removed by the text SHA-256 cache. | Supply stable item IDs or deduplicate upstream. |
| Provider filter DSLs differ | A filter expression is not portable across all backends. | Keep filters simple or isolate provider-specific expressions. |
| Batch buffering is in memory | Process loss discards buffered work. | Use an external durable ingestion queue when required. |
| `deleteIf` may enumerate records | Predicate deletes can be expensive. | Reserve for low-frequency management tasks. |
| Streaming batches may report unknown totals | `BatchStarted.total` is `-1` for streaming input. | Derive progress from completed/failed counts. |
| One `VectorService` per application | Multiple providers cannot coexist at runtime. | Use `VectorOperationsFacade` for failover, not parallel providers. |

---

## FAQ

### Which provider should I choose?

Choose Milvus or Qdrant for dedicated large-scale vector retrieval. Choose Redis when Redis is already operational and the data volume is moderate. Choose PostgreSQL when SQL and transactions are central. Choose MongoDB Atlas for document-plus-vector workloads. Choose Neo4j for graph relationships. Choose Weaviate for vector-first schema and hybrid-search workflows.

### How do I switch providers?

Add the desired provider module, remove the previous one, configure its native connection settings, align the index dimension and metric, and change `platform.component.vector.provider`. Provider-specific schema and operational behavior needs to be reviewed; the common Java calls remain stable.

### How do I switch embedding models?

Change the embedding model bean or AI component configuration, then create new indexes whose dimension and vector space match the new model. Existing vectors cannot be compared safely with vectors from an incompatible model.

### Why does an image call fail with `UnsupportedModalityException`?

The image model is absent or the content/modality pair is invalid. Register an image embedding model under `imageEmbeddingModel`, verify that it accepts the data URL format, and use `ImageContent` for `Modality.IMAGE`.

### Why is `writeApiCalls` smaller than the number of records?

It counts successful provider chunk calls, not records. A chunk of 100 records contributes one write API call.

### Why is an item failure reported at `PERSISTING` rather than `QUEUED`?

The stage identifies the operation that failed. `PERSISTING` tells a recovery consumer that loading and embedding completed and that the provider write should be retried or inspected.

### Does `failFast` cancel every already-running provider call?

It terminates the reactive batch path as soon as the failure propagates. Calls already handed to a provider may follow that provider's cancellation behavior; use an external idempotency key for safe retries.

### Does `VectorOperationsFacade` merge provider results?

No. It executes one action against the primary and ordered fallback providers. It returns the first successful result or throws an aggregate `VectorFacadeExecutionException`.

### Can every provider create aliases and run backups?

No. Only the real implementations in the matrix do so today. Unsupported paths throw `UnsupportedOperationException` with provider and index context.

### Why does `VectorMultiProviderGuard` refuse to start?

The component uses a single-provider architecture. When more than one `VectorService` bean is detected, the guard throws `IllegalStateException` listing every detected implementation. Remove all but one provider module to satisfy the constraint.

---

## Further Reading

- [Chinese documentation](./README.zh.md)
- [Milvus provider module](./atlas-richie-component-vector-milvus/)
- [Qdrant provider module](./atlas-richie-component-vector-qdrant/)
- [Redis provider module](./atlas-richie-component-vector-redis/)
- [PostgreSQL provider module](./atlas-richie-component-vector-postgresql/)
- [MongoDB Atlas provider module](./atlas-richie-component-vector-mongodb-atlas/)
- [Neo4j provider module](./atlas-richie-component-vector-neo4j/)
- [Weaviate provider module](./atlas-richie-component-vector-weaviate/)

---

`atlas-richie-component-vector` — one vector contract, explicit provider capabilities, and a bounded reactive ingestion path.
