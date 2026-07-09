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
package com.richie.gateway.vo;

import com.richie.context.utils.data.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.List;

/**
 * 第三方客户端配置 VO（从 Redis 读取，用于网关认证）
 * <p>
 * 注意：此配置对象不包含公司信息，只包含网关侧认证所需的信息
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyClientConfigVO {

    /**
     * 客户端ID（必需）
     */
    private String clientId;

    /**
     * 客户端密钥，明文（必需）
     */
    private String clientSecret;

    /**
     * 客户端名称（可选）
     */
    private String clientName;

    /**
     * 权限范围列表（可选）
     */
    private List<String> scopes;

    /**
     * 是否启用（必需）
     */
    private Boolean enabled;

    /**
     * IP 白名单列表（可选）
     */
    private List<String> ipWhitelist;

    /**
     * Token 有效期（小时）（可选）
     */
    private Integer tokenValidDuration;

    /**
     * Refresh Token 有效期（小时）（可选）
     */
    private Integer refreshTokenValidDuration;

    /**
     * 限流配置（可选）
     */
    private Integer rateLimit;

    /**
     * Redis Hash 字段定义，统一收口避免魔法字符串散落。
     */
    public enum Field {
        CLIENT_ID("clientId", String.class),
        CLIENT_SECRET("clientSecret", String.class),
        CLIENT_NAME("clientName", String.class),
        SCOPES("scopes", List.class),
        ENABLED("enabled", Boolean.class),
        IP_WHITELIST("ipWhitelist", List.class),
        TOKEN_VALID_DURATION("tokenValidDuration", Integer.class),
        REFRESH_TOKEN_VALID_DURATION("refreshTokenValidDuration", Integer.class),
        RATE_LIMIT("rateLimit", Integer.class);

        private final String redisField;
        private final Class<?> valueClass;

        Field(String redisField, Class<?> valueClass) {
            this.redisField = redisField;
            this.valueClass = valueClass;
        }

        public String redisField() {
            return redisField;
        }

        public String getName() {
            return redisField;
        }

        @SuppressWarnings("unchecked")
        public <T> Class<T> valueClass() {
            return (Class<T>) valueClass;
        }

        public Object parseRawValue(Object rawValue) {
            if (rawValue == null) {
                return null;
            }
            return switch (this) {
                case CLIENT_ID, CLIENT_SECRET, CLIENT_NAME -> rawValue.toString();
                case ENABLED -> parseBoolean(rawValue);
                case TOKEN_VALID_DURATION, REFRESH_TOKEN_VALID_DURATION, RATE_LIMIT -> {
                    try {
                        if (rawValue instanceof Number number) {
                            yield number.intValue();
                        }
                        yield Integer.parseInt(rawValue.toString());
                    } catch (Exception e) {
                        yield null;
                    }
                }
                case SCOPES, IP_WHITELIST -> parseStringList(rawValue);
            };
        }

        private Boolean parseBoolean(Object rawValue) {
            if (rawValue instanceof Boolean boolValue) {
                return boolValue;
            }
            String value = rawValue.toString();
            if (StringUtils.isBlank(value)) {
                return null;
            }
            return "true".equalsIgnoreCase(value) || "1".equals(value);
        }

        private List<String> parseStringList(Object rawValue) {
            if (rawValue instanceof Collection<?> collection) {
                return collection.stream().map(String::valueOf).toList();
            }
            String value = rawValue.toString();
            if (StringUtils.isBlank(value)) {
                return List.of();
            }
            try {
                return JsonUtils.getInstance().deserialize(value, new TypeReference<>() {
                });
            } catch (Exception e) {
                return java.util.Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(StringUtils::isNotBlank)
                        .toList();
            }
        }
    }
}
