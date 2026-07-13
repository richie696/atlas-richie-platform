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

import java.util.Locale;
import java.util.Set;

/**
 * OCR 识别目标语言 —— 业务侧按场景选择.
 *
 * <p>每个常量对应一组 BCP-47 / ISO 639-1 标签, Provider 在请求时按需拼接或映射到 vendor 私有 code.
 *
 * <p>单 vendor 单次识别支持多语言的 vendor (如 Tesseract) 把多个 {@code Languages} 用
 * "{@code +}" 拼接 (例如 {@code jpn+eng}); 单语言 vendor (Baidu / Aliyun / Paddle 等)
 * 取首个语言. 业务侧无须关心 vendor 差异.
 *
 * <p>{@link #AUTO} 是兜底 sentinel: 业务侧在 {@link OcrOptions.Builder} 里设了
 * {@code .languages(Languages.AUTO)} 时, 引擎层会用 {@code OcrProperties.defaultLanguages}
 * 解析成实际语言集合.
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
public enum Languages {
    /** 兜底 sentinel: 业务侧显式声明 "用全局 yml 配置的 defaultLanguages" */
    AUTO(),

    /** 简体中文 + 英文混合（默认） */
    CHINESE_SIMPLIFIED_AND_ENGLISH("zh-CN", "en"),
    /** 仅简体中文 */
    CHINESE_SIMPLIFIED("zh-CN"),
    /** 仅英文 */
    ENGLISH("en"),
    /** 繁体中文 */
    CHINESE_TRADITIONAL("zh-TW"),
    /** 日文 */
    JAPANESE("ja"),
    /** 韩文 */
    KOREAN("ko"),
    /** 阿拉伯数字单字符识别（验证码 / 票据号） */
    DIGITS_ONLY("en"),
    /** 纯拉丁字母（含英法德西意） */
    LATIN("en", "fr", "de", "es", "it"),

    /** 阿拉伯文 */
    ARABIC("ar"),
    /** 俄文 */
    RUSSIAN("ru"),
    /** 印地文 */
    HINDI("hi"),
    /** 泰文 */
    THAI("th"),
    /** 越南文 */
    VIETNAMESE("vi"),
    /** 希腊文 */
    GREEK("el"),
    /** 土耳其文 */
    TURKISH("tr");

    private final String[] tags;

    Languages(String... tags) {
        this.tags = tags.length == 0 ? new String[0] : tags.clone();
    }

    /**
     * BCP-47 / ISO 639-1 标签数组 —— Provider 拼接到请求时使用.
     */
    public String[] tags() {
        return tags.length == 0 ? new String[0] : tags.clone();
    }

    /**
     * 取首个 tag（用于单语言 vendor 的兜底映射, 如 Baidu / Aliyun / Paddle）.
     */
    public String primaryTag() {
        return tags.length == 0 ? Locale.ENGLISH.getLanguage() : tags[0];
    }

    /**
     * 是否是 sentinel / 兜底语言（AUTO 唯一）.
     */
    public boolean isSentinel() {
        return tags.length == 0;
    }

    /**
     * 兜底默认集合 —— yml 没配 defaultLanguages 时使用.
     */
    public static Set<Languages> fallbackDefault() {
        return Set.of(CHINESE_SIMPLIFIED_AND_ENGLISH);
    }
}