package cn.richie696.component.ocr.parser;

import cn.richie696.component.parser.model.ReadEvent;

import java.util.concurrent.Flow;

/**
 * 将 parser 图片事件可选地增强为 OCR 文本 {@link ReadEvent.Section} 的流式适配器。
 *
 * <p>该接口不装饰或替换 {@code DocumentReader}；业务代码按需把 parser 输出的事件流传入
 * {@link #enrich(Flow.Publisher)}，以保持 document-parser 对 OCR 的零依赖。</p>
 */
public interface OcrDocumentEnrichmentAdapter {

    /**
     * 返回保留 parser 事件顺序的增强流。每个图片事件至多派生一个 OCR 文本段。
     *
     * @param source document-parser 输出的事件流
     * @return OCR 增强后的事件流
     */
    Flow.Publisher<ReadEvent> enrich(Flow.Publisher<ReadEvent> source);
}
