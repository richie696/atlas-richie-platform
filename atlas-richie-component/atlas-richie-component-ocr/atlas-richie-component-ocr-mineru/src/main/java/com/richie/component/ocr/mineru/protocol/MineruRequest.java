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
package com.richie.component.ocr.mineru.protocol;

/**
 * 上传至 MinerU sidecar 的请求 DTO（record 类型）。
 *
 * <p>由 {@code MineruOcrProvider#toProviderRequest} 构造、由 {@code MineruOcrProvider#upload}
 * 以 multipart/form-data 形式序列化提交。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 * @param pdfData 待识别的 PDF 原始字节
 */
public record MineruRequest(
        byte[] pdfData) {
}