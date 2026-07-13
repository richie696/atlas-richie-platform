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

import com.richie.component.ocr.model.OcrOptions;

/**
 * 发送到阿里云读光 OCR {@code /v1/ocr/recognize} 端点的协议层请求载荷。
 *
 * <p>vendor: {@code aliyun}；API 协议类型: HTTP JSON 同步识别请求；
 * 配置方式: 由 {@code platform.component.ocr.aliyun.*} 决定 endpoint、鉴权和模型参数。</p>
 *
 * <p>按阿里云协议要求，{@link #imageUrl()} 与 {@link #imageBase64()} 必须且只能有一个携带图片。
 *
 * @param imageUrl 图片公网访问地址；当 {@code imageBase64} 携带图片内容时为 {@code null}
 * @param imageBase64 不带 {@code data:} URI 前缀的图片字节 Base64；使用 {@code imageUrl} 时为 {@code null}
 * @param options 调用侧 OCR 选项，会被转换为阿里云的 {@code table}、{@code hmode} 等请求字段
 */
public record AliyunRequest(
        String imageUrl,
        String imageBase64,
        OcrOptions options) {
}