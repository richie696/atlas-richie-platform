# atlas-richie-component-document-chunking 设计说明

## 1. 定位与边界

`atlas-richie-component-document-chunking` 是独立的通用文档切片组件：将 **文本字符串**按指定规则稳定地转换为 `Chunk`
列表。它服务于商用 RAG 知识库，也可以用于全文检索、摘要、审核与数据标注。

它不属于向量库组件，也不负责文件解析、Embedding、向量写入、权限判定或文档版本事务。职责边界如下：

```text
document-parser / 任意文本来源 -> String content
                                      |
                                      v
                        document-chunking -> List<Chunk>
                                      |
                                      v
知识库编排层 -> 补充来源、tenantId / scope / ACL / documentId / version
                                      |
                                      v
AI 组件 Embedding + vector 组件 -> VectorRecord、向量入库/检索
```

切片核心没有对 `atlas-richie-component-document-parser`、`atlas-richie-component-ai` 或 `atlas-richie-component-vector`
的编译期依赖。解析组件只是最常见的调用方之一：其公开的 `ReadResult` / `ReadEvent.Section` 中提供 `ParsedSection.text()`
，适配器或业务编排层取出该字符串后调用切片核心；也可以输入网页正文、数据库文本或消息内容。

来源位置、租户、部门与共享范围均由编排层在切片前后维护。切片组件只返回相对于输入 `content`
的字符边界；编排层负责将它与解析页码、章节路径关联，并将权限元数据写入 `VectorRecord.metadata`
。这样不会将企业权限模型、文件解析或向量入库固化进底层文本处理组件。

## 2. 目标、非目标与核心原则

目标：

- 保证同一输入和同一规则快照得到相同的切片结果，便于重建索引、定位引用和排障。
- 支持结构化文档、低质量 OCR 文本、FAQ、网页与高价值长文档的差异化策略。
- 每个切片可通过字符区间追溯回输入文本；文档版本和规则版本由适配器或编排层关联保存。
- 所有切片能力不依赖模型供应商；本组件不调用 Tokenizer 或 LLM。

非目标：

- 不在本组件内解析 PDF、DOCX、HTML 或 OCR；调用方只传入已准备好的字符串。
- 不管理知识库、文档生命周期、ACL 或向量索引。
- 不引入 `apiKey`、`embeddingProvider`、模型名、Tokenizer 等任何 AI 配置或依赖。

原则：纯文本输入、纯切片输出、确定性且无副作用；先利用调用方传入的可靠边界信息，再做长度约束；任何策略都必须有可预测的兜底策略。

## 3. 推荐公共模型与接口

首期实现应以以下稳定模型为中心，具体包名可随编码规范确定：

```java
public interface ChunkingService {
    ChunkingResult chunk(String content, ChunkingRule rule);
}

public record Chunk(
        int ordinal,
        String text,
        int charStart,
        int charEnd) {}

public record ChunkingResult(
        List<Chunk> chunks, ChunkingDiagnostics diagnostics) {}
```

`String content` 是唯一业务输入；`ChunkingRule` 是唯一行为输入。`Chunk` 的字符偏移永远相对于该 `content`
，因此调用方可无歧义地回映到自己的解析结果或原文。若上游存在页码、标题路径、文档 ID 等信息，编排层自行建立 `Chunk.ordinal`
与这些来源信息的映射；组件不接收、更不猜测这些业务字段。

`ChunkingRule` 采用不可变、带类型的规则对象，而不是把所有参数堆进一个巨型 DTO。每次入库时记录 `ruleId`、`ruleVersion`
、策略类型和生效参数快照。知识库保存规则定义；组件只消费已解析的规则快照。这样文档重切片、回滚与版本审计都有明确依据。

每个 `Chunk` 必须满足：非空、顺序稳定、文本不越过硬长度上限、`ordinal` 从零连续递增，且 `charStart` / `charEnd`
均是输入字符串中的合法区间。

## 4. 原子核心与上下游无缝组合

“无缝组合”不等于让核心包直接依赖所有相邻组件。正确的分层是：
**核心算法保持纯文本契约；相邻组件的公开契约由专用适配器连接。** 这样调用者既能像搭积木一样组合，又不会让 PDF
解析、LLM、向量存储等依赖泄漏进切片算法。

