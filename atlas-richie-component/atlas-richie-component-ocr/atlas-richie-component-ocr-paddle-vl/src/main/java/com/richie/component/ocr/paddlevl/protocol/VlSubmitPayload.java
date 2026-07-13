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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Base64;

/**
 * PaddleOCR-VL sidecar {@code POST {grpc-endpoint}/submit} 端点请求 wire-format record。
 *
 * <p>把原 {@code ObjectNode body = JsonNodeFactory.instance.objectNode(); body.put(...)}
 * 手动构造改为 typed record + {@code JsonUtils.serialize(this)} 一行序列化。
 *
 * <p>字段语义:
 * <ul>
 *   <li>{@code image} —— base64 编码后的图片字节串（来自 {@link VlRequest#imageData()}）</li>
 *   <li>{@code lang} —— 语言码（{@code zh}/{@code en}/{@code ja}/{@code ko} 等）</li>
 *   <li>{@code table_recognition} —— 是否启用表格识别</li>
 *   <li>{@code gpu_pool} —— sidecar GPU worker 数量（来自 Provider 配置，不是 per-call 参数）</li>
 * </ul>
 *
 * <p>全部字段为基本类型 / String，无可空字段；{@code @JsonInclude(NON_NULL)} 作为通用防御保留。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 * @param image base64 编码后的图片字节串（来自 {@link VlRequest#imageData()}）
 * @param lang 语言码（{@code zh}/{@code en}/{@code ja}/{@code ko} 等）
 * @param tableRecognition 是否启用表格识别
 * @param gpuPool sidecar GPU worker 数量（来自 Provider 配置，不是 per-call 参数）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record VlSubmitPayload(
        @JsonProperty("image") String image,
        @JsonProperty("lang") String lang,
        @JsonProperty("table_recognition") boolean tableRecognition,
        @JsonProperty("gpu_pool") int gpuPool) {

    /**
     * 从内部 {@link VlRequest} 与 Provider 级 {@code gpuPool} 配置构造 submit wire payload。
     *
     * @param request 内部请求（含原始图片字节 / 语言码 / 表格识别开关）
     * @param gpuPool Provider 配置的 sidecar GPU worker 数量
     * @return 序列化后字节与原 {@code ObjectNode.put(...)} 链等价的 typed payload
     */
    public static VlSubmitPayload of(VlRequest request, int gpuPool) {
        return new VlSubmitPayload(
                Base64.getEncoder().encodeToString(request.imageData()),
                request.lang(),
                request.tableRecognition(),
                gpuPool);
    }
}