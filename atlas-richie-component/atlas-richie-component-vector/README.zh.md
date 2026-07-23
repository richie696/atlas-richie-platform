# Atlas Richie 向量组件（`atlas-richie-component-vector`）

> 为 7 种向量数据库后端提供统一的多模态向量检索、响应式批处理管道与运维 API。

`atlas-richie-component-vector` 在 7 个 provider 模块之上提供一套面向应用的统一向量 API。core 模块持有稳定的契约、多模态内容模型、响应式批处理管道、运维 API 与跨 provider 调度门面。每个 provider 模块持有自身 SDK 调用与连接配置，并向 Spring 容器贡献一个 `VectorService` 实现。

## 目录

- [概述](#概述)
  - [组件定位](#组件定位)
  - [组件不做什么](#组件不做什么)
- [功能特性](#功能特性)
- [架构](#架构)
  - [模块划分](#模块划分)
  - [核心包结构](#核心包结构)
- [API 参考](#api-参考)
  - [`VectorService` 接口](#vectorservice-接口)
  - [单条操作](#单条操作)
  - [搜索与索引操作](#搜索与索引操作)
  - [批量操作](#批量操作)
  - [核心模型类型](#核心模型类型)
- [响应式批处理管道](#响应式批处理管道)
  - [三阶段数据通路](#三阶段数据通路)
  - [Stage A — 嵌入生产者](#stage-a--嵌入生产者)
  - [Stage B — 写入消费者](#stage-b--写入消费者)
  - [Stage C — 终态汇总](#stage-c--终态汇总)
  - [Sinks 背压机制](#sinks-背压机制)
  - [`bufferTimeout` 攒批](#buffertimeout-攒批)
  - [批量配置](#批量配置)
  - [失败语义](#失败语义)
  - [去重与计数器](#去重与计数器)
- [运维 API](#运维-api)
  - [五个运维方法](#五个运维方法)
  - [per-provider 支持矩阵](#per-provider-支持矩阵)
  - [`throwUnsupportedOps` 助手](#throwunsupportedops-助手)
- [多模态嵌入](#多模态嵌入)
- [跨 Provider 调度](#跨-provider-调度)
  - [`VectorOperationsFacade`](#vectoroperationsfacade)
  - [访问与执行方法](#访问与执行方法)
  - [失败聚合与可观测性](#失败聚合与可观测性)
- [配置参考](#配置参考)
  - [通用属性](#通用属性)
  - [批处理属性](#批处理属性)
  - [门面属性](#门面属性)
  - [Provider 属性](#provider-属性)
- [Provider 对比](#provider-对比)
- [实现细节](#实现细节)
  - [`AbstractVectorService` 基类](#abstractvectorservice-基类)
  - [`BatchPipelineCoordinator` 抽离](#batchpipelinecoordinator-抽离)
  - [Provider 实现模式](#provider-实现模式)
  - [为什么用 sealed 类型](#为什么用-sealed-类型)
  - [为什么 facade 是具体类](#为什么-facade-是具体类)
- [已知限制](#已知限制)
- [常见问题](#常见问题)
- [延伸阅读](#延伸阅读)

---

## 概述

组件在 7 个 provider 模块之上提供统一的 `VectorService` 契约：

- Milvus
- Qdrant
- Redis
- PostgreSQL（含 `pgvector`）
- MongoDB Atlas
- Neo4j
- Weaviate

这种拆分是有意为之。core 持有所有"对调用方必须看起来一致"的部分——内容模型、模态路由、响应式批处理事件协议、运维 API、跨 provider 调度。provider 模块持有所有"对单一后端原生"的部分——SDK 调用、连接工厂、该后端支持的索引/别名/快照/恢复生命周期钩子。

### 组件定位

| 能力 | 行为 |
|---|---|
| 存储 | 通过统一的 `VectorRecord` 模型与 `VectorContent` 载荷存储文本与图片记录。 |
| 检索 | 文本检索、图片检索、向量检索、混合检索、多向量检索。 |
| 嵌入 | 通过 `ModalityAwareEmbeddingService` 将 `TEXT` / `IMAGE` 内容路由到对应 `EmbeddingModel`。 |
| 索引生命周期 | 创建、删除、检查、列表、清空、克隆、等待就绪、描述、统计、健康检查。 |
| 运维 | 暴露 `optimize`、`createAlias`、`switchAlias`、`backup`、`restore` 五个方法。 |
| 批处理 | 输出 `Flux<BatchEvent>`，对所有在途 item 推送嵌入与持久化进度。 |
| Provider 选择 | 通过 `platform.component.vector.provider` 选择激活的 provider。 |
| Provider 容错 | 可选的 `VectorOperationsFacade` 提供重试、回退链与 Micrometer 指标。 |

### 组件不做什么

组件不会替你创建嵌入模型，不会为每个 provider 归一化向量，也不会把不支持的 provider 能力伪装成可移植。运维 API 是诚实的：一个方法可以属于公共契约，但某个 provider 可能抛 `UnsupportedOperationException`。批处理缓冲是内存安全边界，不是持久化队列。facade 不做多库合并，也不保证跨 provider 写入原子性。

---

## 功能特性

- **多模态路由**：`ModalityAwareEmbeddingService` 持有文本模型（必填基线）与图片模型（可选扩展）。
- **Sealed 内容类型**：让编译器可见的内容域边界。
- **统一 `VectorRecord`**：单条写入、检索结果载体、批处理入参共用同一个模型。
- **`VectorService` 契约 42 个方法**：按能力分组，外加 `addImage(Path)` 与 `addBatch(List)` 两个便利重载。
- **响应式 `BatchEvent` 协议**：6 个事件类型 + 8 个值的 `Stage` 枚举，适合 UI 进度展示与运维监控。
- **三阶段批处理管道**：Stage A（嵌入）与 Stage B（写入）之间通过 Sinks 背压衔接，Stage B 用 `bufferTimeout` 攒批。
- **嵌入与写入并发可独立配置**：避免一边瓶颈压垮另一边。
- **失败语义可配置**：`failFast`、`dedupCacheSize`、`itemIdSource` 控制容错策略。
- **五个运维 API**：`optimize`、`createAlias`、`switchAlias`、`backup`、`restore`，配套 per-provider 支持矩阵。
- **`throwUnsupportedOps` 助手**：统一的、可被日志聚合的"未实现"消息格式。
- **七个 provider 实现**：共用一个抽象基类与一个管道协调器。
- **跨 provider 门面**：主备选择、重试、有序回退、失败聚合、Micrometer 指标。
- **文本检索重排**：当 `RerankService` 注入时生效。
- **Provider 感知的索引元数据**：通过 `IndexInfo` 与 `IndexStatus` 暴露。

把上述关注点集中到一个 core 的理由是可组合性——应用可以更换 provider、模型、批处理配置或运维策略，无需重写业务流。

---

## 架构

### 模块划分

父模块包含 9 个模块：1 个 parent POM、1 个 core 模块、7 个 provider 模块。

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

父模块不会仅靠 classpath 选择数据库。provider 模块提供 client、连接工厂与一个 `AbstractVectorService` 子类；`platform.component.vector.provider` 控制条件激活。`VectorMultiProviderGuard` 在启动期检测到多于一个 `VectorService` bean 时直接失败，把"单 provider"约束前置到启动期。

### 核心包结构

core 模块划分为 9 个包。拆分原则是让稳定契约与编排分离、provider 无关机制与公共表层分离。

| 包 | 职责 |
|---|---|
| `config` | `VectorProperties`、`VectorFacadeProperties`、`VectorAutoConfiguration`、`VectorMultiProviderGuard`。绑定到 `platform.component.vector.*`。 |
| `embeddings` | `ModalityAwareEmbeddingService`。将 `TEXT` / `IMAGE` 内容路由到对应 `EmbeddingModel`；暴露 `supportsModality` 与 `dimensionFor`。 |
| `enums` | `VectorProvider` 与 `EmbeddingProvider`，配置侧共享的枚举。 |
| `exceptions` | `UnsupportedModalityException`、`VectorStoreNotExistException`。 |
| `model` | `VectorRecord`、`VectorContent`、`BatchEvent`、`BatchStats`、`Stage`、`IndexInfo`、`IndexStatus`、`Modality`、`SearchOptions`、`HybridSearchOptions`、`VectorSearchResult`。 |
| `operations` | `VectorOperationsFacade`。跨 provider 调度门面，持有主备选择、重试、回退、指标。 |
| `pipeline` | `BatchPipelineCoordinator`。拥有响应式 Stage A / Stage B / Stage C 摄入管道。 |
| `service` | 公共 `VectorService` 契约；应用侧依赖入口；所有 provider 实现此接口。 |
| `service.impl` | `AbstractVectorService`。共享单条行为、provider 钩子、运维方法委托。 |

provider 模块只新增连接/配置类与 `service.impl` 实现，不会复制批处理管道或重新实现模态逻辑。

---

## API 参考

### `VectorService` 接口

公共契约按能力维度分组。接口共暴露 42 个运维方法 + 2 个便利重载（`addImage(Path)`、`addBatch(List)`）。

| 分组 | 运维方法数 | 便利重载数 | 用途 |
|---|---:|---:|---|
| Add | 4 | 1 | 添加文本、完整记录、图片字节、图片 URL。 |
| Update | 4 | 0 | 更新文本、完整记录、图片字节/路径。 |
| Delete | 2 | 0 | 按 ID 或按谓词删除。 |
| Get | 2 | 0 | 按 ID 或按 ID 列表读取。 |
| Text search | 3 | 0 | 默认 / 阈值 / 完整选项三种文本搜索。 |
| Image search | 3 | 0 | 默认 / 阈值 / Path 三种图片搜索。 |
| Index base | 6 | 0 | 创建、删除、检查、配置、计数、分页。 |
| Index extension | 6 | 0 | 列表、清空、更新配置、克隆、等待、描述。 |
| Stats & health | 2 | 0 | 索引统计与健康检查。 |
| Advanced search | 2 | 0 | 混合检索与多向量检索。 |
| Operations | 5 | 0 | `optimize`、`createAlias`、`switchAlias`、`backup`、`restore`。 |
| Batch | 3 | 1 | 从 `Flux` 增 / 改 / 删，加 `List` 便利重载。 |
| **合计** | **42** | **2** | 两个便利重载让调用方免去手动读字节或构造 `Flux`。 |

### 单条操作

Add 系列按模态分流：

- `addText` 构造 `TextContent` 记录，走文本模型。
- `add` 接收 `VectorRecord`，按 `record.getContent().modality()` 路由。
- `addImage` 接收字节或 `Path`，使用 `ImageContent`。
- `addImageUrl` 先下载远程图片再走图片路径。

Update 系列在 `AbstractVectorService` 中采用 delete + insert 等价语义——这是有意的，因为不同 provider 的 update 语义差异显著。`update` 必须携带 ID；add 操作在 ID 为空时生成 UUID。

Delete 与 Get 系列把 provider 特有查询封装在 `deleteByIds` 与 `getByIds` 之后。`deleteIf` 是面向管理的谓词路径，可能需要遍历索引评估谓词，不应作为高频删除 API 使用。

### 搜索与索引操作

文本搜索支持三种签名：仅 `limit`、`limit + minScore`、或完整 `SearchOptions`。当 `RerankService` 存在且 `rerank=true` 时，文本检索结果在向量召回后会被重排。图片检索默认不重排——双塔图片模型已在对齐的嵌入空间内表达相似度。

`SearchOptions` 携带：

- `rerank`（Boolean，默认 `true`）
- `minScore`（Double）
- `filterExpression`（String，provider-specific DSL）
- `namespace`（String）
- `limit`（Integer）
- `type`（String，文档类型过滤）

`HybridSearchOptions` 携带向量与关键词权重、关键词查询以及内嵌 `SearchOptions`。支持完整 BM25 + 向量混合语义的 provider 执行混合路径；其他 provider 退化为向量搜索并打日志。

索引生命周期方法覆盖：schema 创建/删除/存在性/配置/计数/分页/列表/清空/更新/克隆/就绪等待/描述/统计/健康。`IndexInfo` 报告名称、模态、维度、度量、索引类型、状态、文档数、时间戳与 provider 元数据。`IndexStatus` 暴露 `CREATING`、`READY`、`UPDATING`、`DELETING`、`FAILED`、`UNKNOWN` 六种状态，provider 把原生生命周期映射到最接近的状态。

### 批量操作

批量契约返回 `Flux<BatchEvent>`：

- `addBatch(String, Flux<VectorRecord>)`
- `addBatch(String, List<VectorRecord>)` 便利重载
- `updateBatch(String, Flux<VectorRecord>)`
- `deleteBatch(String, Flux<String>)`

Add 与 Update 路径共用 `BatchPipelineCoordinator`。协调器针对 add 语义优化；update 复用同一嵌入-写入路径，需要 delete + insert 时委托给单条 update 契约。Delete 走 provider 自身的 delete 路径并按 ID 输出进度事件，不经过 Sinks 管道。

### 核心模型类型

#### `Modality`

`Modality` 是一个枚举，当前包含 `TEXT` 与 `IMAGE` 两个值。枚举是最合适的形态——路由决策是有限且穷举的，并在 Java switch 表达式中使用。它只承担路由判别职责，不承载内容数据——内容载荷由 `VectorContent` 表达。

#### `VectorContent`

`VectorContent` 是一个 sealed 接口，仅允许 `TextContent` 与 `ImageContent` 两个实现：

- `TextContent(text, mimeType)`：校验非空文本，mimeType 默认为 `text/plain`。
- `ImageContent(data, mimeType)`：校验非空字节与 `image/*` 前缀 MIME。
- `ImageContent.ofPath`：从 `Path` 读取字节，保持同样的校验规则。

sealed 接口优于"枚举 + 对象映射"——不同内容载荷有不变量。模式匹配可以强制 `TEXT` 必须配 `TextContent`，`IMAGE` 必须配 `ImageContent`；新增内容类型必须显式扩展 permits 列表。

#### `VectorRecord`

`VectorRecord` 是服务边界的统一可变 Java Bean，承载：

- `id` 与 `indexName`
- `content`
- `metadata`
- `tags`、`source`、`namespace`、`status`
- `score`、`createdAt`、`updatedAt`

工厂方法 `text` / `image` / `imageUrl` 让常见路径更可读。`imageUrl` 把 URL 暂存到保留 metadata 字段，由管道或调用方负责解析真实字节。`metadata.__itemId` 是批处理 item 跟踪的保留键；`itemId()` 在该键缺失时回退到记录 ID。

`VectorRecord` 刻意与 Spring AI `Document` 解耦。core 在 provider 边界做转换，让应用代码不依赖 Spring AI 文档元数据约定。

#### `SearchOptions` 与 `HybridSearchOptions`

选项对象防止方法重载膨胀，让 provider 在不变更服务签名的情况下接收通用过滤意图。provider-specific 过滤表达式保留为 String，因为各家 DSL 不可互换。

#### `IndexInfo` 与 `IndexStatus`

`IndexInfo` 是归一化的观察结果，并非承诺所有 provider 暴露完全一致的元数据。`IndexStatus` 是生命周期枚举；provider 把原生生命周期映射到最接近的状态。

#### `BatchStats`

`BatchStats` 包含 `total`、`succeeded`、`failed`、耗时、`embeddingApiCalls`、`writeApiCalls`。计数器衡量的是 provider/API 调用次数，不是记录条数。一个 100 条记录的 chunk 是一次 write API call。

---

## 响应式批处理管道

### 三阶段数据通路

批处理路径由 `BatchPipelineCoordinator` 编排，含有两个并发数据阶段 + 一个终态汇总。

```text
Flux<VectorRecord>
      │
      ▼
Stage A：嵌入生产者
  flatMap(embeddingConcurrency)
  ├─ 解析 itemId
  ├─ 可选文本去重
  ├─ 路由到文本/图片模型
  ├─ 发 ItemStarted(LOADED)
  ├─ 发 StageChanged(LOADED → EMBEDDED)
  └─ 把 EmbeddedItem 推入 Sinks.Many
      │
      ▼
Sinks.Many<EmbeddedItem>
      │
      ▼
Stage B：写入消费者
  bufferTimeout(writeBatchSize, 50 ms)
  flatMap(writeConcurrency)
  ├─ 发 ItemStarted(PERSISTING)
  ├─ 每个 chunk 一次 writer.write(index, docs)
  ├─ 每次成功 chunk writeApiCalls += 1
  ├─ 发 StageChanged(PERSISTING → PERSISTED)
  └─ 每条发 ItemCompleted
      │
      ▼
Stage C：BatchCompleted(BatchStats)
```

拆分是有意的。嵌入调用往往是远程且模型受限的，写入则受 provider 吞吐与负载大小限制。两边并发独立配置，避免一边瓶颈压垮另一边。

### Stage A — 嵌入生产者

Stage A 订阅输入 `Flux<VectorRecord>`，通过 `flatMap(..., embeddingConcurrency)` 并发处理每条记录：

1. 按 `itemIdSource` 配置通过 `resolveItemId(record, contentHash, cfg)` 解析本批次的 item id。
2. 可选检查去重缓存；命中则短路发出 `ItemCompleted`，跳过嵌入与写入。
3. 通过 `ModalityAwareEmbeddingService` 把记录路由到文本或图片模型（由 `AbstractVectorService.embedForBatch` 委托）。
4. 为该记录发出 `ItemStarted(LOADED)` 与 `StageChanged(LOADED → EMBEDDED)`。
5. 把 `EmbeddedItem`（记录 + 向量 + itemId + 模态）推入 Sinks 衔接通道。

### Stage B — 写入消费者

Stage B 消费 Sinks 衔接的嵌入流，并按 provider 容量分组：

1. `bufferTimeout(writeBatchSize, 50 ms)`：累积到 `writeBatchSize` 条或 50 ms 超时即触发 flush。
2. `flatMap(..., writeConcurrency)`：允许最多 `writeConcurrency` 个 chunk 并发调用 `writer.write(index, docs)`。
3. 每个 chunk：先对每条发 `ItemStarted(PERSISTING)`，再调一次写入器；成功则 `writeApiCalls += 1`；再对每条发 `StageChanged(PERSISTING → PERSISTED)` 与 `ItemCompleted`。
4. `failFast=false` 时，写入器异常会让 chunk 内每条记录各发一条 `ItemFailed(PERSISTING, error)`，然后继续下一个 chunk。

生产者和消费者都跑在 `Schedulers.boundedElastic()` 上。Provider 写入是阻塞调用，弹性调度器把它们隔离在并行调度器池之外。

### Stage C — 终态汇总

Stage C 在 Stage A 与 Stage B 合并流结束后恰好发出一次 `BatchCompleted(BatchStats)`。即便出现非 failFast 失败、即便管道级异常，汇总依然会被发出——消费方可以始终依赖这个终态事件完成批次级清理。

### Sinks 背压机制

Stage A 不直接写入 Stage B，而是把嵌入后的 item 推到 `Sinks.Many<EmbeddedItem>`（unicast，`onBackpressureBuffer(backpressureBuffer)`）。

队列满时，推送使用 `Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(1))`——短暂的忙循环重试。这种方式在衔接处施加背压，避免内存无界增长。结果是一组有界的在途嵌入项：

- 嵌入生产者在缓冲有空间时可继续推进。
- 写入端变慢会让生产者最终等待。
- 输入 `Flux` 不会被队列满静默丢弃。
- 终态与已取消的 sink 视为终态条件，不再无限重试。

队列不是持久化机制。进程终止时内存中的批次丢失；持久化重试属于应用或摄入基础设施的职责。

### `bufferTimeout` 攒批

Stage B 应用 `bufferTimeout(writeBatchSize, Duration.ofMillis(50))`。触发 flush 的条件：

1. 已累积 `writeBatchSize` 条嵌入项。
2. 上一次 item 进入缓冲后 50 ms 超时。

大流场景下这种策略给 provider 高效调用；小流场景下得到有界延迟。`writeConcurrency` 控制并发推给写入器的 chunk 数。`writeBatchSize` 同时是 `bufferTimeout` 上界与单次 `writer.write` 调用承载的文档数。

因此 `writeApiCalls` 的语义是"成功的 chunk 级写入调用次数"，而不是 item、嵌入或事件的计数。这个定义让该指标在 provider 成本与吞吐分析上更有意义。

### 批量配置

`VectorProperties.Batch` 绑定到 `platform.component.vector.batch.*`。默认值与 `BatchPipelineCoordinator.DEFAULT_BATCH` 一致——YAML 缺省时行为不变。

| 属性 | 默认值 | 含义 |
|---|---:|---|
| `embeddingBatchSize` | `32` | 预留给支持列表输入的嵌入适配器的批大小。 |
| `embeddingConcurrency` | `8` | Stage A 最大并发嵌入任务数。 |
| `writeBatchSize` | `100` | 单次 provider 写入的 chunk 大小上限，同时是 `bufferTimeout` 缓冲上界。 |
| `writeConcurrency` | `4` | Stage B 最大并发 chunk 数。 |
| `backpressureBuffer` | `1024` | Sinks 衔接通道中等待的最大嵌入项数。 |
| `failFast` | `false` | 是否在首个失败时终止批次。 |
| `dedupCacheSize` | `10000` | 大于 0 时启用内容去重。 |
| `itemIdSource` | `METADATA` | 选择 `METADATA`、`ID` 或 `HASH` 作为 item id 来源。 |

所有字段会被协调器规范化到安全下界（`Math.max(1, ...)`），避免配置成 0 或负数导致管道死锁。

`ConfigProvider` 是回调而非拷贝字段。构造可能在可选的 Spring setter 注入完成前发生，回调可以在批次启动时观察到最新的 `VectorProperties.Batch`。因此 setter 注入的新配置在下一批 run 时立即生效，无需重建服务。

### 失败语义

`failFast=false` 时：

- 嵌入异常 → 该记录发 `ItemFailed(EMBEDDING, error)`，其他记录继续。
- chunk 写入异常 → chunk 内每条记录各发一条 `ItemFailed(PERSISTING, error)`，其他 chunk 继续。
- 管道仍会发出终态 `BatchCompleted`。

`failFast=true` 时：

- 嵌入或写入异常立刻终止当前管道。终止处理仍会尝试发出终态 `BatchCompleted` 汇总。
- 已经交给 provider 的调用遵循该 provider 自身的取消行为；安全重试需要外部幂等键。

消费方应使用 `ItemFailed.failedStage()` 做单条恢复，使用终态 `BatchCompleted.stats()` 做批次级清理。

### 去重与计数器

文本内容可以用 SHA-256 哈希作为可选去重缓存。命中后短路发出 `ItemCompleted`，不再嵌入或写入。图片路径不计算内容哈希；图片去重应交给稳定的应用 item id 或外部内容寻址存储。

`itemIdSource` 选择：

- `METADATA`：`metadata.__itemId`，回退到记录 ID。
- `ID`：记录 ID，回退到记录 `itemId()`。
- `HASH`：内容 SHA-256，回退到记录 ID。

批处理 item id 是事件关联键，不一定是 provider 记录 ID。

`embeddingApiCalls` 与 `writeApiCalls` 是原子计数器，分别在每次成功嵌入或每次成功 chunk 写入后自增。它们通过 `BatchStats` 暴露给监控与容量规划。

---

## 运维 API

### 五个运维方法

五个公共运维方法是契约的一部分。各 provider 的支持程度不同；支持矩阵描述的是"今天实现了什么"，而不是"契约假装可移植什么"。

| 方法 | 含义 | 典型场景 |
|---|---|---|
| `optimize(indexName)` | 请求 provider 压缩、合并段或重建内部结构。 | 回收空间、提升查询性能。 |
| `createAlias(indexName, alias)` | 创建指向索引的别名。 | 稳定应用名、阶段化发布。 |
| `switchAlias(oldIndexName, newIndexName, alias)` | 在支持的情况下原子地把别名切换到新索引。 | 蓝绿索引替换、零停机切换。 |
| `backup(indexName, targetPath)` | 把索引导出或快照到目标路径。 | 灾难恢复、离线留存。 |
| `restore(sourcePath, indexName)` | 从 provider 兼容的备份恢复索引。 | 恢复或环境引导。 |

这些方法返回 `boolean`，因为成功的 provider 实现上报成功/失败结果。不支持的实现不返回 `false`，而是通过 `throwUnsupportedOps` 抛 `UnsupportedOperationException`。

### per-provider 支持矩阵

下表描述每个 provider 实现当前实际能力：

| Provider | `optimize` | `createAlias` | `switchAlias` | `backup` | `restore` |
|---|:---:|:---:|:---:|:---:|:---:|
| Milvus | Real | Real | Real | UOE | UOE |
| Qdrant | Real | UOE | UOE | UOE | UOE |
| PostgreSQL | Real | UOE | UOE | UOE | UOE |
| Redis | UOE | UOE | UOE | UOE | UOE |
| MongoDB Atlas | UOE | UOE | UOE | UOE | UOE |
| Neo4j | UOE | UOE | UOE | UOE | UOE |
| Weaviate | UOE | UOE | UOE | UOE | UOE |

- **Milvus**：`optimize` 调用 `manualCompact`；`createAlias` 调用 `createAlias`；`switchAlias` 调用 `alterAlias`。备份与恢复由 `milvus-backup` 外部工具承担，provider 实现抛 UOE，提示调用方走外部工具。
- **Qdrant**：`optimize` 通过 `createSnapshotAsync` 触发段合并与索引合并（快照本身副作用）；备份与恢复走 `qdrant-backup`；不支持别名。
- **PostgreSQL**：`optimize` 在底层表上执行 `VACUUM ANALYZE`；别名、备份、恢复分别依赖 `CREATE VIEW`、`pg_dump`、`pg_restore`，provider 抛 UOE。
- **Redis、MongoDB Atlas、Neo4j、Weaviate**：五个运维方法全部抛 UOE；它们的核心能力是检索与持久化，不是原生别名/快照原语。

共 35 个 provider-operation 槽位：5 个真实实现，30 个 `UnsupportedOperationException` 路径。矩阵就是"什么可移植"的契约。

### `throwUnsupportedOps` 助手

provider 实现对不支持的 boolean 操作调用这个共享助手：

```java
@Override
protected boolean backupImpl(String indexName, String targetPath) {
    return throwUnsupportedOps("backup", indexName, "qdrant");
}
```

助手始终抛出包含操作名、provider 与索引的消息：

```text
backup 未实现: provider=qdrant, index=demo
```

返回类型是 `boolean`，匹配 Java 的"无条件抛"模式——调用方可以在声明返回 `boolean` 的方法里直接写 `return throwUnsupportedOps(...)`，不需要补一条不可达的 `return` 语句。

这个助手把原本散落在 7 个 provider 实现中 30+ 处 `throw new UnsupportedOperationException(...)` 集中到一处。消息格式专为日志聚合与栈追踪关联而设计，同时携带操作名与 provider 标识。

助手不是能力探针，从不返回 `false`。需要能力发现的场景请通过支持矩阵维护 provider 策略，或在运维边界捕获 `UnsupportedOperationException`。

---

## 多模态嵌入

`ModalityAwareEmbeddingService` 是核心路由器。`embed(modality, content)` 方法执行穷举 switch：

```text
TEXT  + TextContent  → textModel.embed(text)
IMAGE + ImageContent → imageModel.embed(data URL)
```

`textModel` 必填，因为文本是基线能力。`imageModel` 可选。`supportsModality(TEXT)` 构造后始终返回 `true`；`supportsModality(IMAGE)` 仅在 image 模型存在时返回 `true`。`dimensionFor` 暴露所选模型的维度；image 模型不可用时返回 0。

服务会校验模态/内容对齐。`TEXT` 模态配 `ImageContent`，或 `IMAGE` 模态配 `TextContent`，会被拒绝而不是被强转。这样模型输入错误被推到调用方最近的位置。

跨模态检索同样受向量空间约束。文本-图片、图片-文本检索只有当两侧模型输出可比较（例如都使用 CLIP/SigLIP 或多模态嵌入模型）时才有意义。因此，引入图片模型本身不够——其维度与语义空间必须与目标索引匹配。

图片嵌入时，`ImageContent`（字节 + MIME）会被转换为 `data:<mime>;base64,...` URL，由 Spring AI `EmbeddingModel.embed(String)` 消费。该格式与 Spring AI 暴露的多模态嵌入适配器对齐，包括 Bailian/DashScope 的多模态嵌入模型。

未来扩展可以新增 `VectorContent` 变体与对应模态路由，但 sealed 类型与穷举 switch 让扩展必须显式进行。

---

## 跨 Provider 调度

### `VectorOperationsFacade`

`VectorOperationsFacade` 是可选的多 provider 调度器。它是有意的具体类而非接口：调度算法只有一种实现，加一层接口只会多一个替换点而不增加行为。

facade 由 `VectorAutoConfiguration` 创建——只要 Spring 容器中存在至少一个 `VectorService` bean。每个 provider 模块按 bean name 贡献实例，facade 用 `Map<String, VectorService>` 索引。

### 访问与执行方法

- `primary()`：返回配置的默认 provider（`platform.component.vector.facade.default-provider`）。
- `get(providerName)`：按名称返回 provider。
- `providerNames()`：返回已注册的 provider 列表（不可变视图）。
- `execute(operation, action)`：在主备链上执行操作。

尝试序列：

```text
配置的主 provider
  → fallback-chain[0]
  → fallback-chain[1]
  → ...
```

名称按顺序去重。每个 provider 在初次尝试后再重试 `maxRetries` 次，按 `retryBackoffMillis` 指数退避（`base * 2^attempt`）。若全部失败，`VectorFacadeExecutionException` 携带每个 provider 一条 `ProviderFailure`，保留 provider 名与根因。

### 失败聚合与可观测性

`ProviderFailure(provider, cause)` 记录单个 provider 的最后一次错误。`VectorFacadeExecutionException` 聚合列表并通过 `getFailures()` 暴露，调用方可以按 provider 记录或路由失败而不丢失上下文。

`MeterRegistry` 可用时，facade 上报：

- `vector.facade.operation`：Timer，按 `provider` 与 `operation` 打 tag。
- `vector.facade.failure`：Counter，按 `provider`、`operation`、异常类打 tag。

指标覆盖单次重试尝试，让 provider 降级在回退链耗尽前可见。日志会记录失败的 provider 与向下一个 provider 的切换。

facade 适用于需要受控回退策略的场景。它不合并多库结果，也不保证跨 provider 写入原子性。

---

## 配置参考

### 通用属性

| 属性 | 默认值 | 说明 |
|---|---|---|
| `platform.component.vector.provider` | `REDIS` | 激活的 provider 枚举值。 |
| `platform.component.vector.embedding-provider` | `OPENAI` | 嵌入配置选择。 |
| `platform.component.vector.default-index` | `documents` | 默认逻辑索引。 |
| `platform.component.vector.indexes` | 未设置 | 命名 `IndexConfig` 映射。 |
| `platform.component.vector.indexes.*.dimension` | `1536` | 向量维度，必须与模型一致。 |
| `platform.component.vector.indexes.*.metric` | `cosine` | 距离度量。 |
| `platform.component.vector.indexes.*.index-type` | `hnsw` | provider 支持的索引类型。 |
| `platform.component.vector.indexes.*.replicas` | `1` | 副本数（如支持）。 |
| `platform.component.vector.indexes.*.shards` | `1` | 分片数（如支持）。 |

### 批处理属性

`platform.component.vector.batch.*` 控制响应式管道。完整字段表见 [批量配置](#批量配置)。

### 门面属性

`platform.component.vector.facade.*` 控制 `VectorOperationsFacade`。

| 属性 | 默认值 | 说明 |
|---|---|---|
| `platform.component.vector.facade.default-provider` | `redisVectorServiceImpl` | 主 provider 的 bean name。 |
| `platform.component.vector.facade.fallback-chain` | 空 | 有序的回退 bean name 列表。 |
| `platform.component.vector.facade.max-retries` | `2` | 每个 provider 在回退前的重试次数，总尝试 = `max-retries + 1`。 |
| `platform.component.vector.facade.retry-backoff-millis` | `100` | 指数退避基准（`base, base*2, base*4, ...`）。 |

### Provider 属性

连接属性刻意不扁平化进公共契约。host、port、database、collection、schema、凭据与原生调优参数都放在对应 provider 配置类下。

公共索引配置是"意图"。Provider 可能忽略它无法表达的字段、把它存到 `IndexInfo.metadata`、或上报不支持。这样避免假装 Milvus shard、PostgreSQL 表、Redis 索引、Weaviate 类具有相同的生命周期语义。

### 维度规则

配置的索引维度、嵌入模型维度、provider schema 维度必须一致。PostgreSQL 在创建 `vector(N)` 列时解析配置的 `IndexConfig.dimension`，仅在配置缺失时回退到默认值。当应用切到其他模型时，pgvector 表或原生适配器不应使用陈旧的硬编码维度初始化。

---

## Provider 对比

| Provider | provider 值 | 优势 | 典型场景 |
|---|---|---|---|
| Milvus | `milvus` | 原生 collection、ANN 索引、compaction、别名。 | 大规模向量负载。 |
| Qdrant | `qdrant` | 高性能向量检索、快照触发优化。 | 专用向量检索。 |
| Redis | `redis` | 低延迟 RediSearch 向量字段。 | 已部署 Redis、数据量适中。 |
| PostgreSQL | `postgresql` | SQL、事务与 `pgvector` 控制。 | 已围绕 PostgreSQL 构建的系统。 |
| MongoDB Atlas | `mongodb` | 文档与向量一体平台。 | 元数据密集的文档检索。 |
| Neo4j | `neo4j` | 图关系 + 向量索引。 | 知识图谱与推荐。 |
| Weaviate | `weaviate` | 原生向量 schema、混合检索方向。 | schema API 优先的向量优先应用。 |

`VectorMultiProviderGuard` 检测到多于一个 `VectorService` bean 时直接拒绝启动。要切换 provider，移除原 provider 模块、新增目标 provider 模块，并修改 `platform.component.vector.provider`。

---

## 实现细节

### `AbstractVectorService` 基类

`AbstractVectorService` 是所有 provider 实现的共享基类。它持有公共 Spring AI `Document` 映射，所有调用都路由到 provider 钩子：

- 必选钩子：`similaritySearchByVector`、`addEmbeddings`、`deleteByIds`、`getByIds`、`listDocumentsImpl`。
- 可选钩子（默认 UOE）：`createIndexImpl`、`deleteIndexImpl`、`indexExistsImpl`、`getIndexConfigImpl`、`countDocumentsImpl`、`listIndexesImpl`、`truncateIndexImpl`、`updateIndexConfigImpl`、`cloneIndexImpl`、`getIndexStatsImpl`、`describeIndexImpl`、`hybridSearchImpl`、`optimizeImpl`、`createAliasImpl`、`switchAliasImpl`、`backupImpl`、`restoreImpl`，以及按 500 ms 轮询 `indexExists` 的 `awaitIndexReadyImpl`。

基类还持有：

- 单条 `add` / `addText` / `addImage(byte[], Path, URL)` / `update` / `updateText` / `updateImage(byte[], Path)`。
- 通过可选 `ModalityAwareEmbeddingService`（`@Autowired(required = false)` 注入）的模态路由。
- 通过可选 `RerankService` 的文本重排（`tryRerank`）。
- 混合搜索回退（`hybridSearchImpl` 默认调 `searchByText`）。
- 三步健康探针（`healthCheckImpl`：schema 存在 → 文档计数可读 → 计数非负）。
- 通过三个回调（`ConfigProvider`、`Embedder`、`DocumentWriter`）构造 `BatchPipelineCoordinator`。
- 委托到协调器的批量公共入口。
- 共享的 `throwUnsupportedOps` 助手。

基类较大是有理由的——共有行为集中在一个地方。但批处理管道不在这个类里——它在 `BatchPipelineCoordinator` 中。

### `BatchPipelineCoordinator` 抽离

协调器位于独立的 `pipeline` 包，因为基类应该只包含 provider 面向的契约与共享单条语义，不应该背负第二套编排引擎。独立的包承担唯一的批处理实现。

它通过三个回调与基类衔接：

- `ConfigProvider`：每次 run 读取当前 `VectorProperties.Batch`。
- `Embedder`：把 `VectorRecord` 转换为嵌入向量（生产环境委托 `AbstractVectorService.embedForBatch`）。
- `DocumentWriter`：写入一个 provider chunk 的 Spring AI 文档（生产环境委托 `AbstractVectorService.addEmbeddings`）。

这里回调优于拷贝字段：它让协调器独立于 `AbstractVectorService`、支持 setter 注入、并保证下一批 run 看到当前配置。

### Provider 实现模式

每个 provider 模块遵循同一骨架：

1. `service.impl` 类继承 `AbstractVectorService`。
2. 构造函数取 `(RerankService, VectorStore, EmbeddingModel)`，让基类组装批处理协调器。
3. 必选钩子调用 provider 原生 SDK 或 SQL：`MilvusClient`、`QdrantClient`、`RedisCommands`、`JdbcTemplate`、`MongoCollection`、`Neo4jSession` 或 `WeaviateClient`。
4. 可选钩子只在 provider 原生支持时重写；否则调用 `throwUnsupportedOps(op, indexName, providerName)`。
5. provider 模块的 Spring 自动配置使用 `@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "<name>")`，每个应用只激活一个 `VectorService` bean。

这个模式让 core 保持轻量、provider 保持诚实。每个 provider 对"我能原生做什么"与"我委托给外部工具做什么"都有清晰的边界。

### 为什么用 sealed 类型

枚举可以标注内容，但无法保证文本数据一定有文本载荷，也无法保证图片数据一定有字节与图片 MIME。`VectorContent` 把载荷与不变量放在一起，`Modality` 退化为小型路由判别器。这种分离带来可读的记录、穷举的 switch 与更清晰的校验错误。

### 为什么 facade 是具体类

`VectorOperationsFacade` 是单一的跨 provider 算法，本身没有 provider 特定的实现。引入接口只会增加替换点而不增加行为。调用方依然依赖具体 facade，因为它的重试/回退语义是 core 工具的一部分。

---

## 已知限制

| 限制 | 影响 | 建议 |
|---|---|---|
| 运维能力非均匀原生 | 35 个 provider-operation 槽位中 30 个当前抛 UOE。 | 使用运维工作流前查阅支持矩阵。 |
| 图片模型可选 | 未配置 `imageEmbeddingModel` 时图片 add/search 失败。 | 配置兼容的图片模型并匹配维度。 |
| 协调器不计算图片内容哈希 | 图片去重不会被文本 SHA-256 缓存处理。 | 使用稳定 item id 或上游去重。 |
| Provider 过滤 DSL 差异大 | 过滤表达式不可跨所有后端移植。 | 保持过滤表达式简单，或将 provider-specific 表达式隔离。 |
| 批处理缓冲在内存 | 进程丢失会丢弃缓冲中的工作。 | 需要时使用外部持久化摄入队列。 |
| `deleteIf` 可能遍历记录 | 谓词删除代价较高。 | 仅用于低频管理任务。 |
| 流式批次可能上报未知总数 | `BatchStarted.total` 在流式输入时为 `-1`。 | 由 succeeded/failed 计数派生进度。 |
| 每个应用仅一个 `VectorService` | 运行时不能并存多个 provider。 | 用 `VectorOperationsFacade` 做容错，而非并行 provider。 |

---

## 常见问题

### 应该选哪个 provider？

大规模专用向量检索选 Milvus 或 Qdrant；已部署 Redis 且数据量适中选 Redis；需要 SQL 与事务选 PostgreSQL；需要文档与向量一体选 MongoDB Atlas；需要图关系选 Neo4j；需要 schema API 与混合检索方向选 Weaviate。

### 怎么切换 provider？

新增目标 provider 模块、移除原 provider 模块、配置其原生连接、对齐索引维度与度量、修改 `platform.component.vector.provider`。Provider 特有 schema 与运维行为需要重新评估，公共 Java 调用保持稳定。

### 怎么切换嵌入模型？

更换嵌入模型 bean 或 AI 组件配置，并创建维度与向量空间匹配的新索引。不兼容模型产出的向量不可直接比较。

### 为什么图片调用报 `UnsupportedModalityException`？

图片模型未配置，或内容/模态对不合法。请注册一个 `imageEmbeddingModel` 嵌入模型 bean，确认其接受 data URL 格式，并使用 `ImageContent` 配合 `Modality.IMAGE`。

### 为什么 `writeApiCalls` 比记录数少？

它统计的是成功的 provider chunk 调用次数，而非记录数。一个 100 条记录的 chunk 贡献一次 write API call。

### 为什么单条失败上报在 `PERSISTING` 而不是 `QUEUED`？

Stage 标识失败发生的位置。`PERSISTING` 让恢复消费方知道加载与嵌入已完成，应当重试或检查的是 provider 写入。

### `failFast` 会取消所有已发起的 provider 调用吗？

它会在失败传播时立即终止响应式批处理路径。已交给 provider 的调用遵循该 provider 的取消行为；安全重试需要外部幂等键。

### `VectorOperationsFacade` 会合并 provider 结果吗？

不会。它在主 provider 与有序回退 provider 上执行同一动作，返回首个成功结果或抛出聚合的 `VectorFacadeExecutionException`。

### 所有 provider 都能创建别名与备份吗？

不能。仅有支持矩阵中标记为 Real 的实现支持。其他路径抛携带 provider 与索引上下文的 `UnsupportedOperationException`。

### 为什么 `VectorMultiProviderGuard` 拒绝启动？

组件采用单 provider 架构。检测到多于一个 `VectorService` bean 时，guard 抛 `IllegalStateException` 并列出所有检测到的实现。保留一个 provider 模块、移除其他即可满足约束。

---

## 延伸阅读

- [英文文档](./README.md)
- [Milvus provider 模块](./atlas-richie-component-vector-milvus/)
- [Qdrant provider 模块](./atlas-richie-component-vector-qdrant/)
- [Redis provider 模块](./atlas-richie-component-vector-redis/)
- [PostgreSQL provider 模块](./atlas-richie-component-vector-postgresql/)
- [MongoDB Atlas provider 模块](./atlas-richie-component-vector-mongodb-atlas/)
- [Neo4j provider 模块](./atlas-richie-component-vector-neo4j/)
- [Weaviate provider 模块](./atlas-richie-component-vector-weaviate/)

---

`atlas-richie-component-vector` —— 一套向量契约、明确的 provider 能力边界、有界的响应式摄入路径。
