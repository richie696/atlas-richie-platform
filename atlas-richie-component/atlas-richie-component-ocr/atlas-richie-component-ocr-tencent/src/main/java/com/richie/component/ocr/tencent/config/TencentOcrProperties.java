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
package com.richie.component.ocr.tencent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 腾讯云 OCR 私有配置属性。
 *
 * <p>vendor: {@code tencent}；API 协议类型: 腾讯云 OCR HTTP JSON 接口，使用 TC3-HMAC-SHA256
 * 签名鉴权；配置方式: 通过 Spring Boot {@link ConfigurationProperties} 绑定
 * {@code platform.component.ocr.tencent.*}，再由自动配置转换为 Provider 的运行时配置。</p>
 *
 * <p>绑定前缀: {@code platform.component.ocr.tencent}
 *
 * <p>典型配置:
 * <pre>
 * platform:
 *   component:
 *     ocr:
 *       vendor: tencent               # 必填, 激活本 tencent 模块
 *       tencent:
 *         provider-name: tencent-prod          # 可选, 不写用默认
 *         endpoint: <a href="https://ocr.tencentcloudapi.com">https://ocr.tencentcloudapi.com</a>
 *         timeout-ms: 30000
 *         region: ap-guangzhou
 *         credentials:
 *           secret-id: ${TENCENT_SECRET_ID:}
 *           secret-key: ${TENCENT_SECRET_KEY:}
 * </pre>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
@Data
@ConfigurationProperties(prefix = "platform.component.ocr.tencent")
public class TencentOcrProperties {

    /**
     * 腾讯云 OCR API endpoint。
     */
    private String endpoint = "https://ocr.tencentcloudapi.com";

    /**
     * 单次 HTTP 调用超时 (毫秒), 默认 30 秒。
     */
    private long timeoutMs = 30_000L;

    /**
     * 腾讯云服务地域, 默认广州。
     */
    private String region = "ap-guangzhou";

    /**
     * OCR API Action 名称, 默认高精度版 {@code GeneralAccurateOCR}。
     * <p>可选值:
     * <ul>
     *   <li>{@code GeneralAccurateOCR} — 高精度版 (推荐, 99% 准确率)</li>
     *   <li>{@code GeneralBasicOCR} — 标准版 (96% 准确率)</li>
     * </ul>
     */
    private String action = "GeneralAccurateOCR";

    /**
     * 鉴权凭据 (SecretId + SecretKey)。
     */
    @NestedConfigurationProperty
    private Credentials credentials = new Credentials();

    /**
     * 腾讯云 API 凭据（SecretId + SecretKey），用于 TC3-HMAC-SHA256 签名。
     *
     * <p>通过 {@code platform.component.ocr.tencent.credentials.*} 子配置绑定。
     */
    @Data
    public static class Credentials {
        /** 腾讯云 API 密钥 Id。 */
        private String secretId;
        /** 腾讯云 API 密钥 Key。 */
        private String secretKey;
    }
}
