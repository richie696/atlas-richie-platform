/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
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

/**
 * 提交至 PaddleOCR-VL sidecar 的请求 DTO（record 类型）。
 *
 * <p>由 {@code PaddleVlOcrProvider#toProviderRequest} 构造、由 {@code PaddleVlOcrProvider#submit} 序列化。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 * @param imageData 待识别的图片原始字节
 * @param lang 语言码，例如 {@code zh}、{@code en}、{@code ja}、{@code ko}
 * @param tableRecognition 是否启用表格识别，启用后 sidecar 将输出结构化表格块
 */
public record VlRequest(
        byte[] imageData,
        String lang,
        boolean tableRecognition) {
}