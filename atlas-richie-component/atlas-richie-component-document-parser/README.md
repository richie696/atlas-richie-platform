# Atlas Richie Document Parser Component (atlas-richie-component-document-parser)

> **One-line value**: A single `DocumentReader` entry point that parses **PDF / Word / Excel / PPT / ODF / TXT / Markdown** with built-in SSRF defense, structured segments, and a streaming event API for RAG pipelines.

---

## 📖 Contents

- [📋 Overview](#-overview)
- [✨ Core Capabilities](#-core-capabilities)
- [🏗️ Architecture](#-architecture)
  - [Module Structure](#module-structure)
  - [Format → Parser Routing](#format--parser-routing)
- [🚀 Quick Start](#-quick-start)
  - [1. Add the Dependency](#1-add-the-dependency)
  - [2. Write Code](#2-write-code)
  - [3. Access Parsed Segments](#3-access-parsed-segments)
- [🌊 Streaming Async API](#-streaming-async-api)
  - [Why Streaming?](#why-streaming)
  - [ParseEvent Types](#parseevent-types)
  - [Image Handling Philosophy](#image-handling-philosophy)
  - [When Images Have No Wrapper](#when-images-have-no-wrapper)
  - [Strict Mode (opt-in)](#strict-mode-opt-in)
- [🛡️ URL Three Lines of Defense](#-url-three-lines-of-defense)
  - [Defense 1 — SSRF (URL / Host / IP Validation)](#defense-1--ssrf-url--host--ip-validation)
  - [Defense 2 — HTTP HEAD Protocol Layer](#defense-2--http-head-protocol-layer)
  - [Defense 3 — Content Sniffing](#defense-3--content-sniffing)
- [🖼️ Image-Only PDF Detection](#-image-only-pdf-detection)
  - [Heuristic](#heuristic)
  - [Exception Payload](#exception-payload)
- [⚙️ Configuration Reference](#-configuration-reference)
  - [1. Top-Level (`platform.component.parser`)](#1-top-level-platformcomponentparser)
  - [2. URL Fetch Policy (`platform.component.parser.url`)](#2-url-fetch-policy-platformcomponentparserurl)
  - [3. PDF Processing (`platform.component.parser.pdf`)](#3-pdf-processing-platformcomponentparserpdf)
  - [4. Excel Processing (`platform.component.parser.excel`)](#4-excel-processing-platformcomponentparserexcel)
- [⚠️ Exception Reference](#-exception-reference)
- [🎯 Best Practices](#-best-practices)
  - [Choosing the Right Entry Point](#choosing-the-right-entry-point)
  - [Segment Granularity for RAG](#segment-granularity-for-rag)
  - [URL Hardening](#url-hardening)
  - [Memory & Throughput](#memory--throughput)
- [❓ FAQ](#-faq)
  - [Why is there no OC](#why-is-there-no-ocrvlm-in-the-component)
  - [What happens when my PDF is a scanned image?](#what-happens-when-my-pdf-is-a-scanned-image)
  - [TXT/Markdown goes through Tika — can I bypass it?](#txtmarkdown-goes-through-tika--can-i-bypass-it)
  - [Can I parse a remote Excel without downloading it first?](#can-i-parse-a-remote-excel-without-downloading-it-first)
  - [How do I parse the same file 100 times in parallel?](#how-do-i-parse-the-same-file-100-times-in-parallel)
- [📅 Implementation Status](#-implementation-status)
- [📕 Related Documentation](#-related-documentation)

---

## 📋 Overview

`atlas-richie-component-document-parser` is the unified document-parsing component of the Atlas Richie platform. It exposes a single `DocumentReader` facade so business code never depends on Apache Tika, Apache Fesod, PDFBox, or POI directly.

**Underlying engines**:

| Engine                                              | Role                                                                 |
|-----------------------------------------------------|----------------------------------------------------------------------|
| Apache Tika 3.3.1 (`tika-parsers-standard-package`) | PDF / Word / PPT / ODF / TXT / Markdown / HTML / XML                 |
| Apache Fesod `2.0.2-incubating`                     | Excel (`.xlsx` / `.xls` / `.ods`) — streaming, O(1) memory per sheet |
| Apache PDFBox 3.0.7                                 | PDF text extraction (via Tika)                                       |
| Apache POI 5.5.1                                    | Word / PPT / xlsx images                                             |
| Jsoup 1.18.1                                        | `<img>` scanning in Tika XHTML output                                |

**Design contract**: all implementation details live in `internal/`. Replacing Tika or Fesod touches `internal/` only — public API and Spring configuration are unchanged.

---

## ✨ Core Capabilities

- **Single facade** — `DocumentReader.parse(...)` with 4 overloads: `File` / `InputStream` / `URL` / `String` (auto-detect).
- **Format auto-detection** — content sniffing via Tika magic bytes, with extension-name fallback when sniffing fails.
- **Structured segments** — each `DocumentSegment` carries `pageNumber`, `sectionPath`, and a metadata map, optimized for RAG chunk retrieval.
- **SSRF defense** — `UrlFetcher` enforces three layers (IP blocklist → HEAD validation → content sniffing) before any byte is read.
- **DNS-rebinding protection** — host resolution → IP validation → connect, with no TOCTOU window.
- **Cross-host redirect blocking** — 30x responses only followed if `Location` host equals original host (≤ 5 hops).
- **Image-only PDF detection** — explicit failure with `ImageOnlyPdfException` (strict mode) rather than silently returning empty text.
- **Streaming Excel** — Fesod reads one sheet at a time at O(1) memory; large `.xlsx` files stay bounded.
- **Streaming event API** — `parseStream(...)` emits `ParseEvent` records as parsing progresses, suitable for "parse → embed → store" pipelines.
- **Image extraction (Phase 8)** — `ParseEvent.ImageStreaming` returns embedded image bytes for caller-side OCR / storage / VLM.

---

## 🏗️ Architecture

### Module Structure

```
com.richie.component.parser/
├── DocumentParser                # SPI: streaming interface
├── DocumentReader                # 4-overload facade — developer entry point
├── ParsedDocument                # Record (title / author / segments / metadata)
├── DocumentSegment               # Record (text / pageNumber / sectionPath / meta)
├── ImageSegment                  # Record (data / format / sectionPath / meta)  [Phase 8]
├── ParserSource                  # sealed (File / Stream / Url)
│   ├── FileSource
│   ├── StreamSource
│   └── UrlSource
├── ParserContext / UrlFetchPolicy # Configuration records
├── ParseListener / ParseEvent    # Event-driven subscription model
│   ├── Streaming                 # text segment arrived
│   ├── ImageStreaming            # image bytes ready
│   ├── Finished                  # summary + counts
│   └── Failed                    # exception (no throw)
├── config/
│   ├── ParserProperties          # @ConfigurationProperties
│   └── ParserAutoConfiguration   # 5 @Bean registrations
├── exception/
│   ├── DocumentParseException    # Parent of all parse errors
│   ├── FormatNotSupportedException
│   └── ImageOnlyPdfException
└── internal/                     # Implementation details (not exported)
    ├── Format                    # PDF / DOCX / XLSX / ... / UNKNOWN
    ├── FormatDetector            # Tika magic bytes + extension fallback
    ├── TextFastPathParser        # TXT / Markdown bypass-Tika path
    ├── TikaDocumentParser        # PDF / Word / PPT / ODF / RTF / HTML / XML
    ├── FesodDocumentParser       # Excel via Apache Fesod
    ├── UrlFetcher                # JDK HttpClient + SSRF + HEAD + DNS-rebinding
    └── ParserRouter              # Format → DocumentParser dispatch
```

### Format → Parser Routing

| Extension                      | Format                               | Routed To                                                          | Reason                                                                                                                                                                    |
|--------------------------------|--------------------------------------|--------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `.pdf`                         | `PDF`                                | `TikaDocumentParser`                                               | Binary PDF, parsed via Tika + PDFBox                                                                                                                                      |
| `.docx`                        | `DOCX`                               | `TikaDocumentParser`                                               | Modern Word, parsed via Tika + POI                                                                                                                                        |
| `.doc`                         | `DOC`                                | `TikaDocumentParser`                                               | Legacy Word, parsed via Tika + POI                                                                                                                                        |
| `.pptx`                        | `PPTX`                               | `TikaDocumentParser`                                               | Modern PowerPoint, parsed via Tika + POI                                                                                                                                  |
| `.ppt`                         | `PPT`                                | `TikaDocumentParser`                                               | Legacy PowerPoint, parsed via Tika + POI                                                                                                                                  |
| `.xlsx`                        | `XLSX`                               | `FesodDocumentParser`                                              | Modern Excel, project-mandated Fesod                                                                                                                                      |
| `.xls`                         | `XLS`                                | `FesodDocumentParser`                                              | Legacy Excel, project-mandated Fesod                                                                                                                                      |
| `.ods`                         | `ODS`                                | `FesodDocumentParser`                                              | OpenDocument Spreadsheet, via POI `WorkbookFactory` auto-detect                                                                                                           |
| `.odt`                         | `ODT`                                | `TikaDocumentParser`                                               | OpenDocument Text, via Tika                                                                                                                                               |
| `.odp`                         | `ODP`                                | `TikaDocumentParser`                                               | OpenDocument Presentation, via Tika                                                                                                                                       |
| `.rtf`                         | `RTF`                                | `TikaDocumentParser`                                               | Rich Text Format, via Tika                                                                                                                                                |
| `.txt`                         | `TXT`                                | `TikaDocumentParser`                                               | Plain text (UTF-8 / BOM / encoding detection)                                                                                                                             |
| `.md` / `.markdown`            | `MD`                                 | `TikaDocumentParser`                                               | Markdown — also takes the in-house fast-path                                                                                                                              |
| `.html` / `.htm`               | `HTML`                               | `TikaDocumentParser`                                               | HTML, parsed as XHTML via Tika + Jsoup                                                                                                                                    |
| `.xml`                         | `XML`                                | `TikaDocumentParser`                                               | XML, parsed via Tika                                                                                                                                                      |
| UNKNOWN                        | `UNKNOWN`                            | (throws `FormatNotSupportedException`)                             | Caller should provide a file with a recognized extension                                                                                                                  |

> **ODS is the exception within OpenDocument**: `.ods` goes through `FesodDocumentParser` (streaming, O(1) memory per sheet, Apache governance), while `.odt` and `.odp` go through Tika. The same Microsoft vs OpenDocument "every spreadsheet inside Fesod" rule that applies to .xlsx / .xls is extended to ODS for consistency.
>
> **Swapping engines never changes the dispatch table** — only the implementation inside `internal/` is touched.

---



---

## 🚀 Quick Start

### 1. Add the Dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-document-parser</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Transitive dependencies (managed via `atlas-richie-dependencies` BOM):

- `org.apache.tika:tika-core` + `tika-parsers-standard-package` 3.3.1
- `org.apache.fesod:fesod-sheet` 2.0.2-incubating
- `org.jsoup:jsoup` 1.18.1
- `org.apache.poi:poi-ooxml` 5.5.1
- `org.apache.pdfbox:pdfbox` 3.0.7

### 2. Write Code

`DocumentReader` is auto-configured by `@EnableConfigurationProperties(ParserProperties.class)` + `ParserAutoConfiguration`. In a Spring Boot application, inject it directly:

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

The four entry points:

```java
// 1) Local file
ParsedDocument doc = reader.parse(new File("contract.pdf"));

// 2) InputStream (caller manages stream lifecycle — resource hint required)
ParsedDocument doc = reader.parse(inputStream, "report.docx");

// 3) HTTPS URL — auto-runs three lines of defense
ParsedDocument doc = reader.parse(new URL("https://example.com/manual.pdf"));

// 4) String auto-detect: HTTP / HTTPS / file:// / plain path
ParsedDocument doc = reader.parse("https://example.com/manual.pdf");
ParsedDocument doc = reader.parse("/data/contracts/q3.pdf");
ParsedDocument doc = reader.parse("file:///tmp/notes.md");
```

In non-Spring contexts (tests, scripts), construct the facade manually:

```java
ParserProperties properties = new ParserProperties();
TikaDocumentParser tika = new TikaDocumentParser(properties);
FesodDocumentParser fesod = new FesodDocumentParser();
ParserRouter router = new ParserRouter(tika, fesod);
UrlFetcher urlFetcher = new UrlFetcher();
DocumentReader reader = new DocumentReader(properties, router, urlFetcher);
```

### 3. Access Parsed Segments

```java
ParsedDocument doc = reader.parse(new File("contract.pdf"));

doc.title();         // String or null
doc.author();        // String or null
doc.metadata();      // Map<String, Object> — format-specific (format, contentType, sheetCount …)
doc.segments().forEach(seg -> {
    System.out.println("Page " + seg.pageNumber() + ": " + seg.text());
    System.out.println("Section: " + seg.sectionPath());
    System.out.println("Meta: " + seg.meta());
});
```

The `ParseListener` (used by `parseStream`) is the receiving end for streamed events — see the next section.

---

## 🌊 Streaming Async API

### Why Streaming?

For batch ingestion — say, "external system pushes 100 PDFs, embed each segment as soon as it is parsed" — the synchronous `parse(...)` blocks until the entire document is read. `parseStream(source, listener)` solves this by emitting `ParseEvent` records as parsing progresses.

### ParseEvent Types

`ParseEvent` is a sealed hierarchy:

| Type             | Emitted When                   | Payload                                                          |
|------------------|--------------------------------|------------------------------------------------------------------|
| `Streaming`      | A text segment is parsed       | `DocumentSegment segment`                                        |
| `ImageStreaming` | An embedded image is found     | `ImageSegment image` (raw bytes)                                 |
| `Finished`       | Parsing completes successfully | `ParsedDocument summary`, `int totalSegments`, `int totalImages` |
| `Failed`         | Parsing fails                  | `DocumentParseException error` (no throw)                        |

All four types are emitted through a single `ParseListener.onEvent(ParseEvent event)` method. Switch-pattern consumption:

```java
reader.parseStream(
    new ParserSource.FileSource(pdfFile),
    event -> switch (event) {
        case ParseEvent.Streaming s ->
            embeddingService.embed(s.segment().text());
        case ParseEvent.ImageStreaming i ->
            objectStorage.put(sha256(i.image().data()), i.image().data());
        case ParseEvent.Finished f ->
            log.info("done: {} segments, {} images", f.totalSegments(), f.totalImages());
        case ParseEvent.Failed err ->
            log.error("parse failed", err.error());
    }
);
```

> **Failures never throw** — they are wrapped in `ParseEvent.Failed`. This is by design: one bad document in a batch must not abort the rest.

### Image Handling Philosophy

The component does **not** perform OCR / VLM. It returns embedded images as raw bytes via `ParseEvent.ImageStreaming`. The caller decides the downstream:

- OCR service: Tesseract, Alibaba Cloud OCR, Qwen-VL …
- Object storage: S3 / OSS / MinIO …
- Indexed for visual retrieval, or simply ignored

PDF / Word / PPT image bytes are extracted via:

- `Jsoup` parsing of Tika's XHTML output → `<img>` tags
- `Apache POI` `XSSFPicture` (xlsx, Phase 8)

### When Images Have No Wrapper

Tika sometimes wraps images as raw PDF XObjects without producing `<img>` tags in its XHTML output. In that case, a **synthetic placeholder** `ImageStreaming` event is emitted:

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

This signals "the page is image-only, run OCR / VLM on the source PDF". The placeholder carries no actual bytes — the caller triggers downstream OCR / VLM.

### Strict Mode (opt-in)

The default behavior is to emit a synthetic `ImageStreaming` when a scanned-image heuristic triggers. If the business wants explicit failure instead, enable **strict mode**:

```yaml
platform:
  component:
    parser:
      pdf:
        image-only-detection:
          enabled: true   # default false — throw ImageOnlyPdfException
```

In strict mode, `parse()` throws `ImageOnlyPdfException`; `parseStream()` emits `ParseEvent.Failed` carrying the exception.

---

## 🛡️ URL Three Lines of Defense

`UrlFetcher` enforces SSRF protection before any byte is fetched from a remote URL. Each defense is independently configurable.

### Defense 1 — SSRF (URL / Host / IP Validation)

- **Scheme**: HTTPS required by default; HTTP allowed only if `allow-http: true`.
- **DNS resolution + IP validation**: resolved IP must NOT be in:
  - `127.0.0.0/8` (loopback), `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`
  - IPv6: `::1`, `fc00::/7` (unique-local), link-local, site-local
- **DNS-rebinding protection**: IP validation happens **immediately** after DNS resolution, on the same code path that opens the connection — eliminating the TOCTOU gap.

### Defense 2 — HTTP HEAD Protocol Layer

- **Content-Type whitelist**: PDF / DOCX / DOC / XLSX / XLS / PPTX / PPT / ODT / ODS / ODP / RTF / plain text (`text/plain`) / Markdown (`text/markdown`) / HTML (`text/html`) / XML (`application/xml`, `text/xml`) / `application/octet-stream` (fallback).
- **Content-Length cap**: rejects responses with `Content-Length > max-bytes` (default **200 MB**).
- **Cross-host redirect blocking**: 30x responses followed **only** when `Location` host equals the original host, up to **5 hops**.

### Defense 3 — Content Sniffing

- **Tika Detector magic bytes**: after download, the byte stream is sniffed via `FormatDetector.detectFormat`. If the sniffed format disagrees with the declared `Content-Type` (and the declared type isn't `application/octet-stream`), `FormatNotSupportedException` is thrown.

If any defense fails, the connection is refused and **no bytes are returned to the parser** — the URL is treated as if it does not exist.

---

## 🖼️ Image-Only PDF Detection

PDFs created from scanners contain image pages with no selectable text. Returning an empty `ParsedDocument` silently breaks downstream RAG pipelines. The component detects this and reacts explicitly.

### Heuristic

`TikaDocumentParser` evaluates:

```
(textChars < min-text-chars  AND  imageCount >= min-image-count)
OR
(textChars < 50)                          # fast-path
```

Default thresholds (configurable):

- `min-text-chars`: **200**
- `min-image-count`: **5**

### Exception Payload

`ImageOnlyPdfException` carries diagnostic fields:

```java
public class ImageOnlyPdfException extends DocumentParseException {
    int    imageCount;   // number of image objects detected
    int    textLength;   // total text characters extracted
    long   fileSize;     // bytes of the source PDF
    String message;
    String filePath;
}
```

These let the caller decide whether to OCR the file, run VLM, or skip.

> **Why not OCR automatically?** OCR is expensive, often requires a cloud API key, and is not always necessary. Surfacing `ImageOnlyPdfException` keeps the parser component focused and lets the business wire OCR / VLM explicitly.

---

## ⚙️ Configuration Reference

> Below is organized by configuration prefix. Every option lists its default, accepted range, and behavior.

### 1. Top-Level (`platform.component.parser`)

| Config    | Type    | Default | Description                                                  |
|-----------|---------|---------|--------------------------------------------------------------|
| `enabled` | boolean | `true`  | Master switch; off disables all parser `@Bean` registrations |

### 2. URL Fetch Policy (`platform.component.parser.url`)

| Config             | Type     | Default       | Description                                                         |
|--------------------|----------|---------------|---------------------------------------------------------------------|
| `allow-http`       | boolean  | `false`       | If `false`, `http://` URLs are refused (rejected at Defense 1)      |
| `allow-private-ip` | boolean  | `false`       | If `false`, loopback / RFC1918 / link-local IPs are refused         |
| `follow-redirects` | boolean  | `true`        | Whether to follow 30x responses (limited to 5 hops, same host only) |
| `max-bytes`        | long     | `209_715_200` | Max response body size in bytes (default **200 MB**)                |
| `connect-timeout`  | Duration | `5s`          | TCP connect timeout                                                 |
| `read-timeout`     | Duration | `60s`         | Body read timeout                                                   |

### 3. PDF Processing (`platform.component.parser.pdf`)

| Config                                     | Type    | Default | Description                                               |
|--------------------------------------------|---------|---------|-----------------------------------------------------------|
| `pdf.image-only-detection.enabled`         | boolean | `false` | If `true`, throw `ImageOnlyPdfException` on the heuristic |
| `pdf.image-only-detection.min-text-chars`  | int     | `200`   | Trigger threshold: text characters below this is "sparse" |
| `pdf.image-only-detection.min-image-count` | int     | `5`     | Trigger condition: at least N image objects detected      |

### 4. Excel Processing (`platform.component.parser.excel`)

| Config                            | Type | Default      | Description                                                                           |
|-----------------------------------|------|--------------|---------------------------------------------------------------------------------------|
| `excel.streaming-threshold-bytes` | long | `10_485_760` | File-size threshold (default **10 MB**) above which Fesod switches to chunked reading |

### Configuration Overview (by function)

| What you want to change                     | Config prefix                                                                    | Section |
|---------------------------------------------|----------------------------------------------------------------------------------|---------|
| Disable the whole component                 | `platform.component.parser.enabled`                                              | §1      |
| Lock URL access (HTTPS-only, no private IP) | `platform.component.parser.url.allow-http` / `.allow-private-ip`                 | §2      |
| Tighten URL size / timeout limits           | `platform.component.parser.url.max-bytes` / `.connect-timeout` / `.read-timeout` | §2      |
| Strict image-only PDF rejection             | `platform.component.parser.pdf.image-only-detection.*`                           | §3      |
| Switch Excel to chunked reading             | `platform.component.parser.excel.streaming-threshold-bytes`                      | §4      |

> See [`docs/application-parser-example.yml`](docs/application-parser-example.yml) for a complete example.

---

## ⚠️ Exception Reference

| Exception                     | Trigger Condition                                                      | Suggested Action                           |
|-------------------------------|------------------------------------------------------------------------|--------------------------------------------|
| `DocumentParseException`      | Any parse failure: IO error, file not found, runtime error             | Retry / log / fallback                     |
| `FormatNotSupportedException` | `FormatDetector` returns UNKNOWN and the extension fallback also fails | Provide a file with a recognized extension |
| `ImageOnlyPdfException`       | A PDF triggers the image-only heuristic (only in **strict mode**)      | Run OCR / VLM on the source PDF; or skip   |

```java
try {
    ParsedDocument doc = reader.parse(new File("scan.pdf"));
} catch (ImageOnlyPdfException e) {
    log.warn("PDF is image-only, skipping: {}", e.getMessage());
} catch (DocumentParseException e) {
    log.error("Parse failed: {}", e.getMessage(), e);
}
```

`ParseEvent.Failed` carries the same exceptions inside streaming pipelines — handle them by checking the event type rather than try/catch.

---

## 🎯 Best Practices

### Choosing the Right Entry Point

| Scenario                                         | Recommended                                   |
|--------------------------------------------------|-----------------------------------------------|
| Local known file                                 | `reader.parse(File)`                          |
| Stream from a download / upload pipeline         | `reader.parse(InputStream, name)`             |
| Remote HTTPS / HTTP file                         | `reader.parse(URL)` or `reader.parse(String)` |
| Batch / pipeline that needs fine-grained control | `reader.parseStream(...)`                     |

### Segment Granularity for RAG

- Per-page segments work well for prose documents (PDF / Word).
- Per-row segments work well for spreadsheets — Fesod emits one `DocumentSegment` per row, each with `sectionPath = "/Employees/Row[N]"`.
- Per-section segments are produced for HTML / Markdown (H1 / H2 boundaries).
- Tune the chunk size by post-processing segments (sliding window / sentence aggregation) before embedding.

### URL Hardening

- Keep `allow-http: false` and `allow-private-ip: false` in production — both should be `true` only in development or tests against a local server.
- Set `max-bytes` based on the largest legitimate document your business handles. The default 200 MB protects against malicious large-file DoS.
- Pin `connect-timeout` (e.g. 5s) and `read-timeout` (e.g. 60s). Without these, a slow remote endpoint can hold threads indefinitely.

### Memory & Throughput

- For large Excel files, rely on Fesod's streaming — never load the whole `.xlsx` into memory.
- In a batch with hundreds of files, use `parseStream(...)` + a thread pool per file instead of one shared thread.
- For PDF image-heavy documents, downsize before parsing when feasible: PDFBox can render at lower DPI to a `BufferedImage`, then run Tika on the rendered pages.

---

## ❓ FAQ

### Why is there no OCR/VLM in the component?

OCR / VLM is a business-specific decision — some teams use Tesseract locally, others call Alibaba Cloud OCR, others use Qwen-VL. Embedding any of these would force one default on everyone. By emitting `ParseEvent.ImageStreaming`, the component hands the bytes to the caller and lets them plug in whichever engine fits.

### What happens when my PDF is a scanned image?

Default behavior (non-strict mode) emits a synthetic `ImageStreaming` placeholder so the pipeline knows the page is image-only. Enable strict mode (`pdf.image-only-detection.enabled: true`) to throw `ImageOnlyPdfException` instead.

### TXT/Markdown goes through Tika — can I bypass it?

Yes — `TextFastPathParser` (in `internal/`) reads plain TXT / Markdown directly, bypassing Tika. The router picks fast-path when Tika Detector identifies the file as plain text. Switching fast-path on by default is configurable but currently always-on.

### Can I parse a remote Excel without downloading it first?

Yes — `reader.parse(URL)` runs through `UrlFetcher` and pipes the response bytes straight into Fesod. The three lines of defense apply (Head validation gates Excel by `Content-Type` whitelist, max-bytes caps the body, content sniffing verifies it is genuinely an Excel file).

### How do I parse the same file 100 times in parallel?

`DocumentReader` is thread-safe. Use a thread pool:

```java
ExecutorService pool = Executors.newFixedThreadPool(8);
List<File> files = ...;
files.forEach(f -> pool.submit(() -> reader.parse(f)));
pool.shutdown();
pool.awaitTermination(1, TimeUnit.HOURS);
```

For streaming parallel parsing, see `DocumentReaderTest.parseStream_concurrentMultiplePdfs` for the working pattern.

---

## 📕 Related Documentation

- [Atlas Richie Platform](../../README.md)
- [Atlas Richie Component Library](../README.md)
- [Apache Tika](https://tika.apache.org/)
- [Apache Fesod (Incubating)](https://fesod.apache.org/)