```text
                     ┌──────────────────────────────┐
同步：DocumentReader.read -> ReadResult              │
流式：DocumentReader.readStreaming -> ReadEvent      │
                     └──────────────┬───────────────┘
                                    │ parser adapter（可选）
                                    v
       document-chunking-core: String + ChunkingRule -> ChunkingResult
                                    │ vector 组件提供的 chunk adapter（可选）
                                    v
                      VectorRecord -> VectorService.upsert / upsertAll
```

建议将当前组件演进为以下 Maven 模块，而不是让一个 jar 同时承担三方依赖：

| 模块                                                      | 依赖          | 职责                                                                                                                |
|-----------------------------------------------------------|---------------|---------------------------------------------------------------------------------------------------------------------|
| `atlas-richie-component-document-chunking-core`           | 无组件依赖    | `String -> ChunkingResult`，所有九种确定性文本切片规则与测试。                                                      |
| `atlas-richie-component-document-chunking-parser-adapter` | parser + core | 消费 `ReadResult`、`ReadEvent.Section` 等**解析组件公开模型**，提取 `section.text()` 并保留调用方可用的来源上下文。 |
| （无）                                                    | —             | 切片组件不提供 vector adapter；它不应依赖或组装向量组件的输入模型。                                                 |

根 artifact 可作为聚合 BOM/便利依赖，或只发布 core；具体发布策略应与平台现有组件发布规范保持一致。无论采用哪种方式，`core`
均不能依赖 adapter，避免循环和职责反转。

### 4.1 与 document-parser 的稳定契约

当前 `DocumentReader` 的 **公开**同步输出是 `ReadResult`：其中 `sections()` 是有序的 `List<ParsedSection>`，每个
`ParsedSection` 提供 `text()`、`sectionPath()` 与 `meta()`。适配器的批式入口应为：

```java
List<ChunkedSection> chunk(ReadResult result, ChunkingRule rule);
```

每个 `ChunkedSection` 包含 `sectionIndex`、来源 `ParsedSection`、`ChunkingResult` 与可选来源区间；它不是 core 模型。批式接口按
section 独立切片，因而每个 `Chunk` 的 `charStart/charEnd` 始终相对于对应的 `ParsedSection.text()`；页码、章节路径、表格行等解析元数据仍由
adapter/编排层持有。

当前流式输出的公开契约是 `ReadEvent`：`Section(ParsedSection, fileName)`、`Image`、`Finished(ReadSummary, ...)`、`Failed`
。适配器消费 `Flow.Publisher<ReadEvent>`，并将文本段交给每文档独占的 `StreamingChunker`，向下游发布 `ChunkingEvent` /
`ChunkedSection`：

图片事件不进入文本切片；直接透传、忽略或交由 OCR/VLM 编排是调用方策略。`Failed` 必须终止当前文档的切片上下文，不能把前一文件残留状态带到下一文件。

`DocumentReader.readStreaming(...)` 仍是调用线程内的回调 API，适用于小文档；大文档应使用 `readPublisher(...)`。它在独占解析线程中按下游
demand 推进，`ParserChunkingAdapter` 继续把 demand 逐个传导到 parser。向量写入若需线程切换，仍必须使用容量明确的有界队列/并发策略，不能把无界异步隐藏在
core 中。

### 4.2 与 vector 的稳定契约

向量组件的写入输入是 `VectorRecord`，不是字符串；其 `VectorService.upsert(VectorRecord)` 负责单条幂等写入，
`VectorBulkOperations.upsertAll(indexName, Flux<VectorRecord>)` 负责冷流批量写入。故切片核心不能直接声称 `Chunk`
就是向量组件的输入，二者之间必须有一个显式、无副作用的组装步骤。

```java
// atlas-richie-component-vector 中的可选模块：
VectorRecord toVectorRecord(Chunk chunk, VectorRecordContext context);
```

该 adapter 应归属 `atlas-richie-component-vector`，建议命名为 `atlas-richie-component-vector-chunk-adapter`，依赖
`vector-core` 与 `document-chunking-core`。这符合“谁消费谁适配”：vector 是 `Chunk` 的接收方，负责将它转换为自身的
`VectorRecord` 输入；chunking 不应反向认识 vector。

