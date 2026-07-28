package cn.richie696.component.ocr.parser;

/** 单张图片 OCR 失败时的文档流处理策略。 */
public enum OcrFailureMode {
    /** 保留图片并标注失败元数据，继续处理后续事件。 */
    SKIP_IMAGE,
    /** 将 OCR 失败转换为文档失败事件并终止当前流。 */
    FAIL_DOCUMENT
}
