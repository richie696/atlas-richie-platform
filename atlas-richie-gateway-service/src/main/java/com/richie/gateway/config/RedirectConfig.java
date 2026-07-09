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
package com.richie.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.io.Serializable;

/**
 * 重定向策略配置
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 00:49:35
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.gateway.security.redirect")
public class RedirectConfig implements Serializable {

    /**
     * 重定向URI（默认："/"）
     * <p style="color: yellow">
     *     如果 SecurityFilterConfig.getRule() 为 SecurityRuleEnum.REDIRECT，
     *     则会将请求重定向到该URI，如果该值未配置会重定向到根页面
     */
    private String securityRedirectUri = "/";

    /**
     * 默认构造函数
     */
    public RedirectConfig() {
    }
}
