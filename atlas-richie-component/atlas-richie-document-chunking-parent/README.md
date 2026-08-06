# Atlas Richie Document Chunking Component (atlas-richie-document-chunking-parent)

面向商用 RAG 与知识库的确定性文档切片组件：把已准备好的文本稳定转换为可追溯的 `Chunk`，支持批式、流式、Parser 适配与可选语义边界协同。

> **Audience**：业务开发者 / 知识库工程师。关注“组件解决什么问题、有什么能力、怎么用、怎么配置、出错怎么办”的读者可以直接从本
> README 开始。
> **深度说明**：当前目录暂无专题文档；本 README 包含公开使用契约、配置和运维边界。

---

## 📖 Contents

- [🚀 快速上手](#-快速上手)
    - [1) 添加依赖](#1-添加依赖)
    - [2) 配置](#2-配置)
    - [3) 写代码](#3-写代码)
    - [Ops 入口参考](#ops-入口参考)
- [🔧 核心能力](#-核心能力)
    - [公共接口与确定性保证](#公共接口与确定性保证)
    - [固定字符数切片（FIXED）](#固定字符数切片fixed)
    - [递归分隔符切片（RECURSIVE）](#递归分隔符切片recursive)
    - [固定 Token 切片（TOKEN）](#固定-token-切片token)
    - [段落切片（PARAGRAPH）](#段落切片paragraph)
    - [句子滑动窗口（SENTENCE）](#句子滑动窗口sentence)
    - [Markdown 标题层级切片（MARKDOWN）](#markdown-标题层级切片markdown)
    - [HTML 结构边界切片（HTML）](#html-结构边界切片html)
    - [页 / 幻灯片协同切片（PAGE）](#页--幻灯片协同切片page)
    - [语义边界协同切片（SEMANTIC）](#语义边界协同切片semantic)
    - [四个公共模型的不变量](#四个公共模型的不变量)
    - [流式切片 `StreamingChunker`](#流式切片-streamingchunker)
    - [SPI 扩展点](#spi-扩展点)
- [🛡 进阶能力](#-进阶能力)
    - [与 `document-parser` 适配](#与-document-parser-适配)
    - [与 Spring AI 适配](#与-spring-ai-适配)
    - [与 vector 组件的契约边界](#与-vector-组件的契约边界)
    - [大文档端到端链路](#大文档端到端链路)
    - [自定义规则边界](#自定义规则边界)
- [📦 配置一览](#-配置一览)
    - [模块开关与公共参数](#模块开关与公共参数)
    - [流式配置](#流式配置)
    - [递归策略配置](#递归策略配置)
    - [配置按功能导航](#配置按功能导航)
- [🎯 最佳实践](#-最佳实践)
    - [场景与策略选择](#场景与策略选择)
    - [长度参数选择](#长度参数选择)
    - [流式内存预算](#流式内存预算)
    - [规则版本化](#规则版本化)
    - [诊断与可观测指标](#诊断与可观测指标)
- [🛣 切片质量增强](#-切片质量增强)
    - [Spring AI 语义切片异常降级（默认 `FAIL_FAST`）](#spring-ai-语义切片异常降级默认-fail_fast)
    - [`ChunkingDiagnostics` 扩展为“信号枚举”](#chunkingdiagnostics-扩展为信号枚举)
    - [严格递归切片（独立 `RecursiveChunker`）](#严格递归切片独立-recursivechunker)
    - [推荐实施顺序](#推荐实施顺序)
- [❓ FAQ](#-faq)
- [📚 专题文档](#-专题文档)

---

## 🚀 快速上手

### 1) 添加依赖

父聚合坐标是 `cn.richie696.component:atlas-richie-document-chunking-parent`
，实际业务按能力选择子模块。版本由平台依赖管理统一提供，或按项目版本管理规范显式指定。

#### 仅使用纯文本核心

`document-chunking-core` 只处理 `String`，不依赖 `document-parser`、AI 或
vector。它适合网页清洗结果、数据库字段、消息正文和已经由业务解析好的文本。

```xml
<dependency>
    <groupId>cn.richie696.component</groupId>
    <artifactId>document-chunking-core</artifactId>
</dependency>
```

#### 连接 `document-parser` 的可选适配器

`document-chunking-parser-adapter` 依赖 core 与 `atlas-richie-document-parser`。它把
`ReadResult` 或 `Flow.Publisher<ReadEvent>` 转换为切片结果和 `ChunkingEvent`。

```xml
<dependency>
    <groupId>cn.richie696.component</groupId>
    <artifactId>document-chunking-parser-adapter</artifactId>
</dependency>
```

#### 连接 Spring AI 的可选语义桥接

`document-chunking-semantic-spring-ai` 依赖 core 与 `spring-ai-model`。它在构造时接收调用方提供的
`ChatModel`，不替业务管理模型名称、端点或 `apiKey`。

```xml
<dependency>
    <groupId>cn.richie696.component</groupId>
    <artifactId>document-chunking-semantic-spring-ai</artifactId>
</dependency>
```

> **依赖选择**：只做规则切片时引入 core；解析文件并希望保留来源段落时再引入 parser-adapter；确实需要模型协同判断主题边界时再引入
> semantic-spring-ai。三个模块可以同时使用，但不是必须全部引入。

### 2) 配置

最小配置只需打开组件并指定默认策略。未配置的 Java 默认值为 `RECURSIVE`、`maxCharacters=1600`、`overlapCharacters=160`、
`minChunkCharacters=80`、`maxChunksPerDocument=10000`。

```yaml
platform:
  component:
    document-chunking:
      enabled: true
```

生产推荐配置显式固定规则快照、资源上限和流式尾部容量。配置只描述切片行为和资源边界，不包含模型供应商、模型名称、端点或凭证。

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
        max-pending-characters: 8192
      recursive:
        separators:
          - "\n\n"
          - "\n"
          - "。"
          - "！"
          - "？"
          - ". "
          - " "
```

`max-pending-characters` 是流式会话的尾部容量；运行时会把它提升到至少 `rule.maxCharacters()`，因此不会因配置低于单块上限而无法创建会话。

### 3) 写代码

#### 批式 `ChunkingService.chunk(...)`

`ChunkingService` 是纯文本批式入口，方法接收 `String content` 与 `ChunkingRule`，返回 `ChunkingResult`。`content`
是唯一业务文本输入，`rule` 是唯一行为输入。返回结果包含有序 `Chunk` 列表和 `ChunkingDiagnostics`
。同一文本与同一规则快照重复调用，输出文本、顺序和字符区间保持一致。

```java
import cn.richie696.component.chunking.ChunkingService;
import cn.richie696.component.chunking.model.Chunk;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;

ChunkingService service = new DefaultChunkingService();
ChunkingRule rule = ChunkingRule.recursiveDefaults(1_600, 160);
ChunkingResult result = service.chunk(
        "退款规则\n\n自签收之日起七日内，商品保持完好可以申请退货。",
        rule);

for (Chunk chunk : result.chunks()) {
    System.out.printf("%d [%d,%d): %s%n",
            chunk.ordinal(), chunk.charStart(), chunk.charEnd(), chunk.text());
}
```

`DefaultChunkingService` 是统一入口与策略工厂的宿主，不再把九种算法写在一个实现类中。它会去掉切片首尾空白，维护从零开始连续的
`ordinal`，并按 `ChunkingRule.strategy` 选择对应策略。可通过构造函数注入 `TokenCounter`、最小切片长度和单文档最大切片数；只有提供
`SemanticBoundaryAdvisor` 时才会注册 `SEMANTIC` 策略。

```java
import cn.richie696.component.chunking.DefaultChunkingService;
import cn.richie696.component.chunking.model.ChunkingResult;

DefaultChunkingService service = new DefaultChunkingService();
ChunkingResult result = service.chunk("一段已经准备好的业务文本");
```

#### `StreamingChunker`

`StreamingChunker` 是每份文档独占的增量会话，接收上游分段并尽早返回已经确认边界的 `Chunk`。`accept(String section)`
不会把全文拼成一个结果，而是只保留尚未形成完整块的尾部和 overlap。`finish()` 刷出文档尾部且幂等，`abort()` 丢弃未发出的尾部。会话结束后再次
`accept` 会抛出 `IllegalStateException`。

```java
import cn.richie696.component.chunking.StreamingChunker;
import cn.richie696.component.chunking.model.ChunkingRule;

StreamingChunker chunker = new StreamingChunker(service, rule, 8_192);
for (String section : List.of("第一段内容", "第二段内容", "第三段内容")) {
    List<Chunk> ready = chunker.accept(section);
    ready.forEach(this::indexWhenReady);
}
chunker.finish().forEach(this::indexWhenReady);
```

`StreamingChunkerFactory` 由自动配置创建并保存共享的 `ChunkingService` 与尾部上限。每次调用 `create(rule)`
都返回新的文档会话，并把工厂容量与规则上限取较大值。不要在多个文档之间复用同一个 `StreamingChunker`，否则来源区间和
`ordinal` 会混在一起。

```java
import cn.richie696.component.chunking.StreamingChunkerFactory;

StreamingChunkerFactory factory = new StreamingChunkerFactory(service, 8_192);
StreamingChunker documentSession = factory.create(rule);
```

#### `ParserChunkingAdapter` 的流式入口

`ParserChunkingAdapter` 负责 parser 公开模型到切片模型的转换，构造时接收 `ChunkingService`，可选的第二个参数是
`maxPendingCharacters`。`chunk(ReadResult, ChunkingRule)` 对每个 parser section 独立批式切片。
`adapt(Flow.Publisher<ReadEvent>, ChunkingRule)` 只向下游发布 `ChunkedSection`。`adaptEvents(...)` 发布完整的
`ChunkingEvent`，保留图片、完成和失败语义。

```java
import cn.richie696.component.chunking.parser.ParserChunkingAdapter;
import cn.richie696.component.parser.model.ReadEvent;

ParserChunkingAdapter adapter = new ParserChunkingAdapter(service, 8_192);
Flow.Publisher<ChunkingEvent> events = adapter.adaptEvents(parserPublisher, rule);
events.subscribe(new Flow.Subscriber<>() {
    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(ChunkingEvent event) {
        handleChunkingEvent(event);
        subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
        recordDocumentFailure(throwable);
    }

    @Override
    public void onComplete() {
        markDocumentComplete();
    }
});
```

`adaptEvents` 为每个输入文档创建独占的 `StreamingChunker`，并按下游 demand 向 parser 请求事件。它不会把上游 `Subscription`
直接泄露给下游；`Section` 事件在切片确认后发布，`Finished` 会 flush 尾部，`Failed` 会终止会话并 abort 尚未发出的文本。

#### `SemanticChunkingService` 与 Spring AI

`SemanticChunkingService` 是批式语义切片协调器，不实现 `ChunkingService`，也不能用于 `StreamingChunker`。它接收一个确定性
fallback `ChunkingService` 和一个 `SemanticBoundaryAdvisor`。advisor 返回候选字符边界；服务会清洗、排序、去重边界，并对每个语义段调用递归
fallback 处理长度、overlap 与字符位置。

```java
import cn.richie696.component.chunking.SemanticChunkingService;
import cn.richie696.component.chunking.semantic.SpringAiSemanticBoundaryAdvisor;

SemanticBoundaryAdvisor advisor = new SpringAiSemanticBoundaryAdvisor(chatModel);
SemanticChunkingService semantic = new SemanticChunkingService(service, advisor);
ChunkingRule semanticRule = ChunkingRule.semanticDefaults(1_600, 160);
ChunkingResult result = semantic.chunk(longDocumentText, semanticRule);
```

`SpringAiSemanticBoundaryAdvisor` 属于可选 `semantic-spring-ai` 模块，构造时注入 `ChatModel`。它要求模型输出符合 CSV
契约的边界建议；格式错误、调用失败或模型返回不可用内容会抛异常给上层。默认 `FAIL_FAST`；只有业务明确选择可用性优先时，才允许降级，并在诊断中留下信号：

```java
ChunkingResult result = semantic.chunk(longDocumentText, semanticRule,
        new SemanticChunkingOptions(SemanticFailureMode.FALLBACK_TO_DETERMINISTIC));
```

### Ops 入口参考

|                   入口                   |                          输入 / 输出                           |         典型用途         |      生命周期      |
|:----------------------------------------:|:--------------------------------------------------------------:|:------------------------:|:------------------:|
|       `ChunkingService.chunk(...)`       |          `String` + `ChunkingRule` → `ChunkingResult`          |     普通文本批式切片     | 无状态、可重复调用 |
|    `ParserChunkingAdapter.chunk(...)`    |     `ReadResult` + `ChunkingRule` → `List<ChunkedSection>`     |   Parser 结果批式适配    |  每 section 独立   |
|    `ParserChunkingAdapter.adapt(...)`    | `Flow.Publisher<ReadEvent>` → `Flow.Publisher<ChunkedSection>` |    只消费切片 section    | 受下游 demand 控制 |
| `ParserChunkingAdapter.adaptEvents(...)` | `Flow.Publisher<ReadEvent>` → `Flow.Publisher<ChunkingEvent>`  | 需要图片、完成、失败事件 |   每文档独占会话   |
|      `StreamingChunker.accept(...)`      |               上游文本段 → 已确定 `List<Chunk>`                |      增量处理解析段      |     文档内连续     |
|       `StreamingChunker.finish()`        |                    无 → 尾部 `List<Chunk>`                     |     完成时强制 flush     |        幂等        |
|        `StreamingChunker.abort()`        |                          无 → `void`                           |    失败时清理当前文档    |   结束且不可继续   |

> **流式原则**：业务收到 `Chunk` 后可以立即做有限批量、Embedding 或写入，但下游必须使用容量明确的队列和并发策略，不要在 core
> 内隐藏无界异步。

---

## 🔧 核心能力

### 公共接口与确定性保证

`ChunkingService` 把切片算法抽象成稳定接口，核心实现只关心文本和规则。`chunk(String content)` 使用实现持有的默认规则，
`chunk(String content, ChunkingRule rule)` 使用调用方显式传入的规则快照。返回的 `ChunkingResult`
同时携带结果与诊断，适合在入库任务中保存规则版本和处理状态。core 不调用 Tokenizer、LLM、文件解析器或向量库。

```java
ChunkingResult result = service.chunk(content, rule);
List<Chunk> chunks = result.chunks();
ChunkingDiagnostics diagnostics = result.diagnostics();
```

> **核心不变量**：同一 `String + ChunkingRule` 多次执行，结果文本、顺序、`charStart`、`charEnd` 完全一致；`Chunk`
> 的区间始终相对于本次传入的 `content`。

九种策略由 `ChunkingRule.Strategy` 表示：`FIXED`、`RECURSIVE`、`TOKEN`、`PARAGRAPH`、`SENTENCE`、`MARKDOWN`、`HTML`、`PAGE`、
`SEMANTIC`。每种策略都有独立实现类，并通过 `ChunkingStrategyFactory` 按规则类型选择；前八种始终注册，`SEMANTIC` 仅在应用提供
`SemanticBoundaryAdvisor` 时注册。

### 固定字符数切片（FIXED）

`FIXED` 以 `maxCharacters` 作为硬上限，按字符区间切分，不寻找自然边界。它的结果最容易预测，适合无结构原始文本、测试数据和最终兜底。
`overlapCharacters` 仍可用于相邻块之间保留上下文，但不会改变固定边界的选择。该策略不会因为找不到分隔符而标记自然边界截断。

```java
ChunkingRule rule = ChunkingRule.fixed("raw-text", "v1", 800, 80);
ChunkingResult result = service.chunk(rawText, rule);
```

固定字符数适合把“不知道结构的输入”先安全拆开，不适合直接表达合同条款、Markdown 章节或 FAQ 问答关系。生产规则可以先以
`FIXED` 兜底，再由上游结构判断选择更合适的策略。

### 递归分隔符切片（RECURSIVE）

`RECURSIVE` 按 `ChunkingRule.separators()` 从强结构到弱结构依次寻找边界。默认顺序通常是空行、换行、中文句末、英文句末和空格。当前候选窗口找不到分隔符时会回到硬字符上限，并在
`ChunkingDiagnostics.hardTruncated` 中体现。超长自然段会继续按更弱分隔符递归降级。

```java
ChunkingRule rule = ChunkingRule.recursive(
        "knowledge-default", "v3", 1_600, 160,
        List.of("\n\n", "\n", "。", "！", "？", ". ", " "));
ChunkingResult result = service.chunk(ocrText, rule);
```

这是普通 PDF、DOCX、TXT 和 OCR 文本的默认首选。它不理解真正的语义，也不会清洗 OCR 噪声；调用方应在进入切片前完成文本规范化，并在规则中保存分隔符快照。

### 固定 Token 切片（TOKEN）

`TOKEN` 将 `maxCharacters` 字段解释为最大 token 数，而不是字符数。服务通过注入的 `TokenCounter` 对候选子串计数，并用二分搜索寻找不超过
token 上限的最大结束位置。core 提供 `DefaultChunkingService.approximateTokenCounter()` 作为近似估算，生产系统应注入与
Embedding 或生成模型一致的 tokenizer 适配实现。该策略不会使用自然分隔符选择边界。

```java
TokenCounter counter = text -> productionTokenizer.count(text);
DefaultChunkingService tokenService = new DefaultChunkingService(
        ChunkingRule.token("model-window", "v1", 512, 64),
        counter,
        80,
        10_000);
ChunkingResult result = tokenService.chunk(multilingualText,
        ChunkingRule.token("model-window", "v1", 512, 64));
```

`TokenCounter` 不由 core 内置具体模型实现，因此模型升级时必须同时审查规则版本、tokenizer 版本与重切片结果。不要把字符数和
token 数混写在同一个规则版本里。

### 段落切片（PARAGRAPH）

`PARAGRAPH` 优先使用 `\n\n` 与 `\n` 作为边界，适合排版规范、自然段质量较高的公告、制度和说明书。短段仍可能因为 overlap
或最小长度合并逻辑形成不同大小的结果。超长段落会继续按硬上限切分，并可能记录 `hardTruncated`。

```java
ChunkingRule rule = ChunkingRule.paragraph("policy", "v2", 1_200, 120);
ChunkingResult result = service.chunk(policyText, rule);
```

调用方应先保证段落边界可信。对于 OCR 产生的大量错误换行，直接使用 `PARAGRAPH` 容易造成碎片，应改用 `RECURSIVE` 或先做版面清洗。

### 句子滑动窗口（SENTENCE）

`SENTENCE` 使用中文 `。`、`！`、`？` 和英文 `. `、`! `、`? ` 作为候选边界。它保留相邻窗口的字符 overlap，适合
FAQ、客服记录、会议纪要和自然语言短段。超长单句无法在句末找到边界时会回退到硬长度上限，并需要通过诊断识别。

```java
ChunkingRule rule = ChunkingRule.sentence("faq", "v1", 1_000, 150);
ChunkingResult result = service.chunk(faqText, rule);
```

多语言断句、缩写和代码中的句号不由该策略做语言学判断。需要更精确断句时，应在业务层先提供可靠分段，再调用本组件完成长度控制。

### Markdown 标题层级切片（MARKDOWN）

`MARKDOWN` 优先识别 `#`、`##`、`###` 标题行、代码围栏、空行和换行。它保留输入中的章节边界，不生成额外的标题路径字段。标题下内容过长时仍会使用硬上限；标题路径、文档版本和权限元数据由知识库编排层维护。

```java
ChunkingRule rule = ChunkingRule.markdown("handbook", "v4", 1_600, 160);
ChunkingResult result = service.chunk(markdownText, rule);
```

Markdown 输入应保持标题标记完整，不要在切片前把标题与正文压成一行。对于标题块特别长的技术手册，可先按 Markdown
得到结构块，再对超长块使用 `RECURSIVE`。

### HTML 结构边界切片（HTML）

`HTML` 以 `</h1>`、`</h2>`、`</h3>`、`</p>`、`</li>`、`</tr>`、`<br>` 和 `<br/>` 作为候选边界。它只利用输入字符串中的标记，不负责
HTML 解析、DOM 清洗、脚本删除或安全过滤。适合上游已经完成清洗、仍希望利用结构标记的正文块。

```java
ChunkingRule rule = ChunkingRule.html("wiki-page", "v2", 1_600, 160);
ChunkingResult result = service.chunk(cleanHtml, rule);
```

网页正文通常应先移除导航、脚本、样式和重复模板，再将可信正文交给 `HTML` 或 `RECURSIVE`。不要把不可信 URL 或未经清洗的整页
HTML 直接当作知识库正文。

### 页 / 幻灯片协同切片（PAGE）

`PAGE` 使用分页符 `\f` 和空行作为候选边界，但它不知道真实页码，也不会读取 PDF 或 PPT 文件。推荐调用方按 parser 的页或幻灯片
section 分别传入文本，并在外部保存页码映射。页面内仍可使用 `RECURSIVE` 处理超长内容。

```java
for (ParsedSection page : parsed.pages()) {
    ChunkingResult result = service.chunk(page.text(),
            ChunkingRule.page("contract", "v5", 1_600, 160));
    saveWithPageMetadata(page, result);
}
```

合同、报告和 PPT 不应机械地把固定五页拼成一个 Chunk。页面、幻灯片和章节是引用定位的重要来源，应由编排层在 `Chunk` 生成前后建立映射。

### 语义边界协同切片（SEMANTIC）

`SEMANTIC` 由 `SemanticChunkingService` 执行，要求外部 `SemanticBoundaryAdvisor` 提供候选字符边界。服务会过滤越界、重复和无序边界，再把每个语义段交给确定性
`RECURSIVE` fallback。这样模型只负责建议主题转折，长度上限、overlap、字符区间和结果 ordinal 仍由确定性代码控制。

```java
ChunkingRule rule = ChunkingRule.semantic("contract-high-value", "v1", 1_600, 160,
        List.of("\n\n", "\n", "。", "！", "？"));
SemanticChunkingService semanticService = new SemanticChunkingService(service, advisor);
ChunkingResult result = semanticService.chunk(contractText, rule);
```

语义切片仅支持批式调用，不能接入 `StreamingChunker`。模型调用失败、CSV 输出不合法或 advisor
返回异常时，由上层决定重试或回退到确定性策略，不能把错误吞掉后当成成功切片。

### 四个公共模型的不变量

#### `Chunk`

`Chunk` 是单个切片的不可变记录，包含 `ordinal`、`text`、`charStart` 和 `charEnd`。`text` 必须非空，且对应输入字符串的合法子区间。
`charStart` 包含、`charEnd` 排除，区间表示为 `[charStart, charEnd)`。`ordinal` 从零开始连续递增，适合与
`documentId + documentVersion + chunkOrdinal` 组合生成稳定业务 ID。

```java
Chunk chunk = result.chunks().getFirst();
String sourceText = content.substring(chunk.charStart(), chunk.charEnd());
assert sourceText.equals(chunk.text());
```

#### `ChunkingRule`

`ChunkingRule` 是不可变、带策略类型的行为快照，至少包含 `ruleId`、`version`、`strategy`、`maxCharacters`、`overlapCharacters`
和策略使用的 `separators`。批式重建和规则审计应持久化 `ruleId`、`ruleVersion`、策略类型及实际参数。它不应携带模型 API Key、租户
ACL、文档 ID 或向量库配置。

```java
ChunkingRule rule = ChunkingRule.recursiveDefaults(1_600, 160);
String ruleId = rule.ruleId();
String ruleVersion = rule.version();
```

#### `ChunkingResult`

`ChunkingResult` 包含 `List<Chunk> chunks` 与 `ChunkingDiagnostics diagnostics`。空白或 null 输入返回空列表和诊断，而不是制造空
Chunk。结果列表是不可变快照，调用方可以安全地在任务边界传递，但仍应在业务层补充来源、权限和版本元数据。

```java
ChunkingResult result = service.chunk(content, rule);
List<Chunk> chunks = result.chunks();
ChunkingDiagnostics diagnostics = result.diagnostics();
```

#### `ChunkingDiagnostics`

`ChunkingDiagnostics` 暴露输入/输出字符数、请求/实际策略及 `ChunkingSignal` 集。`hardTruncated=true` 表示候选窗口没有找到策略边界或
token 边界，只能使用硬上限，不能理解为业务失败。`SEMANTIC_FALLBACK` 表示调用方已显式允许降级，`INVALID_SEMANTIC_BOUNDARY`
表示模型给出了非空但全部非法的边界。切片超过 `maxChunksPerDocument` 时会抛异常，编排层应记录文档 ID 与规则版本。

```java
if (diagnostics.hardTruncated()) {
    metrics.counter("chunking.hard_truncated").increment();
}
```

### 流式切片 `StreamingChunker`

`StreamingChunker` 将一个文档表示为独占会话，输入 section 会按顺序追加，并在缓冲区达到规则上限后调用确定性切片逻辑。
`accept` 只返回新确认的 Chunk，内部通过连续字符偏移将局部结果映射回整份文档。相邻 Chunk 的交集按规则保留
overlap，不会把文档尾部泄露到下一份文档。

`maxPendingCharacters` 必须至少容纳一个 `rule.maxCharacters()`。它限制尚未形成完整块的尾部；上游单个 section
也必须设定明确上限。生产内存上界可按下式估算：

```text
单文档切片驻留字符 ≤ maxCharacters + overlapCharacters + 上游单段最大允许长度
```

`finish()` 只能用于当前文档完成时的 flush，调用多次只返回空列表。`abort()` 用于 parser `Failed`、取消或业务拒绝，丢弃未发出的尾部；之后不能继续
`accept`。完成或失败后必须为下一份文档创建新的会话。

```java
StreamingChunker session = factory.create(rule);
try {
    session.accept(sectionOne).forEach(this::emit);
    session.accept(sectionTwo).forEach(this::emit);
    session.finish().forEach(this::emit);
} catch (RuntimeException failure) {
    session.abort();
    throw failure;
}
```

### SPI 扩展点

`TokenCounter` 是 core 定义的 token 计数 SPI，只有 `TOKEN` 策略需要它。实现由调用方注入，可以绑定目标 Embedding 模型或生成模型的
tokenizer。core 提供近似计数器用于默认行为，但不承诺与任何供应商 tokenizer 完全一致。更换 tokenizer 必须建立新规则版本并回归黄金语料。

```java
@FunctionalInterface
public interface TokenCounter {
    int count(String text);
}
```

`SemanticBoundaryAdvisor` 是 core 定义的语义边界建议 SPI，接收完整文本并返回候选字符位置列表。core
不内置模型、网络客户端或供应商实现，调用方负责注入 advisor 的生命周期、超时、重试和凭证。advisor 结果只影响语义段起点，最终长度约束仍由
fallback 服务负责。

```java
@FunctionalInterface
public interface SemanticBoundaryAdvisor {
    List<Integer> boundaries(String content);
}
```

> **SPI 边界**：`TokenCounter` 与 `SemanticBoundaryAdvisor` 都由调用方注入；core 不内置实现，也不读取 AI 配置。需要 Spring
> AI 时引入可选桥接模块并显式传入 `ChatModel`。

---

## 🛡 进阶能力

### 与 `document-parser` 适配

`ParserChunkingAdapter` 是可选模块，面向 parser 的公开 `ReadResult`、`ParsedSection`、`ReadEvent` 和 `Flow.Publisher`
契约。它不依赖 parser 内部 SPI，也不负责解析文件。业务仍可以直接把任意文本来源交给 core；只有需要来源
section、页码、文件名和背压转换时才使用 adapter。

#### 批式入口 `chunk(...)`

`chunk(ReadResult result, ChunkingRule rule)` 遍历 `result.sections()`，按 section 独立调用
`ChunkingService.chunk(section.text(), rule)`。返回的每个 `ChunkedSection` 带 `sectionIndex`、原始 `ParsedSection` 和
`ChunkingResult`。其中 `Chunk` 的字符区间相对于该 section 的 `text()`，不是相对于整个文件二进制流。

```java
List<ChunkedSection> sections = adapter.chunk(readResult, rule);
for (ChunkedSection section : sections) {
    persistChunks(section.section(), section.result(), section.sectionIndex());
}
```

#### 流式入口 `adapt(...)`

`adapt(Flow.Publisher<ReadEvent> source, ChunkingRule rule)` 返回只发布 `ChunkedSection` 的 `Flow.Publisher`
。它适合下游不需要图片、完成统计和失败事件，只希望消费已经切出的 section。下游每消费一个事件再请求一个上游事件，避免把全部
parser 结果预先堆在内存中。

```java
adapter.adapt(readEventPublisher, rule).subscribe(new Flow.Subscriber<>() {
    private Flow.Subscription subscription;

    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    public void onNext(ChunkedSection value) {
        writeBoundedBatch(value);
        subscription.request(1);
    }

    public void onError(Throwable throwable) { handleFailure(throwable); }
    public void onComplete() { markComplete(); }
});
```

#### 完整事件入口 `adaptEvents(...)`

`adaptEvents(...)` 返回 `Flow.Publisher<ChunkingEvent>`，事件包括切片 section、图片、完成和失败。每个输入文档拥有一个
`StreamingChunker`，跨 parser section 保留必要 overlap，并通过 `SourceSpan` 把 Chunk 映射回一个或多个来源 section。下游应按
`ChunkingEvent` 的具体类型处理事件。

```java
adapter.adaptEvents(readEventPublisher, rule)
        .subscribe(this::consumeChunkingEvent, this::handleFailure, this::markComplete);
```

#### `Image`、`Finished`、`Failed` 事件语义

`Image` 不进入文本切片；业务可以直接透传、忽略，或交由 OCR/VLM 编排。`Finished` 表示当前文档正常结束，适配器会 flush
`StreamingChunker` 的尾部，再发布完成语义。`Failed` 表示当前文档失败，适配器会终止会话并丢弃未发出的尾部，不能让前一文件残留内容进入下一文件。

```java
if (event instanceof ChunkingEvent.Image image) {
    routeImageToOcr(image);
} else if (event instanceof ChunkingEvent.Section section) {
    saveChunk(section.value());
} else if (event instanceof ChunkingEvent.Finished finished) {
    finalizeDocument(finished);
} else if (event instanceof ChunkingEvent.Failed failed) {
    markFailed(failed);
}
```

#### demand 转换

适配器维护自己的下游 demand，不把 parser 的 `Subscription` 直接暴露给业务
subscriber。下游请求一个事件时，适配器向上游逐个请求；已到达但尚未可发布的文本会留在当前文档会话中，直到边界确认、完成 flush
或失败 abort。取消订阅时应关闭上游资源，业务不得再使用已取消的会话。

### 与 Spring AI 适配

`semantic-spring-ai` 模块只提供 `SpringAiSemanticBoundaryAdvisor`，用于把 `ChatModel` 的模型响应转换成边界建议。它不改变
core 的 `Chunk`、`ChunkingRule` 或 `ChunkingResult` 契约，也不负责 Embedding、向量入库、文档权限或知识库版本事务。

```java
SpringAiSemanticBoundaryAdvisor advisor = new SpringAiSemanticBoundaryAdvisor(chatModel);
SemanticChunkingService service = new SemanticChunkingService(
        deterministicFallback,
        advisor);
ChunkingResult result = service.chunk(text, semanticRule);
```

模型输出契约是 CSV：桥接层按约定读取字符边界建议，并将可解析的边界交给 core。CSV
结构错误、调用异常、无法转换为合法字符位置或模型拒绝返回时，桥接层抛异常给上层。上层必须显式选择重试、记录失败或改用
`RECURSIVE`，不能把失败伪装为空边界成功。

```java
try {
    ChunkingResult result = semanticService.chunk(text, semanticRule);
    saveSemanticChunks(result);
} catch (RuntimeException failure) {
    log.warn("语义切片失败，回退到递归规则", failure);
    saveDeterministicChunks(fallback.chunk(text, recursiveRule));
}
```

### 与 vector 组件的契约边界

`Chunk` 不是 `VectorRecord`，`document-chunking` 不组装向量记录，也不调用 `VectorService`。`vector-chunk-adapter` 由 vector
组件承担，因为“谁消费谁适配”：vector 负责把 Chunk 转成自己的写入模型，chunking 保持纯文本职责。这样可以避免 core 依赖
vector、形成循环依赖和职责倒置。

```text
ChunkingService -> Chunk / ChunkingResult
知识库编排层 -> 补充 documentId、tenantId、ACL、来源与规则版本
vector-chunk-adapter -> VectorRecord
Embedding -> VectorService.upsert / upsertAll
```

业务应在显式组装步骤中补齐 `documentId`、`documentVersion`、`chunkOrdinal`、`tenantId`、可见范围、部门标签、
`pageStart/pageEnd`、`sectionPath`、`ruleId` 和 `ruleVersion`。切片组件不进行权限判定，也不复制公司共享文档到各部门索引。

### 大文档端到端链路

商用 RAG 的通常链路是 `parser → chunking → vector`，中间还包含来源映射、Embedding 和有界批量写入。Parser 负责从
PDF、DOCX、PPT、HTML 等来源产生文本 section；chunking 负责按规则切出稳定 `Chunk`；vector 侧负责把 Chunk 组装为 `VectorRecord`
并入库。不要把完整文件、模型调用或向量写入塞进 `ChunkingService`。

```text
可重开输入流 / 有界临时文件
    -> document-parser Publisher<ReadEvent>
    -> ParserChunkingAdapter Publisher<ChunkingEvent>
    -> 有界批量 Chunk
    -> Embedding（有界并发）
    -> VectorRecord
    -> VectorService.upsertAll
```

大文档必须同时限制 parser 单段、`StreamingChunker` 尾部、Embedding 在途记录、写入批次和队列容量。不要使用无界
`onBackpressureBuffer()`。解析失败应终止当前文档；已经成功写入的记录由上层文档版本切换策略处理，不要求切片组件回滚。

语义切片是完整上下文例外，不能把全文交给 `StreamingChunker`。推荐先按页、标题或其他可靠结构得到有上限的候选单元，再对候选单元批式调用
`SemanticChunkingService`，避免单次模型输入无限增长。

### 自定义规则边界

业务确实需要按“第 N 条”“附件”“问：/答：”等领域边界切片时，可以在编排层实现受控的自定义边界规则，并将可靠分隔符交给
`RECURSIVE` 或 `PARAGRAPH` 组合使用。自定义规则不是普通业务调用者需要打开的第十种默认策略，必须带规则版本、输入上限和匹配次数限制。

如果实现正则边界，优先使用 RE2J 这类线性时间引擎；不支持回溯引用、环视等不兼容特性。不要对不可信模式直接使用
`java.util.regex` 并依赖线程中断或超时来阻止灾难性回溯。规则编译失败、无匹配或运行失败时，应回退到 `RECURSIVE`
并记录规则版本与拒绝原因。

---

## 📦 配置一览

> 配置前缀为 `platform.component.document-chunking`。所有字段描述确定性切片行为、资源上限或回退边界，不包含模型供应商和凭证。

### 模块开关与公共参数

|           字段            |     类型      |   默认值    |                                            取值范围                                            |                            影响                            |
|:-------------------------:|:-------------:|:-----------:|:----------------------------------------------------------------------------------------------:|:----------------------------------------------------------:|
|         `enabled`         |    boolean    |   `false`   |                                        `true` / `false`                                        |      是否创建 core 的自动配置 Bean；关闭时不启用组件       |
|      `default-rule`       | String / enum | `recursive` | `fixed`、`recursive`、`token`、`paragraph`、`sentence`、`markdown`、`html`、`page`、`semantic` |      `ChunkingService.chunk(content)` 使用的默认策略       |
|     `max-characters`      |      int      |   `1600`    |                                             `> 0`                                              | 普通字符策略的单 Chunk 硬上限；`TOKEN` 时解释为 token 上限 |
|   `overlap-characters`    |      int      |    `160`    |                                 `0 ≤ overlap < maxCharacters`                                  |      相邻 Chunk 的上下文重叠字符数；也影响流式消费量       |
|  `min-chunk-characters`   |      int      |    `80`     |                                             `≥ 0`                                              | 尾部过短时尝试与前一 Chunk 合并；`0` 表示不做最小长度合并  |
| `max-chunks-per-document` |      int      |   `10000`   |                                             `> 0`                                              |   单文档输出数量硬上限；超过时抛 `IllegalStateException`   |
|         `ruleId`          |    String     |  业务提供   |                                              非空                                              |               规则身份；建议随文档版本持久化               |
|         `version`         |    String     |  业务提供   |                                              非空                                              |            规则快照版本；变更后不得覆盖历史含义            |

`maxCharacters` 是字符策略的字符数上限，而 `TOKEN` 策略中的同名字段是 token 上限；建议在业务配置命名和监控标签中明确区分。
`min-chunk-characters` 对 token 策略的合并上限使用实现定义的 token 路径，不应把它当作模型 token 的硬保证。

### 流式配置

|                字段                |   类型   |   默认值   |           取值范围            |                               影响                                |
|:----------------------------------:|:--------:|:----------:|:-----------------------------:|:-----------------------------------------------------------------:|
| `streaming.max-pending-characters` |   int    |   `8192`   |             `> 0`             | 每文档尚未确认尾部的容量；实际会提升到至少 `rule.maxCharacters()` |
|       `rule.maxCharacters()`       |   int    |   规则值   |             `> 0`             |             单次流式切片至少需要容纳的完整候选块长度              |
|     `rule.overlapCharacters()`     |   int    |   规则值   | `0 ≤ overlap < maxCharacters` |               消费 pending 时保留给下一块的 overlap               |
|          上游单段最大长度          | 业务约束 |  无内置值  |        必须有明确上限         |           参与端到端驻留内存上界；过大段会放大瞬时内存            |
|              会话数量              | 业务约束 | 每文档一个 |        与并发上限一致         |               不能跨文档复用一个 `StreamingChunker`               |

`StreamingChunkerFactory` 创建会话时使用 `Math.max(maxPendingCharacters, rule.maxCharacters())`。因此把
`streaming.max-pending-characters` 配得比规则上限小不会突破规则安全边界，但也不会降低单块必需容量。推荐同时按公式核算堆内存和上游并发。

### 递归策略配置

|            字段             |     类型     |                   默认值                   |     取值范围     |                    影响                    |
|:---------------------------:|:------------:|:------------------------------------------:|:----------------:|:------------------------------------------:|
|   `recursive.separators`    | List<String> | `['\\n\\n','\\n','。','！','？','. ',' ']` |  非空字符串列表  |       按顺序寻找不超过上限的自然边界       |
| `ChunkingRule.separators()` | List<String> |                 规则快照值                 | 可按业务语言调整 |     规则级覆盖配置，决定结果是否可复现     |
|          空分隔符           |    String    |                   不允许                   |     不可为空     |   空 token 会被忽略，避免边界搜索无进展    |
|         分隔符顺序          |  List 顺序   |                 结构到字符                 |    由业务固定    | 越靠前优先级越高；改变顺序应生成新规则版本 |
|         无匹配边界          |   诊断状态   |      `hardTruncated=false` 或 `true`       |  由实际结果决定  | 无自然边界时回退硬切并记录 `hardTruncated` |

### 配置按功能导航

| 需要调整的内容  |                      配置入口                       |               主要影响               |
|:---------------:|:---------------------------------------------------:|:------------------------------------:|
|  默认切片策略   | `platform.component.document-chunking.default-rule` |         无显式规则的批式调用         |
| 普通 Chunk 长度 |                  `max-characters`                   | 召回上下文大小、Chunk 数量和处理成本 |
|  上下文连续性   |                `overlap-characters`                 |   相邻 Chunk 的重复内容和索引体积    |
|    尾部碎片     |               `min-chunk-characters`                |          短尾合并与单块长度          |
|    文档保护     |              `max-chunks-per-document`              |      防止异常文本产生无限 Chunk      |
|    流式尾部     |         `streaming.max-pending-characters`          |       单文档内存上界与触发时机       |
|    递归边界     |               `recursive.separators`                |        普通文本的自然断点选择        |
|   Token 计数    |                   `TokenCounter`                    |   `TOKEN` 策略与目标模型窗口一致性   |
|    语义边界     |              `SemanticBoundaryAdvisor`              |    `SEMANTIC` 策略的候选主题转折     |

> **配置纪律**：规则字段在入库任务中应形成不可变快照；不要在线修改同一个 `ruleId + version` 后期待历史 Chunk 自动保持可解释。

---

## 🎯 最佳实践

### 场景与策略选择

|     典型场景      |          推荐策略           |                   推荐做法                    |              主要注意事项              |
|:-----------------:|:---------------------------:|:---------------------------------------------:|:--------------------------------------:|
| 纯文本 PDF / OCR  |         `RECURSIVE`         | 先清洗 OCR 噪声，再按空行、换行、句末递归降级 | 错误换行严重时不要直接使用 `PARAGRAPH` |
| Markdown 技术文档 |         `MARKDOWN`          |   保留标题和代码围栏，超长标题块再递归切片    |          标题路径由编排层保存          |
|  PPT / PDF 合同   |    `PAGE` + `RECURSIVE`     |   调用方按页或幻灯片输入，页面内再按长度切    |     不要机械固定五页合成一个 Chunk     |
|     HTML 网页     | 清洗后 `HTML` / `RECURSIVE` |         上游去掉脚本、导航和重复模板          |     core 不解析 DOM、不做安全清洗      |
|   FAQ / 问答库    |         `SENTENCE`          |    使用句末候选边界并保留 10%–20% overlap     |      超长问答句回退递归或固定切片      |
|   排版规范制度    |         `PARAGRAPH`         |         合并相邻短段，超长段递归降级          |          段落边界质量决定结果          |
|  无结构原始文本   |           `FIXED`           |     作为稳定兜底，必要时保留字符 overlap      |       中文英文 token 比例不一致        |
|   模型窗口敏感    |           `TOKEN`           |        注入目标模型 tokenizer 并版本化        |       更换 tokenizer 必须重切片        |
|  高价值复杂长文   |         `SEMANTIC`          |    先按页/标题形成候选单元，再模型建议边界    |     批式调用，失败回退 `RECURSIVE`     |

九种策略不是九个必须同时启用的开关。生产规则通常是“结构边界策略 + 长度约束 + 兜底策略”：例如 Markdown
先保留标题，超长段落再递归；合同先按页输入，页内再按递归规则处理。

### 长度参数选择

`384~512 tokens` 常见于商用 RAG 的单 Chunk 预算，但字符换算必须结合语言、标点和 tokenizer。中文通常可按约 `1.5×` 字符估算，意味着
384~512 tokens 约为 576~768 个中文字符；英文自然语言常按约 `4×` 字符估算，约为 1536~2048 个英文字符。代码、表格、混合语言和特殊
token 会显著偏离该估算。

|   内容类型   | 粗略 token / 字符关系 | 384~512 tokens 的初始字符预算 |           调整建议            |
|:------------:|:---------------------:|:-----------------------------:|:-----------------------------:|
| 中文自然语言 | 约 1 token / 1.5 字符 |        约 576~768 字符        | 以目标模型 tokenizer 压测校准 |
| 英文自然语言 |  约 1 token / 4 字符  |       约 1536~2048 字符       |      注意单词和标点分布       |
|   中英混合   |    无稳定固定比例     |          先取中间值           |      分语言黄金语料验证       |
| 代码 / 表格  |  token 密度可能更高   |         不宜直接套用          |  按结构切分，设置更保守上限   |

重叠字符一般从目标 Chunk 长度的 `10%~20%` 开始：例如 `maxCharacters=1600` 可从 `overlapCharacters=160~320`
压测。重叠过大增加向量存储、Embedding 成本和重复召回；过小则可能切断跨块上下文。`overlapCharacters` 必须严格小于
`maxCharacters`。

### 流式内存预算

`StreamingChunker` 的尾部不是全文缓存，但端到端内存仍取决于上游单段大小、并发文档数和下游队列。单文档切片阶段可用下面的预算公式：

```text
单文档驻留字符 ≤ maxCharacters + overlapCharacters + 上游单段最大允许长度
```

多个并发文档时，应将右侧结果乘以并发会话数，再加上 parser 解析器自身、图片缓冲、Embedding 在途记录和 vector 批次。不要把
`max-pending-characters` 当作整个 pipeline 的总内存上限；它只约束一个切片会话的 pending 文本。

### 规则版本化

上游应保存 `ruleId`、`ruleVersion`、策略类型、`maxCharacters`、`overlapCharacters`、分隔符列表和 tokenizer
版本（如适用）。同一版本规则不得被在线覆盖，因为 Chunk 的字符区间、向量 ID 和引用定位都依赖规则快照。需要重切片时建立新规则版本，生成新
Chunk 和新向量记录，再由知识库编排层执行双写、切换和旧版本归档。

```java
record ChunkingRuleSnapshot(
        String ruleId,
        String ruleVersion,
        ChunkingRule.Strategy strategy,
        int maxCharacters,
        int overlapCharacters,
        List<String> separators,
        String tokenizerVersion) {
}
```

稳定业务标识建议由 `documentId + documentVersion + chunkOrdinal` 或其哈希生成。重试同一文档版本时使用相同标识，避免切片成功但向量写入重试产生重复记录。

### 诊断与可观测指标

`ChunkingDiagnostics` 应与文档 ID、规则版本和处理耗时一起写入任务日志或指标。建议至少统计每文档切片数、平均长度、超长率、
`hardTruncated` 次数、规则回退率、失败率和流式会话 abort 次数。语义桥接还应单独统计 CSV 解析失败、模型超时和 advisor
异常，不要把模型故障混入普通切片成功数。

|         指标         |           计算方式            |            价值            |     告警方向     |
|:--------------------:|:-----------------------------:|:--------------------------:|:----------------:|
|     每文档切片数     |   `result.chunks().size()`    |   发现异常碎片或规则过细   |     突然升高     |
|   平均 Chunk 长度    |     文本字符总数 / 切片数     |        校准长度预算        |   长期偏离目标   |
|        超长率        | 超过业务软阈值的 Chunk / 总数 |      识别结构边界失效      |     持续升高     |
| `hardTruncated` 次数 |         诊断标记累计          |      发现自然边界不足      | OCR 或新语料激增 |
|        回退率        |    fallback 次数 / 总文档     |   评估语义或结构策略质量   |  语义失败率升高  |
|      abort 次数      |      流式失败/取消会话数      | 识别 parser 或下游背压问题 |  与失败日志关联  |
|       处理耗时       |      文档切片开始到结束       |       成本与吞吐评估       |   P95/P99 超标   |

---

## 🛣 切片质量增强

商用 RAG 对切片质量的要求不止“能切出来”，还要默认可预期、失败可观测、算法语义与名称一致。本节给出三个相互独立又互相支撑的增强方向：语义失败的默认行为、诊断对象的信号化扩展、以及把
`RECURSIVE` 改造为真正的递归切片算法。

### Spring AI 语义切片异常降级（默认 `FAIL_FAST`）

**核心原则**：默认策略应为 `FAIL_FAST`， **不能**
静默降级。语义切片失败可能是限流、模型不可用、响应损坏或鉴权失效；自动退化为普通递归切片会让知识库质量悄悄下降且难以排查。业务方若更重视可用性，可以显式选择回退，并在诊断中留下
`SEMANTIC_FALLBACK` 信号。

```java
public enum SemanticFailureMode {
    FAIL_FAST,
    FALLBACK_TO_DETERMINISTIC
}

public record SemanticChunkingOptions(
        SemanticFailureMode failureMode
) {
    public static SemanticChunkingOptions defaults() {
        return new SemanticChunkingOptions(SemanticFailureMode.FAIL_FAST);
    }
}
```

> **职责划分**：
> - `SpringAiSemanticBoundaryAdvisor`：模型响应为空、`result/output` 为空或格式不合法时，抛出明确的
    `SemanticBoundaryException`，不自行吞掉。
> - `SemanticChunkingService`：根据 `SemanticFailureMode` 决定继续抛出，或回退到 `RECURSIVE`。
> - 重试、限流、熔断、死信：放在知识库编排层或 AI 组件， **不**放进 chunking 原子组件。
> - 若发生回退，必须在诊断信息中记录 `SEMANTIC_FALLBACK`，但不得记录模型原始回答、Prompt 或敏感文档正文。

**收益**：默认保证质量；业务方若更重视可用性，可以显式选择回退。

### `ChunkingDiagnostics` 扩展为“信号枚举”

**核心原则**：当前只有 `hardTruncated` 一个布尔位，难以支撑商用 RAG 的质量治理。改为“不可变诊断对象 + 枚举信号集合”，既能向前兼容旧
API，又能为统计、告警与人工复核提供足够信息。

```java
public record ChunkingDiagnostics(
        int inputCharacters,
        int outputChunks,
        ChunkingRule.Strategy requestedStrategy,
        ChunkingRule.Strategy appliedStrategy,
        Set<ChunkingSignal> signals
) {
    public boolean hardTruncated() {
        return signals.contains(ChunkingSignal.HARD_TRUNCATED);
    }
}

public enum ChunkingSignal {
    HARD_TRUNCATED,          // 无有效边界，只能按长度强切
    SEPARATOR_NOT_FOUND,     // 当前策略范围内未找到期望结构边界
    TOKEN_LIMIT_FORCED,      // 单个 token/字符已超过 token 上限
    SEMANTIC_FALLBACK,       // 语义策略失败，已退回确定性策略
    INVALID_SEMANTIC_BOUNDARY,
    MAX_CHUNKS_REACHED
}
```

> **原则提示**：
> - 不建议把耗时、模型名、重试次数放入 core 诊断对象；这些属于调用链观测，应由 Micrometer、日志或编排层记录。
> - 现有 API（`ChunkingDiagnostics.hardTruncated()` 等）保持兼容，新增 `signals` 字段为只读扩展。

**收益**：

- 可统计知识库中“硬切率”和“语义降级率”。
- 可按文档类型调优切片规则。
- 可对高风险 Chunk 做人工复核或二次处理。

### 严格递归切片（独立 `RecursiveChunker`）

**核心原则**：当前 `RECURSIVE` 是“按分隔符优先级找最近边界”的简化实现；递归切片应改造为 **独立** `RecursiveChunker`
，遵循经典递归字符切分算法，让策略名与算法语义真正一致。

```text
输入文本
  → 按第 1 级分隔符拆分（如 \n\n）
  → 超长片段按第 2 级继续拆分（如 \n）
  → 仍超长则按句子分隔符拆分
  → 再超长按空格/字符拆分
  → 最终按 maxCharacters 强切，并标记 HARD_TRUNCATED
  → 在最终 Chunk 序列上应用 overlap
```

```yaml
recursive:
  separators:
    - "\n\n"
    - "\n"
    - "。"
    - "！"
    - "？"
    - ". "
    - "! "
    - "? "
    - " "
    - ""
```

> 空字符串是最后兜底，表示字符级切分。

> **实现约束**：
> - 所有切分都基于原文 offset， **绝不**拼接伪换行。
> - `Chunk.text()` 必须始终等于原文对应区间。
> - overlap 只能在最终 Chunk 序列生成后处理。
> - 仅最终字符级兜底才标记 `HARD_TRUNCATED`。
> - `PARAGRAPH`、`SENTENCE`、`MARKDOWN` 等策略 **保持当前单策略语义**，不自动变成递归策略。

### 推荐实施顺序

1. 先扩展 `ChunkingDiagnostics` 和信号枚举，让所有后续改动都可观测。
2. 实现严格 `RecursiveChunker`，替换当前 `RECURSIVE` 分支并复用同一信号集合。
3. 引入 `SemanticFailureMode`，把回退结果写入诊断信号。
4. 最后补齐对应测试和文档。

> **设计哲学**：默认可预期、失败可观测、算法语义与名称一致。

### 当前落地状态（截至本版本）

下表为本节三类增强在 **当前发布版本** 下的真实落地情况——记录「设计意图」与「当前代码」的差距，供后续实施参照。

| 增强项                                                                  | 当前代码状态                                                                                                                                                   |
|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SemanticFailureMode` 枚举（`FAIL_FAST` / `FALLBACK_TO_DETERMINISTIC`） | ✅ 已在 `cn.richie696.component.chunking` 包中作为公开枚举骨架存在                                                                                             |
| `SemanticChunkingOptions` record（含 `defaults()` 工厂）                | ✅ 已在 `cn.richie696.component.chunking` 包中作为 record 骨架存在                                                                                             |
| `SemanticBoundaryException`（`RuntimeException`）                       | ✅ 已在 `cn.richie696.component.chunking` 包中作为公开异常类骨架存在                                                                                           |
| `model.ChunkingSignal` 枚举                                             | ⚠️ 已实现 5 个值：`HARD_TRUNCATED` / `SEPARATOR_NOT_FOUND` / `TOKEN_LIMIT_FORCED` / `SEMANTIC_FALLBACK` / `INVALID_SEMANTIC_BOUNDARY`；缺 `MAX_CHUNKS_REACHED` |
| `ChunkingDiagnostics` 扩展为信号集合（新增 `signals` 字段）             | ❌ 未实施；旧 `(hardTruncated, ...)` 两参 record 仍是当前事实                                                                                                  |
| 严格 `RecursiveChunker` 替换简化 `RECURSIVE`                            | ❌ 未实施；当前 `RECURSIVE` 仍是「按分隔符优先级找最近边界」的简化实现                                                                                         |
| `SpringAiSemanticBoundaryAdvisor` 默认 `FAIL_FAST`                      | ❌ 保留旧行为——空文本 / `null` 响应返回 `List.of()`，不完整 `ChatResponse` 抛 NPE，对应 5 个回归测试已全绿                                                     |

> **为什么没一步到位**：本次发布目标为「新增 Javadoc 与 README，不触动主代码」；本节方案已在 4 个类型壳子上沉淀了 API 名字，
> **未并入任何生产链路**。全量接通（含 `ChunkingDiagnostics.signals` 接入、`RecursiveChunker` 算法替换、
> `SemanticChunkingOptions.defaults()` 注入 `FAIL_FAST`）需要一次专门的「切片质量主版本」迭代——执行顺序仍按上文
> *§推荐实施顺序* 第 1→4 步推进。
> **如何打开这些类型**：`import cn.richie696.component.chunking.SemanticFailureMode;`
> 等即可编译并使用其枚举值，但因为它们尚无任何生产消费方，使用前请确认目标模块（编排层或专门的质量治理子模块）的依赖与契约。

---

## ❓ FAQ

### 为什么我的 Chunk 是空的？

`DefaultChunkingService` 对 null 或全空白输入返回空 `chunks`，不会生成空 Chunk。常见原因是 `maxCharacters`
过小导致规则无法容纳有效文本、上游传入的内容本身不可切或清洗阶段把文本全部移除了。先记录输入字符数、规则参数和 parser
section 数量，再确认 `maxCharacters > 0` 且 `overlapCharacters < maxCharacters`。

### 为什么同一文本 + 同一规则产出不同 Chunk？

正常情况下不会出现。core 对同一 `String + ChunkingRule` 保证文本、顺序和字符区间一致；`ordinal`、`charStart`、`charEnd`
应逐次一致。若结果不同，应下钻检查规则是否被在线修改、`TokenCounter` 是否依赖可变 tokenizer、上游是否在调用前改变了文本，或比较时是否混入了不同版本的语义
advisor 输出。

### 流式路径为什么我的 Chunk 出现得晚？

`StreamingChunker` 按 batch 累积到 `maxCharacters` 附近才会触发完整边界检查；短 section 不一定立即形成可确认 Chunk。可以适当调小
`rule.maxCharacters()` 或在文档结束时调用 `finish()` 强制 flush。不要为了提前输出而无上限增大下游并发，也不要把每个 parser
section 直接当成完整 Chunk。

### `Failed` 之后是否还能继续 emit？

不能。`Failed` 会终止当前文档的切片上下文并丢弃未发出的尾部；后续 section 不应继续归属于该文档会话。处理下一份文档时，必须创建新的
`StreamingChunker` 或让新的 `ParserChunkingAdapter` 订阅会话从干净状态开始，避免残留 overlap 和字符偏移污染结果。

### 为什么 core 不提供 vector adapter？

职责单一原则要求 core 只负责 `String -> ChunkingResult`。`VectorRecord` 是 vector 组件的输入模型，adapter 不应反向依赖
vector 或组装 `VectorService` 调用，否则会形成循环依赖并把权限、Embedding、索引和版本事务混进文本算法。vector 侧的
`vector-chunk-adapter` 承担 `Chunk -> VectorRecord` 的显式组装。

### 如何自定义边界规则？

优先在业务编排层清洗并提供可靠分隔符，然后注入 `ChunkingRule` 的 `separators` 给 `RECURSIVE` 或 `PARAGRAPH`。需要模型建议时注入
`SemanticBoundaryAdvisor`；需要模型 tokenizer 一致性时注入 `TokenCounter`。自定义正则必须采用线性时间引擎、限制模式和输入规模，并在无匹配或失败时回退
`RECURSIVE`。SPI 扩展点只负责注入契约，不由 core 自动发现业务实现。

### `SEMANTIC` 为什么有时不能直接传给 `DefaultChunkingService`？

语义策略需要完整上下文和外部边界建议。只有 `DefaultChunkingService` 构造时提供了 `SemanticBoundaryAdvisor`，策略工厂才会注册
`SEMANTIC`；否则会明确报未注册，避免调用方误以为 core 会自动调用 LLM。也可以显式构造
`SemanticChunkingService(fallback, advisor)`，以便自行控制失败回退策略。

### `StreamingChunker` 能否跨多个文档复用？

不能。一个会话维护 `nextOrdinal`、连续字符偏移、pending 尾部和最后发出的结束位置。完成或 abort 后会话结束；跨文档复用会导致
ordinal、offset 和 overlap 串文档。使用 `StreamingChunkerFactory.create(rule)` 为每个文档创建独立实例。

### `PAGE` 会自动识别 PDF 页码吗？

不会。`PAGE` 只识别输入字符串中的分页符和空行边界，不读取 PDF、PPT 或文件元数据。页码、幻灯片编号和来源路径必须由
`document-parser` 或业务编排层持有，并在保存 `Chunk` 或 `VectorRecord` 时补充。

### `max-pending-characters` 设置很小会怎样？

工厂创建会话时会将它提升到至少 `rule.maxCharacters()`
，因此不会小于单块必需容量。它不会消除上游单段、解析器和下游队列的内存开销。生产上应按“单块 + overlap +
上游单段”公式设置，并结合并发文档数压测，而不是只调一个 YAML 字段。

---

## 📚 专题文档

当前组件目录暂无专题文档。公开 API、模块坐标、配置字段、事件语义、边界约束和运维建议均以本 README 为准。

> **职责边界**：该组件接收文本并产出可追溯 `Chunk`；文件解析、OCR、Embedding、权限判定、`VectorRecord`
> 组装、向量写入和文档版本事务由相邻组件或知识库编排层负责。
