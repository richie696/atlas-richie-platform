# Atlas Richie 文档解析组件 (atlas-richie-component-document-parser)

> **一句话价值**: 单一 `DocumentReader` 入口,解析 **PDF / Word / Excel / PPT / ODF / TXT / Markdown**,内置 SSRF 防护、结构化段落,以及面向 RAG 管道的流式事件 API。

---

## 📖 目录

- [📋 概述](#-概述)
- [✨ 核心能力](#-核心能力)
- [🏗️ 架构](#-架构)
  - [模块结构](#模块结构)
  - [格式 → Parser 路由](#格式--parser-路由)
- [🚀 快速开始](#-快速开始)
  - [1. 引入依赖](#1-引入依赖)
  - [2. 编写代码](#2-编写代码)
  - [3. 访问解析段落](#3-访问解析段落)
- [🌊 流式异步 API](#-流式异步-api)
  - [为什么需要流式?](#为什么需要流式)
  - [ParseEvent 类型](#parseevent-类型)
  - [图片处理原则](#图片处理原则)
  - [图片无包装情况](#图片无包装情况)
  - [严格模式(可选)](#严格模式可选)
- [🛡️ URL 三道防线](#-url-三道防线)
  - [防线 1 — SSRF(URL / Host / IP 校验)](#防线-1--ssrfurl--host--ip-校验)
  - [防线 2 — HTTP HEAD 协议层](#防线-2--http-head-协议层)
  - [防线 3 — 内容嗅探](#防线-3--内容嗅探)
- [🖼️ 全图片 PDF 检测](#-全图片-pdf-检测)
  - [启发式规则](#启发式规则)
  - [异常负载](#异常负载)
- [⚙️ 配置参考](#-配置参考)
  - [1. 顶层 (`platform.component.parser`)](#1-顶层-platformcomponentparser)
  - [2. URL 获取策略 (`platform.component.parser.url`)](#2-url-获取策略-platformcomponentparserurl)
  - [3. PDF 处理 (`platform.component.parser.pdf`)](#3-pdf-处理-platformcomponentparserpdf)
  - [4. Excel 处理 (`platform.component.parser.excel`)](#4-excel-处理-platformcomponentparserexcel)
- [⚠️ 异常参考](#-异常参考)
- [🎯 最佳实践](#-最佳实践)
  - [入口选择](#入口选择)
  - [RAG 段落粒度](#rag-段落粒度)
  - [URL 加固](#url-加固)
  - [内存与吞吐](#内存与吞吐)
- [❓ FAQ](#-faq)
  - [为什么组件不做 OCR/VLM?](#为什么组件不做-ocrvlm)
  - [PDF 是扫描件怎么办?](#pdf-是扫描件怎么办)
  - [TXT/Markdown 也走 Tika,能绕开吗?](#txtmarkdown-也走-tika能绕开吗)
  - [能不下载就解析远程 Excel 吗?](#能不下载就解析远程-excel-吗)
  - [同一文件并发解析 100 次?](#同一文件并发解析-100-次)
- [📅 实施进度](#-实施进度)
- [📕 相关文档](#-相关文档)

---

## 📋 概述

`atlas-richie-component-document-parser` 是 Atlas Richie 平台的统一文档解析组件。业务侧只接触一个 `DocumentReader` 门面,**绝不**直接依赖 Apache Tika、Fesod、PDFBox、POI。

**底层引擎**:

| 引擎                                                 | 职责                                                   |
|----------------------------------------------------|------------------------------------------------------|
| Apache Tika 3.3.1(`tika-parsers-standard-package`) | PDF / Word / PPT / ODF / TXT / Markdown / HTML / XML |
| Apache Fesod `2.0.2-incubating`                    | Excel (`.xlsx` / `.xls` / `.ods`)、流式、单 sheet O(1) 内存 |
| Apache PDFBox 3.0.7                                | PDF 文本抽取(经 Tika)                                     |
| Apache POI 5.5.1                                   | Word / PPT / xlsx 图片                                 |
| Jsoup 1.18.1                                       | Tika XHTML 输出中的 `<img>` 扫描                           |

**设计契约**: 所有实现细节位于 `internal/`。替换 Tika 或 Fesod 只触碰 `internal/`,对外 API 与 Spring 配置零变化。

---

## ✨ 核心能力

- **单一门面**: `DocumentReader.parse(...)` 4 重载入口 — `File` / `InputStream` / `URL` / 字符串自动识别
- **格式自动识别**: 基于 Tika magic bytes 内容嗅探,失败时回退到扩展名
- **结构化段落**: 每个 `DocumentSegment` 含 `pageNumber`、`sectionPath`、metadata Map,直接服务 RAG 切片检索
- **SSRF 防御**: `UrlFetcher` 在拉取 URL 前执行三道防线(IP 黑名单 → HEAD 校验 → 内容嗅探)
- **DNS-rebinding 防护**: 解析 host → 校验 IP → 建连,无 TOCTOU 窗口
- **跨主机重定向拦截**: 30x 仅当 `Location` host 与原 host 相同时才跟随,最多 5 跳
- **全图片 PDF 检测**: 显式失败(`ImageOnlyPdfException`)而非静默返回空内容
- **流式 Excel**: Fesod 单 sheet O(1) 内存,大 `.xlsx` 文件内存有界
- **流式事件 API**: `parseStream(...)` 边解析边 emit `ParseEvent`,适合 "解析 → embedding → 入库" 流水线
- **图片提取 (Phase 8)**: `ParseEvent.ImageStreaming` 返回嵌入图片原始字节,供业务侧 OCR / 存储 / VLM

---

## 🏗️ 架构

### 模块结构

```
com.richie.component.parser/
├── DocumentParser                # SPI: 流式接口
├── DocumentReader                # 4 重载门面 — 开发者唯一入口
├── ParsedDocument                # record (title / author / segments / metadata)
├── DocumentSegment               # record (text / pageNumber / sectionPath / meta)
├── ImageSegment                  # record (data / format / sectionPath / meta)  [Phase 8]
├── ParserSource                  # sealed (File / Stream / Url)
│   ├── FileSource
│   ├── StreamSource
│   └── UrlSource
├── ParserContext / UrlFetchPolicy # 配置 record
├── ParseListener / ParseEvent    # 事件驱动订阅模型
│   ├── Streaming                 # 文本段到达
│   ├── ImageStreaming            # 图片字节就绪
│   ├── Finished                  # 汇总 + 计数
│   └── Failed                    # 异常(不抛出)
├── config/
│   ├── ParserProperties          # @ConfigurationProperties
│   └── ParserAutoConfiguration   # 5 个 @Bean
├── exception/
│   ├── DocumentParseException    # 解析异常父类
│   ├── FormatNotSupportedException
│   └── ImageOnlyPdfException
└── internal/                     # 实现细节(不导出)
    ├── Format                    # PDF / DOCX / XLSX / ... / UNKNOWN
    ├── FormatDetector            # Tika magic bytes + 扩展名 fallback
    ├── TextFastPathParser        # TXT / Markdown bypass-Tika 通道
    ├── TikaDocumentParser        # PDF / Word / PPT / ODF / RTF / HTML / XML
    ├── FesodDocumentParser       # Excel via Apache Fesod
    ├── UrlFetcher                # JDK HttpClient + SSRF + HEAD + DNS-rebinding
    └── ParserRouter              # Format → DocumentParser 分发
```

### 格式 → Parser 路由

| 扩展名                            | Format 枚于                  | 路由到                                    | 原因                                                                                                                                               |
|--------------------------------|----------------------------|----------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `.pdf`                         | `PDF`                      | `TikaDocumentParser`                   | 二进制 PDF，Tika + PDFBox                                                                                                                            |
| `.docx`                        | `DOCX`                     | `TikaDocumentParser`                   | Word 2007+，Tika + POI                                                                                                                            |
| `.doc`                         | `DOC`                      | `TikaDocumentParser`                   | 老版 Word，Tika + POI                                                                                                                               |
| `.pptx`                        | `PPTX`                     | `TikaDocumentParser`                   | PPT 2007+，Tika + POI                                                                                                                             |
| `.ppt`                         | `PPT`                      | `TikaDocumentParser`                   | 老版 PPT，Tika + POI                                                                                                                                |
| `.xlsx`                        | `XLSX`                     | `FesodDocumentParser`                  | 现代 Excel，项目硬性要求走 Fesod                                                                                                                           |
| `.xls`                         | `XLS`                      | `FesodDocumentParser`                  | 老版 Excel，项目硬性要求走 Fesod                                                                                                                           |
| `.ods`                         | `ODS`                      | `FesodDocumentParser`                  | OpenDocument 表格，经 POI `WorkbookFactory` 自动识别                                                                                                     |
| `.odt`                         | `ODT`                      | `TikaDocumentParser`                   | OpenDocument 文本，Tika 解析                                                                                                                          |
| `.odp`                         | `ODP`                      | `TikaDocumentParser`                   | OpenDocument 演示，Tika 解析                                                                                                                          |
| `.rtf`                         | `RTF`                      | `TikaDocumentParser`                   | 富文本格式，Tika                                                                                                                                       |
| `.txt`                         | `TXT`                      | `TikaDocumentParser`                   | 纯文本，UTF-8 / BOM / 编码自动检测                                                                                                                         |
| `.md` / `.markdown`            | `MD`                       | `TikaDocumentParser`                   | Markdown，优先走内置 fast-path                                                                                                                         |
| `.html` / `.htm`               | `HTML`                     | `TikaDocumentParser`                   | HTML，以 XHTML 解析（Tika + Jsoup）                                                                                                                    |
| `.xml`                         | `XML`                      | `TikaDocumentParser`                   | XML，Tika 解析                                                                                                                                      |
| UNKNOWN                        | `UNKNOWN`                  | (抛 `FormatNotSupportedException`)      | 业务应提供有可识别扩展名的文件                                                                                                                                  |

> **ODS 是 OpenDocument 中的例外**: `.ods` 走 `FesodDocumentParser`（流式、单 sheet O(1) 内存、Apache 治理），`.odt` / `.odp` 走 Tika。这条规则延续自"所有电子表格统一走 Fesod"的项目硬性要求，适用于 .xlsx / .xls / .ods 三种扩展。
>
> **替换引擎永不修改路由表** — 仅修改 `internal/` 内部实现。

---



---

## 🚀 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-document-parser</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

传递依赖(经 `atlas-richie-dependencies` BOM 托管):

- `org.apache.tika:tika-core` + `tika-parsers-standard-package` 3.3.1
- `org.apache.fesod:fesod-sheet` 2.0.2-incubating
- `org.jsoup:jsoup` 1.18.1
- `org.apache.poi:poi-ooxml` 5.5.1
- `org.apache.pdfbox:pdfbox` 3.0.7

### 2. 编写代码

`DocumentReader` 通过 `@EnableConfigurationProperties(ParserProperties.class)` + `ParserAutoConfiguration` 自动装配。Spring Boot 应用中直接注入即可:

```java
@Service
public class DocumentIngestService {

    private final DocumentReader reader;

    public DocumentIngestService(DocumentReader reader) {
        this.reader = reader;
    }

    public ParsedDocument ingest(File file) {
        return reader.parse(file);
    }
}
```

4 个入口的典型调用:

```java
// 1) 本地文件
ParsedDocument doc = reader.parse(new File("contract.pdf"));

// 2) 输入流(调用方负责流的生命周期 — 需要 nameHint)
ParsedDocument doc = reader.parse(inputStream, "report.docx");

// 3) HTTPS URL(自动跑三道防线)
ParsedDocument doc = reader.parse(new URL("https://example.com/manual.pdf"));

// 4) 字符串自动识别: HTTP / HTTPS / file:// / 纯路径
ParsedDocument doc = reader.parse("https://example.com/manual.pdf");
ParsedDocument doc = reader.parse("/data/contracts/q3.pdf");
ParsedDocument doc = reader.parse("file:///tmp/notes.md");
```

非 Spring 上下文(测试、脚本)手动构造:

```java
ParserProperties properties = new ParserProperties();
TikaDocumentParser tika = new TikaDocumentParser(properties);
FesodDocumentParser fesod = new FesodDocumentParser();
ParserRouter router = new ParserRouter(tika, fesod);
UrlFetcher urlFetcher = new UrlFetcher();
DocumentReader reader = new DocumentReader(properties, router, urlFetcher);
```

### 3. 访问解析段落

```java
ParsedDocument doc = reader.parse(new File("contract.pdf"));

doc.title();         // String 或 null
doc.author();        // String 或 null
doc.metadata();      // Map<String, Object> — 格式相关 (format, contentType, sheetCount …)
doc.segments().forEach(seg -> {
    System.out.println("Page " + seg.pageNumber() + ": " + seg.text());
    System.out.println("Section: " + seg.sectionPath());
    System.out.println("Meta: " + seg.meta());
});
```

`ParseListener`(供 `parseStream` 使用)接收流式事件,见下一节。

---

## 🌊 流式异步 API

### 为什么需要流式?

批量入库场景(例如"外部系统推 100 个 PDF,边解析边 embedding")下,同步 `parse(...)` 会阻塞直到整篇文档读完。`parseStream(source, listener)` 通过 `ParseEvent` 边解析边推送,解决此问题。

### ParseEvent 类型

`ParseEvent` 为 sealed 层次:

| 类型               | 触发时机    | 负载                                                               |
|------------------|---------|------------------------------------------------------------------|
| `Streaming`      | 解析到一段文本 | `DocumentSegment segment`                                        |
| `ImageStreaming` | 解析到嵌入图片 | `ImageSegment image`(原始字节)                                       |
| `Finished`       | 解析成功完成  | `ParsedDocument summary`, `int totalSegments`, `int totalImages` |
| `Failed`         | 解析失败    | `DocumentParseException error`(不抛出)                              |

四种事件统一走 `ParseListener.onEvent(ParseEvent event)` 方法。switch 模式消费:

```java
reader.parseStream(
    new ParserSource.FileSource(pdfFile),
    event -> switch (event) {
        case ParseEvent.Streaming s ->
            embeddingService.embed(s.segment().text());
        case ParseEvent.ImageStreaming i ->
            objectStorage.put(sha256(i.image().data()), i.image().data());
        case ParseEvent.Finished f ->
            log.info("完成: {} 段, {} 张图", f.totalSegments(), f.totalImages());
        case ParseEvent.Failed err ->
            log.error("解析失败", err.error());
    }
);
```

> **失败绝不抛出** — 包装在 `ParseEvent.Failed` 中。这是刻意设计:批量中一份文件失败不应中断其余。

### 图片处理原则

组件**不做** OCR / VLM,通过 `ParseEvent.ImageStreaming` 原样返回嵌入图片字节,业务侧自行决定下游:

- OCR 服务: Tesseract / 阿里云 OCR / 通义千问 VL …
- 对象存储: S3 / OSS / MinIO …
- 视觉检索索引或直接忽略

PDF / Word / PPT 图片字节来源:

- Tika XHTML 输出 → Jsoup 解析 `<img>` 标签
- xlsx: `Apache POI` `XSSFPicture`(Phase 8)

### 图片无包装情况

Tika 有时把 PDF 图片渲染为 raw XObject 而不输出 `<img>` 标签。此时组件 emit 一个 **synthetic 占位** `ImageStreaming`:

```json
{
  "type": "ImageStreaming",
  "image": {
    "format": "image/unknown",
    "data": "",
    "name": "(image-only or embedded-raw-image page)",
    "sectionPath": "/(image-only-page)",
    "meta": { "synthetic": true,
              "reason": "Tika XHTML had no <img> wrappers; may contain raw image XObjects" }
  }
}
```

占位信号:"此页是 image-only,对源 PDF 跑 OCR / VLM"。占位不带实际字节,由业务触发下游处理。

### 严格模式(可选)

默认行为下,扫图像启发式触发时 emit synthetic `ImageStreaming`。业务希望直接失败时,开启 **严格模式**:

```yaml
platform:
  component:
    parser:
      pdf:
        image-only-detection:
          enabled: true   # 默认 false — 改为抛异常
```

严格模式下,`parse()` 抛 `ImageOnlyPdfException`;`parseStream()` emit `ParseEvent.Failed` 携带异常。

---

## 🛡️ URL 三道防线

`UrlFetcher` 在拉取任何 URL 之前强制执行 SSRF 防护,各防线独立可配。

### 防线 1 — SSRF(URL / Host / IP 校验)

- **协议校验**: 默认仅允许 HTTPS;HTTP 仅当 `allow-http: true` 时通过
- **DNS 解析 + IP 校验**: 解析后若落入以下网段即拒绝:
  - `127.0.0.0/8` (loopback), `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`
  - IPv6: `::1`, `fc00::/7` (unique-local), link-local, site-local
- **DNS-rebinding 防护**: IP 校验在 DNS 解析后**立刻**进行,与建连同一代码路径,消除 TOCTOU 漏洞

### 防线 2 — HTTP HEAD 协议层

- **Content-Type 白名单**: PDF / DOCX / DOC / XLSX / XLS / PPTX / PPT / ODT / ODS / ODP / RTF / 纯文本 (`text/plain`) / Markdown (`text/markdown`) / HTML (`text/html`) / XML (`application/xml`、`text/xml`) / `application/octet-stream` (兜底)
- **Content-Length 上限**: 拒绝 `Content-Length > max-bytes`(默认 **200 MB**) 的响应
- **跨主机重定向拦截**: 30x 响应仅当 `Location` host 与原 host 相同时跟随,最多 **5 跳**

### 防线 3 — 内容嗅探

- **Tika Detector magic bytes**: 下载后通过 `FormatDetector.detectFormat` 嗅探字节流。若嗅探格式与声明 `Content-Type` 不一致(且声明类型不是 `application/octet-stream`),抛 `FormatNotSupportedException`

任何一道防线失败,**不会**返回任何字节给 parser — URL 被视为不存在。

---

## 🖼️ 全图片 PDF 检测

扫描件 PDF 仅含图片页、无可选文本。静默返回空 `ParsedDocument` 会让下游 RAG 流水线断链。组件对此显式检测。

### 启发式规则

`TikaDocumentParser` 评估:

```
(textChars < min-text-chars  且  imageCount >= min-image-count)
或
(textChars < 50)                  # 快通道
```

默认阈值(可配):

- `min-text-chars`: **200**
- `min-image-count`: **5**

### 异常负载

`ImageOnlyPdfException` 携带诊断字段:

```java
public class ImageOnlyPdfException extends DocumentParseException {
    int    imageCount;   // 检出的图片对象数
    int    textLength;   // 抽取的总文本字符数
    long   fileSize;     // 源 PDF 字节数
    String message;
    String filePath;
}
```

便于业务侧决策 — OCR / VLM / 跳过 / 报警。

> **为什么不自动 OCR?** OCR 成本高、往往需要云 API key、并非总是必要。抛出 `ImageOnlyPdfException` 让组件保持聚焦,业务按需接入 OCR / VLM。

---

## ⚙️ 配置参考

> 按配置前缀组织,每项标注默认值、范围、行为。

### 1. 顶层 (`platform.component.parser`)

| 配置        | 类型      | 默认值    | 说明                            |
|-----------|---------|--------|-------------------------------|
| `enabled` | boolean | `true` | 总开关;关闭后所有 parser `@Bean` 都不注册 |

### 2. URL 获取策略 (`platform.component.parser.url`)

| 配置                 | 类型       | 默认值           | 说明                                             |
|--------------------|----------|---------------|------------------------------------------------|
| `allow-http`       | boolean  | `false`       | `false` 时 `http://` 直接在防线 1 被拒                 |
| `allow-private-ip` | boolean  | `false`       | `false` 时拒绝 loopback / RFC1918 / link-local IP |
| `follow-redirects` | boolean  | `true`        | 是否跟随 30x(限 5 跳,同 host)                         |
| `max-bytes`        | long     | `209_715_200` | 最大响应体大小(默认 **200 MB**)                         |
| `connect-timeout`  | Duration | `5s`          | TCP 建连超时                                       |
| `read-timeout`     | Duration | `60s`         | 读 body 超时                                      |

### 3. PDF 处理 (`platform.component.parser.pdf`)

| 配置                                         | 类型      | 默认值     | 说明                                      |
|--------------------------------------------|---------|---------|-----------------------------------------|
| `pdf.image-only-detection.enabled`         | boolean | `false` | `true` 时命中启发式即抛 `ImageOnlyPdfException` |
| `pdf.image-only-detection.min-text-chars`  | int     | `200`   | 触发阈值:文本字符低于此值视为"稀疏"                     |
| `pdf.image-only-detection.min-image-count` | int     | `5`     | 触发条件:检出至少 N 张图片对象                       |

### 4. Excel 处理 (`platform.component.parser.excel`)

| 配置                                | 类型 | 默认值          | 说明                                                    |
|-------------------------------------|------|----------------|--------------------------------------------------------|
| `excel.streaming-threshold-bytes`   | long | `10_485_760`   | 文件大小阈值(默认 **10 MB**),Fesod 切到 chunked 读取   |

### 配置导航(按功能)

| 你想改什么                  | 配置前缀                                                                             | 章节 |
|------------------------|----------------------------------------------------------------------------------|----|
| 关闭整个组件                 | `platform.component.parser.enabled`                                              | §1 |
| 锁定 URL 访问(仅 HTTPS、禁内网) | `platform.component.parser.url.allow-http` / `.allow-private-ip`                 | §2 |
| 收严 URL 大小 / 超时阈值       | `platform.component.parser.url.max-bytes` / `.connect-timeout` / `.read-timeout` | §2 |
| 严格拒绝全图片 PDF            | `platform.component.parser.pdf.image-only-detection.*`                           | §3 |
| Excel 切到 chunked 读取    | `platform.component.parser.excel.streaming-threshold-bytes`                      | §4 |

> 完整示例见 [`docs/application-parser-example.yml`](docs/application-parser-example.yml)。

---

## ⚠️ 异常参考

| 异常                            | 触发条件                                          | 建议处理                   |
|-------------------------------|-----------------------------------------------|------------------------|
| `DocumentParseException`      | 任何解析失败:IO 错误、文件不存在、运行时异常                      | 重试 / 记录日志 / fallback   |
| `FormatNotSupportedException` | `FormatDetector` 返回 UNKNOWN 且扩展名 fallback 也失败 | 提供有可识别扩展名的文件           |
| `ImageOnlyPdfException`       | PDF 触发全图片启发式(仅在 **严格模式** 下抛出)                 | 接入 OCR / VLM 流水线;或直接跳过 |

```java
try {
    ParsedDocument doc = reader.parse(new File("scan.pdf"));
} catch (ImageOnlyPdfException e) {
    log.warn("PDF 是全图片扫描件,跳过: {}", e.getMessage());
} catch (DocumentParseException e) {
    log.error("解析失败: {}", e.getMessage(), e);
}
```

流式管道里异常经过同样路径,通过 `ParseEvent.Failed` 投递 — 不要 try/catch,直接判断 event 类型。

---

## 🎯 最佳实践

### 入口选择

| 场景                 | 推荐                                           |
|--------------------|----------------------------------------------|
| 本地已知文件             | `reader.parse(File)`                         |
| 下载 / 上传流水线的流       | `reader.parse(InputStream, name)`            |
| 远程 HTTPS / HTTP 文件 | `reader.parse(URL)` 或 `reader.parse(String)` |
| 批量、需要细粒度控制         | `reader.parseStream(...)`                    |

### RAG 段落粒度

- 散文类 PDF / Word 按页切片效果不错
- 电子表格按行切片 — Fesod 每行 emit 一个 `DocumentSegment`,`sectionPath = "/Employees/Row[N]"`
- HTML / Markdown 按章节切片(H1 / H2 边界)
- 切片大小通过 embedding 前的滑动窗口 / 句子聚合进一步调整

### URL 加固

- 生产环境**保持** `allow-http: false`、`allow-private-ip: false`,仅在 dev / 本地测试 server 时打开
- 按业务最大合法文件设置 `max-bytes`,默认 200 MB 防御恶意大文件 DoS
- 固定 `connect-timeout` (如 5s) 与 `read-timeout` (如 60s);否则慢端点可无限占用线程

### 内存与吞吐

- 大 Excel 文件依赖 Fesod 流式 — 不要把整 `.xlsx` 加载到内存
- 批量数百文件,用 `parseStream(...)` + 每文件一个线程池,而非共享单线程
- 大图 PDF 解析前可缩放 — PDFBox 可降 DPI 渲染为 `BufferedImage`,再走 Tika

---

## ❓ FAQ

### 为什么组件不做 OCR/VLM?

OCR / VLM 是业务特定选择 — 有的用 Tesseract 本地跑、有的用阿里云 OCR、有的用通义千问 VL。任一内置会强制一个默认。emit `ParseEvent.ImageStreaming` 把字节交给业务,插入任意引擎。

### PDF 是扫描件怎么办?

默认行为(非严格模式)emit synthetic `ImageStreaming` 占位,提示流水线该页是 image-only。需要显式失败时,开启严格模式(`pdf.image-only-detection.enabled: true`)抛 `ImageOnlyPdfException`。

### TXT/Markdown 也走 Tika,能绕开吗?

可以 — `TextFastPathParser`(在 `internal/`)直接读 TXT / Markdown,绕开 Tika。Tika Detector 识别为纯文本时,路由器选 fast-path。默认始终开启,可配。

### 能不下载就解析远程 Excel 吗?

可以 — `reader.parse(URL)` 走 `UrlFetcher`,把响应字节直接喂给 Fesod。三道防线生效(HEAD 验证 Excel `Content-Type` 白名单、max-bytes 限大小、嗅探确认是真 Excel)。

### 同一文件并发解析 100 次?

`DocumentReader` 线程安全。使用线程池:

```java
ExecutorService pool = Executors.newFixedThreadPool(8);
List<File> files = ...;
files.forEach(f -> pool.submit(() -> reader.parse(f)));
pool.shutdown();
pool.awaitTermination(1, TimeUnit.HOURS);
```

并发流式解析参考 `DocumentReaderTest.parseStream_concurrentMultiplePdfs` 的工作模式。

---

## 📕 相关文档

- [Atlas Richie 平台](../../README.md)
- [Atlas Richie 组件库](../README.md)
- [Apache Tika](https://tika.apache.org/)
- [Apache Fesod (Incubating)](https://fesod.apache.org/)

