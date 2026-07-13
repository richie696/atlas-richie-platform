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
package com.richie.component.ocr.paddle.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * PaddleOCR Python 子进程 stdout JSON 的 wire-format 记录族。
 *
 * <p>两个 record:
 * <ul>
 *   <li>{@link PaddleOcrEnvelope} —— 顶层（{@code error} 或 {@code items: [...]}, 二者择一）</li>
 *   <li>{@link Item} —— 识别项（{@code text} / {@code score} / {@code bbox} / {@code cls}）</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 * @param items 识别项列表
 * @param error 错误信息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaddleOcrEnvelope(
        @JsonProperty("items") List<Item> items,
        @JsonProperty("error") String error) {

    /**
     * 识别项
     *
     * @author richie696
     * @version 1.0.0
     * @since 2026-07-12
     * @param text  文本
     * @param score 分数
     * @param bbox  边框
     * @param cls   类别
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("text") String text,
            @JsonProperty("score") Double score,
            @JsonProperty("bbox") List<List<Float>> bbox,
            @JsonProperty("cls") Integer cls) {}
}