`VectorRecordContext` 由知识库编排层提供，至少包含 `indexName`、`documentId`、`documentVersion`、`chunkNo`、稳定 `vectorId`
的生成规则、以及租户/部门/公司共享等业务元数据。adapter 用 `VectorRecord.text(indexName, chunk.text())`
创建记录，再填充这些字段与来源映射；它 **不得**负责 Embedding、调用 `VectorService`
、权限决策或文档版本事务。这个模块是可选依赖：只做文本切片的系统无需引入；直接使用 vector 组件的业务也无需引入 chunking。

因此，批式组合为：`ReadResult -> List<ChunkedSection> -> List<VectorRecord> -> VectorService.upsertAll(...)`；流式组合为：
`ReadEvent.Section -> ChunkingEvent.Section -> VectorRecord -> 有界批量/VectorService`。两条链路使用相同的 `Chunk` 与
`VectorRecordContext`，确保同步和流式入库的 ID、元数据及切片规则版本完全一致。

### 4.3 大文档的端到端流式与背压方案

仅把 `ChunkingService` 放进回调并不能解决大文档内存问题。本组件链路已经移除了 `DocumentReader.readAllBytes()` 和完成事件中的全文
`ReadResult`：输入在同一条可回收流上完成嗅探与解析，`ReadEvent.Finished` 仅携带摘要，`readPublisher(...)` 按订阅 demand
阻塞解析线程。Tika 文本与 Fesod/底层格式解析器仍各自存在实现级内存开销，因此“大文件可安全处理”仍依赖输入、单段、图片、并发和写入批次的显式上限。

推荐的目标流水线如下：

```text
可重开输入流 / 有界临时文件
  -> 格式探测（仅窥探前 8 KiB，返回同一个可继续读取的流）
  -> Parser Publisher<ReadEvent>
  -> Chunking Publisher<ChunkedSection>
  -> VectorRecord Publisher<VectorRecord>
  -> 有界并发 Embedding
  -> 有界批量写入向量库
```

#### 4.3.1 解析组件必须先具备真实流源

1. `ParserSource` 应提供可关闭、可重开的 `InputStream` 供应能力；文件使用 `Files.newInputStream`，URL 使用带最大字节数限制的响应流，不能在
   `DocumentReader` 中读取为 `byte[]`。
2. 格式探测必须在 **同一个** `BufferedInputStream` 上 `mark/reset` 后继续解析。`DocumentReader` 已统一包装输入流；
   `FormatDetector` 对未支持 mark/reset 的流快速失败，避免临时包装后丢失包装对象而使后续解析从错误位置开始。
3. 文本流完成事件应只携带 `DocumentReadSummary`（标题、作者、格式、计数），不得携带完整 `ReadResult`。同步 `read(...)`
   可另行订阅流并累积成 `ReadResult`，并受 `maxSyncDocumentBytes` / `maxSyncSections` 限制。
4. Tika 路径须以 SAX 事件内容处理器按段落/标题 emit 文本，不能先使用无限制 `BodyContentHandler` 再用 Jsoup
   处理全文。底层格式解析器仍可能有自身内存开销，但可消除平台额外的整文件副本和全文 DOM。
5. Fesod 文本行本身可逐行 emit；Excel 图片提取不能再为此保留全量 `byte[]`
   。应拆成独立的、可选的第二阶段：对可重开文件流重新读取，或在输入不可重开时落入有大小上限的临时文件；图片处理失败不应阻塞文本
   RAG 流。

#### 4.3.2 公开流协议与背压

建议解析适配层公开 `Flow.Publisher<ReadEvent>`（JDK 标准），或在 Spring/Reactor 链路中公开冷 `Flux<ReadEvent>`。现有
`ReadListener` 可保留为桥接实现，但它不能单独表达下游需求量、取消和背压。

每个文档使用一个会话。解析器在下游没有请求额度时暂停读取，在取消时关闭输入流，在失败时只发出该文档的失败终止事件。不得使用无界
`onBackpressureBuffer()`。若需要线程切换，使用容量明确的有界队列；队列满时由策略选择阻塞上游、限速或以可观测的拒绝失败结束，不能静默丢失段落。

