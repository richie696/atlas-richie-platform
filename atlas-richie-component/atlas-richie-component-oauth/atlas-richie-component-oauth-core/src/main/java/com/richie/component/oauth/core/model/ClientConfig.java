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
package com.richie.component.oauth.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客户端配置信息
 * <p>
 * 从 Redis Hash 读取的第三方客户端完整配置
 *
 * @author richie696
 * @since 2026-06-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientConfig {

    private String clientId;
    private String clientSecret;
    private String clientName;
    private Boolean enabled;
    private List<String> scopes;
    private List<String> ipWhitelist;
    private Integer tokenValidDuration;
    private Integer refreshTokenValidDuration;
    private Integer rateLimit;

    /**
     * 客户端配置字段枚举（对应 Redis Hash 字段名）
     */
    public enum Field {
        CLIENT_ID("clientId"),
        CLIENT_SECRET("clientSecret"),
        CLIENT_NAME("clientName"),
        ENABLED("enabled"),
        SCOPES("scopes"),
        IP_WHITELIST("ipWhitelist"),
        TOKEN_VALID_DURATION("tokenValidDuration"),
        REFRESH_TOKEN_VALID_DURATION("refreshTokenValidDuration"),
        RATE_LIMIT("rateLimit");

        private final String name;

        Field(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @SuppressWarnings("unchecked")
        public <T> T parseRawValue(Object rawValue) {
            if (rawValue == null) {
                return null;
            }
            return (T) rawValue;
        }
    }
}
