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
package com.richie.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * CSP（Content-Security-Policy）过滤器配置
 * <p>
 * 在网关层注入 CSP 响应头，防御 XSS 和数据注入攻击。适用于所有通过网关转发的请求。
 * 配置示例：
 * <pre>{@code
 * platform:
 *   gateway:
 *     csp:
 *       enable: true
 *       policy: "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self'; frame-src 'self'; frame-ancestors 'none'; base-uri 'self'"
 * }</pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.gateway.csp")
public class CspFilterConfig {

    /**
     * 是否启用 CSP 过滤器（默认：false）
     * 建议在生产环境开启。
     */
    private boolean enable = false;

    /**
     * 上游代理（Nginx/ALB）是否已配置 CSP 响应头（默认：false）
     * <p>
     * 网关只给 API 响应加 CSP 头。SPA HTML 页面由 Nginx/ALB 直接响应（不经网关），
     * 需额外配置。当 {@code enable=true} 但本字段为 {@code false} 时，系统在启动时
     * 会发出 WARN 日志提醒运维人员补全。
     * <p>
     * 确认 Nginx/ALB 已配置后设此值为 {@code true} 以关闭告警。
     */
    private boolean proxyCspConfigured = false;

    /**
     * Content-Security-Policy 策略字符串
     * <p>
     * 遵循 W3C CSP Level 2 规范。留空或 null 时即使 enable=true 也不会注入 header。
     * <p>
     * 管理端 SPA 推荐默认值（需根据实际部署调整）：
     * <pre>
     * default-src 'self';
     * script-src 'self' 'unsafe-inline';
     * style-src 'self' 'unsafe-inline';
     * img-src 'self' data: https:;
     * font-src 'self' data:;
     * connect-src 'self';
     * frame-src 'self';
     * frame-ancestors 'none';
     * base-uri 'self'
     * </pre>
     */
    private String policy;
}
