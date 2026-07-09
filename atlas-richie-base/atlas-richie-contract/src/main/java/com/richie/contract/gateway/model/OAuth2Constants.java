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
package com.richie.contract.gateway.model;

/**
 * OAuth2.0 常量类
 * <p>
 * 统一管理 OAuth2.0 模块中使用的所有常量值
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-16
 */
public interface OAuth2Constants {

    // ==================== 接口路径 ====================

    /**
     * OAuth2.0 基础路径
     */
    String OAUTH2_BASE = "/api/oauth2";

    /**
     * Token 接口路径
     */
    String OAUTH2_TOKEN_PATH = "%s/token".formatted(OAUTH2_BASE);

    /**
     * 撤销 Token 接口路径
     */
    String OAUTH2_REVOKE_PATH = "%s/revoke".formatted(OAUTH2_BASE);

    // ==================== Grant Types ====================

    /**
     * Client Credentials 授权类型
     */
    String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";

    /**
     * Refresh Token 授权类型
     */
    String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    /**
     * Access Token 授权类型
     */
    String GRANT_TYPE_ACCESS_TOKEN = "access_token";

    /**
     * Bearer Token 类型
     */
    String TOKEN_TYPE_BEARER = "Bearer";

    // ==================== HTTP Headers ====================

    /**
     * Authorization 请求头
     */
    String HEADER_AUTHORIZATION = "Authorization";

    /**
     * Bearer Token 前缀
     */
    String BEARER_PREFIX = "Bearer ";

    /**
     * 第三方客户端 ID 请求头
     */
    String HEADER_X_THIRD_PARTY_CLIENT_ID = "X-Third-Party-Client-Id";

    // ==================== OAuth2.0 错误码 ====================

    /**
     * 无效请求
     */
    String ERROR_INVALID_REQUEST = "invalid_request";

    /**
     * 无效客户端
     */
    String ERROR_INVALID_CLIENT = "invalid_client";

    /**
     * 无效授权
     */
    String ERROR_INVALID_GRANT = "invalid_grant";

    /**
     * 无效令牌
     */
    String ERROR_INVALID_TOKEN = "invalid_token";

    /**
     * 无效权限范围
     */
    String ERROR_INVALID_SCOPE = "invalid_scope";

    /**
     * 不支持的授权类型
     */
    String ERROR_UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type";

    /**
     * 未授权的客户端
     */
    String ERROR_UNAUTHORIZED_CLIENT = "unauthorized_client";

    /**
     * IP 不在白名单中
     */
    String ERROR_IP_NOT_ALLOWED = "ip_not_allowed";

    /**
     * 客户端已禁用
     */
    String ERROR_CLIENT_DISABLED = "client_disabled";

    /**
     * 请求频率超限
     */
    String ERROR_RATE_LIMIT_EXCEEDED = "rate_limit_exceeded";

    /**
     * 服务器错误
     */
    String ERROR_SERVER_ERROR = "server_error";

    /**
     * 配置错误
     */
    String ERROR_INVALID_CONFIG = "invalid_config";

    // ==================== JWT Claims ====================

    /**
     * JWT Claim: 客户端ID
     */
    String JWT_CLAIM_CLIENT_ID = "clientId";

    /**
     * JWT Claim: 类型（第三方系统）
     */
    String JWT_CLAIM_TYPE = "type";

    /**
     * JWT Claim: 类型值（第三方系统）
     */
    String JWT_CLAIM_TYPE_THIRD_PARTY = "third_party";

    /**
     * JWT Claim: 权限范围
     */
    String JWT_CLAIM_SCOPE = "scope";

    /**
     * JWT Claim: 用户名（使用 clientId）
     */
    String JWT_CLAIM_USERNAME = "username";

    /**
     * JWT Subject: 第三方访问令牌
     */
    String JWT_SUBJECT_THIRD_PARTY_ACCESS_TOKEN = "Third Party Access Token";

    // ==================== 默认时间常量（秒）====================

    /**
     * Access Token 默认有效期（1小时）
     */
    long DEFAULT_ACCESS_TOKEN_EXPIRES_IN = 3600L;

    /**
     * Refresh Token 默认有效期（30天）
     */
    long DEFAULT_REFRESH_TOKEN_EXPIRES_IN = 2592000L;

    // ==================== 错误文档 URI ====================

    /**
     * OAuth2.0 错误文档基础 URI
     */
    String ERROR_DOCS_BASE_URI = "https://docs.richie696.cn/oauth2/errors#";
}
