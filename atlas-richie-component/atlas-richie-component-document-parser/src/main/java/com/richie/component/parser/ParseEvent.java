/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.richie.component.parser;

import com.richie.component.parser.exception.DocumentParseException;

/**
 * 流式解析事件 — 业务方订阅此事件流即可拿到解析过程中所有产出。
 * <p>
 * 设计目标:
 * <ul>
 *   <li>边读 → 边解 → 边 emit: 调用方可以立即消费每段,无需等全部解析完成</li>
 *   <li>不封堵调用方的路: 图片资源原样返回 (OCR / VLM / 存图 / 忽略由调用方决定)</li>
 *   <li>支持多 PDF 并发: 每个 source 独立 emit, 互不干扰</li>
 * </ul>
 *
 * <p>
 * <b>Schema 契约 (业务方按此断言 — 跨 3 个 parser 一致)</b>:
 * <ul>
 *   <li><b>Streaming</b> — 每段文本, 必有有效 {@code segment.sectionPath()} + {@code segment.meta().get("format")}</li>
 *   <li><b>ImageStreaming</b> — 每张图片, 必有 {@code image.format()} 以 {@code image/} 起头, {@code image.data()} 永不 null</li>
 *   <li><b>Finished</b> — 必有 {@code summary.metadata().get("format")} 标识来源 parser (Office → {@code tika/fesod}, 纯文本 → {@code text/plain})</li>
 *   <li><b>Failed</b> — 每个解析失败都 emit (NOT 抛异常), 业务方可统一捕获</li>
 * </ul>
 * 实现差异 (已知 trade-off):
 * <ul>
 *   <li>Tika 当前 TBD: 是否抽取真图字节 (Phase B 计划)</li>
 *   <li>Fesod {@code image.name()} 可能为空</li>
 *   <li>TextFastPath 无图片路径</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public sealed interface ParseEvent {

    /** 事件来源标识 (URL / FilePath / nameHint) */
    String sourceName();

    /** 文本段落事件 — 解析出的一段文本 (段落 / 行 / 页 等) */
    record Streaming(DocumentSegment segment) implements ParseEvent {
        @Override
        public String sourceName() {
            return segment.sectionPath();
        }
    }

    /**
     * 图片资源事件 — 解析出的嵌入图片原始字节 (PDF / Word / PPT / Excel)。
     * <p>
     * 调用方按需处理:
     * <ul>
     *   <li>OCR 服务 (Tesseract / 阿里云 OCR) → 转文本</li>
     *   <li>存储 (S3 / OSS) → 后续异步处理</li>
     *   <li>视觉大模型 (Qwen-VL / GPT-4V) → 多模态识别</li>
     *   <li>忽略 (业务不需要)</li>
     * </ul>
     */
    record ImageStreaming(ImageSegment image) implements ParseEvent {
        @Override
        public String sourceName() {
            return image.sectionPath();
        }
    }

    /**
     * 完成事件 — 包含汇总 + 计数。
     *
     * @param summary        完整 ParsedDocument 汇总 (含所有 Streaming 段 + 元数据)
     * @param totalSegments  本次解析产出的文本段落数
     * @param totalImages     本次解析产出的图片资源数
     */
    record Finished(ParsedDocument summary, int totalSegments, int totalImages)
            implements ParseEvent {
        @Override
        public String sourceName() {
            return summary.metadata() != null
                    ? summary.metadata().getOrDefault("source", "unknown").toString()
                    : "unknown";
        }
    }

    /** 失败事件 — 抛出 DocumentParseException */
    record Failed(DocumentParseException error) implements ParseEvent {
        @Override
        public String sourceName() {
            return error.getMessage();
        }
    }
}
