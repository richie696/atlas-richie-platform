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

/**
 * OAuth2.0 专属异常检测配置
 * <p>
 * 注意：通用异常检测配置（暴力破解、异常 IP、限流）已迁移到 {@link AnomalyDetectionConfig}
 * 此配置类仅保留 OAuth2.0 专属的检测配置（Token 重放、异常刷新）
 *
 * @author richie696
 * @version 2.0
 * @since 2025-12-18
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.gateway.interface-auth.anomaly-detection")
public class OAuth2AnomalyDetectionConfig {

    /**
     * 是否启用 OAuth2.0 专属异常检测（默认：true）
     * <p>
     * 注意：通用异常检测由 {@link AnomalyDetectionConfig} 控制
     */
    private boolean enabled = true;

    /**
     * Token 重放检测配置（OAuth2.0 专属）
     */
    private TokenReplayConfig tokenReplay = new TokenReplayConfig();

    /**
     * 异常刷新检测配置（OAuth2.0 专属）
     */
    private AbnormalRefreshConfig abnormalRefresh = new AbnormalRefreshConfig();

    /**
     * Token 重放检测配置
     */
    @Data
    public static class TokenReplayConfig {
        /** 默认构造函数，供配置绑定使用。 */
        public TokenReplayConfig() {
        }

        /**
         * 同一 token 最大 IP 数（默认：2）
         */
        private int maxIpsPerToken = 2;

        /**
         * 时间窗口（秒，默认：60，即 1 分钟）
         */
        private int timeWindowSeconds = 60;
    }

    /**
     * 异常刷新检测配置
     */
    @Data
    public static class AbnormalRefreshConfig {
        /** 默认构造函数，供配置绑定使用。 */
        public AbnormalRefreshConfig() {
        }

        /**
         * 1 分钟内最大刷新次数（默认：10）
         */
        private int maxRefreshesPerMinute = 10;

        /**
         * 时间窗口（秒，默认：60，即 1 分钟）
         */
        private int timeWindowSeconds = 60;
    }

}
