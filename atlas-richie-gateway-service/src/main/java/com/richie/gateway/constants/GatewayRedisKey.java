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
package com.richie.gateway.constants;

/**
 * 网关 Redis Key 枚举
 * <p>
 * 统一管理网关服务中使用的所有 Redis Key，避免硬编码散落在代码中
 * <p>
 * 使用方式：
 * <pre>
 * // 获取完整 Key（带参数）
 * String key = GatewayRedisKey.OAUTH2_CLIENT_CONFIG.getKey(clientId);
 * 
 * // 获取 Key 前缀
 * String prefix = GatewayRedisKey.OAUTH2_CLIENT_CONFIG.getPrefix();
 * </pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-18
 */
public enum GatewayRedisKey {

    // ==================== OAuth2.0 相关 ====================

    /**
     * 第三方客户端配置
     * Key: third-party-client:{clientId}
     * Type: Hash
     */
    OAUTH2_CLIENT_CONFIG("third-party-client:", "third-party-client:%s"),

    /**
     * Refresh Token 存储
     * Key: refresh-token:{refreshToken}
     * Type: Hash
     */
    OAUTH2_REFRESH_TOKEN("refresh-token:", "refresh-token:%s"),

    /**
     * 客户端 Refresh Token 索引（用于立即作废功能）
     * Key: client-refresh-token:{clientId}
     * Type: String (存储当前有效的 refreshToken)
     */
    OAUTH2_CLIENT_REFRESH_TOKEN_INDEX("client-refresh-token:", "client-refresh-token:%s"),

    /**
     * OAuth2.0 签发令牌每日调用次数计数
     * Key: oauth2:daily:issue-count:{clientId}:{yyyyMMdd}
     * Type: String (计数器)
     */
    OAUTH2_DAILY_TOKEN_ISSUE_COUNT("oauth2:daily:issue-count:", "oauth2:daily:issue-count:%s"),

    /**
     * Refresh Token 分布式锁
     * Key: refresh-token-lock:{refreshToken}
     * Type: String (锁)
     */
    OAUTH2_REFRESH_TOKEN_LOCK("refresh-token-lock:", "refresh-token-lock:%s"),

    /**
     * Access Token 黑名单
     * Key: access-token-blacklist:{accessToken}
     * Type: String
     */
    OAUTH2_ACCESS_TOKEN_BLACKLIST("access-token-blacklist:", "access-token-blacklist:%s"),

    /**
     * Access Token 与 IP 绑定
     * Key: access-token-ip:{accessToken}
     * Type: Hash
     * Fields: clientId, ip, createdAt
     */
    OAUTH2_ACCESS_TOKEN_IP_BIND("access-token-ip:", "access-token-ip:%s"),

    /**
     * OAuth2.0 Token 重放检测 - Token 使用的 IP 集合
     * Key: oauth2:anomaly:token:ips:{token}
     * Type: Set
     */
    OAUTH2_ANOMALY_TOKEN_IPS("oauth2:anomaly:token:ips:", "oauth2:anomaly:token:ips:%s"),

    /**
     * OAuth2.0 异常刷新检测 - 刷新次数计数
     * Key: oauth2:anomaly:refresh:count:{clientId}
     * Type: String (计数器)
     */
    OAUTH2_ANOMALY_REFRESH_COUNT("oauth2:anomaly:refresh:count:", "oauth2:anomaly:refresh:count:%s"),

    /**
     * OAuth2.0 限流 - 基于客户端配置的限流计数
     * Key: oauth2:anomaly:ratelimit:oauth2:{clientId}
     * Type: String (计数器)
     */
    OAUTH2_ANOMALY_RATELIMIT("oauth2:anomaly:ratelimit:oauth2:", "oauth2:anomaly:ratelimit:oauth2:%s"),

    /**
     * OAuth2.0 审计事件流
     * Key: oauth2:audit:events
     * Type: Stream
     */
    OAUTH2_AUDIT_EVENTS("oauth2:audit:events", "oauth2:audit:events"),

    /**
     * 网关接口配置索引
     * Key: gateway:api:index
     * Type: Set
     * Value: apiCode 列表（如 order.read、order.write）
     */
    GATEWAY_API_INDEX("gateway:api:index", "gateway:api:index"),

    /**
     * 网关接口配置
     * Key: gateway:api:{apiCode}
     * Type: Hash
     * Fields: apiCode, apiName, pathPattern, httpMethod, requireScope, enabled
     */
    GATEWAY_API_CONFIG("gateway:api:", "gateway:api:%s"),

    /**
     * 接口所需的 Scope 列表
     * Key: gateway:api:scopes:{apiCode}
     * Type: Set
     * Value: scope_code 列表
     */
    GATEWAY_API_SCOPES("gateway:api:scopes:", "gateway:api:scopes:%s"),

    /**
     * Scope 定义
     * Key: gateway:scope:{scopeCode}
     * Type: Hash
     * Fields: scopeCode, scopeName, enabled
     */
    GATEWAY_SCOPE_CONFIG("gateway:scope:", "gateway:scope:%s"),