`readStreaming(...)` 的现有同步回调可用于小文档；大文档应由新的 Publisher API
在专用解析线程上订阅。其“异步”来自明确的调度和背压，而不是在回调内部偷偷提交无限制任务。

#### 4.3.3 切片的流式会话

`ChunkingService.chunk(String, rule)` 保持纯、无状态。`parser-adapter` 增加每文档一个 `StreamingChunker`：收到文本段时立刻产出已确定边界的
Chunk，只保留尚未达到边界的尾部和 overlap。其内存上界应为 `maxCharacters + overlapCharacters + 单个上游段最大允许长度`
，而不是全文大小。

语义切片例外：模型需要完整候选文本才能判断主题转折，`SemanticChunkingService` 仅支持批式调用，不能接入 `StreamingChunker`
。大文档必须先按标题/页等确定性结构拆成有上限的候选单元，再对每个单元调用语义切片，避免把全文送入模型。

跨 `ParsedSection` 合并时，`StreamingChunker` 通过 `sourceSequence` 和相对字符区间维护来源映射；在 `Completed` 或 `Failed`
时显式 flush/abort，绝不将一个文档的尾部拼入下一个文档。稳定 ID 由 `documentId + documentVersion + chunkNo`（或其哈希）生成，确保重试
`upsert` 幂等。

#### 4.3.4 向量阶段的有界并发

向量组件已有 `upsertAll(indexName, Flux<VectorRecord>)` 与 `BulkIngestionPipeline`，可作为最后一段。但应补齐
`maxInFlightRecords` / `prefetch` 配置：Embedding 并发、写入并发、单批大小和等待时间共同决定最大在途记录数。实现时应使用有限
prefetch 的 `flatMap`，避免 Reactor 默认预取把大量 `VectorRecord` 提前堆进堆内存。

推荐的运行参数以压测为准：Embedding 并发受模型限流和 CPU
约束，写入批大小受向量库限制，队列容量只允许覆盖短时抖动。可用内存估算为“上游尾部 + 有界队列 + 在途 embedding 记录 +
写入批次”，每一项必须有显式上限。

失败语义为：单条 embedding/写入失败按稳定 ID 记录事件并按策略重试或进入死信；解析失败终止当前文档并取消尚未处理的下游；已成功
upsert 的记录无需回滚，可由文档版本切换事务决定是否激活。整篇文档的 active 版本只能在完成事件和写入结果均成功后切换。

## 5. 九种切片策略：推荐实现顺序与选择

九种策略不是互斥的“九个开关”。生产规则通常是“结构边界策略 + 长度约束策略 + 兜底策略”的组合，例如 Markdown
标题分段后，对超长段落再递归切片。下表按建议的落地和使用优先级排列。

