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

import java.util.Map;
import java.util.Objects;

/**
 * 解析出的嵌入图片资源 — 组件对外公开的数据结构。
 * <p>
 * 这是 DocumentReader 流式解析产出的"自有图片数据结构",
 * 不暴露内部 {@code ImageSegment}。
 * <p>
 * <b>字段</b>:
 * <ul>
 *   <li>{@link #format()} — MIME (以 {@code image/} 开头, 例如 {@code image/png})</li>
 *   <li>{@link #data()} — 图片字节 (永不 null, 可能为空数组当仅检测到 placeholder)</li>
 *   <li>{@link #name()} — 图片名 (来源文件 / 嵌入对象名, 可能为 null)</li>
 *   <li>{@link #sectionPath()} — 来源位置路径</li>
 *   <li>{@link #size()} — {@code data.length}, 调用方便捷省去 length 调用</li>
 *   <li>{@link #meta()} — 元数据 (含 {@code source} / {@code mimeType} / {@code size} 等 key)</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-09
 */
public final class ParsedImage {

    private final String format;
    private final byte[] data;
    private final String name;
    private final String sectionPath;
    private final int size;
    private final Map<String, Object> meta;

    public ParsedImage(String format,
                       byte[] data,
                       String name,
                       String sectionPath,
                       Map<String, Object> meta) {
        this.format = format != null ? format : "image/unknown";
        this.data = data == null ? new byte[0] : data;
        this.name = name;
        this.sectionPath = sectionPath;
        this.size = this.data.length;
        this.meta = meta == null ? Map.of() : Map.copyOf(meta);
    }

    public String format() {
        return format;
    }

    public byte[] data() {
        return data;
    }

    public String name() {
        return name;
    }

    public String sectionPath() {
        return sectionPath;
    }

    public int size() {
        return size;
    }

    public Map<String, Object> meta() {
        return meta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParsedImage that)) return false;
        return size == that.size
                && Objects.equals(format, that.format)
                && Objects.equals(name, that.name)
                && Objects.equals(sectionPath, that.sectionPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, size, name, sectionPath);
    }

    @Override
    public String toString() {
        return "ParsedImage{format='" + format + "', name='" + name
                + "', sectionPath='" + sectionPath + "', size=" + size + '}';
    }
}
