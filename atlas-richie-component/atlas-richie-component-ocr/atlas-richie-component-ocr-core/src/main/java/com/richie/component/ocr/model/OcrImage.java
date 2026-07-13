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

import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * 待识别图像 —— 支持 3 种来源，业务侧按场景选。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public abstract sealed class OcrImage
        permits OcrImage.Bytes, OcrImage.Url, OcrImage.Stream {

    private OcrImage() {
    }

    // ---- 3 种变体 ----

    /**
     * 字节数组（来自 OSS 下载 / 上传临时文件 / 解析器抽出的内嵌图）。
     */
    public static final class Bytes extends OcrImage {
        private final byte[] data;
        private final MimeType mime;

        /**
         * 创建字节数组图像变体。
         *
         * @param data 图像原始字节（必传, 由业务侧持有）
         * @param mime 图像 MIME 类型（{@code null} 时默认 {@link MimeType#PNG}）
         */
        public Bytes(byte[] data, MimeType mime) {
            this.data = Objects.requireNonNull(data, "data");
            this.mime = mime != null ? mime : MimeType.PNG;
        }

        /**
         * 创建字节数组图像变体（默认 {@link MimeType#PNG}）。
         *
         * @param data 图像原始字节（必传）
         */
        public Bytes(byte[] data) {
            this(data, MimeType.PNG);
        }

        /**
         * @return 图像原始字节（业务侧持有, 引用而非拷贝, 业务侧不可变更）
         */
        public byte[] data() { return data; }

        /**
         * @return 图像 MIME 类型（永不为 {@code null}）
         */
        public MimeType mime() { return mime; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Bytes bytes)) return false;
            return Arrays.equals(data, bytes.data) && mime == bytes.mime;
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(data);
            result = 31 * result + (mime != null ? mime.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Bytes{size=" + data.length + ", mime=" + mime + '}';
        }
    }

    /**
     * HTTP(S) URL（业务侧不下载，直接传给 Provider 让它下载）。
     */
    public static final class Url extends OcrImage {
        private final String url;
        private final HttpAuth auth;

        /**
         * 创建 URL 图像变体（可携带下载凭证）。
         *
         * @param url  HTTP(S) URL（必传, 业务侧不下载, 由 Provider 自行拉取）
         * @param auth 可选下载凭证（{@code null} 表示 URL 公开可访问）
         */
        public Url(String url, HttpAuth auth) {
            this.url = Objects.requireNonNull(url, "url");
            this.auth = auth;
        }

        /**
         * 创建 URL 图像变体（无下载凭证）。
         *
         * @param url HTTP(S) URL（必传, 业务侧不下载, 由 Provider 自行拉取）
         */
        public Url(String url) {
            this(url, null);
        }

        /**
         * @return HTTP(S) URL
         */
        public String url() { return url; }

        /**
         * @return 下载凭证（{@code null} 表示公开 URL）
         */
        public HttpAuth auth() { return auth; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Url url1)) return false;
            return url.equals(url1.url) && Objects.equals(auth, url1.auth);
        }

        @Override
        public int hashCode() {
            return Objects.hash(url, auth);
        }

        @Override
        public String toString() {
            return "Url{url='" + url + "'}";
        }
    }

    /**
     * 流式输入（大图 / 边下边识别）。
     *
     * <p><b>注意</b>: Stream 是一次性消费的, equals/hashCode 仅基于身份（identity）,
     * 不可作为 {@link java.util.HashMap} / {@link java.util.HashSet} 的 key
     * 来表示"逻辑等价"。
     *
     * <p><b>流所有权</b>: vendor 在读取字节后会立即关闭流 (try-with-resources 模式),
     * 业务侧无需再调用 {@code close()}. 重复关闭可能抛 {@link java.io.IOException}
     * 但不影响 OCR 调用结果（异常被 vendor 内部捕获）.
     */
    public static final class Stream extends OcrImage {
        private final InputStream input;
        private final MimeType mime;

        /**
         * 创建流式输入图像变体。
         *
         * @param input 图像输入流（必传, 一次性消费, vendor 读取后会自动关闭）
         * @param mime  图像 MIME 类型（必传, 不可为 {@code null}, Stream 无法自识别类型）
         */
        public Stream(InputStream input, MimeType mime) {
            this.input = Objects.requireNonNull(input, "input");
            this.mime = Objects.requireNonNull(mime, "mime");
        }

        /**
         * @return 图像输入流（一次性消费, vendor 读取后会自动关闭, 业务侧无需重复关闭）
         */
        public InputStream input() { return input; }

        /**
         * @return 图像 MIME 类型
         */
        public MimeType mime() { return mime; }

        @Override
        public boolean equals(Object o) {
            // identity-only: InputStream 是有状态的（读完即消耗）, 不可重放比较
            return this == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public String toString() {
            return "Stream{mime=" + mime + '}';
        }
    }
}
