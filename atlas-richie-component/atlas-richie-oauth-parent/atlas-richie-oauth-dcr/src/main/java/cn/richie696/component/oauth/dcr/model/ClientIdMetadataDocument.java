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
package cn.richie696.component.oauth.dcr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客户端元数据文档(RFC 7591 扩展元数据模型)。
 * <p>
 * 描述已注册客户端在服务端持有的完整元数据:基础信息、授权能力、联系人、URI 声明、TLS/JWK 资料;
 * 由 {@link DynamicClientRegistrationEndpoint} 写入
 * {@link cn.richie696.component.oauth.core.config.OAuth2RedisKey#OAUTH2_CLIENT_META},由
 * {@link DefaultClientIdMetadataDocumentResolver} 按 clientId 读取。
 * </p>
 * <p>
 * 处于 oauth-dcr 的元数据持久化位置:与 oauth-core 的 {@link cn.richie696.component.oauth.core.model.ClientConfig}
 * 并列,前者承载 RFC 7591 完整元数据,后者承载运行时高频读取字段;二者通过
 * {@link RedisClientRegistrationStore} 同时落 Redis 保持一致。
 * </p>
 * <p>
 * 解决的问题:用独立模型承载 RFC 7591 的扩展元数据,避免把 OIDC logo/tos/policy/contacts 等
 * 字段塞进运行时高频读取的 ClientConfig;同时让"完整元数据按需加载"成为可能,降低
 * ClientRegistry 的内存与带宽占用。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientIdMetadataDocument {

    /**
     * 客户端 ID
     */
    private String clientId;

    /**
     * 客户端 Secret Hash
     */
    private String clientSecret;

    /**
     * 客户端名称
     */
    private String clientName;

    /**
     * 允许的重定向 URI
     */
    private List<String> redirectUris;

    /**
     * 令牌端点认证方法
     */
    private String tokenEndpointAuthMethod;

    /**
     * Grant Types
     */
    private List<String> grantTypes;

    /**
     * Scopes
     */
    private List<String> scopes;

    /**
     * 联系人邮箱
     */
    private List<String> contacts;

    /**
     * 客户端 URI
     */
    private String clientUri;

    /**
     * Logo URI
     */
    private String logoUri;

    /**
     * 所有者
     */
    private String owner;

    /**
     * 停止运营日期
     */
    private String tosUri;

    /**
     * 政策 URI
     */
    private String policyUri;

    /**
     * JWK Set URI
     */
    private String jwksUri;

    /**
     * RFC 8707 Resource 元数据
     */
    private List<String> resource;
}
