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
package com.richie.component.ocr.tesseract.protocol;

/**
 * Tesseract OCR 调用协议 DTO —— 顶层 record, 业务侧 / 单测可直接 import.
 *
 * <p>由 {@code TesseractOcrProvider.toProviderRequest} 构造,
 * 由 {@code TesseractOcrProvider.callProvider} 消费.
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 * @param imageData 待识别的图片原始字节（Tesseract CLI 不支持 stdin + TSV, 故先物化为字节数组）
 * @param language Tesseract 语言码（多语言以 {@code eng+chi_sim} 形式拼接）
 * @param tessdataPath Tesseract 训练数据目录绝对路径, 通过 {@code TESSDATA_PREFIX} 环境变量传入子进程
 * @param timeoutMs 单次识别子进程的超时时间, 单位毫秒
 */
public record TesseractRequest(
        byte[] imageData,
        String language,
        String tessdataPath,
        long timeoutMs) {
}