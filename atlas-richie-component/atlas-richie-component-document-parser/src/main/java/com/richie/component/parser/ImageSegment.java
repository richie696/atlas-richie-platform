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
 * 图片资源模型 — 文档解析过程中提取出的嵌入图片。
 * <p>
 * <b>设计原则</b>: 组件不处理图片内容(不做 OCR / VLM),原样返回原始字节。
 * 调用方按业务需求决定:
 * <ul>
 *   <li>OCR 服务 → 转可索引文本</li>
 *   <li>对象存储 → 保留图片供后续引用</li>
 *   <li>多模态大模型 → 综合识别</li>
 *   <li>忽略 → 不影响文本解析流程</li>
 * </ul>
 *
 * @param format        MIME 类型, 如 {@code "image/png"}, {@code "image/jpeg"}
 * @param data          图片原始字节
 * @param name          图片名称 (可选, 来自 docx/pptx 嵌入信息)
 * @param pageNumber    所在 PDF 页码 (PDF 场景)
 * @param slideNumber   所在 PPT slide 编号 (PPT 场景)
 * @param sectionPath   层级路径, 如 {@code /file.docx/Page[3]/Image[1]}
 * @param meta          附加元数据 (宽/高/DPI/坐标等可选信息)
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public record ImageSegment(
        String format,
        byte[] data,
        String name,
        Integer pageNumber,
        Integer slideNumber,
        String sectionPath,
        Map<String, Object> meta
) {
    public ImageSegment {
        if (data == null) {
            data = new byte[0];
        }
        if (meta == null) {
            meta = Map.of();
        }
    }
}
