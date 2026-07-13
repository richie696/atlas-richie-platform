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
package com.richie.component.ocr.model;

/**
 * 图像 MIME 类型枚举 —— 支持 {@link OcrImage} 的常见输入格式。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public enum MimeType {
    /** PNG 图像（{@code image/png}） */
    PNG("image/png"),
    /** JPEG 图像（{@code image/jpeg}） */
    JPEG("image/jpeg"),
    /** TIFF 图像（{@code image/tiff}, 扫描件常见） */
    TIFF("image/tiff"),
    /** BMP 位图（{@code image/bmp}, Windows 旧格式） */
    BMP("image/bmp"),
    /** WebP 图像（{@code image/webp}, 压缩率高） */
    WEBP("image/webp"),
    /** PDF 文档（{@code application/pdf}, 仅部分 Provider 支持, MinerU / PaddleOCR-VL） */
    PDF("application/pdf");

    private final String contentType;

    MimeType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * @return HTTP 标准 MIME 字符串（如 {@code "image/png"}）, 用于 Provider HTTP 请求头
     */
    public String contentType() {
        return contentType;
    }
}
