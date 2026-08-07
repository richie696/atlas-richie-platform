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
 * RFC 7591 动态客户端注册响应 DTO。
 * <p>
 * 描述注册端点返回的全部字段:自动生成的 {@code client_id}、仅在非 {@code none} 认证方式下返回的
 * {@code clientSecret} 与 {@code clientSecretExpiresAt}、用于后续更新/删除的
 * {@code registrationAccessToken} 与 {@code registrationClientUri},以及原样回传的客户端元数据;
 *由 {@link cn.richie696.component.oauth.dcr.DynamicClientRegistrationEndpoint} 构造,经 OAuth Service 序列化为 JSON 返回。
 * </p>
 * <p>
 * 处于 oauth-dcr 的协议输出位置:是 DynamicClientRegistrationEndpoint 对外暴露的唯一协议契约,
 * 客户端拿到响应后必须保存 registrationAccessToken 才能后续管理自己的客户端元数据。
 * </p>
 * <p>
 * 解决的问题:把 RFC 7591 注册响应固化为统一 DTO,避免在协议层各处重复写 JSON 拼装;同时通过把
 * clientSecret 仅在非 none 方式下回传,确保公开客户端不会泄露 secret,降低泄露面。
 * </p>
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