| 优先级 | 策略                        | 适合文档                                                   | 优点                                                            | 局限与风险                                                            | 推荐使用方式                                                                                                    |
|--------|-----------------------------|------------------------------------------------------------|-----------------------------------------------------------------|-----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| 1      | 递归分隔符切片（S2）        | 通用纯文本、OCR、解析质量不稳定的 PDF/DOCX                 | 稳定、无需模型；按标题/段落/句子/字符逐级降级，语义破坏小于硬切 | 不理解真正语义；分隔符质量影响结果                                    | **默认策略**。以字符上限控制；超长块继续递归。模型 token 预算由调用方折算为字符预算。                           |
| 2      | Markdown/标题层级切片（S6） | Markdown、RST、含标题标记的制度、技术文档、产品手册        | 保留输入文本中的章节边界                                        | 标题缺失或标题下内容过长时不能单独完成切片                            | 仅识别输入字符串中的标题标记；标题路径、页码等业务元数据由调用方关联；超长标题块再执行 S2。                     |
| 3      | 页/幻灯片边界协同切片（S8） | PDF、扫描件、PPT、合同、报告、课件                         | 调用方可保留页码/幻灯片引用                                     | 组件不知道页码，直接“每 5 页一个 Chunk”常超过模型窗口且会混入无关页面 | 调用方按页/幻灯片分别调用本组件并维护映射；各页内用 S2，PPT 通常每页一个逻辑输入。                              |
| 4      | HTML 结构边界协同切片（S7） | 网页、Wiki、知识库导出 HTML                                | 调用方可在保留结构的前提下获得更好边界                          | DOM 清洗与 HTML 解析属于上游原子能力，不能放入本组件                  | 调用方先清洗并将正文块转为字符串或边界标记，再用 S2/S4 切片。                                                   |
| 5      | 句子滑动窗口（S5）          | FAQ、问答库、客服记录、会议纪要、自然语言短段              | 窗口重叠自然，召回上下文连续                                    | 多语言断句和超长句需要额外处理；不保留文档层级                        | 用句子数或 token 窗口建立滑窗，建议重叠 10%–20%；超长句回退 S2。                                                |
| 6      | 段落切片（S4）              | 排版规范的公告、制度、新闻、说明书                         | 实现简单、结果易解释、能保持自然段完整                          | 段落长度差异很大，短段会造成碎片                                      | 合并相邻短段至目标长度；超长段落走 S2；适合作为结构已弱化后的策略。                                             |
| 7      | 固定 Token 切片（S3）       | 对模型窗口敏感、已有确定 Tokenizer 的多语言 RAG            | 与实际模型输入长度一致，可严格控制成本和上下文窗口              | 依赖指定 Tokenizer；换模型可能改变边界                                | 不在本组件实现。由调用方按其模型 Tokenizer 预切分为字符串，或在独立的 AI 适配层实现后再调用本组件的确定性规则。 |
| 8      | 固定字符数切片（S1）        | 无结构的原始文本、应急兜底、测试数据                       | 最稳定、实现最简单、没有外部依赖                                | 对中文/英文 token 比例不准，最容易截断语义                            | 只作为无结构输入的保底；优先在换行/句末回退寻找边界，默认约 800 字符、重叠 80 字符。                            |
| 9      | 语义/LLM 边界建议（S9）     | 高价值合同、研究报告、复杂长文、追求高质量回答的付费知识库 | 可识别主题转换和隐含章节，理论上语义完整度最高                  | 成本、时延、模型漂移、不可复现与结构化输出失败                        | **不在本组件实现**。AI/知识库编排层先生成边界建议或候选文本，再调用本组件做确定性长度约束与校验；失败回退 S2。  |

### 5.1 默认策略矩阵

| 输入类型                          | 首选规则                             | 长度控制与兜底                                     |
|-----------------------------------|--------------------------------------|----------------------------------------------------|
| 普通 PDF/DOCX/TXT、OCR 文本       | S2 递归分隔符                        | 调用方传入字符预算；最终 S1                        |
| 有章节的 Markdown、制度、技术手册 | S6 标题层级                          | 标题块超长时 S2                                    |
| PDF 报告、合同、PPT               | S8 页/幻灯片协同                     | 调用方按页面输入，页面内 S2，禁止机械固定 5 页成块 |
| HTML/Wiki                         | S7 HTML 结构协同                     | 上游清洗正文并转为文本后 S2                        |
| FAQ、客服会话、会议纪要           | S5 句子滑窗                          | 超长句 S2                                          |
| 干净自然段文本                    | S4 段落合并                          | 超长段 S2                                          |
| 强模型窗口约束                    | 调用方 Tokenizer 预处理 + 本组件策略 | Tokenizer 版本由 AI/编排层管理                     |
| 高价值、可接受模型费用            | S9 语义切片                          | S2 候选块 + 结果校验 + S2 回退                     |

## 6. 规则组合、参数与版本化

推荐的规则配置表达为策略专属对象，统一公共字段包括：`ruleId`、`version`、`maxCharacters`、`overlapCharacters`、`minChunkSize`、
`maxChunksPerDocument`、`fallbackRule`。

本组件仅以字符长度作为硬约束。模型 Token 上限与 token 估算由 AI/知识库编排层负责：调用方应将与模型匹配的可用字符预算传入规则，或在调用前完成
Tokenizer 预处理。任何硬截断应在诊断信息中记录。384–512 tokens、40–64 tokens 重叠等模型相关建议属于调用方的 RAG
规则，而不是本组件默认值。

规则升级不能覆盖历史含义。知识库在新版本文档入库时将规则快照随文档版本保存；需要重切片时创建新版本并以新规则重新产出
`Chunk`，再由上层执行向量双写、切换和旧版本归档。

