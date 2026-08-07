package cn.richie696.component.oauth.contract.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;

/** RFC 7662 introspection 响应。 */
public record OAuthIntrospectionResponse(
        boolean active,
        @JsonProperty("client_id")
        String clientId,
        @JsonProperty("token_type")
        String tokenType,
        String scope,
        @JsonProperty("sub")
        String subject,
        @JsonProperty("iss")
        String issuer,
        @JsonProperty("aud")
        String audience,
        @JsonProperty("exp")
        long expiresAt,
        @JsonProperty("iat")
        long issuedAt,
        @JsonProperty("jti")
        String tokenId,
        Map<String, Object> claims
) {
    public OAuthIntrospectionResponse {
        claims = claims == null ? Collections.emptyMap() : Map.copyOf(claims);
    }

    public static OAuthIntrospectionResponse inactive() {
        return new OAuthIntrospectionResponse(false, null, null, null, null, null,
                null, 0, 0, null, Collections.emptyMap());
    }
}
