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
package com.richie.component.ocr.tencent.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 腾讯云 OCR {@code GeneralAccurateOCR} 端点响应的 wire-format 记录族。
 *
 * <p>三个 nested record 描述完整的 JSON 响应结构:
 * <ul>
 *   <li>{@link TencentOcrEnvelope} —— 顶层 envelope（{@code Response} + {@code RequestId}）</li>
 *   <li>{@link ErrorBody} —— {@code Response.Error.Code} + {@code Message}</li>
 *   <li>{@link TextDetection} —— {@code TextDetections} 数组元素（{@code DetectedText} / {@code Confidence} / {@code Polygon}）</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 * @param response 顶层 envelope
 * @param requestId 唯一请求标识
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TencentOcrEnvelope(
        @JsonProperty("Response") Response response,
        @JsonProperty("RequestId") String requestId) {

    /**
     * 腾讯云 envelope 结构体。
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-10
     * @param error 错误信息
     * @param textDetections 文本检测结果
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
            @JsonProperty("Error") ErrorBody error,
            @JsonProperty("TextDetections") List<TextDetection> textDetections) {}

    /**
     * 腾讯云错误信息结构体。
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-10
     * @param code 错误码
     * @param message 错误描述
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorBody(
            @JsonProperty("Code") String code,
            @JsonProperty("Message") String message) {}

    /**
     * 腾讯云文本检测结果结构体。
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-10
     * @param detectedText 检测到的文本
     * @param confidence 置信度
     * @param polygon 文本顶点坐标
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextDetection(
            @JsonProperty("DetectedText") String detectedText,
            @JsonProperty("Confidence") Double confidence,
            @JsonProperty("Polygon") List<PolygonPoint> polygon) {}

    /**
     * 腾讯云单顶点 {@code {X, Y}}
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-10
     * @param x X 坐标
     * @param y Y 坐标
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PolygonPoint(
            @JsonProperty("X") Integer x,
            @JsonProperty("Y") Integer y) {}
}
