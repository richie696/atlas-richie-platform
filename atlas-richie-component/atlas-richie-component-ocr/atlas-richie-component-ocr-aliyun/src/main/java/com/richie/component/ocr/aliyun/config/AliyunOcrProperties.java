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
package com.richie.component.ocr.aliyun.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 阿里云读光 OCR 私有配置属性。
 *
 * <p>vendor: {@code aliyun}；协议类型: 阿里云读光 HTTP JSON 同步识别接口，支持图片 URL 或裸 Base64 请求体；
 * 配置方式: 通过 Spring Boot {@link ConfigurationProperties} 绑定
 * {@code platform.component.ocr.aliyun.*}，再由自动配置转换为 Provider 的运行时配置。</p>
 *
 * <p>绑定前缀: {@code platform.component.ocr.aliyun}
 *
 * <p>典型配置:
 * <pre>
 * platform:
 *   component:
 *     ocr:
 *       vendor: aliyun              # 必填, 激活本 aliyun 模块
 *       aliyun:
 *         provider-name: aliyun-prod         # 可选, 不写用默认
 *         endpoint: https://ocr-api.cn-shanghai.aliyuncs.com
 *         timeout-ms: 30000
 *         credentials:
 *           app-code: ${ALIYUN_APP_CODE:}    # 必填, 阿里云市场版 APPCODE
 *         vendor:                            # 注意: 这是 aliyun OCR API 自己的请求体字段, 不是 OCR 组件 vendor 概念
 *           model: standard-form
 *           feature: false
 * </pre>
 *
 * <p><b>字段命名区分</b>:
 * <ul>
 *   <li>外层 {@code platform.component.ocr.aliyun.*} —— 本类字段</li>
 *   <li>内层 {@code platform.component.ocr.aliyun.vendor.*} —— aliyun OCR API
 *       请求体里的 {@code vendor} 字段 (model / feature), 跟 OCR 组件的 vendor
 *       概念无关, 只是 API 字段同名</li>
 * </ul>
 *
 * <p><b>绑定策略</b>: yaml 字段 kebab-case 与 Java 字段 camelCase 通过 Spring Boot
 * {@code @ConfigurationProperties} 原生 relaxed binding 自动映射, typed POJO 通过
 * 构造函数直接注入 typed AliyunOcrProperties, 无 JsonNode 中间表示
 * SPI.
 */
@Data
@ConfigurationProperties(prefix = "platform.component.ocr.aliyun")
public class AliyunOcrProperties {

    /**
     * Provider 内部标识 —— 多 aliyun 实例区分用, 默认 {@code aliyun-prod}。
     * <p>不写则使用 Provider 内部默认。
     */

    /**
     * 阿里云读光 API endpoint, 默认上海 region。
     */
    private String endpoint = "https://ocr-api.cn-shanghai.aliyuncs.com";

    /**
     * 单次 HTTP 调用超时 (毫秒), 默认 30 秒。
     */
    private long timeoutMs = 30_000L;

    /**
     * 鉴权凭据 (阿里云市场版走 APPCODE 模式)。
     */
    @NestedConfigurationProperty
    private Credentials credentials = new Credentials();

    /**
     * aliyun OCR API 请求体里的 vendor 段 (model / feature) —— API 字段同名, 跟 OCR
     * 组件 vendor 概念无关, 这里保留 API 原始命名以便对齐协议。
     */
    @NestedConfigurationProperty
    private Vendor vendor = new Vendor();

    /**
     * 鉴权凭据。
     */
    @Data
    public static class Credentials {
        /**
         * 阿里云市场版 APPCODE (替代 AK/SK 的简化鉴权)。
         */
        private String appCode;
    }

    /**
     * aliyun OCR API 请求体里的 vendor 段。
     */
    @Data
    public static class Vendor {
        /**
         * 识别模型: {@code standard-form} / {@code standard-ocr} / {@code advanced-ocr}。
         */
        private String model = "standard-form";

        /**
         * 是否启用 advanced 特性 (如表格识别增强)。
         */
        private boolean feature = false;
    }
}