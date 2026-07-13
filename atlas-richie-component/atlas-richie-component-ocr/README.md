# Atlas Richie OCR Component (atlas-richie-component-ocr)

> **One-line value**: A single `OcrProvider` interface that abstracts **8 OCR backends** (Aliyun / Baidu / Tencent Cloud / Volcano Engine / PaddleOCR / Tesseract / PaddleOCR-VL / MinerU) behind one synchronous facade, with built-in multi-language support, yaml-only vendor switching, and zero-config Micrometer / OpenTelemetry integrations.

---

## 📖 Contents

- [📋 Overview](#-overview)
- [✨ Core Capabilities](#-core-capabilities)
- [🏗️ Architecture](#-architecture)
  - [Module Structure](#module-structure)
  - [Three-Layer Package Design](#three-layer-package-design)
- [🚀 Quick Start](#-quick-start)
  - [1. Add the Dependency](#1-add-the-dependency)
  - [2. Configure application.yml](#2-configure-applicationyml)
  - [3. Inject `OcrProvider` and Call](#3-inject-ocrprovider-and-call)
- [🧩 Complete Usage Example](#-complete-usage-example)
  - [Full `pom.xml` fragment](#full-pomxml-fragment)
  - [Full `application.yml`](#full-applicationyml)
  - [Full Service Class (3 patterns + exception handling)](#full-service-class-3-patterns--exception-handling)
- [🌐 Multi-Language](#-multi-language)
  - [Supported Languages](#supported-languages)
  - [Per-Call Override vs Global Default](#per-call-override-vs-global-default)
  - [Vendor Multi-Language Mapping](#vendor-multi-language-mapping)
- [🎯 Vendor Selection Guide](#-vendor-selection-guide)
  - [Quick Decision Matrix](#quick-decision-matrix)
  - [Detailed Trade-offs](#detailed-trade-offs)
  - [Recommendation by Document Type](#recommendation-by-document-type)
  - [Migration Path](#migration-path)
  - [🖥️ Local Deployment & Server Configuration Guide](#-local-deployment--server-configuration-guide)
  - [When You Need Multiple Vendors (Special Case)](#when-you-need-multiple-vendors-special-case)
- [🔌 Optional Extensions](#-optional-extensions)
  - [`ocr-extension-micrometer` — Health Metrics](#ocr-extension-micrometer--health-metrics)
  - [`ocr-extension-otel` — OpenTelemetry Tracing](#ocr-extension-otel--opentelemetry-tracing)
- [⚙️ Configuration Reference](#-configuration-reference)
- [⚠️ Exception Reference](#-exception-reference)
- [🎯 Best Practices](#-best-practices)
- [❓ FAQ](#-faq)
  - [Why is the API split between `engine` / `provider` / `exception` packages?](#why-is-the-api-split-between-engine--provider--exception-packages)
  - [Why inject the concrete `AliyunOcrProvider` instead of a facade?](#why-inject-the-concrete-aliyunocrprovider-instead-of-a-facade)
  - [Can multiple vendors be active simultaneously?](#can-multiple-vendors-be-active-simultaneously)
  - [How does paddle-vl / mineru handle long-running inference under a synchronous API?](#how-does-paddle-vl--mineru-handle-long-running-inference-under-a-synchronous-api)
  - [Why don't `ocr-extension-micrometer` / `ocr-extension-otel` need config?](#why-dont-ocr-extension-micrometer--ocr-extension-otel-need-config)

---

## 📋 Overview

`atlas-richie-component-ocr` is the unified OCR component of the Atlas Richie platform. Business code only depends on a single `OcrProvider` interface and never references vendor-specific SDKs (Aliyun SDK, Baidu OAuth, Tesseract CLI, PaddleOCR Python, etc.). Switching vendors is a yaml-only change.

This component follows the **L2 single-vendor architecture**: exactly one vendor is selected per deployment via `platform.component.ocr.vendor`, and its corresponding `OcrProvider` bean is auto-configured. Business code injects that concrete provider (e.g. `@Autowired AliyunOcrProvider`) and calls `recognize(image, options)` synchronously.

**Underlying engines**:

| Vendor       | Type            | Sync   | Protocol                                                         |
|--------------|-----------------|--------|------------------------------------------------------------------|
| Aliyun       | Cloud           | Sync   | HTTP POST `/v1/ocr/recognize` + APPCODE auth                      |
| Baidu        | Cloud           | Sync   | HTTP POST form `/rest/2.0/ocr/v1/general_basic` + OAuth2 token    |
| Tencent Cloud | Cloud          | Sync   | HTTP POST `ocr.tencentcloudapi.com` + TC3-HMAC-SHA256             |
| Volcano Engine | Cloud         | Sync   | HTTP POST `ocr.volcengineapi.com` + AWS4-HMAC-SHA256              |
| PaddleOCR    | Local CPU       | Sync   | Python subprocess `paddle_ocr.py` (via classpath script)          |
| Tesseract    | Local CPU       | Sync   | CLI subprocess `tesseract ... stdout tsv` (joins multi-lang with `+`) |
| PaddleOCR-VL | Local GPU (VLM) | Sync (internal poll) | HTTP POST `/submit` + GET `/tasks/{id}` polling inside `recognize()` |
| MinerU       | Local GPU       | Sync (internal poll) | multipart POST `/upload` PDF + GET `/tasks/{id}` polling inside `recognize()` |

> **Note**: All vendors expose a synchronous `recognize(OcrImage, OcrOptions)` API. PaddleOCR-VL and MinerU block their calling thread while polling the upstream service until the task completes — `recognize()` only returns when the final result is ready, or an exception is thrown.

**Design contract**: every vendor implements `OcrProvider extends AbstractOcrProvider<REQ, RES>` with three template methods (`toProviderRequest` / `callProvider` / `fromProviderResponse`). The core composes the abstract types (`OcrImage`, `OcrOptions`, `OcrResult`, `Languages`); vendor modules provide the concrete `OcrProvider` bean.

---

## ✨ Core Capabilities

- **Single facade interface** — `@Autowired AliyunOcrProvider` (or any concrete vendor provider), call `provider.recognize(image, options)`.
- **8 vendor implementations** — switch with `platform.component.ocr.vendor=aliyun|baidu|paddle|tesseract|paddle-vl|mineru|tencent|volcano`.
- **Multi-language** — 16 `Languages` enum constants, `Set<Languages>` per-call, global `default-languages` config, `AUTO` sentinel, vendor-native multi-lang mapping (Tesseract joins with `+`).
- **Synchronous-only API** — every vendor exposes `OcrProvider.recognize(OcrImage, OcrOptions)`. Internally blocking calls (PaddleOCR-VL / MinerU) poll until completion before returning.
- **Yaml-only vendor switching** — change `platform.component.ocr.vendor` plus import the matching module; business code references the concrete provider and recompiles once, never touches vendor SDKs.
- **Single-vendor guarantee** — `@ConditionalOnProperty(vendor=...)` ensures exactly one vendor autoconfig activates per deployment.
- **Zero-config observability** — `ocr-extension-micrometer` and `ocr-extension-otel` auto-detect existing global infrastructure; no yaml config required.
- **8 sealed exceptions** — `ConfigMissing`, `ProviderUnavailable`, `Unrecognized`, `SidecarUnavailable`, `VlmTimeout`, `VlmOutOfMemory`, `ImageTooLargeForSync`, `NoAvailableProvider`.

---

## 🏗️ Architecture

### Module Structure

```
atlas-richie-component-ocr/                              (aggregator POM)
├── atlas-richie-component-ocr-core/                     (SPI + abstract types + defaults)
├── atlas-richie-component-ocr-aliyun/                   (Cloud: HTTP + APPCODE)
├── atlas-richie-component-ocr-baidu/                    (Cloud: HTTP + OAuth2)
├── atlas-richie-component-ocr-tencent/                  (Cloud: TC3-HMAC-SHA256)
├── atlas-richie-component-ocr-volcano/                  (Cloud: AWS4-HMAC-SHA256)
├── atlas-richie-component-ocr-paddle/                   (Local CPU: Python subprocess)
├── atlas-richie-component-ocr-tesseract/                (Local CPU: CLI subprocess)
├── atlas-richie-component-ocr-paddle-vl/                (Local GPU: VLM, internal sync polling)
├── atlas-richie-component-ocr-mineru/                   (Local GPU: PDF, internal sync polling)
└── atlas-richie-component-ocr-extension/                (optional extensions aggregator)
    ├── atlas-richie-component-ocr-extension-micrometer/ (Health Metrics)
    └── atlas-richie-component-ocr-extension-otel/       (OpenTelemetry Tracing)
```

### Three-Layer Package Design

`ocr-core` organizes code into three semantic sub-packages:

```
com.richie.component.ocr/
├── engine/             # Business-facing types
│   ├── OcrImage                # sealed (Bytes / Url / Stream)
│   ├── OcrOptions              # Builder, dpi / languages / confidenceThreshold
│   ├── OcrResult               # Top-level result (text + blocks + confidence + latencyMs)
│   ├── OcrBlock / OcrLine / Point
│   ├── Languages               # 16 enum constants
│   ├── MimeType / HttpAuth
│
├── provider/           # SPI for vendor implementations
│   ├── OcrProvider             # Top-level interface, single sync recognize(OcrImage, OcrOptions)
│   ├── OcrProviderConfig       # Vendor config interface
│   ├── AbstractOcrProvider<REQ, RES>   # Template-method base class
│
└── exception/          # Cross-package error types
    └── OcrException sealed + 8 subclasses
```

Each vendor module (`ocr-aliyun`, `ocr-baidu`, …) ships one concrete `XxxOcrProvider extends AbstractOcrProvider<Req, Res>` plus an `XxxOcrAutoConfiguration` gated on `platform.component.ocr.vendor`. Exactly one of them activates per deployment.

---

## 🚀 Quick Start

### 1. Add the Dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-ocr-core</artifactId>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-ocr-aliyun</artifactId>  <!-- or any vendor -->
</dependency>
```

### 2. Configure application.yml

```yaml
platform:
  component:
    ocr:
      enabled: true                              # master switch, default true
      vendor: aliyun                             # aliyun | baidu | paddle | tesseract | paddle-vl | mineru | tencent | volcano
      default-languages:                         # global default (optional)
        - CHINESE_SIMPLIFIED_AND_ENGLISH
      aliyun:                                    # vendor-specific section
        endpoint: https://ocr-api.cn-shanghai.aliyuncs.com
        credentials:
          app-code: ${ALIYUN_APP_CODE:}
        timeout-ms: 30000
        vendor.model: standard-form
```

### 3. Inject `OcrProvider` and Call

```java
@Service
@RequiredArgsConstructor
public class OcrBizService {

    private final AliyunOcrProvider ocrProvider;       // inject the concrete vendor provider

    public OcrResult recognize(byte[] imageData) {
        OcrOptions opts = OcrOptions.builder()
                .languages(Languages.CHINESE_SIMPLIFIED)              // per-call language
                .tableRecognition(true)
                .build();
        return ocrProvider.recognize(new OcrImage.Bytes(imageData, MimeType.PNG), opts);
    }

    public OcrResult recognizeMultiLang(byte[] imageData) {
        // Multi-language: Tesseract joins with "+"; Baidu/Paddle take first
        OcrOptions opts = OcrOptions.builder()
                .languages(Languages.JAPANESE, Languages.ENGLISH)
                .build();
        return ocrProvider.recognize(new OcrImage.Bytes(imageData), opts);
    }

    public OcrResult useGlobalDefault(byte[] imageData) {
        // Explicitly use yml default-languages
        OcrOptions opts = OcrOptions.builder()
                .languages(Languages.AUTO)
                .build();
        return ocrProvider.recognize(new OcrImage.Bytes(imageData), opts);
    }
}
```

**Switching vendors** is a two-step change (pom + yml), and only the field type in the constructor (`AliyunOcrProvider` → `TencentOcrProvider` …) changes:

```xml
<!-- pom.xml: remove ocr-aliyun, add ocr-tencent -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-ocr-tencent</artifactId>
</dependency>
```

```yaml
# application.yml: change vendor + vendor config block
platform:
  component:
    ocr:
      vendor: tencent
      tencent:
        region: ap-guangzhou
        action: GeneralAccurateOCR
        credentials:
          secret-id: ${TENCENT_SECRET_ID:}
          secret-key: ${TENCENT_SECRET_KEY:}
```

```java
@Service
@RequiredArgsConstructor
public class OcrBizService {
    private final TencentOcrProvider ocrProvider;   // only this line changed
    // …
}
```

> **Why inject the concrete provider?** With one vendor per deployment there is no registry, no routing, no fallback. The concrete type documents the active choice at the field-declaration site and keeps the Spring graph static.

---

## 🧩 Complete Usage Example

A single end-to-end scenario: invoice OCR service with multi-language, table recognition, and exception mapping. Copy-paste-runnable.

### Full `pom.xml` fragment

```xml
<dependencies>
    <!-- ===== Core (always required) ===== -->
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-core</artifactId>
    </dependency>

    <!-- ===== Pick ONE vendor ===== -->
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-aliyun</artifactId>
    </dependency>
    <!-- Alternatives (uncomment to switch):
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-baidu</artifactId>
    </dependency>
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-tencent</artifactId>
    </dependency>
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-volcano</artifactId>
    </dependency>
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-paddle</artifactId>
    </dependency>
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-tesseract</artifactId>
    </dependency>
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-paddle-vl</artifactId>
    </dependency>
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-mineru</artifactId>
    </dependency>
    -->

    <!-- ===== Optional extensions ===== -->
    <!-- Micrometer health metrics (auto-detects existing MeterRegistry) -->
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-extension-micrometer</artifactId>
    </dependency>
    <!-- OpenTelemetry tracing (auto-detects existing Tracer, GraalVM friendly) -->
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-extension-otel</artifactId>
    </dependency>
</dependencies>
```

### Full `application.yml`

```yaml
platform:
  component:
    ocr:
      # ===== Master switch =====
      enabled: true

      # ===== Active vendor (only ONE activates per @ConditionalOnProperty) =====
      vendor: aliyun

      # ===== Global default languages (used when options.languages is null or AUTO) =====
      default-languages:
        - CHINESE_SIMPLIFIED_AND_ENGLISH
        # - JAPANESE    # uncomment to add as fallback

      # ===== Aliyun (active) =====
      aliyun:
        endpoint: https://ocr-api.cn-shanghai.aliyuncs.com
        timeout-ms: 30000
        credentials:
          app-code: ${ALIYUN_APP_CODE:}            # from Alibaba Cloud market
        vendor.model: standard-form                # standard-form | standard-ocr | advanced-ocr

      # ===== Alternative vendor configs (comment out, fill when switching) =====
      # baidu:
      #   api-key: ${BAIDU_API_KEY:}
      #   secret-key: ${BAIDU_SECRET_KEY:}
      #   endpoint: https://aip.baidubce.com
      #   timeout-ms: 30000
      #
      # tencent:
      #   region: ap-guangzhou
      #   action: GeneralAccurateOCR            # GeneralAccurateOCR | GeneralBasicOCR
      #   timeout-ms: 30000
      #   credentials:
      #     secret-id: ${TENCENT_SECRET_ID:}
      #     secret-key: ${TENCENT_SECRET_KEY:}
      #
      # volcano:
      #   region: cn-north-1
      #   timeout-ms: 30000
      #   credentials:
      #     access-key: ${VOLCANO_ACCESS_KEY:}
      #     secret-key: ${VOLCANO_SECRET_KEY:}
      #
      # paddle:
      #   model-dir: /opt/paddle-ocr/models          # required
      #   python-path: python3
      #   timeout-ms: 60000
      #
      # tesseract:
      #   tessdata-path: /usr/share/tesseract-ocr/4.00/tessdata  # required
      #   timeout-ms: 30000
      #
      # paddle-vl:
      #   grpc-endpoint: localhost:50051
      #   gpu-pool: 1
      #   timeout-ms: 120000
      #
      # mineru:
      #   endpoint: http://mineru.internal:8000
      #   api-key: ${MINERU_API_KEY:}
      #   timeout-ms: 180000

# ===== Optional: enable actuator for /actuator/prometheus =====
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus

# ===== Optional: enable OpenTelemetry OTLP export =====
otel:
  exporter:
    otlp:
      endpoint: ${OTLP_ENDPOINT:http://localhost:4317}
```

### Full Service Class (3 patterns + exception handling)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceOcrService {

    private final AliyunOcrProvider ocrProvider;        // the active vendor's concrete provider

    /** Pattern 1: Sync recognize, single language, table recognition (most common). */
    public OcrResult recognizeInvoice(byte[] imageData) {
        OcrOptions opts = OcrOptions.builder()
                .languages(Languages.CHINESE_SIMPLIFIED)
                .tableRecognition(true)                          // invoices have tables
                .confidenceThreshold(0.7f)
                .build();
        return ocrProvider.recognize(new OcrImage.Bytes(imageData, MimeType.PNG), opts);
    }

    /** Pattern 2: Use yml default-languages globally (most concise — no per-call language). */
    public OcrResult recognizeWithGlobalDefault(byte[] imageData) {
        return ocrProvider.recognize(new OcrImage.Bytes(imageData), OcrOptions.builder().build());
    }

    /** Pattern 3: Explicit AUTO sentinel = same as global default (more explicit). */
    public OcrResult recognizeExplicitAuto(byte[] imageData) {
        return ocrProvider.recognize(
                new OcrImage.Bytes(imageData),
                OcrOptions.builder().languages(Languages.AUTO).build()
        );
    }

    /** Exception handling — map to your domain exceptions. */
    public OcrResult recognizeSafe(byte[] imageData) {
        try {
            return recognizeInvoice(imageData);
        } catch (OcrException.ProviderUnavailable e) {
            // HTTP 5xx / network failure — retry with backoff
            log.warn("Provider unavailable: {}", e.getMessage());
            throw new RetryableException("OCR temporarily unavailable", e);
        } catch (OcrException.Unrecognized e) {
            // Vendor returned error_code != 0 — image format issue
            log.error("OCR rejected: {}", e.getMessage());
            throw new BusinessException("Image format not recognized", e);
        } catch (OcrException.ImageTooLargeForSync e) {
            // PaddleOCR-VL rejects images > 1 MB on the sync path
            log.warn("Image too large for sync OCR: {}", e.getMessage());
            throw new BusinessException("Please supply a smaller image", e);
        }
    }
}
```

---

## 🌐 Multi-Language

### Supported Languages

16 `Languages` enum constants covering CJK + Western + Middle Eastern + South/Southeast Asian:

| Enum                          | BCP-47 / ISO 639-1 | Vendor code (Tesseract) |
|-------------------------------|--------------------|-------------------------|
| `AUTO`                        | (sentinel)         | —                       |
| `CHINESE_SIMPLIFIED_AND_ENGLISH` | zh-CN, en         | `chi_sim+eng`           |
| `CHINESE_SIMPLIFIED`          | zh-CN              | `chi_sim`                |
| `ENGLISH`                     | en                 | `eng`                    |
| `CHINESE_TRADITIONAL`         | zh-TW              | `chi_tra`                |
| `JAPANESE`                    | ja                 | `jpn`                    |
| `KOREAN`                      | ko                 | `kor`                    |
| `DIGITS_ONLY`                 | en                 | `eng`                    |
| `LATIN`                       | en, fr, de, es, it | `eng`                    |
| `ARABIC`                      | ar                 | `ara`                    |
| `RUSSIAN`                     | ru                 | `rus`                    |
| `HINDI`                       | hi                 | `hin`                    |
| `THAI`                        | th                 | `tha`                    |
| `VIETNAMESE`                  | vi                 | `vie`                    |
| `GREEK`                       | el                 | `ell`                    |
| `TURKISH`                     | tr                 | `tur`                    |

### Per-Call Override vs Global Default

Resolution chain when `provider.recognize(image, options)` is called:

```
1. options.languages() not null and not AUTO?  → use it directly (per-call override)
2. else?  → resolve to OcrProperties.defaultLanguages
3. defaultLanguages empty?  → fallback to {CHINESE_SIMPLIFIED_AND_ENGLISH}
```

### Vendor Multi-Language Mapping

| Vendor       | Multi-lang support | Mapping strategy                                  |
|--------------|---------------------|---------------------------------------------------|
| Tesseract    | ✅ Native            | Join with `+` — `languages(JAPANESE, ENGLISH)` → `-l jpn+eng` |
| Baidu        | ❌ Single value      | Take first language; rest ignored                  |
| Aliyun       | ❌ Implicit          | `vendor.model` field (`standard-form` auto-detect) |
| Tencent      | ❌ Single value      | Take first language; rest ignored                  |
| Volcano      | ❌ Single value      | Take first language; rest ignored                  |
| Paddle       | ❌ Single value      | Take first                                        |
| PaddleOCR-VL | ❌ Single value      | Take first                                        |
| MinerU       | N/A                 | PDF structured; no language parameter             |

---

## 🎯 Vendor Selection Guide

How to choose among the 8 OCR backends. The decision is mostly operational (data compliance / cost / SLA), not technical — pick the one that fits your deployment context, not the one with the highest accuracy on benchmarks.

### Quick Decision Matrix

| If your situation is... | Recommended vendor | Why |
|---|---|---|
| 快速上线, 无 GPU 资源, 预算可接受, 数据可出公网 | **Aliyun** / **Baidu** / **Tencent** / **Volcano** | 云服务, 按次计费, 5 分钟接入, 中英识别精度高 |
| 数据合规要求高, 不可出公网 (金融/政务) | **Paddle** 或 **Tesseract** | 私有部署, 数据不出内网, Tesseract 适合纯英文, Paddle 适合多语 |
| 需要识别复杂版式 (表格/公式/手写) | **PaddleOCR-VL** 或 **MinerU** | VLM 路线, 精度显著高于传统 OCR, 但需 GPU (内部阻塞轮询, 调用线程会等待) |
| PDF 票据/合同结构化, 输出 Markdown/Docx | **MinerU** | PDF 结构化专家, 输出格式原生支持 |
| 腾讯云 / 火山引擎生态, 希望统一鉴权体系 | **Tencent** / **Volcano** | 复用现有 SecretId / AccessKey, 无额外 SDK 依赖 |
| 高精度中文识别 (99%+) | **Tencent** (GeneralAccurateOCR) | 腾讯云高精度版, 中文识别率 99%+ |
| 多语种混合文档 (中英日韩等) | **Tesseract** (本地, 自由组合) 或 **Aliyun** (云) | Tesseract 原生支持 `+` 拼接; Aliyun 通过 `model` 字段自动检测 |
| GPU 资源充足, 追求识别质量上限 | **PaddleOCR-VL** | VLM 综合能力最强, 但需 GPU (同步轮询, 配置合适 timeout) |

### Detailed Trade-offs

| Vendor | Type | Strengths | Weaknesses | When NOT to choose |
|---|---|---|---|---|
| **Aliyun** | Cloud | 中文识别最准, 阿里云市场版 APPCODE 简单, SLA 稳定 | 数据出公网, 按次计费, 单 QPS 有上限 | 文档涉密 / 成本敏感 / 高并发 |
| **Baidu** | Cloud | 通用 OCR 精度稳定, OAuth2 access_token 机制成熟 | 数据出公网, 中文通用识别略弱于阿里云 | 同上 |
| **Tencent** | Cloud | 高精度版 99%+ 中文识别; 双模式切换 (Accurate/Basic); TC3-HMAC-SHA256 签名无 SDK 依赖 | 数据出公网; 仅基础/高精度模式, 无 VLM | 需本地部署 / 离线场景 |
| **Volcano** | Cloud | AWS4 签名兼容, 字节跳动内网低延迟, 火山引擎生态 | 数据出公网; 功能相对基础 | 非火山生态 / 需专用 OCR 功能 |
| **Paddle** | Local CPU | 自部署, 多语种 (ch/en/japan/korean), Python 生态易扩展 | 需要 Python + 模型文件 ~200MB, 启动慢 (5s) | 需实时启动 / 无 Python 环境 |
| **Tesseract** | Local CPU | 极轻量 (无 ML 依赖), 多语种原生拼接, 启动快 | 中文识别精度明显弱于云, 表格识别需结合其他方案 | 复杂中文版式 / 高精度场景 |
| **PaddleOCR-VL** | Local GPU VLM | VLM 综合能力最强, 复杂版式/手写/公式识别 | 需 GPU, 内部同步轮询长时间占用调用线程, 图片 > 1 MB 直接抛 `ImageTooLargeForSync` | 无 GPU / 超大图 / 严格实时性要求 (无法拆任务) |
| **MinerU** | Local GPU PDF | PDF 结构化专家, 输出 Markdown/Docx, 学术/法律文档首选 | 需 GPU, 内部同步轮询长时间占用调用线程, 仅 PDF | 非 PDF 场景 / 无 GPU |

### Recommendation by Document Type

| Document type | First choice | Fallback |
|---|---|---|
| 中文票据 (增值税发票/行程单) | Aliyun / Tencent (高精度版) | Baidu / Volcano (fallback) |
| 英文合同/技术文档 | Tesseract (本地, 离线) | Aliyun / Tencent (云, 高精度) |
| 混合语种文档 (中英日) | Aliyun / Tencent (云) / Tesseract (本地) | — |
| 扫描版 PDF (图像型) | MinerU (云 GPU, 结构化) | PaddleOCR-VL (其他 VLM) |
| 手写笔记/草稿 | PaddleOCR-VL (VLM 手写强) | Aliyun (云手写模型) |
| 学术论文/法律合同 (结构化) | MinerU | Paddle (本地) |
| 腾讯云 / 火山引擎生态集成 | Tencent / Volcano (复用现有凭据) | Aliyun / Baidu (跨云) |

### Migration Path

A typical migration journey as requirements evolve:

```
Stage 1: MVP 快速验证
   ↓ 引 Aliyun / Baidu / Tencent / Volcano (5 分钟接入, 按次付费)
Stage 2: 用户增长, 成本上升
   ↓ 切到 Paddle / Tesseract (本地部署, 零边际成本)
Stage 3: 合规要求 (数据出公网限制)
   ↓ 已经在 Stage 2 本地化, 只需完善配置
Stage 4: 复杂版式/手写场景
   ↓ 加 PaddleOCR-VL / MinerU (GPU 节点, VLM 精度)
```

每个 Stage 切换 vendor 仅需改 yml (`vendor` 字段) + 改 vendor 私有段配置 + 改业务类字段类型 (`AliyunOcrProvider` → `TencentOcrProvider` 等), **vendor SPI 调用 0 改动**。

### 🖥️ Local Deployment & Server Configuration Guide

Four local OCR models are supported, each with different hardware requirements. Choose your setup based on expected TPS (transactions per second).

#### Local Model Overview

| Model | Type | Language Coverage | Accuracy | Startup | Model Size |
|---|---|---|---|---|---|
| **PaddleOCR** | CPU ML | 中/EN/JP/KR (80+ languages) | ⭐⭐⭐☆ | ~5s (Python + model) | ~200 MB |
| **Tesseract** | CPU CLI | 100+ languages via `+` join | ⭐⭐☆☆ | ~200 ms (no ML) | ~15 MB |
| **PaddleOCR-VL** | GPU VLM | 中/EN/JP/KR + handwritten + formula | ⭐⭐⭐⭐ | ~10s (GPU init) | ~2 GB |
| **MinerU** | GPU PDF | N/A (PDF structure) | ⭐⭐⭐⭐ | ~8s (GPU init) | ~3 GB |

#### Server Configuration by TPS Tier

| TPS Tier | Use Case | Model | vCPU | RAM | GPU | Storage | Est. Monthly Cost (CN) |
|---|---|---|---|---|---|---|---|
| **< 1 TPS** | Dev/test, low-volume QA | Tesseract | 2 cores | 4 GB | None | 20 GB (SSD) | ~¥200 (ECS共享型) |
| | | PaddleOCR | 4 cores | 8 GB | None | 50 GB (SSD) | ~¥400 (ECS通用型) |
| **1-10 TPS** | Light production, internal tools | PaddleOCR | 8 cores | 16 GB | None | 100 GB (SSD) | ~¥800 (ECS通用型) |
| | | Tesseract | 4 cores | 8 GB | None | 50 GB (SSD) | ~¥400 (ECS共享型) |
| | | PaddleOCR-VL | 8 cores | 32 GB | 1× T4 / RTX 3060 (16 GB) | 200 GB (SSD) | ~¥2,500 (GPU云主机) |
| **10-50 TPS** | Medium production, multi-language high load | PaddleOCR | 16 cores | 32 GB | None (multi-worker) | 200 GB (SSD) | ~¥1,600 (ECS通用型 ×2) |
| | | PaddleOCR-VL | 16 cores | 64 GB | 1× A10 / RTX 4090 (24 GB) | 200 GB (SSD) | ~¥5,000 (GPU云主机) |
| **50+ TPS** | High production, real-time batch | PaddleOCR-VL | 32+ cores | 128 GB | 2× A10 / A100 (80 GB) | 500 GB (SSD) | ~¥15,000 (GPU集群) |
| | | MinerU (PDF) | 32+ cores | 128 GB | 2× A10 / A100 | 1 TB (SSD) | ~¥18,000 (GPU集群) |

#### Configuration Examples by Deployment Scale

**Development / < 1 TPS — single VM with PaddleOCR:**
```yaml
platform:
  component:
    ocr:
      vendor: paddle
      paddle:
        model-dir: /opt/paddle-ocr/models
        python-path: python3
        timeout-ms: 60000
```

**Light Production / 1-10 TPS — GPU node with PaddleOCR-VL:**
```yaml
platform:
  component:
    ocr:
      vendor: paddle-vl
      paddle-vl:
        grpc-endpoint: localhost:50051
        gpu-pool: 2
        timeout-ms: 120000
```

**High Throughput / 10+ TPS — multi-worker PaddleOCR + load balancer:**
```yaml
platform:
  component:
    ocr:
      vendor: paddle
      paddle:
        model-dir: /opt/paddle-ocr/models
        python-path: python3
        # Deploy 2+ PaddleOCR workers behind a reverse proxy
        timeout-ms: 120000
```

#### Key Tuning Parameters

| Parameter | Effect | Recommended Range |
|---|---|---|
| `timeout-ms` | Max wait per request; PaddleOCR-VL / MinerU block the calling thread until completion or timeout | 30_000 (CPU) / 120_000 (GPU) / 180_000 (MinerU PDF) |
| CPU `model-dir` (Paddle) | Path to pre-downloaded model; avoids download delay on each startup | Local SSD path, not NFS |
| `python-path` | Python interpreter with `paddlepaddle` installed | Use conda venv, not system python |
| GPU `gpu-pool` | Concurrent GPU workers; one per GPU card recommended | 1-4 per host |
| OMP threads (Tesseract) | CPU parallelism for single-image OCR | 2-4 (beyond that yields diminishing returns) |

#### Recommended Cloud Instance Types (China Region)

| Scale | Alibaba Cloud | Tencent Cloud | Volcano Engine |
|---|---|---|---|
| CPU < 1 TPS | ecs.e-c1m2.large | S5.SMALL2 | ecs.g3.large |
| CPU 1-10 TPS | ecs.g7.xlarge | S5.LARGE8 | ecs.g3.4xlarge |
| GPU 1-10 TPS | ecs.gp1.xlarge (T4) | GN7.LARGE (T4) | ecs.g1.xlarge (T4) |
| GPU 10-50 TPS | ecs.gp1.4xlarge (A10) | GN10.4XLARGE (A10) | ecs.g1.4xlarge (A10) |
| GPU 50+ TPS | ecs.gp1.8xlarge (A100) | GN10.8XLARGE (A100) | ecs.g1.8xlarge (A100) |

> **Cost advice**: For CPU OCR (Paddle/Tesseract), spot/preemptible instances save 60-80%. For GPU OCR, reserved instances with 1-year commitment save ~40%. Always benchmark with your workload before scaling.

### When You Need Multiple Vendors (Special Case)

大多数场景一个 vendor 足够。组件本身是 L2 单 vendor, 不提供 runtime routing。如果确实需要多 vendor:

| Scenario | Approach |
|---|---|
| 不同文档类型走不同 vendor (如票据走 Aliyun, 扫描 PDF 走 MinerU) | 业务代码按类型分支, 各自 `@Autowired` 一个具体 Provider (每个 vendor 拆自己的 Spring profile / 独立子模块加载) |
| 主备 fallback (主 vendor 挂了切备用) | Spring `@Primary` / `@Qualifier` 组合两个 `@Bean`, 业务侧捕获 `ProviderUnavailable` 后重试备用 |
| 灰度迁移 (Aliyun → Paddle) | 双 profile 部署两套实例, 网关层按租户 / 版本路由 |

**不建议** 业务代码里硬编码 `if (type == X) return vendorX.recognize(...)` 又同时依赖 "运行时 hot-swap vendor" — L2 单 vendor 模型不预设这种用法, 越界改 vendor 会让 `OcrProvider` 字段类型漂移。

---

## 🔌 Optional Extensions

Extensions live under `ocr-extension/` aggregator. Each is **optional** — if you don't import it, the corresponding feature is not active, with zero overhead.

### `ocr-extension-micrometer` — Health Metrics

**When to use**: Business side already has Prometheus / Grafana. Want OCR call latency / outcome auto-exposed as metrics.

**Effect after import**:
- Per-vendor Timer / Counter metrics auto-bound: `ocr.call.latency`, `ocr.call.success`, `ocr.call.failure` (tags: `vendor`, `name`)
- Zero yaml config — `@ConditionalOnBean(MeterRegistry.class)` detects existing global registry

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-ocr-extension-micrometer</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### `ocr-extension-otel` — OpenTelemetry Tracing

**When to use**: Business side has Jaeger / Tempo / Zipkin. Want OCR calls traced end-to-end.

**Effect after import**:
- One span per `recognize()` call, attributes include `vendor` / `name` / result length / latency
- Zero yaml config — `@ConditionalOnBean(Tracer.class)` detects existing global tracer
- **GraalVM friendly**: no javaagent, no reflection, no bytebuddy — pure OTel API usage

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-ocr-extension-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>
```

---

## ⚙️ Configuration Reference

| Config prefix                                     | Default                    | Description                                          |
|---------------------------------------------------|----------------------------|------------------------------------------------------|
| `platform.component.ocr.enabled`                  | `true`                     | Master switch; `false` skips all OCR `@Bean` registration |
| `platform.component.ocr.vendor`                   | (none)                     | Active vendor; triggers vendor autoconfig activation |
| `platform.component.ocr.default-languages`        | `[CHINESE_SIMPLIFIED_AND_ENGLISH]` | Global default language set                  |
| `platform.component.ocr.aliyun.*`                 | vendor-specific            | endpoint / timeout-ms / credentials.app-code / vendor.model |
| `platform.component.ocr.baidu.*`                  | vendor-specific            | api-key / secret-key / endpoint / timeout-ms        |
| `platform.component.ocr.tencent.*`                | vendor-specific            | region / action / credentials.secret-id / credentials.secret-key / timeout-ms |
| `platform.component.ocr.volcano.*`                | vendor-specific            | region / credentials.access-key / credentials.secret-key / timeout-ms |
| `platform.component.ocr.paddle.*`                 | vendor-specific            | model-dir (required) / python-path / timeout-ms      |
| `platform.component.ocr.tesseract.*`              | vendor-specific            | tessdata-path (required) / languages / timeout-ms   |
| `platform.component.ocr.paddle-vl.*`              | vendor-specific            | grpc-endpoint / gpu-pool / timeout-ms                |
| `platform.component.ocr.mineru.*`                 | vendor-specific            | endpoint / api-key / timeout-ms                      |

> See [`ocr-core/src/main/resources/META-INF/ocr/application-ocr-example.yml`](ocr-core/src/main/resources/META-INF/ocr/application-ocr-example.yml) for a complete example covering all 8 vendors.

---

## ⚠️ Exception Reference

| Exception                          | Trigger                                                            | Suggested Action                                |
|------------------------------------|--------------------------------------------------------------------|-------------------------------------------------|
| `OcrException.ConfigMissing`       | Vendor `load()` finds required config key missing                   | Add the missing yaml key                       |
| `OcrException.ProviderUnavailable` | HTTP 5xx / connection failure / generic exception wrapped         | Retry with fallback / check network             |
| `OcrException.Unrecognized`        | HTTP 2xx but vendor business `error_code != 0`                     | Check image format / vendor docs                |
| `OcrException.SidecarUnavailable`  | CLI / subprocess / sidecar unreachable / IOException                | Install runtime dependency / check endpoint      |
| `OcrException.VlmTimeout`          | PaddleOCR-VL / MinerU internal polling exceeds configured `timeout-ms` | Increase timeout / scale up GPU                |
| `OcrException.VlmOutOfMemory`      | VLM GPU OOM                                                        | Reduce batch size / scale up GPU                |
| `OcrException.ImageTooLargeForSync` | PaddleOCR-VL sync call with image > `MAX_SYNC_BYTES` (1 MB)          | Resize image before calling / switch vendor    |
| `OcrException.NoAvailableProvider` | No concrete `OcrProvider` bean matched `platform.component.ocr.vendor` | Add the right vendor module / fix yaml vendor value |

```java
try {
    OcrResult r = ocrProvider.recognize(image, options);
} catch (OcrException.ProviderUnavailable e) {
    log.warn("OCR provider unavailable, status={}", e.httpStatus(), e);
} catch (OcrException.NoAvailableProvider e) {
    log.error("No OCR provider available: {}", e.getMessage());
}
```

---

## 🎯 Best Practices

1. **Pick the right vendor for your scale and SLA**:
   - Cloud (Aliyun / Baidu / Tencent / Volcano): pay-per-call, managed, fast onboarding
   - Local CPU (Paddle / Tesseract): zero cost, offline, lower accuracy
   - Local GPU VLM (PaddleOCR-VL / MinerU): highest accuracy on complex docs, requires GPU; calling thread blocks while the vendor polls internally

2. **Use global `default-languages` for the common case; override per-call for outliers**:
   - Set `default-languages: [JAPANESE]` once in yaml
   - For one-off Chinese invoices, call `.languages(Languages.CHINESE_SIMPLIFIED)` to override

3. **Use `OcrImage.Url` with pre-signed URLs instead of base64 for large images** (where the vendor supports URL input):
   - Avoids copying bytes through JVM
   - Vendor fetches directly
   - Aliyun and Baidu both support this; PaddleOCR-VL rejects images > 1 MB on the sync path, so pre-signed URL does NOT bypass that limit

4. **Set `timeout-ms` based on the worst-case vendor polling**:
   - CPU / cloud vendors: 30 s is usually enough
   - PaddleOCR-VL: 120 s (single image, GPU inference + polling)
   - MinerU: 180 s (PDF parsing, GPU inference + polling)
   - Too short → `VlmTimeout`; too long → caller gets stuck

5. **If your handler cannot block** (e.g. servlet / reactive worker), the business side can wrap `provider.recognize(...)` with `CompletableFuture.supplyAsync(...)` to shift work to a dedicated executor. The OCR facade itself is synchronous.

6. **For PaddleOCR-VL pre-validate image size** to fail fast on `ImageTooLargeForSync` rather than waiting on a timeout:

   ```java
   if (imageData.length > 1024 * 1024 && provider instanceof PaddleVlOcrProvider) {
       throw new BusinessException("Image exceeds PaddleOCR-VL sync 1 MB limit");
   }
   ```

---

## ❓ FAQ

### Why is the API split between `engine` / `provider` / `exception` packages?

Three-layer semantic separation:
- `engine.*` — business-facing value types (`OcrImage`, `OcrOptions`, `OcrResult`, `Languages`)
- `provider.*` — SPI for vendor implementations (`OcrProvider`, `AbstractOcrProvider`)
- `exception.*` — cross-package error types

Business side imports only `engine.*` + `provider.OcrProvider`. Vendor modules import `provider.*` + `exception.*`. Sealed `OcrException` permits subclasses across all three packages, keeping the exception hierarchy unified.

### Why inject the concrete `AliyunOcrProvider` instead of a facade?

Because there is exactly one vendor per deployment. With no registry, no routing, no hot-swap, a facade would just hide the field-type that actually documents the active choice. Two practical benefits:

- The field declaration (`private final TencentOcrProvider ocrProvider;`) tells reviewers "this service is on Tencent" without reading yaml.
- When you switch vendors, the compiler surfaces the call site (`ocrProvider.recognize(...)`) so you can't accidentally keep using the old SDK by mistake.

If you really need to abstract, declare the field as the interface `OcrProvider` — both work; the concrete type just makes intent explicit.

### Can multiple vendors be active simultaneously?

No — `@ConditionalOnProperty(vendor=<name>, havingValue=...)` ensures exactly one vendor autoconfig activates. This is by design: choosing a vendor is an operational decision made before deployment (data compliance / cost / SLA), not a runtime decision.

For multi-vendor deployments (e.g. Aliyun + MinerU in the same JVM), split into separate Spring profiles / child contexts; each gets its own `platform.component.ocr.vendor` and its own concrete provider.

### How does paddle-vl / mineru handle long-running inference under a synchronous API?

Both vendors expose a single blocking `recognize()` that does NOT return until the upstream task finishes. Internally, the implementation submits the request, polls the upstream task status endpoint, and only returns once it sees a terminal state. From the caller's point of view:

- Calling thread is occupied for the full duration.
- `timeout-ms` caps the wait; if exceeded, `VlmTimeout` (or `VlmOutOfMemory`) is thrown mid-poll.
- For paddle-vl specifically, images > 1 MB fail immediately with `ImageTooLargeForSync` before any network call.

This means the servlet / worker thread that invokes `recognize()` must be one you can afford to block (or wrap with `CompletableFuture.supplyAsync(...)` on the business side, see Best Practices §5).

### Why don't `ocr-extension-micrometer` / `ocr-extension-otel` need config?

`@ConditionalOnBean(MeterRegistry.class)` / `@ConditionalOnBean(Tracer.class)` detect the business app's existing global infrastructure. If the business already runs Micrometer / OTel (which is common in modern Spring Boot apps), the OCR extension auto-binds to it with zero yaml config. If not, the extension silently no-ops.

---

**atlas-richie-component-ocr** — unified OCR facade with pluggable vendors
