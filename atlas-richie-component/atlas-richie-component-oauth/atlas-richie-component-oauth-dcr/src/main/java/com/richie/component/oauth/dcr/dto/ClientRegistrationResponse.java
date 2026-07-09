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
package com.richie.component.oauth.dcr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OAuth 2.0 Dynamic Client Registration Response
 * <p>
 * RFC 7591 客户端注册响应 DTO。
 *
 * @author richie696
 * @since 2026-06-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientRegistrationResponse {

    /**
     * 客户端 ID（自动生成）
     */
    private String clientId;

    /**
     * 客户端密钥（自动生成，仅当 tokenEndpointAuthMethod 非 none 时返回）
     */
    private String clientSecret;

    /**
     * 客户端密钥过期时间
     */
    private Long clientSecretExpiresAt;

    /**
     * 注册 Access Token（用于后续的客户端更新/删除操作）
     */
    private Long registrationAccessToken;

    /**
     * 注册客户端 URI
     */
    private String registrationClientUri;

    /**
     * 客户端名称
     */
    private String clientName;

    /**
     * 允许的重定向 URI 列表
     */
    private List<String> redirectUris;

    /**
     * 令牌端点认证方法
     */
    private String tokenEndpointAuthMethod;

    /**
     * 申请的 grant_types
     */
    private List<String> grantTypes;

    /**
     * 申请的 scopes
     */
    private List<String> scopes;

    /**
     * 客户端 URI
     */
    private String clientUri;

    /**
     * 图标 URI
     */
    private String logoUri;

    /**
     * RFC 8707 resource 元数据
     */
    private List<String> resource;
}
