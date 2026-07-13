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

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 识别选项 —— 业务侧按场景组合.
 *
 * <p>字段语义:
 * <ul>
 *   <li>不暴露 vendor 私有参数</li>
 *   <li>dpi / confidenceThreshold 在 Builder 阶段校验, 非法值直接抛 IllegalArgumentException</li>
 *   <li>{@link #languages} 默认 {@link Languages#CHINESE_SIMPLIFIED_AND_ENGLISH};
 *       业务侧可通过 {@link Builder#languages(Languages...)} 传多语言; 传 {@link Languages#AUTO}
 *       表示用 {@code OcrProperties.defaultLanguages}</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public final class OcrOptions {

    /** 默认置信度阈值（{@link OcrResult#highConfidenceText()} 无参重载使用） */
    public static final float DEFAULT_CONFIDENCE_THRESHOLD = 0.6f;

    private final Set<Languages> languages;
    private final int dpi;
    private final boolean detectOrientation;
    private final boolean tableRecognition;
    private final boolean handwriting;
    private final boolean outputBoundingBoxes;
    private final float confidenceThreshold;

    private OcrOptions(Builder builder) {
        this.languages = builder.languages;
        this.dpi = builder.dpi;
        this.detectOrientation = builder.detectOrientation;
        this.tableRecognition = builder.tableRecognition;
        this.handwriting = builder.handwriting;
        this.outputBoundingBoxes = builder.outputBoundingBoxes;
        this.confidenceThreshold = builder.confidenceThreshold;
    }

    // --- getters ---

    /**
     * 识别目标语言集合. 永远非 null 非空. 业务侧可传入多语言;
     * 单语言 vendor 取 {@link Set#iterator() 首个}, 多语言 vendor (Tesseract) 拼接.
     *
     * @return 识别语言集合（永远非 null 非空, 不可变 EnumSet）
     */
    public Set<Languages> languages() { return languages; }

    /**
     * 取首个语言 —— 单语言 vendor 的便捷入口. 不抛异常, 默认 fallback 到
     * {@link Languages#CHINESE_SIMPLIFIED_AND_ENGLISH}.
     *
     * @return 集合中首个语言元素（{@link EnumSet} 按声明顺序）
     */
    public Languages firstLanguage() {
        return languages.iterator().next();
    }

    /**
     * @return 图像 DPI（取值区间 [72, 1200]）
     */
    public int dpi() { return dpi; }

    /**
     * @return 是否自动检测图像方向
     */
    public boolean detectOrientation() { return detectOrientation; }

    /**
     * @return 是否启用表格识别
     */
    public boolean tableRecognition() { return tableRecognition; }

    /**
     * @return 是否启用手写体识别
     */
    public boolean handwriting() { return handwriting; }

    /**
     * @return 是否输出坐标框（{@link OcrBlock#box()} / {@link OcrLine#box()}）
     */
    public boolean outputBoundingBoxes() { return outputBoundingBoxes; }

    /**
     * @return 置信度阈值（取值区间 [0.0, 1.0], 默认 {@link #DEFAULT_CONFIDENCE_THRESHOLD}）
     */
    public float confidenceThreshold() { return confidenceThreshold; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OcrOptions that)) return false;
        return dpi == that.dpi
            && detectOrientation == that.detectOrientation
            && tableRecognition == that.tableRecognition
            && handwriting == that.handwriting
            && outputBoundingBoxes == that.outputBoundingBoxes
            && Float.compare(confidenceThreshold, that.confidenceThreshold) == 0
            && Objects.equals(languages, that.languages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(languages, dpi, detectOrientation,
            tableRecognition, handwriting, outputBoundingBoxes, confidenceThreshold);
    }

    @Override
    public String toString() {
        return "OcrOptions{languages=" + languages
                + ", dpi=" + dpi + ", tableRecognition=" + tableRecognition + '}';
    }

    // --- 预设 ---

    /**
     * @return 新的 {@link Builder}（默认配置: 中英 + DPI 300 + 自动方向 + 输出坐标框）
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return 默认配置（等同于 {@code builder().build()}）
     */
    public static OcrOptions defaultOptions() {
        return builder().build();
    }

    /**
     * @return 扫描文档预设（自动方向 + 表格识别 + 输出坐标框）
     */
    public static OcrOptions scanDocument() {
        return builder()
            .detectOrientation(true)
            .tableRecognition(true)
            .outputBoundingBoxes(true)
            .build();
    }

    /**
     * @return 手机拍照预设（自动方向 + 输出坐标框 + 不开表格识别）
     */
    public static OcrOptions mobilePhoto() {
        return builder()
            .detectOrientation(true)
            .tableRecognition(false)
            .outputBoundingBoxes(true)
            .build();
    }

    /**
     * @return 手写表单预设（手写体识别 + 输出坐标框）
     */
    public static OcrOptions handwrittenForm() {
        return builder()
            .handwriting(true)
            .outputBoundingBoxes(true)
            .build();
    }

    // --- Builder ---

    public static final class Builder {
        private Set<Languages> languages = EnumSet.of(Languages.CHINESE_SIMPLIFIED_AND_ENGLISH);
        private int dpi = 300;
        private boolean detectOrientation = true;
        private boolean tableRecognition = false;
        private boolean handwriting = false;
        private boolean outputBoundingBoxes = true;
        private float confidenceThreshold = DEFAULT_CONFIDENCE_THRESHOLD;

        private Builder() {}

        /**
         * 设置识别语言 —— 业务侧可传多语言. 重置之前的设置.
         *
         * <p>传 {@link Languages#AUTO} 表示用 {@code OcrProperties.defaultLanguages};
         * Provider 内部负责处理默认语言解析.
         */
        public Builder languages(Languages... languages) {
            Objects.requireNonNull(languages, "languages");
            if (languages.length == 0) {
                throw new IllegalArgumentException("languages must not be empty");
            }
            for (Languages l : languages) {
                Objects.requireNonNull(l, "language element");
            }
            this.languages = EnumSet.copyOf(Set.of(languages));
            return this;
        }

        /**
         * Set 版本, 同 {@link #languages(Languages...)}.
         */
        public Builder languages(Set<Languages> languages) {
            Objects.requireNonNull(languages, "languages");
            if (languages.isEmpty()) {
                throw new IllegalArgumentException("languages must not be empty");
            }
            this.languages = EnumSet.copyOf(languages);
            return this;
        }

        /**
         * 增量添加单个语言 —— 在当前集合上叠加. 链式多次调用累加.
         *
         * <p>注意: 默认值 {@link Languages#CHINESE_SIMPLIFIED_AND_ENGLISH} 也在内.
         * 如需"清空后只加这一个", 用 {@link #languages(Languages...)} 替代.
         */
        public Builder addLanguage(Languages language) {
            Objects.requireNonNull(language, "language");
            this.languages.add(language);
            return this;
        }

        /**
         * 设置图像 DPI。
         *
         * @param dpi 图像 DPI（取值区间 [72, 1200]）
         * @return 当前 Builder
         * @throws IllegalArgumentException 当 dpi 超出合法区间时
         */
        public Builder dpi(int dpi) {
            if (dpi < 72 || dpi > 1200) {
                throw new IllegalArgumentException("dpi must be in [72, 1200], got " + dpi);
            }
            this.dpi = dpi;
            return this;
        }

        /**
         * 设置是否自动检测图像方向。
         *
         * @param detectOrientation 是否自动检测方向
         * @return 当前 Builder
         */
        public Builder detectOrientation(boolean detectOrientation) {
            this.detectOrientation = detectOrientation;
            return this;
        }

        /**
         * 设置是否启用表格识别。
         *
         * @param tableRecognition 是否启用表格识别
         * @return 当前 Builder
         */
        public Builder tableRecognition(boolean tableRecognition) {
            this.tableRecognition = tableRecognition;
            return this;
        }

        /**
         * 设置是否启用手写体识别。
         *
         * @param handwriting 是否启用手写体识别
         * @return 当前 Builder
         */
        public Builder handwriting(boolean handwriting) {
            this.handwriting = handwriting;
            return this;
        }

        /**
         * 设置是否输出坐标框。
         *
         * @param outputBoundingBoxes 是否输出坐标框（{@link OcrBlock#box()} / {@link OcrLine#box()}）
         * @return 当前 Builder
         */
        public Builder outputBoundingBoxes(boolean outputBoundingBoxes) {
            this.outputBoundingBoxes = outputBoundingBoxes;
            return this;
        }

        /**
         * 设置置信度阈值。
         *
         * @param confidenceThreshold 置信度阈值（取值区间 [0.0, 1.0]）
         * @return 当前 Builder
         * @throws IllegalArgumentException 当阈值超出合法区间或为 NaN 时
         */
        public Builder confidenceThreshold(float confidenceThreshold) {
            if (Float.isNaN(confidenceThreshold) || confidenceThreshold < 0f || confidenceThreshold > 1f) {
                throw new IllegalArgumentException(
                        "confidenceThreshold must be in [0.0, 1.0], got " + confidenceThreshold);
            }
            this.confidenceThreshold = confidenceThreshold;
            return this;
        }

        /**
         * 构建不可变的 {@link OcrOptions}。
         *
         * @return 新建 OcrOptions 实例
         */
        public OcrOptions build() {
            return new OcrOptions(this);
        }
    }
}