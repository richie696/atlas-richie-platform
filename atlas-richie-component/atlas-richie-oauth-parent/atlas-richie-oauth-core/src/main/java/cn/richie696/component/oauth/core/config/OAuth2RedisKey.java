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
package cn.richie696.component.oauth.core.config;

/**
 * OAuth 2.1 组件全部 Redis Key 的统一枚举。
 * <p>
 * 把 oauth-core、oauth-authz、oauth-dcr 三个模块使用的所有 Key 前缀/模板收敛到同一份枚举,
 * 提供 {@link #getPrefix()} / {@link #getKey()} / {@link #getKey(String)} / {@link #getKey(Object...)}
 * 四种访问方式,杜绝散落字符串拼接。
 * </p>
 * <p>
 * 处于整个 oauth 组件的存储键名中心位置:被 CacheBackedTokenStore、CacheBackedClientRepository、
 * DefaultAuthorizationCodeStore、RedisClientRegistrationStore、SSRFProtection 等所有需要写 Redis
 * 的实现共同持有,保证跨模块 Key 命名一致。
 * </p>
 * <p>
 * 解决的问题:让 Key 命名可被一站式检索与审计,避免各模块各写一份 magic string 导致后续 Key
 * 迁移/重命名困难;同时为运维提供"全组件用到哪些 Redis Key"的清晰视图。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
public enum OAuth2RedisKey {

    OAUTH2_CLIENT_CONFIG("third-party-client:", "third-party-client:%s"),
    OAUTH2_REFRESH_TOKEN("refresh-token:", "refresh-token:%s"),
    OAUTH2_REFRESH_TOKEN_USED("refresh-token-used:", "refresh-token-used:%s"),
    OAUTH2_CLIENT_REFRESH_TOKEN_INDEX("client-refresh-token:", "client-refresh-token:%s"),
    OAUTH2_DAILY_TOKEN_ISSUE_COUNT("oauth2:daily:issue-count:", "oauth2:daily:issue-count:%s"),
    OAUTH2_REFRESH_TOKEN_LOCK("refresh-token-lock:", "refresh-token-lock:%s"),
    OAUTH2_ACCESS_TOKEN_BLACKLIST("access-token-blacklist:", "access-token-blacklist:%s"),
    OAUTH2_ACCESS_TOKEN_IP_BIND("access-token-ip:", "access-token-ip:%s"),
    OAUTH2_ANOMALY_REFRESH_COUNT("oauth2:anomaly:refresh:count:", "oauth2:anomaly:refresh:count:%s"),
    OAUTH2_ANOMALY_RATELIMIT("oauth2:anomaly:ratelimit:oauth2:", "oauth2:anomaly:ratelimit:oauth2:%s"),
    OAUTH2_ANOMALY_TOKEN_IPS("oauth2:anomaly:token:ips:", "oauth2:anomaly:token:ips:%s"),
    OAUTH2_AUDIT_EVENTS("oauth2:audit:events", "oauth2:audit:events"),

    // ==================== oauth-authz ====================
    OAUTH2_AUTHZ_CODE("authz-code:", "authz-code:%s"),

    // ==================== oauth-dcr ====================
    OAUTH2_CLIENT_META("client-meta:", "client-meta:%s"),
    OAUTH2_REGISTRATION_TOKEN("reg-token:", "reg-token:%s"),
    OAUTH2_SSRF_DNS_CACHE("ssrf:dns:", "ssrf:dns:%s"),

    GATEWAY_API_INDEX("gateway:api:index", "gateway:api:index"),
    GATEWAY_API_CONFIG("gateway:api:", "gateway:api:%s"),
    GATEWAY_API_SCOPES("gateway:api:scopes:", "gateway:api:scopes:%s"),
    GATEWAY_SCOPE_CONFIG("gateway:scope:", "gateway:scope:%s");

    private final String prefix;
    private final String template;

    OAuth2RedisKey(String prefix, String template) {
        this.prefix = prefix;
        this.template = template;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getKey() {
        return prefix;
    }

    public String getKey(String param) {
        return String.format(template, param);
    }

    public String getKey(Object... params) {
        return String.format(template, params);
    }
}
