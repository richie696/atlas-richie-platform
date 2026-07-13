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
import java.util.Objects;

/**
 * 块级识别结果（按版面顺序，含坐标 + 置信度）。
 *
 * @param text       块内合并文本
 * @param box        四点坐标（顺时针或逆时针）
 * @param confidence 块置信度（取值区间 [0.0, 1.0]）
 * @param lines      行级定位结果
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public record OcrBlock(String text, List<Point> box, float confidence, List<OcrLine> lines) {

    /**
     * 创建块级识别结果。
     *
     * @param text       块内合并文本（必传, 通常为该 block 内所有 {@link OcrLine} 文本的拼接）
     * @param box        四点坐标（顺时针或逆时针, {@code null} 视为空列表）
     * @param confidence 块置信度（取值区间 [0.0, 1.0]）
     * @param lines      行级定位结果（{@code null} 视为空列表, 部分 Provider 不输出行级信息）
     */
    public OcrBlock(String text, List<Point> box, float confidence, List<OcrLine> lines) {
        this.text = Objects.requireNonNull(text, "text");
        this.box = box != null ? List.copyOf(box) : List.of();
        this.confidence = confidence;
        this.lines = lines != null ? List.copyOf(lines) : List.of();
    }

    /**
     * 创建块级识别结果（无行级信息）。
     *
     * @param text       块内合并文本（必传）
     * @param box        四点坐标（{@code null} 视为空列表）
     * @param confidence 块置信度（取值区间 [0.0, 1.0]）
     */
    public OcrBlock(String text, List<Point> box, float confidence) {
        this(text, box, confidence, List.of());
    }

    /**
     * @return 块内合并文本
     */
    @Override
    public String text() {
        return text;
    }

    /**
     * @return 四点坐标（顺序由 Provider 决定, 通常为顺时针左上→右上→右下→左下）
     */
    @Override
    public List<Point> box() {
        return box;
    }

    /**
     * @return 块置信度（取值区间 [0.0, 1.0]）
     */
    @Override
    public float confidence() {
        return confidence;
    }

    /**
     * @return 行级定位结果（Provider 未输出时为空列表）
     */
    @Override
    public List<OcrLine> lines() {
        return lines;
    }

    /**
     * 默认阈值（0.6）置信度判定。
     *
     * @return {@code true} 表示该块置信度 ≥ 0.6, 适合 KB 注入等下游消费
     */
    public boolean isConfident() {
        return confidence >= 0.6f;
    }

    /**
     * 自定义阈值置信度判定。
     *
     * @param threshold 阈值（业务侧按场景调整）
     * @return {@code true} 表示该块置信度 ≥ threshold
     */
    public boolean isConfident(float threshold) {
        return confidence >= threshold;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OcrBlock(String text1, List<Point> box1, float confidence1, List<OcrLine> lines1))) return false;
        return Float.compare(confidence, confidence1) == 0
                && text.equals(text1)
                && box.equals(box1)
                && lines.equals(lines1);
    }

}
