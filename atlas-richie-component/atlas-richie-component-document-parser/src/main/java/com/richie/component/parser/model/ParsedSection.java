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
package com.richie.component.parser.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 解析出的一段文本 — 组件对外公开的数据结构。
 * <p>
 * 这是 DocumentReader 流式/批式解析产出的"自有文本段"数据结构,
 * 不暴露内部 {@code DocumentSegment}。
 * <p>
 * <b>字段</b>:
 * <ul>
 *   <li>{@link #text()} — 段落纯文本 (必填, 非 null)</li>
 *   <li>{@link #sectionPath()} — 段落位置路径 (例如 {@code /sample.pdf} 或 {@code /employees/Row[1]})</li>
 *   <li>{@link #meta()} — 元数据, 必有 {@code format} key</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-09
 */
public final class ParsedSection {

    private final String text;
    private final String sectionPath;
    private final Map<String, Object> meta;

    public ParsedSection(String text,
                         String sectionPath,
                         Map<String, Object> meta) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        this.text = text;
        this.sectionPath = sectionPath;
        this.meta = meta == null ? Map.of() : Map.copyOf(meta);
    }

    public String text() {
        return text;
    }

    public String sectionPath() {
        return sectionPath;
    }

    public Map<String, Object> meta() {
        return meta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParsedSection that)) return false;
        return Objects.equals(text, that.text)
                && Objects.equals(sectionPath, that.sectionPath)
                && Objects.equals(meta, that.meta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, sectionPath, meta);
    }

    @Override
    public String toString() {
        return "ParsedSection{path='" + sectionPath + "', text='" + truncate(text, 60) + "', meta=" + meta + '}';
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
