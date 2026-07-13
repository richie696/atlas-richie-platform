/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required for the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ocr.paddle.protocol;

/**
 * 传递给 {@link com.richie.component.ocr.paddle.provider.PaddleOcrProvider} 调用 PaddleOCR 子进程时使用的请求 DTO。
 *
 * <p>由 {@code toProviderRequest} 构造、由 {@code callProvider} 消费；记录组件刻意保持最小化，
 * 仅向 Python 包装脚本传输必要数据。
 *
 * @param imageData 待识别的图片原始字节
 * @param lang PaddleOCR 语言码，例如 {@code ch}（简体中文）、{@code en}（英文）、{@code japan}（日文）
 */
public record PaddleRequest(
        byte[] imageData,
        String lang) {
}