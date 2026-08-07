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
package cn.richie696.component.oauth.authz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RFC 8414 Authorization Server Metadata 模型。
 * <p>
 * 端点 {@code /.well-known/oauth-authorization-server};描述授权服务器的能力:issuer、各端点 URL、
 * 支持的 response_type、PKCE code_challenge_method、grant_types、scopes;由 OAuth Service 在
 * HTTP 适配层序列化为 RFC 8414 规定的 JSON。
 * </p>
 * <p>
 * 处于 oauth-authz 的协议输出契约位置:由 OAuth Service 在启动时基于 OAuth2Properties 与实际
 * 注册的 Bean 构造,作为 Discovery 文档对外发布;客户端据此动态发现端点与能力,避免硬编码 URL。
 * </p>
 * <p>
 * 解决的问题:用统一模型收敛 Discovery 文档的全部字段,避免在适配层各处手写 JSON;同时把可选
 * 字段(Device Authorization Endpoint、Introspection Endpoint 等)以 @Builder 默认值形式表达,
 * 让"启用哪些能力就声明哪些端点"成为自然语义。
 * </p>
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

    /** RFC 8628 Device Authorization Endpoint URL。 */
    private String deviceAuthorizationEndpoint;

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

    /** 保留旧版 8 参数构造方式，避免服务侧编译断裂。 */
    public AuthorizationServerMetadata(String issuer, String authorizationEndpoint, String tokenEndpoint,
                                        String introspectionEndpoint, List<String> responseTypesSupported,
                                        List<String> codeChallengeMethodsSupported,
                                        List<String> grantTypesSupported, List<String> scopesSupported) {
        this.issuer = issuer;
        this.authorizationEndpoint = authorizationEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.introspectionEndpoint = introspectionEndpoint;
        this.responseTypesSupported = responseTypesSupported;
        this.codeChallengeMethodsSupported = codeChallengeMethodsSupported;
        this.grantTypesSupported = grantTypesSupported;
        this.scopesSupported = scopesSupported;
    }
}
