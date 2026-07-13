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
package com.richie.component.ocr.config;

/**
 * OCR 组件支持的 vendor 枚举。
 *
 * <p>单一部署只允许激活一个 vendor —— 这不是技术限制, 而是运营约束:
 * OCR 引擎的选型跟数据合规 / 成本 / SLA 强相关, 是上线前一次性确定的事,
 * 不应在运行时切换。{@code platform.component.ocr.vendor} 字段驱动
 * {@code @ConditionalOnProperty(havingValue=...)} 选择具体 vendor 的
 * {@code AutoConfiguration} 激活。
 *
 * <p>yaml 配小写, 通过 {@link OcrVendorConverter} 转换: {@code vendor: aliyun}
 * ↔ {@link #ALIYUN}。
 *
 * <p>新增 vendor 需要: 1) 在此枚举添加常量 2) 创建对应模块 + 自己的
 * {@code *AutoConfiguration} + {@code META-INF/spring/...imports}。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public enum OcrVendor {

    /**
     * 阿里云读光 OCR (cloud / 同步推荐 / Phase A 实装)。
     */
    ALIYUN("aliyun"),

    /**
     * 百度 OCR (cloud / 同步推荐 / Phase B)。
     */
    BAIDU("baidu"),

    /**
     * PaddleOCR (本地 CPU / in-process / 同步推荐 / Phase B)。
     */
    PADDLE("paddle"),

    /**
     * Tesseract (本地 CPU / in-process / 同步推荐 / Phase B)。
     */
    TESSERACT("tesseract"),

    /**
     * PaddleOCR-VL (本地 GPU / sidecar / VLM 异步 / Phase B-2)。
     */
    PADDLE_VL("paddle-vl"),

    /**
     * MinerU (本地 GPU / remote-service / VLM 异步 / Phase B-2)。
     */
    MINERU("mineru"),

    /**
     * 腾讯云 OCR (cloud / 同步 / TC3-HMAC-SHA256 签名)。
     */
    TENCENT("tencent"),

    /**
     * 火山引擎 OCR (cloud / 同步 / AWS4-HMAC-SHA256 签名)。
     */
    VOLCANO("volcano");

    private final String key;

    OcrVendor(String key) {
        this.key = key;
    }

    /**
     * 返回该 vendor 在 yaml 配置中使用的 key (小写, 短横线分隔)。
     *
     * @return 与 {@code application.yml} 中 {@code platform.component.ocr.vendor} 字段一一对应的小写字符串
     */
    public String key() {
        return key;
    }

    /**
     * 大小写不敏感地把 yaml 字符串解析为枚举; 未识别时抛 {@link IllegalArgumentException},
     * Spring Boot 启动期 fail-fast, 提示用户拼写错误或忘加 vendor 模块。
     *
     * <p>解析规则: {@code trim().toLowerCase()} 后与各枚举常量的 {@link #key()} 比对,
     * 命中即返回。{@code null} 立即抛异常 (与 {@code Enum.valueOf} 一致);
     * 未识别的 key 同样抛 {@link IllegalArgumentException}, 异常消息附带可用 vendor 列表。
     *
     * @param key yaml 写入的 vendor key (如 {@code "aliyun"} / {@code "ALIYUN"} / {@code "  Baidu  "})
     * @return 与之匹配的 {@link OcrVendor} 枚举常量
     * @throws IllegalArgumentException 当 {@code key} 为 {@code null} 或不匹配任何一个已知 vendor 时抛出
     */
    public static OcrVendor fromKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("OCR vendor key is null");
        }
        String normalized = key.trim().toLowerCase();
        for (OcrVendor v : values()) {
            if (v.key.equals(normalized)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unknown OCR vendor: '" + key
                + "'. Available: aliyun, baidu, paddle, tesseract, paddle-vl, mineru, tencent, volcano");
    }
}