    // ==================== 通用异常检测相关 ====================

    /**
     * 通用异常检测 - 用户 IP 集合
     * Key: gateway:anomaly:user:ips:{userId}
     * Type: Set
     */
    ANOMALY_USER_IPS("gateway:anomaly:user:ips:", "gateway:anomaly:user:ips:%s"),

    /**
     * 通用异常检测 - 用户限流计数
     * Key: gateway:anomaly:ratelimit:{userId}
     * Type: String (计数器)
     */
    ANOMALY_RATELIMIT("gateway:anomaly:ratelimit:", "gateway:anomaly:ratelimit:%s"),

    /**
     * 通用异常检测 - 用户失败次数
     * Key: gateway:anomaly:failures:user:{userId}
     * Type: String (计数器)
     */
    ANOMALY_FAILURES_USER("gateway:anomaly:failures:user:", "gateway:anomaly:failures:user:%s"),

    /**
     * 通用异常检测 - IP 失败次数
     * Key: gateway:anomaly:failures:ip:{ip}
     * Type: String (计数器)
     */
    ANOMALY_FAILURES_IP("gateway:anomaly:failures:ip:", "gateway:anomaly:failures:ip:%s"),

    /**
     * 通用异常检测 - 用户封禁标记
     * Key: gateway:anomaly:ban:user:{userId}
     * Type: String
     */
    ANOMALY_BAN_USER("gateway:anomaly:ban:user:", "gateway:anomaly:ban:user:%s"),

    // ==================== 防重复提交相关 ====================

    /**
     * 防重复提交标记
     * Key: platform:gateway:duplicate-submit:{requestId}
     * Type: String
     */
    DUPLICATE_SUBMIT("platform:gateway:duplicate-submit:", "platform:gateway:duplicate-submit:%s"),

    // ==================== ECC 加密相关 ====================

    /**
     * ECC 客户端公钥缓存
     * Key: platform:gateway:ecc:client:publickey:{clientId}
     * Type: String
     */
    ECC_CLIENT_PUBLIC_KEY("platform:gateway:ecc:client:publickey:", "platform:gateway:ecc:client:publickey:%s"),

    /**
     * ECC 共享密钥缓存
     * Key: platform:gateway:ecc:sharedkey:{clientId}
     * Type: Hash
     */
    ECC_SHARED_KEY("platform:gateway:ecc:sharedkey:", "platform:gateway:ecc:sharedkey:%s"),

    // ==================== 配置相关（从配置读取，仅作为文档说明）====================

    /**
     * 访问记录路径（从配置读取）
     * 配置项: platform.gateway.visit-record-path
     * Key: {visitRecordPath}{ip}
     * Type: Hash
     * 注意：此 Key 的实际值从配置中读取，此处仅作为文档说明
     */
    VISIT_RECORD("platform:gateway:visit:", "platform:gateway:visit:%s"),

    /**
     * Token 黑名单路径（从配置读取）
     * 配置项: platform.gateway.token.blacklist-path
     * Key: {blacklistPath}{token}
     * Type: String
     * 注意：此 Key 的实际值从配置中读取，此处仅作为文档说明
     */
    TOKEN_BLACKLIST("platform:gateway:token:", "platform:gateway:token:%s"),

    /**
     * 在线 Token 路径（从配置读取）
     * 配置项: platform.gateway.sso.online-token-path
     * Key: {onlineTokenPath}{key}
     * Type: Set
     * 注意：此 Key 的实际值从配置中读取，此处仅作为文档说明
     */
    SSO_ONLINE_TOKEN("platform:gateway:online-token:", "platform:gateway:online-token:%s"),

    /**
     * 永久封禁路径（从配置读取）
     * 配置项: platform.gateway.security.banned.permanent-path
     * Key: {permanentPath}
     * Type: Set
     * 注意：此 Key 的实际值从配置中读取，此处仅作为文档说明
     */
    SECURITY_PERMANENT_BAN("platform:gateway:security:permanent", "platform:gateway:security:permanent");

    /**
     * Key 前缀
     */
    private final String prefix;

    /**
     * Key 模板（支持参数替换）
     */
    private final String template;

    GatewayRedisKey(String prefix, String template) {
        this.prefix = prefix;
        this.template = template;
    }

    /**
     * 获取 Key 前缀
     *
     * @return Key 前缀
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * 获取完整的 Key（无参数）
     *
     * @return 完整的 Key
     */
    public String getKey() {
        return prefix;
    }

    /**
     * 获取完整的 Key（单个参数）
     *
     * @param param 参数
     * @return 完整的 Key
     */
    public String getKey(String param) {
        return String.format(template, param);
    }

    /**
     * 获取完整的 Key（多个参数）
     * <p>
     * 注意：此方法仅用于模板中包含多个 %s 的情况
     *
     * @param params 参数数组
     * @return 完整的 Key
     */
    public String getKey(Object... params) {
        return String.format(template, params);
    }
}
