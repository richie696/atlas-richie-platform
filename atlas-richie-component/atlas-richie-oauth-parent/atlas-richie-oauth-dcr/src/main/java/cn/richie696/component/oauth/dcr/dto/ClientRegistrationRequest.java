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
package cn.richie696.component.oauth.dcr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RFC 7591 动态客户端注册请求 DTO。
 * <p>
 * 描述客户端注册请求的标准字段:基础信息(client_name / client_uri / logo_uri)、授权能力
 * (redirect_uris / token_endpoint_auth_method / grant_types / scopes / resource / jwks /
 * software_id / software_version);由 OAuth Service 在 HTTP 适配层把 JSON 反序列化为本对象,
 * 再交给 {@link cn.richie696.component.oauth.dcr.DynamicClientRegistrationEndpoint} 处理。
 * </p>
 * <p>
 * 处于 oauth-dcr 的协议输入位置:由 HTTP 适配层构造,被 DynamicClientRegistrationEndpoint 消费;
 * redirect_uri 与 jwks_uri 在协议层内部由 {@link cn.richie696.component.oauth.dcr.support.SSRFProtection} 校验,不依赖 HTTP 适配层。
 * </p>
 * <p>
 * 解决的问题:把 RFC 7591 注册请求固化为统一 DTO,避免在适配层手写 JSON 解析;同时把可选字段
 * (logo_uri / client_uri / tos / policy 等)统一建模,后续接入 OIDC 元数据扩展无需改协议层。
 * </p>
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
