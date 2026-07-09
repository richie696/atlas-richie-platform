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
package com.richie.component.oauth.dcr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OAuth 2.0 Dynamic Client Registration Request
 * <p>
 * RFC 7591 客户端注册请求 DTO。
 *
 * @author richie696
 * @since 2026-06-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientRegistrationRequest {

    /**
     * 客户端名称
     */
    private String clientName;

    /**
     * RFC 7591 要求的 OAuth 2.0 客户端 URI
     */
    private String clientUri;

    /**
     * 客户端图标 URL
     */
    private String logoUri;

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
     * 客户端公钥（JWK 或 JWK Set URL）
     */
    private String jwks;

    /**
     * JWK Set URI
     */
    private String jwksUri;

    /**
     * 客户端软件标识
     */
    private String softwareId;

    /**
     * 客户端软件版本
     */
    private String softwareVersion;

    /**
     * RFC 8707 resource 元数据
     */
    private List<String> resource;
}
