/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.parser;

import java.util.List;
import java.util.Map;

/**
 * 文档解析结果模型。
 * <p>
 * 包含文档元数据(title / author)与段落列表,每个段落携带页码、章节路径等检索增强所需的上下文。
 * <p>
 * <b>字段契约 (业务方按此断言)</b>:
 * <ul>
 *   <li>{@code title} — 可空; 仅 PDF / DOCX / PPT 等格式 (Tika 抽取的 Metadata.TITLE)</li>
 *   <li>{@code author} — 可空; 来源同上</li>
 *   <li>{@code segments} — 永不 null, 永不 null 元素 (空时为空列表)</li>
 *   <li>{@code metadata} — 永不 null, 至少含 {@code "format"} key 标识来源 parser
 *       ({@code "tika"} / {@code "fesod"} / {@code "text/plain"})</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public record ParsedDocument(
        String title,
        String author,
        List<DocumentSegment> segments,
        Map<String, Object> metadata
) {
    public ParsedDocument {
        if (segments == null) {
            segments = List.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
