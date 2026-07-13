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

import com.richie.component.ocr.model.OcrOptions;

/**
 * 发送到腾讯云 OCR API 的协议层请求载荷。
 *
 * <p>vendor: {@code tencent}；API 协议类型: HTTP JSON 同步识别请求；
 * 配置方式: 由 {@code platform.component.ocr.tencent.*} 决定 endpoint、region、action 与 TC3 凭据。</p>
 *
 * @param imageBase64 不带 {@code data:} URI 前缀的图片字节 Base64
 * @param options 调用侧 OCR 选项，会随请求上下文一起保留给 Provider 使用
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public record TencentRequest(String imageBase64, OcrOptions options) {
}
