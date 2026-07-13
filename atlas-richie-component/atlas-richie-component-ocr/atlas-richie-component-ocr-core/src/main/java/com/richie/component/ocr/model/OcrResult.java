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
package com.richie.component.ocr.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 识别结果 —— 业务侧只依赖此模型，不接触 Provider 私有 schema。
 *
 * @param text 整体识别文本（必传, 由 Provider 拼接各 block 文本得到）
 * @param blocks 块级定位结果（{@code null} 视为空列表）
 * @param overallConfidence 整体置信度（取值区间 [0.0, 1.0]）
 * @param pageMetadata 页面级元数据（如 PDF 页码 / 旋转角度 / Vendor 私有字段, {@code null} 视为空 Map）
 * @param latencyMs 识别耗时（毫秒）
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public record OcrResult(String text, List<OcrBlock> blocks, float overallConfidence, Map<String, Object> pageMetadata,
                        long latencyMs) {

    /**
     * 创建识别结果。
     *
     * @param text              整体识别文本（必传, 由 Provider 拼接各 block 文本得到）
     * @param blocks            块级定位结果（{@code null} 视为空列表）
     * @param overallConfidence 整体置信度（取值区间 [0.0, 1.0]）
     * @param pageMetadata      页面级元数据（如 PDF 页码 / 旋转角度 / Vendor 私有字段, {@code null} 视为空 Map）
     * @param latencyMs         识别耗时（毫秒）
     */
    public OcrResult(String text, List<OcrBlock> blocks, float overallConfidence,
                     Map<String, Object> pageMetadata, long latencyMs) {
        this.text = Objects.requireNonNull(text, "text");
        this.blocks = blocks != null ? List.copyOf(blocks) : List.of();
        this.overallConfidence = overallConfidence;
        this.pageMetadata = pageMetadata != null ? Map.copyOf(pageMetadata) : Map.of();
        this.latencyMs = latencyMs;
    }

    // --- getters ---

    /**
     * @return 整体识别文本（拼接所有 block 文本得到）
     */
    @Override
    public String text() {
        return text;
    }

    /**
     * @return 块级定位结果（按版面顺序）
     */
    @Override
    public List<OcrBlock> blocks() {
        return blocks;
    }

    /**
     * @return 整体置信度
     */
    @Override
    public float overallConfidence() {
        return overallConfidence;
    }

    /**
     * @return 页面级元数据
     */
    @Override
    public Map<String, Object> pageMetadata() {
        return pageMetadata;
    }

    /**
     * @return 识别耗时（毫秒）
     */
    @Override
    public long latencyMs() {
        return latencyMs;
    }

    /**
     * 高置信度过滤后的纯文本（业务侧注入 KB 用）。
     *
     * @param threshold 置信度阈值
     * @return 过滤后的文本
     */
    public String highConfidenceText(float threshold) {
        StringBuilder sb = new StringBuilder();
        for (OcrBlock block : blocks) {
            if (block.confidence() >= threshold) {
                if (!sb.isEmpty()) sb.append("\n\n");
                sb.append(block.text());
            }
        }
        return sb.toString();
    }

    /**
     * 高置信度过滤（默认阈值 {@link OcrOptions#DEFAULT_CONFIDENCE_THRESHOLD}）。
     *
     * @return 过滤后的文本
     */
    public String highConfidenceText() {
        return highConfidenceText(OcrOptions.DEFAULT_CONFIDENCE_THRESHOLD);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OcrResult that)) return false;
        return Float.compare(overallConfidence, that.overallConfidence) == 0
                && latencyMs == that.latencyMs
                && text.equals(that.text)
                && blocks.equals(that.blocks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, blocks, overallConfidence, latencyMs);
    }

    @Override
    public String toString() {
        return "OcrResult{text.length=" + text.length()
                + ", blocks=" + blocks.size()
                + ", confidence=" + overallConfidence
                + ", latencyMs=" + latencyMs + '}';
    }
}
