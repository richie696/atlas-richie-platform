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
package cn.richie696.component.oauth.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OAuth 客户端的完整配置模型。
 * <p>
 * 描述一个客户端的元数据:基础信息(client_id / name / secret / enabled)、授权能力(scopes /
 * redirect_uris / grant_types / tokenEndpointAuthMethod / resource)、安全策略(ip_whitelist /
 * rate_limit)与生命周期(tokenValidDuration / refreshTokenValidDuration);同时定义
 * {@link Field} 枚举把字段名映射到 Redis Hash 的实际 Key,供
 * {@link cn.richie696.component.oauth.core.ClientRegistry} 做字段级只读访问。
 * </p>
 * <p>
 * 处于 oauth-core 的客户端数据契约位置:由 {@link cn.richie696.component.oauth.core.spi.ClientRepository} 读写,贯穿 Token 端点、
 * 授权端点、动态注册端点等所有需要客户端信息的协议路径;OAuth Service 可整体替换为带租户/标签
 * 等扩展字段的派生模型。
 * </p>
 * <p>
 * 解决的问题:把"客户端有哪些字段"统一为一个可序列化的模型,避免每个端点自行定义字段子集;
 * 同时通过 Field 枚举把"字段读取"与"模型映射"分离,支持字段级查询而不必加载整张配置。
 * </p>
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
    private List<String> redirectUris;
    private List<String> grantTypes;
    private String tokenEndpointAuthMethod;
    private String resource;
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
        REDIRECT_URIS("redirectUris"),
        GRANT_TYPES("grantTypes"),
        TOKEN_ENDPOINT_AUTH_METHOD("tokenEndpointAuthMethod"),
        RESOURCE("resource"),
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
