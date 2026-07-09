/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.config.mvc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 跨域配置（前缀：{@code platform.component.web.cors}，README.md §5.1）。
 * <p>
 * 作为 web 子域（CORS）独立 Properties 注册；与 {@link WebProperties} 平级，互不嵌套。
 *
 * <h2 style="color:#c00">⚠ Gateway 模式下必须禁用</h2>
 * <p>当部署 <strong>atlas-richie-gateway-service</strong> 时，CORS 跨域头（{@code Access-Control-Allow-Origin}
 * / {@code Access-Control-Allow-Methods} / {@code Access-Control-Allow-Credentials} 等）由 gateway 统一签发。
 * <p>web-core 端再写 CORS 头会导致浏览器收到<strong>两个相互冲突的响应头</strong>（同一个 header key 多值），
 * 触发 CORS 校验失败。
 * <p><strong>YAML 必须显式关闭</strong>：
 * <pre>{@code
 * platform:
 *   component:
 *     web:
 *       cors:
 *         enabled: false                # ← 关键：禁用 CORS，避免与 gateway 重复签头
 * }</pre>
 *
 * @author richie696
 * @since 2026-07
 */
@Data
@ConfigurationProperties(prefix = "platform.component.web.cors")
public class CorsProperties {

    /**
     * 是否启用 CORS。默认 true。
     */
    private boolean enabled = true;

    /**
     * 允许的来源列表；默认 {@code *}。
     */
    private String[] allowedOrigins = {"*"};

    /**
     * 允许的方法列表；默认 GET/POST/PUT/DELETE/OPTIONS。
     */
    private String[] allowedMethods = {"GET", "POST", "PUT", "DELETE", "OPTIONS"};

    /**
     * 允许的请求头列表；默认 {@code *}。
     */
    private String[] allowedHeaders = {"*"};

    /**
     * 预检请求缓存时长（秒）。默认 3600。
     */
    private long maxAge = 3600L;

    /**
     * 是否允许携带凭证。默认 false。
     */
    private boolean allowCredentials = false;
}