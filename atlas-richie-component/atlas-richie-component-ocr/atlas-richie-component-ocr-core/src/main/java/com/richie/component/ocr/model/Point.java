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

/**
 * 坐标点（像素级）。
 *
 * @param x X 坐标
 * @param y Y 坐标
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public record Point(int x, int y) {

    /**
     * 紧凑构造器 —— 校验坐标非负.
     *
     * @param x X 坐标（必传, 非负）
     * @param y Y 坐标（必传, 非负）
     * @throws IllegalArgumentException 当坐标为负数时
     */
    public Point {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Coordinates must be non-negative: x=" + x + ", y=" + y);
        }
    }
}
