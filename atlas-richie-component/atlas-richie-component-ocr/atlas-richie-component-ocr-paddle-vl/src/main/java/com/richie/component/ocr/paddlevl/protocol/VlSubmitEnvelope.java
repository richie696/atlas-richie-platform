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
package com.richie.component.ocr.paddlevl.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * PaddleOCR-VL sidecar 响应的 wire-format 记录族。
 *
 * <p>三个 nested record:
 * <ul>
 *   <li>{@link VlSubmitEnvelope} —— {@code POST /submit} 响应（{@code task_id}）</li>
 *   <li>{@link VlPollEnvelope} —— {@code GET /tasks/{id}} 响应（{@code state} / {@code text} / {@code confidence} / {@code blocks} / {@code error_msg}）</li>
 *   <li>{@link Block} —— {@code blocks} 数组元素（含 {@code lines} 子结构）</li>
 *   <li>{@link Line} —— 行级子结构（{@code text} / {@code confidence} / {@code bbox}）</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 * @param taskId 任务唯一标识
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VlSubmitEnvelope(
        @JsonProperty("task_id") String taskId) {

    /**
     * PaddleOCR-VL sidecar 响应的 wire-format 记录族。
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-12
     * @param state       任务状态
     * @param text        识别文本
     * @param confidence  置信度
     * @param blocks      块级结构列表
     * @param errorCode   错误码
     * @param errorMsg    错误信息
     * @param requiredVramMb  需要的VRAM MB
     * @param availableVramMb 可用的VRAM MB
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VlPollEnvelope(
            @JsonProperty("state") String state,
            @JsonProperty("text") String text,
            @JsonProperty("confidence") Double confidence,
            @JsonProperty("blocks") List<Block> blocks,
            @JsonProperty("error_code") String errorCode,
            @JsonProperty("error_msg") String errorMsg,
            @JsonProperty("required_vram_mb") Integer requiredVramMb,
            @JsonProperty("available_vram_mb") Integer availableVramMb) {}

    /**
     * PaddleOCR-VL sidecar 响应的 wire-format 记录族。
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-12
     * @param text        块级文本
     * @param confidence  置信度
     * @param bbox        边界框
     * @param lines       行级结构列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Block(
            @JsonProperty("text") String text,
            @JsonProperty("confidence") Double confidence,
            @JsonProperty("bbox") List<List<Float>> bbox,
            @JsonProperty("lines") List<Line> lines) {}

    /**
     * PaddleOCR-VL sidecar 响应的 wire-format 记录族。
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-12
     * @param text        行级文本
     * @param confidence  置信度
     * @param bbox        边界框
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(
            @JsonProperty("text") String text,
            @JsonProperty("confidence") Double confidence,
            @JsonProperty("bbox") List<List<Float>> bbox) {}
}