## 7. 自定义正则：作为受控扩展，而非第十个默认策略

业务确实可能需要按“第 N 条”“附件”“问：/答：”等领域边界切片。该能力应设计为 `CustomBoundaryRule`，供管理员或知识库编排层使用，作为
S2/S4 的边界探测器；它不应成为面向普通调用者的默认策略。

安全要求：

- 使用线性时间正则引擎（例如 RE2/J），不要直接对不可信模式使用 `java.util.regex`；线程中断或 30 秒超时不能可靠消除灾难性回溯。
- 限制模式长度、输入长度和单文档匹配次数；拒绝回溯引用、环视等 RE2/J 不支持的特性。
- 规则保存前编译校验；运行时记录拒绝原因和规则版本；无匹配或失败时回退到 S2。

## 8. 与商用 RAG 向量检索的衔接

切片组件不做权限过滤，也不保留业务来源信息。知识库编排层将每个 `Chunk` 映射为 `atlas-richie-component-vector` 的
`VectorRecord` 时补充：

```text
documentId, documentVersion, chunkOrdinal, tenantId,
visibilityScope（部门/公司共享）, departmentIds, ACL 标签,
ruleId, ruleVersion, pageStart/pageEnd, sectionPath
```

典型调用顺序为：`DocumentParser` 解析 → 编排层提取文本并保存来源映射 → `ChunkingService` 切片 → 编排层组装
`VectorRecord` → AI 组件向量化 → `VectorService` 入库。检索时先用 `tenantId` 和可见范围过滤：当前部门可查本部门文档与公司共享文档；公司共享文档可用
`visibilityScope=COMPANY` 表示，而不是复制到每个部门。之后才执行 ANN TopK 相似度检索。
`documentId + documentVersion + chunkOrdinal` 是定位、更新、删除和引用某个切片的稳定业务标识，不应用向量近似检索来完成这些确定性操作。

## 9. 可观测性、失败处理与验收

`ChunkingDiagnostics` 已记录请求策略、实际策略、输入/输出字符数与不可变 `ChunkingSignal` 集。当前信号包括 `HARD_TRUNCATED`、
`SEPARATOR_NOT_FOUND`、`TOKEN_LIMIT_FORCED`、`SEMANTIC_FALLBACK`、`INVALID_SEMANTIC_BOUNDARY`。处理耗时、模型名、重试次数仍由编排层的指标系统记录，避免
core 持有基础设施观测语义。

语义边界服务默认 `FAIL_FAST`：模型调用或响应结构失败会抛出 `SemanticBoundaryException`。调用方若明确优先可用性，可传入
`SemanticChunkingOptions(FALLBACK_TO_DETERMINISTIC)`；此时服务降级为 `RECURSIVE`，并在诊断中写入 `SEMANTIC_FALLBACK`
。重试、熔断和死信仍由 AI/知识库编排层负责。

### 策略工厂与实现隔离

九种策略不再集中在 `DefaultChunkingService` 或边界选择器的 `switch` 中。每种策略都有独立的 `ChunkingStrategy` 实现，统一接收
`String + ChunkingRule` 并返回 `ChunkingResult`；`ChunkingStrategyFactory`
是唯一的策略选择点。字符窗口、overlap、坐标、小尾段合并和诊断由共享支持类负责，策略类只表达本策略的边界规则。

流式会话从同一策略工厂取得 `StreamingChunkingStrategy`，因此批式与流式不会维护两套分隔符映射。语义策略不实现流式能力：它只有在应用提供
`SemanticBoundaryAdvisor` 时注册，并在流式场景明确拒绝，而不会在运行中静默改用其他算法。

首期验收标准：

- 同一 `String content + ChunkingRuleSnapshot` 重复执行，文本、顺序和字符区间完全一致。
- 任何输出均不为空、顺序连续，并满足配置的长度硬上限或有明确的截断诊断。
- `Chunk` 的字符区间均可无歧义地映射回输入字符串；上游来源映射由编排层集成测试保障。
- 建立 Markdown、PDF/OCR、HTML、FAQ、表格、超长句和中英文混合的黄金语料回归测试。
- 对正则边界规则覆盖超长输入与恶意模式测试；对 S9 覆盖结构化输出错误、超时和回退测试。

