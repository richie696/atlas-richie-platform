/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Gateway 边缘鉴权配置。OAuth 权威数据由 Authorization Server 管理。 */
@Data
@ConfigurationProperties(prefix = "platform.gateway.interface-auth")
public class GatewayOAuthProperties {

    /** 是否启用 Gateway Resource Server 鉴权过滤器。 */
    private boolean enabled = false;

    /** OAuth 错误文档基础 URI。 */
    private String errorDocsBaseUri;

    /** 迁移期 Authorization Server 反向代理地址。为空时不注册兼容代理。 */
    private String authorizationServerBaseUri;
}
