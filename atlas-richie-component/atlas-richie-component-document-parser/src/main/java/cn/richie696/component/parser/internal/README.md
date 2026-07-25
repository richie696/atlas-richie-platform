# internal/

实现细节目录。**对外不导出**,包名带 `internal` 仅作为命名约定,不强制模块隔离。

## 计划内容 (Phase 2-5)

- `FormatDetector` — 基于 magic bytes 的内容嗅探(Phase 2)
- `TextFastPathParser` — txt/markdown fast-path 实现(Phase 2)
- `TikaDocumentParser` — PDF/Word/PPT/ODF 实现(Phase 3)
- `FesodDocumentParser` — xlsx/xls 流式实现(Phase 4)
- `UrlFetcher` — URL 三道防线(SSRF + HEAD + Detector, Phase 5)
- `ParserRouter` — SPI 路由,根据 FormatDetector 选择实现(Phase 5)

## 替换原则

未来若用 POI / 其他库替代 Tika 或 Fesod,**仅修改本目录内代码**,
对外 SPI + Facade + 模型 API 零变化。

## Phase 1 状态

空目录,等待 Phase 2 实施。
