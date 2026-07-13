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
package com.richie.component.ocr.volcano.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 火山引擎 OCR 端点响应的 wire-format 记录族
 *
 * <p>四个 nested record 描述完整的 JSON 响应结构:
 * <ul>
 *   <li>{@link VolcanoOcrEnvelope} —— 顶层 envelope
 *       ({@code ResponseMetadata.Error} + {@code Result: {RequestId, Texts: [...]}})</li>
 *   <li>{@link ErrorBody} —— {@code ResponseMetadata.Error} 的 {@code Code} + {@code Message}</li>
 *   <li>{@link TextBlock} —— {@code Texts} 数组元素（{@code Text} / {@code Confidence} / {@code Rect} / 行级 {@code LineTexts}）</li>
 *   <li>{@link LineText} —— 行级子结构（{@code Text} / {@code Confidence} / {@code Rect}）</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 * @param responseMetadata 响应元数据
 * @param result 结果
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VolcanoOcrEnvelope(
        @JsonProperty("ResponseMetadata") ResponseMetadata responseMetadata,
        @JsonProperty("Result") Result result) {

    /**
     * 响应元数据
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-12
     * @param error 错误信息
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseMetadata(
            @JsonProperty("Error") ErrorBody error) {}

    /**
     * 错误信息
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-12
     * @param code 错误码
     * @param message 错误消息
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorBody(
            @JsonProperty("Code") String code,
            @JsonProperty("Message") String message) {}

    /**
     * 结果
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-12
     * @param requestId 请求 ID
     * @param texts 文本块列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("RequestId") String requestId,
            @JsonProperty("Texts") List<TextBlock> texts) {}

    /**
     * 文本块
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-12
     * @param text 文本内容
     * @param confidence 置信度
     * @param rect 文本块位置
     * @param lineTexts 行级文本列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextBlock(
            @JsonProperty("Text") String text,
            @JsonProperty("Confidence") Double confidence,
            @JsonProperty("Rect") Rect rect,
            @JsonProperty("LineTexts") List<LineText> lineTexts) {}

    /**
     * 行级文本
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-12
     * @param text 文本内容
     * @param confidence 置信度
     * @param rect 文本位置
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineText(
            @JsonProperty("Text") String text,
            @JsonProperty("Confidence") Double confidence,
            @JsonProperty("Rect") Rect rect) {}

    /**
     * 文本位置
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-12
     * @param x 左上角 x 坐标
     * @param y 左上角 y 坐标
     * @param width 宽度
     * @param height 高度
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rect(
            @JsonProperty("X") Integer x,
            @JsonProperty("Y") Integer y,
            @JsonProperty("Width") Integer width,
            @JsonProperty("Height") Integer height) {}
}
