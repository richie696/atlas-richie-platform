/*
 * Copyright (c) 2026 Richie (https://www.richie696.cn)
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

package com.richie.component.parser.model;

import com.richie.component.parser.exception.DocumentParseException;

/**
 * 文档读取事件 — 业务方订阅流式解析产出的事件。包裹组件公开的数据结构,
 * 不暴露内部 {@code ParseEvent} / {@code DocumentSegment} / {@code ImageSegment}。
 * <p>
 * <b>Schema 契约 (业务方按此断言)</b>:
 * <ul>
 *   <li><b>Section</b> — 每段文本, 必有有效 {@code section.sectionPath()} + {@code section.meta()}</li>
 *   <li><b>Image</b> — 每张图片, 必有 {@code image.format()} 以 {@code image/} 起头, {@code image.data()} 永不 null</li>
 *   <li><b>Finished</b> — 必有 {@code result.metadata().get("format")} 标识来源 parser (Office → {@code tika/fesod}, 纯文本 → {@code text/plain})</li>
 *   <li><b>Failed</b> — 每个解析失败都 emit (NOT 抛异常), 业务方可统一捕获</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-09
 */
public sealed interface ReadEvent {

    /**
     * 文本段事件 — {@code fileName} 标识来源文件 (批次流式时让 listener 直接归并到 per-file accumulator)。
     */
    record Section(ParsedSection section, String fileName) implements ReadEvent {}

    /**
     * 图片资源事件 — {@code fileName} 标识来源文件 (批次流式时让 listener 直接归并到 per-file accumulator)。
     */
    record Image(ParsedImage image, String fileName) implements ReadEvent {}

    /**
     * 完成事件 — 含汇总结果 + 计数。
     *
     * @param result         完整 ReadResult 汇总
     * @param totalSections  本次解析产出的文本段数
     * @param totalImages    本次解析产出的图片资源数
     */
    record Finished(ReadResult result, int totalSections, int totalImages)
            implements ReadEvent {}

    /** 失败事件 */
    record Failed(DocumentParseException error) implements ReadEvent {}
}
