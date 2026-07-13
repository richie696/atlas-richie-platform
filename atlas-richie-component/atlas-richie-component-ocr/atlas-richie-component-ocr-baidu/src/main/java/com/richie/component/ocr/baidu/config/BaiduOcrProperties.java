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
package com.richie.component.ocr.baidu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 百度智能云 OCR 私有配置属性。
 *
 * <p>vendor: {@code baidu}；API 协议类型: 百度 OCR REST API v2，识别接口使用
 * {@code application/x-www-form-urlencoded} 请求体并通过 OAuth2 {@code access_token} 鉴权；
 * 配置方式: 通过 Spring Boot {@link ConfigurationProperties} 绑定
 * {@code platform.component.ocr.baidu.*}，再由自动配置转换为 Provider 的运行时配置。</p>
 *
 * <p>绑定前缀: {@code platform.component.ocr.baidu}
 *
 * <p>典型配置:
 * <pre>
 * platform:
 *   component:
 *     ocr:
 *       vendor: baidu                          # 必填, 激活本 baidu 模块
 *       baidu:
 *         provider-name: baidu-prod            # 可选
 *         api-key: ${BAIDU_API_KEY:}
 *         secret-key: ${BAIDU_SECRET_KEY:}
 *         endpoint: https://aip.baidubce.com
 *       timeout-ms: 30000
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "platform.component.ocr.baidu")
public class BaiduOcrProperties {

    /**
     * Provider 内部标识 —— 多 baidu 实例区分用, 默认 {@code baidu-prod}。
     */

    /**
     * 百度智能云 OAuth2 API Key。
     */
    private String apiKey;

    /**
     * 百度智能云 OAuth2 Secret Key。
     */
    private String secretKey;

    /**
     * 百度 OCR API endpoint, 默认官方。
     */
    private String endpoint = "https://aip.baidubce.com";

    /**
     * 单次 HTTP 调用超时 (毫秒), 默认 30 秒。
     */
    private long timeoutMs = 30_000L;
}