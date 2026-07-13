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
package com.richie.component.ocr.aliyun.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.richie.component.ocr.model.OcrOptions;

/**
 * 阿里云读光 OCR {@code /v1/ocr/recognize} 端点请求 wire-format record。
 *
 * <p>字段语义:
 * <ul>
 *   <li>{@code url} / {@code body} 二选一 —— URL 模式或裸 base64 模式</li>
 *   <li>{@code prob} / {@code charInfo} / {@code rotate} 三个开关，默认 false (阿里云要求)</li>
 *   <li>{@code table} 由 {@link OcrOptions#tableRecognition()} 决定</li>
 *   <li>{@code hmode} 仅在手写体识别时为 {@code true}（其它场景下 null → JSON 字段省略）</li>
 *   <li>{@code model} vendor 私有字段，固定来自 {@code AliyunOcrProperties}</li>
 *   <li>{@code feature} 仅在 {@code feature=true} flag 时为 {@code "advanced"}（其它场景 null → 省略）</li>
 * </ul>
 *
 * <p>{@code @JsonInclude(NON_NULL)} 字段级策略: Boolean/包装类/String 的 {@code null} 自动从输出 JSON 中剔除。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AliyunOcrPayload(
        @JsonProperty("url") String url,
        @JsonProperty("body") String body,
        @JsonProperty("prob") boolean prob,
        @JsonProperty("charInfo") boolean charInfo,
        @JsonProperty("rotate") boolean rotate,
        @JsonProperty("table") boolean table,
        @JsonProperty("hmode") Boolean hmode,
        @JsonProperty("model") String model,
        @JsonProperty("feature") String feature) {

    /** 构造 base64 image payload（非手写体 / 无高级特性）。 */
    public static AliyunOcrPayload ofBase64(String base64, OcrOptions options, String model, String feature) {
        return new AliyunOcrPayload(
                null,
                base64,
                false, false, false,
                options.tableRecognition(),
                options.handwriting() ? Boolean.TRUE : null,
                model,
                feature);
    }

    /** 构造 URL image payload。 */
    public static AliyunOcrPayload ofUrl(String url, OcrOptions options, String model, String feature) {
        return new AliyunOcrPayload(
                url,
                null,
                false, false, false,
                options.tableRecognition(),
                options.handwriting() ? Boolean.TRUE : null,
                model,
                feature);
    }
}
