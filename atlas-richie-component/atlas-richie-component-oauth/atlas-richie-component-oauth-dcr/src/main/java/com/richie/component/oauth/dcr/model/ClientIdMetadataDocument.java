package com.richie.component.oauth.dcr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Client ID Metadata Document
 * <p>
 * RFC 7591 扩展，支持客户端通过外部文档声明元数据。
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
