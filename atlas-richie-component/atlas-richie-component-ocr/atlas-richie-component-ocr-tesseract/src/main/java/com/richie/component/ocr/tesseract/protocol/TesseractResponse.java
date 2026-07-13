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

import java.util.List;

import com.richie.component.ocr.model.OcrLine;

/**
 * Tesseract OCR 调用的顶层结果 DTO（record 类型）。
 *
 * <p>由 {@code TesseractOcrProvider#callProvider} 产出、由 {@code TesseractOcrProvider#fromProviderResponse} 消费。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 * @param lines 从 TSV 解析并按 {@code (block_num, par_num, line_num)} 聚合得到的行列表
 * @param avgConfidence 所有行级置信度的平均值（已归一化至 {@code [0, 1]}）
 * @param latencyMs 调用 Tesseract CLI 子进程的墙钟耗时，单位毫秒
 */
public record TesseractResponse(
        List<OcrLine> lines,
        float avgConfidence,
        long latencyMs) {
}