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
package com.richie.component.web.core.config.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web 过滤器通用配置（前缀：{@code platform.component.web.filter}，README.md §4.1）。
 * <p>
 * 当前承载 {@link #keyHeader} 配置：{@link com.richie.component.web.core.spi.support.HeaderBasedKeyResolver}
 * 默认从该 header 取 clientKey；{@link com.richie.component.web.core.spi.support.ClientIdDimension} 同样读取。
 *
 * <h2 style="color:#e80">⚠ Gateway 模式下：保留子域，但 keyHeader 必须与 gateway 透传 header 对齐</h2>
 * <p>atlas-richie-gateway-service 端的过滤器链会<strong>透传 client header</strong>到下游 web 服务（典型如 {@code X-Client-Id}）。
 * web 端本配置照常生效——gateway 不会解析限流 / 熔断的 clientKey，这一步仍由 web 端完成。
 * <p>但是 web-core 端 {@link #keyHeader} 配置名<strong>必须与 gateway 实际透传的 header 一致</strong>，
 * 否则 {@link com.richie.component.web.core.interceptor.RateLimitInterceptor} /
 * {@link com.richie.component.web.core.interceptor.CircuitBreakerInterceptor} 拿不到 clientKey，
 * 会全部按"未识别"短路 401。
 * <p>部署 gateway 时建议显式写明对齐：
 * <pre>{@code
 * platform:
 *   component:
 *     web:
 *       filter:
 *         key-header: X-Client-Id   # 必须与 gateway 网关侧透传 header 名一致
 * }</pre>
 *
 * @author richie696
 * @since 2026-07
 */
@Data
@ConfigurationProperties(prefix = "platform.component.web.filter")
public class WebFilterProperties {

    /**
     * ClientKey 解析默认读取的 header 名。默认 {@code X-Client-Id}。
     * <p>Gateway 模式下必须与 gateway 透传 header 名一致，否则限流/熔断按"未识别"短路。
     */
    private String keyHeader = "X-Client-Id";
}