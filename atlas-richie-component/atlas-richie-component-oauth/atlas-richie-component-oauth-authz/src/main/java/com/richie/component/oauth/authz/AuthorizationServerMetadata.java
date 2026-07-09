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
package com.richie.component.oauth.authz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RFC 8414 Authorization Server Metadata
 * <p>
 * 端点：/.well-known/oauth-authorization-server
 *
 * @author richie696
 * @since 2026-06-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationServerMetadata {

    /**
     * 授权服务器的标识符
     */
    private String issuer;

    /**
     * RFC 8414 授权端点 URL
     */
    private String authorizationEndpoint;

    /**
     * RFC 7009 Token 撤销端点 URL
     */
    private String tokenEndpoint;

    /**
     * RFC 7662 Token 内省端点 URL
     */
    private String introspectionEndpoint;

    /**
     * 支持的 OAuth 2.0 响应类型
     */
    private List<String> responseTypesSupported;

    /**
     * 支持的 PKCE code_challenge 方法
     */
    private List<String> codeChallengeMethodsSupported;

    /**
     * 支持的 grant_types
     */
    private List<String> grantTypesSupported;

    /**
     * 支持的 scopes
     */
    private List<String> scopesSupported;
}
