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
 * 行级定位结果（可选；部分 Provider 输出）。
 *
 * @param text       行内文本（必传）
 * @param box        四点坐标（{@code null} 视为空列表）
 * @param confidence 行置信度（取值区间 [0.0, 1.0]）
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public record OcrLine(String text, List<Point> box, float confidence) {

    /**
     * 创建行级定位结果。
     *
     * @param text       行内文本（必传）
     * @param box        四点坐标（{@code null} 视为空列表）
     * @param confidence 行置信度（取值区间 [0.0, 1.0]）
     */
    public OcrLine(String text, List<Point> box, float confidence) {
        this.text = Objects.requireNonNull(text, "text");
        this.box = box != null ? List.copyOf(box) : List.of();
        this.confidence = confidence;
    }

    /**
     * @return 行内文本
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
     * @return 行置信度（取值区间 [0.0, 1.0]）
     */
    @Override
    public float confidence() {
        return confidence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OcrLine(String text1, List<Point> box1, float confidence1))) return false;
        return Float.compare(confidence, confidence1) == 0
                && text.equals(text1)
                && box.equals(box1);
    }

}
