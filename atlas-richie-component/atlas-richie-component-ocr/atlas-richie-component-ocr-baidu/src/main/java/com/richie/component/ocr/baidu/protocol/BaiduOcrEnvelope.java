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
package com.richie.component.ocr.baidu.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 百度智能云 OCR {@code /rest/2.0/ocr/v1/general_basic} 端点响应的 wire-format 记录族.
 *
 * <p>两个 nested record 描述完整的 JSON 响应结构:
 * <ul>
 *   <li>{@link BaiduOcrEnvelope} —— 顶层 envelope
 *       ({@code words_result} / {@code words_result_num} / {@code log_id} / 错误时的 {@code error_code} + {@code error_msg})</li>
 *   <li>{@link WordItem} —— {@code words_result} 数组元素（仅 {@code words} 字段）</li>
 * </ul>
 *
 * <p>所有记录通过 {@code @JsonIgnoreProperties(ignoreUnknown = true)} 容错百度新增字段。由
 * {@code HttpResponse.bodyAs(BaiduOcrEnvelope.class)} 一行代码即可将字节响应流反序列化为
 * Java record 实例，避免手动 JsonNode 树遍历。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BaiduOcrEnvelope(
        @JsonProperty("words_result") List<WordItem> wordsResult,
        @JsonProperty("words_result_num") Integer wordsResultNum,
        @JsonProperty("log_id") String logId,
        @JsonProperty("error_code") Integer errorCode,
        @JsonProperty("error_msg") String errorMsg) {

    /**
     * 百度通用文字识别响应 {@code words_result} 数组元素 —— 每行识别结果。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WordItem(
            @JsonProperty("words") String words) {
    }
}
