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
package com.richie.component.ocr.volcano.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 火山引擎 OCR 私有配置属性。
 *
 * <p>vendor: {@code volcano}；API 协议类型: 火山引擎视觉智能 HTTP JSON 接口，使用
 * AWS4-HMAC-SHA256 签名鉴权；配置方式: 通过 Spring Boot {@link ConfigurationProperties} 绑定
 * {@code platform.component.ocr.volcano.*}，再由自动配置转换为 Provider 的运行时配置。</p>
 *
 * <p>绑定前缀: {@code platform.component.ocr.volcano}
 *
 * <p>典型配置:
 * <pre>
 * platform:
 *   component:
 *     ocr:
 *       vendor: volcano              # 必填, 激活本 volcano 模块
 *       volcano:
 *         provider-name: volcano-prod         # 可选, 不写用默认
 *         endpoint: <a href="https://visual.volcengineapi.com">https://visual.volcengineapi.com</a>
 *         timeout-ms: 30000
 *         region: cn-north-1
 *         credentials:
 *           access-key: ${VOLCANO_ACCESS_KEY:}
 *           secret-key: ${VOLCANO_SECRET_KEY:}
 * </pre>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
@Data
@ConfigurationProperties(prefix = "platform.component.ocr.volcano")
public class VolcanoOcrProperties {

    /**
     * 火山引擎 OCR API endpoint。
     */
    private String endpoint = "https://visual.volcengineapi.com";

    /**
     * 单次 HTTP 调用超时 (毫秒), 默认 30 秒。
     */
    private long timeoutMs = 30_000L;

    /**
     * 火山引擎服务地域, 默认华北。
     */
    private String region = "cn-north-1";

    /**
     * 鉴权凭据 (AccessKey + SecretKey)。
     */
    @NestedConfigurationProperty
    private Credentials credentials = new Credentials();

    /**
     * 火山引擎 API 凭据（AccessKey + SecretKey），用于 AWS4-HMAC-SHA256 签名。
     *
     * <p>通过 {@code platform.component.ocr.volcano.credentials.*} 子配置绑定。
     */
    @Data
    public static class Credentials {
        /** 火山引擎 API 访问密钥 Id。 */
        private String accessKey;
        /** 火山引擎 API 访问密钥 Secret。 */
        private String secretKey;
    }
}
