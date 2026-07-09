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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文档解析结果 — 组件对外公开的数据结构。
 * <p>
 * 这是 {@link com.richie.component.parser.DocumentReader DocumentReader} 解析返回的"自有数据结构",
 * 不暴露内部 {@code DocumentSegment} / {@code ImageSegment} / {@code ParsedDocument}。
 * 业务方只持有此类型,不直接依赖任何内部实现细节。
 * <p>
 * <b>字段</b>:
 * <ul>
 *   <li>{@link #title()} — 文档标题 (可能为 null)</li>
 *   <li>{@link #author()} — 文档作者 (可能为 null)</li>
 *   <li>{@link #sections()} — 文本段列表 (有序)</li>
 *   <li>{@link #images()} — 图片资源列表 (有序)</li>
 *   <li>{@link #metadata()} — 元数据, 必含 {@code format} (解析来源: "tika" / "fesod" / "text/plain")</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-09
 */
public final class ReadResult {

    private final String title;
    private final String author;
    private final List<ParsedSection> sections;
    private final List<ParsedImage> images;
    private final Map<String, Object> metadata;

    public ReadResult(String title,
                      String author,
                      List<ParsedSection> sections,
                      List<ParsedImage> images,
                      Map<String, Object> metadata) {
        this.title = title;
        this.author = author;
        this.sections = sections == null
                ? Collections.emptyList()
                : List.copyOf(sections);
        this.images = images == null
                ? Collections.emptyList()
                : List.copyOf(images);
        this.metadata = metadata == null
                ? Map.of()
                : Map.copyOf(metadata);
    }

    public String title() {
        return title;
    }

    public String author() {
        return author;
    }

    public List<ParsedSection> sections() {
        return sections;
    }

    public List<ParsedImage> images() {
        return images;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public int sectionCount() {
        return sections.size();
    }

    public int imageCount() {
        return images.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadResult that)) return false;
        return Objects.equals(title, that.title)
                && Objects.equals(author, that.author)
                && Objects.equals(sections, that.sections)
                && Objects.equals(images, that.images)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, author, sections, images, metadata);
    }

    @Override
    public String toString() {
        return "ReadResult{title='" + title + "', author='" + author
                + "', sections=" + sections.size() + ", images=" + images.size()
                + ", metadata=" + metadata + '}';
    }
}
