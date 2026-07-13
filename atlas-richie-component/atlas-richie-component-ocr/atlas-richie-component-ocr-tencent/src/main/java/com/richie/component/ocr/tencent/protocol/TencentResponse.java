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

/**
 * 腾讯云 OCR API 返回的协议层原始响应，尚未转换为引擎统一的 {@code OcrResult}。
 *
 * <p>响应体以 typed {@link TencentOcrEnvelope} 暴露，不再使用 {@code JsonNode} 树遍历。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 * @param body 已 typed 反序列化的腾讯云 OCR 响应 envelope
 * @param latencyMs 从请求发出到客户端收到响应的墙钟耗时
 */
public record TencentResponse(
        TencentOcrEnvelope body,
        long latencyMs) {
}
