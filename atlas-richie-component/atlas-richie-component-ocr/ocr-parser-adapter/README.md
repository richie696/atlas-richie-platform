# OCR Parser Adapter

`ocr-parser-adapter` 是 `document-parser` 的可选下游流式增强器。 它不改变 parser 的输出，也不反向引入
OCR 依赖；只有业务工程同时引入本模块、一个 OCR Provider 并显式开启配置时，才会对 `ReadEvent.Image` 执行 OCR。

```yaml
platform:
  component:
    ocr:
      parser-adapter:
        enabled: true
        max-image-bytes: 10485760 # 10 MiB
        supported-mime-types: [image/png, image/jpeg, image/tiff, image/bmp, image/webp]
        min-confidence: 0.6
        emit-original-image: true
        failure-mode: SKIP_IMAGE # SKIP_IMAGE | FAIL_DOCUMENT
        detect-orientation: true
        table-recognition: false
```

业务代码显式将 parser 的事件流交给适配器：

```java
Flow.Publisher<ReadEvent> enhanced = ocrDocumentEnrichmentAdapter.enrich(parserEvents);
```

适配器逐个请求并处理上游事件，不创建无界队列或隐式线程池。图片事件保留并补充 `ocr.*` 元数据； 成功识别后会紧随其后发出一个
`ReadEvent.Section`，其中 `contentSource=OCR`，位置路径为原图片路径加 `/ocr`。
`Finished.totalSections` 会包含新增的 OCR 文本段。`Finished.totalImages` 表示实际 emit 的图片事件数； 原 parser
的图片总数保留在完成摘要元数据的 `ocr.sourceImages` 中。
