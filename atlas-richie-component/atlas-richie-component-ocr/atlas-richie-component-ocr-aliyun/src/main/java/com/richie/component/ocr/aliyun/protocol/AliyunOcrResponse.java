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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 阿里云读光 OCR {@code /v1/ocr/recognize} 端点响应的 wire-format 记录族。
 *
 * <p>三个 nested record 描述了完整的 JSON 响应结构:
 * <ul>
 *   <li>{@link AliyunOcrResponse} —— 顶层 envelope（{@code ret} / {@code content} / {@code prism_wnum} /
 *       {@code prism_wordsInfo} / {@code requestId}）</li>
 *   <li>{@link AliyunWordBlock} —— 词级定位块（{@code word} / {@code prob} / {@code pos} / {@code lines}）</li>
 *   <li>{@link AliyunWordLine} —— 行级子结构（{@code word} / {@code prob} / {@code pos}）</li>
 * </ul>
 *
 * <p>所有记录通过 {@code @JsonIgnoreProperties(ignoreUnknown = true)} 容错阿里云文档外字段的演进。
 * 由 {@code HttpResponse.bodyAs(AliyunOcrResponse.class)} 一行代码即可将字节响应流反序列化为
 * Java record 实例，避免手动 JsonNode 树遍历。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AliyunOcrResponse(
        @JsonProperty("ret") String ret,
        @JsonProperty("content") String content,
        @JsonProperty("prism_wnum") Integer prismWnum,
        @JsonProperty("prism_wordsInfo") List<AliyunWordBlock> prismWordsInfo,
        @JsonProperty("requestId") String requestId
) {

    /**
     * 阿里云读光响应 {@code prism_wordsInfo} 数组元素 —— 词级块。
     *
     * <p>{@code prob} 字段为 0-100 整数置信度；{@code pos} 为 4 对坐标点构成的多边形
     * ({@code [[x,y],[x,y],[x,y],[x,y]]})。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AliyunWordBlock(
            @JsonProperty("word") String word,
            @JsonProperty("prob") Integer prob,
            @JsonProperty("pos") List<List<Integer>> pos,
            @JsonProperty("lines") List<AliyunWordLine> lines
    ) {
    }

    /**
     * 阿里云读光响应词级块 {@code lines} 数组元素 —— 行级子结构。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AliyunWordLine(
            @JsonProperty("word") String word,
            @JsonProperty("prob") Integer prob,
            @JsonProperty("pos") List<List<Integer>> pos
    ) {
    }
}
