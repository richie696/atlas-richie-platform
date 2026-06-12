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