## 10. 分阶段实现计划

1. **核心确定性能力**：先创建 `core`，实现公共模型、S1/S2/S4/S5/S6/S8、规则快照、字符位置和诊断；这是所有知识库可以立即复用的无依赖能力。
2. **解析适配能力**：实现 `parser-adapter` 的 `ReadResult` 批式转换与 `ReadListener` 流式转换；由调用方决定执行线程与背压策略。
3. **向量适配能力**：在 `atlas-richie-component-vector` 中实现可选的 `vector-chunk-adapter`，提供 `Chunk -> VectorRecord`
   纯组装逻辑；不把 `VectorService` 调用放入适配器。
4. **结构边界输入能力**：由调用方将标题、页、HTML 区块等结构转换为文本边界或按块多次调用；组件不解析文件、不保存来源元数据。
5. **AI 协同能力**：S3 与 S9 由 AI/知识库编排层在组件外完成；其输出仍以字符串交给本组件进行确定性长度约束和结果校验。
6. **质量闭环**：结合检索命中率、答案引用完整率、上下文利用率和成本，针对知识库类型调整规则版本；不通过在线直接覆盖历史切片。

## 11. 配置、模型 SPI 与可选依赖

组件统一采用 `platform.component.document-chunking` 前缀，并遵循现有原子组件的 `enabled + 默认值 + 策略专属嵌套项` 形式：

```yaml
platform:
  component:
    document-chunking:
      enabled: true
      default-rule: recursive
      max-characters: 1600
      overlap-characters: 160
      min-chunk-characters: 80
      max-chunks-per-document: 10000
      streaming:
        # 每份文档在切片会话中允许保留的尾部字符上限；实际值至少为 max-characters
        max-pending-characters: 8192
      recursive:
        separators: ["\n\n", "\n", "。", "！", "？", ". ", " "]
```

配置只描述确定性切片行为、资源上限和回退规则，绝不包含模型厂商、模型名称、API Key、端点或 Tokenizer 实现。

S1/S2/S4/S5/S6/S7/S8 均为确定性算法，不需要 AI 大模型。S3 需要与目标模型相匹配的 Tokenizer，但不需要模型推理；core 仅定义
`TokenCounter` SPI，由调用方提供实现。S9 才需要大模型调用，core 只定义 `SemanticBoundaryAdvisor` SPI，调用方传入已经构建好的实例。为便利
Spring AI 使用者，可提供可选 `document-chunking-semantic-spring-ai` 子包：它只在构造时接收 `ChatModel`，不读取或管理任何模型配置。

## 12. 实施记录与代码落地顺序

1. 将根模块变更为聚合工程，创建 `core` 与可选 `parser-adapter`；core 不依赖 parser、AI 或 vector。
2. 在 core 中实现 `Chunk`、`ChunkingRule`、`ChunkingService`、`ChunkingProperties`、诊断模型和 `StreamingChunker`。静态 API
   始终为 `String -> ChunkingResult`。
3. 实现 S1/S2/S4/S5/S6 与安全的自定义边界规则；S7/S8 以调用方提供文本边界/分段的方式协同；S3/S9 通过 SPI 选择性接入。
4. parser-adapter 消费 parser 公开的 `ReadResult`、`ReadEvent` 与 `Flow.Publisher<ReadEvent>`，以每文档独立
   `StreamingChunker` 转换为同步结果或背压流；适配器按下游 demand 每次只向上游请求一个事件，跨 `Section` 保留尾部与
   overlap，并在 `Finished` 或上游 `onComplete` 时 flush。
5. vector 侧另建可选 `atlas-richie-component-vector-chunk-adapter`，负责 `Chunk -> VectorRecord` 组装，保持“谁消费谁适配”。
6. 对确定性、字符区间、规则版本、流式 flush/abort、背压与资源上限建立测试；编译和验证结论持续记录在本文件中。

该顺序让商用 RAG 先获得可预测、可审计且无外部依赖的基础切片能力；Tokenizer、LLM、文件解析与向量写入始终由各自的原子组件或知识库编排层承担，避免职责倒置。
