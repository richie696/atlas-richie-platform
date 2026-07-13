# Atlas Richie OCR 组件 (atlas-richie-component-ocr)

> **一句话价值**: 单一 vendor 入口,屏蔽 **8 个 OCR 引擎** (阿里云 / 百度 / 腾讯云 / 火山引擎 / PaddleOCR / Tesseract / PaddleOCR-VL / MinerU) 的协议差异,内置多语言、零配置 Micrometer / OpenTelemetry 集成。每个 vendor 实现内部处理同步阻塞(对 VLM/PDF 即内部轮询),业务侧只看到一个 `recognize()` 方法。

---

## 📖 目录

- [📋 概述](#-概述)
- [✨ 核心能力](#-核心能力)
- [🏗️ 架构](#-架构)
  - [模块结构](#模块结构)
  - [包结构](#包结构)
- [🚀 快速开始](#-快速开始)
  - [1. 引入依赖](#1-引入依赖)
  - [2. 配置 application.yml](#2-配置-applicationyml)
  - [3. 注入具体 Provider 调用](#3-注入具体-provider-调用)
- [🧩 完整使用示例](#-完整使用示例)
  - [完整 `pom.xml` 片段](#完整-pomxml-片段)
  - [完整 `application.yml`](#完整-applicationyml)
  - [完整服务类 (同步 + 异常处理)](#完整服务类-同步--异常处理)
- [🌐 多语言](#-多语言)
  - [支持的语言](#支持的语言)
  - [per-call 覆盖 vs 全局默认](#per-call-覆盖-vs-全局默认)
  - [vendor 多语言映射策略](#vendor-多语言映射策略)
- [🎯 Vendor 选型指南](#-vendor-选型指南)
  - [速决矩阵](#速决矩阵)
  - [详细权衡](#详细权衡)
  - [按文档类型推荐](#按文档类型推荐)
  - [迁移路径](#迁移路径)
  - [🖥️ 本地自部署方案与服务器配置指南](#-本地自部署方案与服务器配置指南)
- [🔌 可选扩展](#-可选扩展)
  - [`ocr-extension-micrometer` — 健康指标](#ocr-extension-micrometer--健康指标)
  - [`ocr-extension-otel` — OpenTelemetry Tracing](#ocr-extension-otel--opentelemetry-tracing)
- [⚙️ 配置参考](#-配置参考)
- [⚠️ 异常参考](#-异常参考)
- [🎯 最佳实践](#-最佳实践)
- [❓ FAQ](#-faq)

---

## 📋 概述

`atlas-richie-component-ocr` 是 Atlas Richie 平台的统一 OCR 组件。业务侧只接触具体 vendor 的 Provider bean (如 `AliyunOcrProvider`),**绝不**直接依赖 vendor SDK（阿里云 SDK、百度 OAuth、Tesseract CLI、PaddleOCR Python 等）。切换 vendor 仅需改 yaml,业务代码最多需要改一个 `@Autowired` 的类型。

**底层引擎**:

| Vendor       | 类型            | 同步   | 协议                                                            |
|--------------|-----------------|--------|-----------------------------------------------------------------|
| 阿里云读光   | 云服务          | 同步   | HTTP POST `/v1/ocr/recognize` + APPCODE 鉴权                      |
| 百度 OCR     | 云服务          | 同步   | HTTP POST form `/rest/2.0/ocr/v1/general_basic` + OAuth2 token   |
| 腾讯云 OCR   | 云服务          | 同步   | HTTP POST `ocr.tencentcloudapi.com` + TC3-HMAC-SHA256 签名        |
| 火山引擎 OCR | 云服务          | 同步   | HTTP POST `ocr.volcengineapi.com` + AWS4-HMAC-SHA256 签名         |
| PaddleOCR    | 本地 CPU        | 同步   | Python subprocess `paddle_ocr.py` (classpath 脚本)               |
| Tesseract    | 本地 CPU        | 同步   | CLI subprocess `tesseract ... stdout tsv` (多语言 `+` 拼接)       |
| PaddleOCR-VL | 本地 GPU (VLM)  | 同步   | HTTP POST `/submit` base64 + 内部 GET `/tasks/{id}` 轮询         |
| MinerU       | 本地 GPU        | 同步   | multipart POST `/upload` PDF + 内部 GET `/tasks/{id}` 轮询      |

> PaddleOCR-VL 与 MinerU 虽然内部需要轮询任务状态,但全部在 `recognize()` 内同步阻塞完成调用,业务侧不感知异步细节。

**设计契约**: 每个 vendor 实现 `extends AbstractOcrProvider<REQ, RES>` 三个模板方法（`toProviderRequest` / `callProvider` / `fromProviderResponse`）。core 提供 SPI 与默认实现,vendor 模块提供具体协议封装。

---

## ✨ 核心能力

- **单一入口** — `@Autowired AliyunOcrProvider` 等具体 vendor bean,调 `provider.recognize(image, options)` 即可
- **8 vendor 实现** — yaml `platform.component.ocr.vendor=aliyun|baidu|tencent|volcano|paddle|tesseract|paddle-vl|mineru` 切换
- **统一接口** — `OcrProvider.recognize(OcrImage, OcrOptions)` 是 vendor 间唯一的调用入口
- **多语言** — 16 种 `Languages`,`Set<Languages>` per-call 多选,全局 `default-languages` 配置,`AUTO` 兜底,vendor 原生多语言映射（Tesseract 用 `+` 拼接）
- **同步阻塞** — 所有 vendor 统一同步调用。VLM/PDF 内部轮询,业务侧不感知
- **零配置可观测性** — `ocr-extension-micrometer` / `ocr-extension-otel` 自动检测业务侧全局基础设施,零 yaml 配置
- **单引擎保证** — `@ConditionalOnProperty(vendor=...)` 确保同一部署只有一个 vendor 激活
- **8 个 sealed 异常** — `ConfigMissing` / `ProviderUnavailable` / `Unrecognized` / `SidecarUnavailable` / `VlmTimeout` / `VlmOutOfMemory` / `ImageTooLargeForSync` / `NoAvailableProvider`

---

## 🏗️ 架构

### 模块结构

```
atlas-richie-component-ocr/                              (聚合 POM)
├── atlas-richie-component-ocr-core/                     (SPI + 默认实现)
├── atlas-richie-component-ocr-aliyun/                   (云: HTTP + APPCODE)
├── atlas-richie-component-ocr-baidu/                    (云: HTTP + OAuth2)
├── atlas-richie-component-ocr-tencent/                  (云: TC3-HMAC-SHA256 签名)
├── atlas-richie-component-ocr-volcano/                  (云: AWS4-HMAC-SHA256 签名)
├── atlas-richie-component-ocr-paddle/                   (本地 CPU: Python subprocess)
├── atlas-richie-component-ocr-tesseract/                (本地 CPU: CLI subprocess)
├── atlas-richie-component-ocr-paddle-vl/                (本地 GPU: VLM 内部轮询)
├── atlas-richie-component-ocr-mineru/                   (本地 GPU: PDF 内部轮询)
└── atlas-richie-component-ocr-extension/                (可选扩展聚合)
    ├── atlas-richie-component-ocr-extension-micrometer/ (健康指标)
    └── atlas-richie-component-ocr-extension-otel/       (OpenTelemetry Tracing)
```

### 包结构

`ocr-core` 内部按语义分包：

```
com.richie.component.ocr/
├── engine/             # 业务侧直接使用
│   ├── OcrImage                            # sealed (Bytes / Url / Stream)
│   ├── OcrOptions / Languages               # Builder + 16 语言枚举
│   ├── OcrResult / OcrBlock / OcrLine / Point
│   ├── MimeType / HttpAuth
│
├── provider/           # vendor SPI 接口
│   ├── OcrProvider                          # vendor 实现接口
│   ├── AbstractOcrProvider<REQ, RES>        # 模板方法基类
│   ├── ProviderHealth / HealthState / ProviderType / DeploymentModel / TrustLevel
│
└── exception/          # 跨包错误类型
    └── OcrException sealed + 8 子类
```

---

## 🚀 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-ocr-core</artifactId>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-ocr-aliyun</artifactId>  <!-- 或任意 vendor -->
</dependency>
```

### 2. 配置 application.yml

```yaml
platform:
  component:
    ocr:
      enabled: true                              # 主开关, 默认 true
      vendor: aliyun                             # aliyun | baidu | tencent | volcano | paddle | tesseract | paddle-vl | mineru
      default-languages:                         # 全局默认语言 (可选)
        - CHINESE_SIMPLIFIED_AND_ENGLISH
      aliyun:                                    # vendor 私有配置
        endpoint: https://ocr-api.cn-shanghai.aliyuncs.com
        credentials:
          app-code: ${ALIYUN_APP_CODE:}
        timeout-ms: 30000
        vendor.model: standard-form
```

### 3. 注入具体 Provider 调用

```java
@Service
@RequiredArgsConstructor
public class OcrBizService {

    private final AliyunOcrProvider ocrProvider;  // 注入具体 vendor bean

    public OcrResult recognize(byte[] imageData) {
        OcrOptions opts = OcrOptions.builder()
                .languages(Languages.CHINESE_SIMPLIFIED)              // per-call 单语言
                .tableRecognition(true)
                .build();
        return ocrProvider.recognize(new OcrImage.Bytes(imageData, MimeType.PNG), opts);
    }

    public OcrResult recognizeMultiLang(byte[] imageData) {
        // 多语言: Tesseract 拼 jpn+eng; Baidu/Paddle 取首个
        OcrOptions opts = OcrOptions.builder()
                .languages(Languages.JAPANESE, Languages.ENGLISH)
                .build();
        return ocrProvider.recognize(new OcrImage.Bytes(imageData), opts);
    }

    public OcrResult useGlobalDefault(byte[] imageData) {
        // 显式声明用 yml 的 default-languages
        OcrOptions opts = OcrOptions.builder()
                .languages(Languages.AUTO)
                .build();
        return ocrProvider.recognize(new OcrImage.Bytes(imageData), opts);
    }
}
```

**切换 vendor** 仅改 yaml + 改 `@Autowired` 类型:

```yaml
# 之前: aliyun. 从 pom.xml 移除 aliyun 包.
# 之后: tencent. 加 tencent 到 pom.xml + 改 yaml.
platform:
  component:
    ocr:
      vendor: tencent
      tencent:
        credentials:
          secret-id: ${TENCENT_SECRET_ID:}
          secret-key: ${TENCENT_SECRET_KEY:}
        timeout-ms: 30000
```

```java
// 同步把 @Autowired 改成 TenCentOcrProvider(或具体 vendor 对应的类名)
private final TencentOcrProvider ocrProvider;
```

---

## 🧩 完整使用示例

一个端到端场景: 票据 OCR 服务 + 异常处理。Copy-paste 即可运行。

### 完整 `pom.xml` 片段

```xml
<dependencies>
    <!-- ===== Core (始终需要) ===== -->
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-core</artifactId>
    </dependency>

    <!-- ===== 选一个 vendor ===== -->
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-aliyun</artifactId>
    </dependency>
    <!-- 备选 (取消注释即切换):
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

    <!-- ===== 可选扩展 ===== -->
    <!-- Micrometer 健康指标 (自动检测已有 MeterRegistry) -->
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-extension-micrometer</artifactId>
    </dependency>
    <!-- OpenTelemetry tracing (自动检测已有 Tracer, GraalVM 友好) -->
    <dependency>
        <groupId>com.richie.component</groupId>
        <artifactId>atlas-richie-component-ocr-extension-otel</artifactId>
    </dependency>
</dependencies>
```

### 完整 `application.yml`

```yaml
platform:
  component:
    ocr:
      # ===== 主开关 =====
      enabled: true

      # ===== 激活的 vendor (每个 @ConditionalOnProperty 只激活一个) =====
      vendor: aliyun

      # ===== 全局默认语言 (options.languages 为 null 或 AUTO 时使用) =====
      default-languages:
        - CHINESE_SIMPLIFIED_AND_ENGLISH
        # - JAPANESE    # 取消注释作为 fallback

      # ===== 阿里云 (当前激活) =====
      aliyun:
        endpoint: https://ocr-api.cn-shanghai.aliyuncs.com
        timeout-ms: 30000
        credentials:
          app-code: ${ALIYUN_APP_CODE:}            # 阿里云市场 APPCODE
        vendor.model: standard-form                # standard-form | standard-ocr | advanced-ocr

      # ===== 备选 vendor 配置 (切换时取消注释并填写) =====
      # baidu:
      #   api-key: ${BAIDU_API_KEY:}
      #   secret-key: ${BAIDU_SECRET_KEY:}
      #   endpoint: https://aip.baidubce.com
      #   timeout-ms: 30000
      #
      # tencent:
      #   endpoint: https://ocr.tencentcloudapi.com
      #   region: ap-guangzhou
      #   action: GeneralAccurateOCR        # GeneralAccurateOCR | GeneralBasicOCR
      #   credentials:
      #     secret-id: ${TENCENT_SECRET_ID:}
      #     secret-key: ${TENCENT_SECRET_KEY:}
      #   timeout-ms: 30000
      #
      # volcano:
      #   endpoint: https://ocr.volcengineapi.com
      #   region: cn-north-1
      #   credentials:
      #     access-key: ${VOLCANO_ACCESS_KEY:}
      #     secret-key: ${VOLCANO_SECRET_KEY:}
      #   timeout-ms: 30000
      #
      # paddle:
      #   model-dir: /opt/paddle-ocr/models          # 必填
      #   python-path: python3
      #   timeout-ms: 60000
      #
      # tesseract:
      #   tessdata-path: /usr/share/tesseract-ocr/4.00/tessdata  # 必填
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

# ===== 可选: 暴露 actuator/prometheus 端点 =====
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus

# ===== 可选: OTel OTLP 导出 =====
otel:
  exporter:
    otlp:
      endpoint: ${OTLP_ENDPOINT:http://localhost:4317}
```

### 完整服务类 (同步 + 异常处理)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceOcrService {

    private final AliyunOcrProvider ocrProvider;  // 注入具体 vendor bean

    /** 同步识别 + 单语言 + 表格识别 (最常用). */
    public OcrResult recognizeInvoice(byte[] imageData) {
        OcrOptions opts = OcrOptions.builder()
                .languages(Languages.CHINESE_SIMPLIFIED)
                .tableRecognition(true)                          // 票据有表格
                .confidenceThreshold(0.7f)
                .build();
        return ocrProvider.recognize(new OcrImage.Bytes(imageData, MimeType.PNG), opts);
    }

    /** 预签名 URL 模式: vendor 直接拉取, 字节不经过 JVM. */
    public OcrResult recognizeByUrl(String presignedUrl) {
        OcrOptions opts = OcrOptions.builder()
                .languages(Languages.JAPANESE, Languages.ENGLISH)
                .tableRecognition(true)
                .build();
        return ocrProvider.recognize(new OcrImage.Url(presignedUrl, null), opts);
    }

    /** 使用 yml 全局默认语言 (最简洁, 不传 language). */
    public OcrResult recognizeWithGlobalDefault(byte[] imageData) {
        return ocrProvider.recognize(new OcrImage.Bytes(imageData), OcrOptions.builder().build());
    }

    /** 显式 AUTO 哨兵 = 等同全局默认 (更显式). */
    public OcrResult recognizeExplicitAuto(byte[] imageData) {
        return ocrProvider.recognize(
                new OcrImage.Bytes(imageData),
                OcrOptions.builder().languages(Languages.AUTO).build()
        );
    }

    /** 异常处理 —— 映射到业务域异常. */
    public OcrResult recognizeSafe(byte[] imageData) {
        try {
            return recognizeInvoice(imageData);
        } catch (OcrException.ProviderUnavailable e) {
            // HTTP 5xx / 网络失败 —— 带退避重试
            log.warn("Provider unavailable: {}", e.getMessage());
            throw new RetryableException("OCR temporarily unavailable", e);
        } catch (OcrException.Unrecognized e) {
            // vendor 返回 error_code != 0 —— 图像格式问题
            log.error("OCR rejected: {}", e.getMessage());
            throw new BusinessException("Image format not recognized", e);
        } catch (OcrException.ConfigMissing e) {
            // vendor 必填配置缺失
            log.error("OCR config missing: {}", e.getMessage());
            throw new BusinessException("OCR config invalid", e);
        }
    }
}
```

> 业务侧如需异步,可自行封装 `CompletableFuture.supplyAsync(() -> ocrProvider.recognize(image, opts))`,或结合 `atlas-richie-component-concurrency` 使用结构化并发。组件不强制绑定异步范式。

---

## 🌐 多语言

### 支持的语言

16 个 `Languages` 枚举常量,覆盖 CJK + 西方 + 中东 + 南亚 / 东南亚：

| 枚举                            | BCP-47 / ISO 639-1 | Tesseract vendor code |
|-------------------------------|--------------------|------------------------|
| `AUTO`                        | (sentinel)         | —                      |
| `CHINESE_SIMPLIFIED_AND_ENGLISH` | zh-CN, en         | `chi_sim+eng`          |
| `CHINESE_SIMPLIFIED`          | zh-CN              | `chi_sim`               |
| `ENGLISH`                     | en                 | `eng`                   |
| `CHINESE_TRADITIONAL`         | zh-TW              | `chi_tra`               |
| `JAPANESE`                    | ja                 | `jpn`                   |
| `KOREAN`                      | ko                 | `kor`                   |
| `DIGITS_ONLY`                 | en                 | `eng`                   |
| `LATIN`                       | en, fr, de, es, it | `eng`                   |
| `ARABIC`                      | ar                 | `ara`                   |
| `RUSSIAN`                     | ru                 | `rus`                   |
| `HINDI`                       | hi                 | `hin`                   |
| `THAI`                        | th                 | `tha`                   |
| `VIETNAMESE`                  | vi                 | `vie`                   |
| `GREEK`                       | el                 | `ell`                   |
| `TURKISH`                     | tr                 | `tur`                   |

### per-call 覆盖 vs 全局默认

`provider.recognize(image, options)` 调用时的解析链：

```
1. options.languages() 非 null 且非 AUTO?  → 直接用 (per-call 覆盖)
2. 否则?  → 解析为 OcrProperties.defaultLanguages
3. defaultLanguages 为空?  → fallback 到 {CHINESE_SIMPLIFIED_AND_ENGLISH}
```

### vendor 多语言映射策略

| Vendor       | 多语言支持 | 映射策略                                  |
|--------------|------------|-------------------------------------------|
| Tesseract    | ✅ 原生    | `+` 拼接 — `languages(JAPANESE, ENGLISH)` → `-l jpn+eng` |
| Baidu        | ❌ 单值    | 取首个语言, 其余忽略                       |
| Aliyun       | ❌ 隐式    | `vendor.model` 字段 (`standard-form` 自动检测) |
| 腾讯云       | ❌ 自动检测 | action 自动适配多语, 无需显式参数           |
| 火山引擎     | ❌ 自动检测 | 内置多语识别, 无需显式语言参数               |
| Paddle       | ❌ 单值    | 取首个                                     |
| PaddleOCR-VL | ❌ 单值    | 取首个                                     |
| MinerU       | N/A        | PDF 结构化, 不涉及语言选择                  |

---

## 🎯 Vendor 选型指南

8 个 OCR 后端怎么选? 决策主要是**运营层面**（数据合规 / 成本 / SLA),不是技术层面——选适合部署场景的,不是 benchmark 分数最高的。

### 速决矩阵

| 你的场景 | 推荐 vendor | 理由 |
|---|---|---|
| 快速上线, 无 GPU 资源, 预算可接受, 数据可出公网 | **Aliyun** / **Baidu** / **腾讯云** / **火山引擎** | 云服务, 按次计费, 5 分钟接入, 中英识别精度高 |
| 数据合规要求高, 不可出公网 (金融/政务) | **Paddle** 或 **Tesseract** | 私有部署, 数据不出内网, Tesseract 适合纯英文, Paddle 适合多语 |
| 需要识别复杂版式 (表格/公式/手写) | **PaddleOCR-VL** 或 **MinerU** | VLM 路线, 精度显著高于传统 OCR, 但需 GPU |
| PDF 票据/合同结构化, 输出 Markdown/Docx | **MinerU** | PDF 结构化专家, 输出格式原生支持 |
| 多语种混合文档 (中英日韩等) | **Tesseract** (本地, 自由组合) 或 **Aliyun** (云) | Tesseract 原生支持 `+` 拼接; Aliyun 通过 `model` 字段自动检测 |
| GPU 资源充足, 追求识别质量上限 | **PaddleOCR-VL** | VLM 综合能力最强, 但需 GPU (同步阻塞, 可能较慢) |
| 腾讯云/火山引擎已有采购合同, 资源复用 | **腾讯云** 或 **火山引擎** | 复用现有云账号, 统一发票管理, 无需额外对接 |

### 详细权衡

| Vendor | 类型 | 强项 | 弱项 | 不要选的情况 |
|---|---|---|---|---|
| **Aliyun** | 云 | 中文识别最准, 阿里云市场版 APPCODE 简单, SLA 稳定 | 数据出公网, 按次计费, 单 QPS 有上限 | 文档涉密 / 成本敏感 / 高并发 |
| **Baidu** | 云 | 通用 OCR 精度稳定, OAuth2 access_token 机制成熟 | 数据出公网, 中文通用识别略弱于阿里云 | 同上 |
| **腾讯云** | 云 | 高精度版 99% 准确率, TC3 签名安全, 双模式切换 | 数据出公网, 按次计费, 需 SecretId/SecretKey 密钥管理 | 无腾讯云账号 / 需私有部署 |
| **火山引擎** | 云 | AWS4 签名兼容, 火山引擎生态整合, 多区域部署 | 数据出公网, 按次计费, 国内区域略少 | 无火山引擎账号 / 需私有部署 |
| **Paddle** | 本地 CPU | 自部署, 多语种 (ch/en/japan/korean), Python 生态易扩展 | 需要 Python + 模型文件 ~200MB, 启动慢 (5s) | 需实时启动 / 无 Python 环境 |
| **Tesseract** | 本地 CPU | 极轻量 (无 ML 依赖), 多语种原生拼接, 启动快 | 中文识别精度明显弱于云, 表格识别需结合其他方案 | 复杂中文版式 / 高精度场景 |
| **PaddleOCR-VL** | 本地 GPU VLM | VLM 综合能力最强, 复杂版式/手写/公式识别 | 需 GPU, 同步调用耗时长, 部署复杂度高 | 无 GPU / 实时性要求高 |
| **MinerU** | 本地 GPU PDF | PDF 结构化专家, 输出 Markdown/Docx, 学术/法律文档首选 | 需 GPU, 同步调用耗时长, 仅 PDF | 非 PDF 场景 / 无 GPU |

### 按文档类型推荐

| 文档类型 | 首选 | 备选 |
|---|---|---|
| 中文票据 (增值税发票/行程单) | Aliyun / 腾讯云 | Baidu / 火山引擎 (fallback) |
| 英文合同/技术文档 | Tesseract (本地, 离线) | 腾讯云 / Aliyun (云, 高精度) |
| 混合语种文档 (中英日) | 腾讯云 (高精度版) / Tesseract (本地) | 火山引擎 (fallback) |
| 扫描版 PDF (图像型) | MinerU (云 GPU, 结构化) | PaddleOCR-VL (其他 VLM) |
| 手写笔记/草稿 | PaddleOCR-VL (VLM 手写强) | 腾讯云 (高精度版) |
| 学术论文/法律合同 (结构化) | MinerU | Paddle (本地) |
| 已有腾讯云/火山引擎采购合同 | 腾讯云 / 火山引擎 (复用账号) | Aliyun (fallback) |

### 迁移路径

典型的迁移路径随需求演进：

```
Stage 1: MVP 快速验证
   ↓ 引 Aliyun / Baidu / 腾讯云 / 火山引擎 (5 分钟接入, 按次付费)
Stage 2: 用户增长, 成本上升
   ↓ 切到 Paddle / Tesseract (本地部署, 零边际成本)
Stage 3: 合规要求 (数据出公网限制)
   ↓ 已经在 Stage 2 本地化, 只需完善配置
Stage 4: 复杂版式/手写场景
   ↓ 加 PaddleOCR-VL / MinerU (GPU 节点, VLM 精度)
Stage 5: 云资源统一管理
   ↓ 复用腾讯云/火山引擎账号, 走已有采购合同和发票
```

每个 Stage 切换 vendor 仅需改 yml (vendor 字段) + 改 vendor 私有段配置, **业务代码只需调整 `@Autowired` 类型**。

### 🖥️ 本地自部署方案与服务器配置指南

组件支持 4 种本地方案, 各有不同的硬件需求。根据预期 TPS (每秒处理数) 选择合适的配置。

#### 本地方案一览

| 模型 | 类型 | 语种覆盖 | 精度 | 启动时间 | 模型大小 |
|---|---|---|---|---|---|
| **PaddleOCR** | CPU ML | 中/EN/JP/KR (80+ 语言) | ⭐⭐⭐☆ | ~5s (Python + 模型加载) | ~200 MB |
| **Tesseract** | CPU CLI | 100+ 语言通过 `+` 拼接 | ⭐⭐☆☆ | ~200 ms (无 ML) | ~15 MB |
| **PaddleOCR-VL** | GPU VLM | 中/EN/JP/KR + 手写 + 公式 | ⭐⭐⭐⭐ | ~10s (GPU 初始化) | ~2 GB |
| **MinerU** | GPU PDF | N/A (PDF 结构化) | ⭐⭐⭐⭐ | ~8s (GPU 初始化) | ~3 GB |

#### 按 TPS 分档推荐配置

| TPS 档次 | 适用场景 | 推荐方案 | vCPU | 内存 | GPU | 存储 | 参考月成本 (国内) |
|---|---|---|---|---|---|---|---|
| **< 1 TPS** | 开发测试、低量质检 | Tesseract | 2 核 | 4 GB | 无 | 20 GB (SSD) | ~¥200 (ECS共享型) |
| | | PaddleOCR | 4 核 | 8 GB | 无 | 50 GB (SSD) | ~¥400 (ECS通用型) |
| **1-10 TPS** | 轻量生产、内部工具 | PaddleOCR | 8 核 | 16 GB | 无 | 100 GB (SSD) | ~¥800 (ECS通用型) |
| | | Tesseract | 4 核 | 8 GB | 无 | 50 GB (SSD) | ~¥400 (ECS共享型) |
| | | PaddleOCR-VL | 8 核 | 32 GB | 1× T4 / RTX 3060 (16 GB) | 200 GB (SSD) | ~¥2,500 (GPU云主机) |
| **10-50 TPS** | 中量生产、多语种高负载 | PaddleOCR (多 worker) | 16 核 | 32 GB | 无 | 200 GB (SSD) | ~¥1,600 (ECS通用型 ×2) |
| | | PaddleOCR-VL | 16 核 | 64 GB | 1× A10 / RTX 4090 (24 GB) | 200 GB (SSD) | ~¥5,000 (GPU云主机) |
| **50+ TPS** | 大量生产、实时批量 | PaddleOCR-VL | 32+ 核 | 128 GB | 2× A10 / A100 (80 GB) | 500 GB (SSD) | ~¥15,000 (GPU集群) |
| | | MinerU (PDF) | 32+ 核 | 128 GB | 2× A10 / A100 | 1 TB (SSD) | ~¥18,000 (GPU集群) |

#### 按部署规模的实际配置示例

**开发 / < 1 TPS — 单台 PaddleOCR:**
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

**轻量生产 / 1-10 TPS — GPU 节点 PaddleOCR-VL:**
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

**高吞吐 / 10+ TPS — 多 worker PaddleOCR + 负载均衡:**
```yaml
platform:
  component:
    ocr:
      vendor: paddle
      paddle:
        model-dir: /opt/paddle-ocr/models
        python-path: python3
        # 部署 2 个以上 PaddleOCR worker, 前端挂反向代理
        timeout-ms: 120000
```

#### 关键调优参数

| 参数 | 作用 | 推荐范围 |
|---|---|---|
| `timeout-ms` | 单次请求最大等待, 太短会导致 VLM 误判超时 | 30_000 (CPU) / 120_000 (GPU) |
| CPU `model-dir` (Paddle) | 预下载模型路径, 避免每次启动下载 | 本地 SSD, 不要用 NFS |
| `python-path` | Python 解释器 (需安装 paddlepaddle) | 用 conda 虚拟环境, 不要用系统 python |
| GPU `gpu-pool` | 并发 GPU worker, 建议 1 个 worker 对应 1 张卡 | 1-4 每台主机 |
| OMP 线程 (Tesseract) | 单图 OCR 的 CPU 并行度 | 2-4 (再高收益递减) |

#### 推荐云实例类型 (国内区域)

| 规模 | 阿里云 | 腾讯云 | 火山引擎 |
|---|---|---|---|
| CPU < 1 TPS | ecs.e-c1m2.large | S5.SMALL2 | ecs.g3.large |
| CPU 1-10 TPS | ecs.g7.xlarge | S5.LARGE8 | ecs.g3.4xlarge |
| GPU 1-10 TPS | ecs.gp1.xlarge (T4) | GN7.LARGE (T4) | ecs.g1.xlarge (T4) |
| GPU 10-50 TPS | ecs.gp1.4xlarge (A10) | GN10.4XLARGE (A10) | ecs.g1.4xlarge (A10) |
| GPU 50+ TPS | ecs.gp1.8xlarge (A100) | GN10.8XLARGE (A100) | ecs.g1.8xlarge (A100) |

> **成本建议**: CPU OCR (Paddle/Tesseract) 用竞价实例节约 60-80%; GPU OCR 用包年包月节约 ~40%。务必用实际负载做基准测试后再扩缩容。

---

## 🔌 可选扩展

所有扩展位于 `ocr-extension/` 聚合下, 每个都是**可选** —— 不引就不激活, 零开销。

### `ocr-extension-micrometer` — 健康指标

**使用场景**: 业务侧已部署 Prometheus / Grafana。希望 OCR Provider 健康指标自动上墙。

**引入后效果**:
- 每个 Provider 自动绑定 3 个 Gauge:
  - `ocr.provider.health.consecutive_failures{vendor=...,name=...}`
  - `ocr.provider.health.state{vendor=...,name=...}` (0=HEALTHY/1=DEGRADED/2=DOWN)
  - `ocr.provider.health.uptime_minutes{vendor=...,name=...}`
- 零 yaml 配置 — `@ConditionalOnBean(MeterRegistry.class)` 检测业务侧全局 Registry

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

**使用场景**: 业务侧已部署 Jaeger / Tempo / Zipkin。希望 OCR Provider 健康快照作为 OTel event 输出。

**引入后效果**:
- 启动时输出 1 个 OTel span: `ocr.providers.health.snapshot`
- 每个 Provider 1 个 event: `ocr.provider.health` (属性: name / vendor / state / consecutive_failures)
- 用 event 而非独立 span (避免污染 trace context)
- 零 yaml 配置 — `@ConditionalOnBean(Tracer.class)` 检测业务侧全局 Tracer
- **GraalVM 友好**: 零 javaagent / 零反射 / 零 bytebuddy — 纯 OTel API 使用

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

## ⚙️ 配置参考

| 配置前缀                                       | 默认值                        | 说明                                              |
|------------------------------------------------|-------------------------------|--------------------------------------------------|
| `platform.component.ocr.enabled`                | `true`                         | 主开关; `false` 时所有 OCR `@Bean` 不注册        |
| `platform.component.ocr.vendor`                 | (无)                          | 激活的 vendor; 触发对应 vendor autoconfig        |
| `platform.component.ocr.default-languages`      | `[CHINESE_SIMPLIFIED_AND_ENGLISH]` | 全局默认语言集合                              |
| `platform.component.ocr.aliyun.*`               | vendor 私有                    | endpoint / timeout-ms / credentials.app-code / vendor.model |
| `platform.component.ocr.baidu.*`                | vendor 私有                    | api-key / secret-key / endpoint / timeout-ms      |
| `platform.component.ocr.tencent.*`              | vendor 私有                    | endpoint / region / action / credentials.secret-id / credentials.secret-key / timeout-ms |
| `platform.component.ocr.volcano.*`              | vendor 私有                    | endpoint / region / credentials.access-key / credentials.secret-key / timeout-ms |
| `platform.component.ocr.paddle.*`               | vendor 私有                    | model-dir (必填) / python-path / timeout-ms       |
| `platform.component.ocr.tesseract.*`            | vendor 私有                    | tessdata-path (必填) / languages / timeout-ms    |
| `platform.component.ocr.paddle-vl.*`            | vendor 私有                    | grpc-endpoint / gpu-pool / timeout-ms             |
| `platform.component.ocr.mineru.*`               | vendor 私有                    | endpoint / api-key / timeout-ms                   |

> 完整 yaml 范例见 [`ocr-core/src/main/resources/META-INF/ocr/application-ocr-example.yml`](ocr-core/src/main/resources/META-INF/ocr/application-ocr-example.yml), 覆盖全部 8 vendor + 多语言使用示例。

---

## ⚠️ 异常参考

| 异常                              | 触发场景                                                      | 建议处理                              |
|----------------------------------|--------------------------------------------------------------|-------------------------------------|
| `OcrException.ConfigMissing`     | vendor `load()` 发现必填配置缺失                              | 补 yaml                              |
| `OcrException.ProviderUnavailable` | HTTP 5xx / 连接失败 / 通用异常包装                          | 重试 / fallback / 查网络              |
| `OcrException.Unrecognized`      | HTTP 2xx 但 vendor 业务 `error_code != 0`                    | 查图像格式 / 查 vendor 文档          |
| `OcrException.SidecarUnavailable` | CLI / subprocess / sidecar 不可达 / IOException              | 装运行时依赖 / 查 endpoint            |
| `OcrException.VlmTimeout`        | VLM 内部轮询超过配置 `timeoutMs`                            | 增大 timeout / 扩 GPU                |
| `OcrException.VlmOutOfMemory`    | VLM GPU OOM                                                    | 减小批大小 / 扩 GPU                  |
| `OcrException.ImageTooLargeForSync` | VLM 同步调用 image > `MAX_SYNC_BYTES` (1 MB)              | 压缩图片 / 拆分图像 / 改用云 vendor  |
| `OcrException.NoAvailableProvider` | vendor bean 未注册 / vendor 配置不完整                     | 补 vendor 配置 / 查 vendor 私有段     |

```java
try {
    OcrResult r = ocrProvider.recognize(image, options);
} catch (OcrException.ProviderUnavailable e) {
    log.warn("OCR provider unavailable, status={}", e.httpStatus(), e);
} catch (OcrException.ConfigMissing e) {
    log.error("OCR config missing: {}", e.getMessage());
}
```

---

## 🎯 最佳实践

1. **按规模和 SLA 选 vendor**:
   - 云（阿里云 / 百度 / 腾讯云 / 火山引擎）：按次计费, 托管, 上线快
   - 本地 CPU（Paddle / Tesseract）：零成本, 离线, 准确度较低
   - 本地 GPU VLM（PaddleOCR-VL / MinerU）：复杂文档高精度, 同步阻塞, 耗时长

2. **全局 `default-languages` 通用 + per-call 覆盖个例**:
   - yaml 设 `default-languages: [JAPANESE]`
   - 个别中文发票调用 `.languages(Languages.CHINESE_SIMPLIFIED)` 覆盖

3. **大图用 `OcrImage.Url` 预签名而不是 base64**:
   - 避免字节在 JVM 中转
   - vendor 直接 fetch
   - 阿里云 / 百度都支持

4. **直接 `@Autowired` 具体 vendor bean**:
   - 不要在业务代码手动 switch vendor, 直接注入具体 Provider
   - 切换 vendor 时改 yaml + 改 `@Autowired` 类型即可

5. **业务侧需要异步时自己封装**:
   - `CompletableFuture.supplyAsync(() -> ocrProvider.recognize(image, opts))`
   - 或结合 `atlas-richie-component-concurrency` 使用结构化并发
   - 组件不强制绑定异步范式, 同步 API 更易测试与排查

6. **超时按 vendor 类型区分**:
   - 云 vendor: 30_000 ms 通常足够
   - 本地 CPU (Paddle / Tesseract): 60_000 ms
   - 本地 GPU VLM (PaddleOCR-VL / MinerU): 120_000 ms 以上, 因内部轮询

---

## ❓ FAQ

### 为什么 API 分 engine / provider / exception 三个包?

三层语义分离:
- `engine.*` — 业务侧通用类型 (OcrImage / OcrResult)
- `provider.*` — vendor SPI (OcrProvider / AbstractOcrProvider)
- `exception.*` — 跨包错误

业务侧只 import `engine.*` 和具体 vendor 的 Provider 类。vendor 实现 import `provider.*` + `exception.*`。sealed `OcrException` 跨包 permits, 保持异常体系统一。

### 能同时启用多个 vendor 吗?

不能。`@ConditionalOnProperty(vendor=<name>, havingValue=...)` 确保同一部署只有一个 vendor autoconfig 激活。这是设计如此: 选 vendor 是运营决策 (上线前一次性决定), 不是运行时决策。

### PaddleOCR-VL 和 MinerU 是同步还是异步?

API 层完全同步 —— 业务侧只看到 `provider.recognize(image, options)`,调用返回 `OcrResult`。这两个 vendor 内部需要轮询任务状态, 但轮询全部封装在 `recognize()` 内部完成, 业务侧不感知。如果内部轮询超过 `timeoutMs`, 抛 `OcrException.VlmTimeout`。

### 业务侧需要异步怎么办?

自行封装:
```java
CompletableFuture.supplyAsync(
    () -> ocrProvider.recognize(image, opts),
    executorService
);
```
或结合 `atlas-richie-component-concurrency` 的结构化并发。组件不强制绑定异步范式。

### 为什么 `ocr-extension-micrometer` / `ocr-extension-otel` 不需要 yaml 配置?

`@ConditionalOnBean(MeterRegistry.class)` / `@ConditionalOnBean(Tracer.class)` 检测业务 app 现有全局基础设施。现代 Spring Boot app 通常已经接入了 Micrometer / OTel, OCR 扩展自动绑定, 零 yaml 配置。如果业务没接, 扩展静默 no-op。

### PaddleOCR-VL 同步调用 image > 1MB 怎么办?

`OcrException.ImageTooLargeForSync` 直接抛出, 不再异步降级。业务侧可选: 压缩图片、拆分图像、改用云 vendor (阿里云/百度) 处理大图。

---

**atlas-richie-component-ocr** — 统一 OCR 门面, 可插拔 vendor
