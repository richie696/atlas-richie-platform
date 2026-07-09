/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.http.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HTTP 客户端核心配置。
 * <p>
 * 通过 {@code platform.component.http.provider} 选择底层实现：
 * <ul>
 *   <li>{@code okhttp}（默认）</li>
 *   <li>{@code http_client_5}</li>
 *   <li>{@code rest_client}</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.http")
public class HttpCoreProperties {

    private HttpProvider provider;

    /**
     * 是否跳过 SSL/TLS 证书校验。
     * <p>
     * 默认为 {@code true}（强制证书校验）。
     * 仅开发/联调环境可设为 {@code false}，生产环境请保持默认。
     */
    private boolean strictSsl = true;

}
