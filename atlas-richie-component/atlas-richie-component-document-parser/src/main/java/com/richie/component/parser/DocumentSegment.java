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

package com.richie.component.parser;

import java.util.Map;

/**
 * 文档段落模型。
 * <p>
 * 段落级粒度,携带页码、章节路径等检索增强所需的元数据。
 * RAG 场景中,每个段落可作为一个 embedding 单元送入向量库。
 * <p>
 * <b>字段契约 (业务方按此断言)</b>:
 * <ul>
 *   <li>{@code text} — 必填, 非 null, 已 trim</li>
 *   <li>{@code pageNumber} — 可空; 多页文档 (PDF/PPT) 必填, 其它可空</li>
 *   <li>{@code sectionPath} — 以 {@code /} 起头的层级路径, e.g. {@code "/file.pdf/Page[1]"} 或 {@code "/file.xlsx/Sheet[1]/Row[1]"}</li>
 *   <li>{@code meta} — 永不 null, 至少含 {@code "format"} key 标识来源 (例如 {@code "tika"}, {@code "fesod"}, {@code "text"})。
 *       可选 key: {@code tag} (HTML 标签), {@code sheet}/{@code row} (Excel), {@code order} (TextFastPath 顺序号)</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public record DocumentSegment(
        String text,
        Integer pageNumber,
        String sectionPath,
        Map<String, Object> meta
) {
    public DocumentSegment {
        if (text == null) {
            text = "";
        }
        if (meta == null) {
            meta = Map.of();
        }
    }
}
