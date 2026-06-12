package com.richie.component.oauth.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth 2.1 Token 内省响应
 *
 * @author richie696
 * @since 2026-06-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenIntrospection {

    private boolean active;
    private String clientId;
    private String tokenType;
    private String scope;
    private Long expiresIn;
    private String sub;
    private String iss;
}
